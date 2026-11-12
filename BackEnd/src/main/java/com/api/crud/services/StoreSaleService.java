package com.api.crud.services;

import com.api.crud.DTO.*;
import com.api.crud.exceptions.*;
import com.api.crud.models.entity.*;
import com.api.crud.models.enums.*;
import com.api.crud.repositories.*;
import com.api.crud.validation.InputRules;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;

import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class StoreSaleService {

    private final StoreSaleDao sales;
    private final OrderDao orders;
    private final OrderWorkflowDao workflow;
    private final UserDao users;
    private final OrderShippingService shipping;
    private final OrderService lifecycle;
    private final PhysicalReturnService returns;
    private final ModelMapper mapper;
    private final ObjectMapper json;
    private final Validator validator;

    public StoreSaleService(
            StoreSaleDao sales,
            OrderDao orders,
            OrderWorkflowDao workflow,
            UserDao users,
            OrderShippingService shipping,
            OrderService lifecycle,
            PhysicalReturnService returns,
            ModelMapper mapper,
            ObjectMapper json,
            Validator validator) {
        this.sales = sales;
        this.orders = orders;
        this.workflow = workflow;
        this.users = users;
        this.shipping = shipping;
        this.lifecycle = lifecycle;
        this.returns = returns;
        this.mapper = mapper;
        this.json = json;
        this.validator = validator;
    }

    private UserModel actor(String email, boolean adminOnly) {
        UserModel actor = users.getUserByEmail(email).orElseThrow(UnauthenticatedException::new);
        if (actor.getStatus() != AccountStatus.ACTIVE) throw new UnauthenticatedException();
        if (!actor.getRoleNames().contains("ADMIN")
                && (adminOnly || !actor.getRoleNames().contains("SUPPORT")))
            throw new ForbiddenOperationException();
        return actor;
    }

    private void validate(StoreSaleCreateRequestDTO request) {
        if (request == null) throw new InvalidRequestException("Datos de venta requeridos");
        var errors = validator.validate(request);
        if (!errors.isEmpty()) throw new ConstraintViolationException(errors);
        if (request.getDeliveryMethod() == DeliveryMethod.DIGITAL_DOWNLOAD)
            throw new InvalidRequestException("La venta presencial admite productos fisicos");
        if (request.getDeliveryMethod() == DeliveryMethod.SHIPPING) {
            if (request.isDeliveredImmediately())
                throw new InvalidRequestException(
                        "Un envio no puede registrarse como entrega inmediata");
        } else {
            if (request.getShippingAddress() != null)
                throw new InvalidRequestException("El retiro en local no requiere direccion");
            if (!request.isDeliveredImmediately()
                    && (request.getCustomerName() == null || request.getCustomerPhone() == null))
                throw new InvalidRequestException("El retiro posterior requiere nombre y telefono");
        }
        if (request.getPaymentMethod() == StorePaymentMethod.CARD_TERMINAL
                && (request.getPaymentReference() == null
                        || request.getPaymentReference().isBlank()))
            throw new InvalidRequestException(
                    "El cobro con terminal requiere referencia de operacion");
        Set<Long> products = new HashSet<>();
        for (var item : request.getItems()) {
            if (!products.add(item.getProductId()))
                throw new InvalidRequestException(
                        "Producto repetido: agrupe la cantidad en una linea");
        }
    }

    private String fingerprint(StoreSaleCreateRequestDTO request) {
        ObjectNode payload = json.valueToTree(request);
        payload.put("amountPaid", request.getAmountPaid().stripTrailingZeros().toPlainString());
        ArrayNode items = json.createArrayNode();
        request.getItems().stream()
                .sorted(Comparator.comparing(StoreSaleItemRequestDTO::getProductId))
                .forEach(item -> items.add(json.valueToTree(item)));
        payload.set("items", items);
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(payload.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 no disponible", exception);
        }
    }

    @Transactional
    public OrderAdminDetailDTO create(String email, StoreSaleCreateRequestDTO request) {
        UserModel actor = actor(email, false);
        validate(request);
        String hash = fingerprint(request);
        workflow.lockUser(actor.getId());
        Order existing = sales.getByReference(request.getReference().toString()).orElse(null);
        if (existing != null) {
            StoreSaleDetails details = existing.getStoreSale();
            if (!hash.equals(details.getRequestHash())
                    || !Objects.equals(details.getCashierUserId(), actor.getId()))
                throw new BusinessConflictException(
                        "La referencia ya pertenece a otra solicitud de venta");
            return mapper.map(existing, OrderAdminDetailDTO.class);
        }
        Order order = new Order();
        order.setSalesChannel(SalesChannel.IN_STORE);
        order.setDeliveryMethod(request.getDeliveryMethod());
        if (request.getDeliveryMethod() == DeliveryMethod.SHIPPING) {
            shipping.prepare(order, mapper.map(request, OrderCreateRequestDTO.class), true);
        } else {
            order.setShippingCost(new BigDecimal("0.00"));
        }
        List<OrderItem> lines = new ArrayList<>();
        Map<ProductPhysical, Integer> remaining = new LinkedHashMap<>();
        BigDecimal subtotal = new BigDecimal("0.00");
        for (var item :
                request.getItems().stream()
                        .sorted(Comparator.comparing(StoreSaleItemRequestDTO::getProductId))
                        .toList()) {
            Product product = workflow.lockProduct(item.getProductId());
            if (!(product instanceof ProductPhysical physical))
                throw new InvalidRequestException("La venta presencial admite productos fisicos");
            if (!product.isActive()) throw new BusinessConflictException("Producto desactivado");
            InputRules.price(product.getPriceProduct());
            if (physical.getStockProduct() < item.getQuantity())
                throw new InsufficientStockException("Stock insuficiente");
            remaining.put(physical, physical.getStockProduct() - item.getQuantity());
            OrderItem line =
                    new OrderItem(order, product, item.getQuantity(), product.getPriceProduct());
            line.setProductName(product.getNameProduct());
            lines.add(line);
            subtotal = subtotal.add(line.getSubtotal());
        }
        BigDecimal total = subtotal.add(order.getShippingCost());
        InputRules.price(total);
        if (request.getAmountPaid().compareTo(total) != 0)
            throw new BusinessConflictException(
                    "El importe cobrado no coincide con los precios actuales y el envio; revise el"
                            + " cobro antes de registrar la venta");
        StoreSaleDetails details = mapper.map(request, StoreSaleDetails.class);
        LocalDateTime now = LocalDateTime.now();
        details.setReference(request.getReference().toString());
        details.setRequestHash(hash);
        details.setCashierUserId(actor.getId());
        details.setPaidAt(now);
        if (request.isDeliveredImmediately()) details.setDeliveredAt(now);
        order.setStoreSale(details);
        order.setPurchaseDateOrder(now);
        order.setPaymentStatus(PaymentStatus.PAID);
        order.setOrderStatus(
                request.isDeliveredImmediately() ? OrderStatus.DELIVERED : OrderStatus.CONFIRMED);
        order.setItemsOrder(lines);
        order.setItemsSubtotal(subtotal);
        order.setTotalOrder(total);
        order.setStockDeducted(true);
        remaining.forEach(ProductPhysical::setStockProduct);
        orders.saveOrder(order);
        workflow.flush();
        audit(order, actor, "STORE_SALE_PAID", request.getReason().trim());
        return mapper.map(order, OrderAdminDetailDTO.class);
    }

    @Transactional
    public OrderAdminDetailDTO refund(String email, Long orderId, String reason) {
        UserModel actor = actor(email, true);
        if (reason == null || reason.isBlank() || reason.length() > 500)
            throw new InvalidRequestException("Debe indicar un motivo de hasta 500 caracteres");
        Order order = workflow.lockOrder(orderId);
        if (order.getSalesChannel() != SalesChannel.IN_STORE
                || order.getStoreSale() == null
                || order.getProviderPaymentId() != null)
            throw new BusinessConflictException("Esta operacion requiere una venta presencial");
        if (order.getPaymentStatus() == PaymentStatus.REFUNDED)
            return mapper.map(order, OrderAdminDetailDTO.class);
        if (order.getPaymentStatus() != PaymentStatus.PAID)
            throw new BusinessConflictException("La venta no admite registrar el reembolso");
        returns.requireRefundReady(order);
        StoreSaleDetails details = order.getStoreSale();
        details.setRefundedAt(LocalDateTime.now());
        details.setRefundedBy(actor.getId());
        details.setRefundReason(reason.trim());
        order.setPaymentStatus(PaymentStatus.REFUNDED);
        lifecycle.cancelRefundedOrder(order);
        for (OrderItem item : order.getItemsOrder()) {
            item.setRefundRequestedQuantity(item.getAmountOrderItem());
            item.setRefundedQuantity(item.getAmountOrderItem());
            if (order.getOrderStatus() == OrderStatus.CANCELED)
                item.setCanceledQuantity(item.getAmountOrderItem());
        }
        audit(order, actor, "STORE_SALE_REFUNDED", reason.trim());
        return mapper.map(order, OrderAdminDetailDTO.class);
    }

    private void audit(Order order, UserModel actor, String action, String reason) {
        OrderChangeAudit audit = new OrderChangeAudit();
        audit.setOrderId(order.getIdOrder());
        audit.setActorUserId(actor.getId());
        audit.setAction(action);
        audit.setNewValue(order.getPaymentStatus().name() + ":" + order.getOrderStatus().name());
        audit.setReason(reason);
        audit.setCreatedAt(LocalDateTime.now());
        workflow.saveAudit(audit);
    }
}

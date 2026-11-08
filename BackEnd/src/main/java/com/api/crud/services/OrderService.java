package com.api.crud.services;

import com.api.crud.DTO.*;
import com.api.crud.exceptions.*;
import com.api.crud.models.entity.*;
import com.api.crud.models.enums.*;
import com.api.crud.repositories.*;

import jakarta.annotation.PostConstruct;

import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class OrderService {

    private final OrderDao orderDao;

    private final UserDao userDao;

    private final OrderWorkflowDao workflow;

    private final ModelMapper mapper;

    private final DigitalDeliveryService digitalDeliveries;

    private final OrderShippingService shipping;

    @Value("${orders.expiration.reservation-minutes:30}")
    private long reservationMinutes = 30;

    public OrderService(
            OrderDao orderDao,
            UserDao userDao,
            OrderWorkflowDao workflow,
            ModelMapper mapper,
            DigitalDeliveryService digitalDeliveries,
            OrderShippingService shipping) {
        this.orderDao = orderDao;
        this.userDao = userDao;
        this.workflow = workflow;
        this.mapper = mapper;
        this.digitalDeliveries = digitalDeliveries;
        this.shipping = shipping;
    }

    @PostConstruct
    void validateReservationMinutes() {
        if (reservationMinutes < 1) {
            throw new IllegalStateException("La reserva debe durar al menos un minuto");
        }
    }

    public List<Long> getExpiredOrderIds(LocalDateTime now, long afterId) {
        return workflow.getExpiredOrderIds(now, now.minusMinutes(reservationMinutes), afterId, 100);
    }

    @Transactional
    public boolean expireOrder(Long id) {
        Order order = workflow.lockOrder(id);
        if (!isDue(order, LocalDateTime.now())) return false;
        if (order.getExpiresAt() == null) {
            order.setExpiresAt(order.getPurchaseDateOrder().plusMinutes(reservationMinutes));
        }
        restoreStock(order);
        order.setOrderStatus(OrderStatus.EXPIRED);
        audit(
                order,
                null,
                "EXPIRE",
                "PENDING",
                "EXPIRED",
                "Vencimiento automatico de la reserva sin pago");
        return true;
    }

    private boolean isDue(Order order, LocalDateTime now) {
        if (order.getOrderStatus() != OrderStatus.PENDING
                || (order.getPaymentStatus() != PaymentStatus.PENDING
                        && order.getPaymentStatus() != PaymentStatus.FAILED)) {
            return false;
        }
        LocalDateTime deadline = order.getExpiresAt();
        if (deadline == null && order.getPurchaseDateOrder() != null) {
            deadline = order.getPurchaseDateOrder().plusMinutes(reservationMinutes);
        }
        return deadline != null && !deadline.isAfter(now);
    }

    private UserModel user(String email) {
        return userDao.getUserByEmail(email).orElseThrow(() -> new UnauthenticatedException());
    }

    private Order owned(String email, Long id) {
        long userId = user(email).getId();
        return orderDao.getOrderById(id)
                .filter(order -> order.getUserOrder().getId() == userId)
                .orElseThrow(() -> new ResourceNotFoundException("Orden no encontrada"));
    }

    private ApplicationException conflict(String message) {
        return new BusinessConflictException(message);
    }

    private String reason(String reason) {
        if (reason == null || reason.isBlank() || reason.length() > 500) {
            throw new InvalidRequestException("Debe indicar un motivo de hasta 500 caracteres");
        }
        return reason.trim();
    }

    public ShippingRateDTO getShippingRate() {
        return new ShippingRateDTO(shipping.getFlatRate());
    }

    @Transactional
    public void updateShipment(String email, Long id, ShipmentTrackingUpdateRequestDTO request) {
        shipping.validate(request);
        UserModel actor = user(email);
        if (actor.getRoleNames().stream()
                .noneMatch(role -> role.equals("ADMIN") || role.equals("SUPPORT"))) {
            throw new ForbiddenOperationException();
        }
        Order order = workflow.lockOrder(id);
        if (order.getPaymentStatus() != PaymentStatus.PAID
                || (order.getOrderStatus() != OrderStatus.CONFIRMED
                        && order.getOrderStatus() != OrderStatus.SHIPPED)
                || order.getItemsOrder().stream()
                        .noneMatch(item -> item.getProductOrderItem() instanceof ProductPhysical)) {
            throw conflict("El seguimiento requiere una orden fisica pagada, confirmada o enviada");
        }
        String motive = reason(request.getReason());
        OrderShipmentTracking previous = order.getShipmentTracking();
        String oldCarrier = previous == null ? null : previous.getCarrier();
        String oldNumber = previous == null ? null : previous.getTrackingNumber();
        String oldUrl = previous == null ? null : previous.getTrackingUrl();
        shipping.updateTracking(order, request);
        OrderShipmentTracking next = order.getShipmentTracking();
        if (!Objects.equals(oldCarrier, next.getCarrier()))
            audit(order, actor, "SHIPMENT_CARRIER", oldCarrier, next.getCarrier(), motive);
        if (!Objects.equals(oldNumber, next.getTrackingNumber()))
            audit(order, actor, "SHIPMENT_NUMBER", oldNumber, next.getTrackingNumber(), motive);
        if (!Objects.equals(oldUrl, next.getTrackingUrl()))
            audit(order, actor, "SHIPMENT_URL", oldUrl, next.getTrackingUrl(), motive);
    }

    public Page<OrderListDTO> getMyOrders(
            String email, OrderStatus status, PaymentStatus payment, Pageable pageable) {
        return workflow.search(user(email).getId(), null, status, payment, null, null, pageable)
                .map(order -> mapper.map(order, OrderListDTO.class));
    }

    public OrderDetailDTO getMyOrder(String email, Long id) {
        return detail(owned(email, id));
    }

    public Page<OrderAdminListDTO> getAdminOrders(
            String email,
            OrderStatus status,
            PaymentStatus payment,
            LocalDateTime start,
            LocalDateTime end,
            Pageable pageable) {
        return workflow.search(null, email, status, payment, start, end, pageable)
                .map(order -> mapper.map(order, OrderAdminListDTO.class));
    }

    public OrderAdminDetailDTO getAdminOrder(Long id) {
        Order order =
                orderDao.getOrderById(id)
                        .orElseThrow(() -> new ResourceNotFoundException("Orden no encontrada"));
        return mapper.map(order, OrderAdminDetailDTO.class);
    }

    private OrderDetailDTO detail(Order order) {
        return mapper.map(order, OrderDetailDTO.class);
    }

    private void audit(
            Order order,
            UserModel actor,
            String action,
            String before,
            String after,
            String reason) {
        OrderChangeAudit audit = new OrderChangeAudit();
        audit.setOrderId(order.getIdOrder());
        audit.setActorUserId(actor == null ? null : actor.getId());
        audit.setAction(action);
        audit.setPreviousValue(before);
        audit.setNewValue(after);
        audit.setReason(reason);
        audit.setCreatedAt(LocalDateTime.now());
        workflow.saveAudit(audit);
    }

    @Transactional
    public OrderDetailDTO createMyOrder(String email, OrderCreateRequestDTO request) {
        UserModel buyer = user(email);
        workflow.lockUser(buyer.getId());
        Cart cart = workflow.lockCart(buyer.getId()).orElseThrow(() -> conflict("Carrito vacio"));
        if (!cart.isActive()) throw conflict("Carrito deshabilitado");
        if (cart.getItemsCart().isEmpty()) throw conflict("Carrito vacio");
        Order order = new Order();
        boolean requiresShipping =
                cart.getItemsCart().stream()
                        .anyMatch(item -> item.getProductCartItem() instanceof ProductPhysical);
        shipping.prepare(order, request, requiresShipping);
        order.setUserOrder(buyer);
        order.setPurchaseDateOrder(LocalDateTime.now());
        order.setExpiresAt(order.getPurchaseDateOrder().plusMinutes(reservationMinutes));
        order.setOrderStatus(OrderStatus.PENDING);
        order.setPaymentStatus(PaymentStatus.PENDING);
        List<OrderItem> purchased = new ArrayList<>();
        Set<Long> digitalProducts = new HashSet<>();
        BigDecimal total = BigDecimal.ZERO;
        List<CartItem> sorted =
                cart.getItemsCart().stream()
                        .sorted(
                                Comparator.comparing(
                                        item -> item.getProductCartItem().getIdProduct()))
                        .toList();
        for (CartItem item : sorted) {
            int amount = item.getAmountCartItem();
            Product product = workflow.lockProduct(item.getProductCartItem().getIdProduct());
            if (product instanceof ProductDigital
                    && (amount != 1 || !digitalProducts.add(product.getIdProduct()))) {
                throw conflict("Solo se permite una unidad por producto digital");
            }
            if (amount <= 0 || !product.isActive())
                throw conflict("Producto o cantidad no disponible");
            if (product instanceof ProductPhysical physical) {
                if (physical.getStockProduct() < amount)
                    throw new InsufficientStockException("Stock insuficiente");
                physical.setStockProduct(physical.getStockProduct() - amount);
            }
            OrderItem line = new OrderItem(order, product, amount, product.getPriceProduct());
            line.setProductName(product.getNameProduct());
            if (product instanceof ProductDigital digital) {
                if (digital.getDownloadLink() == null || digital.getDownloadLink().isBlank()) {
                    throw conflict("El producto digital no tiene contenido disponible");
                }
                line.setDigitalDownloadLink(digital.getDownloadLink());
            }
            purchased.add(line);
            total = total.add(line.getSubtotal());
        }
        BigDecimal grandTotal = total.add(order.getShippingCost());
        if (grandTotal.compareTo(new BigDecimal("99999999.99")) > 0)
            throw conflict("Total fuera del limite admitido");
        order.setItemsOrder(purchased);
        order.setItemsSubtotal(total);
        order.setTotalOrder(grandTotal);
        order.setStockDeducted(true);
        orderDao.saveOrder(order);
        cart.getItemsCart().clear();
        workflow.flush();
        audit(order, buyer, "CREATE", null, "PENDING", "Compra desde el carrito");
        return detail(order);
    }

    @Transactional
    public void cancelMyOrder(String email, Long id, String reason) {
        String motive = reason(reason);
        UserModel actor = user(email);
        // Check ownership before locking or revealing the order state.
        owned(email, id);
        Order order = workflow.lockOrder(id);
        if (order.getUserOrder().getId() != actor.getId()) {
            throw new ResourceNotFoundException("Orden no encontrada");
        }
        if (order.getOrderStatus() == OrderStatus.CANCELED) return;
        if (order.getOrderStatus() != OrderStatus.PENDING
                || (order.getPaymentStatus() != PaymentStatus.PENDING
                        && order.getPaymentStatus() != PaymentStatus.FAILED)) {
            throw conflict("Esta orden no admite cancelacion por el cliente");
        }
        cancel(order, actor, motive);
    }

    @Transactional
    public void cancelAdminOrder(String email, Long id, String reason) {
        UserModel actor = user(email);
        Order order = workflow.lockOrder(id);
        cancel(order, actor, reason(reason));
    }

    private void cancel(Order order, UserModel actor, String reason) {
        if (order.getOrderStatus() == OrderStatus.CANCELED) return;
        if ((order.getOrderStatus() != OrderStatus.PENDING
                        && order.getOrderStatus() != OrderStatus.CONFIRMED)
                || order.getPaymentStatus() == PaymentStatus.PAID) {
            throw conflict(
                    "La cancelacion requiere una orden sin enviar y sin pago pendiente de reembolso");
        }
        restoreStock(order);
        String before = order.getOrderStatus().name();
        order.setOrderStatus(OrderStatus.CANCELED);
        audit(order, actor, "CANCEL", before, "CANCELED", reason);
    }

    private void restoreStock(Order order) {
        for (OrderItem item :
                (order.isStockDeducted() ? order.getItemsOrder() : List.<OrderItem>of())
                        .stream()
                                .sorted(
                                        Comparator.comparing(
                                                line -> line.getProductOrderItem().getIdProduct()))
                                .toList()) {
            Product product = workflow.lockProduct(item.getProductOrderItem().getIdProduct());
            if (product instanceof ProductPhysical physical) {
                long stock = (long) physical.getStockProduct() + item.getAmountOrderItem();
                if (stock > Integer.MAX_VALUE) throw conflict("Stock fuera del limite admitido");
                physical.setStockProduct((int) stock);
            }
        }
        order.setStockDeducted(false);
    }

    @Transactional
    public void updateOrderStatus(String email, Long id, OrderStatus status, String reason) {
        String motive = reason(reason);
        if (status == null) throw new InvalidRequestException("Estado requerido");
        UserModel actor = user(email);
        Order order = workflow.lockOrder(id);
        if (status == OrderStatus.CANCELED) {
            cancel(order, actor, motive);
            return;
        }
        OrderStatus previous = order.getOrderStatus();
        if (previous == status) return;
        boolean digitalOnly =
                order.getItemsOrder().stream()
                        .allMatch(item -> item.getProductOrderItem() instanceof ProductDigital);
        boolean allowed =
                (previous == OrderStatus.PENDING && status == OrderStatus.CONFIRMED)
                        || (previous == OrderStatus.CONFIRMED
                                && status == OrderStatus.SHIPPED
                                && !digitalOnly)
                        || (previous == OrderStatus.SHIPPED && status == OrderStatus.DELIVERED)
                        || (previous == OrderStatus.CONFIRMED
                                && status == OrderStatus.DELIVERED
                                && digitalOnly);
        if (!allowed || order.getPaymentStatus() != PaymentStatus.PAID)
            throw conflict("Transicion de orden no permitida");
        if (!digitalOnly && (status == OrderStatus.SHIPPED || status == OrderStatus.DELIVERED)) {
            shipping.requireReadyToShip(order);
            if (status == OrderStatus.SHIPPED)
                order.getShipmentTracking().setShippedAt(LocalDateTime.now());
            if (status == OrderStatus.DELIVERED)
                order.getShipmentTracking().setDeliveredAt(LocalDateTime.now());
        }
        order.setOrderStatus(status);
        audit(order, actor, "ORDER_STATUS", previous.name(), status.name(), motive);
    }

    @Transactional
    public void updatePaymentStatus(String email, Long id, PaymentStatus status, String reason) {
        String motive = reason(reason);
        if (status == null) throw new InvalidRequestException("Estado requerido");
        UserModel actor = user(email);
        Order order = workflow.lockOrder(id);
        if (isDue(order, LocalDateTime.now())) {
            throw conflict("La reserva de esta orden vencio. Debe iniciar una nueva compra");
        }
        PaymentStatus previous = order.getPaymentStatus();
        if (previous == status) {
            if (status == PaymentStatus.PAID) digitalDeliveries.enablePaidOrder(order);
            return;
        }
        boolean beforeShipping =
                order.getOrderStatus() == OrderStatus.PENDING
                        || order.getOrderStatus() == OrderStatus.CONFIRMED;
        boolean allowed =
                (previous == PaymentStatus.PENDING
                                && (status == PaymentStatus.PAID || status == PaymentStatus.FAILED))
                        || (previous == PaymentStatus.FAILED
                                && (status == PaymentStatus.PENDING
                                        || status == PaymentStatus.PAID))
                        || (previous == PaymentStatus.PAID && status == PaymentStatus.REFUNDED);
        if (!beforeShipping || !allowed) throw conflict("Transicion de pago no permitida");
        order.setPaymentStatus(status);
        if (status == PaymentStatus.PAID) digitalDeliveries.enablePaidOrder(order);
        if (status == PaymentStatus.REFUNDED) digitalDeliveries.revokeOrder(order);
        audit(order, actor, "PAYMENT_STATUS", previous.name(), status.name(), motive);
    }
}

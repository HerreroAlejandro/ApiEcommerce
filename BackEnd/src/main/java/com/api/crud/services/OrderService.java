package com.api.crud.services;

import com.api.crud.DTO.*;
import com.api.crud.models.entity.*;
import com.api.crud.models.enums.*;
import com.api.crud.repositories.*;

import org.modelmapper.ModelMapper;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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

    public OrderService(
            OrderDao orderDao, UserDao userDao, OrderWorkflowDao workflow, ModelMapper mapper) {
        this.orderDao = orderDao;
        this.userDao = userDao;
        this.workflow = workflow;
        this.mapper = mapper;
    }

    private UserModel user(String email) {
        return userDao.getUserByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    private Order owned(String email, Long id) {
        long userId = user(email).getId();
        return orderDao.getOrderById(id)
                .filter(order -> order.getUserOrder().getId() == userId)
                .orElseThrow(
                        () ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND, "Orden no encontrada"));
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private String reason(String reason) {
        if (reason == null || reason.isBlank() || reason.length() > 500) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Debe indicar un motivo de hasta 500 caracteres");
        }
        return reason.trim();
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
                .map(
                        order -> {
                            OrderAdminListDTO dto = mapper.map(order, OrderAdminListDTO.class);
                            dto.setUserId(order.getUserOrder().getId());
                            dto.setUserEmail(order.getUserOrder().getEmail());
                            return dto;
                        });
    }

    public OrderAdminDetailDTO getAdminOrder(Long id) {
        Order order =
                orderDao.getOrderById(id)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND, "Orden no encontrada"));
        OrderAdminDetailDTO dto = mapper.map(order, OrderAdminDetailDTO.class);
        dto.setUserId(order.getUserOrder().getId());
        dto.setUserEmail(order.getUserOrder().getEmail());
        dto.setItemsOrder(items(order));
        return dto;
    }

    private List<OrderItemResponseDTO> items(Order order) {
        return order.getItemsOrder().stream()
                .map(
                        item -> {
                            OrderItemResponseDTO dto = new OrderItemResponseDTO();
                            dto.setIdOrderItem(item.getIdOrderItem());
                            dto.setProductId(item.getProductOrderItem().getIdProduct());
                            dto.setProductName(item.getProductName());
                            dto.setPriceOrderItem(item.getPriceOrderItem());
                            dto.setAmountOrderItem(item.getAmountOrderItem());
                            dto.setSubtotal(item.getSubtotal());
                            return dto;
                        })
                .toList();
    }

    private OrderDetailDTO detail(Order order) {
        OrderDetailDTO dto = mapper.map(order, OrderDetailDTO.class);
        dto.setItemsOrder(items(order));
        return dto;
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
        audit.setActorUserId(actor.getId());
        audit.setAction(action);
        audit.setPreviousValue(before);
        audit.setNewValue(after);
        audit.setReason(reason);
        audit.setCreatedAt(LocalDateTime.now());
        workflow.saveAudit(audit);
    }

    @Transactional
    public OrderDetailDTO createMyOrder(String email) {
        UserModel buyer = user(email);
        workflow.lockUser(buyer.getId());
        Cart cart = workflow.lockCart(buyer.getId()).orElseThrow(() -> conflict("Carrito vacio"));
        if (!cart.isActive()) throw conflict("Carrito deshabilitado");
        if (cart.getItemsCart().isEmpty()) throw conflict("Carrito vacio");
        Order order = new Order();
        order.setUserOrder(buyer);
        order.setPurchaseDateOrder(LocalDateTime.now());
        order.setOrderStatus(OrderStatus.PENDING);
        order.setPaymentStatus(PaymentStatus.PENDING);
        List<OrderItem> purchased = new ArrayList<>();
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
            if (amount <= 0 || !product.isActive())
                throw conflict("Producto o cantidad no disponible");
            if (product instanceof ProductPhysical physical) {
                if (physical.getStockProduct() < amount) throw conflict("Stock insuficiente");
                physical.setStockProduct(physical.getStockProduct() - amount);
            }
            OrderItem line = new OrderItem(order, product, amount, product.getPriceProduct());
            line.setProductName(product.getNameProduct());
            purchased.add(line);
            total = total.add(line.getSubtotal());
        }
        if (total.compareTo(new BigDecimal("99999999.99")) > 0)
            throw conflict("Total fuera del limite admitido");
        order.setItemsOrder(purchased);
        order.setTotalOrder(total);
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
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Orden no encontrada");
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
        String before = order.getOrderStatus().name();
        order.setOrderStatus(OrderStatus.CANCELED);
        order.setStockDeducted(false);
        audit(order, actor, "CANCEL", before, "CANCELED", reason);
    }

    @Transactional
    public void updateOrderStatus(String email, Long id, OrderStatus status, String reason) {
        String motive = reason(reason);
        if (status == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Estado requerido");
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
        order.setOrderStatus(status);
        audit(order, actor, "ORDER_STATUS", previous.name(), status.name(), motive);
    }

    @Transactional
    public void updatePaymentStatus(String email, Long id, PaymentStatus status, String reason) {
        String motive = reason(reason);
        if (status == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Estado requerido");
        UserModel actor = user(email);
        Order order = workflow.lockOrder(id);
        PaymentStatus previous = order.getPaymentStatus();
        if (previous == status) return;
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
        audit(order, actor, "PAYMENT_STATUS", previous.name(), status.name(), motive);
    }
}

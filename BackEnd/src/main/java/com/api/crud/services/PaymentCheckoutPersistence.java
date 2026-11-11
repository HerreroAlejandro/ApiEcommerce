package com.api.crud.services;

import com.api.crud.DTO.CheckoutResponseDTO;
import com.api.crud.config.MercadoPagoProperties;
import com.api.crud.exceptions.*;
import com.api.crud.models.entity.*;
import com.api.crud.models.enums.*;
import com.api.crud.repositories.*;

import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

// crea y conserva la vinculación entre orden y checkout.
@Service
@Transactional(readOnly = true)
public class PaymentCheckoutPersistence {

    private final PaymentDao payments;

    private final OrderDao orders;

    private final OrderWorkflowDao workflow;

    private final UserDao users;

    private final MercadoPagoClient client;

    private final MercadoPagoProperties properties;

    private final ModelMapper mapper;

    @Value("${orders.expiration.reservation-minutes:30}")
    private long reservationMinutes = 30;

    public PaymentCheckoutPersistence(
            PaymentDao payments,
            OrderDao orders,
            OrderWorkflowDao workflow,
            UserDao users,
            MercadoPagoClient client,
            MercadoPagoProperties properties,
            ModelMapper mapper) {
        this.payments = payments;
        this.orders = orders;
        this.workflow = workflow;
        this.users = users;
        this.client = client;
        this.properties = properties;
        this.mapper = mapper;
    }

    private Order owned(String email, Long orderId) {
        UserModel user = users.getUserByEmail(email).orElseThrow(UnauthenticatedException::new);
        if (user.getStatus() != AccountStatus.ACTIVE) throw new UnauthenticatedException();
        orders.getOrderById(orderId)
                .filter(
                        order ->
                                order.getUserOrder() != null
                                        && order.getUserOrder().getId() == user.getId())
                .orElseThrow(ResourceNotFoundException::new);
        Order order = workflow.lockOrder(orderId);
        if (order.getUserOrder() == null || order.getUserOrder().getId() != user.getId())
            throw new ResourceNotFoundException();
        return order;
    }

    private Instant requirePayable(Order order) {
        if (order.getSalesChannel() != SalesChannel.ONLINE
                || order.getOrderStatus() != OrderStatus.PENDING
                || !order.isStockDeducted()
                || (order.getPaymentStatus() != PaymentStatus.PENDING
                        && order.getPaymentStatus() != PaymentStatus.FAILED)
                || order.getTotalOrder() == null
                || order.getTotalOrder().signum() <= 0) {
            throw new BusinessConflictException("La orden no permite iniciar un pago online");
        }
        if (order.getItemsOrder() == null
                || order.getItemsOrder().isEmpty()
                || order.getShippingCost() == null
                || order.getShippingCost().signum() < 0) {
            throw new BusinessConflictException("La orden no tiene un detalle de compra valido");
        }
        BigDecimal calculated = order.getShippingCost();
        for (OrderItem item : order.getItemsOrder()) {
            if (item.getAmountOrderItem() < 1
                    || item.getPriceOrderItem() == null
                    || item.getPriceOrderItem().signum() <= 0
                    || item.getProductName() == null
                    || item.getProductName().isBlank()
                    || (item.getProductOrderItem() instanceof ProductDigital
                            && (item.getAmountOrderItem() != 1
                                    || item.getDigitalDownloadLink() == null
                                    || item.getDigitalDownloadLink().isBlank()))) {
                throw new BusinessConflictException(
                        "El detalle historico requiere una nueva compra o revision");
            }
            calculated = calculated.add(item.getSubtotal());
        }
        if (calculated.compareTo(order.getTotalOrder()) != 0)
            throw new BusinessConflictException(
                    "El importe de la orden no coincide con sus productos y envio");
        LocalDateTime deadline = order.getExpiresAt();
        if (deadline == null && order.getPurchaseDateOrder() != null)
            deadline = order.getPurchaseDateOrder().plusMinutes(reservationMinutes);
        if (deadline == null || !deadline.isAfter(LocalDateTime.now()))
            throw new BusinessConflictException("La reserva vencio. Debe iniciar una nueva compra");
        return deadline.atZone(ZoneId.systemDefault()).toInstant();
    }

    @Transactional
    public String prepare(String email, Long orderId) {
        properties.requireEnabled();
        Order order = owned(email, orderId);
        Instant deadline = requirePayable(order);
        PaymentCheckout checkout = payments.getCheckoutByOrderId(orderId).orElse(null);
        if (checkout == null) {
            checkout = new PaymentCheckout();
            checkout.setReference(UUID.randomUUID().toString());
            checkout.setOrder(order);
            checkout.setAmount(order.getTotalOrder());
            checkout.setCurrency(properties.getCurrency());
            checkout.setCollectorId(properties.getCollectorId());
            checkout.setLiveMode(properties.isLiveMode());
            checkout.setExpiresAt(deadline);
            checkout.setCreatedAt(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS));
            checkout.setRequestBody(client.prepareOrderRequest(checkout));
            payments.saveCheckout(checkout);
        }
        return checkout.getReference();
    }

    // Separate transaction: the durable reference already exists if the remote call times out.
    @Transactional(timeout = 40)
    public CheckoutResponseDTO ensureOrder(String email, String reference) {
        properties.requireEnabled();
        PaymentCheckout initial =
                payments.getCheckoutByReference(reference)
                        .orElseThrow(ResourceNotFoundException::new);
        Order order = owned(email, initial.getOrder().getIdOrder());
        requirePayable(order);
        PaymentCheckout checkout =
                payments.getCheckoutByOrderId(order.getIdOrder())
                        .orElseThrow(ResourceNotFoundException::new);
        payments.refreshCheckout(checkout);
        if (!checkout.getCollectorId().equals(properties.getCollectorId())
                || checkout.isLiveMode() != properties.isLiveMode()) {
            throw new BusinessConflictException(
                    "La configuracion del vendedor cambio para este checkout");
        }
        if (checkout.getProviderOrderId() == null || checkout.getCheckoutUrl() == null) {
            if (checkout.getRequestBody() == null) {
                throw new BusinessConflictException(
                        "Este checkout anterior requiere revision. Inicie una nueva compra");
            }
            MercadoPagoClient.CheckoutOrder remote = client.createOrder(checkout);
            if (checkout.getProviderOrderId() != null
                    && !checkout.getProviderOrderId().equals(remote.id())) {
                throw new BusinessConflictException(
                        "Mercado Pago devolvio otra order para el mismo intento");
            }
            if (!checkout.getExpiresAt().isAfter(Instant.now()))
                throw new BusinessConflictException(
                        "La reserva vencio durante la preparacion del pago");
            checkout.setProviderOrderId(remote.id());
            checkout.setCheckoutUrl(remote.checkoutUrl());
        }
        return mapper.map(checkout, CheckoutResponseDTO.class);
    }
}

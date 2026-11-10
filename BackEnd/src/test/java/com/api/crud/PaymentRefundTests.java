package com.api.crud;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.api.crud.DTO.PaymentRefundResponseDTO;
import com.api.crud.config.*;
import com.api.crud.exceptions.*;
import com.api.crud.models.entity.*;
import com.api.crud.models.entity.Order;
import com.api.crud.models.enums.*;
import com.api.crud.repositories.*;
import com.api.crud.services.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

class PaymentRefundTests {

    private PaymentDao payments;
    private PaymentRefundDao refunds;
    private OrderWorkflowDao workflow;
    private DigitalDeliveryService deliveries;
    private MercadoPagoClient client;
    private PaymentRefundPersistence persistence;
    private MercadoPagoProperties properties;
    private UserModel admin;
    private Order order;
    private Payment payment;
    private Map<String, PaymentRefund> saved;

    @BeforeEach
    void setUp() {
        payments = mock(PaymentDao.class);
        refunds = mock(PaymentRefundDao.class);
        workflow = mock(OrderWorkflowDao.class);
        deliveries = mock(DigitalDeliveryService.class);
        client = mock(MercadoPagoClient.class);
        properties = new MercadoPagoProperties();
        properties.setEnabled(true);
        properties.setCollectorId("99");
        UserDao users = mock(UserDao.class);
        admin = new UserModel();
        admin.setId(7L);
        admin.setRoles(new HashSet<>(Set.of(new Role("ADMIN"))));
        when(users.getUserByEmail("admin@example.com")).thenReturn(Optional.of(admin));
        order = new Order();
        order.setIdOrder(10L);
        order.setOrderStatus(OrderStatus.PENDING);
        order.setPaymentStatus(PaymentStatus.PAID);
        order.setStockDeducted(true);
        order.setProviderPaymentId("PAY123");
        PaymentCheckout checkout = new PaymentCheckout();
        checkout.setReference("checkout");
        checkout.setOrder(order);
        checkout.setAmount(new BigDecimal("100.00"));
        checkout.setCollectorId("99");
        checkout.setCurrency("ARS");
        checkout.setProviderOrderId("ORDTST123");
        payment = new Payment();
        payment.setId(5L);
        payment.setCheckout(checkout);
        payment.setProviderPaymentId("PAY123");
        payment.setProviderStatus("approved");
        payment.setAmount(new BigDecimal("100.00"));
        payment.setRefundedAmount(new BigDecimal("0.00"));
        payment.setCurrency("ARS");
        payment.setCollectorId("99");
        payment.setReviewReason(PaymentReviewReason.NONE);
        when(payments.getById(5L)).thenReturn(Optional.of(payment));
        when(workflow.lockOrder(10L)).thenReturn(order);
        saved = new HashMap<>();
        when(refunds.getByPaymentId(5L)).thenAnswer(call -> saved.values().stream().findFirst());
        when(refunds.getById(anyString()))
                .thenAnswer(call -> Optional.ofNullable(saved.get(call.getArgument(0))));
        doAnswer(
                        call -> {
                            PaymentRefund refund = call.getArgument(0);
                            saved.put(refund.getId(), refund);
                            return null;
                        })
                .when(refunds)
                .save(any());
        when(client.refundPayment(any())).thenReturn(new MercadoPagoClient.RefundReceipt("REF123"));
        persistence =
                new PaymentRefundPersistence(
                        payments,
                        refunds,
                        workflow,
                        users,
                        properties,
                        client,
                        deliveries,
                        new ModelMapperConfig().modelMapper());
    }

    @Test
    void requestIsDurableAndRetryKeepsItsKeyAmountActorAndReason() {
        String id = persistence.prepare("admin@example.com", 5L, " Devolucion acordada ");
        assertEquals(id, persistence.prepare("admin@example.com", 5L, "Otro motivo"));
        var refund = saved.get(id);
        assertEquals(7L, refund.getActorUserId());
        assertEquals("Devolucion acordada", refund.getReason());
        assertEquals(new BigDecimal("100.00"), refund.getAmount());
        assertEquals(PaymentRefundStatus.REQUESTED, refund.getStatus());
        assertEquals(PaymentStatus.REVIEW, order.getPaymentStatus());
        assertTrue(order.isStockDeducted());
        verify(deliveries).revokeOrder(order);
        verifyNoInteractions(client);
        persistence.dispatch("admin@example.com", id);
        persistence.dispatch("admin@example.com", id);
        assertEquals(PaymentRefundStatus.PROCESSING, refund.getStatus());
        assertEquals("REF123", refund.getProviderRefundId());
        verify(client).refundPayment(refund);
        assertEquals(id, persistence.getRefund("admin@example.com", 5L).getId());
        assertEquals(5L, persistence.getRefund("admin@example.com", 5L).getPaymentId());
    }

    @Test
    void refundingAnExtraPaymentDoesNotPauseTheOriginalPurchase() {
        order.setProviderPaymentId("PAYORIGINAL");
        payment.setReviewReason(PaymentReviewReason.DUPLICATE_PAYMENT);
        persistence.prepare("admin@example.com", 5L, "Pago duplicado");
        assertEquals(PaymentStatus.PAID, order.getPaymentStatus());
        assertEquals("PAYORIGINAL", order.getProviderPaymentId());
        assertTrue(order.isStockDeducted());
        verifyNoInteractions(deliveries);
    }

    @Test
    void remainingAmountIsCalculatedOnTheServerAndCannotChangeDuringRetries() {
        payment.setRefundedAmount(new BigDecimal("20.00"));
        payment.setReviewReason(PaymentReviewReason.PARTIAL_REFUND);
        String id = persistence.prepare("admin@example.com", 5L, "Devolver resto");
        assertEquals(new BigDecimal("80.00"), saved.get(id).getAmount());
        payment.setRefundedAmount(new BigDecimal("50.00"));
        assertThrows(
                BusinessConflictException.class,
                () -> persistence.dispatch("admin@example.com", id));
        verifyNoInteractions(client);
    }

    @Test
    void timeoutRetainsTheIntentAndRetryUsesTheSameRequest() {
        String id = persistence.prepare("admin@example.com", 5L, "Devolucion");
        when(client.refundPayment(any())).thenThrow(new ExternalServiceException("Timeout"));
        assertThrows(
                ExternalServiceException.class,
                () -> persistence.dispatch("admin@example.com", id));
        assertEquals(PaymentRefundStatus.REQUESTED, saved.get(id).getStatus());
        assertEquals(id, persistence.prepare("admin@example.com", 5L, "Reintento"));
        doReturn(new MercadoPagoClient.RefundReceipt("REF123")).when(client).refundPayment(any());
        persistence.dispatch("admin@example.com", id);
        verify(client, times(2)).refundPayment(saved.get(id));
    }

    @Test
    void chargebacksUnpaidPaymentsInvalidReasonsAndWrongSellersCannotBeRefunded() {
        payment.setProviderStatus("charged_back");
        payment.setReviewReason(PaymentReviewReason.CHARGEBACK);
        assertThrows(
                BusinessConflictException.class,
                () -> persistence.prepare("admin@example.com", 5L, "Reclamo"));
        payment.setProviderStatus("pending");
        assertThrows(
                BusinessConflictException.class,
                () -> persistence.prepare("admin@example.com", 5L, "Reclamo"));
        assertThrows(
                InvalidRequestException.class,
                () -> persistence.prepare("admin@example.com", 5L, " "));
        payment.setProviderStatus("approved");
        payment.setReviewReason(PaymentReviewReason.NONE);
        properties.setCollectorId("100");
        assertThrows(
                BusinessConflictException.class,
                () -> persistence.prepare("admin@example.com", 5L, "Reclamo"));
        assertTrue(saved.isEmpty());
        verifyNoInteractions(client, deliveries);
    }

    @Test
    void supportAndClientsCannotRequestOrReadRefunds() {
        for (String role : List.of("SUPPORT", "CLIENT")) {
            admin.setRoles(new HashSet<>(Set.of(new Role(role))));
            assertThrows(
                    ForbiddenOperationException.class,
                    () -> persistence.prepare("admin@example.com", 5L, "Reembolso"));
            assertThrows(
                    ForbiddenOperationException.class,
                    () -> persistence.getProviderOrderId("admin@example.com", 5L));
            assertThrows(
                    ForbiddenOperationException.class,
                    () -> persistence.getRefund("admin@example.com", 5L));
        }
        verifyNoInteractions(client, refunds);
    }

    @Test
    void acceptedRefundCanRemainProcessingWhenTheFinalProviderQueryFails() {
        PaymentRefundPersistence storage = mock(PaymentRefundPersistence.class);
        PaymentConfirmationService confirmations = mock(PaymentConfirmationService.class);
        var remote =
                new MercadoPagoClient.VerifiedOrder(
                        "ORDTST123",
                        "checkout",
                        "99",
                        "ARS",
                        new BigDecimal("100.00"),
                        false,
                        List.of());
        when(storage.getProviderOrderId("admin@example.com", 5L)).thenReturn("ORDTST123");
        when(storage.prepare("admin@example.com", 5L, "Devolver")).thenReturn("request");
        when(client.getOrder("ORDTST123"))
                .thenReturn(remote)
                .thenThrow(new ExternalServiceException("Proveedor no disponible"));
        when(confirmations.applyOrder(remote)).thenReturn(List.of());
        PaymentRefundResponseDTO response = new PaymentRefundResponseDTO();
        response.setStatus(PaymentRefundStatus.PROCESSING);
        when(storage.getRefund("admin@example.com", 5L)).thenReturn(response);
        var service = new PaymentRefundService(storage, client, confirmations);
        assertEquals(
                PaymentRefundStatus.PROCESSING,
                service.refund("admin@example.com", 5L, "Devolver").getStatus());
        var sequence = inOrder(storage, client, confirmations);
        sequence.verify(storage).getProviderOrderId("admin@example.com", 5L);
        sequence.verify(client).getOrder("ORDTST123");
        sequence.verify(confirmations).applyOrder(remote);
        sequence.verify(storage).prepare("admin@example.com", 5L, "Devolver");
        sequence.verify(storage).dispatch("admin@example.com", "request");
        sequence.verify(client).getOrder("ORDTST123");
        sequence.verify(storage).getRefund("admin@example.com", 5L);
    }

    @Test
    void unauthorizedRefundNeverQueriesMercadoPago() {
        PaymentRefundPersistence storage = mock(PaymentRefundPersistence.class);
        when(storage.getProviderOrderId("client@example.com", 5L))
                .thenThrow(new ForbiddenOperationException());
        var service =
                new PaymentRefundService(storage, client, mock(PaymentConfirmationService.class));
        assertThrows(
                ForbiddenOperationException.class,
                () -> service.refund("client@example.com", 5L, "Devolver"));
        verifyNoInteractions(client);
    }

    @Test
    void confirmedRequestsNeverSendAnotherRefund() {
        String id = persistence.prepare("admin@example.com", 5L, "Reembolso");
        saved.get(id).setStatus(PaymentRefundStatus.CONFIRMED);
        payment.setProviderStatus("refunded");
        payment.setRefundedAmount(payment.getAmount());
        persistence.dispatch("admin@example.com", id);
        verifyNoInteractions(client);
    }
}

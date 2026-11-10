package com.api.crud;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.api.crud.config.ModelMapperConfig;
import com.api.crud.exceptions.ExternalServiceException;
import com.api.crud.models.entity.*;
import com.api.crud.models.entity.Order;
import com.api.crud.models.enums.*;
import com.api.crud.repositories.*;
import com.api.crud.services.*;
import com.api.crud.services.MercadoPagoClient.VerifiedPayment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

class PaymentConfirmationTests {

    private PaymentDao payments;
    private PaymentRefundDao refunds;
    private OrderWorkflowDao workflow;
    private OrderService orders;
    private DigitalDeliveryService deliveries;
    private PaymentConfirmationService service;
    private Order order;
    private PaymentCheckout checkout;
    private Map<String, Payment> records;
    private Instant version;

    @BeforeEach
    void setUp() {
        payments = mock(PaymentDao.class);
        refunds = mock(PaymentRefundDao.class);
        workflow = mock(OrderWorkflowDao.class);
        orders = mock(OrderService.class);
        deliveries = mock(DigitalDeliveryService.class);
        service =
                new PaymentConfirmationService(
                        payments,
                        workflow,
                        deliveries,
                        orders,
                        new ModelMapperConfig().modelMapper(),
                        refunds);
        version = Instant.now().truncatedTo(ChronoUnit.MICROS);
        order = new Order();
        order.setIdOrder(10L);
        order.setOrderStatus(OrderStatus.PENDING);
        order.setPaymentStatus(PaymentStatus.PENDING);
        order.setStockDeducted(true);
        order.setTotalOrder(new BigDecimal("100.00"));
        checkout = new PaymentCheckout();
        checkout.setReference("checkout-reference");
        checkout.setOrder(order);
        checkout.setCurrency("ARS");
        checkout.setCollectorId("99");
        checkout.setAmount(new BigDecimal("100.00"));
        checkout.setExpiresAt(version.plusSeconds(600));
        records = new HashMap<>();
        when(payments.getCheckoutByReference("checkout-reference"))
                .thenReturn(Optional.of(checkout));
        when(payments.getCheckoutByOrderId(10L)).thenReturn(Optional.of(checkout));
        when(workflow.lockOrder(10L)).thenReturn(order);
        when(payments.getByProviderId(anyString()))
                .thenAnswer(call -> Optional.ofNullable(records.get(call.getArgument(0))));
        when(payments.getByOrderId(10L)).thenAnswer(call -> new ArrayList<>(records.values()));
        doAnswer(
                        call -> {
                            Payment payment = call.getArgument(0);
                            assertNotNull(payment.getProviderStatus());
                            assertNotNull(payment.getAmount());
                            assertNotNull(payment.getReviewReason());
                            payment.setId((long) records.size() + 1);
                            records.put(payment.getProviderPaymentId(), payment);
                            return null;
                        })
                .when(payments)
                .savePayment(any());
    }

    private VerifiedPayment snapshot(
            String id, String status, String amount, String refunded, Instant updated) {
        return new VerifiedPayment(
                id,
                checkout.getReference(),
                "99",
                "ARS",
                new BigDecimal(amount),
                new BigDecimal(refunded),
                status,
                "credit_card",
                "visa",
                version.minusSeconds(10),
                updated,
                false);
    }

    @Test
    void expirationBeforeConfirmationKeepsOrderExpiredAndRestoresStockOnlyOnce() {
        ProductPhysical product = new ProductPhysical();
        product.setIdProduct(4L);
        product.setStockProduct(0);
        order.setPurchaseDateOrder(LocalDateTime.now().minusHours(1));
        order.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        order.setItemsOrder(List.of(new OrderItem(order, product, 1, checkout.getAmount())));
        when(workflow.lockProduct(4L)).thenReturn(product);
        OrderService expiration = new OrderService(
                mock(OrderDao.class), mock(UserDao.class), workflow,
                new ModelMapperConfig().modelMapper(), deliveries,
                mock(OrderShippingService.class), payments);

        assertTrue(expiration.expireOrder(10L));
        VerifiedPayment approved = snapshot("PAY1", "approved", "100.00", "0.00", version);
        service.apply(approved);
        service.apply(approved);

        assertEquals(OrderStatus.EXPIRED, order.getOrderStatus());
        assertEquals(PaymentStatus.REVIEW, order.getPaymentStatus());
        assertEquals(PaymentReviewReason.LATE_APPROVAL, records.get("PAY1").getReviewReason());
        assertEquals(1, product.getStockProduct());
        assertFalse(order.isStockDeducted());
        assertFalse(expiration.expireOrder(10L));
        verify(deliveries, never()).enablePaidOrder(any());
        verify(workflow).lockProduct(4L);
    }

    @Test
    void confirmationBeforeExpirationKeepsPaidOrderAndItsReservedStock() {
        order.setExpiresAt(LocalDateTime.now().plusMinutes(1));
        service.apply(snapshot("PAY1", "approved", "100.00", "0.00", version));
        order.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        OrderService expiration = new OrderService(
                mock(OrderDao.class), mock(UserDao.class), workflow,
                new ModelMapperConfig().modelMapper(), deliveries,
                mock(OrderShippingService.class), payments);

        assertFalse(expiration.expireOrder(10L));
        assertEquals(PaymentStatus.PAID, order.getPaymentStatus());
        assertEquals(OrderStatus.PENDING, order.getOrderStatus());
        assertTrue(order.isStockDeducted());
        verify(workflow, never()).lockProduct(anyLong());
        verify(deliveries).enablePaidOrder(order);
    }

    @Test
    void ordersRecoverTimeoutBindingsAndProcessSeveralAttemptsUnderOneLock() {
        var remote =
                new MercadoPagoClient.VerifiedOrder(
                        "ORDTST123",
                        checkout.getReference(),
                        "99",
                        "ARS",
                        checkout.getAmount(),
                        false,
                        List.of(
                                snapshot("PAY1", "rejected", "100.00", "0.00", version),
                                snapshot("PAY2", "approved", "100.00", "0.00", version)));
        assertEquals(2, service.applyOrder(remote).size());
        assertEquals("ORDTST123", checkout.getProviderOrderId());
        assertEquals("PAY2", order.getProviderPaymentId());
        assertEquals(PaymentStatus.PAID, order.getPaymentStatus());
        verify(workflow).lockOrder(10L);
        service.applyOrder(remote);
        verify(deliveries).enablePaidOrder(order);
        assertEquals(2, records.size());
        verify(workflow, times(2)).saveAudit(any());
    }

    @Test
    void anotherProviderOrderCannotReuseTheSameExternalReference() {
        checkout.setProviderOrderId("ORDTST123");
        var remote =
                new MercadoPagoClient.VerifiedOrder(
                        "ORDTST999",
                        checkout.getReference(),
                        "99",
                        "ARS",
                        checkout.getAmount(),
                        false,
                        List.of(snapshot("PAY1", "approved", "100.00", "0.00", version)));
        assertThrows(
                com.api.crud.exceptions.BusinessConflictException.class,
                () -> service.applyOrder(remote));
        assertTrue(records.isEmpty());
        verifyNoInteractions(deliveries);
    }

    @Test
    void anApprovedPaymentCannotReopenFulfillmentWhileItsRefundIsPending() {
        service.apply(snapshot("PAY1", "approved", "100.00", "0.00", version));
        Payment payment = records.get("PAY1");
        PaymentRefund refund = new PaymentRefund();
        refund.setStatus(PaymentRefundStatus.PROCESSING);
        when(refunds.getByPaymentId(payment.getId())).thenReturn(Optional.of(refund));
        order.setPaymentStatus(PaymentStatus.REVIEW);
        service.apply(snapshot("PAY1", "approved", "100.00", "0.00", version.plusSeconds(1)));
        assertEquals(PaymentStatus.REVIEW, order.getPaymentStatus());
        assertEquals(PaymentReviewReason.NONE, payment.getReviewReason());
        verify(deliveries).enablePaidOrder(order);
    }

    @Test
    void verifiedRefundOfDuplicateConfirmsTheRequestWithoutRefundingTheOriginalPurchase() {
        service.apply(snapshot("PAY1", "approved", "100.00", "0.00", version));
        service.apply(snapshot("PAY2", "approved", "100.00", "0.00", version));
        Payment extra = records.get("PAY2");
        PaymentRefund refund = new PaymentRefund();
        refund.setId("request");
        refund.setAmount(new BigDecimal("100.00"));
        refund.setPreviouslyRefundedAmount(new BigDecimal("0.00"));
        refund.setStatus(PaymentRefundStatus.PROCESSING);
        when(refunds.getByPaymentId(extra.getId())).thenReturn(Optional.of(refund));
        var refunded = snapshot("PAY2", "refunded", "100.00", "100.00", version.plusSeconds(1));
        service.apply(refunded);
        service.apply(refunded);
        assertEquals(PaymentRefundStatus.CONFIRMED, refund.getStatus());
        assertNotNull(refund.getConfirmedAt());
        assertEquals(PaymentStatus.PAID, order.getPaymentStatus());
        assertEquals("PAY1", order.getProviderPaymentId());
        assertTrue(order.isStockDeducted());
        verify(deliveries, never()).revokeOrder(any());
        verify(orders, never()).cancelRefundedOrder(any());
        verify(workflow, times(4)).saveAudit(any());
    }

    @Test
    void duplicateApprovalHasOnePaymentAndOneDeliveryEffect() {
        var approved = snapshot("123", "approved", "100.00", "0.00", version);
        service.apply(approved);
        service.apply(approved);
        assertEquals(PaymentStatus.PAID, order.getPaymentStatus());
        assertEquals("123", order.getProviderPaymentId());
        assertEquals(1, records.size());
        verify(deliveries).enablePaidOrder(order);
        verify(workflow).saveAudit(any());
    }

    @Test
    void adminSynchronizationRecoversMissedNotificationAndCanBeRepeated() {
        UserDao users = mock(UserDao.class);
        UserModel admin = mock(UserModel.class);
        when(admin.getRoleNames()).thenReturn(List.of("ADMIN"));
        when(users.getUserByEmail("admin@example.com")).thenReturn(Optional.of(admin));
        MercadoPagoClient client = mock(MercadoPagoClient.class);
        var remote = new MercadoPagoClient.VerifiedOrder(
                "ORDTST123", checkout.getReference(), "99", "ARS",
                checkout.getAmount(), false,
                List.of(snapshot("PAY1", "approved", "100.00", "0.00", version)));
        when(client.getOrder("ORDTST123"))
                .thenThrow(new ExternalServiceException("Conexion temporalmente no disponible"))
                .thenReturn(remote);
        PaymentService coordinator = new PaymentService(
                mock(PaymentCheckoutPersistence.class), service, client,
                mock(MercadoPagoWebhookVerifier.class), payments,
                mock(OrderDao.class), users, new ModelMapperConfig().modelMapper());

        assertThrows(ExternalServiceException.class,
                () -> coordinator.synchronize("admin@example.com", "ORDTST123"));
        assertEquals(PaymentStatus.PENDING, order.getPaymentStatus());
        assertTrue(records.isEmpty());
        coordinator.synchronize("admin@example.com", "ORDTST123");
        coordinator.synchronize("admin@example.com", "ORDTST123");
        assertEquals(PaymentStatus.PAID, order.getPaymentStatus());
        assertEquals(1, records.size());
        verify(deliveries).enablePaidOrder(order);
        verify(workflow).saveAudit(any());
    }

    @Test
    void rejectedAttemptDoesNotPreventAnotherAttemptFromPaying() {
        var rejected = snapshot("122", "rejected", "100.00", "0.00", version);
        service.apply(rejected);
        service.apply(rejected);
        assertEquals(PaymentStatus.FAILED, order.getPaymentStatus());
        assertEquals(OrderStatus.PENDING, order.getOrderStatus());
        assertTrue(order.isStockDeducted());
        verify(deliveries, never()).enablePaidOrder(any());
        verify(workflow).saveAudit(any());
        service.apply(snapshot("123", "approved", "100.00", "0.00", version));
        service.apply(rejected);
        assertEquals(PaymentStatus.PAID, order.getPaymentStatus());
        assertEquals(2, records.size());
        verify(deliveries).enablePaidOrder(order);
    }

    @Test
    void secondApprovedPaymentIsFlaggedWithoutDeliveringTwice() {
        service.apply(snapshot("123", "approved", "100.00", "0.00", version));
        var result = service.apply(snapshot("124", "approved", "100.00", "0.00", version));
        assertEquals(PaymentReviewReason.DUPLICATE_PAYMENT, result.getReviewReason());
        assertEquals("123", order.getProviderPaymentId());
        verify(deliveries).enablePaidOrder(order);
    }

    @Test
    void oldNotificationCannotUndoARefundOrGrantAccessAgain() {
        var approved = snapshot("123", "approved", "100.00", "0.00", version);
        service.apply(approved);
        service.apply(snapshot("123", "refunded", "100.00", "100.00", version.plusSeconds(1)));
        service.apply(approved);
        assertEquals(PaymentStatus.REFUNDED, order.getPaymentStatus());
        verify(deliveries).enablePaidOrder(order);
        verify(deliveries).revokeOrder(order);
        verify(orders).cancelRefundedOrder(order);
    }

    @Test
    void lateApprovalDoesNotRecreateExpiredInventoryOrGrantAccess() {
        order.setOrderStatus(OrderStatus.EXPIRED);
        order.setStockDeducted(false);
        var result = service.apply(snapshot("123", "approved", "100.00", "0.00", version));
        assertEquals(PaymentReviewReason.LATE_APPROVAL, result.getReviewReason());
        assertEquals(PaymentStatus.REVIEW, order.getPaymentStatus());
        assertFalse(order.isStockDeducted());
        assertEquals(OrderStatus.EXPIRED, order.getOrderStatus());
        verify(deliveries, never()).enablePaidOrder(any());
    }

    @Test
    void amountCurrencyCollectorAndModeMustMatchTheCheckout() {
        var wrongAmount = service.apply(snapshot("123", "approved", "99.00", "0.00", version));
        assertEquals(PaymentReviewReason.AMOUNT_MISMATCH, wrongAmount.getReviewReason());
        var currency =
                new VerifiedPayment(
                        "124",
                        checkout.getReference(),
                        "99",
                        "USD",
                        new BigDecimal("100.00"),
                        BigDecimal.ZERO,
                        "approved",
                        "credit_card",
                        "visa",
                        version,
                        version,
                        false);
        assertEquals(
                PaymentReviewReason.CURRENCY_MISMATCH, service.apply(currency).getReviewReason());
        var collector =
                new VerifiedPayment(
                        "125",
                        checkout.getReference(),
                        "98",
                        "ARS",
                        new BigDecimal("100.00"),
                        BigDecimal.ZERO,
                        "approved",
                        "credit_card",
                        "visa",
                        version,
                        version,
                        false);
        assertEquals(
                PaymentReviewReason.COLLECTOR_MISMATCH, service.apply(collector).getReviewReason());
        var mode =
                new VerifiedPayment(
                        "126",
                        checkout.getReference(),
                        "99",
                        "ARS",
                        new BigDecimal("100.00"),
                        BigDecimal.ZERO,
                        "approved",
                        "credit_card",
                        "visa",
                        version,
                        version,
                        true);
        assertEquals(PaymentReviewReason.MODE_MISMATCH, service.apply(mode).getReviewReason());
        assertEquals(PaymentStatus.PENDING, order.getPaymentStatus());
        verifyNoInteractions(deliveries);
    }

    @Test
    void partialRefundBlocksFulfillmentUntilAFullRefundIsVerified() {
        service.apply(snapshot("123", "approved", "100.00", "0.00", version));
        var partial =
                service.apply(
                        snapshot("123", "approved", "100.00", "25.00", version.plusSeconds(1)));
        assertEquals(PaymentReviewReason.PARTIAL_REFUND, partial.getReviewReason());
        assertEquals(PaymentStatus.REVIEW, order.getPaymentStatus());
        service.apply(snapshot("123", "refunded", "100.00", "100.00", version.plusSeconds(2)));
        assertEquals(PaymentStatus.REFUNDED, order.getPaymentStatus());
        verify(orders).cancelRefundedOrder(order);
    }

    @Test
    void chargebackRevokesAccessAndUnknownReferenceDoesNotTouchOrders() {
        service.apply(snapshot("123", "approved", "100.00", "0.00", version));
        service.apply(snapshot("123", "charged_back", "100.00", "0.00", version.plusSeconds(1)));
        assertEquals(PaymentStatus.REVIEW, order.getPaymentStatus());
        verify(deliveries).revokeOrder(order);
        clearInvocations(workflow);
        var unknown =
                new VerifiedPayment(
                        "999",
                        "unrelated-application",
                        "99",
                        "ARS",
                        new BigDecimal("100.00"),
                        BigDecimal.ZERO,
                        "approved",
                        "credit_card",
                        "visa",
                        version,
                        version,
                        false);
        assertNull(service.apply(unknown));
        verifyNoInteractions(workflow);
    }
}

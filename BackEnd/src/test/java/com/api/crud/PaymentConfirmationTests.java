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
import com.api.crud.services.ChargebackService;
import com.api.crud.services.MercadoPagoClient.VerifiedPayment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

class PaymentConfirmationTests {

    private PaymentDao payments;
    private PaymentRefundDao refunds;
    private ChargebackDao chargebackRecords;
    private List<ChargebackCase> chargebackHistory;
    private PaymentRefundAttemptDao attempts;
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
        attempts = mock(PaymentRefundAttemptDao.class);
        chargebackRecords = mock(ChargebackDao.class);
        chargebackHistory = new ArrayList<>();
        when(chargebackRecords.getByPaymentId(anyLong()))
                .thenAnswer(call -> List.copyOf(chargebackHistory));
        doAnswer(
                        call -> {
                            ChargebackCase record = call.getArgument(0);
                            record.setId((long) chargebackHistory.size() + 1);
                            chargebackHistory.add(record);
                            return null;
                        })
                .when(chargebackRecords)
                .save(any());
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
                        refunds,
                        attempts,
                        new ChargebackLifecycle(
                                chargebackRecords, refunds, workflow, orders, deliveries));
        version = Instant.now().truncatedTo(ChronoUnit.MICROS);
        order = new Order();
        order.setIdOrder(10L);
        order.setItemsOrder(List.of());
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
        OrderService expiration =
                new OrderService(
                        mock(OrderDao.class),
                        mock(UserDao.class),
                        workflow,
                        new ModelMapperConfig().modelMapper(),
                        deliveries,
                        mock(OrderShippingService.class),
                        payments);

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
        OrderService expiration =
                new OrderService(
                        mock(OrderDao.class),
                        mock(UserDao.class),
                        workflow,
                        new ModelMapperConfig().modelMapper(),
                        deliveries,
                        mock(OrderShippingService.class),
                        payments);

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
        PaymentRefundAttempt attempt = new PaymentRefundAttempt();
        attempt.setId(refund.getId());
        attempt.setRefund(refund);
        attempt.setStatus(PaymentRefundStatus.PROCESSING);
        attempt.setProviderRefundId("REF123");
        when(attempts.getById(refund.getId())).thenReturn(Optional.of(attempt));
        var base = snapshot("PAY2", "refunded", "100.00", "100.00", version.plusSeconds(1));
        var refunded =
                new VerifiedPayment(
                        base.id(),
                        base.reference(),
                        base.collectorId(),
                        base.currency(),
                        base.amount(),
                        base.refundedAmount(),
                        base.status(),
                        base.paymentType(),
                        base.paymentMethod(),
                        base.approvedAt(),
                        base.updatedAt(),
                        base.liveMode(),
                        Set.of("REF123"));
        service.apply(refunded);
        service.apply(refunded);
        assertEquals(PaymentRefundStatus.CONFIRMED, refund.getStatus());
        assertNotNull(refund.getConfirmedAt());
        assertEquals(PaymentRefundStatus.CONFIRMED, attempt.getStatus());
        assertNotNull(attempt.getConfirmedAt());
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
    void moneyReturnedByAnotherRefundDoesNotConfirmAnUnrelatedAttempt() {
        service.apply(snapshot("PAY1", "approved", "100.00", "0.00", version));
        Payment payment = records.get("PAY1");
        PaymentRefund refund = new PaymentRefund();
        refund.setId("request");
        refund.setAmount(new BigDecimal("100.00"));
        refund.setPreviouslyRefundedAmount(BigDecimal.ZERO);
        refund.setStatus(PaymentRefundStatus.PROCESSING);
        PaymentRefundAttempt attempt = new PaymentRefundAttempt();
        attempt.setId("request");
        attempt.setRefund(refund);
        attempt.setStatus(PaymentRefundStatus.PROCESSING);
        attempt.setProviderRefundId("REFCURRENT");
        when(refunds.getByPaymentId(payment.getId())).thenReturn(Optional.of(refund));
        when(attempts.getById("request")).thenReturn(Optional.of(attempt));
        var base = snapshot("PAY1", "refunded", "100.00", "100.00", version.plusSeconds(1));
        var external =
                new VerifiedPayment(
                        base.id(),
                        base.reference(),
                        base.collectorId(),
                        base.currency(),
                        base.amount(),
                        base.refundedAmount(),
                        base.status(),
                        base.paymentType(),
                        base.paymentMethod(),
                        base.approvedAt(),
                        base.updatedAt(),
                        base.liveMode(),
                        Set.of("REFOTHER"));
        service.apply(external);
        service.apply(external);
        assertEquals(PaymentRefundStatus.CONFIRMED, refund.getStatus());
        assertEquals(PaymentStatus.REFUNDED, order.getPaymentStatus());
        assertEquals(PaymentRefundStatus.PROCESSING, attempt.getStatus());
        assertNull(attempt.getConfirmedAt());
        verify(orders).cancelRefundedOrder(order);
    }

    @Test
    void matchingProviderReferenceCanConfirmAttemptAfterTheFinancialRequestWasClosed() {
        service.apply(snapshot("PAY1", "approved", "100.00", "0.00", version));
        Payment payment = records.get("PAY1");
        PaymentRefund refund = new PaymentRefund();
        refund.setId("request");
        refund.setAmount(new BigDecimal("100.00"));
        refund.setPreviouslyRefundedAmount(BigDecimal.ZERO);
        refund.setStatus(PaymentRefundStatus.PROCESSING);
        PaymentRefundAttempt attempt = new PaymentRefundAttempt();
        attempt.setId("request");
        attempt.setRefund(refund);
        attempt.setStatus(PaymentRefundStatus.PROCESSING);
        attempt.setProviderRefundId("REFCURRENT");
        when(refunds.getByPaymentId(payment.getId())).thenReturn(Optional.of(refund));
        when(attempts.getById("request")).thenReturn(Optional.of(attempt));
        var base = snapshot("PAY1", "refunded", "100.00", "100.00", version.plusSeconds(1));
        service.apply(base);
        Instant financiallyConfirmedAt = refund.getConfirmedAt();
        assertEquals(PaymentRefundStatus.PROCESSING, attempt.getStatus());
        var identified =
                new VerifiedPayment(
                        base.id(),
                        base.reference(),
                        base.collectorId(),
                        base.currency(),
                        base.amount(),
                        base.refundedAmount(),
                        base.status(),
                        base.paymentType(),
                        base.paymentMethod(),
                        base.approvedAt(),
                        base.updatedAt(),
                        base.liveMode(),
                        Set.of("REFCURRENT"));
        service.apply(identified);
        service.apply(identified);
        assertEquals(PaymentRefundStatus.CONFIRMED, attempt.getStatus());
        assertNotNull(attempt.getConfirmedAt());
        assertEquals(financiallyConfirmedAt, refund.getConfirmedAt());
        verify(orders).cancelRefundedOrder(order);
        verify(workflow)
                .saveAudit(argThat(audit -> "REFUND_ATTEMPT_CONFIRMED".equals(audit.getAction())));
    }

    @Test
    void rejectedRefundStillBlocksDeliveryButVerifiedMoneyReturnedCanConfirmItLater() {
        service.apply(snapshot("PAY1", "approved", "100.00", "0.00", version));
        order.setPaymentStatus(PaymentStatus.REVIEW);
        PaymentRefund refund = new PaymentRefund();
        refund.setId("refund-request");
        refund.setPayment(records.get("PAY1"));
        refund.setStatus(PaymentRefundStatus.REJECTED);
        refund.setAmount(new BigDecimal("100.00"));
        refund.setPreviouslyRefundedAmount(BigDecimal.ZERO);
        refund.setRejectedAt(version);
        refund.setRejectionCode("cannot_refund_order");
        PaymentRefundAttempt rejected = new PaymentRefundAttempt();
        rejected.setId(refund.getId());
        rejected.setRefund(refund);
        rejected.setStatus(PaymentRefundStatus.REJECTED);
        rejected.setRejectedAt(version);
        when(attempts.getById(refund.getId())).thenReturn(Optional.of(rejected));
        when(refunds.getByPaymentId(records.get("PAY1").getId())).thenReturn(Optional.of(refund));
        service.apply(snapshot("PAY1", "approved", "100.00", "0.00", version.plusSeconds(1)));
        assertEquals(PaymentStatus.REVIEW, order.getPaymentStatus());
        assertEquals(PaymentRefundStatus.REJECTED, refund.getStatus());
        service.apply(snapshot("PAY1", "refunded", "100.00", "100.00", version.plusSeconds(2)));
        assertEquals(PaymentRefundStatus.CONFIRMED, refund.getStatus());
        assertNotNull(refund.getConfirmedAt());
        assertEquals(version, refund.getRejectedAt());
        assertEquals("cannot_refund_order", refund.getRejectionCode());
        assertEquals(PaymentRefundStatus.REJECTED, rejected.getStatus());
        assertNull(rejected.getConfirmedAt());
        verify(deliveries).enablePaidOrder(order);
        verify(orders).cancelRefundedOrder(order);
    }

    @Test
    void adminSynchronizationRecoversMissedNotificationAndCanBeRepeated() {
        UserDao users = mock(UserDao.class);
        UserModel admin = mock(UserModel.class);
        when(admin.getRoleNames()).thenReturn(List.of("ADMIN"));
        when(users.getUserByEmail("admin@example.com")).thenReturn(Optional.of(admin));
        MercadoPagoClient client = mock(MercadoPagoClient.class);
        var remote =
                new MercadoPagoClient.VerifiedOrder(
                        "ORDTST123",
                        checkout.getReference(),
                        "99",
                        "ARS",
                        checkout.getAmount(),
                        false,
                        List.of(snapshot("PAY1", "approved", "100.00", "0.00", version)));
        when(client.getOrder("ORDTST123"))
                .thenThrow(new ExternalServiceException("Conexion temporalmente no disponible"))
                .thenReturn(remote);
        PaymentService coordinator =
                new PaymentService(
                        mock(PaymentCheckoutPersistence.class),
                        service,
                        client,
                        mock(MercadoPagoWebhookVerifier.class),
                        payments,
                        mock(OrderDao.class),
                        users,
                        new ModelMapperConfig().modelMapper(),
                        mock(ChargebackService.class));

        assertThrows(
                ExternalServiceException.class,
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
        assertEquals("123", order.getProviderPaymentId());
        assertEquals("rejected", records.get("122").getProviderStatus());
        assertNotNull(records.get("123").getAcceptedAt());
        assertTrue(order.isStockDeducted());
        verify(workflow, never()).lockProduct(anyLong());
        verify(orders, never()).expireReservedOrder(any());
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

    @Test
    void verifiedItemRefundKeepsRemainingPurchaseUsableAndRepeatedEventDoesNotRefundUnitsTwice() {
        service.apply(snapshot("PAY123", "approved", "100.00", "0.00", version));
        Payment payment = records.get("PAY123");
        var item = new OrderItem(order, new ProductPhysical(), 2, new BigDecimal("50.00"));
        item.setIdOrderItem(20L);
        item.setRefundRequestedQuantity(1);
        item.setCanceledQuantity(1);
        order.setItemsOrder(List.of(item));
        var allocation = new RefundItemAllocation();
        allocation.setOrderItemId(20L);
        allocation.setQuantity(1);
        allocation.setUnitPrice(new BigDecimal("50.00"));
        var refund = new PaymentRefund();
        refund.setId(UUID.randomUUID().toString());
        refund.setPayment(payment);
        refund.setItemized(true);
        refund.setAmount(new BigDecimal("50.00"));
        refund.setPreviouslyRefundedAmount(BigDecimal.ZERO);
        refund.setStatus(PaymentRefundStatus.PROCESSING);
        refund.setProviderRefundId("REF123");
        refund.setItems(List.of(allocation));
        when(refunds.getByPaymentId(payment.getId())).thenReturn(Optional.of(refund));
        when(refunds.getAllByPaymentId(payment.getId())).thenReturn(List.of(refund));
        order.setPaymentStatus(PaymentStatus.REVIEW);
        var remote =
                new VerifiedPayment(
                        "PAY123",
                        checkout.getReference(),
                        "99",
                        "ARS",
                        new BigDecimal("100.00"),
                        new BigDecimal("50.00"),
                        "approved",
                        "credit_card",
                        "visa",
                        version.minusSeconds(10),
                        version.plusSeconds(1),
                        false,
                        Set.of("REF123"));
        service.apply(remote);
        assertEquals(PaymentRefundStatus.CONFIRMED, refund.getStatus());
        assertEquals(PaymentStatus.PARTIALLY_REFUNDED, order.getPaymentStatus());
        assertEquals(PaymentReviewReason.NONE, payment.getReviewReason());
        assertEquals(1, item.getRefundedQuantity());
        service.apply(remote);
        assertEquals(1, item.getRefundedQuantity());
        assertEquals(1, item.getFulfillmentQuantity());
        verify(orders, never()).cancelRefundedOrder(any());
        verify(deliveries, never()).revokeOrder(any());
        service.apply(
                snapshot("PAY123", "charged_back", "100.00", "50.00", version.plusSeconds(2)));
        assertEquals(PaymentStatus.REVIEW, order.getPaymentStatus());
        service.apply(remote);
        assertEquals(PaymentStatus.REVIEW, order.getPaymentStatus());
        assertEquals(PaymentReviewReason.CHARGEBACK, payment.getReviewReason());
        service.apply(snapshot("PAY123", "approved", "100.00", "50.00", version.plusSeconds(3)));
        assertEquals(PaymentStatus.REVIEW, order.getPaymentStatus());
        assertEquals(PaymentReviewReason.CHARGEBACK, payment.getReviewReason());
    }

    @Test
    void
            caseResolutionCanSettleAnUnchangedPaymentSnapshotAndRecoveryNeverReopensCanceledFulfillment() {
        service.apply(snapshot("PAY123", "approved", "100.00", "0.00", version));
        doAnswer(
                        call -> {
                            order.setOrderStatus(OrderStatus.CANCELED);
                            order.setStockDeducted(false);
                            return null;
                        })
                .when(orders)
                .cancelChargebackOrder(order);
        var disputed =
                new VerifiedPayment(
                        "PAY123",
                        checkout.getReference(),
                        "99",
                        "ARS",
                        new BigDecimal("100.00"),
                        BigDecimal.ZERO,
                        "charged_back",
                        "credit_card",
                        "visa",
                        version,
                        version.plusSeconds(1),
                        false,
                        Set.of(),
                        "in_process",
                        "123456",
                        Set.of("9988"));
        service.apply(disputed);
        service.apply(disputed);
        assertEquals(1, chargebackHistory.size());
        assertEquals("123456", records.get("PAY123").getProviderReferenceId());
        chargebackHistory.get(0).setResolution(ChargebackResolution.LOST);
        service.apply(disputed);
        assertEquals(PaymentStatus.CHARGED_BACK, order.getPaymentStatus());
        chargebackHistory.get(0).setResolution(ChargebackResolution.WON);
        var recovered =
                new VerifiedPayment(
                        "PAY123",
                        checkout.getReference(),
                        "99",
                        "ARS",
                        new BigDecimal("100.00"),
                        BigDecimal.ZERO,
                        "charged_back",
                        "credit_card",
                        "visa",
                        version,
                        version.plusSeconds(2),
                        false,
                        Set.of(),
                        "reimbursed",
                        "123456",
                        Set.of("9988"));
        service.apply(recovered);
        assertEquals(OrderStatus.CANCELED, order.getOrderStatus());
        assertEquals(PaymentStatus.REVIEW, order.getPaymentStatus());
        assertEquals(PaymentReviewReason.LATE_APPROVAL, records.get("PAY123").getReviewReason());
        var refunded =
                new VerifiedPayment(
                        "PAY123",
                        checkout.getReference(),
                        "99",
                        "ARS",
                        new BigDecimal("100.00"),
                        new BigDecimal("100.00"),
                        "charged_back",
                        "credit_card",
                        "visa",
                        version,
                        version.plusSeconds(3),
                        false,
                        Set.of("REF1"),
                        "reimbursed",
                        "123456",
                        Set.of("9988"));
        service.apply(refunded);
        assertEquals(PaymentStatus.REFUNDED, order.getPaymentStatus());
        assertEquals(OrderStatus.CANCELED, order.getOrderStatus());
        assertFalse(order.isStockDeducted());
    }

    @ParameterizedTest
    @EnumSource(
            value = OrderStatus.class,
            names = {"PENDING", "CONFIRMED", "SHIPPED", "DELIVERED"})
    void correctedVerifiedPaymentRestoresAnAcceptedPurchaseWithoutChangingFulfillment(
            OrderStatus status) {
        service.apply(snapshot("PAY1", "approved", "100.00", "0.00", version));
        Payment payment = records.get("PAY1");
        Instant accepted = payment.getAcceptedAt();
        assertNotNull(accepted);
        order.setOrderStatus(status);
        service.apply(snapshot("PAY1", "approved", "90.00", "0.00", version.plusSeconds(1)));
        assertEquals(PaymentStatus.REVIEW, order.getPaymentStatus());
        assertEquals(PaymentReviewReason.AMOUNT_MISMATCH, payment.getReviewReason());
        var corrected = snapshot("PAY1", "approved", "100.00", "0.00", version.plusSeconds(2));
        service.apply(corrected);
        assertEquals(PaymentStatus.PAID, order.getPaymentStatus());
        assertEquals(PaymentReviewReason.NONE, payment.getReviewReason());
        assertEquals(status, order.getOrderStatus());
        assertEquals(accepted, payment.getAcceptedAt());
        assertTrue(order.isStockDeducted());
        service.apply(corrected);
        verify(deliveries).restorePaidOrderAccess(order);
        verify(deliveries).enablePaidOrder(order);
        verify(workflow, never()).lockProduct(anyLong());
        var audits = org.mockito.ArgumentCaptor.forClass(OrderChangeAudit.class);
        verify(workflow, atLeastOnce()).saveAudit(audits.capture());
        assertEquals(
                1,
                audits.getAllValues().stream()
                        .filter(audit -> "PAYMENT_REVIEW_RESOLVED".equals(audit.getAction()))
                        .count());
    }

    @Test
    void recoveryUsesOriginalAcceptanceNotTheNewProviderTimestampOrCheckoutDeadline() {
        service.apply(snapshot("PAY1", "approved", "100.00", "0.00", version));
        service.apply(snapshot("PAY1", "in_process", "100.00", "0.00", version.plusSeconds(1)));
        checkout.setExpiresAt(version.minusSeconds(1));
        var corrected =
                new VerifiedPayment(
                        "PAY1",
                        checkout.getReference(),
                        "99",
                        "ARS",
                        new BigDecimal("100.00"),
                        BigDecimal.ZERO,
                        "approved",
                        "credit_card",
                        "visa",
                        version.plusSeconds(2),
                        version.plusSeconds(2),
                        false);
        service.apply(corrected);
        assertEquals(PaymentStatus.PAID, order.getPaymentStatus());
        verify(deliveries).restorePaidOrderAccess(order);
        verify(orders, never()).expireReservedOrder(any());
    }

    @Test
    void providerBindingWithoutPriorAcceptanceCannotForcePaid() {
        service.apply(snapshot("PAY1", "pending", "100.00", "0.00", version));
        order.setProviderPaymentId("PAY1");
        order.setPaymentStatus(PaymentStatus.REVIEW);
        service.apply(snapshot("PAY1", "approved", "100.00", "0.00", version.plusSeconds(1)));
        assertNull(records.get("PAY1").getAcceptedAt());
        assertEquals(PaymentStatus.REVIEW, order.getPaymentStatus());
        assertEquals(
                PaymentReviewReason.INVALID_PROVIDER_STATE, records.get("PAY1").getReviewReason());
        verifyNoInteractions(deliveries);
    }

    @ParameterizedTest
    @EnumSource(
            value = OrderStatus.class,
            names = {"CANCELED", "EXPIRED"})
    void correctedMoneyNeverReopensACanceledOrExpiredPurchase(OrderStatus status) {
        service.apply(snapshot("PAY1", "approved", "100.00", "0.00", version));
        order.setOrderStatus(status);
        order.setStockDeducted(false);
        order.setPaymentStatus(PaymentStatus.REVIEW);
        service.apply(snapshot("PAY1", "approved", "100.00", "0.00", version.plusSeconds(1)));
        assertEquals(status, order.getOrderStatus());
        assertEquals(PaymentStatus.REVIEW, order.getPaymentStatus());
        assertEquals(PaymentReviewReason.LATE_APPROVAL, records.get("PAY1").getReviewReason());
        assertFalse(order.isStockDeducted());
        verify(deliveries, never()).restorePaidOrderAccess(any());
    }

    @Test
    void releasedInventoryStillBlocksRecoveryOfAnAcceptedPurchase() {
        service.apply(snapshot("PAY1", "approved", "100.00", "0.00", version));
        order.setPaymentStatus(PaymentStatus.REVIEW);
        order.setStockDeducted(false);
        service.apply(snapshot("PAY1", "approved", "100.00", "0.00", version.plusSeconds(1)));
        assertEquals(PaymentStatus.REVIEW, order.getPaymentStatus());
        verify(deliveries, never()).restorePaidOrderAccess(any());
    }

    @Test
    void sameSnapshotCanCloseExplainedPartialReviewButNeverAnExternalUnknownPartial() {
        service.apply(snapshot("PAY1", "approved", "100.00", "0.00", version));
        var partial = snapshot("PAY1", "approved", "100.00", "20.00", version.plusSeconds(1));
        service.apply(partial);
        service.apply(partial);
        assertEquals(PaymentStatus.REVIEW, order.getPaymentStatus());
        verify(deliveries, never()).restorePaidOrderAccess(any());
        PaymentRefund refund = new PaymentRefund();
        refund.setId("partial");
        refund.setItemized(true);
        refund.setAmount(new BigDecimal("20.00"));
        refund.setPreviouslyRefundedAmount(BigDecimal.ZERO);
        refund.setStatus(PaymentRefundStatus.CONFIRMED);
        when(refunds.getAllByPaymentId(1L)).thenReturn(List.of(refund));
        when(refunds.getByPaymentId(1L)).thenReturn(Optional.of(refund));
        service.apply(partial);
        assertEquals(PaymentStatus.PARTIALLY_REFUNDED, order.getPaymentStatus());
        service.apply(partial);
        verify(deliveries).restorePaidOrderAccess(order);
    }

    @Test
    void unchangedPaymentCannotBypassAPendingOrFullRefundOrAMismatchedOrderTotal() {
        var approved = snapshot("PAY1", "approved", "100.00", "0.00", version);
        service.apply(approved);
        order.setPaymentStatus(PaymentStatus.REVIEW);
        PaymentRefund refund = new PaymentRefund();
        refund.setItemized(true);
        refund.setStatus(PaymentRefundStatus.REJECTED);
        when(refunds.getAllByPaymentId(1L)).thenReturn(List.of(refund));
        service.apply(approved);
        assertEquals(PaymentStatus.REVIEW, order.getPaymentStatus());
        refund.setItemized(false);
        refund.setStatus(PaymentRefundStatus.CONFIRMED);
        service.apply(approved);
        assertEquals(PaymentStatus.REVIEW, order.getPaymentStatus());
        when(refunds.getAllByPaymentId(1L)).thenReturn(List.of());
        order.setTotalOrder(new BigDecimal("99.00"));
        service.apply(approved);
        assertEquals(PaymentStatus.REVIEW, order.getPaymentStatus());
        verify(deliveries, never()).restorePaidOrderAccess(any());
    }

    @ParameterizedTest
    @EnumSource(
            value = PaymentReviewReason.class,
            names = {
                "CURRENCY_MISMATCH",
                "COLLECTOR_MISMATCH",
                "MODE_MISMATCH",
                "UNSUPPORTED_METHOD",
                "INVALID_PROVIDER_STATE"
            })
    void otherReviewCausesRequireCorrectedCanonicalDataBeforeRecovery(PaymentReviewReason issue) {
        service.apply(snapshot("PAY1", "approved", "100.00", "0.00", version));
        var invalid =
                new VerifiedPayment(
                        "PAY1",
                        checkout.getReference(),
                        issue == PaymentReviewReason.COLLECTOR_MISMATCH ? "other" : "99",
                        issue == PaymentReviewReason.CURRENCY_MISMATCH ? "USD" : "ARS",
                        new BigDecimal("100.00"),
                        BigDecimal.ZERO,
                        "approved",
                        issue == PaymentReviewReason.UNSUPPORTED_METHOD ? "ticket" : "credit_card",
                        "visa",
                        issue == PaymentReviewReason.INVALID_PROVIDER_STATE
                                ? version.plusSeconds(120)
                                : version.minusSeconds(10),
                        version.plusSeconds(1),
                        issue == PaymentReviewReason.MODE_MISMATCH);
        service.apply(invalid);
        assertEquals(PaymentStatus.REVIEW, order.getPaymentStatus());
        assertEquals(issue, records.get("PAY1").getReviewReason());
        service.apply(invalid);
        verify(deliveries, never()).restorePaidOrderAccess(any());
        service.apply(snapshot("PAY1", "approved", "100.00", "0.00", version.plusSeconds(2)));
        assertEquals(PaymentStatus.PAID, order.getPaymentStatus());
        verify(deliveries).restorePaidOrderAccess(order);
    }
}

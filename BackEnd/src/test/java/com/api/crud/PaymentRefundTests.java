package com.api.crud;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.api.crud.DTO.ItemRefundLineRequestDTO;
import com.api.crud.DTO.ItemRefundRequestDTO;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

class PaymentRefundTests {

    private PaymentDao payments;
    private PaymentRefundDao refunds;
    private PaymentRefundAttemptDao attempts;
    private Map<String, PaymentRefundAttempt> attemptRecords;
    private OrderWorkflowDao workflow;
    private DigitalDeliveryService deliveries;
    private PhysicalReturnService physicalReturns;
    private MercadoPagoClient client;
    private PaymentRefundPersistence persistence;
    private MercadoPagoProperties properties;
    private UserModel admin;
    private Order order;
    private Payment payment;
    private Map<String, PaymentRefund> saved;

    private MercadoPagoClient.VerifiedOrder unchangedProviderOrder(Set<String> unsettled) {
        var snapshot =
                new MercadoPagoClient.VerifiedPayment(
                        "PAY123",
                        "checkout",
                        "99",
                        "ARS",
                        new BigDecimal("100.00"),
                        BigDecimal.ZERO,
                        "approved",
                        "credit_card",
                        "visa",
                        Instant.now().minusSeconds(10),
                        Instant.now(),
                        false);
        return new MercadoPagoClient.VerifiedOrder(
                "ORDTST123",
                "checkout",
                "99",
                "ARS",
                new BigDecimal("100.00"),
                false,
                List.of(snapshot),
                unsettled);
    }

    @BeforeEach
    void setUp() {
        payments = mock(PaymentDao.class);
        refunds = mock(PaymentRefundDao.class);
        attempts = mock(PaymentRefundAttemptDao.class);
        attemptRecords = new HashMap<>();
        when(attempts.getById(anyString()))
                .thenAnswer(call -> Optional.ofNullable(attemptRecords.get(call.getArgument(0))));
        doAnswer(
                        call -> {
                            PaymentRefundAttempt attempt = call.getArgument(0);
                            attemptRecords.put(attempt.getId(), attempt);
                            return null;
                        })
                .when(attempts)
                .save(any());
        when(attempts.getByRefundId(anyString(), any()))
                .thenAnswer(
                        call ->
                                new PageImpl<>(
                                        attemptRecords.values().stream()
                                                .filter(
                                                        attempt ->
                                                                attempt.getRefund()
                                                                        .getId()
                                                                        .equals(
                                                                                call.getArgument(
                                                                                        0)))
                                                .sorted(
                                                        Comparator.comparingInt(
                                                                        PaymentRefundAttempt
                                                                                ::getAttemptNumber)
                                                                .reversed())
                                                .toList()));
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
        when(refunds.getByPaymentId(5L))
                .thenAnswer(
                        call ->
                                saved.values().stream()
                                        .max(Comparator.comparing(PaymentRefund::getRequestedAt)));
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
        physicalReturns = mock(PhysicalReturnService.class);
        persistence =
                new PaymentRefundPersistence(
                        payments,
                        refunds,
                        workflow,
                        users,
                        properties,
                        client,
                        deliveries,
                        new ModelMapperConfig().modelMapper(),
                        physicalReturns,
                        attempts);
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
    void verifiedProviderRejectionIsRecordedWithoutReleasingFulfillmentOrSendingAgain() {
        String id = persistence.prepare("admin@example.com", 5L, "Cancelar la compra");
        when(client.refundPayment(any()))
                .thenThrow(new RefundRejectedException("cannot_refund_order"));
        when(client.getOrder("ORDTST123")).thenReturn(unchangedProviderOrder(Set.of()));
        persistence.dispatch("admin@example.com", id);
        var result = persistence.getRefund("admin@example.com", 5L);
        assertEquals(PaymentRefundStatus.REJECTED, result.getStatus());
        assertEquals("cannot_refund_order", result.getRejectionCode());
        assertNotNull(result.getRejectedAt());
        assertTrue(result.getRejectionReason().contains("estado actual"));
        assertEquals(PaymentStatus.REVIEW, order.getPaymentStatus());
        assertTrue(order.isStockDeducted());
        assertEquals(id, persistence.prepare("admin@example.com", 5L, "Otro intento"));
        persistence.dispatch("admin@example.com", id);
        verify(client).refundPayment(any());
        verify(client).getOrder("ORDTST123");
        verify(deliveries).revokeOrder(order);
        verify(workflow, never()).lockProduct(anyLong());
        verify(workflow)
                .saveAudit(
                        argThat(
                                audit ->
                                        "REFUND_REJECTED".equals(audit.getAction())
                                                && audit.getReason()
                                                        .contains("cannot_refund_order")));
    }

    @Test
    void manualRetryCreatesOneSuccessorAndKeepsRejectedAttemptHistoryAndOriginalIntent() {
        String id = persistence.prepare("admin@example.com", 5L, "Cancelar compra");
        when(client.refundPayment(any()))
                .thenThrow(new RefundRejectedException("cannot_refund_order"));
        when(client.getOrder("ORDTST123")).thenReturn(unchangedProviderOrder(Set.of()));
        persistence.dispatch("admin@example.com", id);
        PaymentRefundAttempt first = attemptRecords.get(id);
        assertEquals(PaymentRefundStatus.REJECTED, first.getStatus());
        admin.setId(8L);
        persistence.prepareRetry("admin@example.com", 5L, id, "Causa corregida, reintento manual");
        String successor = saved.get(id).getCurrentAttemptId();
        assertNotEquals(id, successor);
        assertEquals(2, saved.get(id).getAttemptNumber());
        assertEquals(id, attemptRecords.get(successor).getPreviousAttemptId());
        assertEquals(
                "Causa corregida, reintento manual", attemptRecords.get(successor).getReason());
        assertEquals("Cancelar compra", saved.get(id).getReason());
        assertEquals(7L, saved.get(id).getActorUserId());
        assertEquals(7L, first.getActorUserId());
        assertEquals(8L, attemptRecords.get(successor).getActorUserId());
        assertEquals(PaymentRefundStatus.REJECTED, first.getStatus());
        assertNotNull(first.getRejectedAt());
        assertNull(saved.get(id).getRejectedAt());
        assertEquals(PaymentStatus.REVIEW, order.getPaymentStatus());
        assertEquals(id, persistence.prepareRetry("admin@example.com", 5L, id, "Reenvio HTTP"));
        assertEquals(2, attemptRecords.size());
        doReturn(new MercadoPagoClient.RefundReceipt("REF456")).when(client).refundPayment(any());
        persistence.dispatch("admin@example.com", id);
        persistence.dispatch("admin@example.com", id);
        assertEquals(PaymentRefundStatus.PROCESSING, attemptRecords.get(successor).getStatus());
        assertEquals("REF456", attemptRecords.get(successor).getProviderRefundId());
        assertEquals(PaymentRefundStatus.REJECTED, first.getStatus());
        var history = persistence.getAttempts("admin@example.com", 5L, PageRequest.of(0, 20));
        assertEquals(2, history.getTotalElements());
        assertEquals(successor, history.getContent().get(0).getId());
        assertEquals("cannot_refund_order", history.getContent().get(1).getRejectionCode());
        verify(client, times(2)).refundPayment(any());
        verify(workflow)
                .saveAudit(argThat(audit -> "REFUND_RETRY_REQUESTED".equals(audit.getAction())));
    }

    @Test
    void uncertainProcessingAndConfirmedRequestsCannotCreateANewKey() {
        String id = persistence.prepare("admin@example.com", 5L, "Cancelar");
        for (PaymentRefundStatus state :
                List.of(
                        PaymentRefundStatus.REQUESTED,
                        PaymentRefundStatus.PROCESSING,
                        PaymentRefundStatus.CONFIRMED)) {
            saved.get(id).setStatus(state);
            attemptRecords.get(id).setStatus(state);
            assertThrows(
                    BusinessConflictException.class,
                    () -> persistence.prepareRetry("admin@example.com", 5L, id, "Nuevo intento"));
        }
        assertEquals(id, saved.get(id).getCurrentAttemptId());
        assertEquals(1, attemptRecords.size());
        verifyNoInteractions(client);
    }

    @Test
    void retryRevalidatesFinancialStateAndReplayingItsSourceNeverCreatesAThirdAttempt() {
        String id = persistence.prepare("admin@example.com", 5L, "Cancelar");
        when(client.refundPayment(any()))
                .thenThrow(new RefundRejectedException("cannot_refund_order"));
        when(client.getOrder("ORDTST123")).thenReturn(unchangedProviderOrder(Set.of()));
        persistence.dispatch("admin@example.com", id);
        when(client.getOrder("ORDTST123")).thenReturn(unchangedProviderOrder(Set.of("PAY123")));
        assertThrows(
                ExternalServiceException.class,
                () -> persistence.prepareRetry("admin@example.com", 5L, id, "Corregido"));
        assertEquals(1, attemptRecords.size());
        when(client.getOrder("ORDTST123")).thenReturn(unchangedProviderOrder(Set.of()));
        persistence.prepareRetry("admin@example.com", 5L, id, "Corregido");
        persistence.dispatch("admin@example.com", id);
        assertEquals(PaymentRefundStatus.REJECTED, saved.get(id).getStatus());
        String second = saved.get(id).getCurrentAttemptId();
        persistence.prepareRetry("admin@example.com", 5L, id, "Reenvio anterior");
        assertEquals(second, saved.get(id).getCurrentAttemptId());
        assertEquals(2, attemptRecords.size());
        persistence.prepareRetry("admin@example.com", 5L, second, "Tercer intento expreso");
        assertEquals(3, attemptRecords.size());
        assertEquals(3, saved.get(id).getAttemptNumber());
        assertThrows(
                BusinessConflictException.class,
                () -> persistence.prepareRetry("admin@example.com", 5L, id, "Peticion obsoleta"));
    }

    @Test
    void simultaneousRetryPreparationsProduceOnlyOneSuccessorUnderTheOrderLock() throws Exception {
        String id = persistence.prepare("admin@example.com", 5L, "Cancelar");
        when(client.refundPayment(any()))
                .thenThrow(new RefundRejectedException("cannot_refund_order"));
        when(client.getOrder("ORDTST123")).thenReturn(unchangedProviderOrder(Set.of()));
        persistence.dispatch("admin@example.com", id);
        ReentrantLock lock = new ReentrantLock();
        // Simulates the transaction-scoped write lock; MySQL integration remains pending.
        when(workflow.lockOrder(10L))
                .thenAnswer(
                        call -> {
                            lock.lock();
                            return order;
                        });
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<String>> requests = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                requests.add(
                        executor.submit(
                                () -> {
                                    assertTrue(start.await(5, TimeUnit.SECONDS));
                                    try {
                                        return persistence.prepareRetry(
                                                "admin@example.com", 5L, id, "Causa corregida");
                                    } finally {
                                        if (lock.isHeldByCurrentThread()) lock.unlock();
                                    }
                                }));
            }
            start.countDown();
            for (var request : requests) assertEquals(id, request.get(10, TimeUnit.SECONDS));
            assertEquals(2, attemptRecords.size());
            assertEquals(2, saved.get(id).getAttemptNumber());
            assertEquals(
                    id,
                    attemptRecords.get(saved.get(id).getCurrentAttemptId()).getPreviousAttemptId());
            verify(client).refundPayment(any());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void failedCanonicalVerificationKeepsTheOutcomeUncertainAndTheSameRequestKey() {
        String id = persistence.prepare("admin@example.com", 5L, "Cancelar la compra");
        when(client.refundPayment(any()))
                .thenThrow(new RefundRejectedException("refund_amount_exceeds"));
        when(client.getOrder("ORDTST123")).thenThrow(new ExternalServiceException("Sin conexion"));
        assertThrows(
                ExternalServiceException.class,
                () -> persistence.dispatch("admin@example.com", id));
        assertEquals(PaymentRefundStatus.REQUESTED, saved.get(id).getStatus());
        assertNull(saved.get(id).getRejectionCode());
        doReturn(unchangedProviderOrder(Set.of("PAY123"))).when(client).getOrder("ORDTST123");
        assertThrows(
                ExternalServiceException.class,
                () -> persistence.dispatch("admin@example.com", id));
        assertEquals(PaymentRefundStatus.REQUESTED, saved.get(id).getStatus());
        assertNull(saved.get(id).getRejectedAt());
        assertEquals(PaymentStatus.REVIEW, order.getPaymentStatus());
    }

    @Test
    void aDifferentProviderResourceCannotBeUsedToVerifyARejection() {
        String id = persistence.prepare("admin@example.com", 5L, "Cancelar");
        when(client.refundPayment(any()))
                .thenThrow(new RefundRejectedException("transaction_not_found"));
        var original = unchangedProviderOrder(Set.of());
        when(client.getOrder("ORDTST123"))
                .thenReturn(
                        new MercadoPagoClient.VerifiedOrder(
                                "ORDTST999",
                                original.reference(),
                                original.collectorId(),
                                original.currency(),
                                original.amount(),
                                original.liveMode(),
                                original.payments()));
        assertThrows(
                ExternalServiceException.class,
                () -> persistence.dispatch("admin@example.com", id));
        assertEquals(PaymentRefundStatus.REQUESTED, saved.get(id).getStatus());
        assertNull(saved.get(id).getRejectedAt());
    }

    @Test
    void changedRefundedAmountOrStaleFinancialDataCannotVerifyARejection() {
        String id = persistence.prepare("admin@example.com", 5L, "Cancelar");
        when(client.refundPayment(any()))
                .thenThrow(new RefundRejectedException("refund_amount_exceeds"));
        var original = unchangedProviderOrder(Set.of());
        var previous = original.payments().get(0);
        var changed =
                new MercadoPagoClient.VerifiedPayment(
                        previous.id(),
                        previous.reference(),
                        previous.collectorId(),
                        previous.currency(),
                        previous.amount(),
                        BigDecimal.TEN,
                        previous.status(),
                        previous.paymentType(),
                        previous.paymentMethod(),
                        previous.approvedAt(),
                        previous.updatedAt(),
                        previous.liveMode());
        when(client.getOrder("ORDTST123"))
                .thenReturn(
                        new MercadoPagoClient.VerifiedOrder(
                                original.id(),
                                original.reference(),
                                original.collectorId(),
                                original.currency(),
                                original.amount(),
                                original.liveMode(),
                                List.of(changed)));
        assertThrows(
                ExternalServiceException.class,
                () -> persistence.dispatch("admin@example.com", id));
        when(client.getOrder("ORDTST123")).thenReturn(original);
        payment.setProviderUpdatedAt(previous.updatedAt().plusSeconds(1));
        assertThrows(
                ExternalServiceException.class,
                () -> persistence.dispatch("admin@example.com", id));
        assertEquals(PaymentRefundStatus.REQUESTED, saved.get(id).getStatus());
        assertNull(saved.get(id).getRejectionCode());
        assertEquals(PaymentStatus.REVIEW, order.getPaymentStatus());
    }

    @Test
    void shippedPurchaseRequiresReturnResolutionBeforeARefundIsCreated() {
        order.setOrderStatus(OrderStatus.SHIPPED);
        doThrow(new BusinessConflictException("Recepcion pendiente"))
                .when(physicalReturns)
                .requireRefundReady(order);
        assertThrows(
                BusinessConflictException.class,
                () -> persistence.prepare("admin@example.com", 5L, "Reembolsar"));
        assertTrue(saved.isEmpty());
        assertEquals(PaymentStatus.PAID, order.getPaymentStatus());
        verifyNoInteractions(deliveries);
        doNothing().when(physicalReturns).requireRefundReady(order);
        persistence.prepare("admin@example.com", 5L, "Devolucion recibida");
        assertEquals(PaymentStatus.REVIEW, order.getPaymentStatus());
    }

    @Test
    void extraPaymentRefundDoesNotRequireReturningTheOriginalPurchase() {
        order.setOrderStatus(OrderStatus.DELIVERED);
        order.setProviderPaymentId("PAYORIGINAL");
        payment.setReviewReason(PaymentReviewReason.DUPLICATE_PAYMENT);
        persistence.prepare("admin@example.com", 5L, "Pago duplicado");
        verifyNoInteractions(physicalReturns, deliveries);
        assertEquals(PaymentStatus.PAID, order.getPaymentStatus());
    }

    @Test
    void refundRequestedBeforeDispatchPreventsTheShipmentTransition() {
        order.setOrderStatus(OrderStatus.CONFIRMED);
        order.setItemsOrder(
                List.of(new OrderItem(order, new ProductPhysical(), 1, BigDecimal.TEN)));
        persistence.prepare("admin@example.com", 5L, "Cancelar antes del envio");
        UserDao actors = mock(UserDao.class);
        when(actors.getUserByEmail("admin@example.com")).thenReturn(Optional.of(admin));
        OrderShippingService shipping = mock(OrderShippingService.class);
        OrderService lifecycle =
                new OrderService(
                        mock(OrderDao.class),
                        actors,
                        workflow,
                        new ModelMapperConfig().modelMapper(),
                        deliveries,
                        shipping,
                        payments);
        assertThrows(
                BusinessConflictException.class,
                () ->
                        lifecycle.updateOrderStatus(
                                "admin@example.com", 10L, OrderStatus.SHIPPED, "Despachar"));
        assertEquals(OrderStatus.CONFIRMED, order.getOrderStatus());
        assertEquals(PaymentStatus.REVIEW, order.getPaymentStatus());
        verifyNoInteractions(shipping);
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
        when(storage.getRefundById("admin@example.com", 5L, "request")).thenReturn(response);
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
        sequence.verify(storage).getRefundById("admin@example.com", 5L, "request");
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

    private ItemRefundRequestDTO partial(UUID reference, int quantity) {
        var line = new ItemRefundLineRequestDTO();
        line.setOrderItemId(20L);
        line.setQuantity(quantity);
        var request = new ItemRefundRequestDTO();
        request.setReference(reference);
        request.setReason("Devolver unidades");
        request.setItems(List.of(line));
        return request;
    }

    private ProductPhysical physicalLine() {
        var product = new ProductPhysical();
        product.setIdProduct(4L);
        product.setStockProduct(3);
        var item = new OrderItem(order, product, 3, new BigDecimal("25.00"));
        item.setIdOrderItem(20L);
        order.setItemsOrder(List.of(item));
        when(workflow.lockProduct(4L)).thenReturn(product);
        return product;
    }

    @Test
    void itemRefundUsesHistoricalPriceAndRestoresOnlySelectedUnitsOnce() {
        var product = physicalLine();
        product.setPriceProduct(new BigDecimal("999.00"));
        UUID reference = UUID.randomUUID();
        String id = persistence.prepareItems("admin@example.com", 5L, partial(reference, 2));
        assertEquals(new BigDecimal("50.00"), saved.get(id).getAmount());
        assertEquals(5, product.getStockProduct());
        var item = order.getItemsOrder().get(0);
        assertEquals(3, item.getAmountOrderItem());
        assertEquals(2, item.getCanceledQuantity());
        assertEquals(1, item.getFulfillmentQuantity());
        assertEquals(0, item.getRefundedQuantity());
        assertEquals(2, item.getRefundRequestedQuantity());
        assertEquals(id, persistence.prepareItems("admin@example.com", 5L, partial(reference, 2)));
        assertEquals(5, product.getStockProduct());
        assertEquals(1, attemptRecords.size());
        verify(deliveries).revokeItems(order, Set.of(20L));
        verify(deliveries, never()).revokeOrder(any());
        var dto = persistence.getRefundById("admin@example.com", 5L, id);
        assertTrue(dto.isItemized());
        assertEquals(2, dto.getItems().get(0).getQuantity());
    }

    @Test
    void itemRefundRejectsOversizedForeignDuplicateAndChangedReplay() {
        var product = physicalLine();
        assertThrows(
                BusinessConflictException.class,
                () ->
                        persistence.prepareItems(
                                "admin@example.com", 5L, partial(UUID.randomUUID(), 4)));
        var foreign = partial(UUID.randomUUID(), 1);
        foreign.getItems().get(0).setOrderItemId(99L);
        assertThrows(
                InvalidRequestException.class,
                () -> persistence.prepareItems("admin@example.com", 5L, foreign));
        var duplicate = partial(UUID.randomUUID(), 1);
        duplicate.setItems(List.of(duplicate.getItems().get(0), duplicate.getItems().get(0)));
        assertThrows(
                InvalidRequestException.class,
                () -> persistence.prepareItems("admin@example.com", 5L, duplicate));
        assertEquals(3, product.getStockProduct());
        UUID reference = UUID.randomUUID();
        persistence.prepareItems("admin@example.com", 5L, partial(reference, 1));
        assertThrows(
                BusinessConflictException.class,
                () -> persistence.prepareItems("admin@example.com", 5L, partial(reference, 2)));
        assertThrows(
                BusinessConflictException.class,
                () ->
                        persistence.prepareItems(
                                "admin@example.com", 5L, partial(UUID.randomUUID(), 1)));
    }

    @Test
    void itemRefundAfterShipmentDoesNotRestoreStockAndRequiresReceipt() {
        var product = physicalLine();
        order.setOrderStatus(OrderStatus.SHIPPED);
        persistence.prepareItems("admin@example.com", 5L, partial(UUID.randomUUID(), 1));
        verify(physicalReturns).requireItemRefundReady(order, order.getItemsOrder().get(0), 1);
        assertEquals(3, product.getStockProduct());
        assertEquals(0, order.getItemsOrder().get(0).getCanceledQuantity());
        assertEquals(OrderStatus.SHIPPED, order.getOrderStatus());
    }

    @Test
    void subsequentPartialsCannotRefundTheSameUnitsAndFullRefundUsesRemainingMoney() {
        var product = physicalLine();
        String first =
                persistence.prepareItems("admin@example.com", 5L, partial(UUID.randomUUID(), 2));
        saved.get(first).setStatus(PaymentRefundStatus.CONFIRMED);
        payment.setRefundedAmount(new BigDecimal("50.00"));
        order.setPaymentStatus(PaymentStatus.PARTIALLY_REFUNDED);
        assertThrows(
                BusinessConflictException.class,
                () ->
                        persistence.prepareItems(
                                "admin@example.com", 5L, partial(UUID.randomUUID(), 2)));
        String second =
                persistence.prepareItems("admin@example.com", 5L, partial(UUID.randomUUID(), 1));
        assertEquals(new BigDecimal("25.00"), saved.get(second).getAmount());
        assertEquals(6, product.getStockProduct());
        saved.get(second).setStatus(PaymentRefundStatus.CONFIRMED);
        payment.setRefundedAmount(new BigDecimal("75.00"));
        order.setPaymentStatus(PaymentStatus.PARTIALLY_REFUNDED);
        String full =
                persistence.prepare("admin@example.com", 5L, "Devolver el saldo incluido envio");
        assertFalse(saved.get(full).isItemized());
        assertEquals(new BigDecimal("25.00"), saved.get(full).getAmount());
        assertEquals(3, saved.size());
    }

    @Test
    void canceledOrderWithVerifiedChargebackRecoveryCanRequestRefundWithoutResumingPurchase() {
        payment.setProviderStatus("charged_back");
        payment.setProviderStatusDetail("reimbursed");
        payment.setReviewReason(PaymentReviewReason.LATE_APPROVAL);
        order.setOrderStatus(OrderStatus.CANCELED);
        order.setPaymentStatus(PaymentStatus.REVIEW);
        String id =
                persistence.prepare(
                        "admin@example.com", 5L, "Devolver dinero recuperado del contracargo");
        persistence.dispatch("admin@example.com", id);
        assertEquals(PaymentRefundStatus.PROCESSING, saved.get(id).getStatus());
        assertEquals(OrderStatus.CANCELED, order.getOrderStatus());
        assertEquals(PaymentStatus.REVIEW, order.getPaymentStatus());
        verify(client).refundPayment(saved.get(id));
    }
}

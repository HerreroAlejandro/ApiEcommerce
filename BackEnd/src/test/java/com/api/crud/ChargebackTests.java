package com.api.crud;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.api.crud.config.*;
import com.api.crud.exceptions.*;
import com.api.crud.models.entity.*;
import com.api.crud.models.entity.Order;
import com.api.crud.models.enums.*;
import com.api.crud.repositories.*;
import com.api.crud.services.*;

import org.junit.jupiter.api.*;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

class ChargebackTests {
    private ChargebackDao cases;
    private PaymentRefundDao refunds;
    private OrderWorkflowDao workflow;
    private OrderService orders;
    private DigitalDeliveryService deliveries;
    private ChargebackLifecycle lifecycle;
    private ChargebackPersistence persistence;
    private Payment payment;
    private Order order;
    private UserModel admin;
    private final List<ChargebackCase> records = new ArrayList<>();
    private Instant version;

    @BeforeEach
    void setUp() {
        cases = mock(ChargebackDao.class);
        refunds = mock(PaymentRefundDao.class);
        workflow = mock(OrderWorkflowDao.class);
        orders = mock(OrderService.class);
        deliveries = mock(DigitalDeliveryService.class);
        lifecycle = new ChargebackLifecycle(cases, refunds, workflow, orders, deliveries);
        UserDao users = mock(UserDao.class);
        admin = new UserModel();
        admin.setId(7L);
        admin.setRoles(Set.of(new Role("ADMIN")));
        when(users.getUserByEmail("admin@example.com")).thenReturn(Optional.of(admin));
        var properties = new MercadoPagoProperties();
        properties.setEnabled(true);
        properties.setCollectorId("99");
        persistence =
                new ChargebackPersistence(
                        cases, users, workflow, properties, new ModelMapperConfig().modelMapper());
        order = new Order();
        order.setIdOrder(10L);
        order.setOrderStatus(OrderStatus.CONFIRMED);
        order.setPaymentStatus(PaymentStatus.PAID);
        order.setProviderPaymentId("PAY123");
        order.setItemsOrder(List.of());
        payment = new Payment();
        payment.setId(5L);
        payment.setProviderPaymentId("PAY123");
        payment.setProviderReferenceId("123456");
        payment.setAmount(new BigDecimal("100.00"));
        payment.setReviewReason(PaymentReviewReason.NONE);
        PaymentCheckout checkout = new PaymentCheckout();
        checkout.setOrder(order);
        checkout.setCollectorId("99");
        checkout.setCurrency("ARS");
        checkout.setProviderOrderId("ORDTST123");
        payment.setCheckout(checkout);
        version = Instant.now().minusSeconds(20);
        when(cases.getByPaymentId(5L)).thenAnswer(call -> List.copyOf(records));
        when(cases.getById(anyLong()))
                .thenAnswer(
                        call ->
                                records.stream()
                                        .filter(
                                                record ->
                                                        record.getId().equals(call.getArgument(0)))
                                        .findFirst());
        doAnswer(
                        call -> {
                            ChargebackCase record = call.getArgument(0);
                            record.setId((long) records.size() + 1);
                            records.add(record);
                            return null;
                        })
                .when(cases)
                .save(any());
        when(workflow.lockOrder(10L)).thenReturn(order);
        doAnswer(
                        call -> {
                            if (order.getOrderStatus() == OrderStatus.PENDING
                                    || order.getOrderStatus() == OrderStatus.CONFIRMED)
                                order.setOrderStatus(OrderStatus.CANCELED);
                            return null;
                        })
                .when(orders)
                .cancelChargebackOrder(order);
    }

    private MercadoPagoClient.VerifiedPayment snapshot(String detail) {
        return new MercadoPagoClient.VerifiedPayment(
                "PAY123",
                "ref",
                "99",
                "ARS",
                new BigDecimal("100.00"),
                BigDecimal.ZERO,
                "charged_back",
                "credit_card",
                "visa",
                version,
                version,
                false,
                Set.of(),
                detail,
                "123456",
                Set.of("9988"));
    }

    private MercadoPagoClient.VerifiedChargeback result(Boolean coverage, Instant updated) {
        return new MercadoPagoClient.VerifiedChargeback(
                "9988",
                Set.of("123456"),
                "ARS",
                new BigDecimal("100.00"),
                false,
                coverage,
                "review_pending",
                "unauthorized",
                updated,
                version.plusSeconds(1000));
    }

    @Test
    void detectionIsIdempotentCancelsFulfillmentAndNeverMovesMoney() {
        lifecycle.apply(payment, snapshot("in_process"));
        lifecycle.apply(payment, snapshot("in_process"));
        assertEquals(1, records.size());
        assertEquals(ChargebackResolution.OPEN, records.get(0).getResolution());
        assertEquals(OrderStatus.CANCELED, order.getOrderStatus());
        assertEquals(PaymentStatus.REVIEW, order.getPaymentStatus());
        verify(deliveries).revokeOrder(order);
        verify(cases).save(any());
        assertNull(records.get(0).getCoverageApplied());
    }

    @Test
    void lostCasePreservesDeliveredHistoryAndDoesNotRepeatRevocation() {
        order.setOrderStatus(OrderStatus.DELIVERED);
        lifecycle.apply(payment, snapshot("settled"));
        persistence.applyVerified(1L, result(false, version));
        lifecycle.apply(payment, snapshot("settled"));
        assertEquals(ChargebackResolution.LOST, records.get(0).getResolution());
        assertEquals(PaymentStatus.CHARGED_BACK, order.getPaymentStatus());
        assertEquals(OrderStatus.DELIVERED, order.getOrderStatus());
        assertEquals(PaymentReviewReason.CHARGEBACK_LOST, payment.getReviewReason());
        clearInvocations(deliveries);
        lifecycle.apply(payment, snapshot("settled"));
        verifyNoInteractions(deliveries);
        verify(orders, never()).cancelRefundedOrder(any());
    }

    @Test
    void favorableResolutionAloneDoesNotProveMoneyWasRecovered() {
        order.setOrderStatus(OrderStatus.DELIVERED);
        lifecycle.apply(payment, snapshot("in_process"));
        persistence.applyVerified(1L, result(true, version));
        lifecycle.apply(payment, snapshot("settled"));
        assertEquals(PaymentStatus.REVIEW, order.getPaymentStatus());
        lifecycle.apply(payment, snapshot("reimbursed"));
        assertEquals(PaymentStatus.PAID, order.getPaymentStatus());
        verify(deliveries).restorePaidOrderAccess(order);
        assertEquals(OrderStatus.DELIVERED, order.getOrderStatus());
    }

    @Test
    void recoveredMoneyNeverReopensCanceledOrderAndBecomesRefundable() {
        lifecycle.apply(payment, snapshot("in_process"));
        persistence.applyVerified(1L, result(true, version));
        lifecycle.apply(payment, snapshot("reimbursed"));
        assertEquals(OrderStatus.CANCELED, order.getOrderStatus());
        assertEquals(PaymentStatus.REVIEW, order.getPaymentStatus());
        assertEquals(PaymentReviewReason.LATE_APPROVAL, payment.getReviewReason());
        verify(deliveries, never()).restorePaidOrderAccess(any());
    }

    @Test
    void extraPaymentChargebackDoesNotCancelTheLegitimatePurchase() {
        order.setProviderPaymentId("PAY_OTHER");
        lifecycle.apply(payment, snapshot("in_process"));
        persistence.applyVerified(1L, result(false, version));
        lifecycle.apply(payment, snapshot("settled"));
        assertEquals(PaymentStatus.PAID, order.getPaymentStatus());
        assertEquals(OrderStatus.CONFIRMED, order.getOrderStatus());
        verifyNoInteractions(orders, deliveries);
    }

    @Test
    void caseIdentityAndEnvironmentMustMatchAndOldResultsCannotReopenIt() {
        lifecycle.apply(payment, snapshot("in_process"));
        var wrongPayment =
                new MercadoPagoClient.VerifiedChargeback(
                        "9988",
                        Set.of("999"),
                        "ARS",
                        BigDecimal.TEN,
                        false,
                        true,
                        "valid",
                        "unauthorized",
                        version,
                        null);
        assertThrows(
                BusinessConflictException.class, () -> persistence.applyVerified(1L, wrongPayment));
        assertEquals(ChargebackResolution.OPEN, records.get(0).getResolution());
        persistence.applyVerified(1L, result(false, version));
        persistence.applyVerified(1L, result(null, version.minusSeconds(1)));
        assertEquals(ChargebackResolution.LOST, records.get(0).getResolution());
        assertThrows(
                ExternalServiceException.class,
                () -> persistence.applyVerified(1L, result(true, version)));
        assertThrows(
                ExternalServiceException.class,
                () -> persistence.applyVerified(1L, result(null, version.plusSeconds(1))));
    }

    @Test
    void notesAreAuditedAndCannotChangeTheProviderDecision() {
        lifecycle.apply(payment, snapshot("in_process"));
        var response =
                persistence.note("admin@example.com", 1L, "Comprobante enviado desde Mercado Pago");
        assertEquals(5L, response.getPaymentId());
        assertEquals(7L, response.getLastActorUserId());
        assertEquals(ChargebackResolution.OPEN, response.getResolution());
        assertNull(response.getCoverageApplied());
        admin.setRoles(Set.of(new Role("SUPPORT")));
        assertDoesNotThrow(() -> persistence.get("admin@example.com", 1L));
        assertThrows(
                ForbiddenOperationException.class,
                () -> persistence.note("admin@example.com", 1L, "Cambiar"));
        assertThrows(
                ForbiddenOperationException.class,
                () -> persistence.getSyncDetails("admin@example.com", 1L));
        admin.setRoles(Set.of(new Role("CLIENT")));
        assertThrows(
                ForbiddenOperationException.class, () -> persistence.get("admin@example.com", 1L));
    }

    @Test
    void paginationUsesModelMapperAndValidatesLimits() {
        lifecycle.apply(payment, snapshot("in_process"));
        var page = PageRequest.of(0, 20);
        when(cases.getCases(ChargebackResolution.OPEN, page))
                .thenReturn(new PageImpl<>(records, page, 1));
        var result = persistence.getCases("admin@example.com", ChargebackResolution.OPEN, page);
        assertEquals("9988", result.getContent().get(0).getProviderCaseId());
        assertThrows(
                InvalidRequestException.class,
                () -> persistence.getCases("admin@example.com", null, PageRequest.of(0, 101)));
    }

    @Test
    void allCasesMustBeWonBeforeRestoringAccessAndPendingRefundStillBlocksDelivery() {
        order.setOrderStatus(OrderStatus.DELIVERED);
        lifecycle.apply(payment, snapshot("in_process"));
        persistence.applyVerified(1L, result(true, version));
        var second = new ChargebackCase();
        second.setId(2L);
        second.setPayment(payment);
        second.setProviderCaseId("9989");
        records.add(second);
        lifecycle.apply(payment, snapshot("reimbursed"));
        assertEquals(PaymentStatus.REVIEW, order.getPaymentStatus());
        verify(deliveries, never()).restorePaidOrderAccess(any());
        second.setResolution(ChargebackResolution.WON);
        var refund = new PaymentRefund();
        refund.setStatus(PaymentRefundStatus.REQUESTED);
        when(refunds.getAllByPaymentId(5L)).thenReturn(List.of(refund));
        lifecycle.apply(payment, snapshot("reimbursed"));
        assertEquals(PaymentStatus.REVIEW, order.getPaymentStatus());
        verify(deliveries, never()).restorePaidOrderAccess(any());
    }

    @Test
    void groupedCaseAmountIsNotMisrepresentedAsTheAmountOfOneLocalPayment() {
        lifecycle.apply(payment, snapshot("in_process"));
        var remote =
                new MercadoPagoClient.VerifiedChargeback(
                        "9988",
                        Set.of("123456", "654321"),
                        "ARS",
                        new BigDecimal("150.00"),
                        false,
                        null,
                        "pending",
                        "unauthorized",
                        version,
                        null);
        persistence.applyVerified(1L, remote);
        var response = persistence.get("admin@example.com", 1L);
        assertTrue(response.isGrouped());
        assertEquals(new BigDecimal("150.00"), response.getAmount());
        assertEquals(10L, response.getOrderId());
        assertEquals(ChargebackResolution.OPEN, response.getResolution());
    }

    @Test
    void chargebackInvalidatesEveryDownloadEvenWhenARefundAlreadyHeldTheOrder() {
        order.setOrderStatus(OrderStatus.SHIPPED);
        order.setPaymentStatus(PaymentStatus.REVIEW);
        lifecycle.apply(payment, snapshot("in_process"));
        lifecycle.apply(payment, snapshot("in_process"));
        verify(deliveries).revokeOrder(order);
        assertEquals(OrderStatus.SHIPPED, order.getOrderStatus());
    }
}

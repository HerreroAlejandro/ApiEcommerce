package com.api.crud;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.api.crud.config.ModelMapperConfig;
import com.api.crud.controllers.DigitalDeliveryController;
import com.api.crud.exceptions.*;
import com.api.crud.models.entity.*;
import com.api.crud.models.enums.*;
import com.api.crud.repositories.*;
import com.api.crud.services.*;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.math.BigDecimal;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

class DigitalDeliveryServiceTests {

    private DigitalDeliveryDao deliveries;
    private OrderWorkflowDao workflow;
    private UserDao users;
    private DigitalFileGateway files;
    private DigitalDeliveryService service;
    private Order order;
    private OrderItem item;
    private DigitalDelivery delivery;
    private UserModel buyer;

    @BeforeEach
    void setUp() {
        deliveries = mock(DigitalDeliveryDao.class);
        workflow = mock(OrderWorkflowDao.class);
        users = mock(UserDao.class);
        files = mock(DigitalFileGateway.class);
        OrderDao orders = mock(OrderDao.class);
        service =
                new DigitalDeliveryService(
                        deliveries,
                        orders,
                        workflow,
                        users,
                        new ModelMapperConfig().modelMapper(),
                        files);
        buyer = new UserModel();
        buyer.setId(7L);
        buyer.setRoles(Set.of(new Role("CLIENT")));
        when(users.getUserByEmail("buyer@example.com")).thenReturn(Optional.of(buyer));
        order = new Order();
        order.setIdOrder(10L);
        order.setUserOrder(buyer);
        order.setOrderStatus(OrderStatus.PENDING);
        order.setPaymentStatus(PaymentStatus.PAID);
        ProductDigital product = new ProductDigital();
        product.setDownloadLink("https://example.com/changed.zip");
        product.setActive(false);
        item = new OrderItem(order, product, 1, BigDecimal.TEN);
        item.setIdOrderItem(11L);
        item.setProductName("Producto comprado");
        item.setDigitalDownloadLink("https://example.com/purchased.zip");
        order.setItemsOrder(List.of(item));
        delivery = new DigitalDelivery();
        delivery.setId(20L);
        delivery.setOrderItem(item);
        delivery.setSourceUrl(item.getDigitalDownloadLink());
        delivery.setEnabledAt(LocalDateTime.now());
        delivery.setStatus(DigitalDeliveryStatus.AVAILABLE);
        when(orders.getOrderById(10L)).thenReturn(Optional.of(order));
        when(workflow.lockOrder(10L)).thenReturn(order);
        when(deliveries.getById(20L)).thenReturn(Optional.of(delivery));
        when(deliveries.lockById(20L)).thenReturn(Optional.of(delivery));
        when(deliveries.getByOrderId(10L)).thenReturn(List.of(delivery));
    }

    @Test
    void enablingUsesPurchasedSourceAndIsIdempotent() {
        service.enablePaidOrder(order);
        verify(deliveries, never()).save(any());
        when(deliveries.getByOrderId(10L)).thenReturn(List.of());
        service.enablePaidOrder(order);
        ArgumentCaptor<DigitalDelivery> saved = ArgumentCaptor.forClass(DigitalDelivery.class);
        verify(deliveries).save(saved.capture());
        assertEquals(item.getDigitalDownloadLink(), saved.getValue().getSourceUrl());
        assertEquals(DigitalDeliveryStatus.AVAILABLE, saved.getValue().getStatus());
    }

    @Test
    void pendingPaymentDoesNotGrantAccess() {
        order.setPaymentStatus(PaymentStatus.PENDING);
        assertThrows(BusinessConflictException.class, () -> service.enablePaidOrder(order));
        assertThrows(
                BusinessConflictException.class,
                () -> service.issueToken("buyer@example.com", 20L));
        verify(deliveries, never()).save(any());
    }

    @Test
    void tokenIsHashedAndAClaimConsumesExactlyOneAttempt() {
        var token = service.issueToken("buyer@example.com", 20L);
        assertNotEquals(token.getToken(), delivery.getTokenHash());
        assertEquals(64, delivery.getTokenHash().length());
        assertEquals("/digital-deliveries/20/download", token.getDownloadPath());
        assertEquals(delivery.getTokenExpiresAt(), token.getExpiresAt());
        var grant = service.claim("buyer@example.com", 20L, token.getToken());
        assertEquals(item.getDigitalDownloadLink(), grant.sourceUrl());
        assertEquals(DigitalDeliveryStatus.USED, delivery.getStatus());
        assertEquals(1, delivery.getAttemptCount());
        assertNull(delivery.getTokenHash());
        assertThrows(
                BusinessConflictException.class,
                () -> service.claim("buyer@example.com", 20L, token.getToken()));
        assertThrows(
                BusinessConflictException.class,
                () -> service.issueToken("buyer@example.com", 20L));
        verify(workflow).saveAudit(any());
    }

    @Test
    void completeDownloadCyclePreservesHistoryAndRequiresANewTokenAfterReopening() {
        UserModel admin = new UserModel();
        admin.setId(9L);
        admin.setRoles(Set.of(new Role("ADMIN")));
        when(users.getUserByEmail("admin@example.com")).thenReturn(Optional.of(admin));
        var first = service.issueToken("buyer@example.com", 20L);
        service.claim("buyer@example.com", 20L, first.getToken());
        LocalDateTime firstAttempt = delivery.getAttemptedAt();
        service.reopen("admin@example.com", 20L, "Descarga interrumpida");
        assertNull(delivery.getTokenHash());
        assertNull(delivery.getTokenExpiresAt());
        assertEquals(firstAttempt, delivery.getAttemptedAt());
        assertEquals(1, delivery.getAttemptCount());
        assertThrows(BusinessConflictException.class,
                () -> service.claim("buyer@example.com", 20L, first.getToken()));
        var replacement = service.issueToken("buyer@example.com", 20L);
        assertNotEquals(first.getToken(), replacement.getToken());
        service.claim("buyer@example.com", 20L, replacement.getToken());
        assertEquals(2, delivery.getAttemptCount());
        assertEquals(DigitalDeliveryStatus.USED, delivery.getStatus());
        verify(workflow, times(3)).saveAudit(any());
    }

    @Test
    void interruptedTransferConsumesAttemptAndRequiresAdministrativeReopening() throws Exception {
        var token = service.issueToken("buyer@example.com", 20L);
        DigitalDeliveryController controller = new DigitalDeliveryController(service, files);
        var auth = new UsernamePasswordAuthenticationToken("buyer@example.com", null);
        doThrow(new IOException("Transferencia interrumpida"))
                .when(files).stream(any(), any());
        assertThrows(IOException.class, () -> controller.download(
                auth, 20L, token.getToken(),
                new MockHttpServletRequest("GET", "/digital-deliveries/20/download"),
                new MockHttpServletResponse()));
        assertEquals(DigitalDeliveryStatus.USED, delivery.getStatus());
        assertEquals(1, delivery.getAttemptCount());
        assertThrows(BusinessConflictException.class,
                () -> service.issueToken("buyer@example.com", 20L));
    }

    @Test
    void simultaneousClaimsConsumeOnlyOneAttemptUnderTheOrderLock() throws Exception {
        var token = service.issueToken("buyer@example.com", 20L);
        ReentrantLock lock = new ReentrantLock();
        // Models the transaction's database lock; this does not replace a MySQL integration test.
        when(workflow.lockOrder(10L)).thenAnswer(call -> {
            lock.lock();
            return order;
        });
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> attempts = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                attempts.add(executor.submit(() -> {
                    assertTrue(start.await(5, TimeUnit.SECONDS));
                    try {
                        service.claim("buyer@example.com", 20L, token.getToken());
                        return true;
                    } catch (BusinessConflictException exception) {
                        return false;
                    } finally {
                        if (lock.isHeldByCurrentThread()) lock.unlock();
                    }
                }));
            }
            start.countDown();
            int successful = 0;
            for (Future<Boolean> attempt : attempts) {
                if (attempt.get(10, TimeUnit.SECONDS)) successful++;
            }
            assertEquals(1, successful);
            assertEquals(1, delivery.getAttemptCount());
            verify(workflow).saveAudit(any());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void expiredUnusedPermissionCanBeReplacedAndOldTokenCannotBeReplayed() {
        var old = service.issueToken("buyer@example.com", 20L);
        delivery.setTokenExpiresAt(LocalDateTime.now().minusSeconds(1));
        assertThrows(
                BusinessConflictException.class,
                () -> service.claim("buyer@example.com", 20L, old.getToken()));
        assertEquals(0, delivery.getAttemptCount());
        var fresh = service.issueToken("buyer@example.com", 20L);
        assertNotEquals(old.getToken(), fresh.getToken());
        assertThrows(
                BusinessConflictException.class,
                () -> service.claim("buyer@example.com", 20L, old.getToken()));
        service.claim("buyer@example.com", 20L, fresh.getToken());
        assertEquals(1, delivery.getAttemptCount());
    }

    @Test
    void foreignPurchaseIsHiddenBeforeLockingOrIssuingTokens() {
        UserModel other = new UserModel();
        other.setId(8L);
        when(users.getUserByEmail("other@example.com")).thenReturn(Optional.of(other));
        assertThrows(
                ResourceNotFoundException.class,
                () -> service.issueToken("other@example.com", 20L));
        assertThrows(
                ResourceNotFoundException.class,
                () -> service.getMyDeliveries("other@example.com", 10L));
        verify(workflow, never()).lockOrder(any());
    }

    @Test
    void refundRevokesUnusedAndUsedDeliveriesAndInvalidatesTokens() {
        service.issueToken("buyer@example.com", 20L);
        order.setPaymentStatus(PaymentStatus.REFUNDED);
        service.revokeOrder(order);
        assertEquals(DigitalDeliveryStatus.REVOKED, delivery.getStatus());
        assertNotNull(delivery.getRevokedAt());
        assertNull(delivery.getTokenHash());
        assertThrows(
                BusinessConflictException.class,
                () -> service.issueToken("buyer@example.com", 20L));
    }

    @Test
    void paymentReviewOrRefundBlocksAnAlreadyIssuedTokenAndAdministrativeReopening() {
        UserModel admin = new UserModel();
        admin.setId(9L);
        admin.setRoles(Set.of(new Role("ADMIN")));
        when(users.getUserByEmail("admin@example.com")).thenReturn(Optional.of(admin));
        var token = service.issueToken("buyer@example.com", 20L);
        for (PaymentStatus status : List.of(PaymentStatus.REVIEW, PaymentStatus.REFUNDED)) {
            order.setPaymentStatus(status);
            assertThrows(BusinessConflictException.class,
                    () -> service.claim("buyer@example.com", 20L, token.getToken()));
            assertThrows(BusinessConflictException.class,
                    () -> service.issueToken("buyer@example.com", 20L));
            assertThrows(BusinessConflictException.class,
                    () -> service.reopen("admin@example.com", 20L, "Nuevo intento"));
        }
        assertEquals(0, delivery.getAttemptCount());
        verify(workflow, never()).saveAudit(any());
    }

    @Test
    void staffReopeningIsAuditedAndCannotBypassRefund() {
        UserModel support = new UserModel();
        support.setId(9L);
        support.setRoles(Set.of(new Role("SUPPORT")));
        when(users.getUserByEmail("support@example.com")).thenReturn(Optional.of(support));
        delivery.setStatus(DigitalDeliveryStatus.USED);
        delivery.setAttemptCount(1);
        service.reopen("support@example.com", 20L, "Conexion interrumpida");
        assertEquals(DigitalDeliveryStatus.AVAILABLE, delivery.getStatus());
        assertEquals(1, delivery.getAttemptCount());
        verify(workflow).saveAudit(any());
        delivery.setStatus(DigitalDeliveryStatus.USED);
        order.setPaymentStatus(PaymentStatus.REFUNDED);
        assertThrows(
                BusinessConflictException.class,
                () -> service.reopen("support@example.com", 20L, "Otro intento"));
        assertThrows(
                ForbiddenOperationException.class,
                () -> service.reopen("buyer@example.com", 20L, "Otro intento"));
    }

    @Test
    void buyerResponseNeverExposesSourceOrTokenHash() throws Exception {
        service.issueToken("buyer@example.com", 20L);
        String json =
                new ObjectMapper()
                        .findAndRegisterModules()
                        .writeValueAsString(service.getMyDeliveries("buyer@example.com", 10L));
        assertTrue(json.contains("Producto comprado"));
        assertFalse(json.contains("https://"));
        assertFalse(json.contains("tokenHash"));
        assertFalse(json.contains(delivery.getTokenHash()));
    }

    @Test
    void configuredOriginFailureDoesNotConsumeTheRightOrCreateAToken() {
        doThrow(new ExternalServiceException("Origen no disponible"))
                .when(files)
                .validateSource(any());
        assertThrows(
                ExternalServiceException.class, () -> service.issueToken("buyer@example.com", 20L));
        assertEquals(DigitalDeliveryStatus.AVAILABLE, delivery.getStatus());
        assertNull(delivery.getTokenHash());
    }
}

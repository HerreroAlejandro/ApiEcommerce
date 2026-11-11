package com.api.crud;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.api.crud.config.ModelMapperConfig;
import com.api.crud.exceptions.BusinessConflictException;
import com.api.crud.models.entity.*;
import com.api.crud.models.enums.*;
import com.api.crud.repositories.*;
import com.api.crud.services.DigitalDeliveryService;
import com.api.crud.services.OrderExpirationScheduler;
import com.api.crud.services.OrderService;
import com.api.crud.services.OrderShippingService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

class OrderExpirationTests {

    private OrderWorkflowDao workflow;

    private OrderService service;

    private Order order;

    private ProductPhysical product;

    @BeforeEach
    void setUp() {
        workflow = mock(OrderWorkflowDao.class);
        UserDao users = mock(UserDao.class);
        service =
                new OrderService(
                        mock(OrderDao.class),
                        users,
                        workflow,
                        new ModelMapperConfig().modelMapper(),
                        mock(DigitalDeliveryService.class),
                        mock(OrderShippingService.class),
                        mock(PaymentDao.class));
        UserModel user = new UserModel();
        user.setId(7L);
        when(users.getUserByEmail("admin@example.com")).thenReturn(Optional.of(user));
        product = new ProductPhysical();
        product.setIdProduct(4L);
        product.setStockProduct(3);
        order = new Order();
        order.setIdOrder(10L);
        order.setPurchaseDateOrder(LocalDateTime.now().minusHours(1));
        order.setExpiresAt(LocalDateTime.now().minusMinutes(30));
        order.setOrderStatus(OrderStatus.PENDING);
        order.setPaymentStatus(PaymentStatus.PENDING);
        order.setStockDeducted(true);
        order.setItemsOrder(List.of(new OrderItem(order, product, 2, BigDecimal.TEN)));
        when(workflow.lockOrder(10L)).thenReturn(order);
        when(workflow.lockProduct(4L)).thenReturn(product);
    }

    @Test
    void expirationRestoresStockOnceAndAuditsASystemAction() {
        assertTrue(service.expireOrder(10L));
        assertEquals(OrderStatus.EXPIRED, order.getOrderStatus());
        assertFalse(order.isStockDeducted());
        assertEquals(5, product.getStockProduct());
        assertFalse(service.expireOrder(10L));
        assertEquals(5, product.getStockProduct());
        ArgumentCaptor<OrderChangeAudit> audit = ArgumentCaptor.forClass(OrderChangeAudit.class);
        verify(workflow).saveAudit(audit.capture());
        assertNull(audit.getValue().getActorUserId());
        assertEquals("EXPIRE", audit.getValue().getAction());
        assertEquals("EXPIRED", audit.getValue().getNewValue());
    }

    @Test
    void failedPaymentsExpireButFuturePaidCanceledAndConfirmedOrdersDoNot() {
        order.setExpiresAt(LocalDateTime.now().plusHours(1));
        assertFalse(service.expireOrder(10L));
        order.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        order.setPaymentStatus(PaymentStatus.PAID);
        assertFalse(service.expireOrder(10L));
        order.setPaymentStatus(PaymentStatus.FAILED);
        order.setOrderStatus(OrderStatus.CONFIRMED);
        assertFalse(service.expireOrder(10L));
        order.setOrderStatus(OrderStatus.CANCELED);
        assertFalse(service.expireOrder(10L));
        verify(workflow, never()).lockProduct(anyLong());
        order.setOrderStatus(OrderStatus.PENDING);
        assertTrue(service.expireOrder(10L));
    }

    @Test
    void pendingLegacyOrdersUseTheirPurchaseDateAndDoNotRestoreUndeductedStock() {
        order.setExpiresAt(null);
        order.setStockDeducted(false);
        assertTrue(service.expireOrder(10L));
        assertEquals(order.getPurchaseDateOrder().plusMinutes(30), order.getExpiresAt());
        assertEquals(3, product.getStockProduct());
        verify(workflow, never()).lockProduct(anyLong());
    }

    @Test
    void overduePaymentIsRejectedBeforeSchedulerRuns() {
        assertThrows(
                BusinessConflictException.class,
                () ->
                        service.updatePaymentStatus(
                                "admin@example.com", 10L, PaymentStatus.PAID, "Pago recibido"));
        assertEquals(PaymentStatus.PENDING, order.getPaymentStatus());
        verify(workflow, never()).saveAudit(any());
    }

    @Test
    void timelyPaymentWinsAndCannotBeExpiredAfterward() {
        order.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        order.setPaymentStatus(PaymentStatus.PAID);
        order.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        assertFalse(service.expireOrder(10L));
        assertEquals(3, product.getStockProduct());
    }

    @Test
    void expiredOrdersCannotBeCanceledToRestoreStockAgain() {
        service.expireOrder(10L);
        assertThrows(
                BusinessConflictException.class,
                () -> service.cancelAdminOrder("admin@example.com", 10L, "Cancelacion"));
        assertEquals(5, product.getStockProduct());
    }

    @Test
    void schedulerContinuesPastFailedOrdersAndUsesKeysetPagination() {
        OrderService orders = mock(OrderService.class);
        when(orders.getExpiredOrderIds(any(), eq(0L))).thenReturn(List.of(10L, 11L));
        when(orders.getExpiredOrderIds(any(), eq(11L))).thenReturn(List.of(12L));
        when(orders.getExpiredOrderIds(any(), eq(12L))).thenReturn(List.of());
        doThrow(new BusinessConflictException("Conflicto")).when(orders).expireOrder(10L);
        new OrderExpirationScheduler(orders).expirePendingOrders();
        verify(orders).expireOrder(10L);
        verify(orders).expireOrder(11L);
        verify(orders).expireOrder(12L);
    }
}

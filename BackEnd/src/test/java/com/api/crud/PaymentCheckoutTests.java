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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

class PaymentCheckoutTests {

    private PaymentDao payments;
    private OrderWorkflowDao workflow;
    private MercadoPagoClient client;
    private MercadoPagoProperties properties;
    private PaymentCheckoutPersistence service;
    private Order order;
    private Map<Long, PaymentCheckout> saved;

    @BeforeEach
    void setUp() {
        payments = mock(PaymentDao.class);
        workflow = mock(OrderWorkflowDao.class);
        client = mock(MercadoPagoClient.class);
        properties = new MercadoPagoProperties();
        properties.setEnabled(true);
        properties.setCollectorId("99");
        UserModel user = new UserModel();
        user.setId(7L);
        order = new Order();
        order.setIdOrder(10L);
        order.setUserOrder(user);
        order.setOrderStatus(OrderStatus.PENDING);
        order.setPaymentStatus(PaymentStatus.PENDING);
        order.setStockDeducted(true);
        order.setExpiresAt(LocalDateTime.now().plusMinutes(30));
        order.setTotalOrder(new BigDecimal("150.00"));
        OrderItem item = new OrderItem(order, new ProductPhysical(), 1, new BigDecimal("150.00"));
        item.setIdOrderItem(11L);
        item.setProductName("Compra");
        order.setItemsOrder(List.of(item));
        UserDao users = mock(UserDao.class);
        OrderDao orders = mock(OrderDao.class);
        when(users.getUserByEmail("buyer@example.com")).thenReturn(Optional.of(user));
        when(orders.getOrderById(10L)).thenReturn(Optional.of(order));
        when(workflow.lockOrder(10L)).thenReturn(order);
        saved = new HashMap<>();
        when(payments.getCheckoutByOrderId(10L))
                .thenAnswer(call -> Optional.ofNullable(saved.get(10L)));
        when(payments.getCheckoutByReference(anyString()))
                .thenAnswer(
                        call ->
                                saved.values().stream()
                                        .filter(
                                                checkout ->
                                                        checkout.getReference()
                                                                .equals(call.getArgument(0)))
                                        .findFirst());
        doAnswer(
                        call -> {
                            saved.put(10L, call.getArgument(0));
                            return null;
                        })
                .when(payments)
                .saveCheckout(any());
        when(client.prepareOrderRequest(any())).thenReturn("{\"type\":\"online\"}");
        when(client.createOrder(any()))
                .thenReturn(
                        new MercadoPagoClient.CheckoutOrder(
                                "ORDTST123",
                                "https://www.mercadopago.com.ar/checkout?order_id=ORDTST123"));
        service =
                new PaymentCheckoutPersistence(
                        payments,
                        orders,
                        workflow,
                        users,
                        client,
                        properties,
                        new ModelMapperConfig().modelMapper());
    }

    @Test
    void retriesReuseDurableReferenceAndReturnTheSameCheckoutOrder() {
        String reference = service.prepare("buyer@example.com", 10L);
        assertNotNull(saved.get(10L));
        assertNotNull(saved.get(10L).getRequestBody());
        assertEquals(new BigDecimal("150.00"), saved.get(10L).getAmount());
        var first = service.ensureOrder("buyer@example.com", reference);
        assertEquals(reference, service.prepare("buyer@example.com", 10L));
        var retry = service.ensureOrder("buyer@example.com", reference);
        assertEquals(first.getCheckoutUrl(), retry.getCheckoutUrl());
        assertEquals(10L, retry.getOrderId());
        verify(client).createOrder(any());
        verify(payments, times(2)).refreshCheckout(saved.get(10L));
    }

    @Test
    void timeoutDoesNotLoseTheBindingNeededForLaterNotifications() {
        String reference = service.prepare("buyer@example.com", 10L);
        when(client.createOrder(any())).thenThrow(new ExternalServiceException("Timeout"));
        assertThrows(
                ExternalServiceException.class,
                () -> service.ensureOrder("buyer@example.com", reference));
        assertEquals(reference, saved.get(10L).getReference());
        assertNull(saved.get(10L).getProviderOrderId());
    }

    @Test
    void disabledExpiredPaidAndForeignOrdersCannotStartCheckout() {
        properties.setEnabled(false);
        assertThrows(
                ExternalServiceException.class, () -> service.prepare("buyer@example.com", 10L));
        verifyNoInteractions(client);
        properties.setEnabled(true);
        order.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        assertThrows(
                BusinessConflictException.class, () -> service.prepare("buyer@example.com", 10L));
        order.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        order.setPaymentStatus(PaymentStatus.PAID);
        assertThrows(
                BusinessConflictException.class, () -> service.prepare("buyer@example.com", 10L));
        UserModel other = new UserModel();
        other.setId(8L);
        order.setUserOrder(other);
        assertThrows(
                ResourceNotFoundException.class, () -> service.prepare("buyer@example.com", 10L));
        assertTrue(saved.isEmpty());
    }
}

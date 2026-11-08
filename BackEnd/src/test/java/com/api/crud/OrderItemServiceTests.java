package com.api.crud;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.api.crud.models.entity.*;
import com.api.crud.models.entity.Order;
import com.api.crud.models.enums.*;
import com.api.crud.repositories.*;
import com.api.crud.services.OrderItemService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.*;

class OrderItemServiceTests {

    private OrderItemService service;

    private OrderItemDao items;

    private OrderDao orders;

    private Order order;

    private OrderItem item;

    @BeforeEach
    void setUp() {
        items = mock(OrderItemDao.class);
        orders = mock(OrderDao.class);
        UserDao users = mock(UserDao.class);
        UserModel user = new UserModel();
        user.setId(7L);
        when(users.getUserByEmail("client@example.com")).thenReturn(Optional.of(user));
        service = new OrderItemService(items, orders, users);
        order = new Order();
        order.setIdOrder(10L);
        order.setUserOrder(user);
        order.setOrderStatus(OrderStatus.CONFIRMED);
        order.setPaymentStatus(PaymentStatus.PAID);
        ProductPhysical product = new ProductPhysical();
        product.setIdProduct(4L);
        product.setNameProduct("Nombre actual");
        product.setPriceProduct(new BigDecimal("999.00"));
        item = new OrderItem(order, product, 2, new BigDecimal("100.00"));
        item.setIdOrderItem(1L);
        item.setProductName("Nombre comprado");
        when(items.getOrderItemById(1L)).thenReturn(Optional.of(item));
        when(orders.getOrderById(10L)).thenReturn(Optional.of(order));
        when(items.getItemsByOrderId(10L)).thenReturn(List.of(item));
    }

    @Test
    void responsesUsePurchasedNameAndPrice() {
        var result = service.getMyItem("client@example.com", 1L);
        assertEquals("Nombre comprado", result.getProductName());
        assertEquals(new BigDecimal("100.00"), result.getPriceOrderItem());
        assertEquals(new BigDecimal("200.00"), result.getSubtotal());
        assertEquals(1, service.getMyItems("client@example.com", 10L).size());
    }

    @Test
    void foreignOrderAndItemReturnNotFound() {
        UserModel other = new UserModel();
        other.setId(8L);
        order.setUserOrder(other);
        assertEquals(
                404,
                assertThrows(
                                ResponseStatusException.class,
                                () -> service.getMyItem("client@example.com", 1L))
                        .getStatusCode()
                        .value());
        assertThrows(
                ResponseStatusException.class, () -> service.getMyItems("client@example.com", 10L));
        verify(items, never()).getItemsByOrderId(anyLong());
    }

    @Test
    void adminDetailAddsOrderContextWithoutCurrentCatalogValues() {
        var result = service.getAdminItem(1L);
        assertEquals(10L, result.getOrderId());
        assertEquals(7L, result.getUserId());
        assertEquals(OrderStatus.CONFIRMED, result.getOrderStatus());
        assertEquals(PaymentStatus.PAID, result.getPaymentStatus());
        assertEquals("Nombre comprado", result.getProductName());
        assertEquals(1, service.getAdminItems(10L).size());
    }

    @Test
    void existingEmptyOrderDiffersFromMissingOrderOrItem() {
        when(items.getItemsByOrderId(10L)).thenReturn(List.of());
        assertTrue(service.getAdminItems(10L).isEmpty());
        assertTrue(service.getMyItems("client@example.com", 10L).isEmpty());
        when(orders.getOrderById(10L)).thenReturn(Optional.empty());
        assertEquals(
                404,
                assertThrows(ResponseStatusException.class, () -> service.getAdminItems(10L))
                        .getStatusCode()
                        .value());
        when(items.getOrderItemById(1L)).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class, () -> service.getAdminItem(1L));
    }
}

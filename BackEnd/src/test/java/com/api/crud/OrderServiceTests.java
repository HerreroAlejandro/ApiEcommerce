package com.api.crud;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.api.crud.config.ModelMapperConfig;
import com.api.crud.exceptions.*;
import com.api.crud.models.entity.*;
import com.api.crud.models.enums.*;
import com.api.crud.repositories.*;
import com.api.crud.services.OrderService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.*;

import java.math.BigDecimal;
import java.util.*;

class OrderServiceTests {

    private OrderService service;

    private OrderDao orders;

    private UserDao users;

    private OrderWorkflowDao workflow;

    private UserModel user;

    private ProductPhysical product;

    private Cart cart;

    private Order order;

    @BeforeEach
    void setUp() {
        orders = mock(OrderDao.class);
        users = mock(UserDao.class);
        workflow = mock(OrderWorkflowDao.class);
        service = new OrderService(orders, users, workflow, new ModelMapperConfig().modelMapper());
        user = new UserModel();
        user.setId(7L);
        user.setEmail("client@example.com");
        when(users.getUserByEmail("client@example.com")).thenReturn(Optional.of(user));
        product = new ProductPhysical();
        product.setIdProduct(4L);
        product.setNameProduct("Articulo");
        product.setPriceProduct(new BigDecimal("150.00"));
        product.setStockProduct(5);
        cart = new Cart();
        cart.setUserCart(user);
        cart.setIdCart(12L);
        cart.getItemsCart().add(new CartItem(1L, cart, product, 2));
        when(workflow.lockCart(7L)).thenReturn(Optional.of(cart));
        when(workflow.lockProduct(4L)).thenReturn(product);
        order = new Order();
        order.setIdOrder(10L);
        order.setUserOrder(user);
        order.setOrderStatus(OrderStatus.PENDING);
        order.setPaymentStatus(PaymentStatus.PENDING);
        order.setStockDeducted(true);
        OrderItem line = new OrderItem(order, product, 2, new BigDecimal("100.00"));
        line.setProductName("Nombre comprado");
        order.setItemsOrder(List.of(line));
        order.setTotalOrder(new BigDecimal("200.00"));
        when(orders.getOrderById(10L)).thenReturn(Optional.of(order));
        when(workflow.lockOrder(10L)).thenReturn(order);
        doAnswer(
                        call -> {
                            ((Order) call.getArgument(0)).setIdOrder(11L);
                            return null;
                        })
                .when(orders)
                .saveOrder(any());
    }

    @Test
    void checkoutUsesCurrentPriceCapturesNameDecreasesStockAndClearsCart() {
        var result = service.createMyOrder("client@example.com");
        assertEquals(11L, result.getIdOrder());
        assertEquals(new BigDecimal("300.00"), result.getTotalOrder());
        assertEquals("Articulo", result.getItemsOrder().get(0).getProductName());
        assertEquals(3, product.getStockProduct());
        assertTrue(cart.getItemsCart().isEmpty());
        verify(workflow).lockUser(7L);
        verify(workflow).saveAudit(any(OrderChangeAudit.class));
    }

    @Test
    void unavailableStockDisabledCartAndEmptyCartRejectCheckout() {
        product.setStockProduct(1);
        assertEquals(
                409,
                ExceptionProblems.status(
                                assertThrows(
                                        ApplicationException.class,
                                        () -> service.createMyOrder("client@example.com")))
                        .value());
        cart.setActive(false);
        assertThrows(ApplicationException.class, () -> service.createMyOrder("client@example.com"));
        cart.setActive(true);
        cart.getItemsCart().clear();
        assertThrows(ApplicationException.class, () -> service.createMyOrder("client@example.com"));
        verify(orders, never()).saveOrder(any());
    }

    @Test
    void foreignOrderIsNotVisibleOrCancelable() {
        UserModel other = new UserModel();
        other.setId(8L);
        order.setUserOrder(other);
        assertEquals(
                404,
                ExceptionProblems.status(
                                assertThrows(
                                        ApplicationException.class,
                                        () -> service.getMyOrder("client@example.com", 10L)))
                        .value());
        assertThrows(
                ApplicationException.class,
                () -> service.cancelMyOrder("client@example.com", 10L, "Error"));
        verify(workflow, never()).lockOrder(anyLong());
    }

    @Test
    void legacyOrderWithoutStockDeductionDoesNotIncreaseStockOnCancel() {
        order.setStockDeducted(false);
        service.cancelMyOrder("client@example.com", 10L, "Cancelar orden anterior");
        assertEquals(5, product.getStockProduct());
        verify(workflow, never()).lockProduct(anyLong());
    }

    @Test
    void cancellationRestoresStockOnlyOnce() {
        service.cancelMyOrder("client@example.com", 10L, "Error");
        service.cancelMyOrder("client@example.com", 10L, "Reintento");
        assertEquals(7, product.getStockProduct());
        assertEquals(OrderStatus.CANCELED, order.getOrderStatus());
        verify(workflow, times(1)).lockProduct(4L);
        verify(workflow, times(1)).saveAudit(any());
    }

    @Test
    void paidOrdersRequireAdministrativeRefundBeforeCanceling() {
        order.setPaymentStatus(PaymentStatus.PAID);
        assertThrows(
                ApplicationException.class,
                () -> service.cancelMyOrder("client@example.com", 10L, "Error"));
        assertThrows(
                ApplicationException.class,
                () -> service.cancelAdminOrder("client@example.com", 10L, "Error"));
        service.updatePaymentStatus(
                "client@example.com", 10L, PaymentStatus.REFUNDED, "Reembolso manual confirmado");
        service.cancelAdminOrder("client@example.com", 10L, "Cancelacion luego de reembolso");
        assertEquals(OrderStatus.CANCELED, order.getOrderStatus());
    }

    @Test
    void transitionsRequirePaymentAndPreventReversal() {
        assertThrows(
                ApplicationException.class,
                () ->
                        service.updateOrderStatus(
                                "client@example.com", 10L, OrderStatus.CONFIRMED, "Confirmacion"));
        service.updatePaymentStatus("client@example.com", 10L, PaymentStatus.PAID, "Pago recibido");
        service.updateOrderStatus("client@example.com", 10L, OrderStatus.CONFIRMED, "Confirmacion");
        service.updateOrderStatus("client@example.com", 10L, OrderStatus.SHIPPED, "Envio");
        assertThrows(
                ApplicationException.class,
                () -> service.cancelAdminOrder("client@example.com", 10L, "Cancelar"));
        assertThrows(
                ApplicationException.class,
                () ->
                        service.updateOrderStatus(
                                "client@example.com", 10L, OrderStatus.PENDING, "Volver"));
    }

    @Test
    void detailPreservesPurchasedNameAndPriceAndListScopesUser() {
        product.setNameProduct("Nombre nuevo");
        product.setPriceProduct(new BigDecimal("200.00"));
        var detail = service.getMyOrder("client@example.com", 10L);
        assertEquals("Nombre comprado", detail.getItemsOrder().get(0).getProductName());
        assertEquals(new BigDecimal("100.00"), detail.getItemsOrder().get(0).getPriceOrderItem());
        Pageable page = PageRequest.of(0, 10);
        when(workflow.search(7L, null, null, null, null, null, page))
                .thenReturn(new PageImpl<>(List.of(order)));
        assertEquals(
                1, service.getMyOrders("client@example.com", null, null, page).getTotalElements());
        var admin = service.getAdminOrder(10L);
        assertEquals(7L, admin.getUserId());
        assertEquals("client@example.com", admin.getUserEmail());
    }
}

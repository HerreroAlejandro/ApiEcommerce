package com.api.crud;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.api.crud.DTO.*;
import com.api.crud.config.ModelMapperConfig;
import com.api.crud.exceptions.*;
import com.api.crud.models.entity.*;
import com.api.crud.models.enums.*;
import com.api.crud.repositories.*;
import com.api.crud.services.DigitalDeliveryService;
import com.api.crud.services.OrderService;
import com.api.crud.services.OrderShippingService;

import jakarta.validation.Validation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.data.domain.*;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

class OrderServiceTests {

    private OrderService service;

    private OrderDao orders;

    private UserDao users;

    private OrderWorkflowDao workflow;

    private UserModel user;

    private ProductPhysical product;

    private Cart cart;

    private Order order;

    private static OrderShippingService shippingService() {
        OrderShippingService shipping =
                new OrderShippingService(
                        new ModelMapperConfig().modelMapper(),
                        Validation.buildDefaultValidatorFactory().getValidator());
        ReflectionTestUtils.setField(shipping, "flatRate", "0.00");
        return shipping;
    }

    private static OrderCreateRequestDTO checkoutRequest() {
        ShippingAddressDTO address = new ShippingAddressDTO();
        address.setRecipientName("Cliente Prueba");
        address.setPhone("1112345678");
        address.setStreet("Calle de prueba");
        address.setStreetNumber("123");
        address.setCity("Ciudad");
        address.setRegion("Provincia");
        address.setPostalCode("1234");
        address.setCountryCode("AR");
        OrderCreateRequestDTO request = new OrderCreateRequestDTO();
        request.setShippingAddress(address);
        return request;
    }

    @BeforeEach
    void setUp() {
        orders = mock(OrderDao.class);
        users = mock(UserDao.class);
        workflow = mock(OrderWorkflowDao.class);
        service =
                new OrderService(
                        orders,
                        users,
                        workflow,
                        new ModelMapperConfig().modelMapper(),
                        mock(DigitalDeliveryService.class),
                        shippingService(),
                        mock(PaymentDao.class));
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
        order.setShippingAddress(
                new ModelMapperConfig()
                        .modelMapper()
                        .map(checkoutRequest().getShippingAddress(), OrderShippingAddress.class));
        OrderShipmentTracking tracking = new OrderShipmentTracking();
        tracking.setCarrier("Carrier");
        tracking.setTrackingNumber("TRACK123");
        order.setShipmentTracking(tracking);
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
    void twoBuyersCompetingForLastUnitProduceOnlyOneOrder() throws Exception {
        product.setStockProduct(1);
        cart.getItemsCart().clear();
        cart.getItemsCart().add(new CartItem(1L, cart, product, 1));
        UserModel secondBuyer = new UserModel();
        secondBuyer.setId(8L);
        secondBuyer.setEmail("second@example.com");
        Cart secondCart = new Cart();
        secondCart.setUserCart(secondBuyer);
        secondCart.setIdCart(13L);
        secondCart.getItemsCart().add(new CartItem(2L, secondCart, product, 1));
        when(users.getUserByEmail(secondBuyer.getEmail())).thenReturn(Optional.of(secondBuyer));
        when(workflow.lockCart(8L)).thenReturn(Optional.of(secondCart));

        // Simulates the DAO's product write lock, held until the transaction finishes.
        // Actual MySQL locking must also be checked in an isolated integration environment.
        ReentrantLock productLock = new ReentrantLock();
        when(workflow.lockProduct(4L))
                .thenAnswer(
                        call -> {
                            productLock.lock();
                            return product;
                        });
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> attempts = new ArrayList<>();
            for (String email : List.of(user.getEmail(), secondBuyer.getEmail())) {
                attempts.add(
                        executor.submit(
                                () -> {
                                    assertTrue(start.await(5, TimeUnit.SECONDS));
                                    try {
                                        service.createMyOrder(email, checkoutRequest());
                                        return true;
                                    } catch (InsufficientStockException exception) {
                                        return false;
                                    } finally {
                                        if (productLock.isHeldByCurrentThread())
                                            productLock.unlock();
                                    }
                                }));
            }
            start.countDown();
            int successful = 0;
            for (Future<Boolean> attempt : attempts) {
                if (attempt.get(10, TimeUnit.SECONDS)) successful++;
            }
            assertEquals(1, successful);
            assertEquals(0, product.getStockProduct());
            assertEquals(1, cart.getItemsCart().size() + secondCart.getItemsCart().size());
            verify(orders).saveOrder(any());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void checkoutUsesCurrentPriceCapturesNameDecreasesStockAndClearsCart() {
        var result = service.createMyOrder("client@example.com", checkoutRequest());
        assertEquals(11L, result.getIdOrder());
        assertEquals(result.getPurchaseDateOrder().plusMinutes(30), result.getExpiresAt());
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
                                        () ->
                                                service.createMyOrder(
                                                        "client@example.com", checkoutRequest())))
                        .value());
        cart.setActive(false);
        assertThrows(
                ApplicationException.class,
                () -> service.createMyOrder("client@example.com", checkoutRequest()));
        cart.setActive(true);
        cart.getItemsCart().clear();
        assertThrows(
                ApplicationException.class,
                () -> service.createMyOrder("client@example.com", checkoutRequest()));
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
        order.setPaymentStatus(PaymentStatus.REFUNDED);
        service.cancelRefundedOrder(order);
        assertEquals(OrderStatus.CANCELED, order.getOrderStatus());
    }

    @ParameterizedTest
    @EnumSource(
            value = OrderStatus.class,
            names = {"PENDING", "CONFIRMED"})
    void paymentsUnderReviewCannotBeCanceledOrReleaseStock(OrderStatus status) {
        user.setRoles(Set.of(new Role("ADMIN")));
        order.setOrderStatus(status);
        order.setPaymentStatus(PaymentStatus.REVIEW);

        BusinessConflictException cancellation =
                assertThrows(
                        BusinessConflictException.class,
                        () ->
                                service.cancelAdminOrder(
                                        "client@example.com", 10L, "Cancelar pedido"));
        assertEquals(409, ExceptionProblems.status(cancellation).value());
        assertThrows(
                BusinessConflictException.class,
                () ->
                        service.updateOrderStatus(
                                "client@example.com",
                                10L,
                                OrderStatus.CANCELED,
                                "Cancelar por estado"));
        assertThrows(
                ApplicationException.class,
                () -> service.cancelMyOrder("client@example.com", 10L, "Cancelar compra"));

        assertEquals(status, order.getOrderStatus());
        assertEquals(PaymentStatus.REVIEW, order.getPaymentStatus());
        assertTrue(order.isStockDeducted());
        assertEquals(5, product.getStockProduct());
        verify(workflow, never()).lockProduct(anyLong());
        verify(workflow, never()).saveAudit(any());
    }

    @Test
    void transitionsRequirePaymentAndPreventReversal() {
        user.setRoles(Set.of(new Role("ADMIN")));
        assertThrows(
                ApplicationException.class,
                () ->
                        service.updateOrderStatus(
                                "client@example.com", 10L, OrderStatus.CONFIRMED, "Confirmacion"));
        order.setPaymentStatus(PaymentStatus.PAID);
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

    @Test
    void digitalCheckoutRejectsMultipleUnitsAndCapturesThePurchasedSource() {
        ProductDigital digital = new ProductDigital();
        digital.setIdProduct(4L);
        digital.setActive(true);
        digital.setNameProduct("Archivo");
        digital.setPriceProduct(BigDecimal.TEN);
        digital.setDownloadLink("https://example.com/file.zip");
        when(workflow.lockProduct(4L)).thenReturn(digital);
        cart.getItemsCart().get(0).setProductCartItem(digital);
        assertThrows(
                ApplicationException.class,
                () -> service.createMyOrder("client@example.com", null));
        cart.getItemsCart().get(0).setAmountCartItem(1);
        service.createMyOrder("client@example.com", null);
        var saved = org.mockito.ArgumentCaptor.forClass(Order.class);
        verify(orders).saveOrder(saved.capture());
        assertEquals(
                digital.getDownloadLink(),
                saved.getValue().getItemsOrder().get(0).getDigitalDownloadLink());
    }

    @Test
    void manualStatusChangesCannotInventAPaymentOrRefund() {
        DigitalDeliveryService deliveries =
                (DigitalDeliveryService)
                        org.springframework.test.util.ReflectionTestUtils.getField(
                                service, "digitalDeliveries");
        assertThrows(
                BusinessConflictException.class,
                () ->
                        service.updatePaymentStatus(
                                "client@example.com", 10L, PaymentStatus.PAID, "Pago confirmado"));
        assertThrows(
                BusinessConflictException.class,
                () ->
                        service.updatePaymentStatus(
                                "client@example.com",
                                10L,
                                PaymentStatus.REFUNDED,
                                "Reembolso confirmado"));
        assertEquals(PaymentStatus.PENDING, order.getPaymentStatus());
        verifyNoInteractions(deliveries);
    }

    @Test
    void registeredOnlineCheckoutCannotBeOverriddenByManualStatusUpdates() {
        PaymentDao payments = (PaymentDao) ReflectionTestUtils.getField(service, "payments");
        when(payments.getCheckoutByOrderId(10L)).thenReturn(Optional.of(new PaymentCheckout()));
        assertThrows(
                BusinessConflictException.class,
                () ->
                        service.updatePaymentStatus(
                                "client@example.com",
                                10L,
                                PaymentStatus.PAID,
                                "Confirmacion manual"));
        assertEquals(PaymentStatus.PENDING, order.getPaymentStatus());
    }

    @Test
    void paymentExpiryUsesTheAlreadyLockedOrderWithoutRefreshingChanges() {
        order.setExpiresAt(java.time.LocalDateTime.now().minusMinutes(1));
        order.setPaymentStatus(PaymentStatus.REVIEW);
        assertFalse(service.expireReservedOrder(order));
        assertTrue(order.isStockDeducted());
        verify(workflow, never()).lockOrder(any());
        order.setPaymentStatus(PaymentStatus.PENDING);
        assertTrue(service.expireReservedOrder(order));
        assertEquals(OrderStatus.EXPIRED, order.getOrderStatus());
        assertEquals(7, product.getStockProduct());
        assertFalse(service.expireReservedOrder(order));
        assertEquals(7, product.getStockProduct());
    }

    @Test
    void refundOfDeliveredMerchandisePreservesDeliveryAndDoesNotRestock() {
        order.setOrderStatus(OrderStatus.DELIVERED);
        order.setPaymentStatus(PaymentStatus.REFUNDED);
        service.cancelRefundedOrder(order);
        assertEquals(OrderStatus.DELIVERED, order.getOrderStatus());
        assertEquals(5, product.getStockProduct());
        assertTrue(order.isStockDeducted());
        verify(workflow, never()).lockProduct(anyLong());
        verify(workflow, never()).saveAudit(any());
    }

    @Test
    void verifiedRefundCancelsAnUnshippedOrderAndRestoresStockOnce() {
        order.setPaymentStatus(PaymentStatus.REFUNDED);
        service.cancelRefundedOrder(order);
        assertEquals(OrderStatus.CANCELED, order.getOrderStatus());
        assertEquals(7, product.getStockProduct());
        service.cancelRefundedOrder(order);
        assertEquals(7, product.getStockProduct());
    }
}

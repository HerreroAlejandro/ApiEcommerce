package com.api.crud;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.api.crud.DTO.*;
import com.api.crud.config.*;
import com.api.crud.exceptions.*;
import com.api.crud.models.entity.*;
import com.api.crud.models.entity.Order;
import com.api.crud.models.enums.*;
import com.api.crud.repositories.*;
import com.api.crud.services.*;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.*;
import org.modelmapper.ModelMapper;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

class StoreSaleTests {

    private static final ValidatorFactory VALIDATORS = Validation.buildDefaultValidatorFactory();
    private StoreSaleService service;
    private OrderService lifecycle;
    private OrderWorkflowDao workflow;
    private OrderDao orders;
    private UserDao users;
    private StoreSaleDao sales;
    private PhysicalReturnDao returnDao;
    private PhysicalReturnService returns;
    private PaymentDao payments;
    private ModelMapper mapper;
    private UserModel cashier;
    private ProductPhysical product;
    private Order saved;
    private PhysicalReturn received;

    @AfterAll
    static void closeValidators() {
        VALIDATORS.close();
    }

    @BeforeEach
    void setUp() {
        mapper = new ModelMapperConfig().modelMapper();
        workflow = mock(OrderWorkflowDao.class);
        orders = mock(OrderDao.class);
        users = mock(UserDao.class);
        sales = mock(StoreSaleDao.class);
        returnDao = mock(PhysicalReturnDao.class);
        payments = mock(PaymentDao.class);
        OrderShippingService shipping = new OrderShippingService(mapper, VALIDATORS.getValidator());
        ReflectionTestUtils.setField(shipping, "flatRate", "0.00");
        cashier = new UserModel();
        cashier.setId(7L);
        cashier.setRoles(Set.of(new Role("ADMIN")));
        when(users.getUserByEmail("operator@example.com")).thenReturn(Optional.of(cashier));
        UserModel customer = new UserModel();
        customer.setId(9L);
        customer.setRoles(Set.of(new Role("CLIENT")));
        when(users.getUserByEmail("customer@example.com")).thenReturn(Optional.of(customer));
        product = new ProductPhysical();
        product.setIdProduct(4L);
        product.setActive(true);
        product.setNameProduct("Articulo comprado");
        product.setPriceProduct(new BigDecimal("100.00"));
        product.setStockProduct(5);
        when(workflow.lockProduct(4L)).thenReturn(product);
        when(workflow.lockOrder(10L)).thenAnswer(call -> saved);
        when(orders.getOrderById(10L)).thenAnswer(call -> Optional.ofNullable(saved));
        when(sales.getByReference(anyString()))
                .thenAnswer(
                        call ->
                                saved != null
                                                && saved.getStoreSale() != null
                                                && saved.getStoreSale()
                                                        .getReference()
                                                        .equals(call.getArgument(0))
                                        ? Optional.of(saved)
                                        : Optional.empty());
        doAnswer(
                        call -> {
                            saved = call.getArgument(0);
                            saved.setIdOrder(10L);
                            long id = 20;
                            for (OrderItem line : saved.getItemsOrder()) line.setIdOrderItem(id++);
                            return null;
                        })
                .when(orders)
                .saveOrder(any());
        when(returnDao.getByOrderId(10L)).thenAnswer(call -> Optional.ofNullable(received));
        doAnswer(
                        call -> {
                            received = call.getArgument(0);
                            received.setId(30L);
                            return null;
                        })
                .when(returnDao)
                .save(any());
        returns =
                new PhysicalReturnService(
                        returnDao, workflow, users, mapper, VALIDATORS.getValidator());
        lifecycle =
                new OrderService(
                        orders,
                        users,
                        workflow,
                        mapper,
                        mock(DigitalDeliveryService.class),
                        shipping,
                        payments);
        service =
                new StoreSaleService(
                        sales,
                        orders,
                        workflow,
                        users,
                        shipping,
                        lifecycle,
                        returns,
                        mapper,
                        new ObjectMapper(),
                        VALIDATORS.getValidator());
    }

    private StoreSaleCreateRequestDTO request(boolean immediate) {
        StoreSaleCreateRequestDTO request = new StoreSaleCreateRequestDTO();
        request.setReference(UUID.randomUUID());
        request.setItems(List.of(item(4L, 2)));
        request.setDeliveryMethod(DeliveryMethod.STORE_PICKUP);
        request.setDeliveredImmediately(immediate);
        request.setPaymentMethod(StorePaymentMethod.CASH);
        request.setAmountPaid(new BigDecimal("200.00"));
        request.setReason("Venta presencial ya cobrada");
        if (!immediate) {
            request.setCustomerName("Cliente invitado");
            request.setCustomerPhone("1123456789");
        }
        return request;
    }

    private StoreSaleItemRequestDTO item(long productId, int amount) {
        StoreSaleItemRequestDTO item = new StoreSaleItemRequestDTO();
        item.setProductId(productId);
        item.setQuantity(amount);
        return item;
    }

    private ShippingAddressDTO address() {
        ShippingAddressDTO address = new ShippingAddressDTO();
        address.setRecipientName("Cliente invitado");
        address.setPhone("1123456789");
        address.setStreet("Calle de prueba");
        address.setStreetNumber("123");
        address.setCity("Ciudad");
        address.setRegion("Provincia");
        address.setPostalCode("1234");
        address.setCountryCode("AR");
        return address;
    }

    @Test
    void anonymousImmediateSaleRecordsPaidOrderAndDeliveryWithoutAccountOrReservation() {
        var result = service.create("operator@example.com", request(true));
        assertNull(saved.getUserOrder());
        assertNull(result.getUserId());
        assertNull(result.getUserEmail());
        assertNull(saved.getExpiresAt());
        assertNull(saved.getShippingAddress());
        assertEquals(SalesChannel.IN_STORE, result.getSalesChannel());
        assertEquals(DeliveryMethod.STORE_PICKUP, result.getDeliveryMethod());
        assertEquals(OrderStatus.DELIVERED, result.getOrderStatus());
        assertEquals(PaymentStatus.PAID, result.getPaymentStatus());
        assertEquals(new BigDecimal("200.00"), result.getTotalOrder());
        assertEquals(7L, result.getStoreSale().getCashierUserId());
        assertNotNull(result.getStoreSale().getPaidAt());
        assertNotNull(result.getStoreSale().getDeliveredAt());
        assertEquals(3, product.getStockProduct());
        assertFalse(lifecycle.expireOrder(10L));
        verifyNoInteractions(payments);
        assertDoesNotThrow(() -> lifecycle.getAdminOrder(10L));
        assertNull(mapper.map(saved, OrderAdminListDTO.class).getUserId());
        assertNull(
                mapper.map(saved.getItemsOrder().get(0), OrderItemAdminDetailDTO.class)
                        .getUserId());
    }

    @Test
    void retriesReturnSameSaleAndHistoricalPriceWithoutDeductingStockAgain() {
        var request = request(true);
        var first = service.create("operator@example.com", request);
        product.setPriceProduct(new BigDecimal("150.00"));
        product.setNameProduct("Nombre actual distinto");
        var retry = service.create("operator@example.com", request);
        assertEquals(first.getIdOrder(), retry.getIdOrder());
        assertEquals(new BigDecimal("200.00"), retry.getTotalOrder());
        assertEquals("Articulo comprado", retry.getItemsOrder().get(0).getProductName());
        assertEquals(3, product.getStockProduct());
        verify(orders).saveOrder(any());
        verify(workflow).lockProduct(4L);
        verify(workflow).saveAudit(any());
        request.getItems().get(0).setQuantity(1);
        assertThrows(
                BusinessConflictException.class,
                () -> service.create("operator@example.com", request));
        assertEquals(3, product.getStockProduct());
    }

    @Test
    void paidPickupWaitsForCollectionAndDoesNotRequireShippingTracking() {
        var request = request(false);
        cashier.setRoles(Set.of(new Role("SUPPORT")));
        service.create("operator@example.com", request);
        assertEquals(OrderStatus.CONFIRMED, saved.getOrderStatus());
        assertNull(saved.getStoreSale().getDeliveredAt());
        assertThrows(
                BusinessConflictException.class,
                () ->
                        lifecycle.updateOrderStatus(
                                "operator@example.com",
                                10L,
                                OrderStatus.SHIPPED,
                                "No corresponde"));
        lifecycle.updateOrderStatus(
                "operator@example.com", 10L, OrderStatus.DELIVERED, "Retiro realizado");
        LocalDateTime deliveredAt = saved.getStoreSale().getDeliveredAt();
        lifecycle.updateOrderStatus(
                "operator@example.com", 10L, OrderStatus.DELIVERED, "Reintento");
        assertEquals(deliveredAt, saved.getStoreSale().getDeliveredAt());
        assertNotNull(deliveredAt);
        assertNull(saved.getShipmentTracking());
        assertEquals(3, product.getStockProduct());
    }

    @Test
    void storeShippingCapturesAddressAndUsesExistingShipmentWorkflow() {
        var request = request(false);
        request.setDeliveryMethod(DeliveryMethod.SHIPPING);
        request.setShippingAddress(address());
        request.setPaymentMethod(StorePaymentMethod.CARD_TERMINAL);
        request.setPaymentReference("TERMINAL-123");
        service.create("operator@example.com", request);
        request.getShippingAddress().setStreet("Otra calle");
        assertEquals("Calle de prueba", saved.getShippingAddress().getStreet());
        assertEquals(new BigDecimal("0.00"), saved.getShippingCost());
        ShipmentTrackingUpdateRequestDTO tracking = new ShipmentTrackingUpdateRequestDTO();
        tracking.setCarrier("Transportista");
        tracking.setTrackingNumber("TRACK123");
        tracking.setReason("Despacho");
        lifecycle.updateShipment("operator@example.com", 10L, tracking);
        lifecycle.updateOrderStatus("operator@example.com", 10L, OrderStatus.SHIPPED, "Enviado");
        lifecycle.updateOrderStatus(
                "operator@example.com", 10L, OrderStatus.DELIVERED, "Entregado");
        assertNotNull(saved.getShipmentTracking().getDeliveredAt());
        assertEquals("TERMINAL-123", saved.getStoreSale().getPaymentReference());
    }

    @Test
    void incompatibleDeliveryContactAndPaymentDataAreRejected() {
        var request = request(false);
        request.setCustomerName(null);
        assertThrows(
                InvalidRequestException.class,
                () -> service.create("operator@example.com", request));
        request.setCustomerName("Cliente");
        request.setShippingAddress(address());
        assertThrows(
                InvalidRequestException.class,
                () -> service.create("operator@example.com", request));
        request.setDeliveryMethod(DeliveryMethod.SHIPPING);
        request.setDeliveredImmediately(true);
        assertThrows(
                InvalidRequestException.class,
                () -> service.create("operator@example.com", request));
        request.setDeliveredImmediately(false);
        request.setPaymentMethod(StorePaymentMethod.CARD_TERMINAL);
        assertThrows(
                InvalidRequestException.class,
                () -> service.create("operator@example.com", request));
        assertEquals(5, product.getStockProduct());
        verify(orders, never()).saveOrder(any());
    }

    @Test
    void insufficientStockInAnotherLineDoesNotPartiallyApplySale() {
        ProductPhysical second = new ProductPhysical();
        second.setIdProduct(5L);
        second.setActive(true);
        second.setPriceProduct(BigDecimal.TEN);
        second.setStockProduct(1);
        when(workflow.lockProduct(5L)).thenReturn(second);
        var request = request(true);
        request.setItems(List.of(item(4, 2), item(5, 2)));
        assertThrows(
                InsufficientStockException.class,
                () -> service.create("operator@example.com", request));
        assertEquals(5, product.getStockProduct());
        assertEquals(1, second.getStockProduct());
        verify(orders, never()).saveOrder(any());
        request.setItems(List.of(item(4, 1), item(4, 2)));
        assertThrows(
                InvalidRequestException.class,
                () -> service.create("operator@example.com", request));
    }

    @Test
    void inactiveAndDigitalProductsCannotBeSoldThroughThisFlow() {
        product.setActive(false);
        assertThrows(
                BusinessConflictException.class,
                () -> service.create("operator@example.com", request(true)));
        when(workflow.lockProduct(4L)).thenReturn(new ProductDigital());
        assertThrows(
                InvalidRequestException.class,
                () -> service.create("operator@example.com", request(true)));
        assertEquals(5, product.getStockProduct());
        verify(orders, never()).saveOrder(any());
    }

    @Test
    void saleTotalBeyondStorageLimitIsRejectedBeforeDeductingStock() {
        product.setPriceProduct(new BigDecimal("50000000.00"));
        assertThrows(
                InvalidRequestException.class,
                () -> service.create("operator@example.com", request(true)));
        assertEquals(5, product.getStockProduct());
        verify(orders, never()).saveOrder(any());
    }

    @Test
    void collectedAmountMustMatchServerPricesBeforeAnyStockChange() {
        var request = request(true);
        request.setAmountPaid(new BigDecimal("150.00"));
        assertThrows(
                BusinessConflictException.class,
                () -> service.create("operator@example.com", request));
        assertEquals(5, product.getStockProduct());
        verify(orders, never()).saveOrder(any());
    }

    @Test
    void storeAndOnlineSalesCompeteForTheSameLastUnit() throws Exception {
        product.setStockProduct(1);
        UserModel customer = users.getUserByEmail("customer@example.com").orElseThrow();
        Cart cart = new Cart();
        cart.setUserCart(customer);
        cart.setIdCart(12L);
        cart.getItemsCart().add(new CartItem(1L, cart, product, 1));
        when(workflow.lockCart(9L)).thenReturn(Optional.of(cart));
        ReentrantLock lock = new ReentrantLock();
        // Models the shared database lock; a MySQL integration test remains necessary.
        when(workflow.lockProduct(4L))
                .thenAnswer(
                        call -> {
                            lock.lock();
                            return product;
                        });
        var local = request(true);
        local.setItems(List.of(item(4L, 1)));
        local.setAmountPaid(new BigDecimal("100.00"));
        var online = new OrderCreateRequestDTO();
        online.setShippingAddress(address());
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> attempts = new ArrayList<>();
            for (boolean store : List.of(true, false)) {
                attempts.add(
                        executor.submit(
                                () -> {
                                    assertTrue(start.await(5, TimeUnit.SECONDS));
                                    try {
                                        if (store) service.create("operator@example.com", local);
                                        else
                                            lifecycle.createMyOrder("customer@example.com", online);
                                        return true;
                                    } catch (InsufficientStockException exception) {
                                        return false;
                                    } finally {
                                        if (lock.isHeldByCurrentThread()) lock.unlock();
                                    }
                                }));
            }
            start.countDown();
            int successes = 0;
            for (var attempt : attempts) if (attempt.get(10, TimeUnit.SECONDS)) successes++;
            assertEquals(1, successes);
            assertEquals(0, product.getStockProduct());
            verify(orders).saveOrder(any());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void refundBeforePickupCancelsAndRestoresStockOnce() {
        service.create("operator@example.com", request(false));
        var result = service.refund("operator@example.com", 10L, "Dinero devuelto en caja");
        assertEquals(PaymentStatus.REFUNDED, result.getPaymentStatus());
        assertEquals(OrderStatus.CANCELED, result.getOrderStatus());
        assertEquals(5, product.getStockProduct());
        assertNotNull(result.getStoreSale().getRefundedAt());
        assertEquals(7L, result.getStoreSale().getRefundedBy());
        service.refund("operator@example.com", 10L, "Reintento");
        assertEquals(5, product.getStockProduct());
        assertEquals("Dinero devuelto en caja", saved.getStoreSale().getRefundReason());
        verify(workflow)
                .saveAudit(
                        argThat(
                                audit ->
                                        "CANCEL".equals(audit.getAction())
                                                && audit.getReason()
                                                        .equals(
                                                                "Cancelacion por reembolso total"
                                                                    + " presencial registrado")));
    }

    @Test
    void refundAfterDeliveryRequiresPhysicalReturnAndDoesNotRestockTwice() {
        service.create("operator@example.com", request(true));
        assertThrows(
                BusinessConflictException.class,
                () -> service.refund("operator@example.com", 10L, "Sin retorno registrado"));
        returns.request("operator@example.com", 10L, "Devolucion total");
        PhysicalReturnItemRequestDTO line = new PhysicalReturnItemRequestDTO();
        line.setOrderItemId(20L);
        line.setReceivedQuantity(2);
        line.setRestockedQuantity(1);
        PhysicalReturnReceiveRequestDTO receipt = new PhysicalReturnReceiveRequestDTO();
        receipt.setReason("Una unidad danada");
        receipt.setItems(List.of(line));
        returns.receive("operator@example.com", 10L, receipt);
        service.refund("operator@example.com", 10L, "Dinero devuelto");
        assertEquals(OrderStatus.DELIVERED, saved.getOrderStatus());
        assertEquals(PaymentStatus.REFUNDED, saved.getPaymentStatus());
        assertEquals(4, product.getStockProduct());
        service.refund("operator@example.com", 10L, "Reintento");
        assertEquals(4, product.getStockProduct());
    }

    @Test
    void guestOrdersAreHiddenFromCustomerOrderItemPaymentAndCheckoutQueries() {
        service.create("operator@example.com", request(true));
        assertThrows(
                ResourceNotFoundException.class,
                () -> lifecycle.getMyOrder("customer@example.com", 10L));
        OrderItemDao items = mock(OrderItemDao.class);
        when(items.getOrderItemById(20L)).thenReturn(Optional.of(saved.getItemsOrder().get(0)));
        OrderItemService itemService = new OrderItemService(items, orders, users, mapper);
        assertThrows(
                ResourceNotFoundException.class,
                () -> itemService.getMyItem("customer@example.com", 20L));
        assertThrows(
                ResourceNotFoundException.class,
                () -> itemService.getMyItems("customer@example.com", 10L));
        MercadoPagoClient client = mock(MercadoPagoClient.class);
        PaymentService paymentService =
                new PaymentService(
                        mock(PaymentCheckoutPersistence.class),
                        mock(PaymentConfirmationService.class),
                        client,
                        mock(MercadoPagoWebhookVerifier.class),
                        payments,
                        orders,
                        users,
                        mapper,
                        mock(ChargebackService.class));
        assertThrows(
                ResourceNotFoundException.class,
                () -> paymentService.getMyPayments("customer@example.com", 10L));
        MercadoPagoProperties properties = new MercadoPagoProperties();
        properties.setEnabled(true);
        PaymentCheckoutPersistence checkout =
                new PaymentCheckoutPersistence(
                        payments, orders, workflow, users, client, properties, mapper);
        assertThrows(
                ResourceNotFoundException.class,
                () -> checkout.prepare("customer@example.com", 10L));
        verifyNoInteractions(client);
    }

    @Test
    void clientsCannotCreateSalesAndSupportCannotRecordRefundsOrAlterOnlinePayments() {
        assertThrows(
                ForbiddenOperationException.class,
                () -> service.create("customer@example.com", request(true)));
        service.create("operator@example.com", request(true));
        cashier.setRoles(Set.of(new Role("SUPPORT")));
        assertThrows(
                ForbiddenOperationException.class,
                () -> service.refund("operator@example.com", 10L, "Devolver"));
        cashier.setRoles(Set.of(new Role("ADMIN")));
        saved.setSalesChannel(SalesChannel.ONLINE);
        assertThrows(
                BusinessConflictException.class,
                () -> service.refund("operator@example.com", 10L, "Devolver"));
        assertThrows(
                BusinessConflictException.class,
                () ->
                        lifecycle.updatePaymentStatus(
                                "operator@example.com",
                                10L,
                                PaymentStatus.REFUNDED,
                                "Cambio generico"));
        assertEquals(PaymentStatus.PAID, saved.getPaymentStatus());
    }
}

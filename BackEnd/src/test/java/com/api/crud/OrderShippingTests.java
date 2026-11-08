package com.api.crud;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.api.crud.DTO.*;
import com.api.crud.config.ModelMapperConfig;
import com.api.crud.exceptions.*;
import com.api.crud.models.entity.*;
import com.api.crud.models.entity.Order;
import com.api.crud.models.enums.*;
import com.api.crud.repositories.*;
import com.api.crud.services.*;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.modelmapper.ModelMapper;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

class OrderShippingTests {

    private static final ValidatorFactory VALIDATORS = Validation.buildDefaultValidatorFactory();

    private ModelMapper mapper;
    private OrderShippingService shipping;
    private OrderService service;
    private OrderDao orders;
    private OrderWorkflowDao workflow;
    private ProductPhysical physical;
    private Cart cart;
    private UserModel user;
    private OrderCreateRequestDTO request;

    @AfterAll
    static void closeValidators() {
        VALIDATORS.close();
    }

    @BeforeEach
    void setUp() {
        mapper = new ModelMapperConfig().modelMapper();
        shipping = new OrderShippingService(mapper, VALIDATORS.getValidator());
        ReflectionTestUtils.setField(shipping, "flatRate", "50.00");
        orders = mock(OrderDao.class);
        workflow = mock(OrderWorkflowDao.class);
        UserDao users = mock(UserDao.class);
        service =
                new OrderService(
                        orders,
                        users,
                        workflow,
                        mapper,
                        mock(DigitalDeliveryService.class),
                        shipping);
        user = new UserModel();
        user.setId(7L);
        user.setFirstName("Nombre del perfil");
        user.setRoles(Set.of(new Role("SUPPORT")));
        when(users.getUserByEmail("buyer@example.com")).thenReturn(Optional.of(user));
        physical = new ProductPhysical();
        physical.setIdProduct(1L);
        physical.setPriceProduct(new BigDecimal("100.00"));
        physical.setNameProduct("Articulo");
        physical.setStockProduct(10);
        cart = new Cart();
        cart.setUserCart(user);
        cart.setItemsCart(new ArrayList<>(List.of(new CartItem(1L, cart, physical, 2))));
        when(workflow.lockCart(7L)).thenReturn(Optional.of(cart));
        when(workflow.lockProduct(1L)).thenReturn(physical);
        doAnswer(
                        call -> {
                            Order order = call.getArgument(0);
                            order.setIdOrder(10L);
                            when(workflow.lockOrder(10L)).thenReturn(order);
                            when(orders.getOrderById(10L)).thenReturn(Optional.of(order));
                            return null;
                        })
                .when(orders)
                .saveOrder(any());
        request = addressRequest();
    }

    private static OrderCreateRequestDTO addressRequest() {
        ShippingAddressDTO address = new ShippingAddressDTO();
        address.setRecipientName("Destinatario de compra");
        address.setPhone("+54 11 1234-5678");
        address.setStreet("Calle de prueba");
        address.setStreetNumber("123");
        address.setApartment("Piso 2");
        address.setCity("Ciudad");
        address.setRegion("Provincia");
        address.setPostalCode("1234");
        address.setCountryCode("AR");
        OrderCreateRequestDTO request = new OrderCreateRequestDTO();
        request.setShippingAddress(address);
        return request;
    }

    private Order savedOrder() {
        ArgumentCaptor<Order> saved = ArgumentCaptor.forClass(Order.class);
        verify(orders).saveOrder(saved.capture());
        return saved.getValue();
    }

    private static ShipmentTrackingUpdateRequestDTO tracking(String number) {
        ShipmentTrackingUpdateRequestDTO request = new ShipmentTrackingUpdateRequestDTO();
        request.setCarrier("Transportista");
        request.setTrackingNumber(number);
        request.setTrackingUrl("https://example.com/track");
        request.setReason("Despacho de la compra");
        return request;
    }

    @Test
    void physicalCheckoutSnapshotsAddressAndPriceBreakdown() {
        var result = service.createMyOrder("buyer@example.com", request);
        assertEquals(new BigDecimal("200.00"), result.getItemsSubtotal());
        assertEquals(new BigDecimal("50.00"), result.getShippingCost());
        assertEquals(new BigDecimal("250.00"), result.getTotalOrder());
        assertEquals("Destinatario de compra", result.getShippingAddress().getRecipientName());
        request.getShippingAddress().setStreet("Otra calle");
        user.setFirstName("Otro nombre");
        ReflectionTestUtils.setField(shipping, "flatRate", "75.00");
        Order saved = savedOrder();
        assertEquals("Calle de prueba", saved.getShippingAddress().getStreet());
        assertEquals("Destinatario de compra", saved.getShippingAddress().getRecipientName());
        assertEquals(new BigDecimal("50.00"), saved.getShippingCost());
        assertEquals(
                new BigDecimal("250.00"),
                service.getMyOrder("buyer@example.com", 10L).getTotalOrder());
    }

    @Test
    void missingAddressAndUnconfiguredRateDoNotDeductStockOrClearCart() {
        assertThrows(
                InvalidRequestException.class,
                () -> service.createMyOrder("buyer@example.com", null));
        ReflectionTestUtils.setField(shipping, "flatRate", "");
        assertThrows(
                ExternalServiceException.class,
                () -> service.createMyOrder("buyer@example.com", request));
        assertEquals(10, physical.getStockProduct());
        assertEquals(1, cart.getItemsCart().size());
        verify(orders, never()).saveOrder(any());
    }

    @Test
    void addressFieldsAndCountryAreValidatedInTheService() {
        request.getShippingAddress().setPostalCode("");
        assertThrows(
                ConstraintViolationException.class,
                () -> service.createMyOrder("buyer@example.com", request));
        request = addressRequest();
        request.getShippingAddress().setCountryCode("ZZ");
        assertThrows(
                InvalidRequestException.class,
                () -> service.createMyOrder("buyer@example.com", request));
        assertEquals(10, physical.getStockProduct());
    }

    @Test
    void digitalOnlyOrderNeedsNeitherAddressNorConfiguredRate() {
        ProductDigital digital = new ProductDigital();
        digital.setIdProduct(2L);
        digital.setNameProduct("Archivo");
        digital.setPriceProduct(new BigDecimal("30.00"));
        digital.setDownloadLink("https://example.com/file.zip");
        cart.setItemsCart(new ArrayList<>(List.of(new CartItem(2L, cart, digital, 1))));
        when(workflow.lockProduct(2L)).thenReturn(digital);
        ReflectionTestUtils.setField(shipping, "flatRate", "");
        var result = service.createMyOrder("buyer@example.com", null);
        assertNull(result.getShippingAddress());
        assertEquals(new BigDecimal("0.00"), result.getShippingCost());
        assertEquals(new BigDecimal("30.00"), result.getTotalOrder());
    }

    @Test
    void mixedOrderPaysTheFlatRateOnce() {
        ProductDigital digital = new ProductDigital();
        digital.setIdProduct(2L);
        digital.setNameProduct("Archivo");
        digital.setPriceProduct(new BigDecimal("30.00"));
        digital.setDownloadLink("https://example.com/file.zip");
        cart.getItemsCart().add(new CartItem(2L, cart, digital, 1));
        when(workflow.lockProduct(2L)).thenReturn(digital);
        var result = service.createMyOrder("buyer@example.com", request);
        assertEquals(new BigDecimal("230.00"), result.getItemsSubtotal());
        assertEquals(new BigDecimal("50.00"), result.getShippingCost());
        assertEquals(new BigDecimal("280.00"), result.getTotalOrder());
    }

    @Test
    void shipmentRequiresPaymentAndTrackingAndRecordsTimestamps() {
        service.createMyOrder("buyer@example.com", request);
        Order order = savedOrder();
        assertThrows(
                BusinessConflictException.class,
                () -> service.updateShipment("buyer@example.com", 10L, tracking("TRACK123")));
        order.setPaymentStatus(PaymentStatus.PAID);
        order.setOrderStatus(OrderStatus.CONFIRMED);
        assertThrows(
                BusinessConflictException.class,
                () ->
                        service.updateOrderStatus(
                                "buyer@example.com", 10L, OrderStatus.SHIPPED, "Enviar"));
        service.updateShipment("buyer@example.com", 10L, tracking("TRACK123"));
        service.updateOrderStatus("buyer@example.com", 10L, OrderStatus.SHIPPED, "Enviar");
        LocalDateTime shippedAt = order.getShipmentTracking().getShippedAt();
        assertNotNull(shippedAt);
        service.updateShipment("buyer@example.com", 10L, tracking("TRACK456"));
        assertEquals(shippedAt, order.getShipmentTracking().getShippedAt());
        service.updateOrderStatus(
                "buyer@example.com", 10L, OrderStatus.DELIVERED, "Entrega confirmada");
        assertNotNull(order.getShipmentTracking().getDeliveredAt());
        assertThrows(
                BusinessConflictException.class,
                () -> service.updateShipment("buyer@example.com", 10L, tracking("TRACK789")));
    }

    @Test
    void trackingCorrectionsAreAuditedAndDoNotChangeTheDeliveryAddress() {
        service.createMyOrder("buyer@example.com", request);
        Order order = savedOrder();
        order.setOrderStatus(OrderStatus.CONFIRMED);
        order.setPaymentStatus(PaymentStatus.PAID);
        service.updateShipment("buyer@example.com", 10L, tracking("TRACK123"));
        clearInvocations(workflow);
        service.updateShipment("buyer@example.com", 10L, tracking("TRACK456"));
        ArgumentCaptor<OrderChangeAudit> audit = ArgumentCaptor.forClass(OrderChangeAudit.class);
        verify(workflow).saveAudit(audit.capture());
        assertEquals("TRACK123", audit.getValue().getPreviousValue());
        assertEquals("TRACK456", audit.getValue().getNewValue());
        assertEquals("Calle de prueba", order.getShippingAddress().getStreet());
        assertEquals(
                "TRACK456", service.getAdminOrder(10L).getShipmentTracking().getTrackingNumber());
    }

    @Test
    void clientCannotManageShipmentAndUnsafeTrackingUrlIsRejected() {
        service.createMyOrder("buyer@example.com", request);
        Order order = savedOrder();
        order.setOrderStatus(OrderStatus.CONFIRMED);
        order.setPaymentStatus(PaymentStatus.PAID);
        user.setRoles(Set.of(new Role("CLIENT")));
        assertThrows(
                ForbiddenOperationException.class,
                () -> service.updateShipment("buyer@example.com", 10L, tracking("TRACK123")));
        ShipmentTrackingUpdateRequestDTO invalid = tracking("TRACK123");
        invalid.setTrackingUrl("javascript:alert(1)");
        assertThrows(
                ConstraintViolationException.class, () -> shipping.updateTracking(order, invalid));
    }

    @Test
    void freeShippingRequiresExplicitZeroAndInvalidRateIsRejected() {
        ReflectionTestUtils.setField(shipping, "flatRate", "-1");
        assertThrows(IllegalStateException.class, shipping::getFlatRate);
        ReflectionTestUtils.setField(shipping, "flatRate", "1.001");
        assertThrows(IllegalStateException.class, shipping::getFlatRate);
        ReflectionTestUtils.setField(shipping, "flatRate", "0");
        assertEquals(new BigDecimal("0.00"), shipping.getFlatRate());
    }

    @Test
    void shippingCannotPushTheGrandTotalBeyondTheDatabaseLimit() {
        physical.setPriceProduct(new BigDecimal("49999999.99"));
        assertThrows(
                BusinessConflictException.class,
                () -> service.createMyOrder("buyer@example.com", request));
        verify(orders, never()).saveOrder(any());
        assertFalse(cart.getItemsCart().isEmpty());
    }
}

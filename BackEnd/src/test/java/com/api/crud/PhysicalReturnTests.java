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
import com.api.crud.services.PhysicalReturnService;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.util.*;

class PhysicalReturnTests {
    private static final ValidatorFactory VALIDATORS = Validation.buildDefaultValidatorFactory();
    private PhysicalReturnDao returns;
    private OrderWorkflowDao workflow;
    private PhysicalReturnService service;
    private UserModel actor;
    private Order order;
    private ProductPhysical product;
    private PhysicalReturn saved;

    @AfterAll
    static void closeValidators() {
        VALIDATORS.close();
    }

    @BeforeEach
    void setUp() {
        returns = mock(PhysicalReturnDao.class);
        workflow = mock(OrderWorkflowDao.class);
        UserDao users = mock(UserDao.class);
        actor = new UserModel();
        actor.setId(7L);
        actor.setRoles(Set.of(new Role("ADMIN")));
        when(users.getUserByEmail("admin@example.com")).thenReturn(Optional.of(actor));
        product = new ProductPhysical();
        product.setIdProduct(4L);
        product.setStockProduct(3);
        order = new Order();
        order.setIdOrder(10L);
        order.setOrderStatus(OrderStatus.DELIVERED);
        order.setPaymentStatus(PaymentStatus.PAID);
        order.setStockDeducted(true);
        OrderItem item = new OrderItem(order, product, 2, BigDecimal.TEN);
        item.setIdOrderItem(20L);
        order.setItemsOrder(List.of(item));
        when(workflow.lockOrder(10L)).thenReturn(order);
        when(workflow.lockProduct(4L)).thenReturn(product);
        when(returns.getByOrderId(10L)).thenAnswer(call -> Optional.ofNullable(saved));
        doAnswer(
                        call -> {
                            saved = call.getArgument(0);
                            saved.setId(30L);
                            return null;
                        })
                .when(returns)
                .save(any());
        service =
                new PhysicalReturnService(
                        returns,
                        workflow,
                        users,
                        new ModelMapperConfig().modelMapper(),
                        VALIDATORS.getValidator());
    }

    private PhysicalReturnReceiveRequestDTO receipt(int received, int restocked) {
        PhysicalReturnItemRequestDTO line = new PhysicalReturnItemRequestDTO();
        line.setOrderItemId(20L);
        line.setReceivedQuantity(received);
        line.setRestockedQuantity(restocked);
        PhysicalReturnReceiveRequestDTO request = new PhysicalReturnReceiveRequestDTO();
        request.setReason("Inspeccion: una unidad danada");
        request.setItems(List.of(line));
        return request;
    }

    @Test
    void fullReturnRestocksOnlySaleableQuantityOnceAndKeepsDeliveryAndPaymentHistory() {
        assertThrows(BusinessConflictException.class, () -> service.requireRefundReady(order));
        var request = service.request("admin@example.com", 10L, " Devolucion acordada ");
        assertEquals(10L, request.getOrderId());
        assertEquals("Devolucion acordada", request.getReason());
        service.request("admin@example.com", 10L, "Reintento");
        verify(returns).save(any());
        assertThrows(BusinessConflictException.class, () -> service.requireRefundReady(order));
        var result = service.receive("admin@example.com", 10L, receipt(2, 1));
        assertEquals(PhysicalReturnStatus.RECEIVED, result.getStatus());
        assertEquals(7L, result.getResolvedBy());
        assertNotNull(result.getResolvedAt());
        assertEquals(1, result.getItems().get(0).getRestockedQuantity());
        assertEquals(4, product.getStockProduct());
        service.receive("admin@example.com", 10L, receipt(2, 1));
        assertEquals(4, product.getStockProduct());
        assertEquals(OrderStatus.DELIVERED, order.getOrderStatus());
        assertEquals(PaymentStatus.PAID, order.getPaymentStatus());
        assertDoesNotThrow(() -> service.requireRefundReady(order));
        verify(workflow, times(2)).saveAudit(any());
    }

    @Test
    void damagedReturnCanBeReceivedWithoutRestocking() {
        service.request("admin@example.com", 10L, "Danado");
        service.receive("admin@example.com", 10L, receipt(2, 0));
        assertEquals(3, product.getStockProduct());
        assertEquals(PhysicalReturnStatus.RECEIVED, saved.getStatus());
    }

    @Test
    void partialForeignDuplicateOrExcessQuantitiesAreRejectedBeforeStockChanges() {
        service.request("admin@example.com", 10L, "Devolucion");
        assertThrows(
                InvalidRequestException.class,
                () -> service.receive("admin@example.com", 10L, receipt(1, 1)));
        assertThrows(
                InvalidRequestException.class,
                () -> service.receive("admin@example.com", 10L, receipt(2, 3)));
        var foreign = receipt(2, 1);
        foreign.getItems().get(0).setOrderItemId(99L);
        assertThrows(
                InvalidRequestException.class,
                () -> service.receive("admin@example.com", 10L, foreign));
        var duplicate = receipt(2, 1);
        duplicate.setItems(List.of(duplicate.getItems().get(0), duplicate.getItems().get(0)));
        assertThrows(
                InvalidRequestException.class,
                () -> service.receive("admin@example.com", 10L, duplicate));
        assertEquals(3, product.getStockProduct());
        assertEquals(PhysicalReturnStatus.REQUESTED, saved.getStatus());
        verify(workflow, never()).lockProduct(anyLong());
    }

    @Test
    void changedReceiptCannotRestockAgainAndOverflowIsRejected() {
        service.request("admin@example.com", 10L, "Devolucion");
        product.setStockProduct(Integer.MAX_VALUE);
        assertThrows(
                BusinessConflictException.class,
                () -> service.receive("admin@example.com", 10L, receipt(2, 1)));
        assertEquals(PhysicalReturnStatus.REQUESTED, saved.getStatus());
        product.setStockProduct(3);
        service.receive("admin@example.com", 10L, receipt(2, 1));
        assertThrows(
                BusinessConflictException.class,
                () -> service.receive("admin@example.com", 10L, receipt(2, 2)));
        assertEquals(4, product.getStockProduct());
    }

    @Test
    void explicitWaiverAllowsRefundWithoutRestockingAndKeepsItsReason() {
        service.request("admin@example.com", 10L, "Devolucion");
        assertThrows(
                InvalidRequestException.class, () -> service.waive("admin@example.com", 10L, " "));
        var result =
                service.waive("admin@example.com", 10L, "Producto destruido: no solicitar retorno");
        service.waive("admin@example.com", 10L, "Reintento");
        assertEquals(PhysicalReturnStatus.WAIVED, result.getStatus());
        assertTrue(result.getResolutionReason().contains("destruido"));
        assertDoesNotThrow(() -> service.requireRefundReady(order));
        assertThrows(
                BusinessConflictException.class,
                () -> service.receive("admin@example.com", 10L, receipt(2, 1)));
        assertEquals(3, product.getStockProduct());
        verify(workflow, never()).lockProduct(anyLong());
        verify(workflow, times(2)).saveAudit(any());
    }

    @Test
    void unshippedOrDigitalOrdersDoNotRequireAPhysicalReturnButCannotCreateOne() {
        order.setOrderStatus(OrderStatus.CONFIRMED);
        assertDoesNotThrow(() -> service.requireRefundReady(order));
        assertThrows(
                BusinessConflictException.class,
                () -> service.request("admin@example.com", 10L, "Devolver"));
        order.setOrderStatus(OrderStatus.DELIVERED);
        order.getItemsOrder().get(0).setProductOrderItem(new ProductDigital());
        assertDoesNotThrow(() -> service.requireRefundReady(order));
        assertThrows(
                BusinessConflictException.class,
                () -> service.request("admin@example.com", 10L, "Devolver"));
    }

    @Test
    void supportCanReadButCannotResolveAndClientsCannotRead() {
        service.request("admin@example.com", 10L, "Devolver");
        actor.setRoles(Set.of(new Role("SUPPORT")));
        assertEquals(30L, service.get("admin@example.com", 10L).getId());
        assertThrows(
                ForbiddenOperationException.class,
                () -> service.receive("admin@example.com", 10L, receipt(2, 1)));
        assertThrows(
                ForbiddenOperationException.class,
                () -> service.waive("admin@example.com", 10L, "Excepcion"));
        actor.setRoles(Set.of(new Role("CLIENT")));
        assertThrows(
                ForbiddenOperationException.class, () -> service.get("admin@example.com", 10L));
    }
}

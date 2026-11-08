package com.api.crud;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.api.crud.models.entity.*;
import com.api.crud.repositories.*;
import com.api.crud.services.CartItemService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

class CartItemServiceTests {

    private CartItemService service;

    private CartItemDao items;

    private CartDao carts;

    private UserDao users;

    private ProductDao products;

    private CartItem item;

    private CartChangeAuditDao audits;

    @BeforeEach
    void setUp() {
        service = new CartItemService();
        items = mock(CartItemDao.class);
        carts = mock(CartDao.class);
        users = mock(UserDao.class);
        products = mock(ProductDao.class);
        audits = mock(CartChangeAuditDao.class);
        ReflectionTestUtils.setField(service, "cartChangeAuditDao", audits);
        ReflectionTestUtils.setField(service, "cartItemDao", items);
        ReflectionTestUtils.setField(service, "cartDao", carts);
        ReflectionTestUtils.setField(service, "userDao", users);
        ReflectionTestUtils.setField(service, "productDao", products);
        UserModel user = new UserModel();
        user.setId(7L);
        Cart cart = new Cart();
        cart.setIdCart(12L);
        cart.setUserCart(user);
        ProductPhysical product = new ProductPhysical();
        product.setPriceProduct(new BigDecimal("150.00"));
        product.setStockProduct(5);
        item = new CartItem();
        item.setIdCartItem(1L);
        item.setCart(cart);
        item.setProductCartItem(product);
        item.setAmountCartItem(2);
        cart.getItemsCart().add(item);
        when(users.getUserByEmail("client@example.com")).thenReturn(Optional.of(user));
        when(items.getCartItemById(1L)).thenReturn(Optional.of(item));
        when(carts.getOrCreateActiveCart(7L)).thenReturn(cart);
        when(products.getProductById(4L)).thenReturn(Optional.of(product));
    }

    @Test
    void disablingPreservesItemsAndRecordsAudit() {
        when(carts.getCartById(12L)).thenReturn(Optional.of(item.getCart()));
        service.changeAdminCartState("client@example.com", 12L, false, "Revision");
        assertFalse(item.getCart().isActive());
        assertEquals(1, item.getCart().getItemsCart().size());
        var capture = ArgumentCaptor.forClass(CartChangeAudit.class);
        verify(audits).save(capture.capture());
        assertEquals("DISABLE", capture.getValue().getAction());
        assertThrows(
                ResponseStatusException.class,
                () -> service.increaseMyItemAmount("client@example.com", 1L, 1));
        service.changeAdminCartState("client@example.com", 12L, true, "Revision finalizada");
        assertTrue(item.getCart().isActive());
    }

    @Test
    void extremeDeletionAuditsCartAndItemsBeforeDeleting() {
        when(carts.getCartById(12L)).thenReturn(Optional.of(item.getCart()));
        service.deleteAdminCart("client@example.com", 12L, "Caso extremo");
        var ordered = inOrder(audits, carts);
        ordered.verify(carts).getCartById(12L);
        ordered.verify(audits, times(2)).save(any(CartChangeAudit.class));
        ordered.verify(carts).deleteCartById(12L);
    }

    @Test
    void adminQuantityChangeRecordsActorTargetAndPreviousAmount() {
        UserModel actor = new UserModel();
        actor.setId(99L);
        when(users.getUserByEmail("admin@example.com")).thenReturn(Optional.of(actor));
        service.updateAdminItemAmount("admin@example.com", 1L, 4, "Asistencia");
        var capture = ArgumentCaptor.forClass(CartChangeAudit.class);
        verify(audits).save(capture.capture());
        CartChangeAudit record = capture.getValue();
        assertEquals(99L, record.getActorUserId());
        assertEquals(7L, record.getTargetUserId());
        assertEquals(2, record.getPreviousAmount());
        assertEquals(4, record.getNewAmount());
        assertEquals("Asistencia", record.getReason());
        assertNotNull(record.getCreatedAt());
        assertEquals(4, item.getAmountCartItem());
    }

    @Test
    void adminRejectsInvalidReasonOrStockWithoutRecordingChange() {
        assertThrows(
                ResponseStatusException.class,
                () -> service.updateAdminItemAmount("client@example.com", 1L, 3, " "));
        assertEquals(
                409,
                assertThrows(
                                ResponseStatusException.class,
                                () ->
                                        service.increaseAdminItemAmount(
                                                "client@example.com", 1L, 4, "Asistencia"))
                        .getStatusCode()
                        .value());
        assertEquals(2, item.getAmountCartItem());
        verifyNoInteractions(audits);
    }

    @Test
    void adminCanRemoveInactiveProductAndClearCartWithAudit() {
        item.getProductCartItem().setActive(false);
        service.removeAdminItem("client@example.com", 1L, "Producto retirado");
        verify(items).removeCartItem(1L);
        verify(audits).save(any(CartChangeAudit.class));
        when(carts.getCartById(12L)).thenReturn(Optional.of(item.getCart()));
        service.clearAdminCart("client@example.com", 12L, "Reiniciar carrito");
        verify(audits, times(2)).save(any(CartChangeAudit.class));
        assertTrue(item.getCart().getItemsCart().isEmpty());
    }

    @Test
    void adminAddReusesCartAndAuditsCombinedQuantity() {
        when(users.getUserById(7L)).thenReturn(Optional.of(item.getCart().getUserCart()));
        when(items.getByCartAndProductId(12L, 4L)).thenReturn(Optional.of(item));
        service.addAdminItem("client@example.com", 7L, 4L, 1, "Asistencia");
        assertEquals(3, item.getAmountCartItem());
        var capture = ArgumentCaptor.forClass(CartChangeAudit.class);
        verify(audits).save(capture.capture());
        assertEquals("ADD", capture.getValue().getAction());
        assertEquals(2, capture.getValue().getPreviousAmount());
        assertEquals(3, capture.getValue().getNewAmount());
    }

    @Test
    void adminListDistinguishesMissingAndEmptyCart() {
        when(carts.getCartById(12L)).thenReturn(Optional.empty());
        assertEquals(
                404,
                assertThrows(ResponseStatusException.class, () -> service.getItemsByCartId(12L))
                        .getStatusCode()
                        .value());
        when(carts.getCartById(12L)).thenReturn(Optional.of(item.getCart()));
        when(items.getItemsByCartId(12L)).thenReturn(List.of());
        assertTrue(service.getItemsByCartId(12L).isEmpty());
        when(items.getItemsByCartId(12L)).thenReturn(List.of(item));
        var summary = service.getItemsByCartId(12L).get(0);
        assertEquals(new BigDecimal("300.00"), summary.getSubtotal());
        assertEquals(2, summary.getAmountCartItem());
    }

    @Test
    void adminDetailIncludesStockOnlyForPhysicalProducts() {
        var detail = service.getCartItemById(1L).orElseThrow();
        assertEquals(12L, detail.getCartId());
        assertEquals(5, detail.getStockProduct());
        ProductDigital digital = new ProductDigital();
        digital.setPriceProduct(new BigDecimal("20.00"));
        item.setProductCartItem(digital);
        assertNull(service.getCartItemById(1L).orElseThrow().getStockProduct());
        when(items.getCartItemById(1L)).thenReturn(Optional.empty());
        assertTrue(service.getCartItemById(1L).isEmpty());
    }

    @Test
    void foreignItemsCannotBeUpdatedIncreasedDecreasedOrRemoved() {
        UserModel other = new UserModel();
        other.setId(8L);
        item.getCart().setUserCart(other);
        assertEquals(
                404,
                assertThrows(
                                ResponseStatusException.class,
                                () -> service.updateMyItemAmount("client@example.com", 1L, 3))
                        .getStatusCode()
                        .value());
        assertThrows(
                ResponseStatusException.class,
                () -> service.increaseMyItemAmount("client@example.com", 1L, 1));
        assertThrows(
                ResponseStatusException.class,
                () -> service.decreaseMyItemAmount("client@example.com", 1L, 1));
        assertThrows(
                ResponseStatusException.class,
                () -> service.removeMyItem("client@example.com", 1L));
        assertEquals(2, item.getAmountCartItem());
        verify(items, never()).removeCartItem(anyLong());
    }

    @Test
    void rejectsInvalidAmountsUnavailableStockAndInactiveProducts() {
        assertThrows(
                ResponseStatusException.class,
                () -> service.addMyItem("client@example.com", 4L, 0));
        assertThrows(
                ResponseStatusException.class,
                () -> service.updateMyItemAmount("client@example.com", 1L, -1));
        assertThrows(
                ResponseStatusException.class,
                () -> service.increaseMyItemAmount("client@example.com", 1L, 4));
        item.getProductCartItem().setActive(false);
        assertThrows(
                ResponseStatusException.class,
                () -> service.addMyItem("client@example.com", 4L, 1));
        verify(items, never()).saveCartItem(any());
    }

    @Test
    void addingUsesAuthenticatedUserAndChecksCombinedQuantity() {
        when(items.getByCartAndProductId(12L, 4L)).thenReturn(Optional.of(item));
        service.addMyItem("client@example.com", 4L, 2);
        assertEquals(4, item.getAmountCartItem());
        assertEquals(new BigDecimal("600.00"), item.getSubtotal());
        verify(carts).getOrCreateActiveCart(7L);
        assertThrows(
                ResponseStatusException.class,
                () -> service.addMyItem("client@example.com", 4L, 2));
        assertEquals(4, item.getAmountCartItem());
    }

    @Test
    void decreasingToZeroRemovesItemFromCart() {
        service.decreaseMyItemAmount("client@example.com", 1L, 2);
        verify(items).removeCartItem(1L);
        assertTrue(item.getCart().getItemsCart().isEmpty());
    }
}

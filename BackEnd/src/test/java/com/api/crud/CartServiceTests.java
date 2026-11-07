package com.api.crud;

import com.api.crud.models.entity.*;
import com.api.crud.repositories.*;
import com.api.crud.services.CartService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CartServiceTests {
    private CartService service;
    private CartDao carts;
    private CartItemDao items;
    private UserDao users;

    @BeforeEach
    void setUp() {
        service = new CartService();
        carts = mock(CartDao.class);
        items = mock(CartItemDao.class);
        users = mock(UserDao.class);
        ReflectionTestUtils.setField(service, "cartDao", carts);
        ReflectionTestUtils.setField(service, "cartItemDao", items);
        ReflectionTestUtils.setField(service, "userDao", users);
    }

    @Test
    void totalUsesAuthenticatedUsersActiveCartAndCountsSubtotalOnce() {
        UserModel user = new UserModel();
        user.setId(7L);
        Cart cart = new Cart();
        cart.setIdCart(12L);
        CartItem item = new CartItem();
        item.setAmountCartItem(2);
        ProductPhysical product = new ProductPhysical();
        product.setPriceProduct(new BigDecimal("100.00"));
        item.setProductCartItem(product);
        when(users.findUserByEmail("client@example.com")).thenReturn(Optional.of(user));
        when(carts.findActiveCartByUserId(7L)).thenReturn(Optional.of(cart));
        when(items.findItemsByCartId(12L)).thenReturn(List.of(item));
        assertEquals(new BigDecimal("200.00"), service.getMyCartTotal("client@example.com"));
        verify(carts).findActiveCartByUserId(7L);
        verify(items).findItemsByCartId(12L);
    }

    @Test
    void missingCartHasZeroTotalAndCanBeCleared() {
        UserModel user = new UserModel();
        user.setId(7L);
        when(users.findUserByEmail("client@example.com")).thenReturn(Optional.of(user));
        when(carts.findActiveCartByUserId(7L)).thenReturn(Optional.empty());
        assertEquals(BigDecimal.ZERO, service.getMyCartTotal("client@example.com"));
        assertTrue(service.findMyActiveCart("client@example.com").getItemsCart().isEmpty());
        assertEquals(BigDecimal.ZERO, service.findMyActiveCart("client@example.com").getTotal());
        service.clearMyCart("client@example.com");
        verifyNoInteractions(items);
    }

    @Test
    void clearOnlyEmptiesAuthenticatedUsersActiveCart() {
        UserModel user = new UserModel();
        user.setId(7L);
        Cart cart = new Cart();
        cart.getItemsCart().add(new CartItem());
        when(users.findUserByEmail("client@example.com")).thenReturn(Optional.of(user));
        when(carts.findActiveCartByUserId(7L)).thenReturn(Optional.of(cart));
        service.clearMyCart("client@example.com");
        assertTrue(cart.getItemsCart().isEmpty());
        verify(carts).findActiveCartByUserId(7L);
        verifyNoInteractions(items);
    }

    @Test
    void cartResponseRecalculatesSubtotalWhenProductPriceChanges() {
        UserModel user = new UserModel();
        user.setId(7L);
        Cart cart = new Cart();
        ProductDigital product = new ProductDigital();
        product.setIdProduct(4L);
        product.setPriceProduct(new BigDecimal("150.00"));
        CartItem item = new CartItem();
        item.setProductCartItem(product);
        item.setAmountCartItem(2);
        cart.getItemsCart().add(item);
        when(users.findUserByEmail("client@example.com")).thenReturn(Optional.of(user));
        when(carts.findActiveCartByUserId(7L)).thenReturn(Optional.of(cart));
        var response = service.findMyActiveCart("client@example.com");
        assertEquals(new BigDecimal("300.00"), response.getTotal());
        assertEquals(new BigDecimal("150.00"), response.getItemsCart().get(0).getPriceProduct());
        product.setPriceProduct(new BigDecimal("175.00"));
        assertEquals(new BigDecimal("350.00"), service.findMyActiveCart("client@example.com").getTotal());
        OrderItem purchased = new OrderItem();
        purchased.setProductOrderItem(product);
        purchased.setAmountOrderItem(2);
        purchased.setPriceOrderItem(new BigDecimal("100.00"));
        assertEquals(new BigDecimal("200.00"), purchased.getSubtotal());
    }
}

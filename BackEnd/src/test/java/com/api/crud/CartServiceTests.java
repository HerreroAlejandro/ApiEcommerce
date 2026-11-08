package com.api.crud;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.api.crud.DTO.CartAdminFilterDTO;
import com.api.crud.DTO.CartAdminListDTO;
import com.api.crud.config.ModelMapperConfig;
import com.api.crud.models.entity.*;
import com.api.crud.repositories.*;
import com.api.crud.services.CartService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

class CartServiceTests {

    private CartService service;

    private CartDao carts;

    private CartItemDao items;

    private UserDao users;

    @BeforeEach
    void setUp() {
        service = new CartService();
        ReflectionTestUtils.setField(service, "modelMapper", new ModelMapperConfig().modelMapper());
        carts = mock(CartDao.class);
        items = mock(CartItemDao.class);
        users = mock(UserDao.class);
        ReflectionTestUtils.setField(service, "cartDao", carts);
        ReflectionTestUtils.setField(service, "cartItemDao", items);
        ReflectionTestUtils.setField(service, "userDao", users);
    }

    @Test
    void adminDetailIncludesCurrentTotalsAndItems() {
        Cart cart = new Cart();
        cart.setIdCart(12L);
        UserModel user = new UserModel();
        user.setId(7L);
        cart.setUserCart(user);
        ProductPhysical product = new ProductPhysical();
        product.setPriceProduct(new BigDecimal("100.00"));
        cart.getItemsCart().add(new CartItem(1L, cart, product, 2));
        when(carts.getCartById(12L)).thenReturn(Optional.of(cart));
        var detail = service.getCartById(12L).orElseThrow();
        assertEquals(1, detail.getItemsCart().size());
        assertEquals(1, detail.getItemCount());
        assertEquals(7L, detail.getUserId());
        assertEquals(new BigDecimal("200.00"), detail.getTotal());
    }

    @Test
    void filteredListingPassesCombinedFiltersAndPaginationToDatabase() {
        var filter = new CartAdminFilterDTO();
        filter.setMinTotal(new BigDecimal("50000.00"));
        filter.setMaxTotal(new BigDecimal("100000.00"));
        filter.setProductId(8L);
        filter.setMinProductQuantity(10);
        filter.setActive(true);
        var pageable = PageRequest.of(1, 10);
        var expected = Page.<CartAdminListDTO>empty(pageable);
        when(carts.getCarts(filter, pageable)).thenReturn(expected);
        assertSame(expected, service.getCarts(filter, pageable));
        verify(carts).getCarts(filter, pageable);
    }

    @Test
    void rejectsInvertedAmountsQuantityWithoutProductAndExcessivePageSize() {
        var filter = new CartAdminFilterDTO();
        filter.setMinTotal(new BigDecimal("100"));
        filter.setMaxTotal(new BigDecimal("50"));
        var page = PageRequest.of(0, 10);
        assertThrows(ResponseStatusException.class, () -> service.getCarts(filter, page));
        filter.setMaxTotal(null);
        filter.setMinProductQuantity(2);
        assertThrows(ResponseStatusException.class, () -> service.getCarts(filter, page));
        filter.setProductId(8L);
        assertThrows(
                ResponseStatusException.class,
                () -> service.getCarts(filter, PageRequest.of(0, 101)));
        verifyNoInteractions(carts);
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
        when(users.getUserByEmail("client@example.com")).thenReturn(Optional.of(user));
        when(carts.getActiveCartByUserId(7L)).thenReturn(Optional.of(cart));
        when(items.getItemsByCartId(12L)).thenReturn(List.of(item));
        assertEquals(new BigDecimal("200.00"), service.getMyCartTotal("client@example.com"));
        verify(carts).getActiveCartByUserId(7L);
        verify(items).getItemsByCartId(12L);
    }

    @Test
    void missingCartHasZeroTotalAndCanBeCleared() {
        UserModel user = new UserModel();
        user.setId(7L);
        when(users.getUserByEmail("client@example.com")).thenReturn(Optional.of(user));
        when(carts.getActiveCartByUserId(7L)).thenReturn(Optional.empty());
        assertEquals(BigDecimal.ZERO, service.getMyCartTotal("client@example.com"));
        assertTrue(service.getMyActiveCart("client@example.com").getItemsCart().isEmpty());
        assertEquals(BigDecimal.ZERO, service.getMyActiveCart("client@example.com").getTotal());
        service.clearMyCart("client@example.com");
        verifyNoInteractions(items);
    }

    @Test
    void clearOnlyEmptiesAuthenticatedUsersActiveCart() {
        UserModel user = new UserModel();
        user.setId(7L);
        Cart cart = new Cart();
        cart.getItemsCart().add(new CartItem());
        when(users.getUserByEmail("client@example.com")).thenReturn(Optional.of(user));
        when(carts.getActiveCartByUserId(7L)).thenReturn(Optional.of(cart));
        service.clearMyCart("client@example.com");
        assertTrue(cart.getItemsCart().isEmpty());
        verify(carts).getActiveCartByUserId(7L);
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
        when(users.getUserByEmail("client@example.com")).thenReturn(Optional.of(user));
        when(carts.getActiveCartByUserId(7L)).thenReturn(Optional.of(cart));
        var response = service.getMyActiveCart("client@example.com");
        assertEquals(new BigDecimal("300.00"), response.getTotal());
        assertEquals(new BigDecimal("150.00"), response.getItemsCart().get(0).getPriceProduct());
        product.setPriceProduct(new BigDecimal("175.00"));
        assertEquals(
                new BigDecimal("350.00"), service.getMyActiveCart("client@example.com").getTotal());
        OrderItem purchased = new OrderItem();
        purchased.setProductOrderItem(product);
        purchased.setAmountOrderItem(2);
        purchased.setPriceOrderItem(new BigDecimal("100.00"));
        assertEquals(new BigDecimal("200.00"), purchased.getSubtotal());
    }
}

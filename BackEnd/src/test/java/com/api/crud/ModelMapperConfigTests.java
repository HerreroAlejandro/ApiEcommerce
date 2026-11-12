package com.api.crud;

import static org.junit.jupiter.api.Assertions.*;

import com.api.crud.DTO.*;
import com.api.crud.config.ModelMapperConfig;
import com.api.crud.models.entity.*;
import com.api.crud.models.entity.Order;

import org.junit.jupiter.api.Test;
import org.modelmapper.ModelMapper;

import java.math.BigDecimal;
import java.util.List;

class ModelMapperConfigTests {

    private final ModelMapper mapper = new ModelMapperConfig().modelMapper();

    @Test
    void nestedCartResponsesUseCurrentProductValues() {
        UserModel user = new UserModel();
        user.setId(7L);
        Cart cart = new Cart();
        cart.setIdCart(12L);
        cart.setUserCart(user);
        ProductPhysical product = new ProductPhysical();
        product.setIdProduct(40L);
        product.setNameProduct("Producto actual");
        product.setImageUrl("https://example.com/image.png");
        product.setPriceProduct(new BigDecimal("150.00"));
        CartItem item = new CartItem(3L, cart, product, 2);
        cart.setItemsCart(List.of(item));
        MyCartResponseDTO mine = mapper.map(cart, MyCartResponseDTO.class);
        CartAdminDetailDTO admin = mapper.map(cart, CartAdminDetailDTO.class);
        assertEquals(40L, mine.getItemsCart().get(0).getProductId());
        assertEquals("Producto actual", mine.getItemsCart().get(0).getNameProduct());
        assertEquals(product.getImageUrl(), mine.getItemsCart().get(0).getImageUrl());
        assertEquals(new BigDecimal("300.00"), mine.getTotal());
        assertEquals(7L, admin.getUserId());
        assertEquals(1, admin.getItemCount());
        assertEquals(mine.getTotal(), admin.getTotal());
        assertEquals(40L, admin.getItemsCart().get(0).getProductId());
        product.setPriceProduct(new BigDecimal("200.00"));
        assertEquals(
                new BigDecimal("400.00"), mapper.map(cart, MyCartResponseDTO.class).getTotal());
    }

    @Test
    void emptyCartMapsToEmptyItemsAndZeroTotal() {
        MyCartResponseDTO result = mapper.map(new Cart(), MyCartResponseDTO.class);
        assertTrue(result.getItemsCart().isEmpty());
        assertEquals(BigDecimal.ZERO, result.getTotal());
    }

    @Test
    void orderDetailsKeepPurchasedValuesInNestedItems() {
        UserModel user = new UserModel();
        user.setId(7L);
        user.setEmail("client@example.com");
        Order order = new Order();
        order.setUserOrder(user);
        ProductPhysical product = new ProductPhysical();
        product.setIdProduct(40L);
        product.setNameProduct("Nombre nuevo");
        product.setPriceProduct(new BigDecimal("999.00"));
        OrderItem item = new OrderItem(order, product, 2, new BigDecimal("100.00"));
        item.setProductName("Nombre comprado");
        order.setItemsOrder(List.of(item));
        OrderDetailDTO mine = mapper.map(order, OrderDetailDTO.class);
        OrderAdminDetailDTO admin = mapper.map(order, OrderAdminDetailDTO.class);
        assertEquals("Nombre comprado", mine.getItemsOrder().get(0).getProductName());
        assertEquals(new BigDecimal("100.00"), mine.getItemsOrder().get(0).getPriceOrderItem());
        assertEquals(new BigDecimal("200.00"), admin.getItemsOrder().get(0).getSubtotal());
        assertEquals(40L, admin.getItemsOrder().get(0).getProductId());
        assertEquals(7L, admin.getUserId());
        assertEquals(user.getEmail(), admin.getUserEmail());
    }

    @Test
    void publicProductDetailIncludesOnlyPhysicalStock() {
        ProductPhysical physical = new ProductPhysical();
        physical.setStockProduct(4);
        assertEquals(4, mapper.map(physical, ProductDetailDTO.class).getStockProduct());
        assertNull(mapper.map(new ProductDigital(), ProductDetailDTO.class).getStockProduct());
    }
}

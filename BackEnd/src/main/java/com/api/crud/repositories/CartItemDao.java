package com.api.crud.repositories;

import com.api.crud.exceptions.*;
import com.api.crud.models.entity.CartItem;

import java.util.List;
import java.util.Optional;

public interface CartItemDao {

    void saveCartItem(CartItem cartItem);

    Optional<CartItem> getCartItemById(Long cartItemId);

    public List<CartItem> getItemsByCartId(Long idCart);

    Optional<CartItem> getByCartAndProductId(Long cartId, Long productId);

    void removeCartItem(Long cartItemId);
}

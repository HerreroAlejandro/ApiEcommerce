package com.api.crud.repositories;

import com.api.crud.models.entity.CartItem;
import java.util.List;
import java.util.Optional;

public interface CartItemDao {

    void saveCartItem(CartItem cartItem);

    Optional<CartItem> findCartItemById(Long cartItemId);

    public List<CartItem> findItemsByCartId(Long idCart);

    Optional<CartItem> findByCartAndProductId(Long cartId, Long productId);


    public void updateCartItemAmount(Long cartItemId, int newAmount);

    void removeCartItem(Long cartItemId);

    void increaseCartItemAmount(Long cartItemId, int increment);

    void decreaseCartItemAmount(Long cartItemId, int decrement);

}

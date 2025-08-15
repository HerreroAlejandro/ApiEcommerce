package com.api.crud.repositories;

import com.api.crud.models.entity.Cart;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;


public interface CartDao {

    List<Cart> getCarts();

    void saveCart(Cart cart);

    public Optional<Cart> findCartByUserEmail(String email);

    Optional<Cart> findCartById(Long idCart);

    Optional<Cart> findCartByUserId(Long userId);

    void deleteCartById(Long idCart);

    Optional<Cart> findActiveCartByUserId(Long userId);

    List<Cart> getCartsByDateRange(LocalDate start, LocalDate end);

    List<Cart> getCartsWithMoreThanNItems(int minItems);

}

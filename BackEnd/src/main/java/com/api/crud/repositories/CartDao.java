package com.api.crud.repositories;

import com.api.crud.models.entity.Cart;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;


public interface CartDao {

    org.springframework.data.domain.Page<com.api.crud.DTO.CartAdminListDTO> getCarts(
            com.api.crud.DTO.CartAdminFilterDTO filter, org.springframework.data.domain.Pageable pageable);

    void saveCart(Cart cart);

    Cart getOrCreateActiveCart(Long userId);

    public Optional<Cart> getCartByUserEmail(String email);

    Optional<Cart> getCartById(Long idCart);

    Optional<Cart> getCartByUserId(Long userId);

    boolean deleteCartById(Long idCart);

    Optional<Cart> getActiveCartByUserId(Long userId);

    List<Cart> getCartsByDateRange(LocalDate start, LocalDate end);


    BigDecimal calculateCartTotal(Long cartId);

    void clearCart(Long cartId);

}

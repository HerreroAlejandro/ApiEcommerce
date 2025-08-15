package com.api.crud.repositories;

import com.api.crud.models.entity.CartItem;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
@Transactional
public class CartItemDaoImp implements CartItemDao{

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional
    public void saveCartItem(CartItem cartItem) {
        try{
            entityManager.merge(cartItem);
        } catch (Exception e){
         System.out.print("error :" + e.getMessage());
        }
    }

    @Override
    public Optional<CartItem> findCartItemById(Long cartItemId) {
        CartItem cartItem = entityManager.find(CartItem.class, cartItemId);
        return Optional.ofNullable(cartItem);
    }

    @Override
    public List<CartItem> findItemsByCartId(Long idCart) {
        String jpql = "SELECT ci FROM CartItem ci WHERE ci.cart.idCart = :idCart";
        return entityManager.createQuery(jpql, CartItem.class)
                .setParameter("idCart", idCart)
                .getResultList();
    }

    @Override
    public Optional<CartItem> findByCartAndProductId(Long cartId, Long productId) {
        String jpql = "SELECT ci FROM CartItem ci WHERE ci.cart.idCart = :cartId AND ci.productCartItem.idProduct = :productId";

        try {
            CartItem cartItem = entityManager.createQuery(jpql, CartItem.class)
                    .setParameter("cartId", cartId)
                    .setParameter("productId", productId)
                    .getSingleResult();
            return Optional.of(cartItem);
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    @Override
    public void updateCartItemAmount(Long cartItemId, int newAmount) {
        CartItem cartItem = entityManager.find(CartItem.class, cartItemId);
        if (cartItem != null) {
            cartItem.setAmountCartItem(newAmount);
            entityManager.merge(cartItem);
        }
    }

    @Override
    @Transactional
    public void removeCartItem(Long cartItemId) {
        CartItem cartItem = entityManager.find(CartItem.class, cartItemId);

        if (cartItem != null) {
            entityManager.remove(cartItem);
        } else {
            throw new IllegalArgumentException("El item del carrito no existe.");
        }
    }


}

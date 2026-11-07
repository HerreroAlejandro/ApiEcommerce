package com.api.crud.repositories;

import com.api.crud.models.entity.Cart;
import com.api.crud.models.entity.UserModel;
import jakarta.persistence.LockModeType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Repository
@Transactional
public class CartDaoImp implements CartDao{

    @PersistenceContext
    private EntityManager entityManager;

    private static final Logger logger = LoggerFactory.getLogger(CartDaoImp.class);

    @Override
    public Cart getOrCreateActiveCart(Long userId) {
        UserModel user = entityManager.find(UserModel.class, userId, LockModeType.PESSIMISTIC_WRITE);
        if (user == null) {
            throw new IllegalArgumentException("Usuario no encontrado");
        }
        Cart cart = getCartByUserId(userId).orElseGet(() -> {
            Cart created = new Cart();
            created.setUserCart(user);
            created.setCreationDate(LocalDate.now());
            entityManager.persist(created);
            return created;
        });
        if (!cart.isActive()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT, "Carrito deshabilitado");
        }
        return cart;
    }

    @Override
    public void saveCart(Cart cart) {
        try {
            if (cart.getIdCart() == null) {
                entityManager.persist(cart);
            } else {
                entityManager.merge(cart);
            }
        } catch (Exception e) {
            logger.error("Error guardando el carrito en la base de datos: ", e);
            throw new RuntimeException("No se pudo guardar el carrito en la base de datos");
        }
    }

    @Override
    public List<Cart> getCarts() {
        logger.debug("Executing query to fetch Carts");
        List<Cart> carts;
        try {
            String jpql = "SELECT c FROM Cart c";
            carts = entityManager.createQuery(jpql, Cart.class).getResultList();
        } catch (Exception e) {
            logger.error("Error while querying Carts: {}", e.getMessage());
            carts = Collections.emptyList();
        }
        return carts;
    }

    @Override
    public Optional<Cart> getCartByUserEmail(String email) {
        String jpql = "SELECT c FROM Cart c WHERE c.userCart.email = :email ORDER BY c.creationDate DESC";
        try {
            Cart cart = entityManager.createQuery(jpql, Cart.class)
                    .setParameter("email", email)
                    .getSingleResult();
            return Optional.ofNullable(cart);
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }


    @Override
    public Optional<Cart> getCartById(Long idCart) {
        try {
            Cart cart = entityManager.find(Cart.class, idCart);
            return Optional.ofNullable(cart);
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<Cart> getCartByUserId(Long userId) {
        String jpql = "SELECT c FROM Cart c WHERE c.userCart.id = :userId";

        try {
            Cart cart = entityManager.createQuery(jpql, Cart.class)
                    .setParameter("userId", userId)
                    .getSingleResult();
            return Optional.ofNullable(cart);
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    @Override
    public boolean deleteCartById(Long idCart) {
        Cart cart = entityManager.find(Cart.class, idCart);

        if (cart == null) {
            return false;
        }

        entityManager.remove(cart);
        return true;
    }

    // 🔹 Nuevo método: Carrito activo
    @Override
    public Optional<Cart> getActiveCartByUserId(Long userId) {
        String jpql = "SELECT c FROM Cart c WHERE c.userCart.id = :userId AND c.active = true";
        try {
            Cart cart = entityManager.createQuery(jpql, Cart.class)
                    .setParameter("userId", userId)
                    .getSingleResult();
            return Optional.ofNullable(cart);
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    // 🔹 Nuevo método: Carritos por rango de fechas
    @Override
    public List<Cart> getCartsByDateRange(LocalDate start, LocalDate end) {
        String jpql = "SELECT c FROM Cart c WHERE c.creationDate BETWEEN :start AND :end";
        try {
            return entityManager.createQuery(jpql, Cart.class)
                    .setParameter("start", start)
                    .setParameter("end", end)
                    .getResultList();
        } catch (Exception e) {
            logger.error("Error obteniendo carritos por rango de fechas: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    // 🔹 Nuevo método: Carritos con más de N ítems
    @Override
    public List<Cart> getCartsWithMoreThanNItems(int minItems) {
        String jpql = "SELECT c FROM Cart c WHERE SIZE(c.itemsCart) > :minItems";
        try {
            return entityManager.createQuery(jpql, Cart.class)
                    .setParameter("minItems", minItems)
                    .getResultList();
        } catch (Exception e) {
            logger.error("Error obteniendo carritos con más de {} items: {}", minItems, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public BigDecimal calculateCartTotal(Long cartId) {
        String jpql = "SELECT SUM(c.productCartItem.priceProduct * c.amountCartItem) FROM CartItem c WHERE c.cart.idCart = :cartId";
        BigDecimal total = entityManager.createQuery(jpql, BigDecimal.class)
                .setParameter("cartId", cartId)
                .getSingleResult();
        return total != null ? total : BigDecimal.ZERO;
    }

    @Override
    public void clearCart(Long cartId) {
        String jpql = "DELETE FROM CartItem c WHERE c.cart.idCart = :cartId";
        entityManager.createQuery(jpql)
                .setParameter("cartId", cartId)
                .executeUpdate();
    }


}



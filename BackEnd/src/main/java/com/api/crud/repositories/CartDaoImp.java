package com.api.crud.repositories;

import com.api.crud.DTO.CartAdminFilterDTO;
import com.api.crud.DTO.CartAdminListDTO;
import com.api.crud.exceptions.*;
import com.api.crud.models.entity.Cart;
import com.api.crud.models.entity.UserModel;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort.Order;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
@Transactional
public class CartDaoImp implements CartDao {

    private static final Logger logger = LoggerFactory.getLogger(CartDaoImp.class);

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Cart getOrCreateActiveCart(Long userId) {
        UserModel user =
                entityManager.find(UserModel.class, userId, LockModeType.PESSIMISTIC_WRITE);
        if (user == null) {
            throw new ResourceNotFoundException("Usuario no encontrado");
        }
        Cart cart =
                getCartByUserId(userId)
                        .orElseGet(
                                () -> {
                                    Cart created = new Cart();
                                    created.setUserCart(user);
                                    created.setCreationDate(LocalDate.now());
                                    entityManager.persist(created);
                                    return created;
                                });
        if (!cart.isActive()) {
            throw new BusinessConflictException("Carrito deshabilitado");
        }
        return cart;
    }

    @Override
    public void saveCart(Cart cart) {
        if (cart.getIdCart() == null) {
            entityManager.persist(cart);
        } else {
            entityManager.merge(cart);
        }
    }

    @Override
    public Page<CartAdminListDTO> getCarts(CartAdminFilterDTO filter, Pageable pageable) {
        String totalFilter =
                "(SELECT COALESCE(SUM(t.productCartItem.priceProduct * t.amountCartItem), 0) FROM CartItem t WHERE t.cart = c)";
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        Map<String, Object> params = new HashMap<>();
        if (filter.getActive() != null) {
            where.append(" AND c.active = :active");
            params.put("active", filter.getActive());
        }
        if (filter.getMinTotal() != null) {
            where.append(" AND ").append(totalFilter).append(" >= :minTotal");
            params.put("minTotal", filter.getMinTotal());
        }
        if (filter.getMaxTotal() != null) {
            where.append(" AND ").append(totalFilter).append(" <= :maxTotal");
            params.put("maxTotal", filter.getMaxTotal());
        }
        if (filter.getProductId() != null) {
            where.append(
                    " AND EXISTS (SELECT f.idCartItem FROM CartItem f WHERE f.cart = c AND f.productCartItem.idProduct = :productId AND f.amountCartItem >= :quantity)");
            params.put("productId", filter.getProductId());
            params.put(
                    "quantity",
                    filter.getMinProductQuantity() == null ? 1 : filter.getMinProductQuantity());
        }
        Map<String, String> allowed =
                Map.of(
                        "idCart",
                        "c.idCart",
                        "userId",
                        "c.userCart.id",
                        "active",
                        "c.active",
                        "creationDate",
                        "c.creationDate",
                        "itemCount",
                        "COUNT(i)",
                        "total",
                        "COALESCE(SUM(p.priceProduct * i.amountCartItem), 0)");
        List<String> sorting = new ArrayList<>();
        for (Order sort : pageable.getSort()) {
            String expression = allowed.get(sort.getProperty());
            if (expression == null)
                throw new InvalidRequestException("Campo de ordenamiento invalido");
            sorting.add(expression + " " + sort.getDirection().name());
        }
        sorting.add("c.idCart DESC");
        String select =
                "SELECT new com.api.crud.DTO.CartAdminListDTO(c.idCart, c.userCart.id, c.active, c.creationDate, COUNT(i), COALESCE(SUM(p.priceProduct * i.amountCartItem), 0)) FROM Cart c LEFT JOIN c.itemsCart i LEFT JOIN i.productCartItem p";
        var query =
                entityManager.createQuery(
                        select
                                + where
                                + " GROUP BY c.idCart, c.userCart.id, c.active, c.creationDate ORDER BY "
                                + String.join(", ", sorting),
                        CartAdminListDTO.class);
        var count = entityManager.createQuery("SELECT COUNT(c) FROM Cart c" + where, Long.class);
        params.forEach(
                (key, value) -> {
                    query.setParameter(key, value);
                    count.setParameter(key, value);
                });
        return new PageImpl<>(
                query.setFirstResult(Math.toIntExact(pageable.getOffset()))
                        .setMaxResults(pageable.getPageSize())
                        .getResultList(),
                pageable,
                count.getSingleResult());
    }

    @Override
    public Optional<Cart> getCartByUserEmail(String email) {
        String jpql =
                "SELECT c FROM Cart c WHERE c.userCart.email = :email ORDER BY c.creationDate DESC";
        try {
            Cart cart =
                    entityManager
                            .createQuery(jpql, Cart.class)
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
            Cart cart =
                    entityManager
                            .createQuery(jpql, Cart.class)
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
            Cart cart =
                    entityManager
                            .createQuery(jpql, Cart.class)
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
        return entityManager
                .createQuery(jpql, Cart.class)
                .setParameter("start", start)
                .setParameter("end", end)
                .getResultList();
    }

    // 🔹 Nuevo método: Carritos con más de N ítems
    @Override
    public BigDecimal calculateCartTotal(Long cartId) {
        String jpql =
                "SELECT SUM(c.productCartItem.priceProduct * c.amountCartItem) FROM CartItem c WHERE c.cart.idCart = :cartId";
        BigDecimal total =
                entityManager
                        .createQuery(jpql, BigDecimal.class)
                        .setParameter("cartId", cartId)
                        .getSingleResult();
        return total != null ? total : BigDecimal.ZERO;
    }

    @Override
    public void clearCart(Long cartId) {
        String jpql = "DELETE FROM CartItem c WHERE c.cart.idCart = :cartId";
        entityManager.createQuery(jpql).setParameter("cartId", cartId).executeUpdate();
    }
}

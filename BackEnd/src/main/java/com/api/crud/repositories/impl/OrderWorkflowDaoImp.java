package com.api.crud.repositories.impl;

import com.api.crud.exceptions.*;
import com.api.crud.models.entity.*;
import com.api.crud.models.enums.*;
import com.api.crud.repositories.OrderWorkflowDao;

import jakarta.persistence.*;

import org.springframework.data.domain.*;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.*;

@Repository
public class OrderWorkflowDaoImp implements OrderWorkflowDao {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public void lockUser(Long id) {
        if (entityManager.find(UserModel.class, id, LockModeType.PESSIMISTIC_WRITE) == null) {
            throw new ResourceNotFoundException("Usuario no encontrado");
        }
    }

    @Override
    public long getActivePendingOrderCount(
            Long userId, LocalDateTime now, LocalDateTime legacyCutoff) {
        return entityManager
                .createQuery(
                        "SELECT COUNT(o) FROM Order o WHERE o.userOrder.id = :userId"
                                + " AND o.salesChannel = :channel AND o.orderStatus = :status"
                                + " AND o.paymentStatus IN :payments AND o.stockDeducted = true"
                                + " AND (o.expiresAt > :now OR (o.expiresAt IS NULL"
                                + " AND o.purchaseDateOrder > :legacyCutoff))",
                        Long.class)
                .setParameter("userId", userId)
                .setParameter("channel", SalesChannel.ONLINE)
                .setParameter("status", OrderStatus.PENDING)
                .setParameter("payments", List.of(PaymentStatus.PENDING, PaymentStatus.FAILED))
                .setParameter("now", now)
                .setParameter("legacyCutoff", legacyCutoff)
                .getSingleResult();
    }

    @Override
    public Optional<Cart> lockCart(Long userId) {
        List<Cart> carts =
                entityManager
                        .createQuery(
                                "SELECT DISTINCT c FROM Cart c LEFT JOIN FETCH c.itemsCart WHERE"
                                        + " c.userCart.id = :userId",
                                Cart.class)
                        .setParameter("userId", userId)
                        .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                        .getResultList();
        return carts.stream().findFirst();
    }

    @Override
    public Product lockProduct(Long id) {
        Product product = entityManager.find(Product.class, id);
        if (product == null) throw new BusinessConflictException("Producto no disponible");
        entityManager.refresh(product, LockModeType.PESSIMISTIC_WRITE);
        return product;
    }

    @Override
    public Order lockOrder(Long id) {
        Order order = entityManager.find(Order.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (order == null) throw new ResourceNotFoundException("Orden no encontrada");
        entityManager.refresh(order, LockModeType.PESSIMISTIC_WRITE);
        return order;
    }

    @Override
    public void saveAudit(OrderChangeAudit audit) {
        entityManager.persist(audit);
    }

    @Override
    public Page<OrderChangeAudit> getOrderHistory(Long orderId, Pageable pageable) {
        List<OrderChangeAudit> rows =
                entityManager
                        .createQuery(
                                "SELECT a FROM OrderChangeAudit a WHERE a.orderId = :id ORDER BY"
                                    + " a.createdAt DESC, a.id DESC",
                                OrderChangeAudit.class)
                        .setParameter("id", orderId)
                        .setFirstResult(Math.toIntExact(pageable.getOffset()))
                        .setMaxResults(pageable.getPageSize())
                        .getResultList();
        long count =
                entityManager
                        .createQuery(
                                "SELECT COUNT(a) FROM OrderChangeAudit a WHERE a.orderId = :id",
                                Long.class)
                        .setParameter("id", orderId)
                        .getSingleResult();
        return new PageImpl<>(rows, pageable, count);
    }

    @Override
    public void flush() {
        entityManager.flush();
    }

    @Override
    public List<Long> getExpiredOrderIds(
            LocalDateTime now, LocalDateTime legacyCutoff, long afterId, int limit) {
        return entityManager
                .createQuery(
                        "SELECT o.idOrder FROM Order o WHERE o.idOrder > :afterId"
                                + " AND o.orderStatus = :status AND o.paymentStatus IN :payments"
                                + " AND (o.expiresAt <= :now OR (o.expiresAt IS NULL"
                                + " AND o.purchaseDateOrder <= :legacyCutoff)) ORDER BY o.idOrder",
                        Long.class)
                .setParameter("afterId", afterId)
                .setParameter("status", OrderStatus.PENDING)
                .setParameter("payments", List.of(PaymentStatus.PENDING, PaymentStatus.FAILED))
                .setParameter("now", now)
                .setParameter("legacyCutoff", legacyCutoff)
                .setMaxResults(limit)
                .getResultList();
    }

    @Override
    public Page<Order> search(
            Long userId,
            String email,
            OrderStatus status,
            PaymentStatus payment,
            LocalDateTime start,
            LocalDateTime end,
            Pageable pageable) {
        if (pageable.getPageSize() > 100
                || pageable.getOffset() > Integer.MAX_VALUE
                || (start != null && end != null && start.isAfter(end))) {
            throw new InvalidRequestException("Paginacion o fechas invalidas");
        }
        Map<String, Object> params = new HashMap<>();
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        if (userId != null) {
            where.append(" AND o.userOrder.id = :userId");
            params.put("userId", userId);
        }
        if (email != null && !email.isBlank()) {
            where.append(" AND o.userOrder.email = :email");
            params.put("email", email);
        }
        if (status != null) {
            where.append(" AND o.orderStatus = :status");
            params.put("status", status);
        }
        if (payment != null) {
            where.append(" AND o.paymentStatus = :payment");
            params.put("payment", payment);
        }
        if (start != null) {
            where.append(" AND o.purchaseDateOrder >= :start");
            params.put("start", start);
        }
        if (end != null) {
            where.append(" AND o.purchaseDateOrder <= :end");
            params.put("end", end);
        }
        Set<String> allowed =
                Set.of(
                        "idOrder",
                        "purchaseDateOrder",
                        "totalOrder",
                        "orderStatus",
                        "paymentStatus");
        List<String> sorting = new ArrayList<>();
        for (Sort.Order sort : pageable.getSort()) {
            if (!allowed.contains(sort.getProperty())) {
                throw new InvalidRequestException("Campo de ordenamiento invalido");
            }
            sorting.add("o." + sort.getProperty() + " " + sort.getDirection().name());
        }
        if (sorting.isEmpty()) sorting.add("o.purchaseDateOrder DESC");
        sorting.add("o.idOrder DESC");
        TypedQuery<Order> query =
                entityManager.createQuery(
                        "SELECT o FROM Order o" + where + " ORDER BY " + String.join(", ", sorting),
                        Order.class);
        TypedQuery<Long> count =
                entityManager.createQuery("SELECT COUNT(o) FROM Order o" + where, Long.class);
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
}

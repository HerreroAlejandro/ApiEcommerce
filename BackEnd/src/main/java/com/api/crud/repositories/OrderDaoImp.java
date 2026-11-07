package com.api.crud.repositories;

import com.api.crud.models.entity.Order;
import com.api.crud.models.entity.Product;
import com.api.crud.models.enums.OrderStatus;
import com.api.crud.models.enums.PaymentStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@Transactional
public class OrderDaoImp implements OrderDao {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public void saveOrder(Order order) {
        if (order.getIdOrder() == null) {
            entityManager.persist(order);
        } else {
            entityManager.merge(order);
        }
    }

    @Override
    public Optional<Order> findOrderById(Long idOrder) {
        return Optional.ofNullable(entityManager.find(Order.class, idOrder));
    }

    @Override
    public List<Order> getOrders() {
        String query = "FROM Order";
        return entityManager.createQuery(query, Order.class).getResultList();
    }

    @Override
    public List<Order> getOrdersByUserEmail(String email) {
        return entityManager.createQuery(
                        "SELECT o FROM Order o WHERE o.userOrder.email = :email", Order.class)
                .setParameter("email", email)
                .getResultList();
    }

    @Override
    public List<Order> getOrdersByUserId(Long userId) {
        String query = "FROM Order o WHERE o.userOrder.id = :userId";
        return entityManager.createQuery(query, Order.class)
                .setParameter("userId", userId)
                .getResultList();
    }

    @Override
    public List<Order> getOrdersByStatus(OrderStatus status) {
        String query = "FROM Order o WHERE o.orderStatus = :status";
        return entityManager.createQuery(query, Order.class)
                .setParameter("status", status)
                .getResultList();
    }

    @Override
    public List<Order> getOrdersByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        String query = "FROM Order o WHERE o.purchaseDateOrder BETWEEN :startDate AND :endDate";
        return entityManager.createQuery(query, Order.class)
                .setParameter("startDate", startDate)
                .setParameter("endDate", endDate)
                .getResultList();
    }

    @Override
    public void updateOrder(Order order) {
        entityManager.merge(order);
    }

    @Override
    public void deleteOrderById(Long idOrder) {
        Order order = entityManager.find(Order.class, idOrder);
        if (order != null) {
            entityManager.remove(order);
        }
    }

    @Override
    public List<Order> getOrdersByPaymentStatus(PaymentStatus paymentStatus) {
        String query = "FROM Order o WHERE o.paymentStatus = :status";
        return entityManager.createQuery(query, Order.class)
                .setParameter("status", paymentStatus)
                .getResultList();
    }

    @Override
    public List<Order> getOrdersByUserAndStatus(Long userId, OrderStatus status) {
        String query = "FROM Order o WHERE o.userOrder.id = :userId AND o.orderStatus = :status";
        return entityManager.createQuery(query, Order.class)
                .setParameter("userId", userId)
                .setParameter("status", status)
                .getResultList();
    }

    @Override
    public Optional<Product> findProductById(Long productId) {
        return Optional.ofNullable(entityManager.find(Product.class, productId));
    }
}

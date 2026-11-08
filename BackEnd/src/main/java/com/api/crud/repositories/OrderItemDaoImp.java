package com.api.crud.repositories;

import com.api.crud.exceptions.*;
import com.api.crud.models.entity.OrderItem;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class OrderItemDaoImp implements OrderItemDao {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Optional<OrderItem> getOrderItemById(Long id) {
        return Optional.ofNullable(entityManager.find(OrderItem.class, id));
    }

    @Override
    public List<OrderItem> getItemsByOrderId(Long orderId) {
        return entityManager
                .createQuery(
                        "SELECT i FROM OrderItem i WHERE i.order.idOrder = :id ORDER BY i.idOrderItem",
                        OrderItem.class)
                .setParameter("id", orderId)
                .getResultList();
    }
}

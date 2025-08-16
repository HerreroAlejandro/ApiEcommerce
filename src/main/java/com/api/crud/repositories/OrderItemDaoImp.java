package com.api.crud.repositories;

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
    public void saveOrderItem(OrderItem orderItem) {
        entityManager.persist(orderItem);
    }

    @Override
    public Optional<OrderItem> findOrderItemById(Long idOrderItem) {
        return Optional.ofNullable(entityManager.find(OrderItem.class, idOrderItem));
    }

    @Override
    public List<OrderItem> findItemsByOrderId(Long orderId) {
        return entityManager.createQuery(
                        "SELECT oi FROM OrderItem oi WHERE oi.order.idOrder = :orderId", OrderItem.class)
                .setParameter("orderId", orderId)
                .getResultList();
    }

    @Override
    public void updateOrderItem(OrderItem orderItem) {
        entityManager.merge(orderItem);
    }

    @Override
    public void deleteOrderItem(Long idOrderItem) {
        findOrderItemById(idOrderItem).ifPresent(entityManager::remove);
    }

    @Override
    public Optional<OrderItem> findByOrderAndProductId(Long orderId, Long productId) {
        List<OrderItem> result = entityManager.createQuery(
                        "SELECT oi FROM OrderItem oi WHERE oi.order.idOrder = :orderId AND oi.productOrderItem.idProduct = :productId",
                        OrderItem.class)
                .setParameter("orderId", orderId)
                .setParameter("productId", productId)
                .getResultList();

        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    @Override
    public void increaseOrderItemAmount(Long orderItemId, int increment) {
        findOrderItemById(orderItemId).ifPresent(item -> {
            item.setAmountOrderItem(item.getAmountOrderItem() + increment);
            entityManager.merge(item);
        });
    }

    @Override
    public void decreaseOrderItemAmount(Long orderItemId, int decrement) {
        findOrderItemById(orderItemId).ifPresent(item -> {
            int newAmount = item.getAmountOrderItem() - decrement;
            if (newAmount < 0) newAmount = 0;
            item.setAmountOrderItem(newAmount);
            entityManager.merge(item);
        });
    }

    @Override
    public List<OrderItem> findItemsByProductId(Long productId) {
        return entityManager.createQuery("SELECT oi FROM OrderItem oi WHERE oi.productOrderItem.idProduct = :productId", OrderItem.class)
                .setParameter("productId", productId)
                .getResultList();
    }


}

package com.api.crud.repositories;

import com.api.crud.models.entity.OrderItem;
import java.util.List;
import java.util.Optional;

public interface OrderItemDao {

    void saveOrderItem(OrderItem orderItem);

    Optional<OrderItem> findOrderItemById(Long idOrderItem);

    List<OrderItem> findItemsByOrderId(Long orderId);

    void updateOrderItem(OrderItem orderItem);

    void deleteOrderItem(Long idOrderItem);

    Optional<OrderItem> findByOrderAndProductId(Long orderId, Long productId);

    void increaseOrderItemAmount(Long orderItemId, int increment);

    void decreaseOrderItemAmount(Long orderItemId, int decrement);

    List<OrderItem> findItemsByProductId(Long productId);
}

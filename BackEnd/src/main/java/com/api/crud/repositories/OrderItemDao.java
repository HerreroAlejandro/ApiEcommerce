package com.api.crud.repositories;

import com.api.crud.exceptions.*;
import com.api.crud.models.entity.OrderItem;

import java.util.List;
import java.util.Optional;

public interface OrderItemDao {

    Optional<OrderItem> getOrderItemById(Long id);

    List<OrderItem> getItemsByOrderId(Long orderId);
}

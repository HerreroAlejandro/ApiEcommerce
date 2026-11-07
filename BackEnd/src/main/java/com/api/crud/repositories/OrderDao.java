package com.api.crud.repositories;

import com.api.crud.models.entity.Order;
import com.api.crud.models.entity.Product;
import com.api.crud.models.enums.OrderStatus;
import com.api.crud.models.enums.PaymentStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderDao {

    void saveOrder(Order order);

    Optional<Order> findOrderById(Long idOrder);

    List<Order> getOrders();

    List<Order> getOrdersByUserEmail(String email);

    List<Order> getOrdersByUserId(Long userId);

    List<Order> getOrdersByStatus(OrderStatus status);

    List<Order> getOrdersByDateRange(LocalDateTime startDate, LocalDateTime endDate);

    void updateOrder(Order order);

    void deleteOrderById(Long idOrder);

    List<Order> getOrdersByPaymentStatus(PaymentStatus paymentStatus);

    List<Order> getOrdersByUserAndStatus(Long userId, OrderStatus status);

    Optional<Product> findProductById(Long productId);

}

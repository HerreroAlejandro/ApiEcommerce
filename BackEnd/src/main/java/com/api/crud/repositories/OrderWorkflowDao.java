package com.api.crud.repositories;

import com.api.crud.models.entity.*;
import com.api.crud.models.enums.*;

import org.springframework.data.domain.*;

import java.time.LocalDateTime;
import java.util.*;

public interface OrderWorkflowDao {

    void lockUser(Long id);

    long getActivePendingOrderCount(Long userId, LocalDateTime now, LocalDateTime legacyCutoff);

    Optional<Cart> lockCart(Long userId);

    Product lockProduct(Long id);

    Order lockOrder(Long id);

    void saveAudit(OrderChangeAudit audit);

    Page<OrderChangeAudit> getOrderHistory(Long orderId, Pageable pageable);

    void flush();

    List<Long> getExpiredOrderIds(
            LocalDateTime now, LocalDateTime legacyCutoff, long afterId, int limit);

    Page<Order> search(
            Long userId,
            String email,
            OrderStatus status,
            PaymentStatus payment,
            LocalDateTime start,
            LocalDateTime end,
            Pageable pageable);
}

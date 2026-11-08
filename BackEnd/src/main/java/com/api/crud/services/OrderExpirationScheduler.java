package com.api.crud.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class OrderExpirationScheduler {

    private static final Logger logger = LoggerFactory.getLogger(OrderExpirationScheduler.class);

    private final OrderService orders;

    public OrderExpirationScheduler(OrderService orders) {
        this.orders = orders;
    }

    @Scheduled(fixedDelayString = "${orders.expiration.check-delay-ms:60000}")
    public void expirePendingOrders() {
        LocalDateTime now = LocalDateTime.now();
        long afterId = 0;
        while (true) {
            List<Long> ids = orders.getExpiredOrderIds(now, afterId);
            if (ids.isEmpty()) return;
            for (Long id : ids) {
                try {
                    orders.expireOrder(id);
                } catch (RuntimeException exception) {
                    logger.error(
                            "No se pudo vencer la orden {}. Se reintentara en el siguiente ciclo",
                            id,
                            exception);
                }
            }
            afterId = ids.get(ids.size() - 1);
        }
    }
}

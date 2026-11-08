package com.api.crud.DTO;

import com.api.crud.models.enums.OrderStatus;
import com.api.crud.models.enums.PaymentStatus;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class OrderAdminListDTO {

    @Getter
    @Setter
    private Long idOrder;

    @Getter
    @Setter
    private BigDecimal totalOrder;

    @Getter
    @Setter
    private LocalDateTime purchaseDateOrder;

    @Getter
    @Setter
    private LocalDateTime expiresAt;

    @Getter
    @Setter
    private OrderStatus orderStatus;

    @Getter
    @Setter
    private PaymentStatus paymentStatus;

    @Getter
    @Setter
    private Long userId;

    @Getter
    @Setter
    private String userEmail;
}

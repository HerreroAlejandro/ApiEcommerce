package com.api.crud.DTO;

import com.api.crud.models.enums.OrderStatus;
import com.api.crud.models.enums.PaymentStatus;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class OrderListDTO {

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
    private OrderStatus orderStatus;

    @Getter
    @Setter
    private PaymentStatus paymentStatus;
}

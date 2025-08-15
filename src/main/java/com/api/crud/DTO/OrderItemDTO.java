package com.api.crud.DTO;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

public class OrderItemDTO {

    @Getter @Setter
    private Long idOrderItem;

    @Getter @Setter
    private BigDecimal priceOrderItem;

    @Getter @Setter
    private int amountOrderItem;

    @Getter @Setter
    private Long order;

    @Getter @Setter
    private Long productOrderItem;
}

package com.api.crud.DTO;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

public class OrderItemFrontDTO {

    @Getter @Setter
    private Long idOrderItem;

    @Getter @Setter
    private BigDecimal priceOrderItem;

    @Getter @Setter
    private int amountOrderItem;

    @Getter @Setter
    private BigDecimal subtotal;

    @Getter @Setter
    private Long productId;

    @Getter @Setter
    private String productName;
}

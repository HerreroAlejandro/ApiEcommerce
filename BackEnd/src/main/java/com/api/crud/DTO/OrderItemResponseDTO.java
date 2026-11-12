package com.api.crud.DTO;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

public class OrderItemResponseDTO {

    @Getter
    @Setter
    private Long idOrderItem;

    @Getter
    @Setter
    private BigDecimal priceOrderItem;

    @Getter
    @Setter
    private int amountOrderItem;

    @Getter
    @Setter
    private BigDecimal subtotal;

    @Getter
    @Setter
    private Long productId;

    @Getter
    @Setter
    private String productName;
    @Getter
    @Setter
    private int refundRequestedQuantity;

    @Getter
    @Setter
    private int refundedQuantity;

    @Getter
    @Setter
    private int canceledQuantity;

    @Getter
    @Setter
    private int fulfillmentQuantity;
}

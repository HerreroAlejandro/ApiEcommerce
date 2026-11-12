package com.api.crud.DTO;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class RefundItemResponseDTO {
    private Long orderItemId;
    private int quantity;
    private BigDecimal unitPrice;
}

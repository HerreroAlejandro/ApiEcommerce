package com.api.crud.DTO;

import jakarta.validation.constraints.*;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

public class CartAdminFilterDTO {

    @DecimalMin("0.0")
    @Digits(integer = 8, fraction = 2)
    @Getter
    @Setter
    private BigDecimal minTotal;

    @DecimalMin("0.0")
    @Digits(integer = 8, fraction = 2)
    @Getter
    @Setter
    private BigDecimal maxTotal;

    @Getter
    @Setter
    private Boolean active;

    @Positive
    @Getter
    @Setter
    private Long productId;

    @Positive
    @Getter
    @Setter
    private Integer minProductQuantity;
}

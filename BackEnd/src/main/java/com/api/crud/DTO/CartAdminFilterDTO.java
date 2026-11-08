package com.api.crud.DTO;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@Getter @Setter
public class CartAdminFilterDTO {
    @DecimalMin("0.0") @Digits(integer = 8, fraction = 2)
    private BigDecimal minTotal;
    @DecimalMin("0.0") @Digits(integer = 8, fraction = 2)
    private BigDecimal maxTotal;
    private Boolean active;
    @Positive private Long productId;
    @Positive private Integer minProductQuantity;
}

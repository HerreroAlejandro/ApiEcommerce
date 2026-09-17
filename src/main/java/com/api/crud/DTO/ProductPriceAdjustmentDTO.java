package com.api.crud.DTO;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

public class ProductPriceAdjustmentDTO {

    @NotNull(message = "Adjustment percentage cannot be null")
    @DecimalMin(value = "-100.0", inclusive = false, message = "Adjustment percentage must be greater than -100")
    @Digits(integer = 3, fraction = 2)
    @Getter @Setter
    private BigDecimal percentage;

    public ProductPriceAdjustmentDTO(BigDecimal percentage) {
        this.percentage = percentage;
    }

    public ProductPriceAdjustmentDTO() {
    }
}

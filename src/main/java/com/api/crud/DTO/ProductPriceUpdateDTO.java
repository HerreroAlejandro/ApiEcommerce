package com.api.crud.DTO;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

public class ProductPriceUpdateDTO {

    @NotNull(message = "New price cannot be null")
    @DecimalMin(value = "0.0", inclusive = false, message = "New price must be greater than 0")
    @Digits(integer = 8, fraction = 2)
    @Getter @Setter
    private BigDecimal newPriceProduct;
}

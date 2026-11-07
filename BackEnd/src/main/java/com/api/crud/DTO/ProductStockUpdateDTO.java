package com.api.crud.DTO;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

public class ProductStockUpdateDTO {

    @NotNull(message = "New stock cannot be null")
    @Min(value = 0, message = "Stock cannot be negative")
    @Getter @Setter
    private Integer newStockProduct;
}

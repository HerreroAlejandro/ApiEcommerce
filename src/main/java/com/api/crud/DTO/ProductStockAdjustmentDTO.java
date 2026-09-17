package com.api.crud.DTO;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

public class ProductStockAdjustmentDTO {

    @Getter @Setter
    @NotNull(message = "Stock adjustment cannot be null")
    private Integer adjustment;

}

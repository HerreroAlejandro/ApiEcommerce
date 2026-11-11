package com.api.crud.DTO;

import jakarta.validation.constraints.*;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StoreSaleItemRequestDTO {
    @NotNull
    @Positive
    private Long productId;

    @NotNull
    @Min(1)
    @Max(99999999)
    private Integer quantity;
}

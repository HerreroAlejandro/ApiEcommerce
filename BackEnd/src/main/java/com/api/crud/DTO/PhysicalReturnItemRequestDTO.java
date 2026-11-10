package com.api.crud.DTO;

import jakarta.validation.constraints.*;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PhysicalReturnItemRequestDTO {

    @NotNull @Positive private Long orderItemId;

    @NotNull @Positive private Integer receivedQuantity;

    @NotNull @PositiveOrZero private Integer restockedQuantity;
}

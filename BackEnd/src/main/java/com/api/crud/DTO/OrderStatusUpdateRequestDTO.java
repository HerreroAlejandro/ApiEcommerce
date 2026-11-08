package com.api.crud.DTO;

import com.api.crud.models.enums.OrderStatus;

import jakarta.validation.constraints.*;

import lombok.Getter;
import lombok.Setter;

public class OrderStatusUpdateRequestDTO {

    @NotNull @Getter @Setter private OrderStatus status;

    @NotBlank
    @Size(max = 500)
    @Getter
    @Setter
    private String reason;
}

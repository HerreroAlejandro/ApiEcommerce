package com.api.crud.DTO;

import com.api.crud.models.enums.OrderStatus;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
@Getter @Setter
public class OrderStatusUpdateRequestDTO {
    @NotNull private OrderStatus status;
    @NotBlank @Size(max = 500) private String reason;
}

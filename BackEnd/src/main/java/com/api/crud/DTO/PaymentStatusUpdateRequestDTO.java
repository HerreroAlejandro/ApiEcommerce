package com.api.crud.DTO;

import com.api.crud.models.enums.PaymentStatus;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
@Getter @Setter
public class PaymentStatusUpdateRequestDTO {
    @NotNull private PaymentStatus status;
    @NotBlank @Size(max = 500) private String reason;
}

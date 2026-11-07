package com.api.crud.DTO;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
@Getter @Setter
public class PaymentStatusUpdateRequestDTO {
    @NotNull private com.api.crud.models.enums.PaymentStatus status;
    @NotBlank @Size(max = 500) private String reason;
}

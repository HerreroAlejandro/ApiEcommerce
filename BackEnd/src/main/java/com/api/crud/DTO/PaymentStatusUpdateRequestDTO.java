package com.api.crud.DTO;

import com.api.crud.models.enums.PaymentStatus;

import jakarta.validation.constraints.*;

import lombok.Getter;
import lombok.Setter;

public class PaymentStatusUpdateRequestDTO {

    @NotNull
    @Getter
    @Setter
    private PaymentStatus status;

    @NotBlank
    @Size(max = 500)
    @Getter
    @Setter
    private String reason;
}

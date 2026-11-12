package com.api.crud.DTO;

import jakarta.validation.constraints.*;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class PaymentRefundRetryRequestDTO {

    @NotNull
    private UUID previousAttemptId;

    @NotBlank
    @Size(max = 500)
    private String reason;
}

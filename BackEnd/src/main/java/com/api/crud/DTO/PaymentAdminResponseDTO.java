package com.api.crud.DTO;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

public class PaymentAdminResponseDTO extends PaymentResponseDTO {

    @Getter
    @Setter
    private Instant acceptedAt;

    @Getter
    @Setter
    private String providerReferenceId;

    @Getter
    @Setter
    private String providerStatusDetail;

    @Getter
    @Setter
    private Long orderId;
}

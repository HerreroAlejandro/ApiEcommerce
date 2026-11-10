package com.api.crud.DTO;

import com.api.crud.models.enums.PaymentRefundStatus;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

public class PaymentRefundResponseDTO {

    @Getter
    @Setter
    private String id;

    @Getter
    @Setter
    private Long paymentId;

    @Getter
    @Setter
    private Long actorUserId;

    @Getter
    @Setter
    private String reason;

    @Getter
    @Setter
    private BigDecimal amount;

    @Getter
    @Setter
    private PaymentRefundStatus status;

    @Getter
    @Setter
    private String providerRefundId;

    @Getter
    @Setter
    private Instant requestedAt;

    @Getter
    @Setter
    private Instant confirmedAt;
}

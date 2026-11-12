package com.api.crud.DTO;

import com.api.crud.models.enums.PaymentRefundStatus;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
public class PaymentRefundAttemptResponseDTO {
    private String id;
    private int attemptNumber;
    private String previousAttemptId;
    private Long actorUserId;
    private String reason;
    private Instant requestedAt;
    private PaymentRefundStatus status;
    private String providerRefundId;
    private Instant confirmedAt;
    private Instant rejectedAt;
    private String rejectionCode;
    private String rejectionReason;
}

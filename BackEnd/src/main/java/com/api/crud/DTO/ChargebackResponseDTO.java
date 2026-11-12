package com.api.crud.DTO;

import com.api.crud.models.enums.ChargebackResolution;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
public class ChargebackResponseDTO {
    private Long id;
    private Long paymentId;
    private Long orderId;
    private String providerCaseId;
    private ChargebackResolution resolution;
    private Instant detectedAt;
    private Instant verifiedAt;
    private Instant providerUpdatedAt;
    private Instant documentationDeadline;
    private BigDecimal amount;
    private String currency;
    private Boolean coverageApplied;
    private String documentationStatus;
    private String providerReason;
    private boolean grouped;
    private String lastAdminNote;
    private Long lastActorUserId;
    private Instant lastNoteAt;
}

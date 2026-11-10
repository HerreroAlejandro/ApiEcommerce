package com.api.crud.DTO;

import com.api.crud.models.enums.PaymentReviewReason;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

public class PaymentResponseDTO {

    @Getter
    @Setter
    private Long id;

    @Getter
    @Setter
    private String providerPaymentId;

    @Getter
    @Setter
    private String providerStatus;

    @Getter
    @Setter
    private BigDecimal amount;

    @Getter
    @Setter
    private BigDecimal refundedAmount;

    @Getter
    @Setter
    private String currency;

    @Getter
    @Setter
    private String paymentMethod;

    @Getter
    @Setter
    private String paymentType;

    @Getter
    @Setter
    private PaymentReviewReason reviewReason;

    @Getter
    @Setter
    private Instant approvedAt;

    @Getter
    @Setter
    private Instant verifiedAt;
}

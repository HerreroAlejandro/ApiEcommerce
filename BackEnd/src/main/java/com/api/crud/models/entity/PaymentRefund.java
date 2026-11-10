package com.api.crud.models.entity;

import com.api.crud.models.enums.PaymentRefundStatus;

import jakarta.persistence.*;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "payment_refunds")
public class PaymentRefund {

    @Id
    @Column(length = 36)
    @Getter
    @Setter
    private String id;

    @Version
    private Long version;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false, unique = true, updatable = false)
    @Getter
    @Setter
    private Payment payment;

    @Column(nullable = false, updatable = false)
    @Getter
    @Setter
    private Long actorUserId;

    @Column(nullable = false, length = 500, updatable = false)
    @Getter
    @Setter
    private String reason;

    @Column(nullable = false, precision = 16, scale = 2, updatable = false)
    @Getter
    @Setter
    private BigDecimal amount;

    @Column(nullable = false, precision = 16, scale = 2, updatable = false)
    @Getter
    @Setter
    private BigDecimal previouslyRefundedAmount;

    @Column(nullable = false, length = 64, updatable = false)
    @Getter
    @Setter
    private String providerOrderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Getter
    @Setter
    private PaymentRefundStatus status;

    @Column(length = 64, unique = true)
    @Getter
    @Setter
    private String providerRefundId;

    @Column(nullable = false, updatable = false)
    @Getter
    @Setter
    private Instant requestedAt;

    @Getter
    @Setter
    private Instant confirmedAt;
}

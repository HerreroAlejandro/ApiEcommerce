package com.api.crud.models.entity;

import com.api.crud.models.enums.PaymentRefundStatus;

import jakarta.persistence.*;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(
        name = "payment_refund_attempts",
        uniqueConstraints = @UniqueConstraint(columnNames = {"refund_id", "attemptNumber"}))
@Getter
@Setter
public class PaymentRefundAttempt {

    @Id
    @Column(length = 36)
    private String id;

    @Version
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "refund_id", nullable = false, updatable = false)
    private PaymentRefund refund;

    @Column(nullable = false, updatable = false)
    private int attemptNumber;

    @Column(length = 36, unique = true, updatable = false)
    private String previousAttemptId;

    @Column(nullable = false, updatable = false)
    private Long actorUserId;

    @Column(nullable = false, length = 500, updatable = false)
    private String reason;

    @Column(nullable = false, updatable = false)
    private Instant requestedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentRefundStatus status;

    @Column(length = 64, unique = true)
    private String providerRefundId;

    private Instant confirmedAt;
    private Instant rejectedAt;

    @Column(length = 64)
    private String rejectionCode;

    @Column(length = 500)
    private String rejectionReason;
}

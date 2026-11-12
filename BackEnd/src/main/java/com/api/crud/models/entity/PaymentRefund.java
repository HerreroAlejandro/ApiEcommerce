package com.api.crud.models.entity;

import com.api.crud.models.enums.PaymentRefundStatus;

import jakarta.persistence.*;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "payment_refunds")
public class PaymentRefund {

    @Column(nullable = false, columnDefinition = "boolean default false", updatable = false)
    @Getter
    @Setter
    private boolean itemized;

    @ElementCollection
    @CollectionTable(
            name = "refund_item_allocations",
            joinColumns = @JoinColumn(name = "refund_id"),
            uniqueConstraints = @UniqueConstraint(columnNames = {"refund_id", "orderItemId"}))
    @Getter
    @Setter
    private List<RefundItemAllocation> items = new ArrayList<>();

    @Id
    @Column(length = 36)
    @Getter
    @Setter
    private String id;

    @Version
    private Long version;

    @Column(length = 36)
    @Setter
    private String currentAttemptId;

    @Column(nullable = false, columnDefinition = "integer default 1")
    @Getter
    @Setter
    private int attemptNumber = 1;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false, updatable = false)
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

    @Getter
    @Setter
    private Instant rejectedAt;

    @Column(length = 64)
    @Getter
    @Setter
    private String rejectionCode;

    @Column(length = 500)
    @Getter
    @Setter
    private String rejectionReason;

    public String getCurrentAttemptId() {
        return currentAttemptId == null ? id : currentAttemptId;
    }
}

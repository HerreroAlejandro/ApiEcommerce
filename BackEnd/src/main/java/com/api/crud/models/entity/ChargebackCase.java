package com.api.crud.models.entity;

import com.api.crud.models.enums.ChargebackResolution;

import jakarta.persistence.*;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
        name = "chargeback_cases",
        uniqueConstraints = @UniqueConstraint(columnNames = {"payment_id", "providerCaseId"}))
@Getter
@Setter
public class ChargebackCase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false, updatable = false)
    private Payment payment;

    @Column(nullable = false, length = 30, updatable = false)
    private String providerCaseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChargebackResolution resolution = ChargebackResolution.OPEN;

    @Column(nullable = false, updatable = false)
    private Instant detectedAt;

    private Instant verifiedAt;
    private Instant providerUpdatedAt;
    private Instant documentationDeadline;

    @Column(precision = 16, scale = 2)
    private BigDecimal amount;

    @Column(length = 3)
    private String currency;

    private Boolean coverageApplied;

    @Column(length = 40)
    private String documentationStatus;

    @Column(length = 100)
    private String providerReason;

    private boolean grouped;

    @Column(length = 500)
    private String lastAdminNote;

    private Long lastActorUserId;
    private Instant lastNoteAt;
}

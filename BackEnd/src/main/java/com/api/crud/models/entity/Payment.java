package com.api.crud.models.entity;

import com.api.crud.models.enums.PaymentReviewReason;

import jakarta.persistence.*;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

// Registra cada operación que Mercado Pago nos informa y que verificamos.
@Entity
@Table(name = "payments")
public class Payment {

    @Getter
    @Setter
    private Instant acceptedAt;

    @Column(length = 30, unique = true)
    @Getter
    @Setter
    private String providerReferenceId;

    @Column(length = 40)
    @Getter
    @Setter
    private String providerStatusDetail;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Getter
    @Setter
    private Long id;

    @Version
    @Getter
    @Setter
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "checkout_reference", nullable = false, updatable = false)
    @Getter
    @Setter
    private PaymentCheckout checkout;

    @Column(nullable = false, unique = true, length = 64, updatable = false)
    @Getter
    @Setter
    private String providerPaymentId;

    @Column(nullable = false, length = 40)
    @Getter
    @Setter
    private String providerStatus;

    @Column(nullable = false, precision = 16, scale = 2)
    @Getter
    @Setter
    private BigDecimal amount;

    @Column(nullable = false, precision = 16, scale = 2)
    @Getter
    @Setter
    private BigDecimal refundedAmount;

    @Column(nullable = false, length = 3)
    @Getter
    @Setter
    private String currency;

    @Column(nullable = false, length = 30)
    @Getter
    @Setter
    private String collectorId;

    @Column(nullable = false)
    @Getter
    @Setter
    private boolean liveMode;

    @Column(length = 50)
    @Getter
    @Setter
    private String paymentMethod;

    @Column(length = 40)
    @Getter
    @Setter
    private String paymentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    @Getter
    @Setter
    private PaymentReviewReason reviewReason;

    @Getter
    @Setter
    private Instant approvedAt;

    @Column(nullable = false)
    @Getter
    @Setter
    private Instant providerUpdatedAt;

    @Column(nullable = false)
    @Getter
    @Setter
    private Instant verifiedAt;
}

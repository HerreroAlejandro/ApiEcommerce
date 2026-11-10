package com.api.crud.models.entity;

import jakarta.persistence.*;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

// Vincula una orden con la pantalla de pago que preparamos en Mercado Pago.
@Entity
@Table(name = "payment_checkouts")
public class PaymentCheckout {

    @Id
    @Column(length = 36)
    @Getter
    @Setter
    private String reference;

    @Version
    @Getter
    @Setter
    private Long version;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, unique = true, updatable = false)
    @Getter
    @Setter
    private Order order;

    @Column(nullable = false, precision = 10, scale = 2, updatable = false)
    @Getter
    @Setter
    private BigDecimal amount;

    @Column(nullable = false, length = 3, updatable = false)
    @Getter
    @Setter
    private String currency;

    @Column(nullable = false, length = 30, updatable = false)
    @Getter
    @Setter
    private String collectorId;

    @Column(nullable = false, updatable = false)
    @Getter
    @Setter
    private boolean liveMode;

    @Column(nullable = false, updatable = false)
    @Getter
    @Setter
    private Instant expiresAt;

    @Column(nullable = false, updatable = false)
    @Getter
    @Setter
    private Instant createdAt;

    @Column(length = 64, unique = true)
    @Getter
    @Setter
    private String providerOrderId;

    @Lob
    @Column(columnDefinition = "LONGTEXT", updatable = false)
    @Getter
    @Setter
    private String requestBody;

    @Column(length = 1000)
    @Getter
    @Setter
    private String checkoutUrl;
}

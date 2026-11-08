package com.api.crud.models.entity;

import com.api.crud.models.enums.DigitalDeliveryStatus;

import jakarta.persistence.*;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "digital_deliveries")
public class DigitalDelivery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Getter
    @Setter
    private Long id;

    @Version
    @Getter
    @Setter
    private Long version;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_item_id", nullable = false, unique = true, updatable = false)
    @Getter
    @Setter
    private OrderItem orderItem;

    @Column(nullable = false, length = 200, updatable = false)
    @Getter
    @Setter
    private String sourceUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Getter
    @Setter
    private DigitalDeliveryStatus status;

    @Column(nullable = false, updatable = false)
    @Getter
    @Setter
    private LocalDateTime enabledAt;

    @Getter
    @Setter
    private LocalDateTime attemptedAt;

    @Getter
    @Setter
    private LocalDateTime revokedAt;

    @Column(nullable = false)
    @Getter
    @Setter
    private int attemptCount;

    @Column(length = 64)
    @Getter
    @Setter
    private String tokenHash;

    @Getter
    @Setter
    private LocalDateTime tokenExpiresAt;
}

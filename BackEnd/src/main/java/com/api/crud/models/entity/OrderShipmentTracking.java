package com.api.crud.models.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Embeddable
public class OrderShipmentTracking {

    @Column(name = "carrier", length = 60)
    @Getter
    @Setter
    private String carrier;

    @Column(name = "trackingNumber", length = 80)
    @Getter
    @Setter
    private String trackingNumber;

    @Column(name = "trackingUrl", length = 200)
    @Getter
    @Setter
    private String trackingUrl;

    @Column(name = "shippedAt", columnDefinition = "datetime(6)")
    @Getter
    @Setter
    private LocalDateTime shippedAt;

    @Column(name = "deliveredAt", columnDefinition = "datetime(6)")
    @Getter
    @Setter
    private LocalDateTime deliveredAt;
}

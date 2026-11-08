package com.api.crud.DTO;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

public class ShipmentTrackingDTO {

    @Getter
    @Setter
    private String carrier;

    @Getter
    @Setter
    private String trackingNumber;

    @Getter
    @Setter
    private String trackingUrl;

    @Getter
    @Setter
    private LocalDateTime shippedAt;

    @Getter
    @Setter
    private LocalDateTime deliveredAt;
}

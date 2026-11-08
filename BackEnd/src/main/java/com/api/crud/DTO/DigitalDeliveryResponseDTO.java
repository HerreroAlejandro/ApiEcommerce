package com.api.crud.DTO;

import com.api.crud.models.enums.DigitalDeliveryStatus;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

public class DigitalDeliveryResponseDTO {

    @Getter
    @Setter
    private Long id;

    @Getter
    @Setter
    private Long orderItemId;

    @Getter
    @Setter
    private String productName;

    @Getter
    @Setter
    private DigitalDeliveryStatus status;

    @Getter
    @Setter
    private LocalDateTime enabledAt;

    @Getter
    @Setter
    private LocalDateTime attemptedAt;

    @Getter
    @Setter
    private LocalDateTime revokedAt;

    @Getter
    @Setter
    private int attemptCount;
}

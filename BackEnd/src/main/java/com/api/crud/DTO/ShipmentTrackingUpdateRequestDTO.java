package com.api.crud.DTO;

import com.api.crud.validation.HttpUrl;

import jakarta.validation.constraints.*;

import lombok.Getter;
import lombok.Setter;

public class ShipmentTrackingUpdateRequestDTO {

    @NotBlank
    @Size(max = 60)
    @Getter
    @Setter
    private String carrier;

    @NotBlank
    @Size(max = 80)
    @Getter
    @Setter
    private String trackingNumber;

    @HttpUrl(allowBlank = true)
    @Size(max = 200)
    @Getter
    @Setter
    private String trackingUrl;

    @NotBlank
    @Size(max = 500)
    @Getter
    @Setter
    private String reason;
}

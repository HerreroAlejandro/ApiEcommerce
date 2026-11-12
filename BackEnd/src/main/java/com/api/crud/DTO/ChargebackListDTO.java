package com.api.crud.DTO;

import com.api.crud.models.enums.ChargebackResolution;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
public class ChargebackListDTO {
    private Long id;
    private Long paymentId;
    private Long orderId;
    private String providerCaseId;
    private ChargebackResolution resolution;
    private String documentationStatus;
    private Instant documentationDeadline;
    private BigDecimal amount;
    private String currency;
    private boolean grouped;
}

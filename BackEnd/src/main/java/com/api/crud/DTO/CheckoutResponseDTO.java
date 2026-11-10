package com.api.crud.DTO;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

public class CheckoutResponseDTO {

    @Getter
    @Setter
    private String reference;

    @Getter
    @Setter
    private Long orderId;

    @Getter
    @Setter
    private String providerOrderId;

    @Getter
    @Setter
    private String checkoutUrl;

    @Getter
    @Setter
    private BigDecimal amount;

    @Getter
    @Setter
    private String currency;

    @Getter
    @Setter
    private Instant expiresAt;
}

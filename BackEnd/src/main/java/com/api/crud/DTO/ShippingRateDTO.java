package com.api.crud.DTO;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

public class ShippingRateDTO {

    @Getter
    @Setter
    private BigDecimal shippingCost;

    public ShippingRateDTO(BigDecimal shippingCost) {
        this.shippingCost = shippingCost;
    }
}

package com.api.crud.models.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum ProductType {
    DIGITAL,
    PHYSICAL;

    @JsonCreator
    public static ProductType fromString(String value) {
        return ProductType.valueOf(value.toUpperCase());
    }
}

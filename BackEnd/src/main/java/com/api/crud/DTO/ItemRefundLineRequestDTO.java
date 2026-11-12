package com.api.crud.DTO;

import com.api.crud.validation.WholeQuantityDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ItemRefundLineRequestDTO {
    @NotNull
    @Positive
    private Long orderItemId;

    @NotNull
    @Positive
    @JsonDeserialize(using = WholeQuantityDeserializer.class)
    private Integer quantity;
}

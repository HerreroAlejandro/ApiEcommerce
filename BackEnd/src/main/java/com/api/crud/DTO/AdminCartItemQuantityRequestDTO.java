package com.api.crud.DTO;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

public class AdminCartItemQuantityRequestDTO {

    @NotNull
    @Positive
    @Max(99999999)
    @Getter
    @Setter
    private Integer amount;

    @NotBlank
    @Size(max = 500)
    @Getter
    @Setter
    private String reason;
}

package com.api.crud.DTO;

import jakarta.validation.constraints.*;

import lombok.Getter;
import lombok.Setter;

public class AdminCartItemAddRequestDTO {

    @NotNull
    @Positive
    @Getter
    @Setter
    private Long userId;

    @NotNull
    @Positive
    @Getter
    @Setter
    private Long productId;

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

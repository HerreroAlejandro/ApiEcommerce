package com.api.crud.DTO;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class ItemRefundRequestDTO {
    @NotNull
    private UUID reference;

    @NotBlank
    @Size(max = 500)
    private String reason;

    @NotEmpty
    @Size(max = 100)
    private List<@NotNull @Valid ItemRefundLineRequestDTO> items;
}

package com.api.crud.DTO;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class PhysicalReturnReceiveRequestDTO {

    @NotBlank
    @Size(max = 500)
    private String reason;

    @NotEmpty @Valid private List<@NotNull PhysicalReturnItemRequestDTO> items;
}

package com.api.crud.DTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

public class ChangeReasonRequestDTO {

    @NotBlank
    @Size(max = 500)
    @Getter
    @Setter
    private String reason;
}

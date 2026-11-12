package com.api.crud.DTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EmailVerificationRequestDTO {
    @NotBlank
    @Pattern(regexp = "[A-Za-z0-9_-]{43}", message = "Token de verificacion invalido")
    private String token;
}

package com.api.crud.DTO;

import jakarta.validation.constraints.*;

import lombok.Getter;
import lombok.Setter;

public class AccountRecoveryRequestDTO {

    @Getter
    @Setter
    @NotBlank
    @Email
    @Size(max = 254)
    private String email;

    public AccountRecoveryRequestDTO(String email) {
        this.email = email;
    }

    public AccountRecoveryRequestDTO() {}
}

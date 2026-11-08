package com.api.crud.DTO;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.constraints.*;
import com.api.crud.validation.PasswordValue;

import lombok.Getter;
import lombok.Setter;

public class PasswordResetDTO {

    @Getter @Setter
    @NotBlank @PasswordValue
    private String newPassword;

    @Getter @Setter
    @NotBlank @PasswordValue
    private String confirmPassword;

    public PasswordResetDTO(String newPassword, String confirmPassword) {
        this.newPassword = newPassword;
        this.confirmPassword = confirmPassword;
    }

    public PasswordResetDTO(){}
    @JsonIgnore
    @AssertTrue(message = "La nueva contraseña y su confirmacion deben coincidir")
    public boolean isPasswordConfirmationValid() {
        return newPassword == null || confirmPassword == null || newPassword.equals(confirmPassword);
    }
}

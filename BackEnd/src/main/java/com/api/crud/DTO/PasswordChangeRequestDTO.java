package com.api.crud.DTO;

import com.api.crud.validation.PasswordValue;
import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.constraints.*;

import lombok.Getter;
import lombok.Setter;

public class PasswordChangeRequestDTO {

    @NotNull
    @Getter
    @Setter
    @NotBlank
    @PasswordValue(min = 1, message = "La contraseña actual no puede superar 72 bytes UTF-8")
    private String currentPassword;

    @NotNull @Getter @Setter @NotBlank @PasswordValue private String newPassword;

    @NotNull @Getter @Setter @NotBlank @PasswordValue private String confirmPassword;

    public PasswordChangeRequestDTO(
            String currentPassword, String newPassword, String confirmPassword) {
        this.currentPassword = currentPassword;
        this.newPassword = newPassword;
        this.confirmPassword = confirmPassword;
    }

    public PasswordChangeRequestDTO() {}

    @JsonIgnore
    @AssertTrue(message = "La nueva contraseña y su confirmacion deben coincidir")
    public boolean isPasswordConfirmationValid() {
        return newPassword == null
                || confirmPassword == null
                || newPassword.equals(confirmPassword);
    }
}

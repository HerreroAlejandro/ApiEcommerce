package com.api.crud.DTO;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

public class PasswordChangeDTO {
    @NotNull @Getter @Setter
    private String currentPassword;

    @NotNull @Getter @Setter
    private String newPassword;

    @NotNull @Getter @Setter
    private String confirmPassword;

    public PasswordChangeDTO(String currentPassword, String newPassword,String confirmPassword) {
        this.currentPassword = currentPassword;
        this.newPassword = newPassword;
        this.confirmPassword = confirmPassword;
    }

    public PasswordChangeDTO(){}
}

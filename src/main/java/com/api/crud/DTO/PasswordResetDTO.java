package com.api.crud.DTO;

import lombok.Getter;
import lombok.Setter;

public class PasswordResetDTO {

    @Getter @Setter
    private String newPassword;

    @Getter @Setter
    private String confirmPassword;

    public PasswordResetDTO(String newPassword, String confirmPassword) {
        this.newPassword = newPassword;
        this.confirmPassword = confirmPassword;
    }

    public PasswordResetDTO(){}
}

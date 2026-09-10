package com.api.crud.DTO;

import lombok.Getter;
import lombok.Setter;

public class AdminPasswordResetDTO {

    @Getter @Setter
    private String newPassword;

    @Getter @Setter
    private String confirmPassword;

    public AdminPasswordResetDTO(String newPassword, String confirmPassword) {
        this.newPassword = newPassword;
        this.confirmPassword = confirmPassword;
    }

    public AdminPasswordResetDTO(){}
}

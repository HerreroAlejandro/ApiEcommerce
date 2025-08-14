package com.api.crud.DTO;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

public class PasswordChangeDTO {
    @NotNull @Getter @Setter
    private String currentPassword;

    @NotNull @Getter @Setter
    private String newPassword;

    public PasswordChangeDTO(String currentPassword, String newPassword) {
        this.currentPassword = currentPassword;
        this.newPassword = newPassword;
    }

    public PasswordChangeDTO(){}
}

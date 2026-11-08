package com.api.crud.DTO;

import jakarta.validation.constraints.*;
import com.api.crud.validation.PasswordValue;

import lombok.Getter;
import lombok.Setter;

public class LoginRequestDTO {

    @Getter @Setter
    @NotBlank @Email @Size(max = 254)
    private String email;

    @Getter @Setter
    @NotBlank @PasswordValue(min = 1, message = "La contraseña no puede superar 72 bytes UTF-8")
    private String password;

    public LoginRequestDTO(String email, String password) {
        this.email = email;
        this.password = password;
    }

    public LoginRequestDTO(){}


}

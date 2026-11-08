package com.api.crud.DTO;

import com.api.crud.validation.PasswordValue;

import jakarta.validation.constraints.*;

import lombok.Getter;
import lombok.Setter;

public class UserRegisterDTO {

    @Getter
    @Setter
    @NotBlank
    @Size(min = 2, max = 25)
    private String firstName;

    @Getter
    @Setter
    @NotBlank
    @Size(min = 2, max = 25)
    private String lastName;

    @Getter
    @Setter
    @NotBlank
    @Email
    @Size(max = 254)
    private String email;

    @Getter
    @Setter
    @Pattern(regexp = "(?=.*[0-9])[+0-9() .-]{8,20}", message = "Telefono invalido")
    private String phone;

    @Getter
    @Setter
    @NotBlank
    @PasswordValue
    private String password;

    public UserRegisterDTO(
            String firstName, String lastName, String email, String phone, String password) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.phone = phone;
        this.password = password;
    }

    public UserRegisterDTO() {}
}

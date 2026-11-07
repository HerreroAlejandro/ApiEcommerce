package com.api.crud.DTO;

import lombok.Getter;
import lombok.Setter;

public class LoginRequestDTO {

    @Getter @Setter
    private String email;

    @Getter @Setter
    private String password;

    public LoginRequestDTO(String email, String password) {
        this.email = email;
        this.password = password;
    }

    public LoginRequestDTO(){}


}

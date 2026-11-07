package com.api.crud.DTO;

import lombok.Getter;
import lombok.Setter;

public class AccountRecoveryRequestDTO {

    @Getter @Setter
    private String email;

    public AccountRecoveryRequestDTO(String email) {
        this.email = email;
    }

    public AccountRecoveryRequestDTO(){}
}

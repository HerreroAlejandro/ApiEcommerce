package com.api.crud.DTO;

import lombok.Getter;
import lombok.Setter;

public class UserUpdateRequestDTO {

    @Getter @Setter
    private String firstName;

    @Getter @Setter
    private String lastName;

    @Getter @Setter
    private String phone;

    public UserUpdateRequestDTO(String firstName, String lastName, String phone) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.phone = phone;
    }

    public UserUpdateRequestDTO(){}
}
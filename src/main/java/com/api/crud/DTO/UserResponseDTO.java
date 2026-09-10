package com.api.crud.DTO;

import lombok.Getter;
import lombok.Setter;

public class UserResponseDTO {

    @Getter @Setter
    private long id;

    @Getter @Setter
    private String firstName;

    @Getter @Setter
    private String lastName;

    @Getter @Setter
    private String email;

    @Getter @Setter
    private String phone;

    public UserResponseDTO(long id, String firstName, String lastName, String email, String phone){
        this.id=id;
        this.firstName=firstName;
        this.lastName=lastName;
        this.email=email;
        this.phone=phone;
    }

    public UserResponseDTO(){}
}
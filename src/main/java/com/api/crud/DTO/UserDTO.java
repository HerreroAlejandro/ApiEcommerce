package com.api.crud.DTO;

import lombok.Getter;
import lombok.Setter;

public class UserDTO {

    @Getter @Setter
    private long id;

    @Getter @Setter
    private String firstName;

    @Getter @Setter
    private String lastName;

    @Getter @Setter
    private String email;

    public UserDTO(long id, String firstName, String lastName, String email){
        this.id=id;
        this.firstName=firstName;
        this.lastName=lastName;
        this.email=email;
    }

    public UserDTO(){}
}
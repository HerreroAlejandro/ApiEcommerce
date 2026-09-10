package com.api.crud.DTO;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

public class UserDetailAdminResponseDTO {

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

    @Getter @Setter
    private boolean active;

    @Getter @Setter
    private List<String> roles;

    public UserDetailAdminResponseDTO(long id, String firstName, String lastName, String email, String phone, boolean active, List<String> roles) {
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.phone = phone;
        this.active = active;
        this.roles = roles;
    }

    public UserDetailAdminResponseDTO(){}
}

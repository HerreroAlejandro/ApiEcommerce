package com.api.crud.DTO;

import lombok.Getter;
import lombok.Setter;
import java.util.ArrayList;
import java.util.List;

public class UserModelDTO {

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
    private String password;

    @Getter @Setter
    private List<String> roles;

    @Getter @Setter
    private List<String> orders;

    public UserModelDTO(long id, String firstName, String lastName, String email, String phone, String password,List<String> roles, List<String> orders) {
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.phone = phone;
        this.password = password;
        this.roles=roles;
        this.orders = orders != null ? orders : new ArrayList<>();
    }

    public UserModelDTO(){}
}

package com.api.crud.DTO;

import com.api.crud.models.enums.AccountStatus;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

public class UserDetailAdminResponseDTO {

    @Getter
    @Setter
    private long id;

    @Getter
    @Setter
    private String firstName;

    @Getter
    @Setter
    private String lastName;

    @Getter
    @Setter
    private String email;

    @Getter
    @Setter
    private String phone;

    @Getter
    @Setter
    private AccountStatus status;

    @Getter
    @Setter
    private List<String> roles;

    public UserDetailAdminResponseDTO(
            long id,
            String firstName,
            String lastName,
            String email,
            String phone,
            AccountStatus status,
            List<String> roles) {
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.phone = phone;
        this.status = status;
        this.roles = roles;
    }

    public UserDetailAdminResponseDTO() {}
}

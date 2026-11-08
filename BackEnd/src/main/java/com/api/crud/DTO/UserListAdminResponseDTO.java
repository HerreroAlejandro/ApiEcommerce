package com.api.crud.DTO;

import com.api.crud.models.enums.AccountStatus;

import lombok.Getter;
import lombok.Setter;

public class UserListAdminResponseDTO {

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

    public UserListAdminResponseDTO(
            long id,
            String firstName,
            String lastName,
            String email,
            String phone,
            AccountStatus status) {
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.phone = phone;
        this.status = status;
    }

    public UserListAdminResponseDTO() {}
}

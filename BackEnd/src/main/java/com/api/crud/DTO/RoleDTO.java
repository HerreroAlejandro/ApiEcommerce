package com.api.crud.DTO;

import lombok.Getter;
import lombok.Setter;


public class RoleDTO {

    @Getter @Setter
    private String nameRole;

    public RoleDTO(String nameRole) {
        this.nameRole = nameRole;
    }

    public RoleDTO(){}

}

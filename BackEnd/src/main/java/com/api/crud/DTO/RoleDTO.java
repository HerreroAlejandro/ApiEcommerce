package com.api.crud.DTO;

import jakarta.validation.constraints.*;

import lombok.Getter;
import lombok.Setter;

public class RoleDTO {

    @Getter
    @Setter
    @NotBlank
    @Size(min = 2, max = 20)
    @Pattern(
            regexp = "[A-Z][A-Z0-9_]*",
            message = "El rol debe usar letras mayusculas, numeros o guion bajo")
    private String nameRole;

    public RoleDTO(String nameRole) {
        this.nameRole = nameRole;
    }

    public RoleDTO() {}
}

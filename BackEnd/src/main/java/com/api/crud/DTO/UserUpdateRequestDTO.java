package com.api.crud.DTO;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.constraints.*;

import lombok.Getter;
import lombok.Setter;

public class UserUpdateRequestDTO {

    @Getter
    @Setter
    @Size(min = 2, max = 25)
    @Pattern(regexp = "(?s).*\\S.*", message = "El nombre no puede estar vacio")
    private String firstName;

    @Getter
    @Setter
    @Size(min = 2, max = 25)
    @Pattern(regexp = "(?s).*\\S.*", message = "El apellido no puede estar vacio")
    private String lastName;

    @Getter
    @Setter
    @Pattern(regexp = "(?=.*[0-9])[+0-9() .-]{8,20}", message = "Telefono invalido")
    private String phone;

    public UserUpdateRequestDTO(String firstName, String lastName, String phone) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.phone = phone;
    }

    public UserUpdateRequestDTO() {}

    @JsonIgnore
    @AssertTrue(message = "Debe indicar al menos un campo para actualizar")
    public boolean isUpdateProvided() {
        return firstName != null || lastName != null || phone != null;
    }
}

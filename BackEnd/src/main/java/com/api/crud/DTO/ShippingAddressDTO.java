package com.api.crud.DTO;

import jakarta.validation.constraints.*;

import lombok.Getter;
import lombok.Setter;

public class ShippingAddressDTO {

    @NotBlank
    @Size(max = 100)
    @Getter
    @Setter
    private String recipientName;

    @NotBlank
    @Size(max = 25)
    @Pattern(regexp = "\\+?[0-9][0-9 ()-]{6,23}[0-9]", message = "Telefono de contacto invalido")
    @Getter
    @Setter
    private String phone;

    @NotBlank
    @Size(max = 120)
    @Getter
    @Setter
    private String street;

    @NotBlank
    @Size(max = 20)
    @Getter
    @Setter
    private String streetNumber;

    @Size(max = 80)
    @Getter
    @Setter
    private String apartment;

    @NotBlank
    @Size(max = 80)
    @Getter
    @Setter
    private String city;

    @NotBlank
    @Size(max = 80)
    @Getter
    @Setter
    private String region;

    @NotBlank
    @Size(max = 20)
    @Getter
    @Setter
    private String postalCode;

    @NotBlank
    @Pattern(regexp = "[A-Z]{2}", message = "Use un codigo de pais de dos letras mayusculas")
    @Size(max = 2)
    @Getter
    @Setter
    private String countryCode;
}

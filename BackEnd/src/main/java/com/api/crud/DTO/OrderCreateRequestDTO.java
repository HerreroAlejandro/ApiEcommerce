package com.api.crud.DTO;

import jakarta.validation.Valid;

import lombok.Getter;
import lombok.Setter;

public class OrderCreateRequestDTO {

    @Valid @Getter @Setter private ShippingAddressDTO shippingAddress;
}

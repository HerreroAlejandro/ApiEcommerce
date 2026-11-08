package com.api.crud.models.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import lombok.Getter;
import lombok.Setter;

@Embeddable
public class OrderShippingAddress {

    @Column(name = "shippingRecipientName", length = 100, updatable = false)
    @Getter
    @Setter
    private String recipientName;

    @Column(name = "shippingPhone", length = 25, updatable = false)
    @Getter
    @Setter
    private String phone;

    @Column(name = "shippingStreet", length = 120, updatable = false)
    @Getter
    @Setter
    private String street;

    @Column(name = "shippingStreetNumber", length = 20, updatable = false)
    @Getter
    @Setter
    private String streetNumber;

    @Column(name = "shippingApartment", length = 80, updatable = false)
    @Getter
    @Setter
    private String apartment;

    @Column(name = "shippingCity", length = 80, updatable = false)
    @Getter
    @Setter
    private String city;

    @Column(name = "shippingRegion", length = 80, updatable = false)
    @Getter
    @Setter
    private String region;

    @Column(name = "shippingPostalCode", length = 20, updatable = false)
    @Getter
    @Setter
    private String postalCode;

    @Column(name = "shippingCountryCode", length = 2, updatable = false)
    @Getter
    @Setter
    private String countryCode;
}

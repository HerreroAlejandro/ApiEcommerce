package com.api.crud.models.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import lombok.Getter;
import lombok.Setter;

@Embeddable
@Getter
@Setter
public class PhysicalReturnLine {

    @Column(nullable = false)
    private Long orderItemId;

    @Column(nullable = false)
    private int receivedQuantity;

    @Column(nullable = false)
    private int restockedQuantity;
}

package com.api.crud.DTO;

import com.api.crud.models.enums.ProductType;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

public class MyCartItemResponseDTO {

    @Getter
    @Setter
    private Long idCartItem;

    @Getter
    @Setter
    private Long productId;

    @Getter
    @Setter
    private String nameProduct;

    @Getter
    @Setter
    private String imageUrl;

    @Getter
    @Setter
    private BigDecimal priceProduct;

    @Getter
    @Setter
    private int amountCartItem;

    @Getter
    @Setter
    private boolean active;

    @Getter
    @Setter
    private ProductType type;

    @Getter
    @Setter
    private Integer stockProduct;

    @Getter
    @Setter
    private BigDecimal subtotal;
}

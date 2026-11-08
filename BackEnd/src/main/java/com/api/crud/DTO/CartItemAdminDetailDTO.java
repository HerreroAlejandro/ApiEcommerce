package com.api.crud.DTO;

import com.api.crud.models.enums.ProductType;
import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

public class CartItemAdminDetailDTO {

    @Getter
    @Setter
    private Long idCartItem;

    @Getter
    @Setter
    private Long cartId;

    @Getter
    @Setter
    private Long productId;

    @Getter
    @Setter
    private String nameProduct;

    @Getter
    @Setter
    private int amountCartItem;

    @Getter
    @Setter
    private BigDecimal priceProduct;

    @Getter
    @Setter
    private BigDecimal subtotal;

    @Getter
    @Setter
    private String imageUrl;

    @Getter
    @Setter
    private ProductType type;

    @Getter
    @Setter
    private boolean active;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Getter
    @Setter
    private Integer stockProduct;
}

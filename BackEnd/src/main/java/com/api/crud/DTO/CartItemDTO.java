package com.api.crud.DTO;

import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

public class CartItemDTO {

    @Getter @Setter
    private Long idCartItem;

    @Getter @Setter
    private Long cartId;

    @Getter @Setter
    private Long productId;

    @Getter @Setter
    private String nameProduct;

    @Getter @Setter
    private BigDecimal priceProduct;

    @Getter @Setter
    private String imageUrl;

    @Getter @Setter
    private int amountCartItem;

    @Getter @Setter
    private BigDecimal subtotal;

    public CartItemDTO(Long idCartItem, Long cartId, Long productId, int amountCartItem, BigDecimal subtotal) {
        this.idCartItem = idCartItem;
        this.cartId = cartId;
        this.productId = productId;
        this.amountCartItem = amountCartItem;
        this.subtotal = subtotal;
    }

    public CartItemDTO(Long idCartItem, Long cartId, Long productId, String nameProduct, BigDecimal priceProduct, String imageUrl, int amountCartItem, BigDecimal subtotal) {
        this.idCartItem = idCartItem;
        this.cartId = cartId;
        this.productId = productId;
        this.nameProduct = nameProduct;
        this.priceProduct = priceProduct;
        this.imageUrl = imageUrl;
        this.amountCartItem = amountCartItem;
        this.subtotal = subtotal;
    }

    public CartItemDTO(){}

}

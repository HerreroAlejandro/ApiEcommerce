package com.api.crud.DTO;

import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@Getter
@Setter
public class MyCartItemResponseDTO {
    private Long idCartItem;
    private Long productId;
    private String nameProduct;
    private String imageUrl;
    private BigDecimal priceProduct;
    private int amountCartItem;
    private BigDecimal subtotal;
}

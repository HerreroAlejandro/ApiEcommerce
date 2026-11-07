package com.api.crud.DTO;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@Getter
@Setter
public class CartItemAdminListDTO {
    private Long idCartItem;
    private Long productId;
    private String nameProduct;
    private int amountCartItem;
    private BigDecimal priceProduct;
    private BigDecimal subtotal;
}

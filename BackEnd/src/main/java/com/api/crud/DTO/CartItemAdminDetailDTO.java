package com.api.crud.DTO;
import com.api.crud.models.enums.ProductType;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@Getter
@Setter
public class CartItemAdminDetailDTO {
    private Long idCartItem;
    private Long cartId;
    private Long productId;
    private String nameProduct;
    private int amountCartItem;
    private BigDecimal priceProduct;
    private BigDecimal subtotal;
    private String imageUrl;
    private ProductType type;
    private boolean active;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Integer stockProduct;
}

package com.api.crud.DTO;

import lombok.Getter;
import lombok.Setter;
import java.util.List;

public class CartDTO {

    @Getter @Setter
    private Long idCart;

    @Getter @Setter
    private Long userCart;

    @Getter @Setter
    private List<CartItemDTO> itemsCart;

    public CartDTO(Long idCart, Long userCart, List<CartItemDTO> itemsCart) {
        this.idCart = idCart;
        this.userCart = userCart;
        this.itemsCart = itemsCart;
    }

    public CartDTO(){}
}

package com.api.crud.DTO;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class CartAdminDetailDTO {

    @Getter
    @Setter
    private Long idCart;

    @Getter
    @Setter
    private Long userId;

    @Getter
    @Setter
    private boolean active;

    @Getter
    @Setter
    private LocalDate creationDate;

    @Getter
    @Setter
    private int itemCount;

    @Getter
    @Setter
    private BigDecimal total;

    @Getter
    @Setter
    private List<CartItemAdminListDTO> itemsCart;
}

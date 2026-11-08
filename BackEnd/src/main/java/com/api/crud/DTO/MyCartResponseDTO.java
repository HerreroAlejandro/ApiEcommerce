package com.api.crud.DTO;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

public class MyCartResponseDTO {

    @Getter
    @Setter
    private List<MyCartItemResponseDTO> itemsCart;

    @Getter
    @Setter
    private BigDecimal total;
}

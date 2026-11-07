package com.api.crud.DTO;

import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
public class MyCartResponseDTO {
    private List<MyCartItemResponseDTO> itemsCart;
    private BigDecimal total;
}

package com.api.crud.DTO;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
@Getter @Setter
public class CartAdminDetailDTO {
    private Long idCart;
    private Long userId;
    private boolean active;
    private LocalDate creationDate;
    private int itemCount;
    private BigDecimal total;
    private List<CartItemAdminListDTO> itemsCart;
}

package com.api.crud.DTO;
import lombok.Getter;
import lombok.Setter;
@Getter @Setter
public class CartAdminDetailDTO {
    private Long idCart;
    private Long userId;
    private boolean active;
    private java.time.LocalDate creationDate;
    private int itemCount;
    private java.math.BigDecimal total;
    private java.util.List<CartItemAdminListDTO> itemsCart;
}

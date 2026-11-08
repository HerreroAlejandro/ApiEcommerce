package com.api.crud.DTO;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
@Getter @Setter
public class CartAdminListDTO {
    public CartAdminListDTO() {}

    public CartAdminListDTO(Long idCart, Long userId, boolean active, LocalDate creationDate,
                            Long itemCount, BigDecimal total) {
        this.idCart = idCart;
        this.userId = userId;
        this.active = active;
        this.creationDate = creationDate;
        this.itemCount = Math.toIntExact(itemCount);
        this.total = total;
    }
    private Long idCart;
    private Long userId;
    private boolean active;
    private LocalDate creationDate;
    private int itemCount;
    private BigDecimal total;

}

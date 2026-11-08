package com.api.crud.DTO;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

public class CartAdminListDTO {

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

    public CartAdminListDTO() {}

    public CartAdminListDTO(
            Long idCart,
            Long userId,
            boolean active,
            LocalDate creationDate,
            Long itemCount,
            BigDecimal total) {
        this.idCart = idCart;
        this.userId = userId;
        this.active = active;
        this.creationDate = creationDate;
        this.itemCount = Math.toIntExact(itemCount);
        this.total = total;
    }
}

package com.api.crud.DTO;

import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class OrderDTO {

    @Getter @Setter
    private Long idOrder;

    @Getter @Setter
    private BigDecimal totalOrder;

    @Getter @Setter
    private LocalDateTime purchaseDateOrder;

    @Getter @Setter
    private String paymentStatus;

    @Getter @Setter
    private String orderStatus;

    @Getter @Setter
    private Long userId;

    @Getter @Setter
    private String userEmail;

    @Getter @Setter
    private List<OrderItemDTO> itemsOrder;

    public OrderDTO() {}

    public OrderDTO(Long idOrder, BigDecimal totalOrder, LocalDateTime purchaseDateOrder, String paymentStatus, String orderStatus, Long userId,
                    String userEmail, List<OrderItemDTO> itemsOrder) {
        this.idOrder = idOrder;
        this.totalOrder = totalOrder;
        this.purchaseDateOrder = purchaseDateOrder;
        this.paymentStatus = paymentStatus;
        this.orderStatus = orderStatus;
        this.userId = userId;
        this.userEmail = userEmail;
        this.itemsOrder = itemsOrder;
    }
}

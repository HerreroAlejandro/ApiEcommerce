package com.api.crud.DTO;

import com.api.crud.models.enums.OrderStatus;
import com.api.crud.models.enums.PaymentStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
@Getter @Setter
public class OrderAdminListDTO {
    private Long idOrder;
    private BigDecimal totalOrder;
    private LocalDateTime purchaseDateOrder;
    private OrderStatus orderStatus;
    private PaymentStatus paymentStatus;
    private Long userId;
    private String userEmail;
}

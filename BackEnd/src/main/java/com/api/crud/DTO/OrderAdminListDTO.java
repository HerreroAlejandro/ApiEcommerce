package com.api.crud.DTO;
import lombok.Getter;
import lombok.Setter;
@Getter @Setter
public class OrderAdminListDTO {
    private Long idOrder;
    private java.math.BigDecimal totalOrder;
    private java.time.LocalDateTime purchaseDateOrder;
    private com.api.crud.models.enums.OrderStatus orderStatus;
    private com.api.crud.models.enums.PaymentStatus paymentStatus;
    private Long userId;
    private String userEmail;
}

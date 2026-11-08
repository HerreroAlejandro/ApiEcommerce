package com.api.crud.DTO;

import com.api.crud.models.enums.OrderStatus;
import com.api.crud.models.enums.PaymentStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
@Getter @Setter
public class OrderAdminDetailDTO {
    private Long idOrder;
    private BigDecimal totalOrder;
    private LocalDateTime purchaseDateOrder;
    private OrderStatus orderStatus;
    private PaymentStatus paymentStatus;
    private Long userId;
    private String userEmail;
    private List<OrderItemResponseDTO> itemsOrder;
}

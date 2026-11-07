package com.api.crud.DTO;
import com.api.crud.models.enums.OrderStatus;
import com.api.crud.models.enums.PaymentStatus;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
@Getter @Setter
public class OrderItemAdminDetailDTO {
    private Long idOrderItem;
    private Long orderId;
    private Long userId;
    private OrderStatus orderStatus;
    private PaymentStatus paymentStatus;
    private Long productId;
    private String productName;
    private int amountOrderItem;
    private BigDecimal priceOrderItem;
    private BigDecimal subtotal;
}
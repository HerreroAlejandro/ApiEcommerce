package com.api.crud.DTO;

import com.api.crud.models.enums.StorePaymentMethod;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class StoreSaleResponseDTO {
    private String reference;
    private StorePaymentMethod paymentMethod;
    private String paymentReference;
    private LocalDateTime paidAt;
    private Long cashierUserId;
    private String customerName;
    private String customerPhone;
    private LocalDateTime deliveredAt;
    private LocalDateTime refundedAt;
    private Long refundedBy;
    private String refundReason;
}

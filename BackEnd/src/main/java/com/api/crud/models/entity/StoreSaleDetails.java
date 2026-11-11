package com.api.crud.models.entity;

import com.api.crud.models.enums.StorePaymentMethod;

import jakarta.persistence.*;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Embeddable
@Getter
@Setter
public class StoreSaleDetails {

    @Column(name = "storeSaleReference", length = 36, unique = true, updatable = false)
    private String reference;

    @Column(name = "storeSaleRequestHash", length = 64, updatable = false)
    private String requestHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "storePaymentMethod", length = 30, updatable = false)
    private StorePaymentMethod paymentMethod;

    @Column(name = "storePaymentReference", length = 100, updatable = false)
    private String paymentReference;

    @Column(name = "storePaidAt", updatable = false)
    private LocalDateTime paidAt;

    @Column(name = "storeCashierUserId", updatable = false)
    private Long cashierUserId;

    @Column(name = "storeCustomerName", length = 100, updatable = false)
    private String customerName;

    @Column(name = "storeCustomerPhone", length = 25, updatable = false)
    private String customerPhone;

    @Column(name = "storeDeliveredAt")
    private LocalDateTime deliveredAt;

    @Column(name = "storeRefundedAt")
    private LocalDateTime refundedAt;

    @Column(name = "storeRefundedBy")
    private Long refundedBy;

    @Column(name = "storeRefundReason", length = 500)
    private String refundReason;
}

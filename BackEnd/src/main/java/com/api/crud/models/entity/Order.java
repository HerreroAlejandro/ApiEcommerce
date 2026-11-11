package com.api.crud.models.entity;

import com.api.crud.models.enums.DeliveryMethod;
import com.api.crud.models.enums.OrderStatus;
import com.api.crud.models.enums.PaymentStatus;
import com.api.crud.models.enums.SalesChannel;

import jakarta.persistence.*;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(
        name = "orders",
        indexes =
                @Index(
                        name = "idx_orders_expiration",
                        columnList = "orderStatus,paymentStatus,expiresAt"))
public class Order {

    @Version
    private Long version;

    @Enumerated(EnumType.STRING)
    @Column(
            nullable = false,
            columnDefinition = "enum('ONLINE','IN_STORE') default 'ONLINE'",
            updatable = false)
    @Getter
    @Setter
    private SalesChannel salesChannel = SalesChannel.ONLINE;

    @Enumerated(EnumType.STRING)
    @Column(
            nullable = false,
            columnDefinition =
                    "enum('SHIPPING','STORE_PICKUP','DIGITAL_DOWNLOAD') default 'SHIPPING'",
            updatable = false)
    @Getter
    @Setter
    private DeliveryMethod deliveryMethod = DeliveryMethod.SHIPPING;

    @Embedded
    @Getter
    @Setter
    private StoreSaleDetails storeSale;

    @Column(nullable = false, columnDefinition = "boolean default false")
    @Getter
    @Setter
    private boolean stockDeducted;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Getter
    @Setter
    private Long idOrder;

    @NotNull(message = "Total cannot be null")
    @Digits(integer = 8, fraction = 2)
    @Getter
    @Setter
    @Column(name = "totalOrder")
    private BigDecimal totalOrder;

    @Column(
            nullable = false,
            precision = 10,
            scale = 2,
            columnDefinition = "decimal(10,2) default 0")
    @Getter
    @Setter
    private BigDecimal itemsSubtotal = new BigDecimal("0.00");

    @Column(
            nullable = false,
            precision = 10,
            scale = 2,
            columnDefinition = "decimal(10,2) default 0")
    @Getter
    @Setter
    private BigDecimal shippingCost = new BigDecimal("0.00");

    @Embedded
    @Getter
    @Setter
    private OrderShippingAddress shippingAddress;

    @Embedded
    @Getter
    @Setter
    private OrderShipmentTracking shipmentTracking;

    @NotNull(message = "purchaseDate cannot be null")
    @Getter
    @Setter
    @Column(name = "purchaseDateOrder")
    private LocalDateTime purchaseDateOrder;

    @Getter
    @Setter
    @Column(name = "expiresAt")
    private LocalDateTime expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "paymentStatus", nullable = false)
    @Getter
    @Setter
    private PaymentStatus paymentStatus;

    @Column(length = 64)
    @Getter
    @Setter
    private String providerPaymentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "orderStatus", nullable = false)
    @Getter
    @Setter
    private OrderStatus orderStatus;

    @ManyToOne
    @JoinColumn(name = "user_id")
    @Getter
    @Setter
    private UserModel userOrder;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
    @Getter
    @Setter
    private List<OrderItem> itemsOrder;

    public Order(
            Long idOrder,
            UserModel userOrder,
            List<OrderItem> itemsOrder,
            BigDecimal totalOrder,
            LocalDateTime purchaseDateOrder) {
        this.idOrder = idOrder;
        this.userOrder = userOrder;
        this.itemsOrder = itemsOrder;
        this.totalOrder = totalOrder;
        this.purchaseDateOrder = purchaseDateOrder;
    }

    public Order() {}
}

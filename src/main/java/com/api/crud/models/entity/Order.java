package com.api.crud.models.entity;

import com.api.crud.models.enums.OrderStatus;
import com.api.crud.models.enums.PaymentStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "orders")
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Getter @Setter
    private Long idOrder;

    @NotNull(message = "Total cannot be null")
    @Digits(integer = 8, fraction = 2)
    @Getter @Setter
    @Column(name = "totalOrder")
    private BigDecimal totalOrder;

    @NotNull(message = "purchaseDate cannot be null")
    @Getter @Setter
    @Column(name = "purchaseDateOrder")
    private LocalDateTime purchaseDateOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "paymentStatus", nullable = false)
    @Getter @Setter
    private PaymentStatus paymentStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "orderStatus", nullable = false)
    @Getter @Setter
    private OrderStatus orderStatus;

    @ManyToOne
    @JoinColumn(name = "user_id")
    @Getter @Setter
    private UserModel userOrder;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
    @Getter @Setter
    private List<OrderItem> itemsOrder;

    public Order(Long idOrder, UserModel userOrder, List<OrderItem> itemsOrder, BigDecimal totalOrder, LocalDateTime purchaseDateOrder) {
        this.idOrder = idOrder;
        this.userOrder = userOrder;
        this.itemsOrder = itemsOrder;
        this.totalOrder = totalOrder;
        this.purchaseDateOrder = purchaseDateOrder;
    }

    public Order(){}

}

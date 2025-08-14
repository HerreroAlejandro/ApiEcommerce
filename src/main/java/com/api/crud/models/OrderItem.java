package com.api.crud.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.Digits;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "order_items")
public class OrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Getter @Setter
    private Long idOrderItem;

    @Getter @Setter
    @Digits(integer = 8, fraction = 0)
    @Column(name = "orderItem")
    private double orderItem;

    @Getter @Setter
    @Digits(integer = 5, fraction = 2)
    @Column(name = "priceOrderItem")
    private BigDecimal priceOrderItem;

    @Getter @Setter
    @Digits(integer = 8, fraction = 0)
    @Column(name = "amountOrderItem")
    private int amountOrderItem;

    @ManyToOne
    @JoinColumn(name = "order_id")
    @Getter @Setter
    private Order order;

    @ManyToOne
    @JoinColumn(name = "product_id")
    @Getter @Setter
    private Product productOrderItem;

    public OrderItem(Long idOrderItem, Order order, Product productOrderItem, int amountOrderItem, BigDecimal priceOrderItem) {
        this.idOrderItem = idOrderItem;
        this.order = order;
        this.productOrderItem = productOrderItem;
        this.amountOrderItem = amountOrderItem;
        this.priceOrderItem = priceOrderItem;
    }

    public OrderItem(){}

}

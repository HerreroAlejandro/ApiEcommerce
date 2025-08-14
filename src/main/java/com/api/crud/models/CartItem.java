package com.api.crud.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.Digits;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@Entity
@Table(name = "cart_items")
public class CartItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Getter @Setter
    private Long idCartItem;

    @ManyToOne
    @JoinColumn(name = "cart_id")
    @Getter @Setter
    private Cart cart;

    @ManyToOne
    @JoinColumn(name = "product_id")
    @Getter @Setter
    private Product productCartItem;

    @Column(name = "amountCartItem")
    @Getter @Setter
    @Digits(integer = 8, fraction = 0)
    private int amountCartItem;

    @Column(name = "priceCartItem")
    @Getter @Setter
    @Digits(integer = 5, fraction = 2)
    private BigDecimal priceCartItem;


    public CartItem(Long idCartItem, Cart cart, Product productCartItem, int amountCartItem, BigDecimal priceCartItem) {
        this.idCartItem = idCartItem;
        this.cart = cart;
        this.productCartItem = productCartItem;
        this.amountCartItem = amountCartItem;
        this.priceCartItem = priceCartItem;
    }

    public CartItem(){}

}

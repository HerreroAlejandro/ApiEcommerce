package com.api.crud.models.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Digits;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@Entity
@Table(name = "cart_items", uniqueConstraints = @UniqueConstraint(
        name = "uk_cart_item_product", columnNames = {"cart_id", "product_id"}))
public class CartItem {
    @Version
    private Long version;
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
    @Digits(integer = 8, fraction = 2)
    private BigDecimal priceCartItem;


    public CartItem(Long idCartItem, Cart cart, Product productCartItem, int amountCartItem, BigDecimal priceCartItem) {
        this.idCartItem = idCartItem;
        this.cart = cart;
        this.productCartItem = productCartItem;
        this.amountCartItem = amountCartItem;
        this.priceCartItem = priceCartItem;
    }

    public CartItem(){}

    public BigDecimal getPriceCartItem() {
        return productCartItem.getPriceProduct().multiply(BigDecimal.valueOf(amountCartItem));
    }

}

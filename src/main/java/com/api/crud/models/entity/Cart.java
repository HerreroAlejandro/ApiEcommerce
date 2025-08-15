package com.api.crud.models.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table (name = "carts")
public class Cart {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Getter @Setter
    private Long idCart;

    @ManyToOne
    @JoinColumn(name = "user_id")
    @Getter @Setter
    private UserModel userCart;

    @Column(nullable = false)
    @Getter @Setter
    private boolean active = true;

    @Column(nullable = false, updatable = false)
    @Getter @Setter
    private LocalDate creationDate;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    @Getter @Setter
    private List<CartItem> itemsCart = new ArrayList<>();

    public Cart(Long idCart, UserModel userCart, LocalDate creationDate, List<CartItem> itemsCart) {
        this.idCart = idCart;
        this.userCart = userCart;
        this.creationDate = LocalDate.now();
        this.itemsCart = new ArrayList<>();
    }

    public Cart(){}

}

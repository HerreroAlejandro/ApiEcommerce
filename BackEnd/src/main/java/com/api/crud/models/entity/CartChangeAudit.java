package com.api.crud.models.entity;

import jakarta.persistence.*;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "cart_change_audit")
public class CartChangeAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Getter
    @Setter
    private Long id;

    @Column(nullable = false, updatable = false)
    @Getter
    @Setter
    private Long actorUserId;

    @Column(nullable = false, updatable = false)
    @Getter
    @Setter
    private Long targetUserId;

    @Column(nullable = false, updatable = false)
    @Getter
    @Setter
    private Long cartId;

    @Column(updatable = false)
    @Getter
    @Setter
    private Long productId;

    @Column(nullable = false, updatable = false)
    @Getter
    @Setter
    private String action;

    @Column(nullable = false, updatable = false)
    @Getter
    @Setter
    private int previousAmount;

    @Column(nullable = false, updatable = false)
    @Getter
    @Setter
    private int newAmount;

    @Column(nullable = false, updatable = false, length = 500)
    @Getter
    @Setter
    private String reason;

    @Column(nullable = false, updatable = false)
    @Getter
    @Setter
    private LocalDateTime createdAt;
}

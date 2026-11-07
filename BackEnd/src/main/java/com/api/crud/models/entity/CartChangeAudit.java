package com.api.crud.models.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "cart_change_audit")
@Getter @Setter
public class CartChangeAudit {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, updatable = false) private Long actorUserId;
    @Column(nullable = false, updatable = false) private Long targetUserId;
    @Column(nullable = false, updatable = false) private Long cartId;
    @Column(updatable = false) private Long productId;
    @Column(nullable = false, updatable = false) private String action;
    @Column(nullable = false, updatable = false) private int previousAmount;
    @Column(nullable = false, updatable = false) private int newAmount;
    @Column(nullable = false, updatable = false, length = 500) private String reason;
    @Column(nullable = false, updatable = false) private LocalDateTime createdAt;
}
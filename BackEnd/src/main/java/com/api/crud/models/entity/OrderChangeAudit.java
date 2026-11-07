package com.api.crud.models.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
@Entity @Table(name = "order_change_audit")
@Getter @Setter
public class OrderChangeAudit {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, updatable = false) private Long orderId;
    @Column(nullable = false, updatable = false) private Long actorUserId;
    @Column(nullable = false, updatable = false) private String action;
    @Column(updatable = false) private String previousValue;
    @Column(updatable = false) private String newValue;
    @Column(nullable = false, updatable = false, length = 500) private String reason;
    @Column(nullable = false, updatable = false) private LocalDateTime createdAt;
}
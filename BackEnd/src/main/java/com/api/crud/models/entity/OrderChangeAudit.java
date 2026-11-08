package com.api.crud.models.entity;

import jakarta.persistence.*;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "order_change_audit")
public class OrderChangeAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Getter
    @Setter
    private Long id;

    @Column(nullable = false, updatable = false)
    @Getter
    @Setter
    private Long orderId;

    @Column(updatable = false)
    @Getter
    @Setter
    private Long actorUserId;

    @Column(nullable = false, updatable = false)
    @Getter
    @Setter
    private String action;

    @Column(updatable = false)
    @Getter
    @Setter
    private String previousValue;

    @Column(updatable = false)
    @Getter
    @Setter
    private String newValue;

    @Column(nullable = false, updatable = false, length = 500)
    @Getter
    @Setter
    private String reason;

    @Column(nullable = false, updatable = false)
    @Getter
    @Setter
    private LocalDateTime createdAt;
}

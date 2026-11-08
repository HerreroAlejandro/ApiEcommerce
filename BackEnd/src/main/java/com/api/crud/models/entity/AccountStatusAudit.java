package com.api.crud.models.entity;

import com.api.crud.models.enums.AccountStatus;

import jakarta.persistence.*;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "account_status_audit")
public class AccountStatusAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Getter
    @Setter
    private Long id;

    @Column(nullable = false, updatable = false)
    @Getter
    @Setter
    private Long userId;

    @Column(nullable = false, updatable = false)
    @Getter
    @Setter
    private Long actorUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    @Getter
    @Setter
    private AccountStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    @Getter
    @Setter
    private AccountStatus newStatus;

    @Column(nullable = false, updatable = false)
    @Getter
    @Setter
    private String source;

    @Column(nullable = false, updatable = false, length = 500)
    @Getter
    @Setter
    private String reason;

    @Column(nullable = false, updatable = false)
    @Getter
    @Setter
    private LocalDateTime createdAt;
}

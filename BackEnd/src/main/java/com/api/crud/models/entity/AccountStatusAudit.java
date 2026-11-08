package com.api.crud.models.entity;
import com.api.crud.models.enums.AccountStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
@Entity @Table(name = "account_status_audit")
@Getter @Setter
public class AccountStatusAudit {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, updatable = false) private Long userId;
    @Column(nullable = false, updatable = false) private Long actorUserId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, updatable = false) private AccountStatus previousStatus;
    @Enumerated(EnumType.STRING) @Column(nullable = false, updatable = false) private AccountStatus newStatus;
    @Column(nullable = false, updatable = false) private String source;
    @Column(nullable = false, updatable = false, length = 500) private String reason;
    @Column(nullable = false, updatable = false) private LocalDateTime createdAt;
}
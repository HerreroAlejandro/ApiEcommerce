package com.api.crud.models.entity;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
public class AccountRecoveryToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Getter @Setter
    private Long id;

    @Getter @Setter
    private String token;

    @Getter @Setter
    private LocalDateTime createdAt;

    @Getter @Setter
    private LocalDateTime expirationDate;

    @Getter @Setter
    private boolean used;

    @ManyToOne
    @JoinColumn(name = "user_id")
    @Getter @Setter
    private UserModel user;

    public AccountRecoveryToken(Long id, String token, LocalDateTime createdAt, LocalDateTime expirationDate, boolean used, UserModel user) {
        this.id = id;
        this.token = token;
        this.createdAt = createdAt;
        this.expirationDate = expirationDate;
        this.used = used;
        this.user = user;
    }

    public AccountRecoveryToken(){}
}

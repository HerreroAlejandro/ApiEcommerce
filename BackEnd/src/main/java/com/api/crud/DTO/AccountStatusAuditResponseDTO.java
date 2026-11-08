package com.api.crud.DTO;

import com.api.crud.models.enums.AccountStatus;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

public class AccountStatusAuditResponseDTO {

    @Getter
    @Setter
    private Long id;

    @Getter
    @Setter
    private Long userId;

    @Getter
    @Setter
    private Long actorUserId;

    @Getter
    @Setter
    private AccountStatus previousStatus;

    @Getter
    @Setter
    private AccountStatus newStatus;

    @Getter
    @Setter
    private String source;

    @Getter
    @Setter
    private String reason;

    @Getter
    @Setter
    private LocalDateTime createdAt;
}

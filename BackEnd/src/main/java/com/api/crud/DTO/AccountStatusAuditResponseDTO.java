package com.api.crud.DTO;
import com.api.crud.models.enums.AccountStatus;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
@Getter @Setter
public class AccountStatusAuditResponseDTO {
    private Long id;
    private Long userId;
    private Long actorUserId;
    private AccountStatus previousStatus;
    private AccountStatus newStatus;
    private String source;
    private String reason;
    private LocalDateTime createdAt;
}
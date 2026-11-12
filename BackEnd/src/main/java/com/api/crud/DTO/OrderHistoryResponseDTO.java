package com.api.crud.DTO;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class OrderHistoryResponseDTO {
    private Long id;
    private Long actorUserId;
    private String action;
    private String previousValue;
    private String newValue;
    private String reason;
    private LocalDateTime createdAt;
}

package com.api.crud.DTO;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class CartHistoryResponseDTO {
    private Long id;
    private Long actorUserId;
    private Long productId;
    private String action;
    private int previousAmount;
    private int newAmount;
    private String reason;
    private LocalDateTime createdAt;
}

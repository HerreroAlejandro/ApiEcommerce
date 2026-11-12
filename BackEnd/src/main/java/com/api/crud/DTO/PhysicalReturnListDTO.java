package com.api.crud.DTO;

import com.api.crud.models.enums.PhysicalReturnStatus;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class PhysicalReturnListDTO {
    private Long id;
    private Long orderId;
    private PhysicalReturnStatus status;
    private LocalDateTime requestedAt;
    private LocalDateTime resolvedAt;
}

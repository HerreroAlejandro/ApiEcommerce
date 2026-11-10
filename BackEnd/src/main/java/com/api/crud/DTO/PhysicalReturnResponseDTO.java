package com.api.crud.DTO;

import com.api.crud.models.enums.PhysicalReturnStatus;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
public class PhysicalReturnResponseDTO {
    private Long id;
    private Long orderId;
    private PhysicalReturnStatus status;
    private String reason;
    private Long requestedBy;
    private LocalDateTime requestedAt;
    private Long resolvedBy;
    private LocalDateTime resolvedAt;
    private String resolutionReason;
    private List<PhysicalReturnItemResponseDTO> items;
}

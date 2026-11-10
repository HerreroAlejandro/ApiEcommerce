package com.api.crud.DTO;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PhysicalReturnItemResponseDTO {
    private Long orderItemId;
    private int receivedQuantity;
    private int restockedQuantity;
}

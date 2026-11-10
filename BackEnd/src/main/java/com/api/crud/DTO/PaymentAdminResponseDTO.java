package com.api.crud.DTO;

import lombok.Getter;
import lombok.Setter;

public class PaymentAdminResponseDTO extends PaymentResponseDTO {

    @Getter
    @Setter
    private Long orderId;
}

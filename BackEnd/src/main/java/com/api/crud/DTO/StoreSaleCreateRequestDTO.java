package com.api.crud.DTO;

import com.api.crud.models.enums.DeliveryMethod;
import com.api.crud.models.enums.StorePaymentMethod;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class StoreSaleCreateRequestDTO {

    @NotNull
    private UUID reference;

    @NotEmpty
    @Size(max = 100)
    @Valid
    private List<@NotNull StoreSaleItemRequestDTO> items;

    @NotNull
    private DeliveryMethod deliveryMethod;

    private boolean deliveredImmediately;

    @Size(min = 2, max = 100)
    @Pattern(regexp = "(?s).*\\S.*")
    private String customerName;

    @Pattern(regexp = "(?=.*[0-9])[+0-9() .-]{8,25}")
    private String customerPhone;

    @Valid
    private ShippingAddressDTO shippingAddress;

    @NotNull
    private StorePaymentMethod paymentMethod;

    @NotNull
    @DecimalMin(value = "0.00", inclusive = false)
    @Digits(integer = 8, fraction = 2)
    private BigDecimal amountPaid;

    @Size(max = 100)
    @Pattern(regexp = "(?s).*\\S.*")
    private String paymentReference;

    @NotBlank
    @Size(max = 500)
    private String reason;
}

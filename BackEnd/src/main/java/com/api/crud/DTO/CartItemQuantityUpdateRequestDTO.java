package com.api.crud.DTO;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
@Getter @Setter
public class CartItemQuantityUpdateRequestDTO {
    @NotNull @Positive private Integer amount;
    @NotBlank @Size(max = 500) private String reason;
}
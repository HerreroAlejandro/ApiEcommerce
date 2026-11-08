package com.api.crud.DTO;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
@Getter @Setter
public class AdminCartItemAddRequestDTO {
    @NotNull @Positive private Long userId;
    @NotNull @Positive private Long productId;
    @NotNull @Positive @Max(99999999) private Integer amount;
    @NotBlank @Size(max = 500) private String reason;
}
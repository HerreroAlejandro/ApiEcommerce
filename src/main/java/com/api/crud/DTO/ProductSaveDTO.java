package com.api.crud.DTO;

import com.api.crud.models.enums.ProductType;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProductSaveDTO {

    @Getter @Setter
    private Long idProduct;

    @Getter @Setter
    @NotBlank(message = "Product name cannot be blank")
    @Size(min = 2, max = 30, message = "Product name must be between 2 and 30 characters")
    private String nameProduct;

    @Getter @Setter
    @NotNull(message = "Product price cannot be null")
    @DecimalMin(value = "0.0", inclusive = false, message = "Product price must be greater than 0")
    @Digits(integer = 8, fraction = 2)
    private BigDecimal priceProduct;

    @Getter @Setter
    @Size(max = 200, message = "Description cannot exceed 200 characters")
    private String description;

    @Getter @Setter
    @Size(max = 200, message = "Image URL cannot exceed 200 characters")
    private String imageUrl;

    @Getter @Setter
    @NotBlank(message = "Product category cannot be blank")
    @Size(max = 50, message = "Product category cannot exceed 50 characters")
    private String category;

    @Getter @Setter
    @NotNull(message = "Product type cannot be null")
    private ProductType type;

    @Getter @Setter
    @Size(max = 200, message = "Download link cannot exceed 200 characters")
    private String downloadLink;

    @Getter @Setter
    @Size(max = 50, message = "License cannot exceed 50 characters")
    private String license;

    @Getter @Setter
    @Min(value = 0, message = "Stock cannot be negative")
    private Integer stockProduct;

    @Getter @Setter
    private boolean active;

    public ProductSaveDTO(String nameProduct, BigDecimal priceProduct, String description, String imageUrl, String category, ProductType type, String downloadLink, String license) {
        this.nameProduct = nameProduct;
        this.priceProduct = priceProduct;
        this.description = description;
        this.imageUrl = imageUrl;
        this.category = category;
        this.type = type;
        this.downloadLink = downloadLink;
        this.license = license;
    }

    public ProductSaveDTO(String nameProduct, BigDecimal priceProduct, String description, String imageUrl, String category, ProductType type, Integer stockProduct) {
        this.nameProduct = nameProduct;
        this.priceProduct = priceProduct;
        this.description = description;
        this.imageUrl = imageUrl;
        this.category = category;
        this.type = type;
        this.stockProduct = stockProduct;
    }

    public ProductSaveDTO(){}

}

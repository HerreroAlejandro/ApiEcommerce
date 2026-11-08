package com.api.crud.DTO;

import com.api.crud.validation.HttpUrl;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

public class ProductUpdateDTO {

    @Getter @Setter
    @Size(min = 2, max = 30, message = "Product name must be between 2 and 30 characters")
    @Pattern(regexp = ".*\\S.*", message = "Product name cannot be blank")
    private String nameProduct;

    @Getter @Setter
    @Size(max = 200, message = "Description cannot exceed 200 characters")
    private String description;

    @Getter @Setter
    @Size(max = 50, message = "Product category cannot exceed 50 characters")
    @Pattern(regexp = ".*\\S.*", message = "Product category cannot be blank")
    private String category;

    @Getter @Setter
    @Size(max = 200, message = "Image URL cannot exceed 200 characters")
    @HttpUrl(allowBlank = true)
    private String imageUrl;

    @Getter @Setter
    @Size(max = 200, message = "Download link cannot exceed 200 characters")
    @HttpUrl
    private String downloadLink;

    @Getter @Setter
    @Size(max = 50, message = "License cannot exceed 50 characters")
    private String license;

    public ProductUpdateDTO() {}
    @JsonIgnore
    @AssertTrue(message = "Debe indicar al menos un campo del producto para actualizar")
    public boolean isUpdateProvided() {
        return nameProduct != null || description != null || category != null || imageUrl != null
                || downloadLink != null || license != null;
    }
}

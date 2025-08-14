package com.api.crud.DTO;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProductDigitalDTO {

    @Getter @Setter
    private Long idProduct;

    @Getter @Setter
    private String nameProduct;

    @Getter @Setter
    private BigDecimal priceProduct;

    @Getter @Setter
    private String description;

    @Getter @Setter
    private String imageUrl;

    @Getter @Setter
    private String category;

    @Getter @Setter
    private String downloadLink;

    @Getter @Setter
    private String license;

    public ProductDigitalDTO(Long idProduct, String nameProduct, BigDecimal priceProduct, String description, String imageUrl, String category, String downloadLink, String license) {
        this.idProduct = idProduct;
        this.nameProduct = nameProduct;
        this.priceProduct = priceProduct;
        this.description = description;
        this.imageUrl = imageUrl;
        this.category = category;
        this.downloadLink = downloadLink;
        this.license = license;
    }

    public ProductDigitalDTO(){}

}

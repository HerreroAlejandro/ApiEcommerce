package com.api.crud.DTO;


import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProductPhysicalDTO {

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
    private Integer stockProduct;

    @Getter @Setter
    private String shippingAddress;

    public ProductPhysicalDTO(Long idProduct, String nameProduct, BigDecimal priceProduct, String description, String imageUrl, String category, int stockProduct, String shippingAddress) {
        this.idProduct = idProduct;
        this.nameProduct = nameProduct;
        this.priceProduct = priceProduct;
        this.description = description;
        this.imageUrl = imageUrl;
        this.category = category;
        this.stockProduct = stockProduct;
        this.shippingAddress = shippingAddress;
    }

    public ProductPhysicalDTO(){}

}

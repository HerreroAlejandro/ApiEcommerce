package com.api.crud.DTO;

import com.api.crud.models.enums.ProductType;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

public class ProductAdminCatalogDTO {

    @Getter @Setter
    private Long idProduct;

    @Getter @Setter
    private String nameProduct;

    @Getter @Setter
    private BigDecimal priceProduct;

    @Getter @Setter
    private String category;

    @Getter @Setter
    private ProductType type;

    @Getter @Setter
    private boolean active;

    @Getter @Setter
    private Integer stockProduct;
}

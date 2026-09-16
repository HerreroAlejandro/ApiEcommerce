package com.api.crud.models.entity;

import com.api.crud.models.enums.ProductType;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@Entity
@Table(name = "productphysical")
@DiscriminatorValue("PHYSICAL")
public class ProductPhysical extends Product{

    @Column(name = "stockProduct")
    @Getter @Setter
    @NotNull(message = "Stock cannot be null")
    @Min(value = 0, message = "Stock cannot be negative")
    private Integer stockProduct;

    public ProductPhysical(String nameProduct, BigDecimal priceProduct, String description, String imageUrl, String category, ProductType type, int stockProduct) {
        super(nameProduct, priceProduct, description, imageUrl, category, type);
        this.stockProduct = stockProduct;
    }

    public ProductPhysical(){}
}

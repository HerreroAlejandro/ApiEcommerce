package com.api.crud.models.entity;

import jakarta.persistence.*;
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
    private Integer stockProduct;

    @Getter @Setter
    @Column(name = "shippingAddress")
    @Size (min =0, max =200)
    private String shippingAddress;


    public ProductPhysical(Long idProduct, String nameProduct, BigDecimal priceProduct, String description, String imageUrl, String category, int stockProduct, String shippingAddress) {
        super(idProduct, nameProduct, priceProduct, description, imageUrl, category);
        this.stockProduct = stockProduct;
        this.shippingAddress = null;
    }

    public ProductPhysical(String nameProduct, BigDecimal priceProduct, String description, String imageUrl, String category, int stockProduct, String shippingAddress) {
        super(nameProduct, priceProduct, description, imageUrl, category);
        this.stockProduct = stockProduct;
        this.shippingAddress = shippingAddress;
    }

    public ProductPhysical(){}
}

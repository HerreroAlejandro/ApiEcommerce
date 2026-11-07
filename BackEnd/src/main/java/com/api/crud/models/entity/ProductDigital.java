package com.api.crud.models.entity;

import com.api.crud.models.enums.ProductType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@Entity
@Table(name = "productdigital")
@DiscriminatorValue("DIGITAL")
public class ProductDigital extends Product{

    @Column(name = "downloadLink")
    @Getter @Setter
    @NotBlank(message = "Download link cannot be blank")
    @Size(max = 200)
    private String downloadLink;

    @Column(name = "license")
    @Getter @Setter
    @Size (max =50)
    private String license;

    public ProductDigital(String nameProduct, BigDecimal priceProduct, String description, String imageUrl, String category, ProductType type, String downloadLink, String license) {
        super(nameProduct, priceProduct, description, imageUrl, category, type);
        this.downloadLink = downloadLink;
        this.license = license;
    }

    public ProductDigital(){}
}

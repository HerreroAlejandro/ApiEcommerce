package com.api.crud.models.entity;

import com.api.crud.models.enums.ProductType;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.util.List;

@Entity
@Table(name = "products")
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "product_type")
public abstract class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Getter @Setter
    private Long idProduct;

    @Column(name = "nameProduct", unique = true)
    @Getter @Setter
    @NotBlank(message = "Product name cannot be blank")
    @Size(min = 2, max = 30, message = "Product name must contain between 2 and 30 characters")
    private String nameProduct;

    @Column(name = "priceProduct")
    @Getter @Setter
    @NotNull(message = "Product price cannot be null")
    @DecimalMin(value = "0.0", inclusive = false, message = "Product price must be greater than 0")
    @Digits(integer = 8, fraction = 2)
    private BigDecimal priceProduct;

    @Column(name = "description")
    @Size(max = 200)
    @Getter @Setter
    private String description;

    @Column(name = "imageUrl")
    @Getter @Setter
    @Size(max = 200)
    private String imageUrl;

    @Column(name = "category")
    @Getter @Setter
    @NotBlank(message = "Product category cannot be blank")
    @Size(max = 50)
    private String category;

    @Getter @Setter
    @NotNull(message = "Product type cannot be null")
    @Enumerated(EnumType.STRING)
    @Column(name = "type")
    private ProductType type;

    @Column(name = "active")
    @Getter @Setter
    private boolean active = true;

    @OneToMany(mappedBy = "productOrderItem")
    @Getter @Setter
    private List<OrderItem> orderItems;

    public Product(String nameProduct, BigDecimal priceProduct, String description, String imageUrl, String category, ProductType type) {
        this.nameProduct = nameProduct;
        this.priceProduct = priceProduct;
        this.description = description;
        this.imageUrl = imageUrl;
        this.category = category;
        this.type = type;
    }

    public Product(){}

}

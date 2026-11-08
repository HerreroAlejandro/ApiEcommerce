package com.api.crud.controllers;

import jakarta.validation.constraints.*;
import jakarta.validation.Valid;

import com.api.crud.DTO.*;
import com.api.crud.models.enums.ProductType;
import com.api.crud.services.ProductService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.Optional;

@RestController
@RequestMapping("/products")

public class ProductController {

    @Autowired
    private ProductService productService;

    private static final Logger logger = LoggerFactory.getLogger(ProductController.class);

    @GetMapping(path = "/ShowProducts")
    public ResponseEntity<Page<ProductCatalogDTO>> showProducts(
            @RequestParam(required = false) @Size(max = 30) String name,
            @RequestParam(required = false) @Size(max = 50) String category,
            @RequestParam(required = false) ProductType type,
            @RequestParam(required = false) @DecimalMin("0.0") @Digits(integer = 8, fraction = 2) BigDecimal minPrice,
            @RequestParam(required = false) @DecimalMin("0.0") @Digits(integer = 8, fraction = 2) BigDecimal maxPrice,
            @RequestParam(required = false) Boolean inStock,
            Pageable pageable) {

        logger.info("Starting to fetch products - name: {}, category: {}, type: {}, minPrice: {}, maxPrice: {}, inStock: {}, page: {}, size: {}",
                name, category, type, minPrice, maxPrice, inStock, pageable.getPageNumber(), pageable.getPageSize());

        ResponseEntity<Page<ProductCatalogDTO>> response;

        Page<ProductCatalogDTO> products = productService.showProducts(name, category, type, minPrice, maxPrice, inStock, pageable);

        logger.info("Successfully fetched {} products.", products.getTotalElements());

        response = ResponseEntity.ok(products);

        return response;
    }

    @GetMapping(path = "/GetProductById/{id}")
    public ResponseEntity<ProductDetailDTO> getProductDetailById(@PathVariable @Positive Long id) {
        logger.info("received request to find product with id {}", id);

        ResponseEntity<ProductDetailDTO> response;

        Optional<ProductDetailDTO> product = productService.getProductDetailById(id);

        if (product.isPresent()) {
            logger.info("Product with ID: {} found successfully.", id);
            response = ResponseEntity.ok(product.get());
        } else {
            logger.info("Product with ID: {} not found.", id);
            response = ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        return response;
    }

}

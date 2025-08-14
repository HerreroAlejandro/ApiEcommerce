package com.api.crud.controllers;

import com.api.crud.DTO.ProductDTO;
import com.api.crud.services.ProductService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/products")

public class ProductController {

    @Autowired
    private ProductService productService;

    private static final Logger logger = LoggerFactory.getLogger(ProductController.class);

    @GetMapping(path = "/ShowProducts")
    public ResponseEntity<List<ProductDTO>> showProducts() {
        logger.info("Starting to fetch products.");
        ResponseEntity<List<ProductDTO>> response;
        List<ProductDTO> products = productService.showProducts();
        if (products.isEmpty()) {
            logger.info("No products found");
            response = ResponseEntity.status(HttpStatus.NOT_FOUND).body(Collections.emptyList());
        }else{
        logger.info("Successfully found {} products.", products.size());
        response = ResponseEntity.ok(products);
    }
        return response;
}

    @GetMapping(path = "/FindProductById/{id}")
    public ResponseEntity<ProductDTO> findProductById(@PathVariable Long id) {
        logger.info("received request to find product with id {}", id);
        ResponseEntity<ProductDTO> response;

        Optional<ProductDTO> product = productService.findProductById(id);
        if (product.isPresent()) {
            logger.info("Product with ID: {} found successfully.", id);
            response = ResponseEntity.ok(product.get());
        } else {
            logger.info("Product with ID: {} not found.", id);
            response = ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return response; // Único return
    }

    @GetMapping(path = "/FindProductByName/{nameProduct}")
    public ResponseEntity<ProductDTO> findProductByName(@PathVariable String nameProduct) {
        logger.info("received request to find product with name {}", nameProduct);
        ResponseEntity<ProductDTO> response;

        Optional<ProductDTO> product = productService.findProductByName(nameProduct);
        if (product.isPresent()) {
            logger.info("Product with name: {} found successfully.", nameProduct);
            response = ResponseEntity.ok(product.get());
        } else {
            logger.info("Product with name: {} not found.", nameProduct);
            response = ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return response;
    }

        @PostMapping (path = "/SaveProduct")
        public ResponseEntity<String> saveProduct(@RequestBody ProductDTO productDTO) {
            logger.info("Received request to save product {}" , productDTO.getNameProduct());
        ResponseEntity<String> response;
        try{
            if (productDTO.getIdProduct() !=null && productService.findProductById(productDTO.getIdProduct()).isPresent()){
                logger.warn("The Product '{}' already exists.", productDTO.getNameProduct());
                response = ResponseEntity.status(HttpStatus.BAD_REQUEST).body("The product already exists");
            }else{
                productService.saveProduct(productDTO);
                response = ResponseEntity.status(HttpStatus.CREATED).body("Product created successfully");
                logger.info("Role {} created successfully.", productDTO.getNameProduct());
            }
        }
        catch (DataIntegrityViolationException e) {
            logger.error("DataIntegrityViolationException occurred while saving product '{}': {}", productDTO.getNameProduct(), e.getMessage());
            response = ResponseEntity.status(HttpStatus.CONFLICT).body("Duplicate product name detected");
        } catch (Exception e) {
            logger.error("Unexpected error occurred while saving product '{}': {}", productDTO.getNameProduct(), e.getMessage());
            response = ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to create product: " + e.getMessage());
        }
            return response;
        }

        @DeleteMapping(path = "/DeleteProduct/{id}")
        public ResponseEntity<Void> deleteProduct(@PathVariable("id") Long id){
        logger.info("received request to delete product with id {}", id);
        ResponseEntity<Void> response;
        boolean deleted = productService.deleteProduct(id);
        if (deleted){
            logger.info("The product with id {} deleted", id);
            response = ResponseEntity.status(HttpStatus.NO_CONTENT).build();
        }else{
            logger.info("The product with id {} cannot be deleted", id);
            response = ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return response;
        }

    }


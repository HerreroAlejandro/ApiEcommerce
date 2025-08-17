package com.api.crud.controllers;

import com.api.crud.DTO.ProductDTO;
import com.api.crud.models.entity.Product;
import com.api.crud.services.ProductService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
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

    // 1) Actualizar stock por nombre
    @PutMapping("/UpdateStock/{nameProduct}")
    public ResponseEntity<String> updateProductStock(
            @PathVariable String nameProduct,
            @RequestParam Integer newStockProduct) {
        logger.info("Received request to update stock for product {}", nameProduct);

        boolean updated = productService.updateProductStock(nameProduct, newStockProduct);
        if (updated) {
            logger.info("Stock for product {} updated successfully to {}", nameProduct, newStockProduct);
            return ResponseEntity.ok("Stock updated successfully");
        } else {
            logger.warn("Product {} not found, stock not updated", nameProduct);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Product not found");
        }
    }

    // 2) Actualizar precio por nombre
    @PutMapping("/UpdatePrice/{nameProduct}")
    public ResponseEntity<String> updateProductPrice(
            @PathVariable String nameProduct,
            @RequestParam BigDecimal newPriceProduct) {
        logger.info("Received request to update price for product {}", nameProduct);

        boolean updated = productService.updateProductPrice(nameProduct, newPriceProduct);
        if (updated) {
            logger.info("Price for product {} updated successfully to {}", nameProduct, newPriceProduct);
            return ResponseEntity.ok("Price updated successfully");
        } else {
            logger.warn("Product {} not found, price not updated", nameProduct);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Product not found");
        }
    }

    // 3) Buscar productos por categoría
    @GetMapping("/FindByCategory/{category}")
    public ResponseEntity<List<ProductDTO>> findProductsByCategory(@PathVariable String category) {
        logger.info("Received request to find products by category {}", category);

        List<ProductDTO> products = productService.findProductsByCategory(category);
        if (products.isEmpty()) {
            logger.info("No products found in category {}", category);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Collections.emptyList());
        }
        logger.info("Found {} products in category {}", products.size(), category);
        return ResponseEntity.ok(products);
    }

    // 4) Buscar productos por rango de precio
    @GetMapping("/FindByPriceRange")
    public ResponseEntity<List<ProductDTO>> findProductsByPriceRange(
            @RequestParam BigDecimal min,
            @RequestParam BigDecimal max) {
        logger.info("Received request to find products with price between {} and {}", min, max);

        List<ProductDTO> products = productService.findProductsByPriceRange(min, max);
        if (products.isEmpty()) {
            logger.info("No products found in price range {} - {}", min, max);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Collections.emptyList());
        }
        logger.info("Found {} products in price range {} - {}", products.size(), min, max);
        return ResponseEntity.ok(products);
    }

    // 5) Buscar productos en stock
    @GetMapping("/FindInStock")
    public ResponseEntity<List<ProductDTO>> findProductsInStock() {
        logger.info("Received request to find products in stock");

        List<ProductDTO> products = productService.findProductsInStock();
        if (products.isEmpty()) {
            logger.info("No products in stock found");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Collections.emptyList());
        }
        logger.info("Found {} products in stock", products.size());
        return ResponseEntity.ok(products);
    }

    // 6) Listar productos ordenados por precio ascendente
    @GetMapping("/SortedByPriceAsc")
    public ResponseEntity<List<Product>> findProductsSortedByPriceAsc() {
        logger.info("Received request to list products sorted by price asc");

        List<Product> products = productService.findProductsSortedByPriceAsc();
        if (products.isEmpty()) {
            logger.info("No products found to sort by price");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Collections.emptyList());
        }
        logger.info("Found {} products sorted by price", products.size());
        return ResponseEntity.ok(products);
    }

    // 1) Productos ordenados por precio descendente
    @GetMapping("/SortedByPriceDesc")
    public ResponseEntity<List<Product>> findProductsSortedByPriceDesc() {
        logger.info("Received request to list products sorted by price desc");

        List<Product> products = productService.findProductsSortedByPriceDesc();
        if (products.isEmpty()) {
            logger.info("No products found to sort by price desc");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Collections.emptyList());
        }
        logger.info("Found {} products sorted by price desc", products.size());
        return ResponseEntity.ok(products);
    }

    // 2) Productos ordenados por nombre
    @GetMapping("/SortedByName")
    public ResponseEntity<List<Product>> findProductsSortedByName() {
        logger.info("Received request to list products sorted by name");

        List<Product> products = productService.findProductsSortedByName();
        if (products.isEmpty()) {
            logger.info("No products found to sort by name");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Collections.emptyList());
        }
        logger.info("Found {} products sorted by name", products.size());
        return ResponseEntity.ok(products);
    }

    // 3) Deshabilitar producto (soft delete)
    @PutMapping("/Disable/{id}")
    public ResponseEntity<String> disableProduct(@PathVariable Long id) {
        logger.info("Received request to disable product with id {}", id);

        boolean disabled = productService.disableProduct(id);
        if (disabled) {
            logger.info("Product with id {} disabled successfully", id);
            return ResponseEntity.ok("Product disabled successfully");
        } else {
            logger.warn("Product with id {} not found or could not be disabled", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Product not found or could not be disabled");
        }
    }

    // 4) Actualizar un producto
    @PutMapping("/Update")
    public ResponseEntity<String> updateProduct(@RequestBody ProductDTO productDTO) {
        logger.info("Received request to update product with id {}", productDTO.getIdProduct());

        boolean updated = productService.updateProduct(productDTO);
        if (updated) {
            logger.info("Product with id {} updated successfully", productDTO.getIdProduct());
            return ResponseEntity.ok("Product updated successfully");
        } else {
            logger.warn("Product with id {} could not be updated", productDTO.getIdProduct());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Product not found or could not be updated");
        }
    }

}


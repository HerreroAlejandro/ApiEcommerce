package com.api.crud.controllers;

import com.api.crud.DTO.*;
import com.api.crud.models.enums.ProductType;
import com.api.crud.services.ProductService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
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

    @PostMapping(path = "/SaveProduct")
    public ResponseEntity<String> saveProduct(@Valid @RequestBody ProductSaveDTO productSaveDTO) {
        logger.info("Received request to save product {}", productSaveDTO.getNameProduct());

        ResponseEntity<String> response;

        try {
            productService.saveProduct(productSaveDTO);
            response = ResponseEntity.status(HttpStatus.CREATED).body("Product created successfully");

            logger.info("Product '{}' created successfully.", productSaveDTO.getNameProduct());

        } catch (DataIntegrityViolationException e) {
            logger.error("DataIntegrityViolationException occurred while saving product '{}': {}", productSaveDTO.getNameProduct(), e.getMessage());

            response = ResponseEntity.status(HttpStatus.CONFLICT).body("Duplicate product name detected");

        } catch (Exception e) {
            logger.error("Unexpected error occurred while saving product '{}': {}", productSaveDTO.getNameProduct(), e.getMessage());

            response = ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to create product: " + e.getMessage());
        }
        return response;
    }

    @GetMapping(path = "/ShowProducts")
    public ResponseEntity<Page<ProductCatalogDTO>> showProducts(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) ProductType type,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
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

    @GetMapping(path = "/AdminShowProducts")
    public ResponseEntity<Page<ProductAdminCatalogDTO>> adminShowProducts(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) ProductType type,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) Boolean inStock,
            Pageable pageable) {

        logger.info("Starting to fetch products for admin - name: {}, category: {}, type: {}, minPrice: {}, maxPrice: {}, active: {}, inStock: {}, page: {}, size: {}",
                name, category, type, minPrice, maxPrice, active, inStock, pageable.getPageNumber(), pageable.getPageSize());

        ResponseEntity<Page<ProductAdminCatalogDTO>> response;

        Page<ProductAdminCatalogDTO> products = productService.adminShowProducts(name, category, type, minPrice, maxPrice, active, inStock, pageable);

        logger.info("Successfully fetched {} products for admin.", products.getTotalElements());

        response = ResponseEntity.ok(products);

        return response;
    }

    @GetMapping(path = "/FindProductById/{id}")
    public ResponseEntity<ProductDetailDTO> findProductDetailById(@PathVariable Long id) {
        logger.info("received request to find product with id {}", id);

        ResponseEntity<ProductDetailDTO> response;

        Optional<ProductDetailDTO> product = productService.findProductDetailById(id);

        if (product.isPresent()) {
            logger.info("Product with ID: {} found successfully.", id);
            response = ResponseEntity.ok(product.get());
        } else {
            logger.info("Product with ID: {} not found.", id);
            response = ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        return response;
    }

    @GetMapping("/AdminFindProductById/{id}")
    public ResponseEntity<ProductAdminDetailDTO> findProductAdminDetailById(
            @PathVariable Long id) {

        logger.info("Received request to find product for admin with id {}", id);

        ResponseEntity<ProductAdminDetailDTO> response;

        Optional<ProductAdminDetailDTO> product =
                productService.findProductAdminDetailById(id);

        if (product.isPresent()) {
            logger.info("Product with ID {} found successfully.", id);
            response = ResponseEntity.ok(product.get());
        } else {
            logger.info("Product with ID {} not found.", id);
            response = ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return response;
    }
/*
    @GetMapping(path = "/FindProductsByName/{nameProduct}")
    public ResponseEntity<Page<ProductCatalogDTO>> findProductsByName(@PathVariable String nameProduct, Pageable pageable) {

        logger.info("Searching products by name: '{}' - page: {}, size: {}", nameProduct, pageable.getPageNumber(), pageable.getPageSize());

        ResponseEntity<Page<ProductCatalogDTO>> response;

        Page<ProductCatalogDTO> products = productService.findProductsByName(nameProduct, pageable);

        response = ResponseEntity.ok(products);

        return response;
    }

    @GetMapping(path = "/FindProductsByNameAdmin/{nameProduct}")
    public ResponseEntity<Page<ProductAdminDetailDTO>> findProductsByNameAdmin(@PathVariable String nameProduct,
            @RequestParam(required = false) Boolean active, Pageable pageable) {

        logger.info("Searching products by name for admin: '{}' - active: {}, page: {}, size: {}", nameProduct, active, pageable.getPageNumber(),
                pageable.getPageSize());

        ResponseEntity<Page<ProductAdminDetailDTO>> response;

        Page<ProductAdminDetailDTO> products = productService.findProductsByNameAdmin(nameProduct, active, pageable);

        response = ResponseEntity.ok(products);

        return response;
    }
*/
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

    @PutMapping("/UpdateStock/{idProduct}")
    public ResponseEntity<String> updateProductStock(@PathVariable Long idProduct, @Valid @RequestBody ProductStockUpdateDTO stockDTO) {
        logger.info("Received request to update stock for product ID {}", idProduct);

        ResponseEntity<String> response;
        boolean updated = productService.updateProductStock(idProduct, stockDTO);

        if (updated) {
            logger.info("Stock for product ID {} updated successfully to {}", idProduct, stockDTO.getNewStockProduct());
            response = ResponseEntity.ok("Stock updated successfully");

        } else {
            logger.info("Product ID {} not found or is not a physical product", idProduct);
            response = ResponseEntity.status(HttpStatus.NOT_FOUND).body("Product not found or is not a physical product");
        }
        return response;
    }

    @PutMapping("/UpdatePrice/{idProduct}")
    public ResponseEntity<String> updateProductPrice(@PathVariable Long idProduct, @Valid @RequestBody ProductPriceUpdateDTO priceDTO) {
        logger.info("Received request to update price for product ID {}", idProduct);

        ResponseEntity<String> response;
        boolean updated = productService.updateProductPrice(idProduct, priceDTO);

        if (updated) {
            logger.info("Price for product ID {} updated successfully to {}", idProduct, priceDTO.getNewPriceProduct());
            response = ResponseEntity.ok("Price updated successfully");

        } else {
            logger.info("Product ID {} not found, price not updated", idProduct);
            response = ResponseEntity.status(HttpStatus.NOT_FOUND).body("Product not found");
        }
        return response;
    }

    @PutMapping("/AdjustPrice/{idProduct}")
    public ResponseEntity<String> adjustProductPrice(@PathVariable Long idProduct,
                                                     @Valid @RequestBody ProductPriceAdjustmentDTO adjustmentDTO) {

        logger.info("Received request to adjust price for product ID {} by {}%", idProduct, adjustmentDTO.getPercentage());

        ResponseEntity<String> response;

        boolean updated = productService.adjustProductPrice(idProduct, adjustmentDTO);

        if (updated) {
            logger.info("Price for product ID {} adjusted successfully by {}%", idProduct, adjustmentDTO.getPercentage());
            response = ResponseEntity.ok("Product price adjusted successfully");

        } else {
            logger.info("Product ID {} not found, price not adjusted", idProduct);

            response = ResponseEntity.status(HttpStatus.NOT_FOUND).body("Product not found");
        }
        return response;
    }

    @PutMapping("/AdjustStock/{idProduct}")
    public ResponseEntity<String> adjustProductStock(@PathVariable Long idProduct,
                                                     @Valid @RequestBody ProductStockAdjustmentDTO adjustmentDTO) {
        ResponseEntity<String> response;

        boolean updated = productService.adjustProductStock(idProduct, adjustmentDTO);

        if (updated) {
            response = ResponseEntity.ok("Product stock adjusted successfully");
        } else {
            response = ResponseEntity.status(HttpStatus.NOT_FOUND).body("Product not found or is not a physical product");
        }
        return response;
    }

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

    @PutMapping("/Enable/{id}")
    public ResponseEntity<String> enableProduct(@PathVariable Long id) {
        logger.info("Received request to enable product with id {}", id);

        ResponseEntity<String> response;

        boolean enabled = productService.enableProduct(id);

        if (enabled) {
            logger.info("Product with id {} enabled successfully", id);
            response = ResponseEntity.ok("Product enabled successfully");
        } else {
            logger.info("Product with id {} not found or could not be enabled", id);
            response = ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body("Product not found or could not be enabled");
        }
        return response;
    }

    @PatchMapping("/UpdateInfo/{idProduct}")
    public ResponseEntity<String> updateProductInfo(@PathVariable Long idProduct, @Valid @RequestBody ProductUpdateDTO productDTO) {

        logger.info("Received request to update product information for product ID {}", idProduct);

        ResponseEntity<String> response;

        boolean updated = productService.updateProductInfo(idProduct, productDTO);

        if (updated) {
            logger.info("Product information for product ID {} updated successfully", idProduct);
            response = ResponseEntity.ok("Product information updated successfully");

        } else {
            logger.info("Product ID {} not found or could not be updated", idProduct);
            response = ResponseEntity.status(HttpStatus.NOT_FOUND).body("Product not found or could not be updated");
        }
        return response;
    }

}


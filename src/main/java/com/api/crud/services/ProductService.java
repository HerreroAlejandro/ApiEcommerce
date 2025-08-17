package com.api.crud.services;

import com.api.crud.DTO.ProductDTO;
import com.api.crud.models.entity.Product;
import com.api.crud.models.entity.ProductDigital;
import com.api.crud.models.entity.ProductPhysical;
import com.api.crud.repositories.ProductDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.modelmapper.ModelMapper;


@Service
public class ProductService {
    @Autowired
    private ProductDao productDao;
    @Autowired
    private ModelMapper modelMapper;

    private static final Logger logger = LoggerFactory.getLogger(ProductService.class);

    public void saveProduct(ProductDTO productDTO) {
        Product product;

        switch (productDTO.getCategory().toUpperCase()) {
            case "DIGITAL":
                product = modelMapper.map(productDTO, ProductDigital.class);
                break;
            case "PHYSICAL":
                product = modelMapper.map(productDTO, ProductPhysical.class);
                break;
            default:
                throw new IllegalArgumentException("Invalid product type: " + productDTO.getCategory());
        }

        product.setActive(true);
        productDao.saveProduct(product);
    }

    public Optional<ProductDTO> findProductByName(String nameProduct) {
        return productDao.findProductByName(nameProduct)
                .map(this::mapProductToDTO);
    }

    public List<ProductDTO> showProducts() {
        logger.info("Starting to process showProducts in service");
        List<Product> products = productDao.showProducts();

        return products.stream()
                .map(this::mapProductToDTO)
                .collect(Collectors.toList());
    }

    public Optional<ProductDTO> findProductById(Long idProduct) {
        return productDao.findProductById(idProduct)
                .map(this::mapProductToDTO);
    }

    public boolean deleteProduct(Long idProduct) {
        logger.info("Starting to process delete Product with id {} in services", idProduct);
        boolean result = productDao.deleteProduct(idProduct);

        if (result) {
            logger.info("Product with id {} deleted", idProduct);
        } else {
            logger.debug("Product with id {} not deleted in services", idProduct);
        }

        return result;
    }

    public boolean updateProductStock(String nameProduct, Integer newStockProduct) {
        logger.info("Updating stock for product {} in service", nameProduct);
        return productDao.updateProductStock(nameProduct, newStockProduct);
    }

    public boolean updateProductPrice(String nameProduct, BigDecimal newPriceProduct) {
        logger.info("Updating price for product {} in service", nameProduct);
        return productDao.updateProductPrice(nameProduct, newPriceProduct);
    }

    public List<ProductDTO> findProductsByCategory(String category) {
        logger.info("Finding products by category {} in service", category);
        return productDao.findProductsByCategory(category)
                .stream()
                .map(this::mapProductToDTO)
                .collect(Collectors.toList());
    }

    public List<ProductDTO> findProductsByPriceRange(BigDecimal min, BigDecimal max) {
        logger.info("Finding products by price range {} - {} in service", min, max);
        return productDao.findProductsByPriceRange(min, max)
                .stream()
                .map(this::mapProductToDTO)
                .collect(Collectors.toList());
    }

    public List<ProductDTO> findProductsInStock() {
        logger.info("Finding products in stock in service");
        return productDao.findProductsInStock()
                .stream()
                .map(this::mapProductToDTO)
                .collect(Collectors.toList());
    }

    private ProductDTO mapProductToDTO(Product product) {
        return modelMapper.map(product, ProductDTO.class);
    }

    public List<Product> findProductsSortedByPriceAsc() {
        return productDao.showProducts()
                .stream()
                .sorted(Comparator.comparing(Product::getPriceProduct))
                .collect(Collectors.toList());
    }

    public List<Product> findProductsSortedByPriceDesc() {
        return productDao.showProducts()
                .stream()
                .sorted(Comparator.comparing(Product::getPriceProduct).reversed())
                .collect(Collectors.toList());
    }

    public List<Product> findProductsSortedByName() {
        return productDao.showProducts()
                .stream()
                .sorted(Comparator.comparing(Product::getNameProduct, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());
    }

    public boolean disableProduct(Long idProduct) {
        logger.info("Disabling product with id {}", idProduct);
        boolean success = false;

        Optional<Product> productOpt = productDao.findProductById(idProduct);

        if (productOpt.isPresent()) {
            Product product = productOpt.get();
            product.setActive(false);

            try {
                productDao.saveProduct(product);
                logger.info("Product with id {} disabled successfully", idProduct);
                success = true;
            } catch (Exception e) {
                logger.error("Error disabling product with id {}: {}", idProduct, e.getMessage());
            }
        } else {
            logger.warn("Product with id {} not found", idProduct);
        }

        return success;
    }

    public boolean updateProduct(ProductDTO productDTO) {
        Product product = modelMapper.map(productDTO, Product.class);
        return productDao.updateProduct(product);
    }

}
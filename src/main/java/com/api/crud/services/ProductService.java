package com.api.crud.services;

import com.api.crud.DTO.*;
import com.api.crud.models.entity.Product;
import com.api.crud.models.entity.ProductDigital;
import com.api.crud.models.entity.ProductPhysical;
import com.api.crud.models.enums.ProductType;
import com.api.crud.repositories.ProductDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.modelmapper.ModelMapper;
import org.springframework.util.StringUtils;


@Service
public class ProductService {
    @Autowired
    private ProductDao productDao;
    @Autowired
    private ModelMapper modelMapper;

    private static final Logger logger = LoggerFactory.getLogger(ProductService.class);

    public void saveProduct(ProductSaveDTO productSaveDTO) {
        Product product;

        switch (productSaveDTO.getType()) {
            case DIGITAL:
                if (!StringUtils.hasText(productSaveDTO.getDownloadLink())) {
                    throw new IllegalArgumentException("Digital product must have a download link");
                }

                if (productSaveDTO.getStockProduct() != null) {
                    throw new IllegalArgumentException("Digital product cannot have stock");
                }
                product = modelMapper.map(productSaveDTO, ProductDigital.class);
                break;

            case PHYSICAL:
                if (StringUtils.hasText(productSaveDTO.getDownloadLink()) || StringUtils.hasText(productSaveDTO.getLicense())) {
                    throw new IllegalArgumentException("Physical product cannot have digital product fields");
                }
                product = modelMapper.map(productSaveDTO, ProductPhysical.class);
                break;

            default:
                throw new IllegalArgumentException("Invalid product type: " + productSaveDTO.getType());
        }

        product.setActive(true);
        productDao.saveProduct(product);
    }

    public Page<ProductCatalogDTO> showProducts(String name, String category, ProductType type, BigDecimal minPrice, BigDecimal maxPrice,
                                                Boolean inStock, Pageable pageable) {

        logger.info(
                "Starting to process showProducts in service - name: {}, category: {}, type: {}, minPrice: {}, maxPrice: {}, inStock: {}, page: {}, size: {}",
                name, category, type, minPrice, maxPrice, inStock, pageable.getPageNumber(), pageable.getPageSize());

        List<Product> products = productDao.showProducts(name, category, type, minPrice, maxPrice, inStock, pageable);

        long totalProducts = productDao.countProducts(name, category, type, minPrice, maxPrice, inStock);

        List<ProductCatalogDTO> catalogProducts = products.stream().map(product -> modelMapper.map(product, ProductCatalogDTO.class))
                        .collect(Collectors.toList());

        return new PageImpl<>(catalogProducts, pageable, totalProducts
        );
    }

    public Page<ProductAdminCatalogDTO> adminShowProducts(String name, String category, ProductType type, BigDecimal minPrice, BigDecimal maxPrice,
            Boolean active, Boolean inStock, Pageable pageable) {

        logger.info("Starting to process adminShowProducts in service - name: {}, category: {}, type: {}, minPrice: {}, maxPrice: {}, active: {}, inStock: {}, page: {}, size: {}",
                name, category, type, minPrice, maxPrice, active, inStock, pageable.getPageNumber(), pageable.getPageSize());

        List<Product> products = productDao.adminShowProducts(name, category, type, minPrice, maxPrice, active, inStock, pageable);

        long totalProducts = productDao.countAdminProducts(name, category, type, minPrice, maxPrice, active, inStock);

        List<ProductAdminCatalogDTO> adminProducts = products.stream()
                        .map(product -> modelMapper.map(product, ProductAdminCatalogDTO.class))
                        .collect(Collectors.toList());

        return new PageImpl<>(adminProducts, pageable, totalProducts);
    }

    public Optional<ProductDetailDTO> findProductDetailById(Long idProduct) {
        return productDao.findProductById(idProduct)
                .map(product -> modelMapper.map(product, ProductDetailDTO.class));
    }

    public Optional<ProductAdminDetailDTO> findProductAdminDetailById(Long idProduct) {
        return productDao.findProductById(idProduct)
                .map(product -> modelMapper.map(product, ProductAdminDetailDTO.class));
    }
/*
    public Page<ProductCatalogDTO> findProductsByName(String nameProduct, Pageable pageable) {

        List<Product> products = productDao.findProductsByName(nameProduct, pageable);
        long totalProducts = productDao.countProductsByName(nameProduct);

        List<ProductCatalogDTO> catalogProducts = products.stream()
                .map(product -> modelMapper.map(product, ProductCatalogDTO.class))
                .collect(Collectors.toList());

        return new PageImpl<>(catalogProducts, pageable, totalProducts);
    }

    public Page<ProductAdminDetailDTO> findProductsByNameAdmin(String nameProduct, Boolean active, Pageable pageable) {

        List<Product> products = productDao.findProductsByNameAdmin(nameProduct, active, pageable);

        long totalProducts = productDao.countProductsByNameAdmin(nameProduct, active);

        List<ProductAdminDetailDTO> adminProducts = products.stream()
                .map(product -> modelMapper.map(product, ProductAdminDetailDTO.class))
                .collect(Collectors.toList());

        return new PageImpl<>(adminProducts, pageable, totalProducts);
    }
*/
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

    public boolean updateProductStock(Long idProduct, ProductStockUpdateDTO stockDTO) {

        logger.info("Updating stock for product ID {} in service", idProduct);

        Optional<Product> product = productDao.findProductById(idProduct);

        if (product.isEmpty()) {
            return false;
        }

        if (!(product.get() instanceof ProductPhysical)) {
            return false;
        }

        ProductPhysical physicalProduct = (ProductPhysical) product.get();
        physicalProduct.setStockProduct(stockDTO.getNewStockProduct());

        return true;
    }

    public boolean updateProductPrice(Long idProduct, ProductPriceUpdateDTO priceDTO) {

        logger.info("Updating price for product ID {} in service", idProduct);

        Optional<Product> product = productDao.findProductById(idProduct);

        if (product.isEmpty()) {
            return false;
        }

        product.get().setPriceProduct(priceDTO.getNewPriceProduct());
        return true;
    }

    public boolean disableProduct(Long idProduct) {
        logger.info("Disabling product with id {}", idProduct);
        boolean success = false;

        Optional<Product> productOpt = productDao.findProductById(idProduct);

        if (productOpt.isPresent()) {
            Product product = productOpt.get();
            product.setActive(false);
            success = true;
        }
        return success;
    }

    public boolean enableProduct(Long idProduct) {
        logger.info("Enabling product with id {}", idProduct);

        boolean success = false;

        Optional<Product> productOpt =
                productDao.findProductById(idProduct);

        if (productOpt.isPresent()) {
            Product product = productOpt.get();
            product.setActive(true);
            success = true;
        }

        return success;
    }

    public boolean updateProductInfo(Long idProduct, ProductUpdateDTO productDTO) {
        logger.info("Updating product information for product ID {}", idProduct);
        boolean updated = false;

        Optional<Product> productOpt = productDao.findProductById(idProduct);

        if (productOpt.isPresent()) {
            Product product = productOpt.get();

            boolean hasChanges =
                    productDTO.getNameProduct() != null ||
                            productDTO.getDescription() != null ||
                            productDTO.getCategory() != null ||
                            productDTO.getImageUrl() != null ||
                            productDTO.getDownloadLink() != null ||
                            productDTO.getLicense() != null;

            if (!hasChanges) {
                throw new IllegalArgumentException("At least one product attribute must be provided");
            }

            boolean hasDigitalFields = productDTO.getDownloadLink() != null || productDTO.getLicense() != null;

            if (hasDigitalFields && !(product instanceof ProductDigital)) {
                throw new IllegalArgumentException("Physical product cannot have digital product fields");
            }

            if (productDTO.getNameProduct() != null) {
                product.setNameProduct(productDTO.getNameProduct());
            }

            if (productDTO.getDescription() != null) {
                product.setDescription(productDTO.getDescription());
            }

            if (productDTO.getCategory() != null) {
                product.setCategory(productDTO.getCategory());
            }

            if (productDTO.getImageUrl() != null) {
                product.setImageUrl(productDTO.getImageUrl());
            }

            if (product instanceof ProductDigital digitalProduct) {

                if (productDTO.getDownloadLink() != null) {
                    digitalProduct.setDownloadLink(productDTO.getDownloadLink());
                }

                if (productDTO.getLicense() != null) {
                    digitalProduct.setLicense(productDTO.getLicense());
                }
            }
            updated = true;
        }
        return updated;
    }

}
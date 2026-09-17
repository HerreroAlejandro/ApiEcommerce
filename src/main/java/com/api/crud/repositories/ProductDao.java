package com.api.crud.repositories;

import com.api.crud.models.entity.Product;
import com.api.crud.models.enums.ProductType;
import org.springframework.data.domain.Pageable;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface ProductDao {

    void saveProduct(Product product);

    List<Product> showProducts(String name, String category, ProductType type, BigDecimal minPrice, BigDecimal maxPrice,
                                      Boolean inStock, Pageable pageable);

    long countProducts(String name, String category, ProductType type, BigDecimal minPrice, BigDecimal maxPrice, Boolean inStock);

    List<Product> adminShowProducts(String name, String category, ProductType type, BigDecimal minPrice, BigDecimal maxPrice,
                                    Boolean active, Boolean inStock, Pageable pageable);

    long countAdminProducts(String name, String category, ProductType type, BigDecimal minPrice, BigDecimal maxPrice, Boolean active,
                            Boolean inStock);

    Optional<Product> findProductById(Long idProduct);

    boolean deleteProduct(Long idProduct);
/*
    List<Product> findProductsByName(String nameProduct, Pageable pageable);

    long countProductsByName(String nameProduct);

    List<Product> findProductsByNameAdmin(String nameProduct, Boolean active, Pageable pageable);

    long countProductsByNameAdmin(String nameProduct, Boolean active);
*/




}

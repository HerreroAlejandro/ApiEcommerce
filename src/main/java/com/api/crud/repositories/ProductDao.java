package com.api.crud.repositories;

import com.api.crud.models.Product;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface ProductDao {

    List<Product> showProducts();

    Optional<Product> findProductById(Long idProduct);

    Optional<Product> findProductByName(String nameProduct);

    void saveProduct(Product product);

    public boolean updateProduct(Product updatedProduct);

    boolean deleteProduct(Long idProduct);

    boolean updateProductStock(String nameProduct, Integer newStockProduct);

    boolean updateProductPrice(String nameProduct, BigDecimal newPriceProduct);

    List<Product> findProductsByCategory(String category);

    List<Product> findProductsByPriceRange(BigDecimal min, BigDecimal max);

    List<Product> findProductsInStock();


}

package com.api.crud.repositories;

import com.api.crud.models.entity.Product;
import com.api.crud.models.entity.ProductPhysical;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;


@Repository
@Transactional
public class ProductDaoImp implements ProductDao{

    @PersistenceContext
    private EntityManager entityManager;

    private static final Logger logger = LoggerFactory.getLogger(ProductDaoImp.class);

    @Override
    public List<Product> showProducts() {
        logger.debug("Executing query to fetch products");
        List<Product> products;
        try{
            String query ="FROM Product p";
            products= entityManager.createQuery(query, Product.class).getResultList();
        } catch(Exception e){
            logger.error("Error while querying product {}:", e.getMessage());
            products= Collections.emptyList();
        }
        return products;
    }

    @Override
    public Optional<Product> findProductById(Long idProduct) {
        logger.debug("Executing query to find product with ID: {}", idProduct);
        Optional<Product> response;

        String jpql = "SELECT p FROM Product p " +
                "LEFT JOIN FETCH ProductDigital pd ON p.idProduct = pd.idProduct " +
                "LEFT JOIN FETCH ProductPhysical pp ON p.idProduct = pp.idProduct " +
                "WHERE p.idProduct = :idProduct";

        try {
            Product product = entityManager.createQuery(jpql, Product.class)
                    .setParameter("idProduct", idProduct)
                    .getSingleResult();
            response = Optional.ofNullable(product);
        } catch (NoResultException e) {
            logger.error("Product with ID {} not found", idProduct);
            response = Optional.empty();
        }
        return response;
    }

    @Override
    public Optional<Product> findProductByName(String nameProduct) {
        logger.debug("Executing query to find product with name: {}", nameProduct);
        Optional<Product> response;

        String jpql = "SELECT p FROM Product p " +
                "LEFT JOIN FETCH ProductDigital pd ON p.idProduct = pd.idProduct " +
                "LEFT JOIN FETCH ProductPhysical pp ON p.idProduct = pp.idProduct " +
                "WHERE p.nameProduct = :nameProduct";

        try {
            Product product = entityManager.createQuery(jpql, Product.class)
                    .setParameter("nameProduct", nameProduct)
                    .getSingleResult();
            response = Optional.ofNullable(product);
        } catch (NoResultException e) {
            logger.error("Product with name {} not found", nameProduct);
            response = Optional.empty();
        }
        return response;
    }

    @Override
    public void saveProduct(Product product) {
        logger.debug("Executing query Registering new product: {}", product.getNameProduct());
        try{
            entityManager.persist(product);
            entityManager.flush();
            logger.debug("Query Product with ID {} was successfully saved", product.getIdProduct());
        }catch(Exception e){
            logger.error("Error query saving Product with ID {}: {}", product.getIdProduct(), e.getMessage());
        }
    }

    @Override
    public boolean deleteProduct(Long idProduct) {
        logger.debug("Executing query Attempting to delete product with ID: {}", idProduct);
        boolean response =false;
        try{
            Product product = entityManager.find(Product.class, idProduct);
            if (product != null) {
                entityManager.remove(product);
                response = true;
            }}catch (Exception e) {
                logger.error("Error while querying delete product with ID {}: {}", idProduct, e.getMessage(), e);
            }
        return response;
        }


    @Override
    @Transactional
    public boolean updateProductStock(String nameProduct, Integer newStockProduct) {
        logger.debug("Updating stock for product {} -> {}", nameProduct, newStockProduct);
        boolean success = false;
        try {
            Product product = entityManager.createQuery(
                            "SELECT p FROM Product p WHERE p.nameProduct = :name", Product.class)
                    .setParameter("name", nameProduct)
                    .getSingleResult();

            if (product instanceof ProductPhysical) {
                ProductPhysical physical = (ProductPhysical) product;
                physical.setStockProduct(newStockProduct);
                entityManager.merge(physical);
                success = true;
            } else {
                logger.warn("Product {} is not a physical product. Cannot update stock.", nameProduct);
            }
        } catch (NoResultException e) {
            logger.warn("No product found with name {}", nameProduct);
        } catch (Exception e) {
            logger.error("Error updating stock for {}: {}", nameProduct, e.getMessage());
        }
        return success;
    }

    @Override
    @Transactional
    public boolean updateProductPrice(String nameProduct, BigDecimal newPriceProduct) {
        logger.debug("Updating price for product {} -> {}", nameProduct, newPriceProduct);
        boolean success = false;
        try {
            Product product = entityManager.createQuery(
                            "SELECT p FROM Product p WHERE p.nameProduct = :name", Product.class)
                    .setParameter("name", nameProduct)
                    .getSingleResult();

            product.setPriceProduct(newPriceProduct);
            entityManager.merge(product);
            success = true;
        } catch (NoResultException e) {
            logger.warn("No product found with name {}", nameProduct);
        } catch (Exception e) {
            logger.error("Error updating price for {}: {}", nameProduct, e.getMessage());
        }
        return success;
    }

    @Override
    public List<Product> findProductsByCategory(String category) {
        List<Product> products = Collections.emptyList();
        if (category != null) {
            try {
                TypedQuery<Product> query = entityManager.createQuery(
                                "SELECT p FROM Product p WHERE LOWER(p.category) = LOWER(:cat)", Product.class)
                        .setParameter("cat", category.trim());
                products = query.getResultList();
            } catch (Exception e) {
                logger.error("Error finding products by category {}: {}", category, e.getMessage());
            }
        }
        return products;
    }

    @Override
    public List<Product> findProductsByPriceRange(BigDecimal min, BigDecimal max) {
        List<Product> products = Collections.emptyList();
        if (min != null && max != null) {
            try {
                TypedQuery<Product> query = entityManager.createQuery(
                                "SELECT p FROM Product p WHERE p.priceProduct BETWEEN :min AND :max", Product.class)
                        .setParameter("min", min)
                        .setParameter("max", max);
                products = query.getResultList();
            } catch (Exception e) {
                logger.error("Error finding products by price range {} - {}: {}", min, max, e.getMessage());
            }
        }
        return products;
    }

    @Override
    public List<Product> findProductsInStock() {
        List<Product> products = Collections.emptyList();
        try {
            TypedQuery<ProductPhysical> query = entityManager.createQuery(
                    "SELECT pp FROM ProductPhysical pp WHERE pp.stockProduct > 0", ProductPhysical.class);
            products = List.copyOf(query.getResultList());
        } catch (Exception e) {
            logger.error("Error finding products in stock: {}", e.getMessage());
        }
        return products;
    }

    @Transactional
    public boolean updateProduct(Product updatedProduct) {
        boolean success = false;
        try {
            Product existingProduct = entityManager.find(Product.class, updatedProduct.getIdProduct());
            if (existingProduct != null) {
                existingProduct.setNameProduct(updatedProduct.getNameProduct());
                existingProduct.setDescription(updatedProduct.getDescription());
                existingProduct.setCategory(updatedProduct.getCategory());
                existingProduct.setImageUrl(updatedProduct.getImageUrl());
                existingProduct.setPriceProduct(updatedProduct.getPriceProduct());
                // stock y precio los podés manejar aparte si querés

                entityManager.merge(existingProduct);
                success = true;
            }
        } catch (Exception e) {
            logger.error("Error updating product: {}", e.getMessage());
        }
        return success;
    }
}







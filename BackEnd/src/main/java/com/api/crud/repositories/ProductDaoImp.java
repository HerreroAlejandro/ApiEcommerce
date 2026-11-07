package com.api.crud.repositories;

import com.api.crud.models.entity.Product;
import com.api.crud.models.enums.ProductType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Pageable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;


@Repository
@Transactional
public class ProductDaoImp implements ProductDao{

    @PersistenceContext
    private EntityManager entityManager;

    private static final Logger logger = LoggerFactory.getLogger(ProductDaoImp.class);

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
    public List<Product> showProducts(String name, String category, ProductType type, BigDecimal minPrice, BigDecimal maxPrice, Boolean inStock,
            Pageable pageable) {

        logger.debug("Executing product catalog query - name: {}, category: {}, type: {}, minPrice: {}, maxPrice: {}, inStock: {}",
                name, category, type, minPrice, maxPrice, inStock);

        StringBuilder jpql = new StringBuilder("""
            SELECT p
            FROM Product p
            WHERE p.active = true
            """);

        if (name != null && !name.trim().isEmpty()) {
            jpql.append("""
                AND LOWER(p.nameProduct) LIKE LOWER(:name)
                """);
        }

        if (category != null && !category.trim().isEmpty()) {
            jpql.append("""
                AND LOWER(p.category) = LOWER(:category)
                """);
        }

        if (type != null) {
            jpql.append("""
                AND p.type = :type
                """);
        }

        if (minPrice != null) {
            jpql.append("""
                AND p.priceProduct >= :minPrice
                """);
        }

        if (maxPrice != null) {
            jpql.append("""
                AND p.priceProduct <= :maxPrice
                """);
        }

        if (Boolean.TRUE.equals(inStock)) {
            jpql.append("""
                AND TYPE(p) = ProductPhysical
                AND TREAT(p AS ProductPhysical).stockProduct > 0
                """);
        }

        // Ordenamiento
        String sortProperty = "idProduct";
        String sortDirection = "ASC";

        if (pageable.getSort().isSorted()) {

            Sort.Order order = pageable.getSort().iterator().next();

            switch (order.getProperty()) {
                case "nameProduct":
                    sortProperty = "nameProduct";
                    break;

                case "priceProduct":
                    sortProperty = "priceProduct";
                    break;

                case "category":
                    sortProperty = "category";
                    break;

                case "idProduct":
                    sortProperty = "idProduct";
                    break;

                default:
                    sortProperty = "idProduct";
            }

            sortDirection = order.getDirection().name();
        }

        jpql.append(" ORDER BY p.")
                .append(sortProperty)
                .append(" ")
                .append(sortDirection);

        TypedQuery<Product> query = entityManager.createQuery(jpql.toString(), Product.class);

        if (name != null && !name.trim().isEmpty()) {
            query.setParameter("name", "%" + name.trim() + "%");
        }

        if (category != null && !category.trim().isEmpty()) {
            query.setParameter("category", category.trim());
        }

        if (type != null) {
            query.setParameter("type", type);
        }

        if (minPrice != null) {
            query.setParameter("minPrice", minPrice);
        }

        if (maxPrice != null) {
            query.setParameter("maxPrice", maxPrice);
        }

        query.setFirstResult((int) pageable.getOffset());
        query.setMaxResults(pageable.getPageSize());

        return query.getResultList();
    }

    @Override
    public long countProducts(String name, String category, ProductType type, BigDecimal minPrice, BigDecimal maxPrice, Boolean inStock) {

        logger.debug(
                "Executing product catalog count query - name: {}, category: {}, type: {}, minPrice: {}, maxPrice: {}, inStock: {}",
                name, category, type, minPrice, maxPrice, inStock);

        StringBuilder jpql = new StringBuilder("""
            SELECT COUNT(p)
            FROM Product p
            WHERE p.active = true
            """);

        if (name != null && !name.trim().isEmpty()) {
            jpql.append("""
                AND LOWER(p.nameProduct) LIKE LOWER(:name)
                """);
        }

        if (category != null && !category.trim().isEmpty()) {
            jpql.append("""
                AND LOWER(p.category) = LOWER(:category)
                """);
        }

        if (type != null) {
            jpql.append("""
                AND p.type = :type
                """);
        }

        if (minPrice != null) {
            jpql.append("""
                AND p.priceProduct >= :minPrice
                """);
        }

        if (maxPrice != null) {
            jpql.append("""
                AND p.priceProduct <= :maxPrice
                """);
        }

        if (Boolean.TRUE.equals(inStock)) {
            jpql.append("""
                AND TYPE(p) = ProductPhysical
                AND TREAT(p AS ProductPhysical).stockProduct > 0
                """);
        }

        TypedQuery<Long> query = entityManager.createQuery(jpql.toString(), Long.class);

        if (name != null && !name.trim().isEmpty()) {
            query.setParameter("name", "%" + name.trim() + "%");
        }

        if (category != null && !category.trim().isEmpty()) {
            query.setParameter("category", category.trim());
        }

        if (type != null) {
            query.setParameter("type", type);
        }

        if (minPrice != null) {
            query.setParameter("minPrice", minPrice);
        }

        if (maxPrice != null) {
            query.setParameter("maxPrice", maxPrice);
        }

        return query.getSingleResult();
    }

    @Override
    public List<Product> adminShowProducts(String name, String category, ProductType type, BigDecimal minPrice, BigDecimal maxPrice,
            Boolean active, Boolean inStock, Pageable pageable) {

        logger.debug("Executing admin product catalog query - name: {}, category: {}, type: {}, minPrice: {}, maxPrice: {}, active: {}, inStock: {}",
                name, category, type, minPrice, maxPrice, active, inStock);

        StringBuilder jpql = new StringBuilder("""
            SELECT p
            FROM Product p
            WHERE 1 = 1
            """);

        if (name != null && !name.trim().isEmpty()) {
            jpql.append("""
                AND LOWER(p.nameProduct) LIKE LOWER(:name)
                """);
        }

        if (category != null && !category.trim().isEmpty()) {
            jpql.append("""
                AND LOWER(p.category) = LOWER(:category)
                """);
        }

        if (type != null) {
            jpql.append("""
                AND p.type = :type
                """);
        }

        if (minPrice != null) {
            jpql.append("""
                AND p.priceProduct >= :minPrice
                """);
        }

        if (maxPrice != null) {
            jpql.append("""
                AND p.priceProduct <= :maxPrice
                """);
        }

        if (active != null) {
            jpql.append("""
                AND p.active = :active
                """);
        }

        if (Boolean.TRUE.equals(inStock)) {
            jpql.append("""
                AND TYPE(p) = ProductPhysical
                AND TREAT(p AS ProductPhysical).stockProduct > 0
                """);
        }

        if (Boolean.FALSE.equals(inStock)) {
            jpql.append("""
                AND TYPE(p) = ProductPhysical
                AND TREAT(p AS ProductPhysical).stockProduct = 0
                """);
        }

        String sortProperty = "idProduct";
        String sortDirection = "ASC";

        if (pageable.getSort().isSorted()) {
            Sort.Order order = pageable.getSort().iterator().next();

            switch (order.getProperty()) {
                case "nameProduct":
                    sortProperty = "nameProduct";
                    break;
                case "priceProduct":
                    sortProperty = "priceProduct";
                    break;
                case "category":
                    sortProperty = "category";
                    break;
                case "idProduct":
                    sortProperty = "idProduct";
                    break;
                case "active":
                    sortProperty = "active";
                    break;
                default:
                    sortProperty = "idProduct";
            }

            sortDirection = order.getDirection().name();
        }

        jpql.append(" ORDER BY p.")
                .append(sortProperty)
                .append(" ")
                .append(sortDirection);

        TypedQuery<Product> query =
                entityManager.createQuery(jpql.toString(), Product.class);

        if (name != null && !name.trim().isEmpty()) {
            query.setParameter("name", "%" + name.trim() + "%");
        }

        if (category != null && !category.trim().isEmpty()) {
            query.setParameter("category", category.trim());
        }

        if (type != null) {
            query.setParameter("type", type);
        }

        if (minPrice != null) {
            query.setParameter("minPrice", minPrice);
        }

        if (maxPrice != null) {
            query.setParameter("maxPrice", maxPrice);
        }

        if (active != null) {
            query.setParameter("active", active);
        }

        query.setFirstResult((int) pageable.getOffset());
        query.setMaxResults(pageable.getPageSize());

        return query.getResultList();
    }

    @Override
    public long countAdminProducts(String name, String category, ProductType type, BigDecimal minPrice, BigDecimal maxPrice, Boolean active,
            Boolean inStock) {

        StringBuilder jpql = new StringBuilder("""
            SELECT COUNT(p)
            FROM Product p
            WHERE 1 = 1
            """);

        if (name != null && !name.trim().isEmpty()) {
            jpql.append("""
                AND LOWER(p.nameProduct) LIKE LOWER(:name)
                """);
        }

        if (category != null && !category.trim().isEmpty()) {
            jpql.append("""
                AND LOWER(p.category) = LOWER(:category)
                """);
        }

        if (type != null) {
            jpql.append("""
                AND p.type = :type
                """);
        }

        if (minPrice != null) {
            jpql.append("""
                AND p.priceProduct >= :minPrice
                """);
        }

        if (maxPrice != null) {
            jpql.append("""
                AND p.priceProduct <= :maxPrice
                """);
        }

        if (active != null) {
            jpql.append("""
                AND p.active = :active
                """);
        }

        if (Boolean.TRUE.equals(inStock)) {
            jpql.append("""
                AND TYPE(p) = ProductPhysical
                AND TREAT(p AS ProductPhysical).stockProduct > 0
                """);
        }

        if (Boolean.FALSE.equals(inStock)) {
            jpql.append("""
                AND TYPE(p) = ProductPhysical
                AND TREAT(p AS ProductPhysical).stockProduct = 0
                """);
        }

        TypedQuery<Long> query = entityManager.createQuery(jpql.toString(), Long.class);

        if (name != null && !name.trim().isEmpty()) {
            query.setParameter("name", "%" + name.trim() + "%");
        }

        if (category != null && !category.trim().isEmpty()) {
            query.setParameter("category", category.trim());
        }

        if (type != null) {
            query.setParameter("type", type);
        }

        if (minPrice != null) {
            query.setParameter("minPrice", minPrice);
        }

        if (maxPrice != null) {
            query.setParameter("maxPrice", maxPrice);
        }

        if (active != null) {
            query.setParameter("active", active);
        }

        return query.getSingleResult();
    }

    @Override
    public Optional<Product> findProductById(Long idProduct) {
        logger.debug("Executing query to find product with ID: {}", idProduct);

        Optional<Product> response;

        String jpql = """
            SELECT p
            FROM Product p
            WHERE p.idProduct = :idProduct
            """;

        try {
            Product product = entityManager.createQuery(jpql, Product.class)
                    .setParameter("idProduct", idProduct)
                    .getSingleResult();

            response = Optional.of(product);

        } catch (NoResultException e) {
            logger.debug("Product with ID {} not found", idProduct);
            response = Optional.empty();
        }

        return response;
    }
/*
    @Override
    public List<Product> findProductsByName(String nameProduct, Pageable pageable) {
        logger.debug("Executing query to find active products by name: {}", nameProduct);

        String jpql = """
            SELECT p
            FROM Product p
            WHERE p.active = true
            AND LOWER(p.nameProduct) LIKE LOWER(:nameProduct)
            ORDER BY p.idProduct ASC
            """;

        TypedQuery<Product> query = entityManager.createQuery(jpql, Product.class);

        query.setParameter("nameProduct", "%" + nameProduct.trim() + "%");
        query.setFirstResult((int) pageable.getOffset());
        query.setMaxResults(pageable.getPageSize());

        return query.getResultList();
    }

    @Override
    public long countProductsByName(String nameProduct) {
        logger.debug("Executing query to count active products by name: {}", nameProduct);

        String jpql = """
            SELECT COUNT(p)
            FROM Product p
            WHERE p.active = true
            AND LOWER(p.nameProduct) LIKE LOWER(:nameProduct)
            """;

        return entityManager
                .createQuery(jpql, Long.class)
                .setParameter("nameProduct", "%" + nameProduct.trim() + "%")
                .getSingleResult();
    }

    @Override
    public List<Product> findProductsByNameAdmin(String nameProduct, Boolean active, Pageable pageable) {

        logger.debug("Executing query to find products by name for admin: {} - active: {}", nameProduct, active);

        String jpql = """
            SELECT p
            FROM Product p
            WHERE LOWER(p.nameProduct) LIKE LOWER(:nameProduct)
            AND (:active IS NULL OR p.active = :active)
            ORDER BY p.idProduct ASC
            """;

        TypedQuery<Product> query = entityManager.createQuery(jpql, Product.class);

        query.setParameter("nameProduct", "%" + nameProduct.trim() + "%");
        query.setParameter("active", active);
        query.setFirstResult((int) pageable.getOffset());
        query.setMaxResults(pageable.getPageSize());

        return query.getResultList();
    }

    @Override
    public long countProductsByNameAdmin(String nameProduct, Boolean active) {

        logger.debug("Executing query to count products by name for admin: {} - active: {}", nameProduct, active);

        String jpql = """
            SELECT COUNT(p)
            FROM Product p
            WHERE LOWER(p.nameProduct) LIKE LOWER(:nameProduct)
            AND (:active IS NULL OR p.active = :active)
            """;

        return entityManager.createQuery(jpql, Long.class)
                .setParameter("nameProduct", "%" + nameProduct.trim() + "%")
                .setParameter("active", active)
                .getSingleResult();
    }
*/
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

}







package com.api.crud.repositories.impl;

import com.api.crud.models.entity.Product;
import com.api.crud.models.enums.ProductType;
import com.api.crud.repositories.ProductDao;

import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
@Transactional
public class ProductDaoImp implements ProductDao {

    private static final Logger logger = LoggerFactory.getLogger(ProductDaoImp.class);

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public void saveProduct(Product product) {
        logger.debug("Executing query Registering new product: {}", product.getNameProduct());
        entityManager.persist(product);
        entityManager.flush();
        logger.debug("Query Product with ID {} was successfully saved", product.getIdProduct());
    }

    @Override
    public List<Product> showProducts(
            String name,
            String category,
            ProductType type,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Boolean inStock,
            Pageable pageable) {
        logger.debug(
                "Executing product catalog query - name: {}, category: {}, type: {}, minPrice: {}, maxPrice: {}, inStock: {}",
                name,
                category,
                type,
                minPrice,
                maxPrice,
                inStock);
        StringBuilder jpql =
                new StringBuilder(
                        """
            SELECT p
            FROM Product p
            WHERE p.active = true
            """);
        if (name != null && !name.trim().isEmpty()) {
            jpql.append(
                    """
                AND LOWER(p.nameProduct) LIKE LOWER(:name)
                """);
        }
        if (category != null && !category.trim().isEmpty()) {
            jpql.append(
                    """
                AND LOWER(p.category) = LOWER(:category)
                """);
        }
        if (type != null) {
            jpql.append(
                    """
                AND p.type = :type
                """);
        }
        if (minPrice != null) {
            jpql.append(
                    """
                AND p.priceProduct >= :minPrice
                """);
        }
        if (maxPrice != null) {
            jpql.append(
                    """
                AND p.priceProduct <= :maxPrice
                """);
        }
        if (Boolean.TRUE.equals(inStock)) {
            jpql.append(
                    """
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
        jpql.append(" ORDER BY p.").append(sortProperty).append(" ").append(sortDirection);
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
    public long countProducts(
            String name,
            String category,
            ProductType type,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Boolean inStock) {
        logger.debug(
                "Executing product catalog count query - name: {}, category: {}, type: {}, minPrice: {}, maxPrice: {}, inStock: {}",
                name,
                category,
                type,
                minPrice,
                maxPrice,
                inStock);
        StringBuilder jpql =
                new StringBuilder(
                        """
            SELECT COUNT(p)
            FROM Product p
            WHERE p.active = true
            """);
        if (name != null && !name.trim().isEmpty()) {
            jpql.append(
                    """
                AND LOWER(p.nameProduct) LIKE LOWER(:name)
                """);
        }
        if (category != null && !category.trim().isEmpty()) {
            jpql.append(
                    """
                AND LOWER(p.category) = LOWER(:category)
                """);
        }
        if (type != null) {
            jpql.append(
                    """
                AND p.type = :type
                """);
        }
        if (minPrice != null) {
            jpql.append(
                    """
                AND p.priceProduct >= :minPrice
                """);
        }
        if (maxPrice != null) {
            jpql.append(
                    """
                AND p.priceProduct <= :maxPrice
                """);
        }
        if (Boolean.TRUE.equals(inStock)) {
            jpql.append(
                    """
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
    public List<Product> adminShowProducts(
            String name,
            String category,
            ProductType type,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Boolean active,
            Boolean inStock,
            Pageable pageable) {
        logger.debug(
                "Executing admin product catalog query - name: {}, category: {}, type: {}, minPrice: {}, maxPrice: {}, active: {}, inStock: {}",
                name,
                category,
                type,
                minPrice,
                maxPrice,
                active,
                inStock);
        StringBuilder jpql =
                new StringBuilder(
                        """
            SELECT p
            FROM Product p
            WHERE 1 = 1
            """);
        if (name != null && !name.trim().isEmpty()) {
            jpql.append(
                    """
                AND LOWER(p.nameProduct) LIKE LOWER(:name)
                """);
        }
        if (category != null && !category.trim().isEmpty()) {
            jpql.append(
                    """
                AND LOWER(p.category) = LOWER(:category)
                """);
        }
        if (type != null) {
            jpql.append(
                    """
                AND p.type = :type
                """);
        }
        if (minPrice != null) {
            jpql.append(
                    """
                AND p.priceProduct >= :minPrice
                """);
        }
        if (maxPrice != null) {
            jpql.append(
                    """
                AND p.priceProduct <= :maxPrice
                """);
        }
        if (active != null) {
            jpql.append(
                    """
                AND p.active = :active
                """);
        }
        if (Boolean.TRUE.equals(inStock)) {
            jpql.append(
                    """
                AND TYPE(p) = ProductPhysical
                AND TREAT(p AS ProductPhysical).stockProduct > 0
                """);
        }
        if (Boolean.FALSE.equals(inStock)) {
            jpql.append(
                    """
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
        jpql.append(" ORDER BY p.").append(sortProperty).append(" ").append(sortDirection);
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
        if (active != null) {
            query.setParameter("active", active);
        }
        query.setFirstResult((int) pageable.getOffset());
        query.setMaxResults(pageable.getPageSize());
        return query.getResultList();
    }

    @Override
    public long countAdminProducts(
            String name,
            String category,
            ProductType type,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Boolean active,
            Boolean inStock) {
        StringBuilder jpql =
                new StringBuilder(
                        """
            SELECT COUNT(p)
            FROM Product p
            WHERE 1 = 1
            """);
        if (name != null && !name.trim().isEmpty()) {
            jpql.append(
                    """
                AND LOWER(p.nameProduct) LIKE LOWER(:name)
                """);
        }
        if (category != null && !category.trim().isEmpty()) {
            jpql.append(
                    """
                AND LOWER(p.category) = LOWER(:category)
                """);
        }
        if (type != null) {
            jpql.append(
                    """
                AND p.type = :type
                """);
        }
        if (minPrice != null) {
            jpql.append(
                    """
                AND p.priceProduct >= :minPrice
                """);
        }
        if (maxPrice != null) {
            jpql.append(
                    """
                AND p.priceProduct <= :maxPrice
                """);
        }
        if (active != null) {
            jpql.append(
                    """
                AND p.active = :active
                """);
        }
        if (Boolean.TRUE.equals(inStock)) {
            jpql.append(
                    """
                AND TYPE(p) = ProductPhysical
                AND TREAT(p AS ProductPhysical).stockProduct > 0
                """);
        }
        if (Boolean.FALSE.equals(inStock)) {
            jpql.append(
                    """
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
    public Optional<Product> getProductById(Long idProduct) {
        logger.debug("Executing query to find product with ID: {}", idProduct);
        Optional<Product> response;
        String jpql =
                """
            SELECT p
            FROM Product p
            WHERE p.idProduct = :idProduct
            """;
        try {
            Product product =
                    entityManager
                            .createQuery(jpql, Product.class)
                            .setParameter("idProduct", idProduct)
                            .getSingleResult();
            response = Optional.of(product);
        } catch (NoResultException e) {
            logger.debug("Product with ID {} not found", idProduct);
            response = Optional.empty();
        }
        return response;
    }

    @Override
    public boolean hasProductReferences(Long idProduct) {
        return !entityManager.createQuery(
                        "SELECT i.idCartItem FROM CartItem i WHERE i.productCartItem.idProduct = :id",
                        Long.class)
                .setParameter("id", idProduct)
                .setMaxResults(1)
                .getResultList()
                .isEmpty()
                || !entityManager.createQuery(
                        "SELECT i.idOrderItem FROM OrderItem i WHERE i.productOrderItem.idProduct = :id",
                        Long.class)
                .setParameter("id", idProduct)
                .setMaxResults(1)
                .getResultList()
                .isEmpty()
                || !entityManager.createQuery(
                        "SELECT a.id FROM CartChangeAudit a WHERE a.productId = :id", Long.class)
                .setParameter("id", idProduct)
                .setMaxResults(1)
                .getResultList()
                .isEmpty();
    }

    @Override
    public boolean deleteProduct(Long idProduct) {
        logger.debug("Executing query Attempting to delete product with ID: {}", idProduct);
        boolean response = false;
        Product product = entityManager.find(Product.class, idProduct);
        if (product != null) {
            entityManager.remove(product);
            entityManager.flush();
            response = true;
        }
        return response;
    }
}

package com.api.crud.repositories.impl;

import com.api.crud.models.entity.CartChangeAudit;
import com.api.crud.repositories.CartChangeAuditDao;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.data.domain.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public class CartChangeAuditDaoImp implements CartChangeAuditDao {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Page<CartChangeAudit> getByCartId(Long cartId, Pageable pageable) {
        List<CartChangeAudit> rows =
                entityManager
                        .createQuery(
                                "SELECT a FROM CartChangeAudit a WHERE a.cartId = :id ORDER BY"
                                    + " a.createdAt DESC, a.id DESC",
                                CartChangeAudit.class)
                        .setParameter("id", cartId)
                        .setFirstResult(Math.toIntExact(pageable.getOffset()))
                        .setMaxResults(pageable.getPageSize())
                        .getResultList();
        long count =
                entityManager
                        .createQuery(
                                "SELECT COUNT(a) FROM CartChangeAudit a WHERE a.cartId = :id",
                                Long.class)
                        .setParameter("id", cartId)
                        .getSingleResult();
        return new PageImpl<>(rows, pageable, count);
    }

    @Transactional
    @Override
    public void save(CartChangeAudit audit) {
        entityManager.persist(audit);
    }
}

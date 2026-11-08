package com.api.crud.repositories;

import com.api.crud.exceptions.*;
import com.api.crud.models.entity.CartChangeAudit;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class CartChangeAuditDao {

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public void save(CartChangeAudit audit) {
        entityManager.persist(audit);
    }
}

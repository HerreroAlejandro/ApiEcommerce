package com.api.crud.repositories.impl;

import com.api.crud.models.entity.CartChangeAudit;
import com.api.crud.repositories.CartChangeAuditDao;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class CartChangeAuditDaoImp implements CartChangeAuditDao {

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    @Override
    public void save(CartChangeAudit audit) {
        entityManager.persist(audit);
    }
}

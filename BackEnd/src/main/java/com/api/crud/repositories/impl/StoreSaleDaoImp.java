package com.api.crud.repositories.impl;

import com.api.crud.models.entity.Order;
import com.api.crud.repositories.StoreSaleDao;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;

import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class StoreSaleDaoImp implements StoreSaleDao {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Optional<Order> getByReference(String reference) {
        return entityManager
                .createQuery(
                        "SELECT o FROM Order o WHERE o.storeSale.reference = :reference",
                        Order.class)
                .setParameter("reference", reference)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .setMaxResults(1)
                .getResultList()
                .stream()
                .findFirst();
    }
}

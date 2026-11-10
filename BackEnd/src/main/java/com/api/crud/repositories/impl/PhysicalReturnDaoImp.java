package com.api.crud.repositories.impl;

import com.api.crud.models.entity.PhysicalReturn;
import com.api.crud.repositories.PhysicalReturnDao;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class PhysicalReturnDaoImp implements PhysicalReturnDao {

    @PersistenceContext private EntityManager entityManager;

    @Override
    public Optional<PhysicalReturn> getByOrderId(Long orderId) {
        return entityManager
                .createQuery(
                        "SELECT r FROM PhysicalReturn r WHERE r.order.idOrder = :id",
                        PhysicalReturn.class)
                .setParameter("id", orderId)
                .getResultStream()
                .findFirst();
    }

    @Override
    public void save(PhysicalReturn physicalReturn) {
        entityManager.persist(physicalReturn);
        entityManager.flush();
    }
}

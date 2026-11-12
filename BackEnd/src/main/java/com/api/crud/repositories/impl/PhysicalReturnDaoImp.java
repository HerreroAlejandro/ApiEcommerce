package com.api.crud.repositories.impl;

import com.api.crud.models.entity.PhysicalReturn;
import com.api.crud.models.enums.PhysicalReturnStatus;
import com.api.crud.repositories.PhysicalReturnDao;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.data.domain.*;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class PhysicalReturnDaoImp implements PhysicalReturnDao {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Page<PhysicalReturn> getReturns(PhysicalReturnStatus status, Pageable pageable) {
        String predicate = status == null ? "" : " WHERE r.status = :status";
        var query =
                entityManager.createQuery(
                        "SELECT r FROM PhysicalReturn r"
                                + predicate
                                + " ORDER BY r.requestedAt DESC, r.id DESC",
                        PhysicalReturn.class);
        var count =
                entityManager.createQuery(
                        "SELECT COUNT(r) FROM PhysicalReturn r" + predicate, Long.class);
        if (status != null) {
            query.setParameter("status", status);
            count.setParameter("status", status);
        }
        return new PageImpl<>(
                query.setFirstResult(Math.toIntExact(pageable.getOffset()))
                        .setMaxResults(pageable.getPageSize())
                        .getResultList(),
                pageable,
                count.getSingleResult());
    }

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

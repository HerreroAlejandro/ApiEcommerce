package com.api.crud.repositories.impl;

import com.api.crud.models.entity.PaymentRefundAttempt;
import com.api.crud.repositories.PaymentRefundAttemptDao;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;

import org.springframework.data.domain.*;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class PaymentRefundAttemptDaoImp implements PaymentRefundAttemptDao {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Optional<PaymentRefundAttempt> getById(String id) {
        // Mutating callers already hold the order lock. Use a current read after waiting.
        return Optional.ofNullable(
                entityManager.find(PaymentRefundAttempt.class, id, LockModeType.PESSIMISTIC_WRITE));
    }

    @Override
    public Page<PaymentRefundAttempt> getByRefundId(String refundId, Pageable pageable) {
        var rows =
                entityManager
                        .createQuery(
                                "SELECT a FROM PaymentRefundAttempt a WHERE a.refund.id = :id ORDER"
                                        + " BY a.attemptNumber DESC",
                                PaymentRefundAttempt.class)
                        .setParameter("id", refundId)
                        .setFirstResult(Math.toIntExact(pageable.getOffset()))
                        .setMaxResults(pageable.getPageSize())
                        .getResultList();
        long count =
                entityManager
                        .createQuery(
                                "SELECT COUNT(a) FROM PaymentRefundAttempt a WHERE a.refund.id ="
                                        + " :id",
                                Long.class)
                        .setParameter("id", refundId)
                        .getSingleResult();
        return new PageImpl<>(rows, pageable, count);
    }

    @Override
    public void save(PaymentRefundAttempt attempt) {
        entityManager.persist(attempt);
        entityManager.flush();
    }

    @Override
    public void refresh(PaymentRefundAttempt attempt) {
        entityManager.refresh(attempt, LockModeType.PESSIMISTIC_WRITE);
    }
}

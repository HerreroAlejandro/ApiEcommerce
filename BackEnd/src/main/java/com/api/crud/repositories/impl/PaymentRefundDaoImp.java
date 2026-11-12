package com.api.crud.repositories.impl;

import com.api.crud.models.entity.PaymentRefund;
import com.api.crud.repositories.PaymentRefundDao;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;

import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class PaymentRefundDaoImp implements PaymentRefundDao {

    @Override
    public List<PaymentRefund> getAllByPaymentId(Long paymentId) {
        return entityManager
                .createQuery(
                        "SELECT r FROM PaymentRefund r WHERE r.payment.id = :id ORDER BY"
                                + " r.requestedAt DESC, r.id DESC",
                        PaymentRefund.class)
                .setParameter("id", paymentId)
                .getResultList();
    }

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Optional<PaymentRefund> getByPaymentId(Long paymentId) {
        return entityManager
                .createQuery(
                        "SELECT r FROM PaymentRefund r WHERE r.payment.id = :id ORDER BY"
                                + " r.requestedAt DESC, r.id DESC",
                        PaymentRefund.class)
                .setParameter("id", paymentId)
                .setMaxResults(1)
                .getResultList()
                .stream()
                .findFirst();
    }

    @Override
    public Optional<PaymentRefund> getById(String id) {
        return Optional.ofNullable(entityManager.find(PaymentRefund.class, id));
    }

    @Override
    public void save(PaymentRefund refund) {
        entityManager.persist(refund);
        entityManager.flush();
    }

    @Override
    public void refresh(PaymentRefund refund) {
        entityManager.refresh(refund, LockModeType.PESSIMISTIC_WRITE);
    }
}

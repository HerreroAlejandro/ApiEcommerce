package com.api.crud.repositories.impl;

import com.api.crud.models.entity.Payment;
import com.api.crud.models.entity.PaymentCheckout;
import com.api.crud.models.enums.PaymentRefundStatus;
import com.api.crud.models.enums.PaymentReviewReason;
import com.api.crud.repositories.PaymentDao;

import jakarta.persistence.*;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class PaymentDaoImp implements PaymentDao {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Optional<Payment> getById(Long paymentId) {
        return Optional.ofNullable(entityManager.find(Payment.class, paymentId));
    }

    @Override
    public void refreshPayment(Payment payment) {
        entityManager.refresh(payment);
    }

    @Override
    public Optional<PaymentCheckout> getCheckoutByOrderId(Long orderId) {
        return entityManager
                .createQuery(
                        "SELECT c FROM PaymentCheckout c WHERE c.order.idOrder = :id",
                        PaymentCheckout.class)
                .setParameter("id", orderId)
                .getResultList()
                .stream()
                .findFirst();
    }

    @Override
    public Optional<PaymentCheckout> getCheckoutByReference(String reference) {
        return Optional.ofNullable(entityManager.find(PaymentCheckout.class, reference));
    }

    @Override
    public void saveCheckout(PaymentCheckout checkout) {
        entityManager.persist(checkout);
        entityManager.flush();
    }

    @Override
    public void refreshCheckout(PaymentCheckout checkout) {
        entityManager.refresh(checkout);
    }

    @Override
    public Optional<Payment> getByProviderId(String paymentId) {
        return entityManager
                .createQuery(
                        "SELECT p FROM Payment p WHERE p.providerPaymentId = :id", Payment.class)
                .setParameter("id", paymentId)
                .getResultList()
                .stream()
                .findFirst();
    }

    @Override
    public List<Payment> getByOrderId(Long orderId) {
        return entityManager
                .createQuery(
                        "SELECT p FROM Payment p WHERE p.checkout.order.idOrder = :id ORDER BY p.id"
                                + " DESC",
                        Payment.class)
                .setParameter("id", orderId)
                .getResultList();
    }

    @Override
    public void savePayment(Payment payment) {
        entityManager.persist(payment);
    }

    @Override
    public Page<Payment> getReviewPayments(Pageable pageable) {
        List<Payment> rows =
                entityManager
                        .createQuery(
                                "SELECT p FROM Payment p WHERE (p.reviewReason <> :none AND"
                                        + " p.reviewReason <> :closed) OR EXISTS (SELECT r.id FROM"
                                        + " PaymentRefund r WHERE r.payment = p AND r.status <>"
                                        + " :confirmed) ORDER BY p.verifiedAt DESC, p.id DESC",
                                Payment.class)
                        .setParameter("none", PaymentReviewReason.NONE)
                        .setParameter("closed", PaymentReviewReason.CHARGEBACK_LOST)
                        .setParameter("confirmed", PaymentRefundStatus.CONFIRMED)
                        .setFirstResult(Math.toIntExact(pageable.getOffset()))
                        .setMaxResults(pageable.getPageSize())
                        .getResultList();
        long count =
                entityManager
                        .createQuery(
                                "SELECT COUNT(p) FROM Payment p WHERE (p.reviewReason <> :none AND"
                                        + " p.reviewReason <> :closed) OR EXISTS (SELECT r.id FROM"
                                        + " PaymentRefund r WHERE r.payment = p AND r.status <>"
                                        + " :confirmed)",
                                Long.class)
                        .setParameter("none", PaymentReviewReason.NONE)
                        .setParameter("closed", PaymentReviewReason.CHARGEBACK_LOST)
                        .setParameter("confirmed", PaymentRefundStatus.CONFIRMED)
                        .getSingleResult();
        return new PageImpl<>(rows, pageable, count);
    }
}

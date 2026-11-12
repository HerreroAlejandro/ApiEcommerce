package com.api.crud.repositories.impl;

import com.api.crud.models.entity.ChargebackCase;
import com.api.crud.models.enums.ChargebackResolution;
import com.api.crud.repositories.ChargebackDao;

import jakarta.persistence.*;

import org.springframework.data.domain.*;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class ChargebackDaoImp implements ChargebackDao {
    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Optional<ChargebackCase> getById(Long id) {
        return Optional.ofNullable(entityManager.find(ChargebackCase.class, id));
    }

    @Override
    public List<ChargebackCase> getByPaymentId(Long paymentId) {
        return entityManager
                .createQuery(
                        "SELECT c FROM ChargebackCase c WHERE c.payment.id = :id ORDER BY c.id",
                        ChargebackCase.class)
                .setParameter("id", paymentId)
                .getResultList();
    }

    @Override
    public List<ChargebackCase> getByProviderOrderId(String providerOrderId) {
        return entityManager
                .createQuery(
                        "SELECT c FROM ChargebackCase c WHERE c.payment.checkout.providerOrderId ="
                                + " :id ORDER BY c.id",
                        ChargebackCase.class)
                .setParameter("id", providerOrderId)
                .getResultList();
    }

    @Override
    public Page<ChargebackCase> getCases(ChargebackResolution resolution, Pageable pageable) {
        String predicate = resolution == null ? "" : " WHERE c.resolution = :resolution";
        var query =
                entityManager.createQuery(
                        "SELECT c FROM ChargebackCase c"
                                + predicate
                                + " ORDER BY c.detectedAt DESC, c.id DESC",
                        ChargebackCase.class);
        var count =
                entityManager.createQuery(
                        "SELECT COUNT(c) FROM ChargebackCase c" + predicate, Long.class);
        if (resolution != null) {
            query.setParameter("resolution", resolution);
            count.setParameter("resolution", resolution);
        }
        return new PageImpl<>(
                query.setFirstResult(Math.toIntExact(pageable.getOffset()))
                        .setMaxResults(pageable.getPageSize())
                        .getResultList(),
                pageable,
                count.getSingleResult());
    }

    @Override
    public void save(ChargebackCase chargeback) {
        entityManager.persist(chargeback);
        entityManager.flush();
    }

    @Override
    public void refresh(ChargebackCase chargeback) {
        entityManager.refresh(chargeback, LockModeType.PESSIMISTIC_WRITE);
    }
}

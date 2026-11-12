package com.api.crud.repositories.impl;

import com.api.crud.models.entity.EmailVerificationToken;
import com.api.crud.repositories.EmailVerificationTokenDao;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class EmailVerificationTokenDaoImp implements EmailVerificationTokenDao {
    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Optional<EmailVerificationToken> getByUserId(Long userId) {
        // The service always locks the user first, serializing confirm/resend operations.
        EmailVerificationToken token = entityManager.find(EmailVerificationToken.class, userId);
        if (token != null) entityManager.refresh(token);
        return Optional.ofNullable(token);
    }

    @Override
    public Optional<Long> getUserIdByTokenHash(String hash) {
        return entityManager
                .createQuery(
                        "SELECT t.user.id FROM EmailVerificationToken t WHERE t.tokenHash = :hash",
                        Long.class)
                .setParameter("hash", hash)
                .getResultStream()
                .findFirst();
    }

    @Override
    public void save(EmailVerificationToken token) {
        entityManager.persist(token);
    }
}

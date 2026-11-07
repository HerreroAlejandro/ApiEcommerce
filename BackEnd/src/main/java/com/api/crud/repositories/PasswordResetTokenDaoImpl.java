package com.api.crud.repositories;

import com.api.crud.models.entity.PasswordResetToken;
import com.api.crud.models.entity.UserModel;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class PasswordResetTokenDaoImpl implements PasswordResetTokenDao {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional
    public void save(PasswordResetToken resetToken) {
        entityManager.persist(resetToken);
    }

    @Override
    public Optional<PasswordResetToken> findByToken(String token) {
        try {
            PasswordResetToken resetToken = entityManager
                    .createQuery("SELECT t FROM PasswordResetToken t " + "JOIN FETCH t.user " + "WHERE t.token = :token", PasswordResetToken.class)
                    .setParameter("token", token)
                    .getSingleResult();

            return Optional.of(resetToken);

        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    @Override
    @Transactional
    public void invalidateTokensByUser(UserModel user) {
        entityManager.createQuery("UPDATE PasswordResetToken t " + "SET t.used = true " + "WHERE t.user = :user " + "AND t.used = false")
                .setParameter("user", user)
                .executeUpdate();
    }

    @Override
    @Transactional
    public void update(PasswordResetToken resetToken) {
        entityManager.merge(resetToken);
    }
}

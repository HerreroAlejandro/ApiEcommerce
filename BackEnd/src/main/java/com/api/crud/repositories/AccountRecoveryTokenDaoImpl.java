package com.api.crud.repositories;

import com.api.crud.models.entity.AccountRecoveryToken;
import com.api.crud.models.entity.UserModel;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class AccountRecoveryTokenDaoImpl implements AccountRecoveryTokenDao {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional
    public void save(AccountRecoveryToken recoveryToken) {
        entityManager.persist(recoveryToken);
    }

    @Override
    public Optional<AccountRecoveryToken> findByToken(String token) {
        try {
            AccountRecoveryToken recoveryToken = entityManager
                    .createQuery(
                            "SELECT t FROM AccountRecoveryToken t " +
                                    "JOIN FETCH t.user " +
                                    "WHERE t.token = :token",
                            AccountRecoveryToken.class
                    )
                    .setParameter("token", token)
                    .getSingleResult();

            return Optional.of(recoveryToken);

        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    @Override
    @Transactional
    public void invalidateTokensByUser(UserModel user) {

        entityManager.createQuery(
                        "UPDATE AccountRecoveryToken t " + "SET t.used = true " + "WHERE t.user = :user " + "AND t.used = false")
                .setParameter("user", user)
                .executeUpdate();
    }

    @Override
    @Transactional
    public void update(AccountRecoveryToken recoveryToken) {
        entityManager.merge(recoveryToken);
    }



}

package com.api.crud.repositories;

import com.api.crud.exceptions.*;
import com.api.crud.models.entity.*;

import jakarta.persistence.*;

import org.springframework.data.domain.*;
import org.springframework.stereotype.Repository;

@Repository
public class AccountStatusDao {

    @PersistenceContext
    private EntityManager entityManager;

    public UserModel lockUser(Long id) {
        UserModel user = entityManager.find(UserModel.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (user == null) throw new ResourceNotFoundException("Usuario no encontrado");
        entityManager.refresh(user, LockModeType.PESSIMISTIC_WRITE);
        return user;
    }

    public void lockToken(AccountRecoveryToken token) {
        entityManager.refresh(token, LockModeType.PESSIMISTIC_WRITE);
    }

    public void save(AccountStatusAudit audit) {
        entityManager.persist(audit);
    }

    public Page<AccountStatusAudit> getHistory(Long userId, Pageable pageable) {
        if (pageable.getPageSize() > 100 || pageable.getOffset() > Integer.MAX_VALUE) {
            throw new InvalidRequestException("Paginacion fuera del limite");
        }
        var query =
                entityManager
                        .createQuery(
                                "SELECT a FROM AccountStatusAudit a WHERE a.userId = :id ORDER BY a.createdAt DESC, a.id DESC",
                                AccountStatusAudit.class)
                        .setParameter("id", userId)
                        .setFirstResult((int) pageable.getOffset())
                        .setMaxResults(pageable.getPageSize());
        long count =
                entityManager
                        .createQuery(
                                "SELECT COUNT(a) FROM AccountStatusAudit a WHERE a.userId = :id",
                                Long.class)
                        .setParameter("id", userId)
                        .getSingleResult();
        return new PageImpl<>(query.getResultList(), pageable, count);
    }
}

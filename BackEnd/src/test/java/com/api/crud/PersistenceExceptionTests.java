package com.api.crud;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.api.crud.models.entity.Role;
import com.api.crud.models.entity.UserModel;
import com.api.crud.repositories.impl.RoleDaoImp;
import com.api.crud.repositories.impl.UserDaoImp;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class PersistenceExceptionTests {

    @Test
    void failedRoleWritePropagatesInsteadOfReportingSuccess() {
        EntityManager entityManager = mock(EntityManager.class);
        RoleDaoImp dao = new RoleDaoImp();
        ReflectionTestUtils.setField(dao, "entityManager", entityManager);
        Role role = new Role();
        PersistenceException failure = new PersistenceException("Write failed");
        doThrow(failure).when(entityManager).persist(role);
        assertSame(failure, assertThrows(PersistenceException.class, () -> dao.saveRole(role)));
    }

    @Test
    void failedUserReadPropagatesInsteadOfReturningMissingUser() {
        EntityManager entityManager = mock(EntityManager.class);
        UserDaoImp dao = new UserDaoImp();
        ReflectionTestUtils.setField(dao, "entityManager", entityManager);
        PersistenceException failure = new PersistenceException("Connection failed");
        when(entityManager.find(UserModel.class, 7L)).thenThrow(failure);
        assertSame(failure, assertThrows(PersistenceException.class, () -> dao.getUserById(7L)));
    }
}

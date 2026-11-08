package com.api.crud;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.api.crud.models.entity.ProductPhysical;
import com.api.crud.repositories.impl.ProductDaoImp;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ProductDaoTests {

    @Test
    void savingProductPropagatesDatabaseFailure() {
        EntityManager manager = mock(EntityManager.class);
        ProductDaoImp dao = new ProductDaoImp();
        ReflectionTestUtils.setField(dao, "entityManager", manager);
        ProductPhysical product = new ProductPhysical();
        PersistenceException failure = new PersistenceException("Database failure");
        doThrow(failure).when(manager).flush();
        assertSame(
                failure, assertThrows(PersistenceException.class, () -> dao.saveProduct(product)));
    }
}

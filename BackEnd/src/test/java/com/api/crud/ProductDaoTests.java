package com.api.crud;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.api.crud.models.entity.ProductPhysical;
import com.api.crud.repositories.impl.ProductDaoImp;
import com.api.crud.repositories.impl.OrderWorkflowDaoImp;
import com.api.crud.models.entity.Product;
import com.api.crud.models.entity.Order;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.LockModeType;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ProductDaoTests {

    @Test
    void reservationAndPaymentLocksRefreshPreviouslyLoadedDatabaseState() {
        EntityManager manager = mock(EntityManager.class);
        OrderWorkflowDaoImp workflow = new OrderWorkflowDaoImp();
        ReflectionTestUtils.setField(workflow, "entityManager", manager);
        ProductPhysical product = new ProductPhysical();
        Order order = new Order();
        when(manager.find(Product.class, 4L)).thenReturn(product);
        when(manager.find(Order.class, 10L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(order);
        assertSame(product, workflow.lockProduct(4L));
        assertSame(order, workflow.lockOrder(10L));
        verify(manager).refresh(product, LockModeType.PESSIMISTIC_WRITE);
        verify(manager).refresh(order, LockModeType.PESSIMISTIC_WRITE);
    }

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

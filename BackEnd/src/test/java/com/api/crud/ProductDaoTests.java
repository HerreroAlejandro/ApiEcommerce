package com.api.crud;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.api.crud.models.entity.Order;
import com.api.crud.models.entity.Product;
import com.api.crud.models.entity.ProductPhysical;
import com.api.crud.repositories.impl.OrderWorkflowDaoImp;
import com.api.crud.repositories.impl.ProductDaoImp;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.TypedQuery;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

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

    @Test
    @SuppressWarnings("unchecked")
    void publicAvailabilityFilterAndCountIncludeDigitalProducts() {
        EntityManager manager = mock(EntityManager.class);
        TypedQuery<Product> list = mock(TypedQuery.class);
        TypedQuery<Long> count = mock(TypedQuery.class);
        when(manager.createQuery(anyString(), eq(Product.class))).thenReturn(list);
        when(manager.createQuery(anyString(), eq(Long.class))).thenReturn(count);
        when(list.getResultList()).thenReturn(List.of());
        when(count.getSingleResult()).thenReturn(0L);
        ProductDaoImp dao = new ProductDaoImp();
        ReflectionTestUtils.setField(dao, "entityManager", manager);
        dao.showProducts(null, null, null, null, null, true, PageRequest.of(0, 20));
        dao.countProducts(null, null, null, null, null, true);
        ArgumentCaptor<String> query = ArgumentCaptor.forClass(String.class);
        verify(manager).createQuery(query.capture(), eq(Product.class));
        verify(manager).createQuery(query.capture(), eq(Long.class));
        for (String jpql : query.getAllValues()) {
            assertTrue(jpql.contains("p.active = true"));
            assertTrue(
                    jpql.contains(
                            "TYPE(p) = ProductDigital OR TREAT(p AS ProductPhysical).stockProduct >"
                                + " 0"));
        }
    }
}

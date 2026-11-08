package com.api.crud.repositories.impl;

import com.api.crud.models.entity.DigitalDelivery;
import com.api.crud.repositories.DigitalDeliveryDao;

import jakarta.persistence.*;

import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class DigitalDeliveryDaoImp implements DigitalDeliveryDao {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Optional<DigitalDelivery> getById(Long id) {
        return Optional.ofNullable(entityManager.find(DigitalDelivery.class, id));
    }

    @Override
    public Optional<DigitalDelivery> lockById(Long id) {
        DigitalDelivery delivery =
                entityManager.find(DigitalDelivery.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (delivery != null) entityManager.refresh(delivery, LockModeType.PESSIMISTIC_WRITE);
        return Optional.ofNullable(delivery);
    }

    @Override
    public List<DigitalDelivery> getByOrderId(Long orderId) {
        return entityManager
                .createQuery(
                        "SELECT d FROM DigitalDelivery d JOIN FETCH d.orderItem i WHERE i.order.idOrder = :orderId ORDER BY d.id",
                        DigitalDelivery.class)
                .setParameter("orderId", orderId)
                .getResultList();
    }

    @Override
    public void save(DigitalDelivery delivery) {
        entityManager.persist(delivery);
    }
}

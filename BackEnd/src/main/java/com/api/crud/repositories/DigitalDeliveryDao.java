package com.api.crud.repositories;

import com.api.crud.models.entity.DigitalDelivery;

import java.util.List;
import java.util.Optional;

public interface DigitalDeliveryDao {

    Optional<DigitalDelivery> getById(Long id);

    Optional<DigitalDelivery> lockById(Long id);

    List<DigitalDelivery> getByOrderId(Long orderId);

    void save(DigitalDelivery delivery);
}

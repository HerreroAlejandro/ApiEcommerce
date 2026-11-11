package com.api.crud.repositories;

import com.api.crud.models.entity.Order;

import java.util.Optional;

public interface StoreSaleDao {
    Optional<Order> getByReference(String reference);
}

package com.api.crud.repositories;

import com.api.crud.models.entity.PhysicalReturn;

import java.util.Optional;

public interface PhysicalReturnDao {
    Optional<PhysicalReturn> getByOrderId(Long orderId);

    void save(PhysicalReturn physicalReturn);
}

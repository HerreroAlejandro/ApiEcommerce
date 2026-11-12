package com.api.crud.repositories;

import com.api.crud.models.entity.PhysicalReturn;
import com.api.crud.models.enums.PhysicalReturnStatus;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface PhysicalReturnDao {
    Optional<PhysicalReturn> getByOrderId(Long orderId);

    void save(PhysicalReturn physicalReturn);

    Page<PhysicalReturn> getReturns(PhysicalReturnStatus status, Pageable pageable);
}

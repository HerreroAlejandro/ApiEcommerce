package com.api.crud.repositories;

import com.api.crud.models.entity.ChargebackCase;
import com.api.crud.models.enums.ChargebackResolution;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface ChargebackDao {
    Optional<ChargebackCase> getById(Long id);

    List<ChargebackCase> getByPaymentId(Long paymentId);

    List<ChargebackCase> getByProviderOrderId(String providerOrderId);

    Page<ChargebackCase> getCases(ChargebackResolution resolution, Pageable pageable);

    void save(ChargebackCase chargeback);

    void refresh(ChargebackCase chargeback);
}

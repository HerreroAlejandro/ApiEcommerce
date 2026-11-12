package com.api.crud.repositories;

import com.api.crud.models.entity.PaymentRefundAttempt;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface PaymentRefundAttemptDao {
    Optional<PaymentRefundAttempt> getById(String id);

    Page<PaymentRefundAttempt> getByRefundId(String refundId, Pageable pageable);

    void save(PaymentRefundAttempt attempt);

    void refresh(PaymentRefundAttempt attempt);
}

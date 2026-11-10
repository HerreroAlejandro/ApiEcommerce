package com.api.crud.repositories;

import com.api.crud.models.entity.PaymentRefund;

import java.util.Optional;

public interface PaymentRefundDao {

    Optional<PaymentRefund> getByPaymentId(Long paymentId);

    Optional<PaymentRefund> getById(String id);

    void save(PaymentRefund refund);

    void refresh(PaymentRefund refund);
}

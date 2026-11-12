package com.api.crud.repositories;

import com.api.crud.models.entity.PaymentRefund;

import java.util.List;
import java.util.Optional;

public interface PaymentRefundDao {

    List<PaymentRefund> getAllByPaymentId(Long paymentId);

    Optional<PaymentRefund> getByPaymentId(Long paymentId);

    Optional<PaymentRefund> getById(String id);

    void save(PaymentRefund refund);

    void refresh(PaymentRefund refund);
}

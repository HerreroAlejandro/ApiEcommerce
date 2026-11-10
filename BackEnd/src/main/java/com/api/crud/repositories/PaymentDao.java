package com.api.crud.repositories;

import com.api.crud.models.entity.Payment;
import com.api.crud.models.entity.PaymentCheckout;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface PaymentDao {

    Optional<Payment> getById(Long paymentId);

    void refreshPayment(Payment payment);

    Optional<PaymentCheckout> getCheckoutByOrderId(Long orderId);

    Optional<PaymentCheckout> getCheckoutByReference(String reference);

    void saveCheckout(PaymentCheckout checkout);

    void refreshCheckout(PaymentCheckout checkout);

    Optional<Payment> getByProviderId(String paymentId);

    List<Payment> getByOrderId(Long orderId);

    void savePayment(Payment payment);

    Page<Payment> getReviewPayments(Pageable pageable);
}

package com.api.crud.services;

import com.api.crud.DTO.ItemRefundRequestDTO;
import com.api.crud.DTO.PaymentRefundAttemptResponseDTO;
import com.api.crud.DTO.PaymentRefundResponseDTO;
import com.api.crud.exceptions.ExternalServiceException;
import com.api.crud.exceptions.ResourceNotFoundException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PaymentRefundService {

    private static final Logger log = LoggerFactory.getLogger(PaymentRefundService.class);

    private final PaymentRefundPersistence persistence;
    private final MercadoPagoClient client;
    private final PaymentConfirmationService confirmations;

    public PaymentRefundService(
            PaymentRefundPersistence persistence,
            MercadoPagoClient client,
            PaymentConfirmationService confirmations) {
        this.persistence = persistence;
        this.client = client;
        this.confirmations = confirmations;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PaymentRefundResponseDTO refund(String email, Long paymentId, String reason) {
        String providerOrderId = persistence.getProviderOrderId(email, paymentId);
        synchronize(providerOrderId);
        String refundId = persistence.prepare(email, paymentId, reason);
        return dispatchAndRead(email, paymentId, providerOrderId, refundId);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PaymentRefundResponseDTO refundItems(
            String email, Long paymentId, ItemRefundRequestDTO request) {
        String providerOrderId = persistence.getProviderOrderId(email, paymentId);
        synchronize(providerOrderId);
        String refundId = persistence.prepareItems(email, paymentId, request);
        return dispatchAndRead(email, paymentId, providerOrderId, refundId);
    }

    @Transactional(readOnly = true)
    public List<PaymentRefundResponseDTO> getRefundHistory(String email, Long paymentId) {
        return persistence.getRefundHistory(email, paymentId);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PaymentRefundResponseDTO retry(
            String email, Long paymentId, String previousAttemptId, String reason) {
        String providerOrderId = persistence.getProviderOrderId(email, paymentId);
        synchronize(providerOrderId);
        String refundId = persistence.prepareRetry(email, paymentId, previousAttemptId, reason);
        return dispatchAndRead(email, paymentId, providerOrderId, refundId);
    }

    private PaymentRefundResponseDTO dispatchAndRead(
            String email, Long paymentId, String providerOrderId, String refundId) {
        persistence.dispatch(email, refundId);
        try {
            synchronize(providerOrderId);
        } catch (ExternalServiceException exception) {
            // Keep the recorded result if the follow-up synchronization is unavailable.
            log.warn("Reembolso {} pendiente de sincronizacion verificable", refundId);
        }
        return persistence.getRefundById(email, paymentId, refundId);
    }

    private void synchronize(String providerOrderId) {
        if (confirmations.applyOrder(client.getOrder(providerOrderId)) == null)
            throw new ResourceNotFoundException("El pago no pertenece a una compra de esta API");
    }

    @Transactional(readOnly = true)
    public PaymentRefundResponseDTO getRefund(String email, Long paymentId) {
        return persistence.getRefund(email, paymentId);
    }

    @Transactional(readOnly = true)
    public Page<PaymentRefundAttemptResponseDTO> getAttempts(
            String email, Long paymentId, Pageable pageable) {
        return persistence.getAttempts(email, paymentId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<PaymentRefundAttemptResponseDTO> getAttemptsForRefund(
            String email, Long paymentId, String refundId, Pageable pageable) {
        return persistence.getAttemptsForRefund(email, paymentId, refundId, pageable);
    }
}

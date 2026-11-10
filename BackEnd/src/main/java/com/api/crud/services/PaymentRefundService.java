package com.api.crud.services;

import com.api.crud.DTO.PaymentRefundResponseDTO;
import com.api.crud.exceptions.ExternalServiceException;
import com.api.crud.exceptions.ResourceNotFoundException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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
        persistence.dispatch(email, refundId);
        try {
            synchronize(providerOrderId);
        } catch (ExternalServiceException exception) {
            // The provider accepted it, but confirmation may arrive later through the webhook.
            log.warn("Reembolso {} aceptado, pendiente de confirmacion verificable", refundId);
        }
        return persistence.getRefund(email, paymentId);
    }

    private void synchronize(String providerOrderId) {
        if (confirmations.applyOrder(client.getOrder(providerOrderId)) == null)
            throw new ResourceNotFoundException("El pago no pertenece a una compra de esta API");
    }

    @Transactional(readOnly = true)
    public PaymentRefundResponseDTO getRefund(String email, Long paymentId) {
        return persistence.getRefund(email, paymentId);
    }
}

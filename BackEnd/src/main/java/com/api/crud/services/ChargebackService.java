package com.api.crud.services;

import com.api.crud.DTO.ChargebackResponseDTO;
import com.api.crud.DTO.PaymentResponseDTO;
import com.api.crud.exceptions.ResourceNotFoundException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ChargebackService {
    private final ChargebackPersistence persistence;
    private final MercadoPagoClient client;
    private final PaymentConfirmationService confirmations;

    public ChargebackService(
            ChargebackPersistence persistence,
            MercadoPagoClient client,
            PaymentConfirmationService confirmations) {
        this.persistence = persistence;
        this.client = client;
        this.confirmations = confirmations;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ChargebackResponseDTO synchronize(String email, Long id) {
        var details = persistence.getSyncDetails(email, id);
        requireOrder(details.providerOrderId());
        persistence.applyVerified(id, client.getChargeback(details.providerCaseId()));
        requireOrder(details.providerOrderId());
        return persistence.get(email, id);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public List<PaymentResponseDTO> synchronizeOrderCases(String providerOrderId) {
        var records = persistence.getOrderCases(providerOrderId);
        if (records.isEmpty()) return null;
        for (var record : records)
            persistence.applyVerified(record.id(), client.getChargeback(record.providerCaseId()));
        return requireOrder(providerOrderId);
    }

    private List<PaymentResponseDTO> requireOrder(String id) {
        List<PaymentResponseDTO> result = confirmations.applyOrder(client.getOrder(id));
        if (result == null)
            throw new ResourceNotFoundException(
                    "El contracargo no pertenece a una compra de esta API");
        return result;
    }
}

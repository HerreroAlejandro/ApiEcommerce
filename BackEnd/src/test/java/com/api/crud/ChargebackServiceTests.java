package com.api.crud;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.api.crud.DTO.ChargebackResponseDTO;
import com.api.crud.exceptions.*;
import com.api.crud.services.*;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

class ChargebackServiceTests {
    @Test
    void ordinaryOrdersPerformNoExtraProviderRequests() {
        var persistence = mock(ChargebackPersistence.class);
        var client = mock(MercadoPagoClient.class);
        var confirmations = mock(PaymentConfirmationService.class);
        var service = new ChargebackService(persistence, client, confirmations);
        assertNull(service.synchronizeOrderCases("ORDTST123"));
        verifyNoInteractions(client, confirmations);
    }

    @Test
    void authorizationPrecedesEveryExternalRequest() {
        var persistence = mock(ChargebackPersistence.class);
        when(persistence.getSyncDetails("client@example.com", 1L))
                .thenThrow(new ForbiddenOperationException());
        var client = mock(MercadoPagoClient.class);
        var confirmations = mock(PaymentConfirmationService.class);
        var service = new ChargebackService(persistence, client, confirmations);
        assertThrows(
                ForbiddenOperationException.class,
                () -> service.synchronize("client@example.com", 1L));
        verifyNoInteractions(client, confirmations);
    }

    @Test
    void providerCaseDecisionIsAppliedBetweenTwoCanonicalPaymentReads() {
        var persistence = mock(ChargebackPersistence.class);
        var details = new ChargebackPersistence.SyncDetails(1L, "9988", "ORDTST123");
        when(persistence.getSyncDetails("admin@example.com", 1L)).thenReturn(details);
        var response = new ChargebackResponseDTO();
        when(persistence.get("admin@example.com", 1L)).thenReturn(response);
        var client = mock(MercadoPagoClient.class);
        var remote =
                new MercadoPagoClient.VerifiedOrder(
                        "ORDTST123", "ref", "99", "ARS", BigDecimal.TEN, false, List.of());
        when(client.getOrder("ORDTST123")).thenReturn(remote);
        var result =
                new MercadoPagoClient.VerifiedChargeback(
                        "9988",
                        Set.of("123456"),
                        "ARS",
                        BigDecimal.TEN,
                        false,
                        true,
                        "valid",
                        "unauthorized",
                        Instant.now(),
                        null);
        when(client.getChargeback("9988")).thenReturn(result);
        var confirmations = mock(PaymentConfirmationService.class);
        when(confirmations.applyOrder(remote)).thenReturn(List.of());
        var service = new ChargebackService(persistence, client, confirmations);
        assertSame(response, service.synchronize("admin@example.com", 1L));
        var sequence = inOrder(persistence, client, confirmations);
        sequence.verify(persistence).getSyncDetails("admin@example.com", 1L);
        sequence.verify(client).getOrder("ORDTST123");
        sequence.verify(confirmations).applyOrder(remote);
        sequence.verify(client).getChargeback("9988");
        sequence.verify(persistence).applyVerified(1L, result);
        sequence.verify(client).getOrder("ORDTST123");
        sequence.verify(confirmations).applyOrder(remote);
        sequence.verify(persistence).get("admin@example.com", 1L);
    }
}

package com.api.crud;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.api.crud.config.MercadoPagoProperties;
import com.api.crud.controllers.MercadoPagoWebhookController;
import com.api.crud.exceptions.ExternalServiceException;
import com.api.crud.exceptions.GlobalExceptionHandler;
import com.api.crud.repositories.*;
import com.api.crud.services.*;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.modelmapper.ModelMapper;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

class PaymentWebhookEndpointTests {

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void unsignedNotificationsAreRejectedAndSignedBodiesCannotOverrideTheFetchedResource(
            boolean lowercaseSignature) throws Exception {
        MercadoPagoProperties properties = new MercadoPagoProperties();
        properties.setEnabled(true);
        properties.setWebhookSecret("test-secret");
        MercadoPagoClient client = mock(MercadoPagoClient.class);
        var canonical =
                new MercadoPagoClient.VerifiedOrder(
                        "ORDTST123", "ref", "99", "ARS", BigDecimal.TEN, false, List.of());
        when(client.getOrder("ORDTST123")).thenReturn(canonical);
        PaymentConfirmationService confirmations = mock(PaymentConfirmationService.class);
        PaymentService service =
                new PaymentService(
                        mock(PaymentCheckoutPersistence.class),
                        confirmations,
                        client,
                        new MercadoPagoWebhookVerifier(properties),
                        mock(PaymentDao.class),
                        mock(OrderDao.class),
                        mock(UserDao.class),
                        new ModelMapper(),
                        mock(ChargebackService.class));
        var mvc =
                MockMvcBuilders.standaloneSetup(new MercadoPagoWebhookController(service))
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .build();
        mvc.perform(post("/payments/mercadopago/webhook").param("data.id", "ORDTST123"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(client, confirmations);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("test-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature =
                "ts=1730000000000,v1="
                        + HexFormat.of()
                                .formatHex(
                                        mac.doFinal(
                                                ((lowercaseSignature
                                                                        ? "id:ordtst123;"
                                                                        : "id:ORDTST123;")
                                                                + "request-id:request-1;ts:1730000000000;")
                                                        .getBytes(StandardCharsets.UTF_8)));
        when(client.getOrder("ORDTST123"))
                .thenThrow(new ExternalServiceException("Proveedor temporalmente no disponible"))
                .thenReturn(canonical);
        mvc.perform(
                        post("/payments/mercadopago/webhook")
                                .param("data.id", "ORDTST123")
                                .param("type", "order")
                                .header("x-request-id", "request-1")
                                .header("x-signature", signature))
                .andExpect(status().isServiceUnavailable());
        verifyNoInteractions(confirmations);
        mvc.perform(
                        post("/payments/mercadopago/webhook")
                                .param("data.id", "ORDTST123")
                                .param("type", "order")
                                .header("x-request-id", "request-1")
                                .header("x-signature", signature)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"data\":{\"id\":\"999\"},\"status\":\"approved\",\"amount\":1}"))
                .andExpect(status().isOk());
        mvc.perform(
                        post("/payments/mercadopago/webhook")
                                .param("data.id", "ORDTST123")
                                .param("type", "chargebacks")
                                .header("x-request-id", "request-1")
                                .header("x-signature", signature)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"data\":{\"id\":\"forged-case\"},\"coverage_applied\":true}"))
                .andExpect(status().isOk());
        verify(client, times(3)).getOrder("ORDTST123");
        verify(client, never()).getOrder("999");
        verify(confirmations, times(2)).applyOrder(canonical);
    }
}

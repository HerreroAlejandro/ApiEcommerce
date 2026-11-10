package com.api.crud;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.api.crud.config.MercadoPagoProperties;
import com.api.crud.exceptions.UnauthenticatedException;
import com.api.crud.repositories.*;
import com.api.crud.services.*;

import org.junit.jupiter.api.Test;
import org.modelmapper.ModelMapper;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

class MercadoPagoWebhookTests {

    private MercadoPagoProperties properties() {
        MercadoPagoProperties properties = new MercadoPagoProperties();
        properties.setEnabled(true);
        properties.setWebhookSecret("test-secret-do-not-use");
        return properties;
    }

    private String signature(String id, String requestId) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(
                new SecretKeySpec(
                        "test-secret-do-not-use".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String message = "id:" + id + ";request-id:" + requestId + ";ts:1730000000000;";
        return "ts=1730000000000,v1="
                + HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void automaticAndSimulatedSignaturesAreAcceptedButAlteredResourcesAreRejected() throws Exception {
        var verifier = new MercadoPagoWebhookVerifier(properties());
        String received = signature("ordtst123", "request-1");
        Logger logger = (Logger) LoggerFactory.getLogger(MercadoPagoWebhookVerifier.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            verifier.verify("ORDTST123", "request-1", received);
            verifier.verify("ORDTST123", "request-1", signature("ORDTST123", "request-1"));
            assertTrue(appender.list.isEmpty());
            assertThrows(UnauthenticatedException.class,
                    () -> verifier.verify("ORDTST124", "request-1", received));
            String message = appender.list.get(0).getFormattedMessage();
            assertTrue(message.contains("FIRMA_NO_COINCIDE"));
            assertFalse(message.contains("test-secret-do-not-use"));
            assertFalse(message.contains(received));
            assertThrows(UnauthenticatedException.class,
                    () -> verifier.verify("ORDTST123", "request-2", received));
            String alteredTimestamp = received.replace("ts=1730000000000", "ts=1730000000001");
            assertThrows(UnauthenticatedException.class,
                    () -> verifier.verify("ORDTST123", "request-1", alteredTimestamp));
            MercadoPagoProperties otherSecret = properties();
            otherSecret.setWebhookSecret("different-secret");
            assertThrows(UnauthenticatedException.class,
                    () -> new MercadoPagoWebhookVerifier(otherSecret).verify("ORDTST123", "request-1", received));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void signedResourceAndRequestIdCannotBeChanged() throws Exception {
        MercadoPagoWebhookVerifier verifier = new MercadoPagoWebhookVerifier(properties());
        String signature = signature("ORDTST123", "request-1");
        verifier.verify("ORDTST123", "request-1", signature);
        assertThrows(
                UnauthenticatedException.class,
                () -> verifier.verify("ORDTST124", "request-1", signature));
        assertThrows(
                UnauthenticatedException.class,
                () -> verifier.verify("ORDTST123", "request-2", signature));
        assertThrows(
                UnauthenticatedException.class,
                () -> verifier.verify("ORDTST123", "request-1", null));
        assertThrows(
                UnauthenticatedException.class,
                () -> verifier.verify("ORDTST123", "request-1", signature + ",ts=99"));
    }

    @Test
    void missingWebhookSecretFailsClosedEvenWhenCheckoutIsEnabled() throws Exception {
        MercadoPagoProperties configuration = properties();
        configuration.setWebhookSecret("");
        var verifier = new MercadoPagoWebhookVerifier(configuration);
        String signature = signature("ORDTST123", "request-1");
        assertThrows(
                UnauthenticatedException.class,
                () -> verifier.verify("ORDTST123", "request-1", signature));
    }

    @Test
    void invalidSignatureNeverFetchesOrConfirmsAPayment() {
        MercadoPagoClient client = mock(MercadoPagoClient.class);
        PaymentConfirmationService confirmations = mock(PaymentConfirmationService.class);
        PaymentService service =
                new PaymentService(
                        mock(PaymentCheckoutPersistence.class),
                        confirmations,
                        client,
                        new MercadoPagoWebhookVerifier(properties()),
                        mock(PaymentDao.class),
                        mock(OrderDao.class),
                        mock(UserDao.class),
                        new ModelMapper());
        assertThrows(
                UnauthenticatedException.class,
                () -> service.webhook("ORDTST123", "request-1", "invalid"));
        verifyNoInteractions(client, confirmations);
    }
}

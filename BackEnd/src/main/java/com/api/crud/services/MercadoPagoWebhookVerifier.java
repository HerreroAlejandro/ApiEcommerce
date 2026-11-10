package com.api.crud.services;

import com.api.crud.config.MercadoPagoProperties;
import com.api.crud.exceptions.UnauthenticatedException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Locale;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

// comprueba la firma de los avisos.
@Component
public class MercadoPagoWebhookVerifier {

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoWebhookVerifier.class);

    private final MercadoPagoProperties properties;

    public MercadoPagoWebhookVerifier(MercadoPagoProperties properties) {
        this.properties = properties;
    }

    public void verify(String dataId, String requestId, String signature) {
        properties.requireEnabled();
        if (properties.getWebhookSecret().isBlank()) throw invalid("FIRMA_NO_CONFIGURADA");
        if (dataId == null) throw invalid("DATA_ID_AUSENTE");
        if (!dataId.matches("ORD[A-Za-z0-9]{1,61}")) throw invalid("DATA_ID_INVALIDO");
        if (requestId == null) throw invalid("REQUEST_ID_AUSENTE");
        if (!requestId.matches("[A-Za-z0-9-]{1,128}")) throw invalid("REQUEST_ID_INVALIDO");
        if (signature == null) throw invalid("SIGNATURE_AUSENTE");
        if (signature.length() > 512) throw invalid("SIGNATURE_INVALIDA");
        Map<String, String> parts = new HashMap<>();
        for (String part : signature.split(",")) {
            String[] pair = part.trim().split("=", 2);
            if (pair.length != 2 || parts.putIfAbsent(pair[0].trim(), pair[1].trim()) != null)
                throw invalid("SIGNATURE_MALFORMADA");
        }
        String timestamp = parts.get("ts");
        String received = parts.get("v1");
        if (timestamp == null
                || !timestamp.matches("[0-9]{1,17}")
                || received == null
                || !received.matches("[a-fA-F0-9]{64}")) throw invalid("SIGNATURE_MALFORMADA");
        String message = "id:" + dataId + ";request-id:" + requestId + ";ts:" + timestamp + ";";
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(
                    new SecretKeySpec(
                            properties.getWebhookSecret().getBytes(StandardCharsets.UTF_8),
                            "HmacSHA256"));
            byte[] expected = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            byte[] receivedBytes = HexFormat.of().parseHex(received);
            if (!MessageDigest.isEqual(expected, receivedBytes)) {
                // Automatic notifications sign a lowercase ID; the simulator also signs the
                // original representation. Both must match the same secret, request ID and ts.
                String lowercaseMessage = "id:" + dataId.toLowerCase(Locale.ROOT)
                        + ";request-id:" + requestId + ";ts:" + timestamp + ";";
                boolean lowercaseMatches = MessageDigest.isEqual(
                        mac.doFinal(lowercaseMessage.getBytes(StandardCharsets.UTF_8)), receivedBytes);
                if (!lowercaseMatches) throw invalid("FIRMA_NO_COINCIDE");
            }
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("No se pudo verificar la firma del pago", exception);
        }
    }

    private UnauthenticatedException invalid(String reason) {
        log.warn("Webhook Mercado Pago rechazado: motivo={}", reason);
        return new UnauthenticatedException("Notificacion de pago no autenticada");
    }
}

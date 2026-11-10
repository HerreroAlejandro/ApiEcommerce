package com.api.crud.config;

import com.api.crud.exceptions.ExternalServiceException;

import jakarta.annotation.PostConstruct;

import lombok.Getter;
import lombok.Setter;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Currency;

@Component
@ConfigurationProperties(prefix = "payments.mercadopago")
public class MercadoPagoProperties {

    @Getter
    @Setter
    private boolean enabled;

    @Getter
    @Setter
    private String accessToken = "";

    @Getter
    @Setter
    private String webhookSecret = "";

    @Getter
    @Setter
    private String collectorId = "";

    @Getter
    @Setter
    private String currency = "ARS";

    @Getter
    @Setter
    private boolean liveMode;

    @Getter
    @Setter
    private String notificationUrl = "";

    @Getter
    @Setter
    private String returnUrl = "";

    @PostConstruct
    void validateConfiguration() {
        if (!enabled) return;
        if (accessToken.isBlank() || !collectorId.matches("[0-9]{1,30}")) {
            throw new IllegalStateException(
                    "Faltan credenciales o identificador de vendedor de Mercado Pago");
        }
        // In development, checkout creation and ADMIN synchronization can be tested first.
        // Live payments must have verified notifications configured before startup.
        if (liveMode && (webhookSecret.isBlank() || notificationUrl.isBlank())) {
            throw new IllegalStateException(
                    "Produccion requiere Webhooks de Mercado Pago configurados");
        }
        if (!notificationUrl.isBlank()) requireHttps(notificationUrl);
        if (!returnUrl.isBlank()) {
            requireHttps(returnUrl);
            URI uri = URI.create(returnUrl);
            if (uri.getRawQuery() != null)
                throw new IllegalStateException("La URL de retorno no debe tener parametros");
        }
        Currency.getInstance(currency);
    }

    private void requireHttps(String value) {
        URI uri = URI.create(value);
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || uri.getHost() == null
                || uri.getUserInfo() != null
                || uri.getFragment() != null) {
            throw new IllegalStateException("Mercado Pago requiere URLs HTTPS validas");
        }
    }

    public void requireEnabled() {
        if (!enabled)
            throw new ExternalServiceException("El pago online todavia no esta configurado");
    }
}

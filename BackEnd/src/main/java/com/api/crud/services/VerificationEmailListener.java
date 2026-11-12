package com.api.crud.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@Component
public class VerificationEmailListener {
    private static final Logger logger = LoggerFactory.getLogger(VerificationEmailListener.class);
    private final EmailService email;
    private final String frontendUrl;

    public VerificationEmailListener(
            EmailService email,
            @Value("${accounts.verification.frontend-url:}") String frontendUrl) {
        this.email = email;
        this.frontendUrl = frontendUrl.trim();
        if (!this.frontendUrl.isEmpty()) {
            URI uri = URI.create(this.frontendUrl);
            if (!("http".equalsIgnoreCase(uri.getScheme())
                            || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null
                    || uri.getUserInfo() != null
                    || uri.getQuery() != null
                    || uri.getFragment() != null) {
                throw new IllegalArgumentException(
                        "La URL de verificacion debe ser HTTP/HTTPS sin parametros");
            }
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void send(EmailVerificationService.VerificationEmail event) {
        String destination =
                frontendUrl.isEmpty()
                        ? "Ingresa este token en la confirmacion de correo: " + event.token()
                        : "Confirma tu correo en: "
                                + UriComponentsBuilder.fromUriString(frontendUrl)
                                        .queryParam("token", event.token())
                                        .build()
                                        .encode()
                                        .toUriString();
        try {
            if (!email.sendEmail(
                    event.email(),
                    "Verifica tu correo",
                    destination + "\nEl permiso vence en 30 minutos y se utiliza una sola vez.")) {
                logger.warn("No se pudo enviar la verificacion; la cuenta permanece pendiente");
            }
        } catch (RuntimeException exception) {
            // The account/token are already committed; never undo registration for an SMTP failure.
            logger.warn("No se pudo enviar la verificacion; puede solicitarse un reenvio");
        }
    }
}

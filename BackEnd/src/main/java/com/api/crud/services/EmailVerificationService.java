package com.api.crud.services;

import com.api.crud.exceptions.InvalidRequestException;
import com.api.crud.models.entity.*;
import com.api.crud.models.enums.AccountStatus;
import com.api.crud.repositories.*;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class EmailVerificationService {
    private final UserDao users;
    private final AccountStatusDao statuses;
    private final EmailVerificationTokenDao tokens;
    private final ApplicationEventPublisher events;
    private final SecureRandom random = new SecureRandom();

    public EmailVerificationService(
            UserDao users,
            AccountStatusDao statuses,
            EmailVerificationTokenDao tokens,
            ApplicationEventPublisher events) {
        this.users = users;
        this.statuses = statuses;
        this.tokens = tokens;
        this.events = events;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void prepareRegistration(UserModel user) {
        issue(user, LocalDateTime.now());
    }

    @Transactional
    public void resend(String email) {
        var existing = users.getUserByEmail(email);
        if (existing.isEmpty()) return;
        UserModel user = statuses.lockUser(existing.get().getId());
        if (user.getStatus() != AccountStatus.PENDING_VERIFICATION || user.isEmailVerified())
            return;
        issue(user, LocalDateTime.now());
    }

    private void issue(UserModel user, LocalDateTime now) {
        EmailVerificationToken token = tokens.getByUserId(user.getId()).orElse(null);
        boolean newToken = token == null;
        if (token != null) {
            if (token.getRequestedAt().plusSeconds(60).isAfter(now)) return;
            if (token.getWindowStartedAt().plusHours(1).isAfter(now)
                    && token.getRequestCount() >= 5) return;
        } else {
            token = new EmailVerificationToken();
            token.setUser(user);
            token.setUserId(user.getId());
        }
        if (token.getWindowStartedAt() == null
                || !token.getWindowStartedAt().plusHours(1).isAfter(now)) {
            token.setWindowStartedAt(now);
            token.setRequestCount(0);
        }
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        token.setTokenHash(hash(value));
        token.setRequestedAt(now);
        token.setExpiresAt(now.plusMinutes(30));
        token.setUsedAt(null);
        token.setRequestCount(token.getRequestCount() + 1);
        if (newToken) tokens.save(token);
        events.publishEvent(new VerificationEmail(user.getEmail(), value));
    }

    @Transactional
    public void confirm(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_-]{43}")) throw invalidToken();
        String hash = hash(value);
        Long userId = tokens.getUserIdByTokenHash(hash).orElseThrow(this::invalidToken);
        UserModel user = statuses.lockUser(userId);
        EmailVerificationToken token = tokens.getByUserId(userId).orElseThrow(this::invalidToken);
        LocalDateTime now = LocalDateTime.now();
        if (user.getStatus() != AccountStatus.PENDING_VERIFICATION
                || user.isEmailVerified()
                || !hash.equals(token.getTokenHash())
                || token.getUsedAt() != null
                || !token.getExpiresAt().isAfter(now)) throw invalidToken();
        token.setUsedAt(now);
        AccountStatusAudit audit = new AccountStatusAudit();
        audit.setUserId(userId);
        audit.setActorUserId(userId);
        audit.setPreviousStatus(user.getStatus());
        audit.setNewStatus(AccountStatus.ACTIVE);
        audit.setCreatedAt(now);
        audit.setSource("EMAIL_VERIFICATION");
        audit.setReason("Direccion de correo verificada mediante token");
        user.setEmailVerified(true);
        user.setStatus(AccountStatus.ACTIVE);
        user.setAuthVersion(Math.incrementExact(user.getAuthVersion()));
        users.update(user);
        statuses.save(audit);
    }

    private InvalidRequestException invalidToken() {
        return new InvalidRequestException(
                "El token o el estado de la cuenta no permiten verificar el correo");
    }

    private String hash(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public record VerificationEmail(String email, String token) {
        @Override
        public String toString() {
            return "VerificationEmail[redacted]";
        }
    }
}

package com.api.crud.services;

import com.api.crud.DTO.AccountStatusAuditResponseDTO;
import com.api.crud.models.entity.*;
import com.api.crud.models.enums.AccountStatus;
import com.api.crud.repositories.*;

import org.modelmapper.ModelMapper;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class AccountStatusService {

    private final UserDao users;

    private final AccountStatusDao statuses;

    private final AccountRecoveryTokenDao tokens;

    private final EmailService email;

    private final ModelMapper mapper;

    public AccountStatusService(
            UserDao users,
            AccountStatusDao statuses,
            AccountRecoveryTokenDao tokens,
            EmailService email,
            ModelMapper mapper) {
        this.users = users;
        this.statuses = statuses;
        this.tokens = tokens;
        this.email = email;
        this.mapper = mapper;
    }

    private UserModel actor(String email) {
        UserModel actor =
                users.getUserByEmail(email)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (actor.getStatus() != AccountStatus.ACTIVE)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return actor;
    }

    private UserModel admin(String email) {
        UserModel actor = actor(email);
        if (!actor.getRoleNames().contains("ADMIN"))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return actor;
    }

    private String reason(String reason) {
        if (reason == null || reason.isBlank() || reason.length() > 500) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Debe indicar un motivo de hasta 500 caracteres");
        }
        return reason.trim();
    }

    private void change(
            UserModel user, long actorId, AccountStatus next, String reason, String source) {
        AccountStatusAudit audit = new AccountStatusAudit();
        audit.setUserId(user.getId());
        audit.setActorUserId(actorId);
        audit.setPreviousStatus(user.getStatus());
        audit.setNewStatus(next);
        audit.setReason(reason);
        audit.setSource(source);
        audit.setCreatedAt(LocalDateTime.now());
        user.setStatus(next);
        user.setAuthVersion(Math.incrementExact(user.getAuthVersion()));
        users.update(user);
        tokens.invalidateTokensByUser(user);
        statuses.save(audit);
    }

    @Transactional
    public void deactivateMyAccount(String email) {
        UserModel actor = actor(email);
        UserModel user = statuses.lockUser(actor.getId());
        if (user.getStatus() != AccountStatus.ACTIVE)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cuenta no activa");
        change(
                user,
                user.getId(),
                AccountStatus.DEACTIVATED,
                "Baja voluntaria del titular",
                "SELF_SERVICE");
    }

    @Transactional
    public void block(String email, Long userId, String reason) {
        String motive = reason(reason);
        UserModel actor = admin(email);
        if (actor.getId() == userId)
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "No puede bloquear su propia cuenta");
        UserModel user = statuses.lockUser(userId);
        if (user.getStatus() == AccountStatus.BLOCKED) return;
        change(user, actor.getId(), AccountStatus.BLOCKED, motive, "ADMIN_BLOCK");
    }

    @Transactional
    public void unblock(String email, Long userId, String reason) {
        restoreByAdmin(email, userId, reason, AccountStatus.BLOCKED, "ADMIN_UNBLOCK");
    }

    @Transactional
    public void activate(String email, Long userId, String reason) {
        restoreByAdmin(email, userId, reason, AccountStatus.DEACTIVATED, "ADMIN_ACTIVATE");
    }

    private void restoreByAdmin(
            String email, Long userId, String reason, AccountStatus expected, String source) {
        String motive = reason(reason);
        UserModel actor = admin(email);
        UserModel user = statuses.lockUser(userId);
        if (user.getStatus() != expected) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Estado de cuenta incompatible con esta operacion");
        }
        change(user, actor.getId(), AccountStatus.ACTIVE, motive, source);
    }

    @Transactional
    public void requestRecovery(String emailAddress) {
        var existing = users.getUserByEmail(emailAddress);
        if (existing.isEmpty()) return;
        UserModel user = statuses.lockUser(existing.get().getId());
        if (user.getStatus() != AccountStatus.DEACTIVATED) return;
        tokens.invalidateTokensByUser(user);
        AccountRecoveryToken token = new AccountRecoveryToken();
        token.setUser(user);
        token.setToken(UUID.randomUUID().toString());
        token.setUsed(false);
        token.setCreatedAt(LocalDateTime.now());
        token.setExpirationDate(LocalDateTime.now().plusMinutes(30));
        tokens.save(token);
        if (!email.sendEmail(
                emailAddress,
                "Account recovery",
                "Use the following token to reactivate your account: " + token.getToken())) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "No se pudo enviar el correo de recuperacion");
        }
    }

    @Transactional
    public void activateWithRecoveryToken(String value) {
        AccountRecoveryToken token =
                tokens.getByToken(value)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.BAD_REQUEST,
                                                "Token de recuperacion invalido"));
        UserModel user = statuses.lockUser(token.getUser().getId());
        statuses.lockToken(token);
        if (user.getStatus() != AccountStatus.DEACTIVATED
                || token.isUsed()
                || !token.getExpirationDate().isAfter(LocalDateTime.now())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "La cuenta o el token no permiten la recuperacion");
        }
        token.setUsed(true);
        tokens.update(token);
        change(
                user,
                user.getId(),
                AccountStatus.ACTIVE,
                "Reactivacion mediante token de correo",
                "EMAIL_RECOVERY");
    }

    @Transactional(readOnly = true)
    public Page<AccountStatusAuditResponseDTO> getHistory(Long userId, Pageable pageable) {
        users.getUserById(userId)
                .orElseThrow(
                        () ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND, "Usuario no encontrado"));
        return statuses.getHistory(userId, pageable)
                .map(audit -> mapper.map(audit, AccountStatusAuditResponseDTO.class));
    }
}

package com.api.crud;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.api.crud.exceptions.*;
import com.api.crud.models.entity.*;
import com.api.crud.models.enums.AccountStatus;
import com.api.crud.repositories.*;
import com.api.crud.services.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.modelmapper.ModelMapper;

import java.time.LocalDateTime;
import java.util.Optional;

class AccountStatusServiceTests {

    private AccountStatusService service;

    private UserDao users;

    private AccountStatusDao statuses;

    private AccountRecoveryTokenDao tokens;

    private EmailService email;

    private UserModel admin;

    private UserModel target;

    @BeforeEach
    void setUp() {
        users = mock(UserDao.class);
        statuses = mock(AccountStatusDao.class);
        tokens = mock(AccountRecoveryTokenDao.class);
        email = mock(EmailService.class);
        service = new AccountStatusService(users, statuses, tokens, email, new ModelMapper());
        admin = new UserModel();
        admin.setId(1L);
        Role role = new Role();
        role.setNameRole("ADMIN");
        admin.getRoles().add(role);
        target = new UserModel();
        target.setId(7L);
        when(users.getUserByEmail("admin@example.com")).thenReturn(Optional.of(admin));
        when(users.getUserByEmail("client@example.com")).thenReturn(Optional.of(target));
        when(statuses.lockUser(7L)).thenReturn(target);
        when(email.sendEmail(anyString(), anyString(), anyString())).thenReturn(true);
    }

    private AccountRecoveryToken recoveryToken() {
        AccountRecoveryToken token = new AccountRecoveryToken();
        token.setUser(target);
        token.setExpirationDate(LocalDateTime.now().plusMinutes(20));
        token.setUsed(false);
        when(tokens.getByToken("token")).thenReturn(Optional.of(token));
        return token;
    }

    @Test
    void voluntaryDeactivationAuditsAndAllowsRecovery() {
        service.deactivateMyAccount("client@example.com");
        assertEquals(AccountStatus.DEACTIVATED, target.getStatus());
        var capture = ArgumentCaptor.forClass(AccountStatusAudit.class);
        verify(statuses).save(capture.capture());
        assertEquals(AccountStatus.ACTIVE, capture.getValue().getPreviousStatus());
        assertEquals("SELF_SERVICE", capture.getValue().getSource());
        assertEquals(7L, capture.getValue().getActorUserId());
        service.requestRecovery("client@example.com");
        verify(tokens).save(any(AccountRecoveryToken.class));
        verify(email).sendEmail(eq("client@example.com"), anyString(), anyString());
    }

    @Test
    void blockedAccountsReceiveNoRecoveryTokenOrEmail() {
        service.block("admin@example.com", 7L, "Revision de seguridad");
        assertEquals(AccountStatus.BLOCKED, target.getStatus());
        service.requestRecovery("client@example.com");
        verify(tokens, never()).save(any());
        verifyNoInteractions(email);
        var capture = ArgumentCaptor.forClass(AccountStatusAudit.class);
        verify(statuses).save(capture.capture());
        assertEquals(1L, capture.getValue().getActorUserId());
        assertEquals("Revision de seguridad", capture.getValue().getReason());
        verify(tokens).invalidateTokensByUser(target);
    }

    @Test
    void tokenIssuedBeforeBlockCannotReactivateBlockedAccount() {
        target.setStatus(AccountStatus.DEACTIVATED);
        recoveryToken();
        service.block("admin@example.com", 7L, "Bloqueo");
        assertThrows(ApplicationException.class, () -> service.activateWithRecoveryToken("token"));
        assertEquals(AccountStatus.BLOCKED, target.getStatus());
        verify(tokens, never()).update(any());
    }

    @Test
    void activateCannotBypassBlockButUnblockCan() {
        target.setStatus(AccountStatus.BLOCKED);
        assertEquals(
                409,
                ExceptionProblems.status(
                                assertThrows(
                                        ApplicationException.class,
                                        () ->
                                                service.activate(
                                                        "admin@example.com", 7L, "Reactivar")))
                        .value());
        service.unblock("admin@example.com", 7L, "Revision aprobada");
        assertEquals(AccountStatus.ACTIVE, target.getStatus());
    }

    @Test
    void recoveryActivatesOnlyVoluntarilyDeactivatedAndConsumesToken() {
        target.setStatus(AccountStatus.DEACTIVATED);
        AccountRecoveryToken token = recoveryToken();
        service.activateWithRecoveryToken("token");
        assertEquals(AccountStatus.ACTIVE, target.getStatus());
        assertTrue(token.isUsed());
        assertThrows(ApplicationException.class, () -> service.activateWithRecoveryToken("token"));
        verify(statuses, times(1)).save(any());
    }

    @Test
    void expiredOrUsedTokenCannotReactivate() {
        target.setStatus(AccountStatus.DEACTIVATED);
        AccountRecoveryToken token = recoveryToken();
        token.setExpirationDate(LocalDateTime.now().minusMinutes(1));
        assertThrows(ApplicationException.class, () -> service.activateWithRecoveryToken("token"));
        token.setExpirationDate(LocalDateTime.now().plusMinutes(1));
        token.setUsed(true);
        assertThrows(ApplicationException.class, () -> service.activateWithRecoveryToken("token"));
        assertEquals(AccountStatus.DEACTIVATED, target.getStatus());
        verify(statuses, never()).save(any());
    }

    @Test
    void staffWithoutAdminCannotChangeStatusAndSelfBlockIsRejected() {
        assertEquals(
                403,
                ExceptionProblems.status(
                                assertThrows(
                                        ApplicationException.class,
                                        () -> service.block("client@example.com", 7L, "Motivo")))
                        .value());
        assertEquals(
                409,
                ExceptionProblems.status(
                                assertThrows(
                                        ApplicationException.class,
                                        () -> service.block("admin@example.com", 1L, "Motivo")))
                        .value());
        assertThrows(ApplicationException.class, () -> service.block("admin@example.com", 7L, " "));
        verify(statuses, never()).save(any());
    }

    @Test
    void unknownAndActiveAccountsDoNotReceiveRecovery() {
        service.requestRecovery("unknown@example.com");
        service.requestRecovery("client@example.com");
        verify(tokens, never()).save(any());
        verifyNoInteractions(email);
    }
}

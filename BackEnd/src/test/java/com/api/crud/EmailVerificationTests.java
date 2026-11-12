package com.api.crud;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.api.crud.DTO.*;
import com.api.crud.config.ModelMapperConfig;
import com.api.crud.exceptions.*;
import com.api.crud.models.entity.*;
import com.api.crud.models.enums.AccountStatus;
import com.api.crud.repositories.*;
import com.api.crud.services.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionalEventListenerFactory;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Optional;

class EmailVerificationTests {
    private final UserDao users = mock(UserDao.class);
    private final AccountStatusDao statuses = mock(AccountStatusDao.class);
    private final EmailVerificationTokenDao tokens = mock(EmailVerificationTokenDao.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final EmailVerificationService service =
            new EmailVerificationService(users, statuses, tokens, events);
    private UserModel user;
    private EmailVerificationToken stored;
    private String raw;

    @BeforeEach
    void setup() {
        user = new UserModel();
        user.setId(7L);
        user.setEmail("client@example.com");
        user.setStatus(AccountStatus.PENDING_VERIFICATION);
        user.setEmailVerified(false);
        when(users.getUserByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(statuses.lockUser(7L)).thenReturn(user);
        when(tokens.getByUserId(7L)).thenAnswer(call -> Optional.ofNullable(stored));
        doAnswer(
                        call -> {
                            stored = call.getArgument(0);
                            return null;
                        })
                .when(tokens)
                .save(any());
        doAnswer(
                        call -> {
                            EmailVerificationService.VerificationEmail event = call.getArgument(0);
                            raw = event.token();
                            return null;
                        })
                .when(events)
                .publishEvent(any(Object.class));
    }

    @Test
    void confirmationActivatesOnlyOnceWithHashedExpiringTokenAndAudit() {
        service.prepareRegistration(user);
        assertNotEquals(raw, stored.getTokenHash());
        assertEquals(64, stored.getTokenHash().length());
        assertTrue(stored.getExpiresAt().isAfter(LocalDateTime.now().plusMinutes(29)));
        when(tokens.getUserIdByTokenHash(stored.getTokenHash())).thenReturn(Optional.of(7L));
        service.confirm(raw);
        assertEquals(AccountStatus.ACTIVE, user.getStatus());
        assertTrue(user.isEmailVerified());
        assertNotNull(stored.getUsedAt());
        assertEquals(1, user.getAuthVersion());
        assertThrows(InvalidRequestException.class, () -> service.confirm(raw));
        verify(statuses, times(1)).save(any(AccountStatusAudit.class));
    }

    @Test
    void expiredAndBlockedAndDeactivatedAccountsCannotBeVerified() {
        service.prepareRegistration(user);
        when(tokens.getUserIdByTokenHash(stored.getTokenHash())).thenReturn(Optional.of(7L));
        stored.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        assertThrows(InvalidRequestException.class, () -> service.confirm(raw));
        stored.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        for (AccountStatus status :
                new AccountStatus[] {AccountStatus.BLOCKED, AccountStatus.DEACTIVATED}) {
            user.setStatus(status);
            assertThrows(InvalidRequestException.class, () -> service.confirm(raw));
        }
        assertFalse(user.isEmailVerified());
        verify(users, never()).update(any());
    }

    @Test
    void resendRotatesTokenAndLimitsFrequencyAndHourlyCount() {
        service.prepareRegistration(user);
        String first = raw;
        when(tokens.getUserIdByTokenHash(stored.getTokenHash())).thenReturn(Optional.of(7L));
        service.resend(user.getEmail());
        assertEquals(first, raw);
        stored.setRequestedAt(LocalDateTime.now().minusMinutes(2));
        service.resend(user.getEmail());
        assertNotEquals(first, raw);
        assertThrows(InvalidRequestException.class, () -> service.confirm(first));
        String second = raw;
        stored.setRequestedAt(LocalDateTime.now().minusMinutes(2));
        stored.setRequestCount(5);
        service.resend(user.getEmail());
        assertEquals(second, raw);
        stored.setWindowStartedAt(LocalDateTime.now().minusHours(2));
        service.resend(user.getEmail());
        assertNotEquals(second, raw);
        assertEquals(1, stored.getRequestCount());
        verify(tokens, times(1)).save(any());
    }

    @Test
    void unknownOrAlreadyActiveAccountsGenerateNoEmail() {
        service.resend("unknown@example.com");
        user.setStatus(AccountStatus.ACTIVE);
        user.setEmailVerified(true);
        service.resend(user.getEmail());
        verifyNoInteractions(events);
        assertThrows(InvalidRequestException.class, () -> service.confirm("bad"));
    }

    @Test
    void registrationPersistsPendingAccountWithEncodedPasswordAndPreparesVerification() {
        UserService registration = new UserService();
        RoleDao roles = mock(RoleDao.class);
        Role role = new Role();
        role.setNameRole("CLIENT");
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(roles.getRoleByName("CLIENT")).thenReturn(Optional.of(role));
        when(encoder.encode("Password123")).thenReturn("encoded-password");
        when(users.getUserByEmail("new@example.com")).thenReturn(Optional.empty());
        EmailVerificationService verification = mock(EmailVerificationService.class);
        ReflectionTestUtils.setField(registration, "userDao", users);
        ReflectionTestUtils.setField(registration, "roleDao", roles);
        ReflectionTestUtils.setField(registration, "passwordEncoder", encoder);
        ReflectionTestUtils.setField(
                registration, "modelMapper", new ModelMapperConfig().modelMapper());
        ReflectionTestUtils.setField(registration, "emailVerificationService", verification);
        UserRegisterDTO request = new UserRegisterDTO();
        request.setEmail("new@example.com");
        request.setPassword("Password123");
        registration.register(request);
        ArgumentCaptor<UserModel> capture = ArgumentCaptor.forClass(UserModel.class);
        verify(users).register(capture.capture());
        UserModel created = capture.getValue();
        assertEquals(AccountStatus.PENDING_VERIFICATION, created.getStatus());
        assertFalse(created.isEmailVerified());
        assertEquals("encoded-password", created.getPassword());
        verify(verification).prepareRegistration(created);
    }

    @Test
    void smtpFailureAfterCommitDoesNotFailTheRegistrationResponse() {
        EmailService email = mock(EmailService.class);
        doThrow(new ExternalServiceException("SMTP unavailable"))
                .when(email)
                .sendEmail(anyString(), anyString(), anyString());
        VerificationEmailListener listener = new VerificationEmailListener(email, "");
        assertDoesNotThrow(
                () ->
                        listener.send(
                                new EmailVerificationService.VerificationEmail(
                                        user.getEmail(), "secret")));
    }

    @Test
    void pendingAccountCannotLoginAndAdministrativeUnblockDoesNotVerifyEmail() {
        UserService login = new UserService();
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        ReflectionTestUtils.setField(login, "userDao", users);
        ReflectionTestUtils.setField(login, "passwordEncoder", encoder);
        when(encoder.matches(anyString(), any())).thenReturn(true);
        LoginRequestDTO request = new LoginRequestDTO();
        request.setEmail(user.getEmail());
        request.setPassword("Password123");
        assertThrows(ForbiddenOperationException.class, () -> login.login(request));
        UserModel admin = new UserModel();
        admin.setId(1L);
        Role role = new Role();
        role.setNameRole("ADMIN");
        admin.getRoles().add(role);
        when(users.getUserByEmail("admin@example.com")).thenReturn(Optional.of(admin));
        user.setStatus(AccountStatus.BLOCKED);
        new AccountStatusService(
                        users,
                        statuses,
                        mock(AccountRecoveryTokenDao.class),
                        mock(EmailService.class),
                        new ModelMapperConfig().modelMapper())
                .unblock("admin@example.com", 7L, "Revision completada");
        assertEquals(AccountStatus.PENDING_VERIFICATION, user.getStatus());
        assertFalse(user.isEmailVerified());
    }

    @Test
    void verificationEmailRunsOnlyAfterCommitAndNeverAfterRollback() {
        EmailService email = mock(EmailService.class);
        when(email.sendEmail(anyString(), anyString(), anyString())).thenReturn(true);
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(TransactionalEventListenerFactory.class);
            context.registerBean(
                    VerificationEmailListener.class,
                    () ->
                            new VerificationEmailListener(
                                    email, "http://localhost:5173/verify-email"));
            context.refresh();
            var manager =
                    new AbstractPlatformTransactionManager() {
                        protected Object doGetTransaction() {
                            return new Object();
                        }

                        protected void doBegin(
                                Object transaction, TransactionDefinition definition) {}

                        protected void doCommit(DefaultTransactionStatus status) {}

                        protected void doRollback(DefaultTransactionStatus status) {}
                    };
            var transaction = new TransactionTemplate(manager);
            transaction.executeWithoutResult(
                    status -> {
                        context.publishEvent(
                                new EmailVerificationService.VerificationEmail(
                                        user.getEmail(), "first"));
                        verifyNoInteractions(email);
                        status.setRollbackOnly();
                    });
            verifyNoInteractions(email);
            transaction.executeWithoutResult(
                    status -> {
                        context.publishEvent(
                                new EmailVerificationService.VerificationEmail(
                                        user.getEmail(), "second"));
                        verifyNoInteractions(email);
                    });
            verify(email)
                    .sendEmail(
                            eq(user.getEmail()),
                            anyString(),
                            contains("http://localhost:5173/verify-email?token=second"));
        }
    }
}

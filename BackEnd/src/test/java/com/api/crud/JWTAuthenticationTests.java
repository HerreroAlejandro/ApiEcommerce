package com.api.crud;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.api.crud.DTO.PasswordChangeRequestDTO;
import com.api.crud.DTO.PasswordResetDTO;
import com.api.crud.config.JWTFilter;
import com.api.crud.config.JWTUtil;
import com.api.crud.models.entity.PasswordResetToken;
import com.api.crud.models.entity.UserModel;
import com.api.crud.repositories.PasswordResetTokenDao;
import com.api.crud.repositories.UserDao;
import com.api.crud.services.UserService;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

import org.junit.jupiter.api.*;
import org.springframework.mock.web.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

class JWTAuthenticationTests {

    private static final String TEST_SECRET = "jwt-test-only-key-0123456789-abcdef-0123456789";

    private JWTUtil jwt;

    private JWTFilter filter;

    private UserModel user;

    private UserService service;

    private PasswordResetTokenDao resetTokens;

    @BeforeEach
    void setUp() {
        jwt = new JWTUtil(TEST_SECRET);
        user = new UserModel();
        user.setId(7L);
        user.setEmail("client@example.com");
        user.setPassword("old-hash");
        UserDao users = mock(UserDao.class);
        when(users.getUserByEmail("client@example.com")).thenReturn(Optional.of(user));
        when(users.getUserById(7L)).thenReturn(Optional.of(user));
        filter = new JWTFilter(jwt, users);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.matches("old-password", "old-hash")).thenReturn(true);
        when(encoder.encode("new-password")).thenReturn("new-hash");
        service = new UserService();
        ReflectionTestUtils.setField(service, "userDao", users);
        ReflectionTestUtils.setField(service, "passwordEncoder", encoder);
        resetTokens = mock(PasswordResetTokenDao.class);
        ReflectionTestUtils.setField(service, "passwordResetTokenDao", resetTokens);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private boolean accepted(String token) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        return SecurityContextHolder.getContext().getAuthentication() != null;
    }

    private String token() {
        return jwt.generateToken(user.getEmail(), List.of("CLIENT"), user.getAuthVersion());
    }

    @Test
    void configuredKeySignsTokenAndWrongKeyIsRejected() throws Exception {
        String token = token();
        assertTrue(accepted(token));
        assertEquals(0L, jwt.extractAuthVersion(token));
        assertEquals(List.of("CLIENT"), jwt.extractRoles(token));
        assertFalse(new JWTUtil("different-test-only-key-0123456789-abcdef").validateToken(token));
        assertFalse(accepted("invalid-token"));
        assertThrows(IllegalArgumentException.class, () -> new JWTUtil("short"));
    }

    @Test
    void missingVersionAndExpiredTokensAreRejected() throws Exception {
        var key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        String legacy =
                Jwts.builder()
                        .setSubject(user.getEmail())
                        .claim("roles", List.of("CLIENT"))
                        .setExpiration(new Date(System.currentTimeMillis() + 60000))
                        .signWith(key, SignatureAlgorithm.HS256)
                        .compact();
        assertFalse(accepted(legacy));
        String expired =
                Jwts.builder()
                        .setSubject(user.getEmail())
                        .claim("roles", List.of("CLIENT"))
                        .claim("authVersion", 0L)
                        .setExpiration(new Date(System.currentTimeMillis() - 60000))
                        .signWith(key, SignatureAlgorithm.HS256)
                        .compact();
        assertFalse(accepted(expired));
    }

    @Test
    void changingOwnPasswordRejectsOldTokenButAcceptsNewLoginToken() throws Exception {
        String previous = token();
        assertTrue(accepted(previous));
        service.changePassword(
                user.getEmail(),
                new PasswordChangeRequestDTO("old-password", "new-password", "new-password"));
        assertEquals(1L, user.getAuthVersion());
        assertEquals("new-hash", user.getPassword());
        assertFalse(accepted(previous));
        assertTrue(accepted(token()));
    }

    @Test
    void adminResetInvalidatesExistingTokens() throws Exception {
        String previous = token();
        service.resetPasswordByAdmin(7L, new PasswordResetDTO("new-password", "new-password"));
        assertEquals(1L, user.getAuthVersion());
        assertFalse(accepted(previous));
    }

    @Test
    void emailResetInvalidatesTokensAndConsumesResetToken() throws Exception {
        String previous = token();
        PasswordResetToken reset = new PasswordResetToken();
        reset.setUser(user);
        reset.setExpirationDate(LocalDateTime.now().plusMinutes(10));
        reset.setUsed(false);
        when(resetTokens.getByToken("reset-token")).thenReturn(Optional.of(reset));
        service.resetPasswordByToken(
                "reset-token", new PasswordResetDTO("new-password", "new-password"));
        assertTrue(reset.isUsed());
        assertEquals(1L, user.getAuthVersion());
        assertFalse(accepted(previous));
    }

    @Test
    void invalidPasswordChangeDoesNotRevokeCurrentSession() throws Exception {
        String previous = token();
        assertThrows(
                RuntimeException.class,
                () ->
                        service.changePassword(
                                user.getEmail(),
                                new PasswordChangeRequestDTO(
                                        "wrong-password", "new-password", "new-password")));
        assertEquals(0L, user.getAuthVersion());
        assertTrue(accepted(previous));
    }
}

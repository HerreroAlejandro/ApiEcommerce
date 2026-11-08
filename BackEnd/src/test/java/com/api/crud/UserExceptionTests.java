package com.api.crud;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.api.crud.DTO.LoginRequestDTO;
import com.api.crud.DTO.UserRegisterDTO;
import com.api.crud.exceptions.BusinessConflictException;
import com.api.crud.exceptions.UnauthenticatedException;
import com.api.crud.models.entity.UserModel;
import com.api.crud.repositories.UserDao;
import com.api.crud.services.UserService;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

class UserExceptionTests {

    @Test
    void invalidCredentialsDoNotDiscloseWhetherTheUserExists() {
        UserService service = new UserService();
        UserDao users = mock(UserDao.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        ReflectionTestUtils.setField(service, "userDao", users);
        ReflectionTestUtils.setField(service, "passwordEncoder", encoder);
        LoginRequestDTO request = new LoginRequestDTO();
        request.setEmail("client@example.com");
        request.setPassword("wrong-password");
        when(users.getUserByEmail(request.getEmail())).thenReturn(Optional.empty());
        String missing =
                assertThrows(UnauthenticatedException.class, () -> service.login(request))
                        .getMessage();
        UserModel user = new UserModel();
        user.setPassword("stored-hash");
        when(users.getUserByEmail(request.getEmail())).thenReturn(Optional.of(user));
        String incorrect =
                assertThrows(UnauthenticatedException.class, () -> service.login(request))
                        .getMessage();
        assertEquals(missing, incorrect);
    }

    @Test
    void duplicateRegistrationIsRejectedBeforeWriting() {
        UserService service = new UserService();
        UserDao users = mock(UserDao.class);
        ReflectionTestUtils.setField(service, "userDao", users);
        UserRegisterDTO request = new UserRegisterDTO();
        request.setEmail("client@example.com");
        when(users.getUserByEmail(request.getEmail())).thenReturn(Optional.of(new UserModel()));
        assertThrows(BusinessConflictException.class, () -> service.register(request));
        verify(users, never()).register(any());
    }
}

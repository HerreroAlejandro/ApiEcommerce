package com.api.crud.services;

import com.api.crud.DTO.*;
import com.api.crud.config.JWTUtil;
import com.api.crud.exceptions.*;
import com.api.crud.models.entity.PasswordResetToken;
import com.api.crud.models.entity.Role;
import com.api.crud.models.entity.UserModel;
import com.api.crud.models.enums.AccountStatus;
import com.api.crud.repositories.AccountRecoveryTokenDao;
import com.api.crud.repositories.PasswordResetTokenDao;
import com.api.crud.repositories.RoleDao;
import com.api.crud.repositories.UserDao;
import com.api.crud.validation.InputRules;

import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    @Autowired
    private UserDao userDao;

    @Autowired
    private RoleDao roleDao;

    @Autowired
    private JWTUtil jwtUtil;

    @Autowired
    private ModelMapper modelMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EmailService emailService;

    @Autowired
    private PasswordResetTokenDao passwordResetTokenDao;

    @Autowired
    private AccountRecoveryTokenDao accountRecoveryTokenDao;

    @Autowired
    private AccountStatusService accountStatusService;

    public String login(LoginRequestDTO loginRequestDTO) {
        UserModel user =
                userDao.getUserByEmail(loginRequestDTO.getEmail())
                        .orElseThrow(
                                () ->
                                        new UnauthenticatedException(
                                                "Email o contraseña incorrectos"));
        if (!passwordEncoder.matches(loginRequestDTO.getPassword(), user.getPassword())) {
            throw new UnauthenticatedException("Email o contraseña incorrectos");
        }
        if (user.getStatus() != AccountStatus.ACTIVE) {
            throw new ForbiddenOperationException("La cuenta no esta activa");
        }
        return jwtUtil.generateToken(
                user.getEmail(), user.getRoleNames().stream().toList(), user.getAuthVersion());
    }

    @Transactional
    public void register(UserRegisterDTO userRegisterDTO) {
        if (userDao.getUserByEmail(userRegisterDTO.getEmail()).isPresent()) {
            throw new BusinessConflictException("Ya existe una cuenta con ese email");
        }
        logger.info("Starting to process register users in service");
        UserModel userModel = modelMapper.map(userRegisterDTO, UserModel.class);
        userModel.setPassword(passwordEncoder.encode(userModel.getPassword()));
        if (userModel.getRoles() == null) {
            userModel.setRoles(new HashSet<>());
        }
        Role role =
                roleDao.getRoleByName("CLIENT")
                        .orElseThrow(
                                () -> {
                                    logger.debug("Registration failed: Role CLIENT does not exist");
                                    return new IllegalStateException(
                                            "El rol CLIENT no existe en la base de datos");
                                });
        userModel.getRoles().add(role);
        userModel.setStatus(AccountStatus.ACTIVE);
        if (userModel.getOrders() == null) {
            userModel.setOrders(new ArrayList<>());
        }
        userDao.register(userModel);
        logger.info("User {} registered successfully", userRegisterDTO.getEmail());
        if (!emailService.sendEmail(
                userModel.getEmail(),
                "Registration Confirmation",
                "Hello "
                        + userModel.getFirstName()
                        + ",\n\nYour account has been created successfully.\n\nGreetings!")) {
            throw new ExternalServiceException("No se pudo enviar el correo de confirmacion");
        }
    }

    public List<UserListAdminResponseDTO> getUserByName(String firstName, String lastName) {
        logger.info(
                "Starting to process search for users with name: {} and lastName: {}",
                firstName,
                lastName);
        List<UserModel> users = userDao.getUserByName(firstName, lastName);
        List<UserListAdminResponseDTO> response =
                users.stream()
                        .map(user -> modelMapper.map(user, UserListAdminResponseDTO.class))
                        .collect(Collectors.toList());
        if (response.isEmpty()) {
            logger.debug("No users found with name: {} and lastName: {}", firstName, lastName);
        }
        return response;
    }

    public Optional<UserDetailAdminResponseDTO> getUserByEmail(String email) {
        logger.info("Starting to process search for user with email: {}", email);
        Optional<UserDetailAdminResponseDTO> response =
                userDao.getUserByEmail(email)
                        .map(user -> modelMapper.map(user, UserDetailAdminResponseDTO.class));
        if (response.isEmpty()) {
            logger.debug("User with email {} not found", email);
        }
        return response;
    }

    public Optional<UserResponseDTO> getProfile(String email) {
        logger.info("Starting to process profile for user with email: {}", email);
        Optional<UserResponseDTO> userResponseDTO =
                userDao.getUserByEmail(email)
                        .map(user -> modelMapper.map(user, UserResponseDTO.class));
        if (userResponseDTO.isEmpty()) {
            logger.debug("User with email {} not found", email);
        }
        return userResponseDTO;
    }

    public UserResponseDTO updateMyProfile(UserUpdateRequestDTO dto) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();
        UserModel updatedUser = userDao.updateMyProfile(email, dto);
        return modelMapper.map(updatedUser, UserResponseDTO.class);
    }

    public void deactivateMyAccount() {
        accountStatusService.deactivateMyAccount(
                SecurityContextHolder.getContext().getAuthentication().getName());
    }

    @Transactional
    public void changePassword(String email, PasswordChangeRequestDTO dto) {
        UserModel user =
                userDao.getUserByEmail(email)
                        .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        if (!passwordEncoder.matches(dto.getCurrentPassword(), user.getPassword())) {
            throw new InvalidRequestException("Contraseña actual incorrecta");
        }
        if (!dto.getNewPassword().equals(dto.getConfirmPassword())) {
            throw new InvalidRequestException("La nueva contraseña y su confirmación no coinciden");
        }
        updatePasswordAndInvalidateTokens(user, dto.getNewPassword());
    }

    public List<UserResponseDTO> getUsers() {
        logger.info("Starting to process getUsers in service");
        List<UserModel> users = userDao.getUsers();
        if (users.isEmpty()) {
            logger.debug("No users found in service.");
        }
        // Mapear lista de usuarios a DTOs
        List<UserResponseDTO> userResponseDTOS =
                users.stream()
                        .map(user -> modelMapper.map(user, UserResponseDTO.class))
                        .collect(Collectors.toList());
        logger.info("Successfully retrieved {} users", users.size());
        return userResponseDTOS;
    }

    public Page<UserListAdminResponseDTO> getUserAdmin(Pageable pageable, String role) {
        InputRules.page(pageable);
        logger.info(
                "Processing admin user listing - page: {}, size: {}, role: {}",
                pageable.getPageNumber(),
                pageable.getPageSize(),
                role);
        Page<UserModel> users = userDao.getUserAdmin(pageable, role);
        Page<UserListAdminResponseDTO> userResponseDTOs =
                users.map(user -> modelMapper.map(user, UserListAdminResponseDTO.class));
        logger.info("Successfully retrieved {} users", userResponseDTOs.getTotalElements());
        return userResponseDTOs;
    }

    public Optional<UserDetailAdminResponseDTO> getUserById(Long id) {
        logger.info("Starting to process search for user with ID: {}", id);
        Optional<UserDetailAdminResponseDTO> userAdminDetailResponseDTO =
                userDao.getUserById(id)
                        .map(user -> modelMapper.map(user, UserDetailAdminResponseDTO.class));
        if (userAdminDetailResponseDTO.isEmpty()) {
            logger.debug("User with ID {} not found", id);
        }
        return userAdminDetailResponseDTO;
    }

    @Transactional
    public void resetPasswordByAdmin(Long id, PasswordResetDTO dto) {
        UserModel user =
                userDao.getUserById(id)
                        .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        if (!dto.getNewPassword().equals(dto.getConfirmPassword())) {
            throw new InvalidRequestException("La nueva contraseña y su confirmación no coinciden");
        }
        updatePasswordAndInvalidateTokens(user, dto.getNewPassword());
    }

    public void requestAccountRecovery(String email) {
        accountStatusService.requestRecovery(email);
    }

    public void activateAccountWithRecoveryToken(String token) {
        accountStatusService.activateWithRecoveryToken(token);
    }

    private void updatePasswordAndInvalidateTokens(UserModel user, String newPassword) {
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setAuthVersion(Math.incrementExact(user.getAuthVersion()));
        userDao.update(user);
        passwordResetTokenDao.invalidateTokensByUser(user);
        accountRecoveryTokenDao.invalidateTokensByUser(user);
    }

    @Transactional
    public void requestPasswordReset(String email) {
        logger.info("Starting password reset request for email: {}", email);
        Optional<UserModel> optionalUser = userDao.getUserByEmail(email);
        if (optionalUser.isEmpty()) {
            logger.debug("No user found for password reset request");
            return;
        }
        UserModel user = optionalUser.get();
        passwordResetTokenDao.invalidateTokensByUser(user);
        String token = UUID.randomUUID().toString();
        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setToken(token);
        resetToken.setUser(user);
        resetToken.setCreatedAt(LocalDateTime.now());
        resetToken.setExpirationDate(LocalDateTime.now().plusMinutes(30));
        resetToken.setUsed(false);
        passwordResetTokenDao.save(resetToken);
        String subject = "Password reset";
        String body = "Use the following token to reset your password: " + token;
        if (!emailService.sendEmail(email, subject, body)) {
            throw new ExternalServiceException("No se pudo enviar el correo de recuperacion");
        }
        logger.info("Password reset token generated successfully");
    }

    @Transactional
    public void resetPasswordByToken(String token, PasswordResetDTO dto) {
        PasswordResetToken resetToken =
                passwordResetTokenDao
                        .getByToken(token)
                        .orElseThrow(
                                () ->
                                        new InvalidRequestException(
                                                "Token de recuperación inválido"));
        if (resetToken.isUsed()) {
            throw new InvalidRequestException("El token de recuperación ya fue utilizado");
        }
        if (resetToken.getExpirationDate().isBefore(LocalDateTime.now())) {
            throw new InvalidRequestException("El token de recuperación ha expirado");
        }
        if (!dto.getNewPassword().equals(dto.getConfirmPassword())) {
            throw new InvalidRequestException("La nueva contraseña y su confirmación no coinciden");
        }
        UserModel user = resetToken.getUser();
        updatePasswordAndInvalidateTokens(user, dto.getNewPassword());
        resetToken.setUsed(true);
    }
}

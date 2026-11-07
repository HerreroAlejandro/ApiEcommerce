package com.api.crud.services;

import com.api.crud.DTO.*;
import com.api.crud.config.JWTUtil;
import com.api.crud.models.entity.AccountRecoveryToken;
import com.api.crud.models.entity.PasswordResetToken;
import com.api.crud.models.entity.Role;
import com.api.crud.models.entity.UserModel;
import com.api.crud.repositories.AccountRecoveryTokenDao;
import com.api.crud.repositories.PasswordResetTokenDao;
import com.api.crud.repositories.RoleDao;
import com.api.crud.repositories.UserDao;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    @Autowired
    private UserDao userDao;

    @Autowired
    private RoleDao roleDao;

    @Autowired
    private RoleService roleService;

    @Autowired
    private JWTUtil jwtUtil;

    @Autowired
    private ModelMapper modelMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AccountRecoveryTokenDao accountRecoveryTokenDao;

    @Autowired
    private EmailService emailService;

    @Autowired
    private PasswordResetTokenDao passwordResetTokenDao;

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);


    public String login(LoginRequestDTO loginRequestDTO) {
        logger.info("Attempting Service login for user: {}", loginRequestDTO.getEmail());
        Optional<UserModel> userModel = userDao.findUserByEmail(loginRequestDTO.getEmail());

        if (userModel.isEmpty()) {
            logger.debug("Login failed: User {} not found", loginRequestDTO.getEmail());
            throw new IllegalArgumentException("User not found");
        }

        UserModel user = userModel.get();

        if (!user.isActive()) {
            logger.debug("Login failed: User {} is deactivated", loginRequestDTO.getEmail());
            throw new IllegalArgumentException("User account is deactivated");
        }

        if (!passwordEncoder.matches(loginRequestDTO.getPassword(), user.getPassword())) {
            logger.debug("Login failed: Incorrect password for {}", loginRequestDTO.getEmail());
            throw new IllegalArgumentException("Wrong password");
        }

        List<String> roles = getRolesForEmail(user.getEmail());
        logger.info("User {} logged in successfully with roles {}", loginRequestDTO.getEmail(), roles);
        return jwtUtil.generateToken(user.getEmail(), roles);
    }

    private List<String> getRolesForEmail(String email) {
        logger.info("Starting to process fetching roles for user with email: {}", email);
        Optional<UserModel> response = userDao.findUserByEmail(email);

        if (response.isPresent()) {
            List<String> roles = response.get().getRoles().stream()
                    .map(Role::getNameRole)
                    .collect(Collectors.toList());
            logger.info("Roles found for user {}: {}", email, roles);
            return roles;
        } else {
            logger.debug("User with email {} not found", email);
            throw new RuntimeException("User not found");
        }
    }

    public void register(UserRegisterDTO userRegisterDTO) {
        logger.info("Starting to process register users in service");

        UserModel userModel = modelMapper.map(userRegisterDTO, UserModel.class);

        userModel.setPassword(passwordEncoder.encode(userModel.getPassword()));

        if (userModel.getRoles() == null) {
            userModel.setRoles(new HashSet<>());
        }

        Role role = roleDao.findRoleByName("CLIENT")
                .orElseThrow(() -> {
                    logger.debug("Registration failed: Role CLIENT does not exist");
                    return new IllegalArgumentException("El rol CLIENT no existe en la base de datos");
                });
        userModel.getRoles().add(role);
        userModel.setActive(true);

        if (userModel.getOrders() == null) {
            userModel.setOrders(new ArrayList<>());
        }
        userDao.register(userModel);
        logger.info("User {} registered successfully", userRegisterDTO.getEmail());
    }

    public List<UserListAdminResponseDTO> findUserByName(String firstName, String lastName) {
        logger.info("Starting to process search for users with name: {} and lastName: {}", firstName, lastName);

        List<UserModel> users = userDao.findUserByName(firstName, lastName);

        List<UserListAdminResponseDTO> response = users.stream()
                        .map(user -> modelMapper.map(user, UserListAdminResponseDTO.class))
                        .collect(Collectors.toList());

        if (response.isEmpty()) {
            logger.debug("No users found with name: {} and lastName: {}", firstName, lastName);
        }
        return response;
    }

    public Optional<UserDetailAdminResponseDTO> findUserByEmail(String email) {
        logger.info("Starting to process search for user with email: {}", email);

        Optional<UserDetailAdminResponseDTO> response = userDao.findUserByEmail(email)
                .map(user -> modelMapper.map(user, UserDetailAdminResponseDTO.class));

        if (response.isEmpty()) {
            logger.debug("User with email {} not found", email);
        }

        return response;
    }

    public Optional<UserResponseDTO> getProfile(String email) {

        logger.info("Starting to process profile for user with email: {}", email);

        Optional<UserResponseDTO> userResponseDTO = userDao.findUserByEmail(email)
                .map(user -> modelMapper.map(user, UserResponseDTO.class));

        if (userResponseDTO.isEmpty()) {
            logger.debug("User with email {} not found", email);
        }

        return userResponseDTO;
    }

    public UserResponseDTO updateMyProfile(UserUpdateRequestDTO dto) {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        String email = authentication.getName();

        UserModel updatedUser = userDao.updateMyProfile(email, dto);

        return modelMapper.map(updatedUser, UserResponseDTO.class);
    }

    public void deactivateMyAccount() {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        String email = authentication.getName();

        UserModel user = userDao.findUserByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        user.setActive(false);

        userDao.update(user);
    }

    public void deactivateUserByAdmin(Long id) {

        UserModel user = userDao.findUserById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        user.setActive(false);
        userDao.update(user);
    }

    public void activateUserByAdmin(Long id) {
        UserModel user = userDao.findUserById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        user.setActive(true);

        userDao.update(user);
    }

    public void changePassword(String email, PasswordChangeRequestDTO dto) {

        UserModel user = userDao.findUserByEmail(email).orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        if (!passwordEncoder.matches(dto.getCurrentPassword(), user.getPassword())) {

            throw new RuntimeException("Contraseña actual incorrecta");
        }

        if (!dto.getNewPassword().equals(dto.getConfirmPassword())) {
            throw new RuntimeException("La nueva contraseña y su confirmación no coinciden");
        }

        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));

        userDao.update(user);
    }

    public List<UserResponseDTO> getUsers() {
        logger.info("Starting to process getUsers in service");
        List<UserModel> users = userDao.getUsers();

        if (users.isEmpty()) {
            logger.debug("No users found in service.");
        }

        // Mapear lista de usuarios a DTOs
        List<UserResponseDTO> userResponseDTOS = users.stream()
                .map(user -> modelMapper.map(user, UserResponseDTO.class))
                .collect(Collectors.toList());

        logger.info("Successfully retrieved {} users", users.size());
        return userResponseDTOS;
    }

    public Page<UserListAdminResponseDTO> getUserAdmin(Pageable pageable, String role) {

        logger.info("Processing admin user listing - page: {}, size: {}, role: {}", pageable.getPageNumber(), pageable.getPageSize(), role);

        Page<UserModel> users = userDao.getUserAdmin(pageable, role);

        Page<UserListAdminResponseDTO> userResponseDTOs = users.map(user ->
                modelMapper.map(user, UserListAdminResponseDTO.class));

        logger.info("Successfully retrieved {} users", userResponseDTOs.getTotalElements());

        return userResponseDTOs;
    }

    public Optional<UserDetailAdminResponseDTO> findUserById(Long id) {
        logger.info("Starting to process search for user with ID: {}", id);

        Optional<UserDetailAdminResponseDTO> userAdminDetailResponseDTO = userDao.findUserById(id)
                .map(user -> modelMapper.map(user, UserDetailAdminResponseDTO.class));

        if (userAdminDetailResponseDTO.isEmpty()) {
            logger.debug("User with ID {} not found", id);
        }

        return userAdminDetailResponseDTO;
    }

    public void resetPasswordByAdmin(Long id, PasswordResetDTO dto) {

        UserModel user = userDao.findUserById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        if (!dto.getNewPassword().equals(dto.getConfirmPassword())) {
            throw new RuntimeException("La nueva contraseña y su confirmación no coinciden");
        }

        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));

        userDao.update(user);
    }

    public boolean deleteUserById(long id) {
        logger.info("Starting to process attempting to delete user with ID: {}", id);
        boolean result = userDao.deleteUserById(id);

        if (result) {
            logger.info("User with ID {} deleted successfully", id);
        } else {
            logger.debug("User with ID {} not found or deletion failed", id);
        }

        return result;
    }

    public void requestAccountRecovery(String email) {

        logger.info("Starting account recovery request for email: {}", email);

        Optional<UserModel> optionalUser = userDao.findUserByEmail(email);

        if (optionalUser.isEmpty()) {
            logger.debug("No user found for account recovery request");
            return;
        }

        UserModel user = optionalUser.get();

        if (user.isActive()) {
            logger.debug("Account recovery requested for an active user");
            return;
        }

        accountRecoveryTokenDao.invalidateTokensByUser(user);

        String token = UUID.randomUUID().toString();

        AccountRecoveryToken recoveryToken = new AccountRecoveryToken();
        recoveryToken.setToken(token);
        recoveryToken.setUser(user);
        recoveryToken.setCreatedAt(LocalDateTime.now());
        recoveryToken.setExpirationDate(LocalDateTime.now().plusMinutes(30));
        recoveryToken.setUsed(false);

        accountRecoveryTokenDao.save(recoveryToken);

        String subject = "Account recovery";
        String body = "Use the following token to reactivate your account: " + token;

        emailService.sendEmail(email, subject, body);

        logger.info("Account recovery token generated successfully");
    }

    public void activateAccountWithRecoveryToken(String token) {

        logger.info("Starting account activation with recovery token");

        AccountRecoveryToken recoveryToken = accountRecoveryTokenDao.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Token de recuperación inválido"));

        if (recoveryToken.isUsed()) {
            throw new RuntimeException("El token de recuperación ya fue utilizado");
        }

        if (recoveryToken.getExpirationDate().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("El token de recuperación ha expirado");
        }

        UserModel user = recoveryToken.getUser();

        user.setActive(true);
        recoveryToken.setUsed(true);

        userDao.update(user);
        accountRecoveryTokenDao.update(recoveryToken);

        logger.info("Account activated successfully with recovery token");
    }

    public void requestPasswordReset(String email) {

        logger.info("Starting password reset request for email: {}", email);

        Optional<UserModel> optionalUser = userDao.findUserByEmail(email);

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

        emailService.sendEmail(email, subject, body);

        logger.info("Password reset token generated successfully");
    }

    @Transactional
    public void resetPasswordByToken(String token, PasswordResetDTO dto) {

        PasswordResetToken resetToken = passwordResetTokenDao.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Token de recuperación inválido"));

        if (resetToken.isUsed()) {
            throw new RuntimeException("El token de recuperación ya fue utilizado");
        }

        if (resetToken.getExpirationDate().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("El token de recuperación ha expirado");
        }

        if (!dto.getNewPassword().equals(dto.getConfirmPassword())) {
            throw new RuntimeException("La nueva contraseña y su confirmación no coinciden");
        }

        UserModel user = resetToken.getUser();

        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        resetToken.setUsed(true);

        userDao.update(user);
        passwordResetTokenDao.update(resetToken);
    }

}
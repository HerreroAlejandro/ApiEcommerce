package com.api.crud.controllers;

import com.api.crud.DTO.*;
import com.api.crud.config.JWTUtil;
import com.api.crud.exceptions.ResourceNotFoundException;
import com.api.crud.services.UserService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/users")
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    @Autowired
    private UserService userService;

    @Autowired
    private JWTUtil jwtUtil;

    @Autowired
    private ModelMapper modelMapper;

    @PostMapping(path = "/register")
    public ResponseEntity<String> register(@Valid @RequestBody UserRegisterDTO userRegisterDTO) {
        userService.register(userRegisterDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body("User created successfully");
    }

    @PostMapping(path = "/login")
    public ResponseEntity<String> login(@Valid @RequestBody LoginRequestDTO loginRequestDTO) {
        return ResponseEntity.ok(userService.login(loginRequestDTO));
    }

    @GetMapping("/profile")
    public ResponseEntity<UserResponseDTO> getProfile(Authentication authentication) {
        String email = authentication.getName();
        Optional<UserResponseDTO> user = userService.getProfile(email);
        if (user.isPresent()) {
            return ResponseEntity.ok(user.get());
        }
        throw new ResourceNotFoundException();
    }

    @PatchMapping("/updateMyProfile")
    public ResponseEntity<UserResponseDTO> updateMyProfile(
            @Valid @RequestBody UserUpdateRequestDTO dto) {
        return ResponseEntity.ok(userService.updateMyProfile(dto));
    }

    @PatchMapping("/changePassword")
    public ResponseEntity<String> changePassword(@Valid @RequestBody PasswordChangeRequestDTO dto) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();
        logger.info("Received request to change password for authenticated user");
        userService.changePassword(email, dto);
        logger.info("Password changed successfully for authenticated user");
        return ResponseEntity.ok("Password updated successfully");
    }

    @PatchMapping("/deactivate")
    public ResponseEntity<String> deactivateMyAccount() {
        logger.info("Received request to deactivate authenticated user");
        userService.deactivateMyAccount();
        logger.info("Authenticated user deactivated successfully");
        return ResponseEntity.ok("Account deactivated successfully");
    }

    @PostMapping("/account-recovery")
    public ResponseEntity<String> requestAccountRecovery(
            @Valid @RequestBody AccountRecoveryRequestDTO dto) {
        logger.info("Received account recovery request");
        userService.requestAccountRecovery(dto.getEmail());
        return ResponseEntity.ok(
                "If an account exists with this email, recovery instructions have been sent.");
    }

    @PatchMapping("/account-recovery/activate")
    public ResponseEntity<String> activateAccountWithRecoveryToken(
            @RequestParam @NotBlank @Size(max = 128) String token) {
        logger.info("Received account activation request with recovery token");
        userService.activateAccountWithRecoveryToken(token);
        logger.info("Account activated successfully with recovery token");
        return ResponseEntity.ok("Account activated successfully");
    }

    @PostMapping("/password-recovery")
    public ResponseEntity<String> requestPasswordReset(
            @Valid @RequestBody AccountRecoveryRequestDTO dto) {
        logger.info("Received password reset request");
        userService.requestPasswordReset(dto.getEmail());
        return ResponseEntity.ok(
                "If an account exists with this email, password reset instructions have been sent.");
    }

    @PatchMapping("/password-recovery/reset")
    public ResponseEntity<String> resetPasswordByToken(
            @RequestParam @NotBlank @Size(max = 128) String token,
            @Valid @RequestBody PasswordResetDTO dto) {
        logger.info("Received password reset request using recovery token");
        userService.resetPasswordByToken(token, dto);
        logger.info("Password reset successfully using recovery token");
        return ResponseEntity.ok("Password reset successfully");
    }
}

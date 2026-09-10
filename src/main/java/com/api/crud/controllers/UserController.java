package com.api.crud.controllers;

import com.api.crud.DTO.LoginRequestDTO;
import com.api.crud.DTO.UserRegisterDTO;
import com.api.crud.DTO.UserResponseDTO;
import com.api.crud.DTO.UserUpdateRequestDTO;
import com.api.crud.DTO.PasswordChangeDTO;
import com.api.crud.config.JWTUtil;
import com.api.crud.services.UserService;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

@RestController
@RequestMapping("/users")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private JWTUtil jwtUtil;

    @Autowired
    private ModelMapper modelMapper;

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    @PostMapping(path = "/register")
    public ResponseEntity<String> register(@RequestBody UserRegisterDTO userRegisterDTO) {
        logger.info("Received request to register a user with email: {}", userRegisterDTO.getEmail());
        ResponseEntity<String> response;
        try {
            if (userService.findUserByEmail(userRegisterDTO.getEmail()).isPresent()) {
                logger.warn("User already registered with email: {}", userRegisterDTO.getEmail());
                response = ResponseEntity.status(HttpStatus.BAD_REQUEST).body("User already registered with this email");
            } else {
                this.userService.register(userRegisterDTO);
                logger.info("User with email '{}' created successfully.", userRegisterDTO.getEmail());
                response = ResponseEntity.status(HttpStatus.CREATED).body("User created successfully");
            }
        } catch (IllegalArgumentException e) {
            logger.error("Invalid user data for email '{}': {}", userRegisterDTO.getEmail(), e.getMessage());
            response = ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid user data: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error occurred while registering user with email '{}': {}", userRegisterDTO.getEmail(), e.getMessage());
            response = ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to create user: " + e.getMessage());
        }
        return response;
    }

    @PostMapping(path = "/login")
    public ResponseEntity<String> login(@RequestBody LoginRequestDTO loginRequestDTO) {
        logger.info("Received request to login user with email: {}", loginRequestDTO.getEmail());

        String token = null;
        HttpStatus status = HttpStatus.OK;
        String message = null;

        try {
            token = userService.login(loginRequestDTO);
            logger.info("Login successful for user with email: {}", loginRequestDTO.getEmail());
        } catch (IllegalArgumentException e) {
            status = HttpStatus.UNAUTHORIZED;
            message = e.getMessage();
            logger.warn("Login failed for user with email: {} - {}", loginRequestDTO.getEmail(), message);
        } catch (Exception e) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
            message = "An unexpected error occurred during login.";
            logger.error("Unexpected error during login for user with email: {}: {}", loginRequestDTO.getEmail(), e.getMessage());
        }
        return ResponseEntity.status(status).body(message != null ? message : token);
    }

    @GetMapping("/profile")
    public ResponseEntity<UserResponseDTO> getProfile(Authentication authentication) {

        String email = authentication.getName();

        Optional<UserResponseDTO> user = userService.getProfile(email);

        if (user.isPresent()) {
            return ResponseEntity.ok(user.get());
        }

        return ResponseEntity.notFound().build();
    }

    @PatchMapping("/updateMyProfile")
    public ResponseEntity<UserResponseDTO> updateMyProfile(@RequestBody UserUpdateRequestDTO dto) {

        ResponseEntity<UserResponseDTO> response;

        try {
            UserResponseDTO updated = userService.updateMyProfile(dto);
            response = ResponseEntity.ok(updated);
        } catch (RuntimeException ex) {
            response = ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }

        return response;
    }

    @PatchMapping("/changePassword")
    public ResponseEntity<String> changePassword(@RequestBody PasswordChangeDTO dto) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        String email = authentication.getName();

        logger.info("Received request to change password for authenticated user");

        try {
            userService.changePassword(email, dto);

            logger.info("Password changed successfully for authenticated user");

            return ResponseEntity.ok("Password updated successfully");

        } catch (RuntimeException ex) {
            logger.error("Error changing password: {}", ex.getMessage());

            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ex.getMessage());
        }
    }

    @PatchMapping("/deactivate")
    public ResponseEntity<String> deactivateMyAccount() {

        logger.info("Received request to deactivate authenticated user");

        try {
            userService.deactivateMyAccount();

            logger.info("Authenticated user deactivated successfully");

            return ResponseEntity.ok("Account deactivated successfully");

        } catch (RuntimeException ex) {

            logger.error("Error deactivating authenticated user: {}", ex.getMessage());

            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ex.getMessage());
        }
    }
}
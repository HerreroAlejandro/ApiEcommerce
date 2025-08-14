package com.api.crud.controllers;

import com.api.crud.DTO.LoginRequestDTO;
import com.api.crud.DTO.UserModelDTO;
import com.api.crud.config.JWTUtil;
import com.api.crud.services.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/users")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private JWTUtil jwtUtil;

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    @PostMapping(path = "/register")
    public ResponseEntity<String> register(@RequestBody UserModelDTO userModelDto) {
        logger.info("Received request to register a user with email: {}", userModelDto.getEmail());
        ResponseEntity<String> response;
        try {
            if (userService.findUserByEmail(userModelDto.getEmail()).isPresent()) {
                logger.warn("User already registered with email: {}", userModelDto.getEmail());
                response = ResponseEntity.status(HttpStatus.BAD_REQUEST).body("User already registered with this email");
            } else {
                this.userService.register(userModelDto);
                logger.info("User with email '{}' created successfully.", userModelDto.getEmail());
                response = ResponseEntity.status(HttpStatus.CREATED).body("User created successfully");
            }
        } catch (IllegalArgumentException e) {
            logger.error("Invalid user data for email '{}': {}", userModelDto.getEmail(), e.getMessage());
            response = ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid user data: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error occurred while registering user with email '{}': {}", userModelDto.getEmail(), e.getMessage());
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

}
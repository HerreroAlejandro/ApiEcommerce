package com.api.crud.controllers;

import com.api.crud.DTO.PasswordResetDTO;
import com.api.crud.DTO.UserDetailAdminResponseDTO;
import com.api.crud.DTO.UserListAdminResponseDTO;
import com.api.crud.DTO.UserResponseDTO;
import com.api.crud.services.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;


import java.util.Collections;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/admin")
public class AuthController {

    @Autowired
    private UserService userService;

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);


    @GetMapping(path = "/ShowUser")
    public ResponseEntity<List<UserResponseDTO>> getUsers() {
        logger.info("Starting to fetch users.");
        List<UserResponseDTO> users = userService.getUsers();

        ResponseEntity<List<UserResponseDTO>> response;
        if (users.isEmpty()) {
            logger.info("No users found");
            response = ResponseEntity.status(HttpStatus.NOT_FOUND).body(Collections.emptyList());
        } else {
            logger.info("Successfully found {} users.", users.size());
            response = ResponseEntity.ok(users);
        }
        return response;
    }

    @GetMapping(path = "/ShowAll")
    public ResponseEntity<Page<UserListAdminResponseDTO>> getUserAdmin(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id,asc") String sort,
            @RequestParam(required = false) String role) {

        logger.info(
                "Starting to fetch users - page: {}, size: {}, sort: {}, role: {}",
                page, size, sort, role
        );

        String[] sortParams = sort.split(",");

        Sort.Order order = sortParams.length > 1 &&
                sortParams[1].equalsIgnoreCase("desc")
                ? Sort.Order.desc(sortParams[0])
                : Sort.Order.asc(sortParams[0]);

        Pageable pageable = PageRequest.of(page, size, Sort.by(order));
        Page<UserListAdminResponseDTO> users = userService.getUserAdmin(pageable, role);

        if (users.isEmpty()) {
            logger.info("No users found with the specified criteria.");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Page.empty());
        }
        logger.info("Successfully fetched {} users.", users.getTotalElements());

        return ResponseEntity.ok(users);
    }

    @GetMapping(path = "/findUserById/{id}")
    public ResponseEntity<UserDetailAdminResponseDTO> findUserById(@PathVariable Long id) {
        logger.info("Starting to fetch user with id: {}", id);
        Optional<UserDetailAdminResponseDTO> user = userService.findUserById(id);
        ResponseEntity<UserDetailAdminResponseDTO> response;

        if (user.isPresent()) {
            logger.info("User with ID: {} found successfully.", id);
            response = ResponseEntity.ok(user.get());
        } else {
            logger.info("User with ID: {} not found.", id);
            response = ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return response;
    }

    @GetMapping(path = "/findUserByName")
    public ResponseEntity<List<UserListAdminResponseDTO>> findUserByName(@RequestParam String firstName, @RequestParam String lastName) {
        logger.info("Received request to fetch users with name: {} {}", firstName, lastName);

        List<UserListAdminResponseDTO> users = userService.findUserByName(firstName, lastName);
        ResponseEntity<List<UserListAdminResponseDTO>> response;

        if (!users.isEmpty()) {
            logger.info("Users with name: {} {} found successfully.", firstName, lastName);
            response = ResponseEntity.ok(users);
        } else {
            logger.info("No users found with name: {} {}.", firstName, lastName);
            response = ResponseEntity.status(HttpStatus.NOT_FOUND).body(Collections.emptyList());
        }
        return response;
    }

    @GetMapping(path = "/findUserByEmail/{email}")
    public ResponseEntity<UserDetailAdminResponseDTO> findUserByEmail(@PathVariable String email) {
        logger.info("Starting to fetch user with email: {}", email);

        Optional<UserDetailAdminResponseDTO> user = userService.findUserByEmail(email);
        ResponseEntity<UserDetailAdminResponseDTO> response;

        if (user.isEmpty()) {
            logger.info("User with email: {} not found.", email);
            response = ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } else {
            logger.info("User with email: {} found successfully.", email);
            response = ResponseEntity.ok(user.get());
        }

        return response;
    }

    @PatchMapping("/{id}/password")
    public ResponseEntity<String> resetPasswordByAdmin(@PathVariable Long id, @RequestBody PasswordResetDTO dto) {
        logger.info("Received request to reset password for user with ID: {}", id);

        try {
            userService.resetPasswordByAdmin(id, dto);
            logger.info("Password reset successfully for user with ID: {}", id);

            return ResponseEntity.ok("Password reset successfully");

        } catch (RuntimeException ex) {
            logger.error("Error resetting password for user with ID {}: {}", id, ex.getMessage());

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        }
    }

    @PatchMapping("/users/{id}/deactivate")
    public ResponseEntity<String> deactivateUserByAdmin(@PathVariable Long id) {
        logger.info("Received request to deactivate user with ID: {}", id);

        try {
            userService.deactivateUserByAdmin(id);
            logger.info("User with ID {} was deactivated successfully.", id);

            return ResponseEntity.ok("User with ID " + id + " was deactivated successfully");

        } catch (RuntimeException ex) {
            logger.error("Error deactivating user with ID {}: {}", id, ex.getMessage());

            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
        }
    }

    @PatchMapping("/users/{id}/activate")
    public ResponseEntity<String> activateUserByAdmin(@PathVariable Long id) {
        logger.info("Received request to activate user with ID: {}", id);

        try {
            userService.activateUserByAdmin(id);
            logger.info("User with ID {} was activated successfully.", id);

            return ResponseEntity.ok("User with ID " + id + " was activated successfully");

        } catch (RuntimeException ex) {
            logger.error("Error activating user with ID {}: {}", id, ex.getMessage());

            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
        }
    }

    @DeleteMapping(path = "/DeleteUserById/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable("id") Long id) {
        logger.info("Received request to delete user with ID: {}", id);
        ResponseEntity<Void> response;
        boolean deleted = userService.deleteUserById(id);
        if (deleted) {
            logger.info("User with ID: {} deleted successfully.", id);
            response = ResponseEntity.status(HttpStatus.NO_CONTENT).build();
        } else {
            logger.info("User with ID: {} not found for deletion.", id);
            response = ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return response;
    }

}

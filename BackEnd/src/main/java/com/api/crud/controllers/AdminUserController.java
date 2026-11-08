package com.api.crud.controllers;

import com.api.crud.DTO.AccountStatusAuditResponseDTO;
import com.api.crud.DTO.ChangeReasonRequestDTO;
import com.api.crud.DTO.PasswordResetDTO;
import com.api.crud.DTO.UserDetailAdminResponseDTO;
import com.api.crud.DTO.UserListAdminResponseDTO;
import com.api.crud.DTO.UserResponseDTO;
import com.api.crud.services.AccountStatusService;
import com.api.crud.services.UserService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/admin")
public class AdminUserController {

    private static final Logger logger = LoggerFactory.getLogger(AdminUserController.class);

    @Autowired
    private UserService userService;

    @Autowired
    private AccountStatusService accountStatusService;

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
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "id,asc")
                    @Pattern(
                            regexp = "[A-Za-z][A-Za-z0-9]*(,(?i:asc|desc))?",
                            message = "Ordenamiento invalido")
                    String sort,
            @RequestParam(required = false) @Size(max = 20) String role) {
        logger.info(
                "Starting to fetch users - page: {}, size: {}, sort: {}, role: {}",
                page,
                size,
                sort,
                role);
        String[] sortParams = sort.split(",");
        Sort.Order order =
                sortParams.length > 1 && sortParams[1].equalsIgnoreCase("desc")
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

    @GetMapping(path = "/getUserById/{id}")
    public ResponseEntity<UserDetailAdminResponseDTO> getUserById(@PathVariable @Positive Long id) {
        logger.info("Starting to fetch user with id: {}", id);
        Optional<UserDetailAdminResponseDTO> user = userService.getUserById(id);
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

    @GetMapping(path = "/getUserByName")
    public ResponseEntity<List<UserListAdminResponseDTO>> getUserByName(
            @RequestParam @NotBlank @Size(min = 2, max = 25) String firstName,
            @RequestParam @NotBlank @Size(min = 2, max = 25) String lastName) {
        logger.info("Received request to fetch users with name: {} {}", firstName, lastName);
        List<UserListAdminResponseDTO> users = userService.getUserByName(firstName, lastName);
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

    @GetMapping(path = "/getUserByEmail/{email}")
    public ResponseEntity<UserDetailAdminResponseDTO> getUserByEmail(
            @PathVariable @NotBlank @Email @Size(max = 254) String email) {
        logger.info("Starting to fetch user with email: {}", email);
        Optional<UserDetailAdminResponseDTO> user = userService.getUserByEmail(email);
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
    public ResponseEntity<String> resetPasswordByAdmin(
            @PathVariable @Positive Long id, @Valid @RequestBody PasswordResetDTO dto) {
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

    @PatchMapping("/users/{id}/block")
    public ResponseEntity<Void> blockUser(
            Authentication authentication,
            @PathVariable @Positive Long id,
            @Valid @RequestBody ChangeReasonRequestDTO dto) {
        accountStatusService.block(authentication.getName(), id, dto.getReason());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/users/{id}/unblock")
    public ResponseEntity<Void> unblockUser(
            Authentication authentication,
            @PathVariable @Positive Long id,
            @Valid @RequestBody ChangeReasonRequestDTO dto) {
        accountStatusService.unblock(authentication.getName(), id, dto.getReason());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/users/{id}/activate")
    public ResponseEntity<Void> activateUserByAdmin(
            Authentication authentication,
            @PathVariable @Positive Long id,
            @Valid @RequestBody ChangeReasonRequestDTO dto) {
        accountStatusService.activate(authentication.getName(), id, dto.getReason());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/users/{id}/status-history")
    public ResponseEntity<Page<AccountStatusAuditResponseDTO>> getStatusHistory(
            @PathVariable @Positive Long id, @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(accountStatusService.getHistory(id, pageable));
    }
}

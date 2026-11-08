package com.api.crud.controllers;

import com.api.crud.DTO.RoleDTO;
import com.api.crud.exceptions.ResourceNotFoundException;
import com.api.crud.services.RoleService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/support/role")
public class RoleController {

    private static final Logger logger = LoggerFactory.getLogger(RoleController.class);

    @Autowired
    private RoleService roleService;

    @GetMapping(path = "/ShowRoles")
    public ResponseEntity<List<RoleDTO>> getRoles() {
        List<RoleDTO> roles = roleService.getRoles();
        ResponseEntity<List<RoleDTO>> response;
        if (roles.isEmpty()) {
            response = ResponseEntity.ok(Collections.emptyList());
        } else {
            logger.info("Roles fetched successfully. Total roles found: {}", roles.size());
            response = ResponseEntity.ok(roles);
        }
        return response;
    }

    @GetMapping(path = "/getRoleById/{id}")
    public ResponseEntity<RoleDTO> getRoleById(@PathVariable @Positive Long id) {
        logger.info("Starting to find role with id {}", id);
        Optional<RoleDTO> roleDTO = roleService.getRoleById(id);
        ResponseEntity<RoleDTO> response;
        if (roleDTO.isPresent()) {
            logger.debug("Role with id {} found", id);
            response = ResponseEntity.ok(roleDTO.get());
        } else {
            logger.debug("Role with id {} not found", id);
            throw new ResourceNotFoundException();
        }
        return response;
    }

    @GetMapping(path = "/getRoleByName/{roleName}")
    public ResponseEntity<RoleDTO> getRoleByName(
            @PathVariable @NotBlank @Size(min = 2, max = 20) String roleName) {
        logger.info("Starting to find role with name {}", roleName);
        Optional<RoleDTO> roleDTO = roleService.getRoleByName(roleName);
        ResponseEntity<RoleDTO> response;
        if (roleDTO.isPresent()) {
            logger.debug("Role {} found", roleName);
            response = ResponseEntity.ok(roleDTO.get());
        } else {
            logger.debug("Role {} not found", roleName);
            throw new ResourceNotFoundException();
        }
        return response;
    }

    @PostMapping(path = "/SaveRole")
    public ResponseEntity<String> saveRole(@Valid @RequestBody RoleDTO roleDto) {
        roleService.saveRole(roleDto);
        return ResponseEntity.status(HttpStatus.CREATED).body("Role created successfully");
    }

    @DeleteMapping(path = "/deleteRoleById/{id}")
    public ResponseEntity<String> deleteRoleById(@PathVariable @Positive Long id) {
        logger.info("Starting to delete role with id {}", id);
        boolean result = roleService.deleteRoleById(id);
        ResponseEntity<String> response;
        if (result) {
            logger.info("Role with ID {} deleted successfully", id);
            response = ResponseEntity.ok("Role with ID " + id + " deleted successfully");
        } else {
            logger.info("Role with ID {} not found or deletion failed", id);
            throw new ResourceNotFoundException();
        }
        return response;
    }
}

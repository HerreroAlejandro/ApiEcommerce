package com.api.crud.controllers;

import com.api.crud.DTO.RoleDTO;
import com.api.crud.services.RoleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/support/role")
public class RoleController {

    @Autowired
    private RoleService roleService;

    private static final Logger logger = LoggerFactory.getLogger(RoleController.class);

    @GetMapping(path = "/ShowRoles")
    public ResponseEntity<List<RoleDTO>> getRoles() {
        logger.info("Starting to fetch roles.");
        List<RoleDTO> roles = roleService.getRoles();
        ResponseEntity<List<RoleDTO>> response;
        if (roles.isEmpty()) {
            logger.info("No roles found, returning empty list.");
            response = ResponseEntity.status(HttpStatus.NOT_FOUND).body(Collections.emptyList());
        }else{
            logger.info("Roles fetched successfully. Total roles found: {}", roles.size());
            response = ResponseEntity.ok(roles);
        }
        return response;
    }

    @GetMapping(path = "/findRoleById/{id}")
    public ResponseEntity<RoleDTO> findRoleById(@PathVariable Long id) {
        logger.info("Starting to find role with id {}", id);
        Optional<RoleDTO> roleDTO = roleService.findRoleById(id);
        ResponseEntity<RoleDTO> response;

        if (roleDTO.isPresent()) {
            logger.debug("Role with id {} found", id);
            response = ResponseEntity.ok(roleDTO.get());
        } else {
            logger.debug("Role with id {} not found", id);
            response = ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
        return response;
    }

    @GetMapping(path = "/findRoleByName/{roleName}")
    public ResponseEntity<RoleDTO> findRoleByName(@PathVariable String roleName) {
        logger.info("Starting to find role with name {}", roleName);
        Optional<RoleDTO> roleDTO = roleService.findRoleByName(roleName);
        ResponseEntity<RoleDTO> response;

        if (roleDTO.isPresent()) {
            logger.debug("Role {} found", roleName);
            response = ResponseEntity.ok(roleDTO.get());
        } else {
            logger.debug("Role {} not found", roleName);
            response = ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
        return response;
    }

    @PostMapping(path = "/SaveRole")
    public ResponseEntity<String> saveRole(@RequestBody RoleDTO roleDto) {
        logger.info("Starting to save role: {}", roleDto.getNameRole());
        ResponseEntity<String> response;
        try {
            if (roleService.findRoleByName(roleDto.getNameRole()).isPresent()) {
                logger.warn("The Role '{}' already exists.", roleDto.getNameRole());
                response = ResponseEntity.status(HttpStatus.BAD_REQUEST).body("The Role already exists");
            } else {
                roleService.saveRole(roleDto);
                response = ResponseEntity.status(HttpStatus.CREATED).body("Role created successfully");
                logger.info("Role {} created successfully.", roleDto.getNameRole());
            }
        } catch (DataIntegrityViolationException e) {
            logger.error("DataIntegrityViolationException occurred while saving role '{}': {}", roleDto.getNameRole(), e.getMessage());
            response = ResponseEntity.status(HttpStatus.CONFLICT).body("Duplicate role name detected");
        } catch (Exception e) {
            logger.error("Unexpected error occurred while saving role '{}': {}", roleDto.getNameRole(), e.getMessage());
            response = ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to create role: " + e.getMessage());
        }
        return response;
    }

    @DeleteMapping(path = "/deleteRoleById/{id}")
    public ResponseEntity<String> deleteRoleById(@PathVariable Long id) {
        logger.info("Starting to delete role with id {}", id);
        boolean result = roleService.deleteRoleById(id);
        ResponseEntity<String> response;

        if (result) {
            logger.info("Role with ID {} deleted successfully", id);
            response = ResponseEntity.ok("Role with ID " + id + " deleted successfully");
        } else {
            logger.info("Role with ID {} not found or deletion failed", id);
            response = ResponseEntity.status(HttpStatus.NOT_FOUND).body("Role with ID " + id + " not found or deletion failed");
        }
        return response;
    }






}
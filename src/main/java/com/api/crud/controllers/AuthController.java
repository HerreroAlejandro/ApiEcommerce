package com.api.crud.controllers;

import com.api.crud.DTO.UserDTO;
import com.api.crud.DTO.UserModelDTO;
import com.api.crud.DTO.UserUpdateDTO;
import com.api.crud.services.UserService;
import org.modelmapper.ModelMapper;
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
import org.springframework.security.core.Authentication;


import java.util.Collections;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/admin")
public class AuthController {

    @Autowired
    private UserService userService;

    @Autowired
    private ModelMapper modelMapper;

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);


    @GetMapping(path = "/findUserByName")
    public ResponseEntity<UserModelDTO> findUserByName(@RequestParam String firstName, @RequestParam String lastName) {
        logger.info("Received request to fetch user with name: {} {}", firstName, lastName);
        Optional<UserModelDTO> user = userService.findUserByName(firstName, lastName);

        ResponseEntity<UserModelDTO> response;
        if (user.isPresent()) {
            logger.info("User with name: {} {} found successfully.", firstName, lastName);
            response = ResponseEntity.ok(user.get());
        } else {
            logger.info("User with name: {} {} not found.", firstName, lastName);
            response = ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return response;
    }

    @GetMapping(path = "/findUserByEmail/{email}")
    public ResponseEntity<UserModelDTO> findUserByEmail(@PathVariable String email) {
        logger.info("Starting to fetch user with email: {}", email);
        Optional<UserModelDTO> userModelDTO = userService.findUserByEmail(email);

        ResponseEntity<UserModelDTO> response;
        if (userModelDTO.isEmpty()) {
            logger.info("User with email: {} not found.", email);
            response = ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } else {
            logger.info("User with email: {} found successfully.", email);
            response = ResponseEntity.ok(userModelDTO.get());
        }
        return response;
    }

    @PutMapping("/users/updateByEmail/{email}")
    public ResponseEntity<UserModelDTO> updateUserByEmail(@PathVariable String email, @RequestBody UserUpdateDTO dto) {
        ResponseEntity<UserModelDTO> response;

        try {
            UserModelDTO updated = userService.updateUserByEmail(email, dto);
            response = ResponseEntity.ok(updated);
        } catch (RuntimeException ex) {
            response = ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
        return response;
    }

    @DeleteMapping("/DeleteUserByEmail/{email}")
    public ResponseEntity<String> deleteUserByEmail(@PathVariable String email) {
        logger.info("received request deleteUserByEmail for email: {}", email);
        ResponseEntity<String> response;

        if (userService.deleteUserByEmail(email)){
            logger.info("The user with mail: {} was erased", email);
            response = ResponseEntity.ok(email);
        }else{
            logger.info("The user with mail: {} wasn't erased", email);
            response = ResponseEntity.status(HttpStatus.NOT_FOUND).body("User with email " + email + " not found");
        }
        return response;
    }

    @GetMapping("/users/profile")
    public ResponseEntity<UserModelDTO> getProfile(Authentication authentication) {
        String email = authentication.getName();
        Optional<UserModelDTO> user = userService.findUserByEmail(email);
        return ResponseEntity.ok(modelMapper.map(user, UserModelDTO.class));
    }
/*
    @GetMapping("/users/orders")
    public ResponseEntity<List<OrderDTO>> getUserOrders(Authentication authentication) {
        String email = authentication.getName();
        List<Order> orders = orderService.getOrdersByUserEmail(email);
        return ResponseEntity.ok(orderMapper.toDTOList(orders));
    }*/

    @GetMapping(path = "/ShowUser")
    public ResponseEntity<List<UserDTO>> getUsers() {
        logger.info("Starting to fetch users.");
        List<UserDTO> users = userService.getUsers();

        ResponseEntity<List<UserDTO>> response;
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
    public ResponseEntity<Page<UserModelDTO>> getUsersModel(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id,asc") String sort) {

        logger.info("Starting to fetch users with pagination - page: {}, size: {}, sort: {}", page, size, sort);
        String[] sortParams = sort.split(",");
        Sort.Order order = sortParams[1].equalsIgnoreCase("desc") ? Sort.Order.desc(sortParams[0]) : Sort.Order.asc(sortParams[0]);
        Pageable pageable = PageRequest.of(page, size, Sort.by(order));

        Page<UserModelDTO> users = userService.getUsersModel(pageable);

        ResponseEntity<Page<UserModelDTO>> response;
        if (users.isEmpty()) {
            logger.info("No users found with the specified pagination and sorting.");
            response = ResponseEntity.status(HttpStatus.NOT_FOUND).body(Page.empty());
        } else {
            logger.info("Successfully fetched {} users (page {} of {})", users.getSize(), page, users.getTotalPages());
            response = ResponseEntity.ok(users);
        }
        return response;
    }



    @GetMapping(path = "/findUserById/{id}")
    public ResponseEntity<UserModelDTO> findUserById(@PathVariable Long id) {
        logger.info("Starting to fetch user with id: {}", id);
        Optional<UserModelDTO> user = userService.findUserById(id);
        ResponseEntity<UserModelDTO> response;

        if (user.isPresent()) {
            logger.info("User with ID: {} found successfully.", id);
            response = ResponseEntity.ok(user.get());
        } else {
            logger.info("User with ID: {} not found.", id);
            response = ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return response;
    }

    @PutMapping(path = "/Alter/{id}")
    public ResponseEntity<UserModelDTO> updateUserById(@RequestBody UserModelDTO request, @PathVariable("id") Long id) {
        logger.info("Received request to update user with ID: {}", id);
        ResponseEntity<UserModelDTO> response;
        UserModelDTO updatedUser = userService.updateUserById(request, id);

        if (updatedUser != null) {
            response = ResponseEntity.ok(updatedUser);
            logger.info("User with ID: {} updated successfully.", id);
        } else {
            logger.info("User with ID: {} not found for update.", id);
            response = ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return response;
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

package com.api.crud.controllers;

import com.api.crud.DTO.CartDTO;
import com.api.crud.services.CartService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/cart")
public class CartController {

    @Autowired
    private CartService cartService;

    private static final Logger logger = LoggerFactory.getLogger(CartController.class);


    @GetMapping(path = "/getCarts")
    public ResponseEntity<List<CartDTO>> getCarts(){
        logger.info("Starting to fetch carts.");
        List<CartDTO> carts = cartService.getCarts();
        ResponseEntity<List<CartDTO>> response;

        if (carts.isEmpty()) {
            logger.info("No users found");
            response = ResponseEntity.status(HttpStatus.NOT_FOUND).body(Collections.emptyList());
        } else {
            logger.info("Successfully found {} carts.", carts.size());
            response = ResponseEntity.ok(carts);
        }
        return response;
    }


    @GetMapping(path = "/findCartById/{id}")
    public ResponseEntity<CartDTO> getCartById(@PathVariable Long id) {
        return cartService.findCartById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    @GetMapping(path = "/findCartByUserId/{userId}")
    public ResponseEntity<CartDTO> getCartByUserId(@PathVariable Long userId) {
        return cartService.findCartByUserId(userId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    @PostMapping(path = "/saveCart")
    public ResponseEntity<String> saveCart(@RequestBody CartDTO cartDTO) {
        ResponseEntity<String> response;
        try {
            cartService.saveCart(cartDTO);
            response = ResponseEntity.status(HttpStatus.CREATED).body("Cart created successfully");
        } catch (Exception e) {
            response = ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error cart dont be created");
        }
        return response;
    }

    @DeleteMapping("/deleteCartById/{id}")
    public ResponseEntity<String> deleteCartById(@PathVariable Long id) {
        ResponseEntity<String> response;
        try {
            cartService.deleteCartById(id);
            response = ResponseEntity.ok("Car was erased");
        } catch (Exception e) {
            response = ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error the cart dont be erased");
        }
        return response;
    }


}

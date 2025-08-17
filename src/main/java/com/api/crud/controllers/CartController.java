package com.api.crud.controllers;

import com.api.crud.DTO.CartDTO;
import com.api.crud.services.CartService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
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

    // 1) Buscar carrito por email de usuario
    @GetMapping("/findCartByUserEmail/{email}")
    public ResponseEntity<CartDTO> findCartByUserEmail(@PathVariable String email) {
        logger.info("Received request to find cart for user with email {}", email);
        return cartService.findCartByUserEmail(email)
                .map(cart -> {
                    logger.info("Cart found for user {}", email);
                    return ResponseEntity.ok(cart);
                })
                .orElseGet(() -> {
                    logger.warn("No cart found for user {}", email);
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
                });
    }

    // 2) Buscar carrito activo por userId
    @GetMapping("/findActiveCartByUserId/{userId}")
    public ResponseEntity<CartDTO> findActiveCartByUserId(@PathVariable Long userId) {
        logger.info("Received request to find active cart for userId {}", userId);
        return cartService.findActiveCartByUserId(userId)
                .map(cart -> {
                    logger.info("Active cart found for userId {}", userId);
                    return ResponseEntity.ok(cart);
                })
                .orElseGet(() -> {
                    logger.warn("No active cart found for userId {}", userId);
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
                });
    }

    // 3) Buscar carritos en un rango de fechas
    @GetMapping("/getCartsByDateRange")
    public ResponseEntity<List<CartDTO>> getCartsByDateRange(@RequestParam("start") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
                                                             @RequestParam("end") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        logger.info("Received request to get carts from {} to {}", start, end);
        List<CartDTO> carts = cartService.getCartsByDateRange(start, end);
        if (carts.isEmpty()) {
            logger.warn("No carts found in date range {} - {}", start, end);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Collections.emptyList());
        }
        logger.info("Found {} carts in date range", carts.size());
        return ResponseEntity.ok(carts);
    }

    // 4) Buscar carritos con más de N items
    @GetMapping("/getCartsWithMoreThanNItems/{minItems}")
    public ResponseEntity<List<CartDTO>> getCartsWithMoreThanNItems(@PathVariable int minItems) {
        logger.info("Received request to get carts with more than {} items", minItems);
        List<CartDTO> carts = cartService.getCartsWithMoreThanNItems(minItems);
        if (carts.isEmpty()) {
            logger.warn("No carts found with more than {} items", minItems);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Collections.emptyList());
        }
        logger.info("Found {} carts with more than {} items", carts.size(), minItems);
        return ResponseEntity.ok(carts);
    }

    // 5) Calcular el total de un carrito
    @GetMapping("/getCartTotal/{cartId}")
    public ResponseEntity<BigDecimal> getCartTotal(@PathVariable Long cartId) {
        logger.info("Received request to calculate total for cart {}", cartId);
        try {
            BigDecimal total = cartService.getCartTotal(cartId);
            logger.info("Total for cart {} is {}", cartId, total);
            return ResponseEntity.ok(total);
        } catch (Exception e) {
            logger.error("Error calculating total for cart {}: {}", cartId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // 6) Vaciar un carrito
    @DeleteMapping("/clearCart/{cartId}")
    public ResponseEntity<String> clearCart(@PathVariable Long cartId) {
        logger.info("Received request to clear cart {}", cartId);
        try {
            cartService.clearCart(cartId);
            logger.info("Cart {} cleared successfully", cartId);
            return ResponseEntity.ok("Cart cleared successfully");
        } catch (Exception e) {
            logger.error("Error clearing cart {}: {}", cartId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to clear cart");
        }
    }



}

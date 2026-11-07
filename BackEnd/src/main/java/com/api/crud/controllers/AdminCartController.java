package com.api.crud.controllers;

import com.api.crud.DTO.CartAdminListDTO;
import com.api.crud.DTO.CartAdminDetailDTO;
import com.api.crud.DTO.AdminCartChangeReasonDTO;
import com.api.crud.services.CartItemService;
import org.springframework.security.core.Authentication;
import jakarta.validation.Valid;
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
@RequestMapping("/admin/cart")
public class AdminCartController {

    @Autowired
    private CartService cartService;

    @Autowired
    private CartItemService cartItemService;

    @PatchMapping("/{id}/disable")
    public ResponseEntity<Void> disableCart(Authentication authentication, @PathVariable Long id,
            @Valid @RequestBody AdminCartChangeReasonDTO dto) {
        cartItemService.changeAdminCartState(authentication.getName(), id, false, dto.getReason());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/enable")
    public ResponseEntity<Void> enableCart(Authentication authentication, @PathVariable Long id,
            @Valid @RequestBody AdminCartChangeReasonDTO dto) {
        cartItemService.changeAdminCartState(authentication.getName(), id, true, dto.getReason());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/deleteCartById/{id}")
    public ResponseEntity<Void> deleteCartById(Authentication authentication, @PathVariable Long id,
            @Valid @RequestBody AdminCartChangeReasonDTO dto) {
        cartItemService.deleteAdminCart(authentication.getName(), id, dto.getReason());
        return ResponseEntity.noContent().build();
    }

    private static final Logger logger = LoggerFactory.getLogger(AdminCartController.class);


    @GetMapping(path = "/getCarts")
    public ResponseEntity<List<CartAdminListDTO>> getCarts(){
        logger.info("Starting to fetch carts.");
        List<CartAdminListDTO> carts = cartService.getCarts();
        ResponseEntity<List<CartAdminListDTO>> response;

        if (carts.isEmpty()) {
            logger.info("No users found");
            response = ResponseEntity.ok(Collections.emptyList());
        } else {
            logger.info("Successfully found {} carts.", carts.size());
            response = ResponseEntity.ok(carts);
        }
        return response;
    }


    @GetMapping(path = "/getCartById/{id}")
    public ResponseEntity<CartAdminDetailDTO> getCartById(@PathVariable Long id) {
        return cartService.getCartById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    @GetMapping(path = "/getCartByUserId/{userId}")
    public ResponseEntity<CartAdminDetailDTO> getCartByUserId(@PathVariable Long userId) {
        return cartService.getCartByUserId(userId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    // 1) Buscar carrito por email de usuario
    @GetMapping("/getCartByUserEmail/{email}")
    public ResponseEntity<CartAdminDetailDTO> getCartByUserEmail(@PathVariable String email) {
        logger.info("Received request to find cart for user with email {}", email);
        return cartService.getCartByUserEmail(email)
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
    @GetMapping("/getActiveCartByUserId/{userId}")
    public ResponseEntity<CartAdminDetailDTO> getActiveCartByUserId(@PathVariable Long userId) {
        logger.info("Received request to find active cart for userId {}", userId);
        return cartService.getActiveCartByUserId(userId)
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
    public ResponseEntity<List<CartAdminListDTO>> getCartsByDateRange(@RequestParam("start") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
                                                             @RequestParam("end") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        logger.info("Received request to get carts from {} to {}", start, end);
        List<CartAdminListDTO> carts = cartService.getCartsByDateRange(start, end);
        if (carts.isEmpty()) {
            logger.warn("No carts found in date range {} - {}", start, end);
            return ResponseEntity.ok(Collections.emptyList());
        }
        logger.info("Found {} carts in date range", carts.size());
        return ResponseEntity.ok(carts);
    }

    // 4) Buscar carritos con mas de N items
    @GetMapping("/getCartsWithMoreThanNItems/{minItems}")
    public ResponseEntity<List<CartAdminListDTO>> getCartsWithMoreThanNItems(@PathVariable int minItems) {
        logger.info("Received request to get carts with more than {} items", minItems);
        List<CartAdminListDTO> carts = cartService.getCartsWithMoreThanNItems(minItems);
        if (carts.isEmpty()) {
            logger.warn("No carts found with more than {} items", minItems);
            return ResponseEntity.ok(Collections.emptyList());
        }
        logger.info("Found {} carts with more than {} items", carts.size(), minItems);
        return ResponseEntity.ok(carts);
    }

}

package com.api.crud.controllers;

import jakarta.validation.constraints.*;

import com.api.crud.DTO.CartAdminListDTO;
import com.api.crud.DTO.CartAdminDetailDTO;
import com.api.crud.DTO.ChangeReasonRequestDTO;
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
    public ResponseEntity<Void> disableCart(Authentication authentication, @PathVariable @Positive Long id,
            @Valid @RequestBody ChangeReasonRequestDTO dto) {
        cartItemService.changeAdminCartState(authentication.getName(), id, false, dto.getReason());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/enable")
    public ResponseEntity<Void> enableCart(Authentication authentication, @PathVariable @Positive Long id,
            @Valid @RequestBody ChangeReasonRequestDTO dto) {
        cartItemService.changeAdminCartState(authentication.getName(), id, true, dto.getReason());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/deleteCartById/{id}")
    public ResponseEntity<Void> deleteCartById(Authentication authentication, @PathVariable @Positive Long id,
            @Valid @RequestBody ChangeReasonRequestDTO dto) {
        cartItemService.deleteAdminCart(authentication.getName(), id, dto.getReason());
        return ResponseEntity.noContent().build();
    }

    private static final Logger logger = LoggerFactory.getLogger(AdminCartController.class);


    @GetMapping(path = "/getCarts")
    public ResponseEntity<org.springframework.data.domain.Page<CartAdminListDTO>> getCarts(
            @Valid @ModelAttribute com.api.crud.DTO.CartAdminFilterDTO filter,
            @org.springframework.data.web.PageableDefault(size = 10) org.springframework.data.domain.Pageable pageable) {
        return ResponseEntity.ok(cartService.getCarts(filter, pageable));
    }
    @GetMapping(path = "/getCartById/{id}")
    public ResponseEntity<CartAdminDetailDTO> getCartById(@PathVariable @Positive Long id) {
        return cartService.getCartById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    @GetMapping(path = "/getCartByUserId/{userId}")
    public ResponseEntity<CartAdminDetailDTO> getCartByUserId(@PathVariable @Positive Long userId) {
        return cartService.getCartByUserId(userId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    // 1) Buscar carrito por email de usuario
    @GetMapping("/getCartByUserEmail/{email}")
    public ResponseEntity<CartAdminDetailDTO> getCartByUserEmail(@PathVariable @NotBlank @Email @Size(max = 254) String email) {
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

}

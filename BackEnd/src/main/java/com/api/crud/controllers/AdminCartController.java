package com.api.crud.controllers;

import com.api.crud.DTO.CartAdminDetailDTO;
import com.api.crud.DTO.CartAdminFilterDTO;
import com.api.crud.DTO.CartAdminListDTO;
import com.api.crud.DTO.ChangeReasonRequestDTO;
import com.api.crud.exceptions.ResourceNotFoundException;
import com.api.crud.services.CartItemService;
import com.api.crud.services.CartService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/admin/cart")
public class AdminCartController {

    private static final Logger logger = LoggerFactory.getLogger(AdminCartController.class);

    @Autowired
    private CartService cartService;

    @Autowired
    private CartItemService cartItemService;

    @PatchMapping("/{id}/disable")
    public ResponseEntity<Void> disableCart(
            Authentication authentication,
            @PathVariable @Positive Long id,
            @Valid @RequestBody ChangeReasonRequestDTO dto) {
        cartItemService.changeAdminCartState(authentication.getName(), id, false, dto.getReason());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/enable")
    public ResponseEntity<Void> enableCart(
            Authentication authentication,
            @PathVariable @Positive Long id,
            @Valid @RequestBody ChangeReasonRequestDTO dto) {
        cartItemService.changeAdminCartState(authentication.getName(), id, true, dto.getReason());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/deleteCartById/{id}")
    public ResponseEntity<Void> deleteCartById(
            Authentication authentication,
            @PathVariable @Positive Long id,
            @Valid @RequestBody ChangeReasonRequestDTO dto) {
        cartItemService.deleteAdminCart(authentication.getName(), id, dto.getReason());
        return ResponseEntity.noContent().build();
    }

    @GetMapping(path = "/getCarts")
    public ResponseEntity<Page<CartAdminListDTO>> getCarts(
            @Valid @ModelAttribute CartAdminFilterDTO filter,
            @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(cartService.getCarts(filter, pageable));
    }

    @GetMapping(path = "/getCartById/{id}")
    public ResponseEntity<CartAdminDetailDTO> getCartById(@PathVariable @Positive Long id) {
        return cartService
                .getCartById(id)
                .map(ResponseEntity::ok)
                .orElseThrow(ResourceNotFoundException::new);
    }

    @GetMapping(path = "/getCartByUserId/{userId}")
    public ResponseEntity<CartAdminDetailDTO> getCartByUserId(@PathVariable @Positive Long userId) {
        return cartService
                .getCartByUserId(userId)
                .map(ResponseEntity::ok)
                .orElseThrow(ResourceNotFoundException::new);
    }

    // 1) Buscar carrito por email de usuario
    @GetMapping("/getCartByUserEmail/{email}")
    public ResponseEntity<CartAdminDetailDTO> getCartByUserEmail(
            @PathVariable @NotBlank @Email @Size(max = 254) String email) {
        logger.info("Received request to find cart for user with email {}", email);
        return cartService
                .getCartByUserEmail(email)
                .map(
                        cart -> {
                            logger.info("Cart found for user {}", email);
                            return ResponseEntity.ok(cart);
                        })
                .orElseGet(
                        () -> {
                            logger.warn("No cart found for user {}", email);
                            throw new ResourceNotFoundException();
                        });
    }

    // 3) Buscar carritos en un rango de fechas
    @GetMapping("/getCartsByDateRange")
    public ResponseEntity<List<CartAdminListDTO>> getCartsByDateRange(
            @RequestParam("start") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
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

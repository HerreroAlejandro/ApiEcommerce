package com.api.crud.controllers;

import com.api.crud.DTO.MyCartResponseDTO;
import com.api.crud.services.CartService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public ResponseEntity<MyCartResponseDTO> getMyCart(Authentication authentication) {
        return ResponseEntity.ok(cartService.findMyActiveCart(authentication.getName()));
    }

    @GetMapping("/total")
    public ResponseEntity<BigDecimal> getMyCartTotal(Authentication authentication) {
        return ResponseEntity.ok(cartService.getMyCartTotal(authentication.getName()));
    }

    @DeleteMapping("/clear")
    public ResponseEntity<Void> clearMyCart(Authentication authentication) {
        cartService.clearMyCart(authentication.getName());
        return ResponseEntity.noContent().build();
    }
}

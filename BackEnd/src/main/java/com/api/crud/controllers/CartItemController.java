package com.api.crud.controllers;

import com.api.crud.services.CartItemService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/cartItem")
public class CartItemController {
    private final CartItemService cartItemService;

    public CartItemController(CartItemService cartItemService) {
        this.cartItemService = cartItemService;
    }

    @PostMapping("/addItemToCart")
    public ResponseEntity<Void> addItemToCart(Authentication authentication,
            @RequestParam Long productId, @RequestParam int amount) {
        cartItemService.addMyItem(authentication.getName(), productId, amount);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/removeItemFromCart")
    public ResponseEntity<Void> removeItemFromCart(Authentication authentication,
            @RequestParam Long cartItemId) {
        cartItemService.removeMyItem(authentication.getName(), cartItemId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{cartItemId}/amount")
    public ResponseEntity<Void> updateCartItemAmount(Authentication authentication,
            @PathVariable Long cartItemId, @RequestParam int newAmount) {
        cartItemService.updateMyItemAmount(authentication.getName(), cartItemId, newAmount);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{cartItemId}/increase")
    public ResponseEntity<Void> increaseItemAmount(Authentication authentication,
            @PathVariable Long cartItemId, @RequestParam int increment) {
        cartItemService.increaseMyItemAmount(authentication.getName(), cartItemId, increment);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{cartItemId}/decrease")
    public ResponseEntity<Void> decreaseItemAmount(Authentication authentication,
            @PathVariable Long cartItemId, @RequestParam int decrement) {
        cartItemService.decreaseMyItemAmount(authentication.getName(), cartItemId, decrement);
        return ResponseEntity.noContent().build();
    }
}
package com.api.crud.controllers;

import com.api.crud.DTO.AdminCartItemAddRequestDTO;
import com.api.crud.DTO.AdminCartItemQuantityRequestDTO;
import com.api.crud.DTO.CartItemAdminDetailDTO;
import com.api.crud.DTO.CartItemAdminListDTO;
import com.api.crud.DTO.ChangeReasonRequestDTO;
import com.api.crud.exceptions.ResourceNotFoundException;
import com.api.crud.services.CartItemService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/cart-items")
public class AdminCartItemController {

    private final CartItemService cartItemService;

    public AdminCartItemController(CartItemService cartItemService) {
        this.cartItemService = cartItemService;
    }

    @PostMapping("/addItem")
    public ResponseEntity<Void> addItem(
            Authentication authentication, @Valid @RequestBody AdminCartItemAddRequestDTO dto) {
        cartItemService.addAdminItem(
                authentication.getName(),
                dto.getUserId(),
                dto.getProductId(),
                dto.getAmount(),
                dto.getReason());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{itemId}/amount")
    public ResponseEntity<Void> updateAmount(
            Authentication authentication,
            @PathVariable @Positive Long itemId,
            @Valid @RequestBody AdminCartItemQuantityRequestDTO dto) {
        cartItemService.updateAdminItemAmount(
                authentication.getName(), itemId, dto.getAmount(), dto.getReason());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{itemId}/increase")
    public ResponseEntity<Void> increaseAmount(
            Authentication authentication,
            @PathVariable @Positive Long itemId,
            @Valid @RequestBody AdminCartItemQuantityRequestDTO dto) {
        cartItemService.increaseAdminItemAmount(
                authentication.getName(), itemId, dto.getAmount(), dto.getReason());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{itemId}/decrease")
    public ResponseEntity<Void> decreaseAmount(
            Authentication authentication,
            @PathVariable @Positive Long itemId,
            @Valid @RequestBody AdminCartItemQuantityRequestDTO dto) {
        cartItemService.decreaseAdminItemAmount(
                authentication.getName(), itemId, dto.getAmount(), dto.getReason());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{itemId}")
    public ResponseEntity<Void> removeItem(
            Authentication authentication,
            @PathVariable @Positive Long itemId,
            @Valid @RequestBody ChangeReasonRequestDTO dto) {
        cartItemService.removeAdminItem(authentication.getName(), itemId, dto.getReason());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/cart/{cartId}/clear")
    public ResponseEntity<Void> clearCart(
            Authentication authentication,
            @PathVariable @Positive Long cartId,
            @Valid @RequestBody ChangeReasonRequestDTO dto) {
        cartItemService.clearAdminCart(authentication.getName(), cartId, dto.getReason());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/getCartItemById/{cartItemId}")
    public ResponseEntity<CartItemAdminDetailDTO> getCartItemById(
            @PathVariable @Positive Long cartItemId) {
        return cartItemService
                .getCartItemById(cartItemId)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResourceNotFoundException());
    }

    @GetMapping("/getItemsByCartId/{cartId}")
    public ResponseEntity<List<CartItemAdminListDTO>> getItemsByCartId(
            @PathVariable @Positive Long cartId) {
        return ResponseEntity.ok(cartItemService.getItemsByCartId(cartId));
    }
}

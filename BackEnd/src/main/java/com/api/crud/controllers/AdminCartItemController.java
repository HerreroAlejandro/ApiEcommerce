package com.api.crud.controllers;
import com.api.crud.DTO.CartItemAdminDetailDTO;
import com.api.crud.DTO.CartItemAdminListDTO;
import com.api.crud.DTO.AdminCartItemAddRequestDTO;
import com.api.crud.DTO.CartItemQuantityUpdateRequestDTO;
import com.api.crud.DTO.CartItemQuantityAdjustmentRequestDTO;
import com.api.crud.DTO.AdminCartChangeReasonDTO;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import com.api.crud.services.CartItemService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/admin/cart-items")
public class AdminCartItemController {
    private final CartItemService cartItemService;
    
    public AdminCartItemController(CartItemService cartItemService) {
        this.cartItemService = cartItemService;
    }

    @PostMapping
    public ResponseEntity<Void> addItem(Authentication authentication,
            @Valid @RequestBody AdminCartItemAddRequestDTO dto) {
        cartItemService.addAdminItem(authentication.getName(), dto.getUserId(), dto.getProductId(),
                dto.getAmount(), dto.getReason());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{itemId}/amount")
    public ResponseEntity<Void> updateAmount(Authentication authentication, @PathVariable Long itemId,
            @Valid @RequestBody CartItemQuantityUpdateRequestDTO dto) {
        cartItemService.updateAdminItemAmount(authentication.getName(), itemId, dto.getAmount(), dto.getReason());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{itemId}/increase")
    public ResponseEntity<Void> increaseAmount(Authentication authentication, @PathVariable Long itemId,
            @Valid @RequestBody CartItemQuantityAdjustmentRequestDTO dto) {
        cartItemService.increaseAdminItemAmount(authentication.getName(), itemId, dto.getAmount(), dto.getReason());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{itemId}/decrease")
    public ResponseEntity<Void> decreaseAmount(Authentication authentication, @PathVariable Long itemId,
            @Valid @RequestBody CartItemQuantityAdjustmentRequestDTO dto) {
        cartItemService.decreaseAdminItemAmount(authentication.getName(), itemId, dto.getAmount(), dto.getReason());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{itemId}")
    public ResponseEntity<Void> removeItem(Authentication authentication, @PathVariable Long itemId,
            @Valid @RequestBody AdminCartChangeReasonDTO dto) {
        cartItemService.removeAdminItem(authentication.getName(), itemId, dto.getReason());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/cart/{cartId}/clear")
    public ResponseEntity<Void> clearCart(Authentication authentication, @PathVariable Long cartId,
            @Valid @RequestBody AdminCartChangeReasonDTO dto) {
        cartItemService.clearAdminCart(authentication.getName(), cartId, dto.getReason());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/findCartItemById/{cartItemId}")
    public ResponseEntity<CartItemAdminDetailDTO> findCartItemById(@PathVariable Long cartItemId) {
        return cartItemService.findCartItemById(cartItemId).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/findItemsByCartId/{cartId}")
    public ResponseEntity<List<CartItemAdminListDTO>> getItemsByCartId(@PathVariable Long cartId) {
        return ResponseEntity.ok(cartItemService.findItemsByCartId(cartId));
    }
}

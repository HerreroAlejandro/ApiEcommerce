package com.api.crud.controllers;

import com.api.crud.DTO.CartItemDTO;
import com.api.crud.services.CartItemService;
import com.api.crud.services.CartService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/cartItem")
public class CartItemController {

    @Autowired
    private CartService cartService;

    @Autowired
    private CartItemService cartItemService;

    @PostMapping (path = "/addItemToCart")
    public ResponseEntity<String> addItemToCart(@RequestParam Long userId, @RequestParam Long productId, @RequestParam int amount) {
        ResponseEntity<String> response;

        try {
            cartItemService.addItemToCart(userId, productId, amount);
            response = ResponseEntity.ok("Producto agregado al carrito correctamente.");
        } catch (IllegalArgumentException e) {
            response = ResponseEntity.badRequest().body("Error: " + e.getMessage());
        } catch (Exception e) {
            response = ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error inesperado al agregar el producto al carrito.");
        }
        return response;
    }

    @GetMapping("/findCartItemById/{cartItemId}")
    public ResponseEntity<CartItemDTO> findCartItemById(@PathVariable Long cartItemId) {
        Optional<CartItemDTO> cartItemDTOOpt = cartItemService.findCartItemById(cartItemId);

        if (cartItemDTOOpt.isPresent()) {
            return ResponseEntity.ok(cartItemDTOOpt.get());
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
    }

    @GetMapping("/findItemsByCartId/{cartId}")
    public ResponseEntity<List<CartItemDTO>> getItemsByCartId(@RequestParam Long cartId) {
        List<CartItemDTO> cartItems;

        try {
            cartItems = cartItemService.findItemsByCartId(cartId);
            return ResponseEntity.ok(cartItems);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(null);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @GetMapping("/findByCartAndProductId/{cartId}/{productId}")
    public ResponseEntity<CartItemDTO> findByCartAndProductId(@PathVariable Long cartId, @PathVariable Long productId) {
        Optional<CartItemDTO> cartItemDTO = cartService.findByCartAndProductId(cartId, productId);

        return cartItemDTO.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping
    public ResponseEntity<String> removeItemFromCart(@RequestParam Long cartId, @RequestParam Long cartItemId) {
        ResponseEntity<String> response;

        try {
            cartItemService.removeItemFromCart(cartId, cartItemId);
            response = ResponseEntity.ok("Producto eliminado del carrito correctamente.");
        } catch (IllegalArgumentException e) {
            response = ResponseEntity.badRequest().body("Error: " + e.getMessage());
        } catch (Exception e) {
            response = ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error inesperado al eliminar el producto del carrito.");
        }
        return response;
    }

    // 1) Actualizar cantidad de un item en el carrito
    @PutMapping("/{cartItemId}/amount")
    public ResponseEntity<String> updateCartItemAmount(
            @PathVariable Long cartItemId,
            @RequestParam int newAmount) {
        try {
            cartItemService.updateCartItemAmount(cartItemId, newAmount);
            return ResponseEntity.ok("Cantidad actualizada correctamente");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al actualizar la cantidad: " + e.getMessage());
        }
    }

    // 2) Obtener todos los items de un carrito con info completa
    @GetMapping("/cart/{cartId}")
    public ResponseEntity<List<CartItemDTO>> getItemsFullByCartId(@PathVariable Long cartId) {
        try {
            List<CartItemDTO> items = cartItemService.getItemsFullByCartId(cartId);
            if (items.isEmpty()) {
                return ResponseEntity.noContent().build();
            }
            return ResponseEntity.ok(items);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // 3) Aumentar cantidad de un item
    @PatchMapping("/{cartItemId}/increase")
    public ResponseEntity<String> increaseItemAmount(
            @PathVariable Long cartItemId,
            @RequestParam int increment) {
        try {
            cartItemService.increaseItemAmount(cartItemId, increment);
            return ResponseEntity.ok("Cantidad aumentada correctamente");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al aumentar la cantidad: " + e.getMessage());
        }
    }

    // 4) Disminuir cantidad de un item
    @PatchMapping("/{cartItemId}/decrease")
    public ResponseEntity<String> decreaseItemAmount(
            @PathVariable Long cartItemId,
            @RequestParam int decrement) {
        try {
            cartItemService.decreaseItemAmount(cartItemId, decrement);
            return ResponseEntity.ok("Cantidad disminuida correctamente");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al disminuir la cantidad: " + e.getMessage());
        }
    }

}

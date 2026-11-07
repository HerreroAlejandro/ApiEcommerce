package com.api.crud.controllers;
import com.api.crud.DTO.OrderItemResponseDTO;
import com.api.crud.services.OrderItemService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/order-items")
public class OrderItemController {
    private final OrderItemService service;
    public OrderItemController(OrderItemService service) { this.service = service; }

    @GetMapping("/{itemId}")
    public ResponseEntity<OrderItemResponseDTO> getOrderItemById(Authentication authentication,
            @PathVariable Long itemId) {
        return ResponseEntity.ok(service.getMyItem(authentication.getName(), itemId));
    }

    @GetMapping("/order/{orderId}")
    public ResponseEntity<List<OrderItemResponseDTO>> getItemsByOrderId(Authentication authentication,
            @PathVariable Long orderId) {
        return ResponseEntity.ok(service.getMyItems(authentication.getName(), orderId));
    }
}
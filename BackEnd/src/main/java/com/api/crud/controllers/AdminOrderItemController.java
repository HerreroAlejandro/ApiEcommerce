package com.api.crud.controllers;

import com.api.crud.DTO.OrderItemAdminDetailDTO;
import com.api.crud.DTO.OrderItemResponseDTO;
import com.api.crud.services.OrderItemService;

import jakarta.validation.constraints.*;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/order-items")
public class AdminOrderItemController {

    private final OrderItemService service;

    public AdminOrderItemController(OrderItemService service) {
        this.service = service;
    }

    @GetMapping("/{itemId}")
    public ResponseEntity<OrderItemAdminDetailDTO> getOrderItemById(
            @PathVariable @Positive Long itemId) {
        return ResponseEntity.ok(service.getAdminItem(itemId));
    }

    @GetMapping("/order/{orderId}")
    public ResponseEntity<List<OrderItemResponseDTO>> getItemsByOrderId(
            @PathVariable @Positive Long orderId) {
        return ResponseEntity.ok(service.getAdminItems(orderId));
    }
}

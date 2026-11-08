package com.api.crud.controllers;

import com.api.crud.DTO.*;
import com.api.crud.models.enums.*;
import com.api.crud.services.OrderService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import org.springframework.data.domain.*;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/admin/orders")
public class AdminOrderController {

    private final OrderService orderService;

    public AdminOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PatchMapping("/{orderId}/shipment")
    public ResponseEntity<Void> updateShipment(
            Authentication authentication,
            @PathVariable @Positive Long orderId,
            @Valid @RequestBody ShipmentTrackingUpdateRequestDTO request) {
        orderService.updateShipment(authentication.getName(), orderId, request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/getOrders")
    public ResponseEntity<Page<OrderAdminListDTO>> getOrders(
            @RequestParam(required = false) @Email @Size(max = 254) String email,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) PaymentStatus paymentStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    LocalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    LocalDateTime end,
            @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(
                orderService.getAdminOrders(email, status, paymentStatus, start, end, pageable));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderAdminDetailDTO> getOrderById(@PathVariable @Positive Long orderId) {
        return ResponseEntity.ok(orderService.getAdminOrder(orderId));
    }

    @PatchMapping("/{orderId}/status")
    public ResponseEntity<Void> updateOrderStatus(
            Authentication authentication,
            @PathVariable @Positive Long orderId,
            @Valid @RequestBody OrderStatusUpdateRequestDTO dto) {
        orderService.updateOrderStatus(
                authentication.getName(), orderId, dto.getStatus(), dto.getReason());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{orderId}/payment-status")
    public ResponseEntity<Void> updatePaymentStatus(
            Authentication authentication,
            @PathVariable @Positive Long orderId,
            @Valid @RequestBody PaymentStatusUpdateRequestDTO dto) {
        orderService.updatePaymentStatus(
                authentication.getName(), orderId, dto.getStatus(), dto.getReason());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{orderId}/cancel")
    public ResponseEntity<Void> cancelOrder(
            Authentication authentication,
            @PathVariable @Positive Long orderId,
            @Valid @RequestBody ChangeReasonRequestDTO dto) {
        orderService.cancelAdminOrder(authentication.getName(), orderId, dto.getReason());
        return ResponseEntity.noContent().build();
    }
}

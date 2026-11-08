package com.api.crud.controllers;

import com.api.crud.DTO.ChangeReasonRequestDTO;
import com.api.crud.DTO.DigitalDeliveryResponseDTO;
import com.api.crud.services.DigitalDeliveryService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/digital-deliveries")
public class AdminDigitalDeliveryController {

    private final DigitalDeliveryService deliveries;

    public AdminDigitalDeliveryController(DigitalDeliveryService deliveries) {
        this.deliveries = deliveries;
    }

    @GetMapping("/order/{orderId}")
    public ResponseEntity<List<DigitalDeliveryResponseDTO>> getDeliveries(
            Authentication authentication, @PathVariable @Positive Long orderId) {
        return ResponseEntity.ok(deliveries.getAdminDeliveries(authentication.getName(), orderId));
    }

    @PostMapping("/{id}/reopen")
    public ResponseEntity<Void> reopen(
            Authentication authentication,
            @PathVariable @Positive Long id,
            @Valid @RequestBody ChangeReasonRequestDTO request) {
        deliveries.reopen(authentication.getName(), id, request.getReason());
        return ResponseEntity.noContent().build();
    }
}

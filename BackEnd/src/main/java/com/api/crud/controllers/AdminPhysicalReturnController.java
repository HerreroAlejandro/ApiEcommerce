package com.api.crud.controllers;

import com.api.crud.DTO.*;
import com.api.crud.services.PhysicalReturnService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/orders/{orderId}/physical-return")
public class AdminPhysicalReturnController {

    private final PhysicalReturnService returns;

    public AdminPhysicalReturnController(PhysicalReturnService returns) {
        this.returns = returns;
    }

    @GetMapping
    public ResponseEntity<PhysicalReturnResponseDTO> get(
            Authentication authentication, @PathVariable @Positive Long orderId) {
        return response(returns.get(authentication.getName(), orderId));
    }

    @PostMapping
    public ResponseEntity<PhysicalReturnResponseDTO> request(
            Authentication authentication,
            @PathVariable @Positive Long orderId,
            @Valid @RequestBody ChangeReasonRequestDTO request) {
        return response(returns.request(authentication.getName(), orderId, request.getReason()));
    }

    @PostMapping("/receive")
    public ResponseEntity<PhysicalReturnResponseDTO> receive(
            Authentication authentication,
            @PathVariable @Positive Long orderId,
            @Valid @RequestBody PhysicalReturnReceiveRequestDTO request) {
        return response(returns.receive(authentication.getName(), orderId, request));
    }

    @PostMapping("/waive")
    public ResponseEntity<PhysicalReturnResponseDTO> waive(
            Authentication authentication,
            @PathVariable @Positive Long orderId,
            @Valid @RequestBody ChangeReasonRequestDTO request) {
        return response(returns.waive(authentication.getName(), orderId, request.getReason()));
    }

    private ResponseEntity<PhysicalReturnResponseDTO> response(PhysicalReturnResponseDTO result) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result);
    }
}

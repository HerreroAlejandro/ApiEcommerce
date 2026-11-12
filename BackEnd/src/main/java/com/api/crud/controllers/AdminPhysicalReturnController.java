package com.api.crud.controllers;

import com.api.crud.DTO.*;
import com.api.crud.models.enums.PhysicalReturnStatus;
import com.api.crud.services.PhysicalReturnService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin")
public class AdminPhysicalReturnController {

    private final PhysicalReturnService returns;

    public AdminPhysicalReturnController(PhysicalReturnService returns) {
        this.returns = returns;
    }

    @GetMapping("/physical-returns")
    public ResponseEntity<Page<PhysicalReturnListDTO>> getReturns(
            Authentication authentication,
            @RequestParam(required = false) PhysicalReturnStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(
                        returns.getReturns(
                                authentication.getName(), status, PageRequest.of(page, size)));
    }

    @GetMapping("/orders/{orderId}/physical-return")
    public ResponseEntity<PhysicalReturnResponseDTO> get(
            Authentication authentication, @PathVariable @Positive Long orderId) {
        return response(returns.get(authentication.getName(), orderId));
    }

    @PostMapping("/orders/{orderId}/physical-return")
    public ResponseEntity<PhysicalReturnResponseDTO> request(
            Authentication authentication,
            @PathVariable @Positive Long orderId,
            @Valid @RequestBody ChangeReasonRequestDTO request) {
        return response(returns.request(authentication.getName(), orderId, request.getReason()));
    }

    @PostMapping("/orders/{orderId}/physical-return/receive")
    public ResponseEntity<PhysicalReturnResponseDTO> receive(
            Authentication authentication,
            @PathVariable @Positive Long orderId,
            @Valid @RequestBody PhysicalReturnReceiveRequestDTO request) {
        return response(returns.receive(authentication.getName(), orderId, request));
    }

    @PostMapping("/orders/{orderId}/physical-return/waive")
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

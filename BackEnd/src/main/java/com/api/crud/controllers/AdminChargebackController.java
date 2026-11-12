package com.api.crud.controllers;

import com.api.crud.DTO.*;
import com.api.crud.models.enums.ChargebackResolution;
import com.api.crud.services.ChargebackPersistence;
import com.api.crud.services.ChargebackService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/chargebacks")
public class AdminChargebackController {
    private final ChargebackPersistence persistence;
    private final ChargebackService service;

    public AdminChargebackController(ChargebackPersistence persistence, ChargebackService service) {
        this.persistence = persistence;
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<Page<ChargebackListDTO>> getCases(
            Authentication authentication,
            @RequestParam(required = false) ChargebackResolution resolution,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(
                        persistence.getCases(
                                authentication.getName(), resolution, PageRequest.of(page, size)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ChargebackResponseDTO> getCase(
            Authentication authentication, @PathVariable @Positive Long id) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(persistence.get(authentication.getName(), id));
    }

    @PostMapping("/{id}/sync")
    public ResponseEntity<ChargebackResponseDTO> synchronize(
            Authentication authentication, @PathVariable @Positive Long id) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.synchronize(authentication.getName(), id));
    }

    @PostMapping("/{id}/notes")
    public ResponseEntity<ChargebackResponseDTO> note(
            Authentication authentication,
            @PathVariable @Positive Long id,
            @Valid @RequestBody ChangeReasonRequestDTO request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(persistence.note(authentication.getName(), id, request.getReason()));
    }
}

package com.api.crud.controllers;

import com.api.crud.DTO.*;
import com.api.crud.services.StoreSaleService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/store-sales")
public class AdminStoreSaleController {

    private final StoreSaleService sales;

    public AdminStoreSaleController(StoreSaleService sales) {
        this.sales = sales;
    }

    @PostMapping
    public ResponseEntity<OrderAdminDetailDTO> create(
            Authentication authentication, @Valid @RequestBody StoreSaleCreateRequestDTO request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(sales.create(authentication.getName(), request));
    }

    @PostMapping("/{orderId}/refund")
    public ResponseEntity<OrderAdminDetailDTO> refund(
            Authentication authentication,
            @PathVariable @Positive Long orderId,
            @Valid @RequestBody ChangeReasonRequestDTO request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(sales.refund(authentication.getName(), orderId, request.getReason()));
    }
}

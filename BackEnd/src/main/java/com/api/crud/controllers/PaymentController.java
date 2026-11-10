package com.api.crud.controllers;

import com.api.crud.DTO.CheckoutResponseDTO;
import com.api.crud.DTO.PaymentResponseDTO;
import com.api.crud.services.PaymentService;

import jakarta.validation.constraints.Positive;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/orders")
public class PaymentController {

    private final PaymentService payments;

    public PaymentController(PaymentService payments) {
        this.payments = payments;
    }
    
    //Solicitar inicio de pago, el comprador inicia o recupera el checkout de su orden.
    @PostMapping("/{orderId}/checkout")
    public ResponseEntity<CheckoutResponseDTO> createCheckout(
            Authentication authentication, @PathVariable @Positive Long orderId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(payments.createCheckout(authentication.getName(), orderId));
    }

    //El comprador consulta sus intentos de pago.
    @GetMapping("/{orderId}/payments")
    public ResponseEntity<List<PaymentResponseDTO>> getMyPayments(
            Authentication authentication, @PathVariable @Positive Long orderId) {
        return ResponseEntity.ok(payments.getMyPayments(authentication.getName(), orderId));
    }
}

package com.api.crud.controllers;

import com.api.crud.services.PaymentService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/payments/mercadopago")
public class MercadoPagoWebhookController {

    private final PaymentService payments;

    public MercadoPagoWebhookController(PaymentService payments) {
        this.payments = payments;
    }

    // Aviso que Mercado Pago envía directamente a nuestro servidor. Puede llegar aunque el cliente
    // cierre el navegador.
    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(
            @RequestParam(name = "data.id", required = false) String dataId,
            @RequestParam(required = false) String type,
            @RequestHeader(name = "x-request-id", required = false) String requestId,
            @RequestHeader(name = "x-signature", required = false) String signature) {
        if (type == null
                || type.equals("order")
                || type.equals("orders_v2")
                || type.equals("chargebacks")) {
            payments.webhook(dataId, requestId, signature);
        }
        return ResponseEntity.ok().build();
    }
}

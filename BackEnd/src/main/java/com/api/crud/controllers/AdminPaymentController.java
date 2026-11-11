package com.api.crud.controllers;

import com.api.crud.DTO.ChangeReasonRequestDTO;
import com.api.crud.DTO.PaymentAdminResponseDTO;
import com.api.crud.DTO.PaymentRefundResponseDTO;
import com.api.crud.DTO.PaymentResponseDTO;
import com.api.crud.models.enums.PaymentRefundStatus;
import com.api.crud.services.PaymentRefundService;
import com.api.crud.services.PaymentService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin")
public class AdminPaymentController {

    private final PaymentService payments;

    private final PaymentRefundService refunds;

    public AdminPaymentController(PaymentService payments, PaymentRefundService refunds) {
        this.payments = payments;
        this.refunds = refunds;
    }

    @PostMapping("/payments/{paymentId}/refund")
    public ResponseEntity<PaymentRefundResponseDTO> refundPayment(
            Authentication authentication,
            @PathVariable @Positive Long paymentId,
            @Valid @RequestBody ChangeReasonRequestDTO request) {
        PaymentRefundResponseDTO refund =
                refunds.refund(authentication.getName(), paymentId, request.getReason());
        return ResponseEntity.status(
                        refund.getStatus() == PaymentRefundStatus.CONFIRMED
                                        || refund.getStatus() == PaymentRefundStatus.REJECTED
                                ? 200
                                : 202)
                .cacheControl(CacheControl.noStore())
                .body(refund);
    }

    @GetMapping("/payments/{paymentId}/refund")
    public ResponseEntity<PaymentRefundResponseDTO> getRefund(
            Authentication authentication, @PathVariable @Positive Long paymentId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(refunds.getRefund(authentication.getName(), paymentId));
    }

    // Admin encuentra operaciones con observaciones.
    @GetMapping("/payments/review")
    public ResponseEntity<Page<PaymentAdminResponseDTO>> getReviewPayments(
            Authentication authentication,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(
                payments.getReviewPayments(authentication.getName(), PageRequest.of(page, size)));
    }

    // consulta las operaciones de una orden.
    @GetMapping("/orders/{orderId}/payments")
    public ResponseEntity<List<PaymentResponseDTO>> getPayments(
            Authentication authentication, @PathVariable @Positive Long orderId) {
        return ResponseEntity.ok(payments.getAdminPayments(authentication.getName(), orderId));
    }

    // ADMIN pide consultar nuevamente una operación al proveedor.
    @PostMapping("/payment-checkouts/{providerOrderId}/sync")
    public ResponseEntity<List<PaymentResponseDTO>> synchronize(
            Authentication authentication,
            @PathVariable @Pattern(regexp = "ORD[A-Z0-9]{1,61}") String providerOrderId) {
        return ResponseEntity.ok(payments.synchronize(authentication.getName(), providerOrderId));
    }
}

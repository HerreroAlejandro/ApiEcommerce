package com.api.crud.controllers;

import com.api.crud.DTO.DigitalDeliveryResponseDTO;
import com.api.crud.DTO.DownloadTokenResponseDTO;
import com.api.crud.exceptions.InvalidRequestException;
import com.api.crud.services.DigitalDeliveryService;
import com.api.crud.services.DigitalFileGateway;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.*;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
public class DigitalDeliveryController {

    private final DigitalDeliveryService deliveries;

    private final DigitalFileGateway files;

    public DigitalDeliveryController(DigitalDeliveryService deliveries, DigitalFileGateway files) {
        this.deliveries = deliveries;
        this.files = files;
    }

    @GetMapping("/orders/{orderId}/digital-deliveries")
    public ResponseEntity<List<DigitalDeliveryResponseDTO>> getMyDeliveries(
            Authentication authentication, @PathVariable @Positive Long orderId) {
        return ResponseEntity.ok(deliveries.getMyDeliveries(authentication.getName(), orderId));
    }

    @PostMapping("/digital-deliveries/{id}/download-token")
    public ResponseEntity<DownloadTokenResponseDTO> issueToken(
            Authentication authentication, @PathVariable @Positive Long id) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(deliveries.issueToken(authentication.getName(), id));
    }

    @GetMapping("/digital-deliveries/{id}/download")
    public void download(
            Authentication authentication,
            @PathVariable @Positive Long id,
            @RequestHeader("X-Download-Token") @NotBlank @Size(max = 128) String token,
            HttpServletRequest request,
            HttpServletResponse response)
            throws IOException {
        if (!request.getMethod().equals("GET") || request.getHeader("Range") != null) {
            throw new InvalidRequestException("La descarga requiere GET completo, sin rangos");
        }
        files.stream(deliveries.claim(authentication.getName(), id, token), response);
    }
}

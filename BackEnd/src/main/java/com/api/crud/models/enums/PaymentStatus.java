package com.api.crud.models.enums;

public enum PaymentStatus {
    PENDING,      // El pago está pendiente
    PAID,         // El pago está realizado
    FAILED,       // El pago falló
    REFUNDED      // El pago fue devuelto
}

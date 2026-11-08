package com.api.crud.models.enums;

public enum PaymentStatus {

    // El pago está pendiente
    PENDING,
    // El pago está realizado
    PAID,
    // El pago falló
    FAILED,
    // El pago fue devuelto
    REFUNDED
}

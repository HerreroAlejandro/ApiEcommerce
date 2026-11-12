package com.api.crud.models.enums;

public enum PaymentStatus {

    // El pago está pendiente
    PENDING,
    // El pago está realizado
    PAID,
    PARTIALLY_REFUNDED,
    CHARGED_BACK,
    // El pago falló
    FAILED,
    // El pago fue devuelto
    REFUNDED,
    // Cobro o devolucion que requiere revision administrativa
    REVIEW
}

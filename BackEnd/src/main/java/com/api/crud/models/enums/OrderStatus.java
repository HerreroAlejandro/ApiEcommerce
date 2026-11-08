package com.api.crud.models.enums;

public enum OrderStatus {

    // Pedido generado pero no procesado
    PENDING,
    // Confirmado
    CONFIRMED,
    // Enviado
    SHIPPED,
    // Entregado
    DELIVERED,
    // Cancelado
    CANCELED,
    // Reserva vencida sin pago
    EXPIRED
}

package com.api.crud.models.enums;

public enum OrderStatus {
    PENDING,      // Pedido generado pero no procesado
    CONFIRMED,    // Confirmado
    SHIPPED,      // Enviado
    DELIVERED,    // Entregado
    CANCELED      // Cancelado
}

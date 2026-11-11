package com.api.crud.DTO;

import com.api.crud.models.enums.DeliveryMethod;
import com.api.crud.models.enums.OrderStatus;
import com.api.crud.models.enums.PaymentStatus;
import com.api.crud.models.enums.SalesChannel;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class OrderAdminDetailDTO {

    @Getter
    @Setter
    private SalesChannel salesChannel;

    @Getter
    @Setter
    private DeliveryMethod deliveryMethod;

    @Getter
    @Setter
    private StoreSaleResponseDTO storeSale;

    @Getter
    @Setter
    private Long idOrder;

    @Getter
    @Setter
    private BigDecimal totalOrder;

    @Getter
    @Setter
    private BigDecimal itemsSubtotal;

    @Getter
    @Setter
    private BigDecimal shippingCost;

    @Getter
    @Setter
    private ShippingAddressDTO shippingAddress;

    @Getter
    @Setter
    private ShipmentTrackingDTO shipmentTracking;

    @Getter
    @Setter
    private LocalDateTime purchaseDateOrder;

    @Getter
    @Setter
    private LocalDateTime expiresAt;

    @Getter
    @Setter
    private OrderStatus orderStatus;

    @Getter
    @Setter
    private PaymentStatus paymentStatus;

    @Getter
    @Setter
    private Long userId;

    @Getter
    @Setter
    private String userEmail;

    @Getter
    @Setter
    private List<OrderItemResponseDTO> itemsOrder;
}

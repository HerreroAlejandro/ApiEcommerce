package com.api.crud.controllers;
import com.api.crud.DTO.*;
import com.api.crud.models.enums.*;
import com.api.crud.services.OrderService;
import jakarta.validation.Valid;
import org.springframework.data.domain.*;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.net.URI;

@RestController
@RequestMapping("/orders")
public class OrderController {
    private final OrderService orderService;
    public OrderController(OrderService orderService) { this.orderService = orderService; }

    @PostMapping("/createOrder")
    public ResponseEntity<OrderDetailDTO> createOrder(Authentication authentication) {
        OrderDetailDTO dto = orderService.createMyOrder(authentication.getName());
        return ResponseEntity.created(URI.create("/orders/" + dto.getIdOrder())).body(dto);
    }

    @GetMapping("/getMyOrders")
    public ResponseEntity<Page<OrderListDTO>> getMyOrders(Authentication authentication,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) PaymentStatus paymentStatus,
            @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(orderService.getMyOrders(authentication.getName(), status, paymentStatus, pageable));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderDetailDTO> getOrderById(Authentication authentication, @PathVariable Long orderId) {
        return ResponseEntity.ok(orderService.getMyOrder(authentication.getName(), orderId));
    }

    @PatchMapping("/{orderId}/cancel")
    public ResponseEntity<Void> cancelOrder(Authentication authentication, @PathVariable Long orderId,
            @Valid @RequestBody OrderCancelRequestDTO dto) {
        orderService.cancelMyOrder(authentication.getName(), orderId, dto.getReason());
        return ResponseEntity.noContent().build();
    }
}
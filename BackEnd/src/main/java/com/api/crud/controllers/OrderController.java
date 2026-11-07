package com.api.crud.controllers;

import com.api.crud.DTO.OrderDTO;
import com.api.crud.DTO.OrderItemDTO;
import com.api.crud.models.enums.OrderStatus;
import com.api.crud.models.enums.PaymentStatus;
import com.api.crud.services.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private static final Logger logger = LoggerFactory.getLogger(OrderController.class);

    private final OrderService orderService;

    @Autowired
    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    // 1) Obtener todas las órdenes
    @GetMapping
    public ResponseEntity<List<OrderDTO>> getAllOrders() {
        logger.info("Fetching all orders...");
        ResponseEntity<List<OrderDTO>> response;
        try {
            List<OrderDTO> orders = orderService.getAllOrders();
            if (orders.isEmpty()) {
                logger.info("No orders found.");
                response = ResponseEntity.status(HttpStatus.NO_CONTENT).body(Collections.emptyList());
            } else {
                logger.info("Found {} orders.", orders.size());
                response = ResponseEntity.ok(orders);
            }
        } catch (Exception e) {
            logger.error("Error fetching orders: {}", e.getMessage());
            response = ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
        return response;
    }

    // 2) Crear una orden
    @PostMapping("/create/{userId}")
    public ResponseEntity<OrderDTO> createOrder(
            @PathVariable Long userId,
            @RequestBody List<OrderItemDTO> items) {
        logger.info("Creating order for user with ID {}", userId);
        ResponseEntity<OrderDTO> response;
        try {
            OrderDTO order = orderService.createOrder(userId, items);
            logger.info("Order created successfully with ID {}", order.getIdOrder());
            response = ResponseEntity.status(HttpStatus.CREATED).body(order);
        } catch (Exception e) {
            logger.error("Error creating order: {}", e.getMessage());
            response = ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
        return response;
    }

    // 3) Obtener orden por ID
    @GetMapping("/{orderId}")
    public ResponseEntity<OrderDTO> getOrderById(@PathVariable Long orderId) {
        logger.info("Fetching order with ID {}", orderId);
        ResponseEntity<OrderDTO> response;
        try {
            OrderDTO order = orderService.getOrderById(orderId);
            response = ResponseEntity.ok(order);
        } catch (RuntimeException e) {
            logger.warn("Order with ID {} not found", orderId);
            response = ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (Exception e) {
            logger.error("Error fetching order with ID {}: {}", orderId, e.getMessage());
            response = ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
        return response;
    }

    // 4) Obtener órdenes de un usuario por email
    @GetMapping("/user/{email}")
    public ResponseEntity<List<OrderDTO>> getOrdersByUserEmail(@PathVariable String email) {
        logger.info("Fetching orders for user with email {}", email);
        ResponseEntity<List<OrderDTO>> response;
        try {
            List<OrderDTO> orders = orderService.getOrdersByUserEmail(email);
            if (orders.isEmpty()) {
                logger.info("No orders found for user {}", email);
                response = ResponseEntity.status(HttpStatus.NO_CONTENT).body(Collections.emptyList());
            } else {
                response = ResponseEntity.ok(orders);
            }
        } catch (Exception e) {
            logger.error("Error fetching orders by email {}: {}", email, e.getMessage());
            response = ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
        return response;
    }

    // 5) Actualizar estado de la orden
    @PutMapping("/{orderId}/status")
    public ResponseEntity<String> updateOrderStatus(
            @PathVariable Long orderId,
            @RequestParam OrderStatus status) {
        logger.info("Updating order status for order ID {}", orderId);
        ResponseEntity<String> response;
        try {
            orderService.updateOrderStatus(orderId, status);
            response = ResponseEntity.ok("Order status updated successfully");
        } catch (RuntimeException e) {
            logger.warn("Order with ID {} not found", orderId);
            response = ResponseEntity.status(HttpStatus.NOT_FOUND).body("Order not found");
        } catch (Exception e) {
            logger.error("Error updating order status: {}", e.getMessage());
            response = ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error updating order status");
        }
        return response;
    }

    // 6) Actualizar estado del pago
    @PutMapping("/{orderId}/payment-status")
    public ResponseEntity<String> updatePaymentStatus(
            @PathVariable Long orderId,
            @RequestParam PaymentStatus status) {
        logger.info("Updating payment status for order ID {}", orderId);
        ResponseEntity<String> response;
        try {
            orderService.updatePaymentStatus(orderId, status);
            response = ResponseEntity.ok("Payment status updated successfully");
        } catch (RuntimeException e) {
            logger.warn("Order with ID {} not found", orderId);
            response = ResponseEntity.status(HttpStatus.NOT_FOUND).body("Order not found");
        } catch (Exception e) {
            logger.error("Error updating payment status: {}", e.getMessage());
            response = ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error updating payment status");
        }
        return response;
    }

    // 7) Cancelar orden
    @PutMapping("/{orderId}/cancel")
    public ResponseEntity<String> cancelOrder(@PathVariable Long orderId) {
        logger.info("Canceling order with ID {}", orderId);
        ResponseEntity<String> response;
        try {
            orderService.cancelOrder(orderId);
            response = ResponseEntity.ok("Order canceled successfully");
        } catch (RuntimeException e) {
            logger.warn("Order with ID {} not found", orderId);
            response = ResponseEntity.status(HttpStatus.NOT_FOUND).body("Order not found");
        } catch (Exception e) {
            logger.error("Error canceling order: {}", e.getMessage());
            response = ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error canceling order");
        }
        return response;
    }

    // 8) Eliminar orden
    @DeleteMapping("/{orderId}")
    public ResponseEntity<String> deleteOrder(@PathVariable Long orderId) {
        logger.info("Deleting order with ID {}", orderId);
        ResponseEntity<String> response;
        try {
            orderService.deleteOrder(orderId);
            response = ResponseEntity.ok("Order deleted successfully");
        } catch (RuntimeException e) {
            logger.warn("Order with ID {} not found", orderId);
            response = ResponseEntity.status(HttpStatus.NOT_FOUND).body("Order not found");
        } catch (Exception e) {
            logger.error("Error deleting order: {}", e.getMessage());
            response = ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error deleting order");
        }
        return response;
    }
}

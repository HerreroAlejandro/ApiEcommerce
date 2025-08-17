package com.api.crud.controllers;

import com.api.crud.DTO.OrderDTO;
import com.api.crud.DTO.OrderItemDTO;
import com.api.crud.DTO.ProductDTO;
import com.api.crud.models.entity.*;
import com.api.crud.services.OrderItemService;
import com.api.crud.services.OrderService;
import com.api.crud.services.ProductService;
import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;

import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/order-items")
public class OrderItemController {

    private static final Logger logger = LoggerFactory.getLogger(OrderItemController.class);

    private final OrderItemService orderItemService;

    private final OrderService orderService;

    private final ProductService productService;

    private final ModelMapper modelMapper;

    @Autowired
    public OrderItemController(OrderItemService orderItemService, OrderService orderService, ProductService productService, ModelMapper modelMapper) {
        this.orderItemService = orderItemService;
        this.orderService = orderService;
        this.productService = productService;
        this.modelMapper = modelMapper;
    }

    // 1) Guardar OrderItem
    @PostMapping("/save")
    public ResponseEntity<OrderItemDTO> saveOrderItem(
            @RequestBody OrderItemDTO dto,
            @RequestParam Long orderId,
            @RequestParam Long productId) {
        logger.info("Saving OrderItem for order {} and product {}", orderId, productId);
        ResponseEntity<OrderItemDTO> response;

        try {
            // Obtener la entidad Order real
            OrderDTO orderDTO = orderService.getOrderById(orderId);
            Order order = modelMapper.map(orderDTO, Order.class);

            // Obtener la entidad Product concreta
            ProductDTO productDTO = productService.findProductById(productId)
                    .orElseThrow(() -> new RuntimeException("Producto no encontrado"));

            Product product;
            if (productDTO.getDownloadLink() != null || productDTO.getLicense() != null) {
                product = modelMapper.map(productDTO, ProductDigital.class);
            } else {
                product = modelMapper.map(productDTO, ProductPhysical.class);
            }

            // Guardar OrderItem usando el servicio
            OrderItemDTO savedItem = orderItemService.saveOrderItem(dto, order, product);
            response = ResponseEntity.status(HttpStatus.CREATED).body(savedItem);
            logger.info("OrderItem created successfully with ID {}", savedItem.getIdOrderItem());
        } catch (Exception e) {
            logger.error("Error saving OrderItem: {}", e.getMessage(), e);
            response = ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }

        return response;
    }

    // 2) Obtener OrderItem por ID
    @GetMapping("/{idOrderItem}")
    public ResponseEntity<OrderItemDTO> getOrderItemById(@PathVariable Long idOrderItem) {
        logger.info("Fetching OrderItem with ID {}", idOrderItem);
        ResponseEntity<OrderItemDTO> response;
        try {
            OrderItemDTO item = orderItemService.getOrderItemById(idOrderItem);
            response = ResponseEntity.ok(item);
        } catch (RuntimeException e) {
            logger.warn("OrderItem with ID {} not found", idOrderItem);
            response = ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (Exception e) {
            logger.error("Error fetching OrderItem: {}", e.getMessage());
            response = ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
        return response;
    }

    // 3) Obtener items por ID de orden
    @GetMapping("/order/{orderId}")
    public ResponseEntity<List<OrderItemDTO>> getItemsByOrderId(@PathVariable Long orderId) {
        logger.info("Fetching OrderItems for order ID {}", orderId);
        ResponseEntity<List<OrderItemDTO>> response;
        try {
            List<OrderItemDTO> items = orderItemService.getItemsByOrderId(orderId);
            if (items.isEmpty()) {
                logger.info("No OrderItems found for order ID {}", orderId);
                response = ResponseEntity.status(HttpStatus.NO_CONTENT).body(Collections.emptyList());
            } else {
                response = ResponseEntity.ok(items);
            }
        } catch (Exception e) {
            logger.error("Error fetching OrderItems: {}", e.getMessage());
            response = ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
        return response;
    }

    // 4) Aumentar cantidad de un item
    @PutMapping("/{idOrderItem}/increase")
    public ResponseEntity<String> increaseAmount(@PathVariable Long idOrderItem, @RequestParam int increment) {
        logger.info("Increasing amount of OrderItem {} by {}", idOrderItem, increment);
        ResponseEntity<String> response;
        try {
            orderItemService.increaseAmount(idOrderItem, increment);
            response = ResponseEntity.ok("Amount increased successfully");
        } catch (Exception e) {
            logger.error("Error increasing amount: {}", e.getMessage());
            response = ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to increase amount");
        }
        return response;
    }

    // 5) Disminuir cantidad de un item
    @PutMapping("/{idOrderItem}/decrease")
    public ResponseEntity<String> decreaseAmount(@PathVariable Long idOrderItem, @RequestParam int decrement) {
        logger.info("Decreasing amount of OrderItem {} by {}", idOrderItem, decrement);
        ResponseEntity<String> response;
        try {
            orderItemService.decreaseAmount(idOrderItem, decrement);
            response = ResponseEntity.ok("Amount decreased successfully");
        } catch (Exception e) {
            logger.error("Error decreasing amount: {}", e.getMessage());
            response = ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to decrease amount");
        }
        return response;
    }

    // 6) Eliminar OrderItem
    @DeleteMapping("/{idOrderItem}")
    public ResponseEntity<String> deleteOrderItem(@PathVariable Long idOrderItem) {
        logger.info("Deleting OrderItem with ID {}", idOrderItem);
        ResponseEntity<String> response;
        try {
            orderItemService.deleteOrderItem(idOrderItem);
            response = ResponseEntity.ok("OrderItem deleted successfully");
        } catch (RuntimeException e) {
            logger.warn("OrderItem with ID {} not found", idOrderItem);
            response = ResponseEntity.status(HttpStatus.NOT_FOUND).body("OrderItem not found");
        } catch (Exception e) {
            logger.error("Error deleting OrderItem: {}", e.getMessage());
            response = ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to delete OrderItem");
        }
        return response;
    }

    // 7) Obtener subtotal de un OrderItem
    @GetMapping("/{idOrderItem}/subtotal")
    public ResponseEntity<BigDecimal> getSubtotal(@PathVariable Long idOrderItem) {
        logger.info("Fetching subtotal for OrderItem with ID {}", idOrderItem);
        ResponseEntity<BigDecimal> response;
        try {
            OrderItemDTO itemDTO = orderItemService.getOrderItemById(idOrderItem);
            // Como el método espera la entidad, podemos mapear el DTO a la entidad
            OrderItem item = modelMapper.map(itemDTO, OrderItem.class);

            BigDecimal subtotal = orderItemService.getSubtotal(item);
            response = ResponseEntity.ok(subtotal);
        } catch (RuntimeException e) {
            logger.warn("OrderItem with ID {} not found", idOrderItem);
            response = ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (Exception e) {
            logger.error("Error fetching subtotal: {}", e.getMessage(), e);
            response = ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
        return response;
    }

}

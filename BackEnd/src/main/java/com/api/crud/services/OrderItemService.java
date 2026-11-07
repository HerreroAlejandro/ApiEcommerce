package com.api.crud.services;
import com.api.crud.DTO.OrderItemResponseDTO;
import com.api.crud.DTO.OrderItemAdminDetailDTO;
import com.api.crud.models.entity.Order;
import com.api.crud.models.entity.OrderItem;
import com.api.crud.repositories.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class OrderItemService {
    private final OrderItemDao items;
    private final OrderDao orders;
    private final UserDao users;
    public OrderItemService(OrderItemDao items, OrderDao orders, UserDao users) {
        this.items = items; this.orders = orders; this.users = users;
    }

    private long userId(String email) {
        return users.getUserByEmail(email).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.UNAUTHORIZED)).getId();
    }
    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Orden o item no encontrado");
    }
    private OrderItem item(Long id) { return items.getOrderItemById(id).orElseThrow(this::notFound); }
    private Order order(Long id) { return orders.getOrderById(id).orElseThrow(this::notFound); }

    private OrderItemResponseDTO response(OrderItem item) {
        OrderItemResponseDTO dto = new OrderItemResponseDTO();
        dto.setIdOrderItem(item.getIdOrderItem());
        dto.setProductId(item.getProductOrderItem().getIdProduct());
        dto.setProductName(item.getProductName());
        dto.setAmountOrderItem(item.getAmountOrderItem());
        dto.setPriceOrderItem(item.getPriceOrderItem());
        dto.setSubtotal(item.getSubtotal());
        return dto;
    }

    public OrderItemResponseDTO getMyItem(String email, Long id) {
        long userId = userId(email);
        OrderItem item = item(id);
        if (item.getOrder().getUserOrder().getId() != userId) throw notFound();
        return response(item);
    }

    public List<OrderItemResponseDTO> getMyItems(String email, Long orderId) {
        long userId = userId(email);
        Order order = order(orderId);
        if (order.getUserOrder().getId() != userId) throw notFound();
        return items.getItemsByOrderId(orderId).stream().map(this::response).toList();
    }

    public OrderItemAdminDetailDTO getAdminItem(Long id) {
        OrderItem item = item(id);
        OrderItemResponseDTO row = response(item);
        OrderItemAdminDetailDTO dto = new OrderItemAdminDetailDTO();
        dto.setIdOrderItem(row.getIdOrderItem());
        dto.setProductId(row.getProductId());
        dto.setProductName(row.getProductName());
        dto.setAmountOrderItem(row.getAmountOrderItem());
        dto.setPriceOrderItem(row.getPriceOrderItem());
        dto.setSubtotal(row.getSubtotal());
        dto.setOrderId(item.getOrder().getIdOrder());
        dto.setUserId(item.getOrder().getUserOrder().getId());
        dto.setOrderStatus(item.getOrder().getOrderStatus());
        dto.setPaymentStatus(item.getOrder().getPaymentStatus());
        return dto;
    }

    public List<OrderItemResponseDTO> getAdminItems(Long orderId) {
        order(orderId);
        return items.getItemsByOrderId(orderId).stream().map(this::response).toList();
    }
}
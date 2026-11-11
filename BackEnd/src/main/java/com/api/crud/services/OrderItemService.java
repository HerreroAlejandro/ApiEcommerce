package com.api.crud.services;

import com.api.crud.DTO.OrderItemAdminDetailDTO;
import com.api.crud.DTO.OrderItemResponseDTO;
import com.api.crud.exceptions.*;
import com.api.crud.models.entity.Order;
import com.api.crud.models.entity.OrderItem;
import com.api.crud.repositories.*;

import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class OrderItemService {

    private final OrderItemDao items;

    private final OrderDao orders;

    private final UserDao users;

    private final ModelMapper modelMapper;

    public OrderItemService(
            OrderItemDao items, OrderDao orders, UserDao users, ModelMapper modelMapper) {
        this.items = items;
        this.orders = orders;
        this.users = users;
        this.modelMapper = modelMapper;
    }

    private long userId(String email) {
        return users.getUserByEmail(email)
                .orElseThrow(() -> new UnauthenticatedException())
                .getId();
    }

    private ApplicationException notFound() {
        return new ResourceNotFoundException("Orden o item no encontrado");
    }

    private OrderItem item(Long id) {
        return items.getOrderItemById(id).orElseThrow(this::notFound);
    }

    private Order order(Long id) {
        return orders.getOrderById(id).orElseThrow(this::notFound);
    }

    private OrderItemResponseDTO response(OrderItem item) {
        return modelMapper.map(item, OrderItemResponseDTO.class);
    }

    public OrderItemResponseDTO getMyItem(String email, Long id) {
        long userId = userId(email);
        OrderItem item = item(id);
        if (item.getOrder().getUserOrder() == null
                || item.getOrder().getUserOrder().getId() != userId) throw notFound();
        return response(item);
    }

    public List<OrderItemResponseDTO> getMyItems(String email, Long orderId) {
        long userId = userId(email);
        Order order = order(orderId);
        if (order.getUserOrder() == null || order.getUserOrder().getId() != userId)
            throw notFound();
        return items.getItemsByOrderId(orderId).stream().map(this::response).toList();
    }

    public OrderItemAdminDetailDTO getAdminItem(Long id) {
        return modelMapper.map(item(id), OrderItemAdminDetailDTO.class);
    }

    public List<OrderItemResponseDTO> getAdminItems(Long orderId) {
        order(orderId);
        return items.getItemsByOrderId(orderId).stream().map(this::response).toList();
    }
}

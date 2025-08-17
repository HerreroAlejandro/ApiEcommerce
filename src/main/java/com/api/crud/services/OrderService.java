package com.api.crud.services;

import com.api.crud.DTO.OrderDTO;
import com.api.crud.DTO.OrderItemDTO;
import com.api.crud.models.entity.Order;
import com.api.crud.models.entity.OrderItem;
import com.api.crud.models.entity.Product;
import com.api.crud.models.entity.UserModel;
import com.api.crud.models.enums.OrderStatus;
import com.api.crud.models.enums.PaymentStatus;
import com.api.crud.repositories.OrderDao;
import com.api.crud.repositories.UserDao;
import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class OrderService {

    private final OrderDao orderDao;
    private final UserDao userDao;
    private final ModelMapper modelMapper;

    private static final Logger logger = LoggerFactory.getLogger(OrderService.class);

    @Autowired
    public OrderService(OrderDao orderDao, UserDao userDao, ModelMapper modelMapper) {
        this.orderDao = orderDao;
        this.userDao = userDao;
        this.modelMapper = modelMapper;
    }

    // Obtener todas las órdenes
    public List<OrderDTO> getAllOrders() {
        return orderDao.getOrders().stream()
                .map(this::mapToOrderDTO)
                .toList();
    }

    // Crear una orden
    public OrderDTO createOrder(Long userId, List<OrderItemDTO> itemsDto) {
        UserModel user = userDao.findUserById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        Order order = new Order();
        order.setUserOrder(user);
        order.setPurchaseDateOrder(LocalDateTime.now());
        order.setPaymentStatus(PaymentStatus.PENDING);
        order.setOrderStatus(OrderStatus.PENDING);

        List<OrderItem> items = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        for (OrderItemDTO itemDTO : itemsDto) {
            OrderItem item = modelMapper.map(itemDTO, OrderItem.class);

            Product product = orderDao.findProductById(itemDTO.getProductOrderItem())
                    .orElseThrow(() -> new RuntimeException("Producto no encontrado"));

            item.setProductOrderItem(product);
            item.setPriceOrderItem(product.getPriceProduct());
            item.setOrder(order);

            total = total.add(item.getSubtotal());
            items.add(item);
        }

        order.setItemsOrder(items);
        order.setTotalOrder(total);
        orderDao.saveOrder(order);

        return mapToOrderDTO(order);
    }

    public OrderDTO getOrderById(Long idOrder) {
        Order order = orderDao.findOrderById(idOrder)
                .orElseThrow(() -> new RuntimeException("Orden no encontrada"));
        return mapToOrderDTO(order);
    }

    public List<OrderDTO> getOrdersByUserEmail(String email) {
        return orderDao.getOrdersByUserEmail(email).stream()
                .map(this::mapToOrderDTO)
                .toList();
    }

    public void updateOrderStatus(Long orderId, OrderStatus status) {
        Order order = orderDao.findOrderById(orderId)
                .orElseThrow(() -> new RuntimeException("Orden no encontrada"));
        order.setOrderStatus(status);
        orderDao.updateOrder(order);
    }

    public void updatePaymentStatus(Long orderId, PaymentStatus status) {
        Order order = orderDao.findOrderById(orderId)
                .orElseThrow(() -> new RuntimeException("Orden no encontrada"));
        order.setPaymentStatus(status);
        orderDao.updateOrder(order);
    }

    public void cancelOrder(Long orderId) {
        updateOrderStatus(orderId, OrderStatus.CANCELED);
    }

    public void deleteOrder(Long orderId) {
        orderDao.deleteOrderById(orderId);
    }

    private OrderDTO mapToOrderDTO(Order order) {
        OrderDTO dto = modelMapper.map(order, OrderDTO.class);
        dto.setUserId(order.getUserOrder().getId());
        dto.setUserEmail(order.getUserOrder().getEmail());
        dto.setItemsOrder(order.getItemsOrder().stream()
                .map(item -> {
                    OrderItemDTO itemDTO = modelMapper.map(item, OrderItemDTO.class);
                    itemDTO.setOrder(item.getOrder().getIdOrder());
                    itemDTO.setProductOrderItem(item.getProductOrderItem().getIdProduct());
                    return itemDTO;
                }).toList());
        return dto;
    }
}
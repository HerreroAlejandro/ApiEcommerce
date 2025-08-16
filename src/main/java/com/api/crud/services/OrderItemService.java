package com.api.crud.services;

import com.api.crud.DTO.OrderItemDTO;
import com.api.crud.models.entity.Order;
import com.api.crud.models.entity.OrderItem;
import com.api.crud.models.entity.Product;
import com.api.crud.repositories.OrderItemDao;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.List;

@Service
public class OrderItemService {

    private final OrderItemDao orderItemDao;
    private final ModelMapper modelMapper;

    @Autowired
    public OrderItemService(OrderItemDao orderItemDao, ModelMapper modelMapper) {
        this.orderItemDao = orderItemDao;
        this.modelMapper = modelMapper;
    }

    // Guardar un item desde DTO
    public OrderItemDTO saveOrderItem(OrderItemDTO dto, Order order, Product product) {
        OrderItem item = modelMapper.map(dto, OrderItem.class);
        item.setOrder(order);
        item.setProductOrderItem(product);
        orderItemDao.saveOrderItem(item);
        return modelMapper.map(item, OrderItemDTO.class);
    }

    // Obtener un item por ID como DTO
    public OrderItemDTO getOrderItemById(Long idOrderItem) {
        OrderItem item = orderItemDao.findOrderItemById(idOrderItem)
                .orElseThrow(() -> new RuntimeException("OrderItem no encontrado"));
        return modelMapper.map(item, OrderItemDTO.class);
    }

    // Obtener items de una orden como DTO
    public List<OrderItemDTO> getItemsByOrderId(Long orderId) {
        List<OrderItem> items = orderItemDao.findItemsByOrderId(orderId);
        return items.stream()
                .map(item -> modelMapper.map(item, OrderItemDTO.class))
                .toList();
    }

    // Incrementar cantidad de un item
    public void increaseAmount(Long orderItemId, int increment) {
        orderItemDao.increaseOrderItemAmount(orderItemId, increment);
    }

    // Decrementar cantidad de un item
    public void decreaseAmount(Long orderItemId, int decrement) {
        orderItemDao.decreaseOrderItemAmount(orderItemId, decrement);
    }

    // Eliminar item
    public void deleteOrderItem(Long idOrderItem) {
        orderItemDao.deleteOrderItem(idOrderItem);
    }

    // Subtotal de un item
    public BigDecimal getSubtotal(OrderItem item) {
        return item.getSubtotal();
    }
}

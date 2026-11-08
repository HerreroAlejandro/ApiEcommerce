package com.api.crud.config;

import com.api.crud.DTO.*;
import com.api.crud.models.entity.*;

import org.modelmapper.Converter;
import org.modelmapper.ModelMapper;
import org.modelmapper.convention.MatchingStrategies;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.util.List;

@Configuration
public class ModelMapperConfig {

    @Bean
    public ModelMapper modelMapper() {
        ModelMapper modelMapper = new ModelMapper();
        modelMapper
                .typeMap(UserModel.class, UserDetailAdminResponseDTO.class)
                .addMappings(
                        mapper -> {
                            mapper.map(
                                    UserModel::getRoleNames, UserDetailAdminResponseDTO::setRoles);
                        });
        // Cuando convierto el Cart a DTO no intento convertir todo el user model a long, tomo solo
        // el id
        modelMapper
                .typeMap(Cart.class, CartDTO.class)
                .addMappings(
                        mapper -> {
                            mapper.map(src -> src.getUserCart().getId(), CartDTO::setUserCart);
                        });
        modelMapper
                .typeMap(CartItem.class, CartItemDTO.class)
                .addMappings(
                        mapper -> {
                            mapper.map(
                                    src -> src.getProductCartItem().getImageUrl(),
                                    CartItemDTO::setImageUrl);
                        });
        configureCartItems(modelMapper);
        configureCarts(modelMapper);
        configureOrderItems(modelMapper);
        configureOrders(modelMapper);
        return modelMapper;
    }

    private void configureCartItems(ModelMapper mapper) {
        mapper.createTypeMap(
                        CartItem.class,
                        MyCartItemResponseDTO.class,
                        mapper.getConfiguration()
                                .copy()
                                .setMatchingStrategy(MatchingStrategies.STRICT))
                .addMappings(
                        mapping -> {
                            mapping.map(
                                    source -> source.getProductCartItem().getIdProduct(),
                                    MyCartItemResponseDTO::setProductId);
                            mapping.map(
                                    source -> source.getProductCartItem().getNameProduct(),
                                    MyCartItemResponseDTO::setNameProduct);
                            mapping.map(
                                    source -> source.getProductCartItem().getPriceProduct(),
                                    MyCartItemResponseDTO::setPriceProduct);
                            mapping.map(
                                    source -> source.getProductCartItem().getImageUrl(),
                                    MyCartItemResponseDTO::setImageUrl);
                        });
        mapper.createTypeMap(
                        CartItem.class,
                        CartItemAdminListDTO.class,
                        mapper.getConfiguration()
                                .copy()
                                .setMatchingStrategy(MatchingStrategies.STRICT))
                .addMappings(
                        mapping -> {
                            mapping.map(
                                    source -> source.getProductCartItem().getIdProduct(),
                                    CartItemAdminListDTO::setProductId);
                            mapping.map(
                                    source -> source.getProductCartItem().getNameProduct(),
                                    CartItemAdminListDTO::setNameProduct);
                            mapping.map(
                                    source -> source.getProductCartItem().getPriceProduct(),
                                    CartItemAdminListDTO::setPriceProduct);
                        });
        mapper.createTypeMap(
                        CartItem.class,
                        CartItemAdminDetailDTO.class,
                        mapper.getConfiguration()
                                .copy()
                                .setMatchingStrategy(MatchingStrategies.STRICT))
                .addMappings(
                        mapping -> {
                            mapping.map(
                                    source -> source.getProductCartItem().getIdProduct(),
                                    CartItemAdminDetailDTO::setProductId);
                            mapping.map(
                                    source -> source.getProductCartItem().getNameProduct(),
                                    CartItemAdminDetailDTO::setNameProduct);
                            mapping.map(
                                    source -> source.getProductCartItem().getPriceProduct(),
                                    CartItemAdminDetailDTO::setPriceProduct);
                            mapping.map(
                                    source -> source.getProductCartItem().getImageUrl(),
                                    CartItemAdminDetailDTO::setImageUrl);
                            mapping.map(
                                    source -> source.getProductCartItem().getType(),
                                    CartItemAdminDetailDTO::setType);
                            mapping.map(
                                    source -> source.getProductCartItem().isActive(),
                                    CartItemAdminDetailDTO::setActive);
                            mapping.map(
                                    source -> source.getCart().getIdCart(),
                                    CartItemAdminDetailDTO::setCartId);
                            Converter<Product, Integer> stock =
                                    context ->
                                            context.getSource() instanceof ProductPhysical physical
                                                    ? physical.getStockProduct()
                                                    : null;
                            mapping.using(stock)
                                    .map(
                                            CartItem::getProductCartItem,
                                            CartItemAdminDetailDTO::setStockProduct);
                        });
    }

    private void configureCarts(ModelMapper mapper) {
        Converter<List<CartItem>, BigDecimal> total =
                context ->
                        context.getSource().stream()
                                .map(CartItem::getSubtotal)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Converter<List<CartItem>, Integer> count = context -> context.getSource().size();
        mapper.createTypeMap(
                        Cart.class,
                        MyCartResponseDTO.class,
                        mapper.getConfiguration()
                                .copy()
                                .setMatchingStrategy(MatchingStrategies.STRICT))
                .addMappings(
                        mapping ->
                                mapping.using(total)
                                        .map(Cart::getItemsCart, MyCartResponseDTO::setTotal));
        mapper.createTypeMap(
                        Cart.class,
                        CartAdminListDTO.class,
                        mapper.getConfiguration()
                                .copy()
                                .setMatchingStrategy(MatchingStrategies.STRICT))
                .addMappings(
                        mapping -> {
                            mapping.map(
                                    source -> source.getUserCart().getId(),
                                    CartAdminListDTO::setUserId);
                            mapping.using(total)
                                    .map(Cart::getItemsCart, CartAdminListDTO::setTotal);
                            mapping.using(count)
                                    .map(Cart::getItemsCart, CartAdminListDTO::setItemCount);
                        });
        mapper.createTypeMap(
                        Cart.class,
                        CartAdminDetailDTO.class,
                        mapper.getConfiguration()
                                .copy()
                                .setMatchingStrategy(MatchingStrategies.STRICT))
                .addMappings(
                        mapping -> {
                            mapping.map(
                                    source -> source.getUserCart().getId(),
                                    CartAdminDetailDTO::setUserId);
                            mapping.using(total)
                                    .map(Cart::getItemsCart, CartAdminDetailDTO::setTotal);
                            mapping.using(count)
                                    .map(Cart::getItemsCart, CartAdminDetailDTO::setItemCount);
                        });
    }

    private void configureOrderItems(ModelMapper mapper) {
        mapper.createTypeMap(
                        OrderItem.class,
                        OrderItemResponseDTO.class,
                        mapper.getConfiguration()
                                .copy()
                                .setMatchingStrategy(MatchingStrategies.STRICT))
                .addMappings(
                        mapping -> {
                            mapping.map(
                                    source -> source.getProductOrderItem().getIdProduct(),
                                    OrderItemResponseDTO::setProductId);
                            mapping.map(
                                    OrderItem::getProductName,
                                    OrderItemResponseDTO::setProductName);
                        });
        mapper.createTypeMap(
                        OrderItem.class,
                        OrderItemAdminDetailDTO.class,
                        mapper.getConfiguration()
                                .copy()
                                .setMatchingStrategy(MatchingStrategies.STRICT))
                .addMappings(
                        mapping -> {
                            mapping.map(
                                    source -> source.getProductOrderItem().getIdProduct(),
                                    OrderItemAdminDetailDTO::setProductId);
                            mapping.map(
                                    OrderItem::getProductName,
                                    OrderItemAdminDetailDTO::setProductName);
                            mapping.map(
                                    source -> source.getOrder().getIdOrder(),
                                    OrderItemAdminDetailDTO::setOrderId);
                            mapping.map(
                                    source -> source.getOrder().getUserOrder().getId(),
                                    OrderItemAdminDetailDTO::setUserId);
                            mapping.map(
                                    source -> source.getOrder().getOrderStatus(),
                                    OrderItemAdminDetailDTO::setOrderStatus);
                            mapping.map(
                                    source -> source.getOrder().getPaymentStatus(),
                                    OrderItemAdminDetailDTO::setPaymentStatus);
                        });
    }

    private void configureOrders(ModelMapper mapper) {
        mapper.createTypeMap(
                        Order.class,
                        OrderAdminListDTO.class,
                        mapper.getConfiguration()
                                .copy()
                                .setMatchingStrategy(MatchingStrategies.STRICT))
                .addMappings(
                        mapping -> {
                            mapping.map(
                                    source -> source.getUserOrder().getId(),
                                    OrderAdminListDTO::setUserId);
                            mapping.map(
                                    source -> source.getUserOrder().getEmail(),
                                    OrderAdminListDTO::setUserEmail);
                        });
        mapper.createTypeMap(
                        Order.class,
                        OrderAdminDetailDTO.class,
                        mapper.getConfiguration()
                                .copy()
                                .setMatchingStrategy(MatchingStrategies.STRICT))
                .addMappings(
                        mapping -> {
                            mapping.map(
                                    source -> source.getUserOrder().getId(),
                                    OrderAdminDetailDTO::setUserId);
                            mapping.map(
                                    source -> source.getUserOrder().getEmail(),
                                    OrderAdminDetailDTO::setUserEmail);
                        });
    }
}

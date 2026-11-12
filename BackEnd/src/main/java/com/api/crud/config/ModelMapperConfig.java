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
        Converter<Product, Integer> productStock =
                context ->
                        context.getSource() instanceof ProductPhysical physical
                                ? physical.getStockProduct()
                                : null;
        modelMapper
                .createTypeMap(Product.class, ProductDetailDTO.class)
                .addMappings(
                        mapping ->
                                mapping.using(productStock)
                                        .map(source -> source, ProductDetailDTO::setStockProduct));
        modelMapper
                .createTypeMap(ProductPhysical.class, ProductDetailDTO.class)
                .includeBase(Product.class, ProductDetailDTO.class);
        modelMapper
                .createTypeMap(ProductDigital.class, ProductDetailDTO.class)
                .includeBase(Product.class, ProductDetailDTO.class);
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
        configureShipping(modelMapper);
        configureOrders(modelMapper);
        configureDigitalDeliveries(modelMapper);
        modelMapper.createTypeMap(
                OrderChangeAudit.class,
                OrderHistoryResponseDTO.class,
                modelMapper
                        .getConfiguration()
                        .copy()
                        .setMatchingStrategy(MatchingStrategies.STRICT));
        modelMapper.createTypeMap(
                CartChangeAudit.class,
                CartHistoryResponseDTO.class,
                modelMapper
                        .getConfiguration()
                        .copy()
                        .setMatchingStrategy(MatchingStrategies.STRICT));
        modelMapper
                .createTypeMap(
                        PhysicalReturn.class,
                        PhysicalReturnListDTO.class,
                        modelMapper
                                .getConfiguration()
                                .copy()
                                .setMatchingStrategy(MatchingStrategies.STRICT))
                .addMapping(
                        source -> source.getOrder().getIdOrder(),
                        PhysicalReturnListDTO::setOrderId);
        modelMapper
                .createTypeMap(
                        ChargebackCase.class,
                        ChargebackListDTO.class,
                        modelMapper
                                .getConfiguration()
                                .copy()
                                .setMatchingStrategy(MatchingStrategies.STRICT))
                .addMappings(
                        mapping -> {
                            mapping.map(
                                    source -> source.getPayment().getId(),
                                    ChargebackListDTO::setPaymentId);
                            mapping.map(
                                    source ->
                                            source.getPayment()
                                                    .getCheckout()
                                                    .getOrder()
                                                    .getIdOrder(),
                                    ChargebackListDTO::setOrderId);
                        });
        modelMapper
                .createTypeMap(
                        ChargebackCase.class,
                        ChargebackResponseDTO.class,
                        modelMapper
                                .getConfiguration()
                                .copy()
                                .setMatchingStrategy(MatchingStrategies.STRICT))
                .addMappings(
                        mapping -> {
                            mapping.map(
                                    source -> source.getPayment().getId(),
                                    ChargebackResponseDTO::setPaymentId);
                            mapping.map(
                                    source ->
                                            source.getPayment()
                                                    .getCheckout()
                                                    .getOrder()
                                                    .getIdOrder(),
                                    ChargebackResponseDTO::setOrderId);
                        });
        modelMapper.createTypeMap(
                PaymentRefundAttempt.class,
                PaymentRefundAttemptResponseDTO.class,
                modelMapper
                        .getConfiguration()
                        .copy()
                        .setMatchingStrategy(MatchingStrategies.STRICT));
        modelMapper
                .createTypeMap(
                        StoreSaleCreateRequestDTO.class,
                        StoreSaleDetails.class,
                        modelMapper
                                .getConfiguration()
                                .copy()
                                .setMatchingStrategy(MatchingStrategies.STRICT))
                .addMappings(
                        mapping -> {
                            mapping.skip(StoreSaleDetails::setReference);
                            mapping.skip(StoreSaleDetails::setRequestHash);
                            mapping.skip(StoreSaleDetails::setCashierUserId);
                            mapping.skip(StoreSaleDetails::setPaidAt);
                            mapping.skip(StoreSaleDetails::setDeliveredAt);
                            mapping.skip(StoreSaleDetails::setRefundedAt);
                            mapping.skip(StoreSaleDetails::setRefundedBy);
                            mapping.skip(StoreSaleDetails::setRefundReason);
                        });
        modelMapper.createTypeMap(
                StoreSaleDetails.class,
                StoreSaleResponseDTO.class,
                modelMapper
                        .getConfiguration()
                        .copy()
                        .setMatchingStrategy(MatchingStrategies.STRICT));
        modelMapper.createTypeMap(
                StoreSaleCreateRequestDTO.class,
                OrderCreateRequestDTO.class,
                modelMapper
                        .getConfiguration()
                        .copy()
                        .setMatchingStrategy(MatchingStrategies.STRICT));
        modelMapper
                .createTypeMap(
                        PhysicalReturn.class,
                        PhysicalReturnResponseDTO.class,
                        modelMapper
                                .getConfiguration()
                                .copy()
                                .setMatchingStrategy(MatchingStrategies.STRICT))
                .addMapping(
                        source -> source.getOrder().getIdOrder(),
                        PhysicalReturnResponseDTO::setOrderId);
        modelMapper.createTypeMap(
                RefundItemAllocation.class,
                RefundItemResponseDTO.class,
                modelMapper
                        .getConfiguration()
                        .copy()
                        .setMatchingStrategy(MatchingStrategies.STRICT));
        modelMapper
                .createTypeMap(
                        PaymentRefund.class,
                        PaymentRefundResponseDTO.class,
                        modelMapper
                                .getConfiguration()
                                .copy()
                                .setMatchingStrategy(MatchingStrategies.STRICT))
                .addMapping(
                        source -> source.getPayment().getId(),
                        PaymentRefundResponseDTO::setPaymentId);
        modelMapper
                .createTypeMap(
                        PaymentCheckout.class,
                        CheckoutResponseDTO.class,
                        modelMapper
                                .getConfiguration()
                                .copy()
                                .setMatchingStrategy(MatchingStrategies.STRICT))
                .addMappings(
                        mapping ->
                                mapping.map(
                                        source -> source.getOrder().getIdOrder(),
                                        CheckoutResponseDTO::setOrderId));
        modelMapper
                .createTypeMap(
                        Payment.class,
                        PaymentAdminResponseDTO.class,
                        modelMapper
                                .getConfiguration()
                                .copy()
                                .setMatchingStrategy(MatchingStrategies.STRICT))
                .addMappings(
                        mapping ->
                                mapping.map(
                                        source -> source.getCheckout().getOrder().getIdOrder(),
                                        PaymentAdminResponseDTO::setOrderId));
        return modelMapper;
    }

    private void configureShipping(ModelMapper mapper) {
        Converter<String, String> optionalUrl =
                context ->
                        context.getSource() == null || context.getSource().isBlank()
                                ? null
                                : context.getSource();
        mapper.createTypeMap(
                ShippingAddressDTO.class,
                OrderShippingAddress.class,
                mapper.getConfiguration().copy().setMatchingStrategy(MatchingStrategies.STRICT));
        mapper.createTypeMap(
                OrderShippingAddress.class,
                ShippingAddressDTO.class,
                mapper.getConfiguration().copy().setMatchingStrategy(MatchingStrategies.STRICT));
        mapper.createTypeMap(
                OrderShipmentTracking.class,
                ShipmentTrackingDTO.class,
                mapper.getConfiguration().copy().setMatchingStrategy(MatchingStrategies.STRICT));
        mapper.createTypeMap(
                        ShipmentTrackingUpdateRequestDTO.class,
                        OrderShipmentTracking.class,
                        mapper.getConfiguration()
                                .copy()
                                .setMatchingStrategy(MatchingStrategies.STRICT))
                .addMappings(
                        mapping -> {
                            mapping.using(optionalUrl)
                                    .map(
                                            ShipmentTrackingUpdateRequestDTO::getTrackingUrl,
                                            OrderShipmentTracking::setTrackingUrl);
                            mapping.skip(OrderShipmentTracking::setShippedAt);
                            mapping.skip(OrderShipmentTracking::setDeliveredAt);
                        });
    }

    private void configureDigitalDeliveries(ModelMapper mapper) {
        mapper.createTypeMap(
                        DigitalDelivery.class,
                        DigitalDeliveryResponseDTO.class,
                        mapper.getConfiguration()
                                .copy()
                                .setMatchingStrategy(MatchingStrategies.STRICT))
                .addMappings(
                        mapping -> {
                            mapping.map(
                                    source -> source.getOrderItem().getIdOrderItem(),
                                    DigitalDeliveryResponseDTO::setOrderItemId);
                            mapping.map(
                                    source -> source.getOrderItem().getProductName(),
                                    DigitalDeliveryResponseDTO::setProductName);
                        });
        Converter<Long, String> path =
                context -> "/digital-deliveries/" + context.getSource() + "/download";
        mapper.createTypeMap(
                        DigitalDelivery.class,
                        DownloadTokenResponseDTO.class,
                        mapper.getConfiguration()
                                .copy()
                                .setMatchingStrategy(MatchingStrategies.STRICT))
                .addMappings(
                        mapping -> {
                            mapping.skip(DownloadTokenResponseDTO::setToken);
                            mapping.map(
                                    DigitalDelivery::getTokenExpiresAt,
                                    DownloadTokenResponseDTO::setExpiresAt);
                            mapping.using(path)
                                    .map(
                                            DigitalDelivery::getId,
                                            DownloadTokenResponseDTO::setDownloadPath);
                        });
    }

    private void configureCartItems(ModelMapper mapper) {
        Converter<Product, Integer> stock =
                context ->
                        context.getSource() instanceof ProductPhysical physical
                                ? physical.getStockProduct()
                                : null;
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
                            mapping.map(
                                    source -> source.getProductCartItem().isActive(),
                                    MyCartItemResponseDTO::setActive);
                            mapping.map(
                                    source -> source.getProductCartItem().getType(),
                                    MyCartItemResponseDTO::setType);
                            mapping.using(stock)
                                    .map(
                                            CartItem::getProductCartItem,
                                            MyCartItemResponseDTO::setStockProduct);
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

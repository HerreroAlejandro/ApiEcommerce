package com.api.crud.services;

import com.api.crud.DTO.*;
import com.api.crud.exceptions.*;
import com.api.crud.models.entity.*;

import jakarta.annotation.PostConstruct;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;

import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Locale;

@Service
public class OrderShippingService {

    private final ModelMapper mapper;

    private final Validator validator;

    @Value("${orders.shipping.flat-rate:}")
    private String flatRate = "";

    public OrderShippingService(ModelMapper mapper, Validator validator) {
        this.mapper = mapper;
        this.validator = validator;
    }

    @PostConstruct
    void validateConfiguration() {
        if (!flatRate.isBlank()) configuredRate();
    }

    private BigDecimal configuredRate() {
        try {
            BigDecimal rate = new BigDecimal(flatRate);
            if (rate.signum() < 0
                    || rate.compareTo(new BigDecimal("99999999.99")) > 0
                    || rate.stripTrailingZeros().scale() > 2) {
                throw new IllegalStateException(
                        "La tarifa de envio debe ser no negativa y tener hasta dos decimales");
            }
            return rate.setScale(2, RoundingMode.UNNECESSARY);
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("Tarifa de envio invalida", exception);
        }
    }

    public BigDecimal getFlatRate() {
        if (flatRate.isBlank())
            throw new ExternalServiceException("El costo de envio no esta configurado");
        return configuredRate();
    }

    public void prepare(Order order, OrderCreateRequestDTO request, boolean physical) {
        ShippingAddressDTO address = request == null ? null : request.getShippingAddress();
        if (!physical) {
            if (address != null)
                throw new InvalidRequestException(
                        "Una compra solo digital no requiere direccion de envio");
            order.setShippingCost(new BigDecimal("0.00"));
            return;
        }
        if (address == null)
            throw new InvalidRequestException(
                    "Debe indicar una direccion para los productos fisicos");
        validate(address);
        if (!Arrays.asList(Locale.getISOCountries()).contains(address.getCountryCode()))
            throw new InvalidRequestException("Codigo de pais invalido");
        order.setShippingCost(getFlatRate());
        order.setShippingAddress(mapper.map(address, OrderShippingAddress.class));
    }

    public void validate(Object request) {
        if (request == null) throw new InvalidRequestException("Datos de envio requeridos");
        var errors = validator.validate(request);
        if (!errors.isEmpty()) throw new ConstraintViolationException(errors);
    }

    public void requireReadyToShip(Order order) {
        if (order.getShippingAddress() == null
                || !validator
                        .validate(mapper.map(order.getShippingAddress(), ShippingAddressDTO.class))
                        .isEmpty()) {
            throw new BusinessConflictException(
                    "La orden no tiene una direccion de entrega completa");
        }
        OrderShipmentTracking tracking = order.getShipmentTracking();
        if (tracking == null
                || tracking.getCarrier() == null
                || tracking.getCarrier().isBlank()
                || tracking.getTrackingNumber() == null
                || tracking.getTrackingNumber().isBlank()) {
            throw new BusinessConflictException(
                    "Debe registrar transportista y seguimiento antes del envio");
        }
    }

    public void updateTracking(Order order, ShipmentTrackingUpdateRequestDTO request) {
        validate(request);
        if (order.getShipmentTracking() == null)
            order.setShipmentTracking(new OrderShipmentTracking());
        mapper.map(request, order.getShipmentTracking());
    }
}

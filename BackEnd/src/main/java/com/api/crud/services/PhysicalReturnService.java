package com.api.crud.services;

import com.api.crud.DTO.*;
import com.api.crud.exceptions.*;
import com.api.crud.models.entity.*;
import com.api.crud.models.enums.*;
import com.api.crud.repositories.*;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;

import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class PhysicalReturnService {

    private final PhysicalReturnDao returns;
    private final OrderWorkflowDao workflow;
    private final UserDao users;
    private final ModelMapper mapper;
    private final Validator validator;

    public PhysicalReturnService(
            PhysicalReturnDao returns,
            OrderWorkflowDao workflow,
            UserDao users,
            ModelMapper mapper,
            Validator validator) {
        this.returns = returns;
        this.workflow = workflow;
        this.users = users;
        this.mapper = mapper;
        this.validator = validator;
    }

    private UserModel staff(String email, boolean adminOnly) {
        UserModel actor = users.getUserByEmail(email).orElseThrow(UnauthenticatedException::new);
        if (actor.getStatus() != AccountStatus.ACTIVE) throw new UnauthenticatedException();
        if (!actor.getRoleNames().contains("ADMIN")
                && (adminOnly || !actor.getRoleNames().contains("SUPPORT")))
            throw new ForbiddenOperationException();
        return actor;
    }

    private String reason(String reason) {
        if (reason == null || reason.isBlank() || reason.length() > 500)
            throw new InvalidRequestException("Debe indicar un motivo de hasta 500 caracteres");
        return reason.trim();
    }

    private void eligible(Order order) {
        if ((order.getOrderStatus() != OrderStatus.SHIPPED
                        && order.getOrderStatus() != OrderStatus.DELIVERED)
                || order.getItemsOrder().stream()
                        .noneMatch(item -> item.getProductOrderItem() instanceof ProductPhysical)
                || !Set.of(PaymentStatus.PAID, PaymentStatus.REVIEW, PaymentStatus.REFUNDED)
                        .contains(order.getPaymentStatus()))
            throw new BusinessConflictException(
                    "La devolucion requiere una compra fisica enviada o entregada");
    }

    public PhysicalReturnResponseDTO get(String email, Long orderId) {
        staff(email, false);
        return response(returns.getByOrderId(orderId).orElseThrow(ResourceNotFoundException::new));
    }

    @Transactional
    public PhysicalReturnResponseDTO request(String email, Long orderId, String motive) {
        UserModel actor = staff(email, true);
        String reason = reason(motive);
        Order order = workflow.lockOrder(orderId);
        eligible(order);
        PhysicalReturn existing = returns.getByOrderId(orderId).orElse(null);
        if (existing != null) return response(existing);
        PhysicalReturn result = new PhysicalReturn();
        result.setOrder(order);
        result.setStatus(PhysicalReturnStatus.REQUESTED);
        result.setReason(reason);
        result.setRequestedBy(actor.getId());
        result.setRequestedAt(LocalDateTime.now());
        returns.save(result);
        audit(result, actor, "PHYSICAL_RETURN_REQUESTED", null, reason);
        return response(result);
    }

    @Transactional
    public PhysicalReturnResponseDTO receive(
            String email, Long orderId, PhysicalReturnReceiveRequestDTO request) {
        UserModel actor = staff(email, true);
        if (request == null) throw new InvalidRequestException("Recepcion requerida");
        var violations = validator.validate(request);
        if (!violations.isEmpty()) throw new ConstraintViolationException(violations);
        Order order = workflow.lockOrder(orderId);
        eligible(order);
        PhysicalReturn result =
                returns.getByOrderId(orderId).orElseThrow(ResourceNotFoundException::new);
        List<OrderItem> physical =
                order.getItemsOrder().stream()
                        .filter(item -> item.getProductOrderItem() instanceof ProductPhysical)
                        .sorted(
                                Comparator.comparing(
                                        item -> item.getProductOrderItem().getIdProduct()))
                        .toList();
        Map<Long, PhysicalReturnItemRequestDTO> received = new HashMap<>();
        for (PhysicalReturnItemRequestDTO line : request.getItems()) {
            if (received.put(line.getOrderItemId(), line) != null)
                throw new InvalidRequestException("Articulo de devolucion repetido");
        }
        if (received.size() != physical.size())
            throw new InvalidRequestException(
                    "Debe registrar la devolucion de todos los articulos fisicos");
        for (OrderItem item : physical) {
            var line = received.get(item.getIdOrderItem());
            if (line == null
                    || line.getReceivedQuantity() != item.getAmountOrderItem()
                    || line.getRestockedQuantity() > line.getReceivedQuantity())
                throw new InvalidRequestException(
                        "Cantidades de devolucion invalidas; solo se admite devolucion total");
        }
        if (result.getStatus() == PhysicalReturnStatus.RECEIVED) {
            boolean same =
                    result.getItems().size() == received.size()
                            && result.getItems().stream()
                                    .allMatch(
                                            line -> {
                                                var input = received.get(line.getOrderItemId());
                                                return input != null
                                                        && input.getReceivedQuantity()
                                                                == line.getReceivedQuantity()
                                                        && input.getRestockedQuantity()
                                                                == line.getRestockedQuantity();
                                            });
            if (!same)
                throw new BusinessConflictException(
                        "La recepcion ya fue registrada con otras cantidades");
            return response(result);
        }
        if (result.getStatus() != PhysicalReturnStatus.REQUESTED)
            throw new BusinessConflictException("La devolucion ya fue resuelta sin recepcion");
        // Validate every stock change before applying any, using the same product lock order as
        // checkout.
        Map<ProductPhysical, Integer> stocks = new LinkedHashMap<>();
        for (OrderItem item : physical) {
            ProductPhysical product =
                    (ProductPhysical)
                            workflow.lockProduct(item.getProductOrderItem().getIdProduct());
            int quantity = received.get(item.getIdOrderItem()).getRestockedQuantity();
            long updated =
                    (long) stocks.getOrDefault(product, product.getStockProduct()) + quantity;
            if (updated > Integer.MAX_VALUE)
                throw new BusinessConflictException("Stock fuera del limite admitido");
            stocks.put(product, (int) updated);
        }
        stocks.forEach(ProductPhysical::setStockProduct);
        for (OrderItem item : physical) {
            result.getItems()
                    .add(mapper.map(received.get(item.getIdOrderItem()), PhysicalReturnLine.class));
        }
        result.setStatus(PhysicalReturnStatus.RECEIVED);
        result.setResolvedBy(actor.getId());
        result.setResolvedAt(LocalDateTime.now());
        result.setResolutionReason(reason(request.getReason()));
        audit(result, actor, "PHYSICAL_RETURN_RECEIVED", "REQUESTED", result.getResolutionReason());
        return response(result);
    }

    @Transactional
    public PhysicalReturnResponseDTO waive(String email, Long orderId, String motive) {
        UserModel actor = staff(email, true);
        String reason = reason(motive);
        Order order = workflow.lockOrder(orderId);
        eligible(order);
        PhysicalReturn result =
                returns.getByOrderId(orderId).orElseThrow(ResourceNotFoundException::new);
        if (result.getStatus() == PhysicalReturnStatus.WAIVED) return response(result);
        if (result.getStatus() != PhysicalReturnStatus.REQUESTED)
            throw new BusinessConflictException("La devolucion ya fue recibida");
        result.setStatus(PhysicalReturnStatus.WAIVED);
        result.setResolvedBy(actor.getId());
        result.setResolvedAt(LocalDateTime.now());
        result.setResolutionReason(reason);
        audit(result, actor, "PHYSICAL_RETURN_WAIVED", "REQUESTED", reason);
        return response(result);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void requireRefundReady(Order order) {
        if ((order.getOrderStatus() != OrderStatus.SHIPPED
                        && order.getOrderStatus() != OrderStatus.DELIVERED)
                || order.getItemsOrder().stream()
                        .noneMatch(item -> item.getProductOrderItem() instanceof ProductPhysical))
            return;
        PhysicalReturn result = returns.getByOrderId(order.getIdOrder()).orElse(null);
        if (result == null || result.getStatus() == PhysicalReturnStatus.REQUESTED)
            throw new BusinessConflictException(
                    "Debe recibir la devolucion fisica o registrar una excepcion antes de"
                        + " reembolsar");
    }

    private PhysicalReturnResponseDTO response(PhysicalReturn result) {
        return mapper.map(result, PhysicalReturnResponseDTO.class);
    }

    private void audit(
            PhysicalReturn result, UserModel actor, String action, String before, String reason) {
        OrderChangeAudit audit = new OrderChangeAudit();
        audit.setOrderId(result.getOrder().getIdOrder());
        audit.setActorUserId(actor.getId());
        audit.setAction(action);
        audit.setPreviousValue(before);
        audit.setNewValue(result.getStatus().name());
        audit.setReason(reason);
        audit.setCreatedAt(LocalDateTime.now());
        workflow.saveAudit(audit);
    }
}

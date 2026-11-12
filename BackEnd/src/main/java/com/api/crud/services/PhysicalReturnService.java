package com.api.crud.services;

import com.api.crud.DTO.*;
import com.api.crud.exceptions.*;
import com.api.crud.models.entity.*;
import com.api.crud.models.enums.*;
import com.api.crud.repositories.*;
import com.api.crud.validation.InputRules;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;

import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

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
                        .noneMatch(
                                item ->
                                        item.getProductOrderItem() instanceof ProductPhysical
                                                && item.getFulfillmentQuantity() > 0)
                || !Set.of(
                                PaymentStatus.PAID,
                                PaymentStatus.PARTIALLY_REFUNDED,
                                PaymentStatus.CHARGED_BACK,
                                PaymentStatus.REVIEW,
                                PaymentStatus.REFUNDED)
                        .contains(order.getPaymentStatus()))
            throw new BusinessConflictException(
                    "La devolucion requiere una compra fisica enviada o entregada");
    }

    public PhysicalReturnResponseDTO get(String email, Long orderId) {
        staff(email, false);
        return response(returns.getByOrderId(orderId).orElseThrow(ResourceNotFoundException::new));
    }

    public Page<PhysicalReturnListDTO> getReturns(
            String email, PhysicalReturnStatus status, Pageable pageable) {
        staff(email, false);
        InputRules.page(pageable);
        return returns.getReturns(status, pageable)
                .map(record -> mapper.map(record, PhysicalReturnListDTO.class));
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

    @Transactional(isolation = Isolation.READ_COMMITTED)
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
        Map<Long, PhysicalReturnLine> previous = new HashMap<>();
        result.getItems().forEach(line -> previous.put(line.getOrderItemId(), line));
        Set<Long> physicalIds =
                physical.stream().map(OrderItem::getIdOrderItem).collect(Collectors.toSet());
        if (!physicalIds.containsAll(received.keySet()))
            throw new InvalidRequestException(
                    "Hay articulos que no pertenecen a la entrega fisica");
        for (OrderItem item : physical) {
            var line = received.get(item.getIdOrderItem());
            if (line == null) continue;
            var before = previous.get(item.getIdOrderItem());
            if (line.getReceivedQuantity() > item.getFulfillmentQuantity()
                    || line.getRestockedQuantity() > line.getReceivedQuantity()
                    || (before != null
                            && (line.getReceivedQuantity() < before.getReceivedQuantity()
                                    || line.getRestockedQuantity() < before.getRestockedQuantity()
                                    || line.getRestockedQuantity() - before.getRestockedQuantity()
                                            > line.getReceivedQuantity()
                                                    - before.getReceivedQuantity())))
                throw new InvalidRequestException(
                        "Las cantidades acumuladas de devolucion no pueden disminuir ni superar la"
                                + " entrega");
        }
        if (result.getStatus() == PhysicalReturnStatus.WAIVED)
            throw new BusinessConflictException("La devolucion ya fue resuelta sin recepcion");
        boolean changed =
                received.values().stream()
                        .anyMatch(
                                line -> {
                                    var before = previous.get(line.getOrderItemId());
                                    return before == null
                                            || before.getReceivedQuantity()
                                                    != line.getReceivedQuantity()
                                            || before.getRestockedQuantity()
                                                    != line.getRestockedQuantity();
                                });
        if (!changed)
            return response(
                    result); // Validate every stock change before applying any, using the same
        // product lock order as
        // checkout.
        Map<ProductPhysical, Integer> stocks = new LinkedHashMap<>();
        for (OrderItem item : physical) {
            ProductPhysical product =
                    (ProductPhysical)
                            workflow.lockProduct(item.getProductOrderItem().getIdProduct());
            var line = received.get(item.getIdOrderItem());
            if (line == null) continue;
            var before = previous.get(item.getIdOrderItem());
            int quantity =
                    line.getRestockedQuantity()
                            - (before == null ? 0 : before.getRestockedQuantity());
            long updated =
                    (long) stocks.getOrDefault(product, product.getStockProduct()) + quantity;
            if (updated > Integer.MAX_VALUE)
                throw new BusinessConflictException("Stock fuera del limite admitido");
            stocks.put(product, (int) updated);
        }
        stocks.forEach(ProductPhysical::setStockProduct);
        for (OrderItem item : physical) {
            var line = received.get(item.getIdOrderItem());
            if (line == null) continue;
            var before = previous.get(item.getIdOrderItem());
            int priorReceived = before == null ? 0 : before.getReceivedQuantity();
            int priorRestocked = before == null ? 0 : before.getRestockedQuantity();
            if (before == null) result.getItems().add(mapper.map(line, PhysicalReturnLine.class));
            else {
                before.setReceivedQuantity(line.getReceivedQuantity());
                before.setRestockedQuantity(line.getRestockedQuantity());
            }
            if (priorReceived != line.getReceivedQuantity()
                    || priorRestocked != line.getRestockedQuantity()) {
                OrderChangeAudit audit = new OrderChangeAudit();
                audit.setOrderId(orderId);
                audit.setActorUserId(actor.getId());
                audit.setAction("PHYSICAL_RETURN_ITEM_RECEIVED");
                audit.setPreviousValue(
                        item.getIdOrderItem() + ":" + priorReceived + ":" + priorRestocked);
                audit.setNewValue(
                        item.getIdOrderItem()
                                + ":"
                                + line.getReceivedQuantity()
                                + ":"
                                + line.getRestockedQuantity());
                audit.setReason(reason(request.getReason()));
                audit.setCreatedAt(LocalDateTime.now());
                workflow.saveAudit(audit);
            }
        }
        result.setStatus(PhysicalReturnStatus.RECEIVED);
        result.setResolvedBy(actor.getId());
        result.setResolvedAt(LocalDateTime.now());
        result.setResolutionReason(reason(request.getReason()));
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
        String previousStatus = result.getStatus().name();
        result.setStatus(PhysicalReturnStatus.WAIVED);
        result.setResolvedBy(actor.getId());
        result.setResolvedAt(LocalDateTime.now());
        result.setResolutionReason(reason);
        audit(result, actor, "PHYSICAL_RETURN_WAIVED", previousStatus, reason);
        return response(result);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void requireRefundReady(Order order) {
        if ((order.getOrderStatus() != OrderStatus.SHIPPED
                        && order.getOrderStatus() != OrderStatus.DELIVERED)
                || order.getItemsOrder().stream()
                        .noneMatch(
                                item ->
                                        item.getProductOrderItem() instanceof ProductPhysical
                                                && item.getFulfillmentQuantity() > 0)) return;
        PhysicalReturn result = returns.getByOrderId(order.getIdOrder()).orElse(null);
        if (result == null || result.getStatus() == PhysicalReturnStatus.REQUESTED)
            throw new BusinessConflictException(
                    "Debe recibir la devolucion fisica o registrar una excepcion antes de"
                            + " reembolsar");
        if (result.getStatus() == PhysicalReturnStatus.RECEIVED) {
            for (OrderItem item : order.getItemsOrder()) {
                if (!(item.getProductOrderItem() instanceof ProductPhysical)) continue;
                int received =
                        result.getItems().stream()
                                .filter(line -> line.getOrderItemId().equals(item.getIdOrderItem()))
                                .mapToInt(PhysicalReturnLine::getReceivedQuantity)
                                .sum();
                if (received < item.getFulfillmentQuantity())
                    throw new BusinessConflictException(
                            "Debe completar la devolucion fisica antes del reembolso total");
            }
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void requireItemRefundReady(Order order, OrderItem item, int requestedQuantity) {
        if (!(item.getProductOrderItem() instanceof ProductPhysical)
                || (order.getOrderStatus() != OrderStatus.SHIPPED
                        && order.getOrderStatus() != OrderStatus.DELIVERED)) return;
        PhysicalReturn result = returns.getByOrderId(order.getIdOrder()).orElse(null);
        if (result != null && result.getStatus() == PhysicalReturnStatus.WAIVED) return;
        int received =
                result == null
                        ? 0
                        : result.getItems().stream()
                                .filter(line -> line.getOrderItemId().equals(item.getIdOrderItem()))
                                .mapToInt(PhysicalReturnLine::getReceivedQuantity)
                                .sum();
        if (received < requestedQuantity - item.getCanceledQuantity())
            throw new BusinessConflictException(
                    "Debe recibir esas unidades o registrar una excepcion antes de reembolsarlas");
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

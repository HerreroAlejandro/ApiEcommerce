package com.api.crud.services;

import com.api.crud.DTO.ItemRefundRequestDTO;
import com.api.crud.DTO.PaymentRefundAttemptResponseDTO;
import com.api.crud.DTO.PaymentRefundResponseDTO;
import com.api.crud.config.MercadoPagoProperties;
import com.api.crud.exceptions.*;
import com.api.crud.models.entity.*;
import com.api.crud.models.enums.*;
import com.api.crud.repositories.*;
import com.api.crud.validation.InputRules;

import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class PaymentRefundPersistence {

    private final PaymentDao payments;
    private final PaymentRefundDao refunds;
    private final OrderWorkflowDao workflow;
    private final UserDao users;
    private final MercadoPagoProperties properties;
    private final MercadoPagoClient client;
    private final DigitalDeliveryService deliveries;
    private final ModelMapper mapper;
    private final PhysicalReturnService physicalReturns;
    private final PaymentRefundAttemptDao attempts;

    public PaymentRefundPersistence(
            PaymentDao payments,
            PaymentRefundDao refunds,
            OrderWorkflowDao workflow,
            UserDao users,
            MercadoPagoProperties properties,
            MercadoPagoClient client,
            DigitalDeliveryService deliveries,
            ModelMapper mapper,
            PhysicalReturnService physicalReturns,
            PaymentRefundAttemptDao attempts) {
        this.payments = payments;
        this.refunds = refunds;
        this.workflow = workflow;
        this.users = users;
        this.properties = properties;
        this.client = client;
        this.deliveries = deliveries;
        this.mapper = mapper;
        this.physicalReturns = physicalReturns;
        this.attempts = attempts;
    }

    private UserModel admin(String email) {
        UserModel actor = users.getUserByEmail(email).orElseThrow(UnauthenticatedException::new);
        if (actor.getStatus() != AccountStatus.ACTIVE) throw new UnauthenticatedException();
        if (!actor.getRoleNames().contains("ADMIN")) throw new ForbiddenOperationException();
        return actor;
    }

    private Payment payment(Long id) {
        return payments.getById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pago no encontrado"));
    }

    public String getProviderOrderId(String email, Long paymentId) {
        admin(email);
        properties.requireEnabled();
        String id = payment(paymentId).getCheckout().getProviderOrderId();
        if (id == null)
            throw new BusinessConflictException(
                    "El pago no tiene una order de Mercado Pago vinculada");
        return id;
    }

    public PaymentRefundResponseDTO getRefund(String email, Long paymentId) {
        admin(email);
        PaymentRefund refund =
                refunds.getByPaymentId(paymentId).orElseThrow(ResourceNotFoundException::new);
        return mapper.map(refund, PaymentRefundResponseDTO.class);
    }

    public List<PaymentRefundResponseDTO> getRefundHistory(String email, Long paymentId) {
        admin(email);
        payment(paymentId);
        return refunds.getAllByPaymentId(paymentId).stream()
                .map(refund -> mapper.map(refund, PaymentRefundResponseDTO.class))
                .toList();
    }

    public PaymentRefundResponseDTO getRefundById(String email, Long paymentId, String refundId) {
        admin(email);
        PaymentRefund refund =
                refunds.getById(refundId).orElseThrow(ResourceNotFoundException::new);
        if (!refund.getPayment().getId().equals(paymentId)) throw new ResourceNotFoundException();
        return mapper.map(refund, PaymentRefundResponseDTO.class);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public String prepareItems(String email, Long paymentId, ItemRefundRequestDTO request) {
        UserModel actor = admin(email);
        properties.requireEnabled();
        if (request == null
                || request.getReference() == null
                || request.getReason() == null
                || request.getReason().isBlank()
                || request.getReason().length() > 500
                || request.getItems() == null
                || request.getItems().isEmpty()
                || request.getItems().size() > 100)
            throw new InvalidRequestException("Referencia, motivo y articulos requeridos");
        Payment initial = payment(paymentId);
        Order order = workflow.lockOrder(initial.getCheckout().getOrder().getIdOrder());
        Payment payment = payment(paymentId);
        payments.refreshPayment(payment);
        Map<Long, Integer> quantities = new HashMap<>();
        for (var line : request.getItems()) {
            if (line == null
                    || line.getOrderItemId() == null
                    || line.getQuantity() == null
                    || line.getQuantity() <= 0
                    || quantities.put(line.getOrderItemId(), line.getQuantity()) != null)
                throw new InvalidRequestException("Articulos o cantidades invalidos o repetidos");
        }
        PaymentRefund repeated = refunds.getById(request.getReference().toString()).orElse(null);
        if (repeated != null) {
            refunds.refresh(repeated);
            boolean same =
                    repeated.isItemized()
                            && repeated.getPayment().getId().equals(paymentId)
                            && repeated.getReason().equals(request.getReason().trim())
                            && repeated.getItems().size() == quantities.size()
                            && repeated.getItems().stream()
                                    .allMatch(
                                            line ->
                                                    Objects.equals(
                                                            quantities.get(line.getOrderItemId()),
                                                            line.getQuantity()));
            if (!same)
                throw new BusinessConflictException(
                        "La referencia ya corresponde a otra solicitud");
            return repeated.getId();
        }
        refundable(payment);
        if (!payment.getProviderPaymentId().equals(order.getProviderPaymentId())
                || (order.getPaymentStatus() != PaymentStatus.PAID
                        && order.getPaymentStatus() != PaymentStatus.PARTIALLY_REFUNDED)
                || order.getOrderStatus() == OrderStatus.CANCELED
                || order.getOrderStatus() == OrderStatus.EXPIRED)
            throw new BusinessConflictException(
                    "Los parciales por articulo requieren el pago principal de una compra vigente");
        PaymentRefund previous = refunds.getByPaymentId(paymentId).orElse(null);
        if (previous != null) {
            refunds.refresh(previous);
            if (previous.getStatus() != PaymentRefundStatus.CONFIRMED)
                throw new BusinessConflictException(
                        "Debe resolver el reembolso anterior antes de solicitar otro");
        }
        PaymentRefund refund = new PaymentRefund();
        refund.setId(request.getReference().toString());
        refund.setItemized(true);
        BigDecimal amount = BigDecimal.ZERO;
        for (OrderItem item : order.getItemsOrder()) {
            Integer quantity = quantities.remove(item.getIdOrderItem());
            if (quantity == null) continue;
            if (quantity > item.getAmountOrderItem() - item.getRefundRequestedQuantity())
                throw new BusinessConflictException(
                        "Las unidades ya estan reembolsadas o solicitadas");
            physicalReturns.requireItemRefundReady(
                    order, item, item.getRefundRequestedQuantity() + quantity);
            RefundItemAllocation allocation = new RefundItemAllocation();
            allocation.setOrderItemId(item.getIdOrderItem());
            allocation.setQuantity(quantity);
            allocation.setUnitPrice(item.getPriceOrderItem());
            refund.getItems().add(allocation);
            amount = amount.add(item.getPriceOrderItem().multiply(BigDecimal.valueOf(quantity)));
        }
        if (!quantities.isEmpty())
            throw new InvalidRequestException("Hay articulos que no pertenecen a la orden");
        if (amount.signum() <= 0
                || amount.compareTo(payment.getAmount().subtract(payment.getRefundedAmount())) > 0)
            throw new BusinessConflictException("Importe no reembolsable");
        refund.setPayment(payment);
        refund.setActorUserId(actor.getId());
        refund.setReason(request.getReason().trim());
        refund.setAmount(amount);
        refund.setPreviouslyRefundedAmount(payment.getRefundedAmount());
        refund.setProviderOrderId(payment.getCheckout().getProviderOrderId());
        refund.setStatus(PaymentRefundStatus.REQUESTED);
        refund.setRequestedAt(Instant.now());
        reserveRefundItems(order, refund);
        order.setPaymentStatus(PaymentStatus.REVIEW);
        refunds.save(refund);
        refund.setCurrentAttemptId(refund.getId());
        attempts.save(newAttempt(refund, actor.getId(), refund.getReason(), null));
        audit(refund, actor.getId(), "ITEM_REFUND_REQUESTED", null, "REQUESTED");
        return refund.getId();
    }

    private void reserveRefundItems(Order order, PaymentRefund refund) {
        for (OrderItem item :
                order.getItemsOrder().stream()
                        .sorted(
                                Comparator.comparing(
                                        line -> line.getProductOrderItem().getIdProduct()))
                        .toList()) {
            int quantity =
                    refund.getItems().stream()
                            .filter(line -> line.getOrderItemId().equals(item.getIdOrderItem()))
                            .mapToInt(RefundItemAllocation::getQuantity)
                            .sum();
            if (quantity == 0) continue;
            item.setRefundRequestedQuantity(item.getRefundRequestedQuantity() + quantity);
            if (order.getOrderStatus() == OrderStatus.PENDING
                    || order.getOrderStatus() == OrderStatus.CONFIRMED) {
                if (order.isStockDeducted()
                        && item.getProductOrderItem() instanceof ProductPhysical) {
                    ProductPhysical product =
                            (ProductPhysical)
                                    workflow.lockProduct(item.getProductOrderItem().getIdProduct());
                    long stock = (long) product.getStockProduct() + quantity;
                    if (stock > Integer.MAX_VALUE)
                        throw new BusinessConflictException("Stock fuera del limite admitido");
                    product.setStockProduct((int) stock);
                }
                item.setCanceledQuantity(item.getCanceledQuantity() + quantity);
            }
        }
        deliveries.revokeItems(
                order,
                refund.getItems().stream()
                        .map(RefundItemAllocation::getOrderItemId)
                        .collect(Collectors.toSet()));
    }

    public Page<PaymentRefundAttemptResponseDTO> getAttempts(
            String email, Long paymentId, Pageable pageable) {
        admin(email);
        InputRules.page(pageable);
        PaymentRefund refund =
                refunds.getByPaymentId(paymentId).orElseThrow(ResourceNotFoundException::new);
        return attempts.getByRefundId(refund.getId(), pageable)
                .map(attempt -> mapper.map(attempt, PaymentRefundAttemptResponseDTO.class));
    }

    public Page<PaymentRefundAttemptResponseDTO> getAttemptsForRefund(
            String email, Long paymentId, String refundId, Pageable pageable) {
        getRefundById(email, paymentId, refundId);
        InputRules.page(pageable);
        return attempts.getByRefundId(refundId, pageable)
                .map(attempt -> mapper.map(attempt, PaymentRefundAttemptResponseDTO.class));
    }

    private void refundable(Payment payment) {
        PaymentCheckout checkout = payment.getCheckout();
        if (!checkout.getCollectorId().equals(properties.getCollectorId())
                || !checkout.getCurrency().equals(properties.getCurrency())
                || checkout.isLiveMode() != properties.isLiveMode()
                || !checkout.getCollectorId().equals(payment.getCollectorId())
                || !checkout.getCurrency().equals(payment.getCurrency())
                || checkout.isLiveMode() != payment.isLiveMode()
                || checkout.getAmount().compareTo(payment.getAmount()) != 0) {
            throw new BusinessConflictException(
                    "El vendedor, entorno o importe del pago requiere revision");
        }
        boolean recoveredChargeback =
                "charged_back".equals(payment.getProviderStatus())
                        && "reimbursed".equals(payment.getProviderStatusDetail())
                        && Set.of(
                                        PaymentReviewReason.LATE_APPROVAL,
                                        PaymentReviewReason.DUPLICATE_PAYMENT,
                                        PaymentReviewReason.NONE,
                                        PaymentReviewReason.PARTIAL_REFUND)
                                .contains(payment.getReviewReason());
        if ((!"approved".equals(payment.getProviderStatus()) && !recoveredChargeback)
                || payment.getRefundedAmount().compareTo(payment.getAmount()) >= 0
                || !Set.of(
                                PaymentReviewReason.NONE,
                                PaymentReviewReason.LATE_APPROVAL,
                                PaymentReviewReason.DUPLICATE_PAYMENT,
                                PaymentReviewReason.PARTIAL_REFUND)
                        .contains(payment.getReviewReason())) {
            throw new BusinessConflictException(
                    "El pago no permite solicitar esta devolucion; los contracargos se gestionan"
                            + " por separado");
        }
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public String prepare(String email, Long paymentId, String reason) {
        UserModel actor = admin(email);
        properties.requireEnabled();
        if (reason == null || reason.isBlank() || reason.length() > 500)
            throw new InvalidRequestException("Debe indicar un motivo de hasta 500 caracteres");
        Payment initial = payment(paymentId);
        Order order = workflow.lockOrder(initial.getCheckout().getOrder().getIdOrder());
        Payment payment = payment(paymentId);
        payments.refreshPayment(payment);
        PaymentRefund existing = refunds.getByPaymentId(paymentId).orElse(null);
        if (existing != null) {
            refunds.refresh(existing);
            if (!existing.isItemized()) return existing.getId();
            if (existing.getStatus() != PaymentRefundStatus.CONFIRMED)
                throw new BusinessConflictException("Debe resolver el reembolso parcial anterior");
        }
        refundable(payment);
        if (payment.getProviderPaymentId().equals(order.getProviderPaymentId())) {
            physicalReturns.requireRefundReady(order);
        }
        if (payment.getCheckout().getProviderOrderId() == null)
            throw new BusinessConflictException(
                    "El pago no tiene una order de Mercado Pago vinculada");
        PaymentRefund refund = new PaymentRefund();
        refund.setId(UUID.randomUUID().toString());
        refund.setPayment(payment);
        refund.setActorUserId(actor.getId());
        refund.setReason(reason.trim());
        refund.setAmount(payment.getAmount().subtract(payment.getRefundedAmount()));
        refund.setPreviouslyRefundedAmount(payment.getRefundedAmount());
        refund.setProviderOrderId(payment.getCheckout().getProviderOrderId());
        refund.setStatus(PaymentRefundStatus.REQUESTED);
        refund.setRequestedAt(Instant.now());
        // Hold fulfillment only for the purchase's payment; an extra payment must not stop it.
        if (payment.getProviderPaymentId().equals(order.getProviderPaymentId())) {
            order.setPaymentStatus(PaymentStatus.REVIEW);
            deliveries.revokeOrder(order);
        }
        refunds.save(refund);
        refund.setCurrentAttemptId(refund.getId());
        attempts.save(newAttempt(refund, actor.getId(), reason.trim(), null));
        audit(refund, actor.getId(), "REFUND_REQUESTED", null, "REQUESTED");
        return refund.getId();
    }

    @Transactional(timeout = 25)
    public String prepareRetry(
            String email, Long paymentId, String previousAttemptId, String reason) {
        UserModel actor = admin(email);
        properties.requireEnabled();
        if (reason == null
                || reason.isBlank()
                || reason.length() > 500
                || previousAttemptId == null)
            throw new InvalidRequestException(
                    "Debe indicar el intento anterior y un motivo de hasta 500 caracteres");
        try {
            UUID.fromString(previousAttemptId);
        } catch (IllegalArgumentException exception) {
            throw new InvalidRequestException("Identificador de intento invalido");
        }
        Payment initial = payment(paymentId);
        Order order = workflow.lockOrder(initial.getCheckout().getOrder().getIdOrder());
        Payment payment = payment(paymentId);
        payments.refreshPayment(payment);
        PaymentRefundAttempt sourceAttempt =
                attempts.getById(previousAttemptId).orElseThrow(ResourceNotFoundException::new);
        PaymentRefund refund = sourceAttempt.getRefund();
        if (!refund.getPayment().getId().equals(paymentId)) throw new ResourceNotFoundException();
        refunds.refresh(refund);
        PaymentRefundAttempt current = currentAttempt(refund);
        // Replaying the same retry request must reuse its successor, even if it failed too.
        if (previousAttemptId.equals(current.getPreviousAttemptId())) return refund.getId();
        if (!previousAttemptId.equals(current.getId()))
            throw new BusinessConflictException(
                    "El intento indicado ya no es el actual; consulte el historial");
        if (refund.getStatus() != PaymentRefundStatus.REJECTED
                || current.getStatus() != PaymentRefundStatus.REJECTED
                || current.getRejectedAt() == null
                || current.getRejectionCode() == null)
            throw new BusinessConflictException(
                    "Solo un rechazo verificado permite crear otro intento; los resultados"
                            + " inciertos conservan su clave");
        refundable(payment);
        if (payment.getProviderPaymentId().equals(order.getProviderPaymentId())
                && !refund.isItemized()) physicalReturns.requireRefundReady(order);
        verifyRejection(refund, client.getOrder(refund.getProviderOrderId()));
        refund.setCurrentAttemptId(UUID.randomUUID().toString());
        refund.setAttemptNumber(Math.incrementExact(refund.getAttemptNumber()));
        refund.setStatus(PaymentRefundStatus.REQUESTED);
        refund.setProviderRefundId(null);
        refund.setRejectedAt(null);
        refund.setRejectionCode(null);
        refund.setRejectionReason(null);
        PaymentRefundAttempt next =
                newAttempt(refund, actor.getId(), reason.trim(), previousAttemptId);
        attempts.save(next);
        OrderChangeAudit audit = new OrderChangeAudit();
        audit.setOrderId(payment.getCheckout().getOrder().getIdOrder());
        audit.setActorUserId(actor.getId());
        audit.setAction("REFUND_RETRY_REQUESTED");
        audit.setPreviousValue(previousAttemptId);
        audit.setNewValue(next.getId());
        audit.setReason(reason.trim());
        audit.setCreatedAt(LocalDateTime.now());
        workflow.saveAudit(audit);
        return refund.getId();
    }

    private PaymentRefundAttempt newAttempt(
            PaymentRefund refund, Long actorId, String reason, String previousId) {
        PaymentRefundAttempt attempt = new PaymentRefundAttempt();
        attempt.setId(refund.getCurrentAttemptId());
        attempt.setRefund(refund);
        attempt.setAttemptNumber(refund.getAttemptNumber());
        attempt.setPreviousAttemptId(previousId);
        attempt.setActorUserId(actorId);
        attempt.setReason(reason);
        attempt.setRequestedAt(Instant.now());
        attempt.setStatus(PaymentRefundStatus.REQUESTED);
        return attempt;
    }

    private PaymentRefundAttempt currentAttempt(PaymentRefund refund) {
        PaymentRefundAttempt attempt =
                attempts.getById(refund.getCurrentAttemptId())
                        .orElseThrow(
                                () ->
                                        new BusinessConflictException(
                                                "Falta migrar el historial de intentos de este"
                                                        + " reembolso"));
        attempts.refresh(attempt);
        if (!attempt.getRefund().getId().equals(refund.getId()))
            throw new BusinessConflictException("El intento no pertenece a esta solicitud");
        return attempt;
    }

    // The request and its key were committed before this transaction/network operation.
    @Transactional(timeout = 40)
    public void dispatch(String email, String refundId) {
        admin(email);
        properties.requireEnabled();
        PaymentRefund initial =
                refunds.getById(refundId).orElseThrow(ResourceNotFoundException::new);
        workflow.lockOrder(initial.getPayment().getCheckout().getOrder().getIdOrder());
        PaymentRefund refund =
                refunds.getById(refundId).orElseThrow(ResourceNotFoundException::new);
        refunds.refresh(refund);
        if (refund.getStatus() == PaymentRefundStatus.CONFIRMED
                || refund.getStatus() == PaymentRefundStatus.REJECTED) return;
        PaymentRefundAttempt attempt = currentAttempt(refund);
        Payment payment = refund.getPayment();
        payments.refreshPayment(payment);
        refundable(payment);
        if (!refund.getProviderOrderId().equals(payment.getCheckout().getProviderOrderId())
                || refund.getPreviouslyRefundedAmount().compareTo(payment.getRefundedAmount())
                        != 0) {
            throw new BusinessConflictException(
                    "El pago cambio durante el reembolso; sincronice y revise la operacion");
        }
        if (refund.getStatus() == PaymentRefundStatus.PROCESSING) return;
        MercadoPagoClient.RefundReceipt receipt;
        try {
            receipt = client.refundPayment(refund);
        } catch (RefundRejectedException rejection) {
            verifyRejection(refund, client.getOrder(refund.getProviderOrderId()));
            String description =
                    switch (rejection.getProviderCode()) {
                        case "refund_amount_exceeds" ->
                                "El importe solicitado supera el disponible para reembolso";
                        case "transaction_not_found" ->
                                "Mercado Pago rechazo el identificador de la transaccion";
                        case "cannot_refund_order" ->
                                "Mercado Pago no permite reembolsar la orden en su estado actual";
                        default ->
                                throw new ExternalServiceException(
                                        "El rechazo del reembolso requiere revision");
                    };
            refund.setStatus(PaymentRefundStatus.REJECTED);
            refund.setRejectedAt(Instant.now());
            refund.setRejectionCode(rejection.getProviderCode());
            refund.setRejectionReason(description);
            attempt.setStatus(PaymentRefundStatus.REJECTED);
            attempt.setRejectedAt(refund.getRejectedAt());
            attempt.setRejectionCode(refund.getRejectionCode());
            attempt.setRejectionReason(description);
            audit(refund, null, "REFUND_REJECTED", "REQUESTED", "REJECTED");
            return;
        }
        refund.setProviderRefundId(receipt.id());
        refund.setStatus(PaymentRefundStatus.PROCESSING);
        attempt.setProviderRefundId(receipt.id());
        attempt.setStatus(PaymentRefundStatus.PROCESSING);
        audit(refund, null, "REFUND_ACCEPTED", "REQUESTED", "PROCESSING");
    }

    private void verifyRejection(PaymentRefund refund, MercadoPagoClient.VerifiedOrder remote) {
        Payment payment = refund.getPayment();
        PaymentCheckout checkout = payment.getCheckout();
        boolean matches =
                remote != null
                        && refund.getProviderOrderId().equals(remote.id())
                        && checkout.getReference().equals(remote.reference())
                        && checkout.getCollectorId().equals(remote.collectorId())
                        && checkout.getCurrency().equals(remote.currency())
                        && checkout.isLiveMode() == remote.liveMode()
                        && checkout.getAmount().compareTo(remote.amount()) == 0
                        && remote.unsettledRefundPaymentIds() != null
                        && !remote.unsettledRefundPaymentIds()
                                .contains(payment.getProviderPaymentId());
        var snapshots =
                remote == null || remote.payments() == null
                        ? List.<MercadoPagoClient.VerifiedPayment>of()
                        : remote.payments().stream()
                                .filter(
                                        snapshot ->
                                                payment.getProviderPaymentId()
                                                        .equals(snapshot.id()))
                                .toList();
        if (!matches || snapshots.size() != 1)
            throw new ExternalServiceException("No se pudo verificar el resultado del reembolso");
        var snapshot = snapshots.get(0);
        if ((!"approved".equals(snapshot.status())
                        && !("charged_back".equals(snapshot.status())
                                && "reimbursed".equals(snapshot.statusDetail())))
                || !checkout.getReference().equals(snapshot.reference())
                || !checkout.getCollectorId().equals(snapshot.collectorId())
                || !checkout.getCurrency().equals(snapshot.currency())
                || checkout.isLiveMode() != snapshot.liveMode()
                || payment.getAmount().compareTo(snapshot.amount()) != 0
                || refund.getPreviouslyRefundedAmount().compareTo(snapshot.refundedAmount()) != 0
                || snapshot.updatedAt() == null
                || (payment.getProviderUpdatedAt() != null
                        && snapshot.updatedAt().isBefore(payment.getProviderUpdatedAt())))
            throw new ExternalServiceException(
                    "El resultado financiero requiere sincronizacion antes de registrar un"
                            + " rechazo");
    }

    private void audit(
            PaymentRefund refund, Long actorId, String action, String before, String after) {
        OrderChangeAudit audit = new OrderChangeAudit();
        audit.setOrderId(refund.getPayment().getCheckout().getOrder().getIdOrder());
        audit.setActorUserId(actorId);
        audit.setAction(action);
        audit.setPreviousValue(before);
        audit.setNewValue(after);
        audit.setReason("Intento " + refund.getCurrentAttemptId() + ": " + refund.getReason());
        if (refund.getStatus() == PaymentRefundStatus.REJECTED)
            audit.setReason(
                    "Intento "
                            + refund.getCurrentAttemptId()
                            + ": "
                            + refund.getRejectionCode()
                            + ": "
                            + refund.getRejectionReason());
        // The request stores the full reason; the audit column is capped at 500 characters.
        if (audit.getReason().length() > 500) audit.setReason(audit.getReason().substring(0, 500));
        audit.setCreatedAt(LocalDateTime.now());
        workflow.saveAudit(audit);
    }
}

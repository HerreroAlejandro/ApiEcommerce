package com.api.crud.services;

import com.api.crud.DTO.PaymentRefundResponseDTO;
import com.api.crud.config.MercadoPagoProperties;
import com.api.crud.exceptions.*;
import com.api.crud.models.entity.*;
import com.api.crud.models.enums.*;
import com.api.crud.repositories.*;

import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

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

    public PaymentRefundPersistence(
            PaymentDao payments,
            PaymentRefundDao refunds,
            OrderWorkflowDao workflow,
            UserDao users,
            MercadoPagoProperties properties,
            MercadoPagoClient client,
            DigitalDeliveryService deliveries,
            ModelMapper mapper,
            PhysicalReturnService physicalReturns) {
        this.payments = payments;
        this.refunds = refunds;
        this.workflow = workflow;
        this.users = users;
        this.properties = properties;
        this.client = client;
        this.deliveries = deliveries;
        this.mapper = mapper;
        this.physicalReturns = physicalReturns;
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
        if (!"approved".equals(payment.getProviderStatus())
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

    @Transactional
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
            return existing.getId();
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
        audit(refund, actor.getId(), "REFUND_REQUESTED", null, "REQUESTED");
        return refund.getId();
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
        if (refund.getStatus() == PaymentRefundStatus.CONFIRMED) return;
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
        MercadoPagoClient.RefundReceipt receipt = client.refundPayment(refund);
        refund.setProviderRefundId(receipt.id());
        refund.setStatus(PaymentRefundStatus.PROCESSING);
        audit(refund, null, "REFUND_ACCEPTED", "REQUESTED", "PROCESSING");
    }

    private void audit(
            PaymentRefund refund, Long actorId, String action, String before, String after) {
        OrderChangeAudit audit = new OrderChangeAudit();
        audit.setOrderId(refund.getPayment().getCheckout().getOrder().getIdOrder());
        audit.setActorUserId(actorId);
        audit.setAction(action);
        audit.setPreviousValue(before);
        audit.setNewValue(after);
        audit.setReason("Reembolso " + refund.getId() + ": " + refund.getReason());
        // The request stores the full reason; the audit column is capped at 500 characters.
        if (audit.getReason().length() > 500) audit.setReason(audit.getReason().substring(0, 500));
        audit.setCreatedAt(LocalDateTime.now());
        workflow.saveAudit(audit);
    }
}

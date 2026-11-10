package com.api.crud.services;

import com.api.crud.DTO.PaymentResponseDTO;
import com.api.crud.exceptions.*;
import com.api.crud.models.entity.*;
import com.api.crud.models.enums.*;
import com.api.crud.repositories.*;
import com.api.crud.services.MercadoPagoClient.VerifiedOrder;
import com.api.crud.services.MercadoPagoClient.VerifiedPayment;

import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

// decide cómo un pago verificado afecta a la orden.
@Service
public class PaymentConfirmationService {

    private static final Set<String> ALLOWED_METHODS =
            Set.of("credit_card", "debit_card", "account_money");

    private final PaymentDao payments;

    private final OrderWorkflowDao workflow;

    private final DigitalDeliveryService deliveries;

    private final OrderService orders;

    private final ModelMapper mapper;

    private final PaymentRefundDao refunds;

    public PaymentConfirmationService(
            PaymentDao payments,
            OrderWorkflowDao workflow,
            DigitalDeliveryService deliveries,
            OrderService orders,
            ModelMapper mapper,
            PaymentRefundDao refunds) {
        this.payments = payments;
        this.workflow = workflow;
        this.deliveries = deliveries;
        this.orders = orders;
        this.mapper = mapper;
        this.refunds = refunds;
    }

    @Transactional
    public List<PaymentResponseDTO> applyOrder(VerifiedOrder remote) {
        PaymentCheckout initial = payments.getCheckoutByReference(remote.reference()).orElse(null);
        if (initial == null) return null;
        Order order = workflow.lockOrder(initial.getOrder().getIdOrder());
        PaymentCheckout checkout =
                payments.getCheckoutByOrderId(order.getIdOrder())
                        .orElseThrow(ResourceNotFoundException::new);
        payments.refreshCheckout(checkout);
        if (!checkout.getReference().equals(remote.reference())
                || !checkout.getCollectorId().equals(remote.collectorId())
                || !checkout.getCurrency().equals(remote.currency())
                || checkout.getAmount().compareTo(remote.amount()) != 0
                || checkout.isLiveMode() != remote.liveMode()
                || (checkout.getProviderOrderId() != null
                        && !checkout.getProviderOrderId().equals(remote.id()))) {
            throw new BusinessConflictException(
                    "La order de Mercado Pago no corresponde a este checkout");
        }
        // Also recovers the binding if creation succeeded but its HTTP response timed out.
        checkout.setProviderOrderId(remote.id());
        List<PaymentResponseDTO> result = new ArrayList<>();
        for (VerifiedPayment payment : remote.payments()) {
            result.add(applyLocked(payment, order, checkout));
        }
        return result;
    }

    @Transactional
    public PaymentResponseDTO apply(VerifiedPayment remote) {
        if (remote.updatedAt() == null)
            throw new ExternalServiceException("El pago no tiene una version verificable");
        PaymentCheckout initial = payments.getCheckoutByReference(remote.reference()).orElse(null);
        // A seller may share this Mercado Pago account with other applications.
        if (initial == null) return null;
        Order order = workflow.lockOrder(initial.getOrder().getIdOrder());
        PaymentCheckout checkout =
                payments.getCheckoutByOrderId(order.getIdOrder())
                        .orElseThrow(ResourceNotFoundException::new);
        return applyLocked(remote, order, checkout);
    }

    private PaymentResponseDTO applyLocked(
            VerifiedPayment remote, Order order, PaymentCheckout checkout) {
        if (remote.updatedAt() == null)
            throw new ExternalServiceException("El pago no tiene una version verificable");
        if (!checkout.getReference().equals(remote.reference()))
            throw new BusinessConflictException("Referencia de pago incompatible");
        Payment payment = payments.getByProviderId(remote.id()).orElse(null);
        if (payment != null
                && !payment.getCheckout().getReference().equals(checkout.getReference()))
            throw new BusinessConflictException("El pago ya pertenece a otra compra");
        if (payment != null
                && "refunded".equals(remote.status())
                && remote.refundedAmount().compareTo(remote.amount()) == 0
                && mismatch(checkout, remote) == PaymentReviewReason.NONE) {
            confirmRefundRequest(payment, remote);
        }
        if (payment != null
                && (remote.updatedAt().isBefore(payment.getProviderUpdatedAt())
                        || sameSnapshot(payment, remote))) {
            return mapper.map(payment, PaymentResponseDTO.class);
        }
        String previous = payment == null ? null : payment.getProviderStatus();
        boolean fresh = payment == null;
        if (payment == null) {
            payment = new Payment();
            payment.setCheckout(checkout);
            payment.setProviderPaymentId(remote.id());
        }
        payment.setProviderStatus(remote.status());
        payment.setAmount(remote.amount());
        payment.setRefundedAmount(remote.refundedAmount());
        payment.setCurrency(remote.currency());
        payment.setCollectorId(remote.collectorId());
        payment.setLiveMode(remote.liveMode());
        payment.setPaymentMethod(remote.paymentMethod());
        payment.setPaymentType(remote.paymentType());
        payment.setApprovedAt(remote.approvedAt());
        payment.setProviderUpdatedAt(remote.updatedAt());
        payment.setVerifiedAt(Instant.now());
        PaymentReviewReason mismatch = mismatch(checkout, remote);
        if (mismatch == PaymentReviewReason.NONE
                && (order.getTotalOrder() == null
                        || checkout.getAmount().compareTo(order.getTotalOrder()) != 0)) {
            mismatch = PaymentReviewReason.AMOUNT_MISMATCH;
        }
        payment.setReviewReason(mismatch);
        if (fresh) payments.savePayment(payment);
        boolean winner = remote.id().equals(order.getProviderPaymentId());
        if (mismatch != PaymentReviewReason.NONE) {
            if (winner) hold(order);
        } else if ("refunded".equals(remote.status())
                && remote.refundedAmount().compareTo(remote.amount()) == 0) {
            refund(order, remote);
        } else if ("charged_back".equals(remote.status())) {
            payment.setReviewReason(PaymentReviewReason.CHARGEBACK);
            if (winner || order.getProviderPaymentId() == null) hold(order);
        } else if (remote.refundedAmount().signum() > 0) {
            payment.setReviewReason(PaymentReviewReason.PARTIAL_REFUND);
            if (winner || order.getProviderPaymentId() == null) {
                if (order.getProviderPaymentId() == null) order.setProviderPaymentId(remote.id());
                hold(order);
            }
        } else if ("approved".equals(remote.status())) {
            approve(order, checkout, payment, remote);
        } else if (Set.of("pending", "in_process", "authorized", "rejected", "cancelled")
                .contains(remote.status())) {
            if (winner) {
                payment.setReviewReason(PaymentReviewReason.INVALID_PROVIDER_STATE);
                hold(order);
            } else if (order.getPaymentStatus() == PaymentStatus.PENDING
                    || order.getPaymentStatus() == PaymentStatus.FAILED) {
                boolean pending =
                        Set.of("pending", "in_process", "authorized").contains(remote.status());
                if (pending) order.setPaymentStatus(PaymentStatus.PENDING);
                else if (payments.getByOrderId(order.getIdOrder()).stream()
                        .noneMatch(
                                p ->
                                        Set.of("pending", "in_process", "authorized")
                                                .contains(p.getProviderStatus())))
                    order.setPaymentStatus(PaymentStatus.FAILED);
            }
        } else {
            payment.setReviewReason(PaymentReviewReason.INVALID_PROVIDER_STATE);
            if (winner) hold(order);
        }
        OrderChangeAudit audit = new OrderChangeAudit();
        audit.setOrderId(order.getIdOrder());
        audit.setAction("PAYMENT_VERIFIED");
        audit.setPreviousValue(previous);
        audit.setNewValue(remote.status() + ":" + payment.getReviewReason());
        audit.setReason("Confirmacion verificada de Mercado Pago, pago " + remote.id());
        audit.setCreatedAt(LocalDateTime.now());
        workflow.saveAudit(audit);
        return mapper.map(payment, PaymentResponseDTO.class);
    }

    private PaymentReviewReason mismatch(PaymentCheckout checkout, VerifiedPayment payment) {
        if (!checkout.getCollectorId().equals(payment.collectorId()))
            return PaymentReviewReason.COLLECTOR_MISMATCH;
        if (checkout.isLiveMode() != payment.liveMode()) return PaymentReviewReason.MODE_MISMATCH;
        if (!checkout.getCurrency().equals(payment.currency()))
            return PaymentReviewReason.CURRENCY_MISMATCH;
        if (checkout.getAmount().compareTo(payment.amount()) != 0)
            return PaymentReviewReason.AMOUNT_MISMATCH;
        if (payment.refundedAmount().compareTo(payment.amount()) > 0)
            return PaymentReviewReason.INVALID_PROVIDER_STATE;
        return PaymentReviewReason.NONE;
    }

    private void approve(
            Order order, PaymentCheckout checkout, Payment payment, VerifiedPayment remote) {
        if (remote.id().equals(order.getProviderPaymentId())
                && refunds.getByPaymentId(payment.getId())
                        .filter(refund -> refund.getStatus() != PaymentRefundStatus.CONFIRMED)
                        .isPresent()) return;
        if (remote.id().equals(order.getProviderPaymentId())
                && order.getPaymentStatus() == PaymentStatus.PAID) return;
        if (order.getProviderPaymentId() != null
                        && !remote.id().equals(order.getProviderPaymentId())
                || order.getPaymentStatus() == PaymentStatus.PAID) {
            payment.setReviewReason(PaymentReviewReason.DUPLICATE_PAYMENT);
            return;
        }
        if (!ALLOWED_METHODS.contains(remote.paymentType() == null ? "" : remote.paymentType())) {
            payment.setReviewReason(PaymentReviewReason.UNSUPPORTED_METHOD);
            return;
        }
        if (remote.approvedAt() == null
                || remote.approvedAt().isAfter(Instant.now().plusSeconds(60))) {
            payment.setReviewReason(PaymentReviewReason.INVALID_PROVIDER_STATE);
            return;
        }
        if (order.getOrderStatus() != OrderStatus.PENDING
                || !order.isStockDeducted()
                || !remote.approvedAt().isBefore(checkout.getExpiresAt())
                || (order.getPaymentStatus() != PaymentStatus.PENDING
                        && order.getPaymentStatus() != PaymentStatus.FAILED)) {
            payment.setReviewReason(PaymentReviewReason.LATE_APPROVAL);
            // Release an overdue, still-unpaid reservation before flagging the money for review.
            orders.expireReservedOrder(order);
            if (order.getProviderPaymentId() == null) hold(order);
            return;
        }
        order.setProviderPaymentId(remote.id());
        order.setPaymentStatus(PaymentStatus.PAID);
        deliveries.enablePaidOrder(order);
    }

    private void confirmRefundRequest(Payment payment, VerifiedPayment remote) {
        refunds.getByPaymentId(payment.getId())
                .ifPresent(
                        refund -> {
                            refunds.refresh(refund);
                            if (refund.getStatus() == PaymentRefundStatus.CONFIRMED
                                    || refund.getPreviouslyRefundedAmount()
                                                    .add(refund.getAmount())
                                                    .compareTo(remote.refundedAmount())
                                            != 0) return;
                            String before = refund.getStatus().name();
                            refund.setStatus(PaymentRefundStatus.CONFIRMED);
                            refund.setConfirmedAt(Instant.now());
                            OrderChangeAudit audit = new OrderChangeAudit();
                            audit.setOrderId(payment.getCheckout().getOrder().getIdOrder());
                            audit.setAction("REFUND_CONFIRMED");
                            audit.setPreviousValue(before);
                            audit.setNewValue("CONFIRMED");
                            audit.setReason(
                                    "Reembolso verificado de Mercado Pago, solicitud "
                                            + refund.getId());
                            audit.setCreatedAt(LocalDateTime.now());
                            workflow.saveAudit(audit);
                        });
    }

    private void refund(Order order, VerifiedPayment remote) {
        if (order.getProviderPaymentId() != null
                && !remote.id().equals(order.getProviderPaymentId())) return;
        if (order.getProviderPaymentId() == null && order.getPaymentStatus() == PaymentStatus.PAID)
            return;
        if (order.getPaymentStatus() == PaymentStatus.REFUNDED) return;
        order.setProviderPaymentId(remote.id());
        order.setPaymentStatus(PaymentStatus.REFUNDED);
        deliveries.revokeOrder(order);
        orders.cancelRefundedOrder(order);
    }

    private void hold(Order order) {
        if (order.getPaymentStatus() == PaymentStatus.REVIEW) return;
        order.setPaymentStatus(PaymentStatus.REVIEW);
        deliveries.revokeOrder(order);
    }

    private boolean sameSnapshot(Payment saved, VerifiedPayment remote) {
        return saved.getProviderUpdatedAt().equals(remote.updatedAt())
                && Objects.equals(saved.getProviderStatus(), remote.status())
                && saved.getAmount().compareTo(remote.amount()) == 0
                && saved.getRefundedAmount().compareTo(remote.refundedAmount()) == 0
                && Objects.equals(saved.getCurrency(), remote.currency())
                && Objects.equals(saved.getCollectorId(), remote.collectorId())
                && saved.isLiveMode() == remote.liveMode()
                && Objects.equals(saved.getPaymentMethod(), remote.paymentMethod())
                && Objects.equals(saved.getPaymentType(), remote.paymentType())
                && Objects.equals(saved.getApprovedAt(), remote.approvedAt());
    }
}

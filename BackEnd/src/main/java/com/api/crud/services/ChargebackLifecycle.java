package com.api.crud.services;

import com.api.crud.models.entity.*;
import com.api.crud.models.enums.*;
import com.api.crud.repositories.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class ChargebackLifecycle {
    private final ChargebackDao cases;
    private final PaymentRefundDao refunds;
    private final OrderWorkflowDao workflow;
    private final OrderService orders;
    private final DigitalDeliveryService deliveries;

    public ChargebackLifecycle(
            ChargebackDao cases,
            PaymentRefundDao refunds,
            OrderWorkflowDao workflow,
            OrderService orders,
            DigitalDeliveryService deliveries) {
        this.cases = cases;
        this.refunds = refunds;
        this.workflow = workflow;
        this.orders = orders;
        this.deliveries = deliveries;
    }

    // Called only with a canonical, identity-checked payment while holding its order lock.
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean apply(Payment payment, MercadoPagoClient.VerifiedPayment remote) {
        List<ChargebackCase> history = new ArrayList<>(cases.getByPaymentId(payment.getId()));
        for (String caseId : remote.chargebackCaseIds()) {
            if (history.stream().anyMatch(record -> record.getProviderCaseId().equals(caseId)))
                continue;
            ChargebackCase record = new ChargebackCase();
            record.setPayment(payment);
            record.setProviderCaseId(caseId);
            record.setDetectedAt(Instant.now());
            cases.save(record);
            history.add(record);
            audit(payment, "CHARGEBACK_DETECTED", null, caseId);
        }
        if (history.isEmpty()
                && !"charged_back".equals(remote.status())
                && payment.getReviewReason() != PaymentReviewReason.CHARGEBACK
                && payment.getReviewReason() != PaymentReviewReason.CHARGEBACK_LOST) return false;
        history.forEach(cases::refresh);
        Order order = payment.getCheckout().getOrder();
        boolean primary = payment.getProviderPaymentId().equals(order.getProviderPaymentId());
        if (order.getProviderPaymentId() == null) {
            order.setProviderPaymentId(payment.getProviderPaymentId());
            primary = true;
        }
        PaymentStatus before = order.getPaymentStatus();
        PaymentReviewReason previousReason = payment.getReviewReason();
        boolean open =
                history.isEmpty()
                        || history.stream()
                                .anyMatch(
                                        record ->
                                                record.getResolution()
                                                        == ChargebackResolution.OPEN);
        boolean lost =
                history.stream()
                        .anyMatch(record -> record.getResolution() == ChargebackResolution.LOST);
        boolean credited =
                "approved".equals(remote.status())
                        || ("charged_back".equals(remote.status())
                                && "reimbursed".equals(remote.statusDetail()))
                        || ("refunded".equals(remote.status())
                                && remote.refundedAmount().compareTo(remote.amount()) == 0);
        if (primary && (open || lost || !credited)) {
            if (before == PaymentStatus.REVIEW
                    && previousReason != PaymentReviewReason.CHARGEBACK
                    && previousReason != PaymentReviewReason.CHARGEBACK_LOST)
                deliveries.revokeOrder(order);
            if (before != PaymentStatus.CHARGED_BACK || !lost) hold(order);
            orders.cancelChargebackOrder(order);
        }
        if (lost) {
            payment.setReviewReason(PaymentReviewReason.CHARGEBACK_LOST);
            if (primary) order.setPaymentStatus(PaymentStatus.CHARGED_BACK);
        } else if (open || !credited) {
            payment.setReviewReason(PaymentReviewReason.CHARGEBACK);
        } else if (!primary) {
            payment.setReviewReason(PaymentReviewReason.DUPLICATE_PAYMENT);
        } else if (remote.refundedAmount().compareTo(remote.amount()) == 0) {
            payment.setReviewReason(PaymentReviewReason.NONE);
            order.setPaymentStatus(PaymentStatus.REFUNDED);
            if (before != PaymentStatus.REFUNDED) {
                deliveries.revokeOrder(order);
                orders.cancelRefundedOrder(order);
            }
            for (OrderItem item : order.getItemsOrder()) {
                item.setRefundRequestedQuantity(item.getAmountOrderItem());
                item.setRefundedQuantity(item.getAmountOrderItem());
                if (order.getOrderStatus() == OrderStatus.CANCELED)
                    item.setCanceledQuantity(item.getAmountOrderItem());
            }
        } else if (order.getOrderStatus() == OrderStatus.CANCELED
                || order.getOrderStatus() == OrderStatus.EXPIRED) {
            payment.setReviewReason(PaymentReviewReason.LATE_APPROVAL);
            hold(order);
        } else {
            List<PaymentRefund> requests = refunds.getAllByPaymentId(payment.getId());
            boolean pending =
                    requests.stream()
                            .anyMatch(
                                    request ->
                                            request.getStatus() != PaymentRefundStatus.CONFIRMED);
            BigDecimal explained =
                    requests.stream()
                            .filter(PaymentRefund::isItemized)
                            .filter(request -> request.getStatus() == PaymentRefundStatus.CONFIRMED)
                            .map(PaymentRefund::getAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (pending || explained.compareTo(remote.refundedAmount()) != 0) {
                payment.setReviewReason(
                        remote.refundedAmount().signum() > 0
                                ? PaymentReviewReason.PARTIAL_REFUND
                                : PaymentReviewReason.NONE);
                hold(order);
            } else {
                payment.setReviewReason(PaymentReviewReason.NONE);
                order.setPaymentStatus(
                        remote.refundedAmount().signum() == 0
                                ? PaymentStatus.PAID
                                : PaymentStatus.PARTIALLY_REFUNDED);
                if (before != order.getPaymentStatus()) deliveries.restorePaidOrderAccess(order);
            }
        }
        if (before != order.getPaymentStatus() || previousReason != payment.getReviewReason())
            audit(
                    payment,
                    "CHARGEBACK_PAYMENT_STATE",
                    before + ":" + previousReason,
                    order.getPaymentStatus() + ":" + payment.getReviewReason());
        return true;
    }

    private void hold(Order order) {
        if (order.getPaymentStatus() != PaymentStatus.REVIEW) {
            order.setPaymentStatus(PaymentStatus.REVIEW);
            deliveries.revokeOrder(order);
        }
    }

    private void audit(Payment payment, String action, String before, String after) {
        OrderChangeAudit audit = new OrderChangeAudit();
        audit.setOrderId(payment.getCheckout().getOrder().getIdOrder());
        audit.setAction(action);
        audit.setPreviousValue(before);
        audit.setNewValue(after);
        audit.setReason("Contracargo verificado del pago " + payment.getProviderPaymentId());
        audit.setCreatedAt(LocalDateTime.now());
        workflow.saveAudit(audit);
    }
}

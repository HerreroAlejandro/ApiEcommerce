package com.api.crud.services;

import com.api.crud.DTO.*;
import com.api.crud.exceptions.*;
import com.api.crud.models.entity.UserModel;
import com.api.crud.repositories.*;
import com.api.crud.validation.InputRules;

import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// coordinan los endpoints y sus permisos.
@Service
public class PaymentService {

    private final PaymentCheckoutPersistence checkout;

    private final PaymentConfirmationService confirmations;

    private final MercadoPagoClient client;

    private final MercadoPagoWebhookVerifier verifier;

    private final PaymentDao payments;

    private final OrderDao orders;

    private final UserDao users;

    private final ModelMapper mapper;

    public PaymentService(
            PaymentCheckoutPersistence checkout,
            PaymentConfirmationService confirmations,
            MercadoPagoClient client,
            MercadoPagoWebhookVerifier verifier,
            PaymentDao payments,
            OrderDao orders,
            UserDao users,
            ModelMapper mapper) {
        this.checkout = checkout;
        this.confirmations = confirmations;
        this.client = client;
        this.verifier = verifier;
        this.payments = payments;
        this.orders = orders;
        this.users = users;
        this.mapper = mapper;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CheckoutResponseDTO createCheckout(String email, Long orderId) {
        String reference = checkout.prepare(email, orderId);
        return checkout.ensureOrder(email, reference);
    }

    public void webhook(String dataId, String requestId, String signature) {
        verifier.verify(dataId, requestId, signature);
        confirmations.applyOrder(client.getOrder(dataId));
    }

    private UserModel user(String email) {
        return users.getUserByEmail(email).orElseThrow(UnauthenticatedException::new);
    }

    private void staff(String email) {
        if (user(email).getRoleNames().stream()
                .noneMatch(role -> role.equals("ADMIN") || role.equals("SUPPORT")))
            throw new ForbiddenOperationException();
    }

    @Transactional(readOnly = true)
    public List<PaymentResponseDTO> getMyPayments(String email, Long orderId) {
        long userId = user(email).getId();
        orders.getOrderById(orderId)
                .filter(
                        order ->
                                order.getUserOrder() != null
                                        && order.getUserOrder().getId() == userId)
                .orElseThrow(ResourceNotFoundException::new);
        return payments.getByOrderId(orderId).stream()
                .map(payment -> mapper.map(payment, PaymentResponseDTO.class))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PaymentResponseDTO> getAdminPayments(String email, Long orderId) {
        staff(email);
        orders.getOrderById(orderId).orElseThrow(ResourceNotFoundException::new);
        return payments.getByOrderId(orderId).stream()
                .map(payment -> mapper.map(payment, PaymentResponseDTO.class))
                .toList();
    }

    public List<PaymentResponseDTO> synchronize(String email, String providerOrderId) {
        if (!user(email).getRoleNames().contains("ADMIN")) throw new ForbiddenOperationException();
        List<PaymentResponseDTO> result =
                confirmations.applyOrder(client.getOrder(providerOrderId));
        if (result == null)
            throw new ResourceNotFoundException("El pago no pertenece a una orden de esta API");
        return result;
    }

    @Transactional(readOnly = true)
    public Page<PaymentAdminResponseDTO> getReviewPayments(String email, Pageable pageable) {
        staff(email);
        InputRules.page(pageable);
        return payments.getReviewPayments(pageable)
                .map(payment -> mapper.map(payment, PaymentAdminResponseDTO.class));
    }
}

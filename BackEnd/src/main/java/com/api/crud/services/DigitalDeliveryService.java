package com.api.crud.services;

import com.api.crud.DTO.DigitalDeliveryResponseDTO;
import com.api.crud.DTO.DownloadTokenResponseDTO;
import com.api.crud.exceptions.*;
import com.api.crud.models.entity.*;
import com.api.crud.models.enums.*;
import com.api.crud.repositories.*;

import jakarta.annotation.PostConstruct;

import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class DigitalDeliveryService {

    private final DigitalDeliveryDao deliveries;

    private final OrderDao orders;

    private final OrderWorkflowDao workflow;

    private final UserDao users;

    private final ModelMapper mapper;

    private final DigitalFileGateway files;

    private final SecureRandom random = new SecureRandom();

    @Value("${digital.download.token-minutes:15}")
    private long tokenMinutes = 15;

    public DigitalDeliveryService(
            DigitalDeliveryDao deliveries,
            OrderDao orders,
            OrderWorkflowDao workflow,
            UserDao users,
            ModelMapper mapper,
            DigitalFileGateway files) {
        this.deliveries = deliveries;
        this.orders = orders;
        this.workflow = workflow;
        this.users = users;
        this.mapper = mapper;
        this.files = files;
    }

    @PostConstruct
    void validateTokenMinutes() {
        if (tokenMinutes < 1)
            throw new IllegalStateException("La duracion del permiso debe ser positiva");
    }

    private UserModel user(String email) {
        return users.getUserByEmail(email).orElseThrow(UnauthenticatedException::new);
    }

    private void requirePaid(Order order) {
        if (order.getPaymentStatus() != PaymentStatus.PAID
                || order.getOrderStatus() == OrderStatus.CANCELED
                || order.getOrderStatus() == OrderStatus.EXPIRED) {
            throw new BusinessConflictException("La compra no habilita la descarga");
        }
    }

    public List<DigitalDeliveryResponseDTO> getMyDeliveries(String email, Long orderId) {
        UserModel buyer = user(email);
        orders.getOrderById(orderId)
                .filter(order -> order.getUserOrder().getId() == buyer.getId())
                .orElseThrow(ResourceNotFoundException::new);
        return deliveries.getByOrderId(orderId).stream()
                .map(delivery -> mapper.map(delivery, DigitalDeliveryResponseDTO.class))
                .toList();
    }

    public List<DigitalDeliveryResponseDTO> getAdminDeliveries(String email, Long orderId) {
        requireStaff(user(email));
        orders.getOrderById(orderId).orElseThrow(ResourceNotFoundException::new);
        return deliveries.getByOrderId(orderId).stream()
                .map(delivery -> mapper.map(delivery, DigitalDeliveryResponseDTO.class))
                .toList();
    }

    private void requireStaff(UserModel actor) {
        if (actor.getRoleNames().stream()
                .noneMatch(role -> role.equals("ADMIN") || role.equals("SUPPORT"))) {
            throw new ForbiddenOperationException();
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void enablePaidOrder(Order order) {
        requirePaid(order);
        Set<Long> existing = new HashSet<>();
        deliveries
                .getByOrderId(order.getIdOrder())
                .forEach(delivery -> existing.add(delivery.getOrderItem().getIdOrderItem()));
        for (OrderItem item : order.getItemsOrder()) {
            if (!(item.getProductOrderItem() instanceof ProductDigital)
                    || existing.contains(item.getIdOrderItem())) continue;
            if (item.getDigitalDownloadLink() == null || item.getDigitalDownloadLink().isBlank()) {
                throw new BusinessConflictException(
                        "La compra digital requiere configurar su contenido historico");
            }
            DigitalDelivery delivery = new DigitalDelivery();
            delivery.setOrderItem(item);
            delivery.setSourceUrl(item.getDigitalDownloadLink());
            delivery.setStatus(DigitalDeliveryStatus.AVAILABLE);
            delivery.setEnabledAt(LocalDateTime.now());
            deliveries.save(delivery);
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void revokeOrder(Order order) {
        for (DigitalDelivery delivery : deliveries.getByOrderId(order.getIdOrder())) {
            delivery.setStatus(DigitalDeliveryStatus.REVOKED);
            delivery.setRevokedAt(LocalDateTime.now());
            invalidateToken(delivery);
        }
    }

    private DigitalDelivery lockDelivery(String email, Long id, boolean staff) {
        UserModel actor = user(email);
        if (staff) {
            requireStaff(actor);
        }
        DigitalDelivery found = deliveries.getById(id).orElseThrow(ResourceNotFoundException::new);
        if (!staff && found.getOrderItem().getOrder().getUserOrder().getId() != actor.getId()) {
            throw new ResourceNotFoundException();
        }
        // All operations lock order before delivery, including payment/refund.
        Order order = workflow.lockOrder(found.getOrderItem().getOrder().getIdOrder());
        if (!staff && order.getUserOrder().getId() != actor.getId())
            throw new ResourceNotFoundException();
        requirePaid(order);
        return deliveries.lockById(id).orElseThrow(ResourceNotFoundException::new);
    }

    @Transactional
    public DownloadTokenResponseDTO issueToken(String email, Long id) {
        DigitalDelivery delivery = lockDelivery(email, id, false);
        if (delivery.getStatus() != DigitalDeliveryStatus.AVAILABLE)
            throw new BusinessConflictException("No quedan intentos de descarga disponibles");
        files.validateSource(delivery.getSourceUrl());
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        delivery.setTokenHash(hash(token));
        delivery.setTokenExpiresAt(LocalDateTime.now().plusMinutes(tokenMinutes));
        DownloadTokenResponseDTO response = mapper.map(delivery, DownloadTokenResponseDTO.class);
        response.setToken(token);
        return response;
    }

    @Transactional
    public DownloadGrant claim(String email, Long id, String token) {
        DigitalDelivery delivery = lockDelivery(email, id, false);
        if (delivery.getStatus() != DigitalDeliveryStatus.AVAILABLE
                || delivery.getTokenHash() == null
                || delivery.getTokenExpiresAt() == null
                || !delivery.getTokenExpiresAt().isAfter(LocalDateTime.now())
                || token == null
                || token.length() > 128
                || !MessageDigest.isEqual(
                        delivery.getTokenHash().getBytes(StandardCharsets.US_ASCII),
                        hash(token).getBytes(StandardCharsets.US_ASCII))) {
            throw new BusinessConflictException(
                    "Permiso de descarga invalido, vencido o utilizado");
        }
        files.validateSource(delivery.getSourceUrl());
        delivery.setStatus(DigitalDeliveryStatus.USED);
        delivery.setAttemptedAt(LocalDateTime.now());
        delivery.setAttemptCount(Math.incrementExact(delivery.getAttemptCount()));
        invalidateToken(delivery);
        audit(
                delivery,
                user(email),
                "DIGITAL_DOWNLOAD_ATTEMPT",
                "AVAILABLE",
                "USED",
                "Inicio del unico intento de descarga autorizado");
        return new DownloadGrant(delivery.getId(), delivery.getSourceUrl());
    }

    @Transactional
    public void reopen(String email, Long id, String reason) {
        if (reason == null || reason.isBlank() || reason.length() > 500)
            throw new InvalidRequestException("Debe indicar un motivo de hasta 500 caracteres");
        DigitalDelivery delivery = lockDelivery(email, id, true);
        if (delivery.getStatus() != DigitalDeliveryStatus.USED)
            throw new BusinessConflictException(
                    "Solo puede rehabilitar una descarga utilizada y pagada");
        delivery.setStatus(DigitalDeliveryStatus.AVAILABLE);
        invalidateToken(delivery);
        audit(delivery, user(email), "DIGITAL_DOWNLOAD_REOPEN", "USED", "AVAILABLE", reason.trim());
    }

    private void invalidateToken(DigitalDelivery delivery) {
        delivery.setTokenHash(null);
        delivery.setTokenExpiresAt(null);
    }

    private void audit(
            DigitalDelivery delivery,
            UserModel actor,
            String action,
            String before,
            String after,
            String reason) {
        OrderChangeAudit audit = new OrderChangeAudit();
        audit.setOrderId(delivery.getOrderItem().getOrder().getIdOrder());
        audit.setActorUserId(actor.getId());
        audit.setAction(action);
        audit.setPreviousValue(delivery.getId() + ":" + before);
        audit.setNewValue(delivery.getId() + ":" + after);
        audit.setReason(reason);
        audit.setCreatedAt(LocalDateTime.now());
        workflow.saveAudit(audit);
    }

    private String hash(String token) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 no disponible", exception);
        }
    }

    public record DownloadGrant(Long deliveryId, String sourceUrl) {}
}

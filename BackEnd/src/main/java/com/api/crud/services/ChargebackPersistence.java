package com.api.crud.services;

import com.api.crud.DTO.ChargebackListDTO;
import com.api.crud.DTO.ChargebackResponseDTO;
import com.api.crud.config.MercadoPagoProperties;
import com.api.crud.exceptions.*;
import com.api.crud.models.entity.*;
import com.api.crud.models.enums.*;
import com.api.crud.repositories.*;
import com.api.crud.validation.InputRules;

import org.modelmapper.ModelMapper;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
@Transactional(readOnly = true)
public class ChargebackPersistence {
    private final ChargebackDao cases;
    private final UserDao users;
    private final OrderWorkflowDao workflow;
    private final MercadoPagoProperties properties;
    private final ModelMapper mapper;

    public ChargebackPersistence(
            ChargebackDao cases,
            UserDao users,
            OrderWorkflowDao workflow,
            MercadoPagoProperties properties,
            ModelMapper mapper) {
        this.cases = cases;
        this.users = users;
        this.workflow = workflow;
        this.properties = properties;
        this.mapper = mapper;
    }

    private UserModel staff(String email, boolean write) {
        UserModel actor = users.getUserByEmail(email).orElseThrow(UnauthenticatedException::new);
        if (actor.getStatus() != AccountStatus.ACTIVE) throw new UnauthenticatedException();
        if (!actor.getRoleNames().contains("ADMIN")
                && (write || !actor.getRoleNames().contains("SUPPORT")))
            throw new ForbiddenOperationException();
        return actor;
    }

    private ChargebackCase getCase(Long id) {
        return cases.getById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Contracargo no encontrado"));
    }

    public Page<ChargebackListDTO> getCases(
            String email, ChargebackResolution resolution, Pageable pageable) {
        staff(email, false);
        InputRules.page(pageable);
        return cases.getCases(resolution, pageable)
                .map(record -> mapper.map(record, ChargebackListDTO.class));
    }

    public ChargebackResponseDTO get(String email, Long id) {
        staff(email, false);
        return mapper.map(getCase(id), ChargebackResponseDTO.class);
    }

    public SyncDetails getSyncDetails(String email, Long id) {
        staff(email, true);
        properties.requireEnabled();
        return details(getCase(id));
    }

    public List<SyncDetails> getOrderCases(String providerOrderId) {
        return cases.getByProviderOrderId(providerOrderId).stream().map(this::details).toList();
    }

    private SyncDetails details(ChargebackCase record) {
        return new SyncDetails(
                record.getId(),
                record.getProviderCaseId(),
                record.getPayment().getCheckout().getProviderOrderId());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void applyVerified(Long id, MercadoPagoClient.VerifiedChargeback remote) {
        properties.requireEnabled();
        ChargebackCase initial = getCase(id);
        workflow.lockOrder(initial.getPayment().getCheckout().getOrder().getIdOrder());
        ChargebackCase record = getCase(id);
        cases.refresh(record);
        Payment payment = record.getPayment();
        PaymentCheckout checkout = payment.getCheckout();
        if (!record.getProviderCaseId().equals(remote.id())
                || payment.getProviderReferenceId() == null
                || !remote.paymentIds().contains(payment.getProviderReferenceId())
                || !checkout.getCollectorId().equals(properties.getCollectorId())
                || !checkout.getCurrency().equals(properties.getCurrency())
                || checkout.isLiveMode() != properties.isLiveMode()
                || !checkout.getCurrency().equals(remote.currency())
                || checkout.isLiveMode() != remote.liveMode()
                || remote.amount().signum() <= 0
                || (remote.paymentIds().size() == 1
                        && remote.amount().compareTo(payment.getAmount()) > 0))
            throw new BusinessConflictException(
                    "El caso de contracargo no corresponde al pago, vendedor, moneda o entorno");
        if (record.getProviderUpdatedAt() != null
                && remote.updatedAt().isBefore(record.getProviderUpdatedAt())) return;
        if (record.getProviderUpdatedAt() != null
                && remote.updatedAt().equals(record.getProviderUpdatedAt())
                && (!Objects.equals(record.getCoverageApplied(), remote.coverageApplied())
                        || record.getAmount().compareTo(remote.amount()) != 0))
            throw new ExternalServiceException(
                    "La version del contracargo contiene datos incompatibles");
        ChargebackResolution next =
                remote.coverageApplied() == null
                        ? ChargebackResolution.OPEN
                        : remote.coverageApplied()
                                ? ChargebackResolution.WON
                                : ChargebackResolution.LOST;
        if (record.getResolution() != ChargebackResolution.OPEN
                && next == ChargebackResolution.OPEN)
            throw new ExternalServiceException(
                    "El caso resuelto requiere una verificacion posterior");
        String before =
                record.getResolution().name()
                        + ":"
                        + record.getDocumentationStatus()
                        + ":"
                        + record.getAmount();
        record.setResolution(next);
        record.setCoverageApplied(remote.coverageApplied());
        record.setDocumentationStatus(remote.documentationStatus());
        record.setDocumentationDeadline(remote.documentationDeadline());
        record.setProviderUpdatedAt(remote.updatedAt());
        record.setAmount(remote.amount());
        record.setCurrency(remote.currency());
        record.setProviderReason(remote.reason());
        record.setGrouped(remote.paymentIds().size() > 1);
        record.setVerifiedAt(Instant.now());
        String after = next.name() + ":" + remote.documentationStatus() + ":" + remote.amount();
        if (!before.equals(after))
            audit(
                    record,
                    null,
                    "CHARGEBACK_CASE_VERIFIED",
                    before,
                    after,
                    "Resultado y documentacion consultados en Mercado Pago");
    }

    @Transactional
    public ChargebackResponseDTO note(String email, Long id, String reason) {
        UserModel actor = staff(email, true);
        if (reason == null || reason.isBlank() || reason.length() > 500)
            throw new InvalidRequestException("Debe indicar una nota de hasta 500 caracteres");
        ChargebackCase initial = getCase(id);
        workflow.lockOrder(initial.getPayment().getCheckout().getOrder().getIdOrder());
        ChargebackCase record = getCase(id);
        cases.refresh(record);
        record.setLastAdminNote(reason.trim());
        record.setLastActorUserId(actor.getId());
        record.setLastNoteAt(Instant.now());
        audit(
                record,
                actor.getId(),
                "CHARGEBACK_ADMIN_NOTE",
                null,
                record.getProviderCaseId(),
                reason.trim());
        return mapper.map(record, ChargebackResponseDTO.class);
    }

    private void audit(
            ChargebackCase record,
            Long actor,
            String action,
            String before,
            String after,
            String reason) {
        OrderChangeAudit audit = new OrderChangeAudit();
        audit.setOrderId(record.getPayment().getCheckout().getOrder().getIdOrder());
        audit.setActorUserId(actor);
        audit.setAction(action);
        audit.setPreviousValue(before);
        audit.setNewValue(after);
        audit.setReason("Caso " + record.getProviderCaseId() + ": " + reason);
        if (audit.getReason().length() > 500) audit.setReason(audit.getReason().substring(0, 500));
        audit.setCreatedAt(LocalDateTime.now());
        workflow.saveAudit(audit);
    }

    public record SyncDetails(Long id, String providerCaseId, String providerOrderId) {}
}

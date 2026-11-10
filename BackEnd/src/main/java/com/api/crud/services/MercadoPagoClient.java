package com.api.crud.services;

import com.api.crud.config.MercadoPagoProperties;
import com.api.crud.exceptions.BusinessConflictException;
import com.api.crud.exceptions.ExternalServiceException;
import com.api.crud.models.entity.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

// hace las solicitudes a la API del proveedor.
@Component
public class MercadoPagoClient {

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoClient.class);

    private static final List<String> DIAGNOSTIC_FIELDS =
            List.of(
                    "expiration_time",
                    "total_amount",
                    "processing_mode",
                    "capture_mode",
                    "external_reference",
                    "not_allowed_types",
                    "not_allowed_ids",
                    "payer",
                    "items");

    private final MercadoPagoProperties properties;

    private final ObjectMapper mapper;

    private volatile List<String> excludedTypes;

    private volatile Instant methodsExpireAt = Instant.EPOCH;

    private final HttpClient http =
            HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .build();

    public MercadoPagoClient(MercadoPagoProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
    }

    public CheckoutOrder createOrder(PaymentCheckout checkout) {
        properties.requireEnabled();
        JsonNode payload;
        try {
            if (checkout.getRequestBody() == null) throw unavailable();
            payload = mapper.readTree(checkout.getRequestBody());
        } catch (IOException exception) {
            throw unavailable();
        }
        JsonNode result = request("POST", "/v1/orders", payload, checkout.getReference());
        String url = text(result, "checkout_url");
        if (url == null || !trustedCheckoutUrl(url)) throw unavailable();
        String id = requiredText(result, "id");
        requireOrderId(id);
        if (!"online".equals(requiredText(result, "type"))
                || !"manual".equals(requiredText(result, "processing_mode"))
                || id.startsWith("ORDTST") == checkout.isLiveMode()
                || !checkout.getReference().equals(requiredText(result, "external_reference"))
                || !checkout.getCollectorId().equals(requiredText(result, "user_id"))
                || !checkout.getCurrency().equals(requiredText(result, "currency"))
                || checkout.getAmount().compareTo(decimal(result, "total_amount")) != 0) {
            throw unavailable();
        }
        return new CheckoutOrder(id, url);
    }

    public String prepareOrderRequest(PaymentCheckout checkout) {
        properties.requireEnabled();
        return orderBody(checkout).toString();
    }

    public RefundReceipt refundPayment(PaymentRefund refund) {
        properties.requireEnabled();
        requireOrderId(refund.getProviderOrderId());
        String paymentId = refund.getPayment().getProviderPaymentId();
        if (!paymentId.matches("PAY[A-Z0-9]{1,61}") || refund.getAmount().signum() <= 0)
            throw unavailable();
        ObjectNode body = mapper.createObjectNode();
        body.putArray("transactions")
                .addObject()
                .put("id", paymentId)
                .put("amount", refund.getAmount().toPlainString());
        JsonNode response =
                request(
                        "POST",
                        "/v1/orders/" + refund.getProviderOrderId() + "/refund",
                        body,
                        refund.getId());
        if (!refund.getProviderOrderId().equals(requiredText(response, "id"))) throw unavailable();
        for (JsonNode result : response.path("transactions").path("refunds")) {
            if (paymentId.equals(text(result, "transaction_id"))
                    && decimal(result, "amount").compareTo(refund.getAmount()) == 0) {
                String id = requiredText(result, "id");
                if (!id.matches("REF[A-Z0-9]{1,61}")) throw unavailable();
                // Acceptance is not confirmation: GET Orders/webhook verifies the final state.
                return new RefundReceipt(id);
            }
        }
        throw unavailable();
    }

    ObjectNode orderBody(PaymentCheckout checkout) {
        ObjectNode body = mapper.createObjectNode();
        body.put("type", "online");
        body.put("processing_mode", "manual");
        body.put("capture_mode", "automatic");
        body.put("total_amount", checkout.getAmount().toPlainString());
        // Fixed across retries: the same idempotency key must have the same request body.
        long remainingSeconds =
                Duration.between(checkout.getCreatedAt(), checkout.getExpiresAt()).getSeconds();
        if (remainingSeconds <= 0) {
            throw new BusinessConflictException(
                    "La reserva no tiene tiempo disponible para iniciar el pago");
        }
        // Avoid fractional seconds: provider validation rejects the microsecond precision
        // returned by Duration.toString() for timestamps persisted as DATETIME(6).
        body.put("expiration_time", Duration.ofSeconds(remainingSeconds).toString());
        ArrayNode items = body.putArray("items");
        for (OrderItem item : checkout.getOrder().getItemsOrder()) {
            items.addObject()
                    .put("external_code", item.getIdOrderItem().toString())
                    .put("title", item.getProductName())
                    .put("quantity", item.getAmountOrderItem())
                    .put("unit_price", item.getPriceOrderItem().toPlainString());
        }
        BigDecimal shipping = checkout.getOrder().getShippingCost();
        if (shipping != null && shipping.signum() > 0) {
            items.addObject()
                    .put("external_code", "shipping")
                    .put("title", "Envio")
                    .put("quantity", 1)
                    .put("unit_price", shipping.toPlainString());
        }
        body.put("external_reference", checkout.getReference());
        ObjectNode config = body.putObject("config");
        ArrayNode excluded = config.putObject("payment_method").putArray("not_allowed_types");
        for (String type : excludedPaymentTypes()) excluded.add(type);
        ObjectNode online = config.putObject("online");
        online.putObject("retries").put("allowed", false);
        if (!properties.getReturnUrl().isBlank()) {
            online.put("success_url", properties.getReturnUrl() + "?result=success");
            online.put("pending_url", properties.getReturnUrl() + "?result=pending");
            online.put("failure_url", properties.getReturnUrl() + "?result=failure");
            online.put("auto_return", "approved");
        }
        return body;
    }

    private synchronized List<String> excludedPaymentTypes() {
        if (excludedTypes != null && methodsExpireAt.isAfter(Instant.now())) return excludedTypes;
        JsonNode methods = request("GET", "/v1/payment_methods", null, null);
        if (!methods.isArray()) throw unavailable();
        Set<String> allowed = Set.of("credit_card", "debit_card", "account_money");
        Set<String> excluded = new TreeSet<>(List.of("ticket", "atm", "bank_transfer"));
        for (JsonNode method : methods) {
            String type = requiredText(method, "payment_type_id");
            if ("digital_wallet".equals(type)) type = "wallet";
            if (!allowed.contains(type)) excluded.add(type);
        }
        excludedTypes = List.copyOf(excluded);
        methodsExpireAt = Instant.now().plusSeconds(300);
        return excludedTypes;
    }

    public VerifiedOrder getOrder(String id) {
        properties.requireEnabled();
        requireOrderId(id);
        JsonNode result = request("GET", "/v1/orders/" + id, null, null);
        if (!id.equals(requiredText(result, "id"))) throw unavailable();
        if (!"online".equals(requiredText(result, "type"))
                || !"manual".equals(requiredText(result, "processing_mode"))) throw unavailable();
        try {
            String reference = requiredText(result, "external_reference");
            String collector = requiredText(result, "user_id");
            String currency = requiredText(result, "currency");
            BigDecimal amount = decimal(result, "total_amount");
            Instant updated = instant(result, "last_updated_date");
            if (updated == null) throw unavailable();
            // Test orders have the ORDTST prefix; the GET response need not include live_mode.
            boolean live = !id.startsWith("ORDTST");
            if (result.has("live_mode")
                    && (!result.path("live_mode").isBoolean()
                            || result.path("live_mode").asBoolean() != live)) throw unavailable();
            List<VerifiedPayment> payments = new ArrayList<>();
            JsonNode transactions = result.path("transactions");
            for (String field : List.of("payments", "refunds", "chargebacks")) {
                JsonNode entries = transactions.path(field);
                if (!entries.isMissingNode() && !entries.isArray()) throw unavailable();
            }
            JsonNode attempts = transactions.path("payments");
            if (attempts.isEmpty()
                    && !"created".equals(text(result, "status"))
                    && !"canceled".equals(text(result, "status"))) throw unavailable();
            Set<String> seen = new HashSet<>();
            for (JsonNode attempt : attempts) {
                String paymentId = requiredText(attempt, "id");
                if (!paymentId.matches("PAY[A-Z0-9]{1,61}") || !seen.add(paymentId))
                    throw unavailable();
                BigDecimal refunded = BigDecimal.ZERO.setScale(2);
                Set<String> refundIds = new HashSet<>();
                for (JsonNode refund : transactions.path("refunds")) {
                    if (!refundIds.add(requiredText(refund, "id"))) throw unavailable();
                    if (paymentId.equals(text(refund, "transaction_id"))
                            && "processed".equals(text(refund, "status"))) {
                        refunded = refunded.add(decimal(refund, "amount"));
                    }
                }
                if (("partially_refunded".equals(text(attempt, "status_detail"))
                                || "refunded".equals(text(attempt, "status")))
                        && refunded.signum() == 0) throw unavailable();
                String status = normalizedStatus(attempt);
                BigDecimal paymentAmount = decimal(attempt, "amount");
                if (refunded.signum() > 0 && refunded.compareTo(paymentAmount) == 0)
                    status = "refunded";
                for (JsonNode chargeback : transactions.path("chargebacks")) {
                    if (paymentId.equals(text(chargeback, "transaction_id")))
                        status = "charged_back";
                }
                // No precise approval time is guaranteed in Orders. Use its canonical update
                // time conservatively; a first confirmation after expiry requires review.
                Instant approved = instant(attempt, "date_approved");
                if (approved == null && "approved".equals(status)) approved = updated;
                payments.add(
                        new VerifiedPayment(
                                paymentId,
                                reference,
                                collector,
                                currency,
                                paymentAmount,
                                refunded,
                                status,
                                text(attempt.path("payment_method"), "type"),
                                text(attempt.path("payment_method"), "id"),
                                approved,
                                updated,
                                live));
            }
            return new VerifiedOrder(
                    id, reference, collector, currency, amount, live, List.copyOf(payments));
        } catch (IllegalArgumentException | DateTimeException exception) {
            throw unavailable();
        }
    }

    private String normalizedStatus(JsonNode payment) {
        String status = requiredText(payment, "status");
        return switch (status) {
            case "processed" ->
                    "accredited".equals(text(payment, "status_detail"))
                                    || "partially_refunded".equals(text(payment, "status_detail"))
                            ? "approved"
                            : "unknown";
            case "created" -> "pending";
            case "processing", "in_review" -> "in_process";
            case "action_required" ->
                    "waiting_capture".equals(text(payment, "status_detail"))
                            ? "authorized"
                            : "pending";
            case "failed" -> "rejected";
            case "canceled", "expired" -> "cancelled";
            case "refunded" -> "refunded";
            case "charged_back" -> "charged_back";
            default -> "unknown";
        };
    }

    private void requireOrderId(String id) {
        if (id == null || !id.matches("ORD[A-Z0-9]{1,61}")) throw unavailable();
    }

    private BigDecimal decimal(JsonNode node, String field) {
        String value = requiredText(node, field);
        BigDecimal number;
        try {
            number = new BigDecimal(value);
        } catch (NumberFormatException exception) {
            throw unavailable();
        }
        if (number.signum() < 0
                || number.stripTrailingZeros().scale() > 2
                || number.precision() - number.scale() > 14) throw unavailable();
        return number.setScale(2);
    }

    private Instant instant(JsonNode node, String field) {
        String value = text(node, field);
        return value == null
                ? null
                : OffsetDateTime.parse(value).toInstant().truncatedTo(ChronoUnit.MICROS);
    }

    protected JsonNode request(String method, String path, JsonNode body, String key) {
        try {
            HttpRequest.Builder builder =
                    HttpRequest.newBuilder(URI.create("https://api.mercadopago.com" + path))
                            .timeout(Duration.ofSeconds(15))
                            .header("Authorization", "Bearer " + properties.getAccessToken());
            if (body == null) builder.GET();
            else {
                builder.header("Content-Type", "application/json");
                builder.header("X-Idempotency-Key", key);
                builder.POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
            }
            HttpResponse<String> response = send(builder.build());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                logProviderFailure(method, path, response);
                throw unavailable();
            }
            JsonNode parsed = mapper.readTree(response.body());
            if (parsed == null || (!parsed.isObject() && !parsed.isArray())) throw unavailable();
            return parsed;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("Mercado Pago: solicitud interrumpida, metodo={}, ruta={}", method, path);
            throw unavailable();
        } catch (IOException exception) {
            log.warn(
                    "Mercado Pago: fallo de comunicacion o respuesta, metodo={}, ruta={}, tipo={}",
                    method,
                    path,
                    exception.getClass().getSimpleName());
            throw unavailable();
        }
    }

    protected HttpResponse<String> send(HttpRequest request)
            throws IOException, InterruptedException {
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private void logProviderFailure(String method, String path, HttpResponse<String> response) {
        Set<String> codes = new TreeSet<>();
        Set<String> fields = new TreeSet<>();
        try {
            JsonNode error = mapper.readTree(response.body());
            if (error != null && error.isObject()) {
                addDiagnosticCode(codes, error.path("error"));
                addDiagnosticCode(codes, error.path("code"));
                for (String collection : List.of("cause", "errors")) {
                    for (JsonNode cause : error.path(collection)) {
                        addDiagnosticCode(codes, cause.path("code"));
                        addDiagnosticCode(codes, cause.path("error"));
                    }
                }
                // Only known field names are retained, never provider messages or payload values.
                String description = error.toString();
                for (String field : DIAGNOSTIC_FIELDS) {
                    if (description.contains(field)) fields.add(field);
                }
            }
        } catch (IOException ignored) {
            // An HTML/plain-text error must not expose its response body in the log.
        }
        String requestId =
                response.headers()
                        .firstValue("x-request-id")
                        .filter(value -> value.matches("[A-Za-z0-9-]{1,128}"))
                        .orElse("no_disponible");
        log.warn(
                "Mercado Pago rechazo la solicitud: metodo={}, ruta={}, HTTP={}, codigos={}, campos={}, requestId={}",
                method,
                path,
                response.statusCode(),
                codes,
                fields,
                requestId);
    }

    private void addDiagnosticCode(Set<String> codes, JsonNode value) {
        if (codes.size() < 10 && value.isValueNode() && value.asText().matches("[a-z0-9_]{1,64}"))
            codes.add(value.asText());
    }

    private boolean trustedCheckoutUrl(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            return "https".equalsIgnoreCase(uri.getScheme())
                    && uri.getUserInfo() == null
                    && host != null
                    && (host.equals("mercadopago.com")
                            || host.endsWith(".mercadopago.com")
                            || host.equals("mercadopago.com.ar")
                            || host.endsWith(".mercadopago.com.ar"));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private String requiredText(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null || value.isBlank()) throw unavailable();
        return value;
    }

    private ExternalServiceException unavailable() {
        return new ExternalServiceException("No se pudo completar la operacion con Mercado Pago");
    }

    public record CheckoutOrder(String id, String checkoutUrl) {}

    public record RefundReceipt(String id) {}

    public record VerifiedOrder(
            String id,
            String reference,
            String collectorId,
            String currency,
            BigDecimal amount,
            boolean liveMode,
            List<VerifiedPayment> payments) {}

    public record VerifiedPayment(
            String id,
            String reference,
            String collectorId,
            String currency,
            BigDecimal amount,
            BigDecimal refundedAmount,
            String status,
            String paymentType,
            String paymentMethod,
            Instant approvedAt,
            Instant updatedAt,
            boolean liveMode) {}
}

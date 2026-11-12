package com.api.crud;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import com.api.crud.config.MercadoPagoProperties;
import com.api.crud.exceptions.BusinessConflictException;
import com.api.crud.exceptions.ExternalServiceException;
import com.api.crud.exceptions.RefundRejectedException;
import com.api.crud.models.entity.*;
import com.api.crud.services.MercadoPagoClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

class MercadoPagoClientTests {

    @Test
    void onlyRecognizedRefundHttpErrorsAreClassifiedForSubsequentVerification() {
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.headers()).thenReturn(HttpHeaders.of(Map.of(), (name, value) -> true));
        MercadoPagoClient client =
                new MercadoPagoClient(properties(), new ObjectMapper()) {
                    @Override
                    protected HttpResponse<String> send(HttpRequest request) {
                        return response;
                    }
                };
        Payment payment = new Payment();
        payment.setProviderPaymentId("PAY123");
        PaymentRefund refund = new PaymentRefund();
        refund.setId("a9d184ec-b676-4c01-95e5-f8c657690774");
        refund.setPayment(payment);
        refund.setProviderOrderId("ORDTST123");
        refund.setAmount(BigDecimal.TEN);
        for (var rejection :
                Map.of(
                                400,
                                "refund_amount_exceeds",
                                404,
                                "transaction_not_found",
                                409,
                                "cannot_refund_order")
                        .entrySet()) {
            when(response.statusCode()).thenReturn(rejection.getKey());
            when(response.body()).thenReturn("{\"error\":\"" + rejection.getValue() + "\"}");
            assertEquals(
                    rejection.getValue(),
                    assertThrows(RefundRejectedException.class, () -> client.refundPayment(refund))
                            .getProviderCode());
        }
        for (String code :
                List.of(
                        "order_refund_already_in_process",
                        "order_already_refunded",
                        "idempotency_key_already_used",
                        "unknown_error")) {
            when(response.statusCode()).thenReturn(409);
            when(response.body()).thenReturn("{\"error\":\"" + code + "\"}");
            assertThrowsExactly(ExternalServiceException.class, () -> client.refundPayment(refund));
        }
        for (int status : List.of(401, 403, 429, 500)) {
            when(response.statusCode()).thenReturn(status);
            when(response.body()).thenReturn("{\"error\":\"cannot_refund_order\"}");
            assertThrowsExactly(ExternalServiceException.class, () -> client.refundPayment(refund));
        }
        when(response.statusCode()).thenReturn(400);
        when(response.body()).thenReturn("not-json");
        assertThrowsExactly(ExternalServiceException.class, () -> client.refundPayment(refund));
    }

    @Test
    void refundTargetsOnlyTheSelectedTransactionAndReusesItsIdempotencyKey() throws Exception {
        FakeClient client = new FakeClient(properties());
        client.response =
                new ObjectMapper()
                        .readTree(
                                """
{"id":"ORDTST123","transactions":{"refunds":[
  {"id":"REF123","transaction_id":"PAY123","amount":"80.00","status":"processing"}
]}}
""");
        Payment payment = new Payment();
        payment.setProviderPaymentId("PAY123");
        PaymentRefund refund = new PaymentRefund();
        refund.setId("a9d184ec-b676-4c01-95e5-f8c657690774");
        refund.setProviderOrderId("ORDTST123");
        refund.setPayment(payment);
        refund.setAmount(new BigDecimal("80.00"));
        assertEquals("REF123", client.refundPayment(refund).id());
        assertEquals("/v1/orders/ORDTST123/refund", client.path);
        assertEquals(refund.getId(), client.key);
        assertEquals(1, client.payload.path("transactions").size());
        assertEquals("PAY123", client.payload.path("transactions").get(0).path("id").asText());
        assertEquals("80.00", client.payload.path("transactions").get(0).path("amount").asText());
        JsonNode firstBody = client.payload.deepCopy();
        client.refundPayment(refund);
        assertEquals(firstBody, client.payload);
        assertEquals(refund.getId(), client.key);
        refund.setCurrentAttemptId("7126952c-0de6-4d25-a07d-717c496cd30c");
        client.refundPayment(refund);
        assertEquals(refund.getCurrentAttemptId(), client.key);
        assertEquals(firstBody, client.payload);
        refund.setProviderOrderId("ORDTST999");
        assertThrows(ExternalServiceException.class, () -> client.refundPayment(refund));
    }

    @Test
    void providerErrorsLogSafeDiagnosticsWithoutCredentialsOrBuyerData() {
        MercadoPagoProperties properties = properties();
        properties.setAccessToken("private-test-token");
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(400);
        when(response.headers())
                .thenReturn(
                        HttpHeaders.of(
                                Map.of("x-request-id", List.of("request-123")),
                                (name, value) -> true));
        when(response.body())
                .thenReturn(
                        """
{"error":"bad_request","message":"Invalid expiration_time for buyer@example.com",
 "cause":[{"code":"invalid_expiration_time","message":"private-test-token"}]}
""");
        MercadoPagoClient client =
                new MercadoPagoClient(properties, new ObjectMapper()) {
                    @Override
                    protected HttpResponse<String> send(HttpRequest request) {
                        return response;
                    }
                };
        Logger logger = (Logger) LoggerFactory.getLogger(MercadoPagoClient.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            assertThrows(ExternalServiceException.class, () -> client.getOrder("ORDTST123"));
            String diagnostic = appender.list.get(0).getFormattedMessage();
            assertTrue(diagnostic.contains("HTTP=400"));
            assertTrue(diagnostic.contains("invalid_expiration_time"));
            assertTrue(diagnostic.contains("request-123"));
            assertFalse(diagnostic.contains("private-test-token"));
            assertFalse(diagnostic.contains("buyer@example.com"));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void networkFailuresRemainSanitizedAndDoNotExposeExceptionMessages() {
        MercadoPagoClient client =
                new MercadoPagoClient(properties(), new ObjectMapper()) {
                    @Override
                    protected HttpResponse<String> send(HttpRequest request) throws IOException {
                        throw new IOException("private-test-token");
                    }
                };
        ExternalServiceException exception =
                assertThrows(ExternalServiceException.class, () -> client.getOrder("ORDTST123"));
        assertFalse(exception.getMessage().contains("private-test-token"));
        assertNull(exception.getCause());
    }

    private static class FakeClient extends MercadoPagoClient {
        JsonNode payload;
        JsonNode response;
        String path;
        String key;

        FakeClient(MercadoPagoProperties properties) {
            super(properties, new ObjectMapper());
        }

        @Override
        protected JsonNode request(String method, String path, JsonNode body, String key) {
            if (path.equals("/v1/payment_methods")) {
                return new ObjectMapper()
                        .createArrayNode()
                        .add(
                                new ObjectMapper()
                                        .createObjectNode()
                                        .put("payment_type_id", "consumer_credits"));
            }
            this.path = path;
            this.payload = body;
            this.key = key;
            return response;
        }
    }

    private MercadoPagoProperties properties() {
        MercadoPagoProperties properties = new MercadoPagoProperties();
        properties.setEnabled(true);
        properties.setCollectorId("99");
        properties.setReturnUrl("https://shop.example/payment-result");
        return properties;
    }

    private PaymentCheckout checkout() {
        Order order = new Order();
        order.setShippingCost(new BigDecimal("50.00"));
        OrderItem item = new OrderItem(order, new ProductPhysical(), 2, new BigDecimal("100.00"));
        item.setIdOrderItem(11L);
        item.setProductName("Nombre comprado");
        order.setItemsOrder(List.of(item));
        PaymentCheckout checkout = new PaymentCheckout();
        checkout.setReference("reference-1");
        checkout.setOrder(order);
        checkout.setAmount(new BigDecimal("250.00"));
        checkout.setCollectorId("99");
        checkout.setCurrency("ARS");
        checkout.setCreatedAt(Instant.parse("2026-11-09T12:00:00Z"));
        checkout.setExpiresAt(checkout.getCreatedAt().plusSeconds(1800));
        return checkout;
    }

    @Test
    void reservationDurationUsesWholeSecondsWithoutExtendingItsDeadline() throws Exception {
        FakeClient client = new FakeClient(properties());
        PaymentCheckout checkout = checkout();
        checkout.setCreatedAt(Instant.parse("2026-11-09T12:00:00.123456Z"));
        checkout.setExpiresAt(Instant.parse("2026-11-09T12:29:59.987654Z"));
        JsonNode body = new ObjectMapper().readTree(client.prepareOrderRequest(checkout));
        String expiration = body.path("expiration_time").asText();
        assertEquals("PT29M59S", expiration);
        assertTrue(
                checkout.getCreatedAt()
                        .plus(java.time.Duration.parse(expiration))
                        .isBefore(checkout.getExpiresAt()));
        checkout.setExpiresAt(checkout.getCreatedAt().plusMillis(500));
        assertThrows(BusinessConflictException.class, () -> client.prepareOrderRequest(checkout));
    }

    @Test
    void orderUsesStoredItemsShippingAndStableIdempotentBody() throws Exception {
        MercadoPagoProperties properties = properties();
        FakeClient client = new FakeClient(properties);
        client.response =
                new ObjectMapper()
                        .readTree(
                                """
{"id":"ORDTST123","type":"online","processing_mode":"manual","status":"processed",
 "checkout_url":"https://www.mercadopago.com.ar/checkout/v1/redirect?order_id=ORDTST123",
 "external_reference":"reference-1","user_id":"99","currency":"ARS","total_amount":"250.00"}
""");
        PaymentCheckout checkout = checkout();
        checkout.setRequestBody(client.prepareOrderRequest(checkout));
        client.createOrder(checkout);
        JsonNode original = client.payload.deepCopy();
        assertEquals("/v1/orders", client.path);
        assertEquals("reference-1", client.key);
        assertEquals("manual", client.payload.path("processing_mode").asText());
        assertEquals("250.00", client.payload.path("total_amount").asText());
        assertEquals("Nombre comprado", client.payload.path("items").get(0).path("title").asText());
        assertEquals("50.00", client.payload.path("items").get(1).path("unit_price").asText());
        assertEquals("PT30M", client.payload.path("expiration_time").asText());
        assertEquals("automatic", client.payload.path("capture_mode").asText());
        assertTrue(
                client.payload
                        .path("config")
                        .path("online")
                        .path("retries")
                        .path("allowed")
                        .asBoolean());
        assertTrue(
                client.payload
                        .path("config")
                        .path("payment_method")
                        .path("not_allowed_types")
                        .toString()
                        .contains("consumer_credits"));
        assertEquals(
                "https://shop.example/payment-result?result=success",
                client.payload.path("config").path("online").path("success_url").asText());
        assertFalse(client.payload.has("access_token"));
        assertFalse(client.payload.has("notification_url"));
        properties.setReturnUrl("https://changed.example");
        client.createOrder(checkout);
        assertEquals(original, client.payload);
        checkout.setCollectorId("100");
        assertThrows(ExternalServiceException.class, () -> client.createOrder(checkout));
    }

    private JsonNode remoteOrder() throws Exception {
        return new ObjectMapper()
                .readTree(
                        """
{"id":"ORDTST123","type":"online","processing_mode":"manual","status":"processed",
 "external_reference":"reference-1","user_id":"99","currency":"ARS",
 "total_amount":"150.00","last_updated_date":"2026-11-09T12:00:00Z",
 "transactions":{"payments":[{"id":"PAY123","amount":"150.00",
 "status":"processed","status_detail":"accredited",
 "payment_method":{"type":"debit_card","id":"debvisa"}}]}}
""");
    }

    @Test
    void canonicalOrdersNormalizePaymentsAndRejectInvalidResources() throws Exception {
        FakeClient client = new FakeClient(properties());
        client.response = remoteOrder();
        var order = client.getOrder("ORDTST123");
        assertEquals("/v1/orders/ORDTST123", client.path);
        var payment = order.payments().get(0);
        assertEquals(new BigDecimal("150.00"), payment.amount());
        assertEquals("PAY123", payment.id());
        assertEquals("approved", payment.status());
        assertFalse(payment.liveMode());
        assertEquals(Instant.parse("2026-11-09T12:00:00Z"), payment.approvedAt());
        assertThrows(ExternalServiceException.class, () -> client.getOrder("ORDTST124"));
        assertThrows(ExternalServiceException.class, () -> client.getOrder("../users"));
        ((ObjectNode) client.response).put("processing_mode", "automatic");
        assertThrows(ExternalServiceException.class, () -> client.getOrder("ORDTST123"));
    }

    @Test
    void refundsAreSummedOnlyForTheirTransactionAndChargebacksBlockFulfillment() throws Exception {
        FakeClient client = new FakeClient(properties());
        client.response = remoteOrder();
        var transactions = (ObjectNode) client.response.path("transactions");
        transactions
                .putArray("refunds")
                .addObject()
                .put("id", "REF1")
                .put("transaction_id", "PAY123")
                .put("amount", "50.00")
                .put("status", "processed");
        assertEquals(
                new BigDecimal("50.00"),
                client.getOrder("ORDTST123").payments().get(0).refundedAmount());
        assertEquals(
                Set.of("REF1"),
                client.getOrder("ORDTST123").payments().get(0).confirmedRefundIds());
        transactions
                .withArray("refunds")
                .addObject()
                .put("id", "REF2")
                .put("transaction_id", "PAY123")
                .put("amount", "100.00")
                .put("status", "processed");
        assertEquals("refunded", client.getOrder("ORDTST123").payments().get(0).status());
        transactions
                .putArray("chargebacks")
                .addObject()
                .put("transaction_id", "PAY123")
                .put("status", "in_process");
        assertEquals("charged_back", client.getOrder("ORDTST123").payments().get(0).status());
    }

    @Test
    void incompleteRefundInformationNeverApprovesAPartiallyRefundedPayment() throws Exception {
        FakeClient client = new FakeClient(properties());
        client.response = remoteOrder();
        ((ObjectNode) client.response.path("transactions").path("payments").get(0))
                .put("status_detail", "partially_refunded");
        assertThrows(ExternalServiceException.class, () -> client.getOrder("ORDTST123"));
    }

    @Test
    void unresolvedRefundsAreReportedWithoutCountingThemAsReturnedMoney() throws Exception {
        FakeClient client = new FakeClient(properties());
        client.response = remoteOrder();
        ((ObjectNode) client.response.path("transactions"))
                .putArray("refunds")
                .addObject()
                .put("id", "REF1")
                .put("transaction_id", "PAY123")
                .put("amount", "150.00")
                .put("status", "processing");
        var result = client.getOrder("ORDTST123");
        assertTrue(result.unsettledRefundPaymentIds().contains("PAY123"));
        assertEquals(0, result.payments().get(0).refundedAmount().signum());
        assertEquals("approved", result.payments().get(0).status());
    }

    @Test
    void canonicalOrderExposesCaseAndNumericPaymentReferencesWithoutTrustingWebhookBody()
            throws Exception {
        FakeClient client = new FakeClient(properties());
        client.response = remoteOrder();
        ObjectNode transaction =
                (ObjectNode) client.response.path("transactions").path("payments").get(0);
        transaction.put("reference_id", "123456");
        transaction.put("status", "charged_back");
        transaction.put("status_detail", "reimbursed");
        ((ObjectNode) client.response.path("transactions"))
                .putArray("chargebacks")
                .addObject()
                .put("transaction_id", "PAY123")
                .put("case_id", "9988");
        var snapshot = client.getOrder("ORDTST123").payments().get(0);
        assertEquals(Set.of("9988"), snapshot.chargebackCaseIds());
        assertEquals("123456", snapshot.numericPaymentId());
        assertEquals("reimbursed", snapshot.statusDetail());
        assertEquals("charged_back", snapshot.status());
        transaction.put("status", "processed").put("status_detail", "partially_refunded");
        ((ObjectNode) client.response.path("transactions"))
                .putArray("refunds")
                .addObject()
                .put("id", "REF123")
                .put("transaction_id", "PAY123")
                .put("amount", "50.00")
                .put("status", "processed");
        var partiallyRefunded = client.getOrder("ORDTST123").payments().get(0);
        assertEquals("approved", partiallyRefunded.status());
        assertEquals(Set.of("9988"), partiallyRefunded.chargebackCaseIds());
    }

    @Test
    void chargebackConsultationPreservesUnresolvedCoverageAndRejectsMixedModeOrMalformedResponses()
            throws Exception {
        FakeClient client = new FakeClient(properties());
        client.response =
                new ObjectMapper()
                        .readTree(
                                """
{"id":"9988","payments":[123456],"currency":"ARS","amount":"50.00","reason":"unauthorized",
 "coverage_applied":null,"documentation_status":"pending","live_mode":false,
 "date_last_updated":"2026-01-01T00:00:00Z"}
""");
        var result = client.getChargeback("9988");
        assertNull(result.coverageApplied());
        assertEquals("/v1/chargebacks/9988", client.path);
        assertEquals(Set.of("123456"), result.paymentIds());
        ((ObjectNode) client.response).put("coverage_applied", false);
        assertEquals(Boolean.FALSE, client.getChargeback("9988").coverageApplied());
        ((ObjectNode) client.response).put("live_mode", true);
        assertThrows(ExternalServiceException.class, () -> client.getChargeback("9988"));
        assertThrows(ExternalServiceException.class, () -> client.getChargeback("../orders"));
    }

    @Test
    void chargebackHttpRequestIncludesSellerIdentityHeader() throws Exception {
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body())
                .thenReturn(
                        """
{"id":"9988","payments":[123456],"currency":"ARS","amount":"50.00","reason":"unauthorized",
 "coverage_applied":true,"documentation_status":"valid","live_mode":false,
 "date_last_updated":"2026-01-01T00:00:00Z"}
""");
        MercadoPagoClient client =
                new MercadoPagoClient(properties(), new ObjectMapper()) {
                    @Override
                    protected HttpResponse<String> send(HttpRequest request) {
                        assertEquals(
                                "99", request.headers().firstValue("X-Caller-Id").orElseThrow());
                        assertEquals("GET", request.method());
                        return response;
                    }
                };
        assertEquals(Boolean.TRUE, client.getChargeback("9988").coverageApplied());
    }

    @Test
    void legacyCheckoutRetainsDisabledRetriesAndItsExactIdempotentRequest() throws Exception {
        FakeClient client = new FakeClient(properties());
        client.response =
                new ObjectMapper()
                        .readTree(
                                """
{"id":"ORDTST123","type":"online","processing_mode":"manual",
 "checkout_url":"https://www.mercadopago.com.ar/checkout/v1/redirect?order_id=ORDTST123",
 "external_reference":"reference-1","user_id":"99","currency":"ARS","total_amount":"250.00"}
""");
        PaymentCheckout legacyCheckout = checkout();
        ObjectNode legacyBody =
                (ObjectNode)
                        new ObjectMapper().readTree(client.prepareOrderRequest(legacyCheckout));
        ((ObjectNode) legacyBody.path("config").path("online").path("retries"))
                .put("allowed", false);
        String persistedRequest = legacyBody.toString();
        legacyCheckout.setRequestBody(persistedRequest);
        client.createOrder(legacyCheckout);
        client.createOrder(legacyCheckout);
        assertEquals(legacyBody, client.payload);
        assertEquals(persistedRequest, legacyCheckout.getRequestBody());
        assertEquals(legacyCheckout.getReference(), client.key);
    }
}

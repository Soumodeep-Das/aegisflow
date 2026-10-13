package com.aegisflow.simulator.payment.persistence.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.aegisflow.simulator.payment.CreatePaymentCommand;
import com.aegisflow.simulator.payment.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class CanonicalJsonTest {

    private final CanonicalJson canonicalJson = new CanonicalJson(new ObjectMapper());
    private final PaymentCommandValidator validator = new PaymentCommandValidator();
    private final PaymentJsonFactory jsonFactory = new PaymentJsonFactory(canonicalJson);

    @Test
    void recursivelySortsObjectKeysButPreservesArrayOrder() {
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("z", Map.of("b", 2, "a", 1));
        first.put("a", List.of("second", "first"));

        Map<String, Object> second = new LinkedHashMap<>();
        second.put("a", List.of("second", "first"));
        second.put("z", Map.of("a", 1, "b", 2));

        String firstJson = canonicalJson.write(first);
        String secondJson = canonicalJson.write(second);

        assertEquals("{\"a\":[\"second\",\"first\"],\"z\":{\"a\":1,\"b\":2}}", firstJson);
        assertEquals(firstJson, secondJson);
        assertEquals(canonicalJson.sha256(firstJson), canonicalJson.sha256(secondJson));
    }

    @Test
    void requestFingerprintExcludesCorrelationButBindsMaterialContent() {
        NormalizedPaymentCommand first = validator.validate(command("corr-one", new BigDecimal("10.0")));
        NormalizedPaymentCommand replay = validator.validate(command("corr-two", new BigDecimal("10.00")));
        NormalizedPaymentCommand changed = validator.validate(command("corr-three", new BigDecimal("11.00")));

        assertEquals(
                jsonFactory.requestFingerprint(first).sha256(),
                jsonFactory.requestFingerprint(replay).sha256());
        assertNotEquals(
                jsonFactory.requestFingerprint(first).sha256(),
                jsonFactory.requestFingerprint(changed).sha256());
    }

    @Test
    void paymentAuthorizedEnvelopeIsAFrozenCanonicalProducerFixture() {
        NormalizedPaymentCommand normalized = validator.validate(command("corr-one", new BigDecimal("10.00")));

        PaymentJsonFactory.PaymentEvent event = jsonFactory.event(
                normalized,
                UUID.fromString("00000000-0000-0000-0000-000000000202"),
                1L,
                Instant.parse("2026-10-12T00:00:00.123456Z"));

        assertEquals("PaymentAuthorized", event.eventType());
        assertEquals(
                "{\"businessTransactionId\":\"txn-1\",\"correlationId\":\"corr-one\","
                        + "\"details\":{\"amount\":\"10.00\",\"currency\":\"INR\",\"orderId\":\"order-1\","
                        + "\"paymentId\":\"00000000-0000-0000-0000-000000000101\",\"paymentVersion\":1},"
                        + "\"eventId\":\"00000000-0000-0000-0000-000000000202\","
                        + "\"eventType\":\"PaymentAuthorized\",\"occurredAt\":\"2026-10-12T00:00:00.123456Z\","
                        + "\"schemaVersion\":\"1.0\",\"sourceSystem\":\"payment-service\","
                        + "\"tenantId\":\"tenant-one\"}",
                event.envelope());
        assertEquals("6d87343783e19987c4b7d3a821bb528ce64a4a70fd3d5a1f694d25565c8e4755", event.payloadHash());
    }

    private static CreatePaymentCommand command(String correlationId, BigDecimal amount) {
        return new CreatePaymentCommand(
                "tenant-one",
                "request-1",
                UUID.fromString("00000000-0000-0000-0000-000000000101"),
                "txn-1",
                "order-1",
                PaymentStatus.AUTHORIZED,
                amount,
                "INR",
                null,
                correlationId);
    }
}

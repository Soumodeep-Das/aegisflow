package com.aegisflow.simulator.payment.persistence.internal;

import com.aegisflow.simulator.payment.PaymentStatus;
import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("postgres")
final class PaymentJsonFactory {

    static final String SOURCE_SYSTEM = "payment-service";
    static final String SCHEMA_VERSION = "1.0";

    private final CanonicalJson canonicalJson;

    PaymentJsonFactory(CanonicalJson canonicalJson) {
        this.canonicalJson = canonicalJson;
    }

    HashedJson requestFingerprint(NormalizedPaymentCommand command) {
        Map<String, Object> material = new TreeMap<>();
        material.put("amount", command.eventAmount());
        material.put("businessTransactionId", command.businessTransactionId());
        material.put("currency", command.currency());
        material.put("declineReason", command.declineReason() == null ? null : command.declineReason().name());
        material.put("idempotencyKey", command.idempotencyKey());
        material.put("operation", IdempotencyRecordStore.OPERATION);
        material.put("orderId", command.orderId());
        material.put("paymentId", command.paymentId().toString());
        material.put("status", command.status().name());
        material.put("tenantId", command.tenantId());
        return hashed(material);
    }

    PaymentEvent event(
            NormalizedPaymentCommand command, UUID eventId, long businessVersion, Instant occurredAt) {
        String eventType = command.status() == PaymentStatus.AUTHORIZED
                ? "PaymentAuthorized"
                : "PaymentDeclined";

        Map<String, Object> details = new TreeMap<>();
        details.put("amount", command.eventAmount());
        details.put("currency", command.currency());
        details.put("orderId", command.orderId());
        details.put("paymentId", command.paymentId().toString());
        details.put("paymentVersion", businessVersion);
        if (command.declineReason() != null) {
            details.put("reasonCode", command.declineReason().name());
        }

        Map<String, Object> envelope = new TreeMap<>();
        envelope.put("businessTransactionId", command.businessTransactionId());
        envelope.put("correlationId", command.correlationId());
        envelope.put("details", details);
        envelope.put("eventId", eventId.toString());
        envelope.put("eventType", eventType);
        envelope.put("occurredAt", occurredAt.toString());
        envelope.put("schemaVersion", SCHEMA_VERSION);
        envelope.put("sourceSystem", SOURCE_SYSTEM);
        envelope.put("tenantId", command.tenantId());

        HashedJson payload = hashed(envelope);
        return new PaymentEvent(eventType, payload.json(), payload.sha256());
    }

    private HashedJson hashed(Object value) {
        String json = canonicalJson.write(value);
        return new HashedJson(json, canonicalJson.sha256(json));
    }

    record HashedJson(String json, String sha256) {}

    record PaymentEvent(String eventType, String envelope, String payloadHash) {}
}

package com.aegisflow.simulator.order.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("postgres")
class OrderOutbox {
    private final JdbcTemplate jdbc;
    OrderOutbox(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    void append(CreateOrder command, UUID eventId, long version, Instant now) {
        String envelope = envelope(command, eventId, version, now);
        jdbc.update("""
                INSERT INTO aegisflow.outbox_event(tenant_id,outbox_id,aggregate_id,aggregate_version,event_id,
                    event_type,envelope,payload_hash,payload_hash_version,status,created_at,next_attempt_at)
                VALUES (?,?,?,?,?,'OrderCreated',CAST(? AS jsonb),?,?,'PENDING',?,?)
                """, command.tenantId(), UUID.randomUUID(), command.orderId(), version, eventId, envelope,
                CanonicalJson.hash(envelope), CanonicalJson.PROFILE, Timestamp.from(now), Timestamp.from(now));
    }
    static String envelope(CreateOrder command, UUID eventId, long version, Instant now) {
        return CanonicalJson.write(Map.of("tenantId", command.tenantId(), "eventId", eventId.toString(),
                "sourceSystem", "order-service", "businessTransactionId", command.businessTransactionId(),
                "schemaVersion", "1.0", "occurredAt", now.toString(), "correlationId", command.correlationId(),
                "eventType", "OrderCreated", "details", Map.of("orderId", command.orderId().toString(),
                        "amount", command.amount().toPlainString(), "currency", command.currency(),
                        "lineItems", CanonicalJson.items(command.lines()), "orderVersion", version)));
    }
}

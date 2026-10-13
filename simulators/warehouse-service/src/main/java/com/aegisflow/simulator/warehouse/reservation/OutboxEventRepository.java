package com.aegisflow.simulator.warehouse.reservation;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("postgres")
final class OutboxEventRepository {

    private final JdbcTemplate jdbcTemplate;

    OutboxEventRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void append(
            String tenantId,
            UUID outboxId,
            UUID aggregateId,
            long aggregateVersion,
            WarehouseOutboxMessage message) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        var occurredAt = OffsetDateTime.ofInstant(message.occurredAt(), ZoneOffset.UTC);
        var inserted = jdbcTemplate.update("""
                INSERT INTO aegisflow.outbox_event (
                    tenant_id, outbox_id, aggregate_id, aggregate_version,
                    event_id, event_type, envelope, payload_hash, payload_hash_version,
                    status, created_at, published_at, publish_attempts, next_attempt_at,
                    last_error_code, lease_owner, lease_until, fencing_token
                ) VALUES (?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?,
                          'PENDING', ?, NULL, 0, ?, NULL, NULL, NULL, 0)
                """,
                tenantId,
                outboxId,
                aggregateId,
                aggregateVersion,
                message.eventId(),
                message.eventType(),
                message.envelope(),
                message.payloadHash(),
                message.payloadHashVersion(),
                occurredAt,
                occurredAt);
        if (inserted != 1) {
            throw new IllegalStateException("outbox append did not insert exactly one row");
        }
    }
}

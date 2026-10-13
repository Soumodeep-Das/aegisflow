package com.aegisflow.simulator.payment.persistence.internal;

import com.aegisflow.simulator.payment.PaymentStatus;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("postgres")
final class IdempotencyRecordStore {

    static final String OPERATION = "CREATE_PAYMENT";

    private final JdbcTemplate jdbcTemplate;

    IdempotencyRecordStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    ClaimResult claim(
            NormalizedPaymentCommand command,
            String requestHash,
            UUID eventId,
            long resourceVersion,
            Instant createdAt) {
        int inserted = jdbcTemplate.update(
                """
                INSERT INTO aegisflow.idempotency_record (
                    tenant_id, operation, idempotency_key, request_hash_version, request_hash,
                    result_code, resource_id, resource_version, event_id, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (tenant_id, operation, idempotency_key) DO NOTHING
                """,
                command.tenantId(),
                OPERATION,
                command.idempotencyKey(),
                CanonicalJson.PROFILE,
                requestHash,
                command.status().name(),
                command.paymentId(),
                resourceVersion,
                eventId,
                Timestamp.from(createdAt));
        if (inserted == 1) {
            return ClaimResult.won(new StoredResult(
                    CanonicalJson.PROFILE,
                    requestHash,
                    command.paymentId(),
                    resourceVersion,
                    eventId,
                    command.status(),
                    createdAt));
        }
        return ClaimResult.replay(load(command.tenantId(), command.idempotencyKey()));
    }

    private StoredResult load(String tenantId, String idempotencyKey) {
        return jdbcTemplate.queryForObject(
                """
                SELECT request_hash_version, request_hash, resource_id, resource_version,
                       event_id, result_code, created_at
                  FROM aegisflow.idempotency_record
                 WHERE tenant_id = ? AND operation = ? AND idempotency_key = ?
                """,
                (resultSet, rowNumber) -> new StoredResult(
                        resultSet.getString("request_hash_version"),
                        resultSet.getString("request_hash"),
                        resultSet.getObject("resource_id", UUID.class),
                        resultSet.getLong("resource_version"),
                        resultSet.getObject("event_id", UUID.class),
                        PaymentStatus.valueOf(resultSet.getString("result_code")),
                        resultSet.getTimestamp("created_at").toInstant()),
                tenantId,
                OPERATION,
                idempotencyKey);
    }

    record ClaimResult(boolean won, StoredResult storedResult) {

        static ClaimResult won(StoredResult result) {
            return new ClaimResult(true, result);
        }

        static ClaimResult replay(StoredResult result) {
            return new ClaimResult(false, result);
        }
    }

    record StoredResult(
            String requestHashVersion,
            String requestHash,
            UUID resourceId,
            long resourceVersion,
            UUID eventId,
            PaymentStatus status,
            Instant createdAt) {}
}

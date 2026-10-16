package com.aegisflow.simulator.warehouse.reservation;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("postgres")
class IdempotencyRepository {

    private static final String OPERATION = "CREATE_RESERVATION";

    private final JdbcTemplate jdbcTemplate;

    IdempotencyRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    IdempotencyClaim claim(
            String tenantId,
            String idempotencyKey,
            String requestHash,
            UUID reservationId,
            UUID eventId,
            long reservationVersion,
            ReservationStatus status,
            Instant createdAt) {
        var inserted = jdbcTemplate.update("""
                INSERT INTO aegisflow.idempotency_record (
                    tenant_id, operation, idempotency_key,
                    request_hash, request_hash_version,
                    resource_id, resource_version, event_id, result_code, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (tenant_id, operation, idempotency_key) DO NOTHING
                """,
                tenantId,
                OPERATION,
                idempotencyKey,
                requestHash,
                CanonicalJson.HASH_VERSION,
                reservationId,
                reservationVersion,
                eventId,
                status.name(),
                OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC));

        var stored = jdbcTemplate.queryForObject("""
                SELECT request_hash, request_hash_version,
                       resource_id, resource_version, event_id, result_code
                  FROM aegisflow.idempotency_record
                 WHERE tenant_id = ? AND operation = ? AND idempotency_key = ?
                """,
                (resultSet, rowNumber) -> new StoredClaim(
                        resultSet.getString("request_hash"),
                        resultSet.getString("request_hash_version"),
                        resultSet.getObject("resource_id", UUID.class),
                        resultSet.getLong("resource_version"),
                        resultSet.getObject("event_id", UUID.class),
                        ReservationStatus.valueOf(resultSet.getString("result_code"))),
                tenantId,
                OPERATION,
                idempotencyKey);

        if (!CanonicalJson.HASH_VERSION.equals(stored.requestHashVersion())
                || !requestHash.equals(stored.requestHash())) {
            throw new IdempotencyConflictException();
        }

        return new IdempotencyClaim(
                inserted == 1,
                stored.reservationId(),
                stored.eventId(),
                stored.reservationVersion(),
                stored.status());
    }

    private record StoredClaim(
            String requestHash,
            String requestHashVersion,
            UUID reservationId,
            long reservationVersion,
            UUID eventId,
            ReservationStatus status) {
    }
}

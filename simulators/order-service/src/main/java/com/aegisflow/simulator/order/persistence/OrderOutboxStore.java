package com.aegisflow.simulator.order.persistence;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** All operations commit independently; broker calls must never run inside these transactions. */
class OrderOutboxStore {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    OrderOutboxStore(JdbcTemplate jdbc, PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        transaction = new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setTimeout(5);
    }

    Optional<OutboxClaim> claim(String workerId, Duration lease) {
        CreateOrder.text(workerId, "workerId");
        long millis = boundedMillis(lease, 1_000, 300_000);
        return transaction.execute(ignored -> jdbc.query("""
                WITH candidate AS (
                    SELECT tenant_id,outbox_id FROM aegisflow.outbox_event
                    WHERE status='PENDING' AND next_attempt_at<=clock_timestamp()
                      AND (lease_until IS NULL OR lease_until<=clock_timestamp())
                    ORDER BY next_attempt_at,created_at,outbox_id
                    FOR UPDATE SKIP LOCKED LIMIT 1
                )
                UPDATE aegisflow.outbox_event AS o
                SET lease_owner=?,lease_until=clock_timestamp()+(? * interval '1 millisecond'),
                    fencing_token=o.fencing_token+1,
                    publish_attempts=LEAST(o.publish_attempts::bigint+1,2147483647)::integer
                FROM candidate AS c WHERE o.tenant_id=c.tenant_id AND o.outbox_id=c.outbox_id
                RETURNING o.tenant_id,o.outbox_id,o.event_id,o.envelope::text,o.payload_hash,
                    o.payload_hash_version,o.lease_owner,o.fencing_token,o.publish_attempts
                """, (rs, row) -> new OutboxClaim(rs.getString(1), rs.getObject(2, UUID.class),
                rs.getObject(3, UUID.class), rs.getString(4), rs.getString(5), rs.getString(6),
                rs.getString(7), rs.getLong(8), rs.getInt(9)), workerId, millis).stream().findFirst());
    }

    boolean published(OutboxClaim claim) {
        return update(claim, "status='PUBLISHED',published_at=clock_timestamp(),last_error_code=NULL");
    }

    boolean retry(OutboxClaim claim, Duration backoff, String fixedErrorCode) {
        long millis = boundedMillis(backoff, 1_000, 60_000);
        checkCode(fixedErrorCode);
        return update(claim, "next_attempt_at=clock_timestamp()+(? * interval '1 millisecond'),last_error_code=?",
                millis, fixedErrorCode);
    }

    boolean fail(OutboxClaim claim, String fixedErrorCode) {
        checkCode(fixedErrorCode);
        return update(claim, "status='FAILED',last_error_code=?", fixedErrorCode);
    }

    private boolean update(OutboxClaim claim, String trustedSetClause, Object... values) {
        var args = new java.util.ArrayList<Object>(java.util.Arrays.asList(values));
        args.add(claim.tenantId()); args.add(claim.outboxId()); args.add(claim.workerId()); args.add(claim.fencingToken());
        // clock_timestamp, not transaction-start time: a lock wait cannot revive an expired lease.
        return Boolean.TRUE.equals(transaction.execute(ignored -> jdbc.update(
                "UPDATE aegisflow.outbox_event SET " + trustedSetClause + ",lease_owner=NULL,lease_until=NULL "
                + "WHERE tenant_id=? AND outbox_id=? AND lease_owner=? AND fencing_token=? "
                + "AND status='PENDING' AND lease_until>clock_timestamp()", args.toArray()) == 1));
    }

    private static void checkCode(String code) {
        if (code == null || !code.matches("[A-Z_]{1,64}")) throw new IllegalArgumentException("Fixed error code required");
    }

    private static long boundedMillis(Duration duration, long min, long max) {
        if (duration == null || duration.compareTo(Duration.ofMillis(min)) < 0
                || duration.compareTo(Duration.ofMillis(max)) > 0) throw new IllegalArgumentException("Duration out of bounds");
        return duration.toMillis();
    }
}

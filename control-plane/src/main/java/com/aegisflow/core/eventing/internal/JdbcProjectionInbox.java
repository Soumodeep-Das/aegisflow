package com.aegisflow.core.eventing.internal;

import com.aegisflow.core.eventing.ProjectionInbox;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

final class JdbcProjectionInbox implements ProjectionInbox {
    private final JdbcTemplate jdbc;
    JdbcProjectionInbox(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    private void transactionRequired() {
        if (!TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Projection inbox requires an active Core transaction");
    }
    @Override public boolean tryLock() {
        transactionRequired();
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT pg_try_advisory_xact_lock(104001,1)",Boolean.class));
    }
    private void gateRequired() {
        if (!tryLock()) throw new IllegalStateException("Projection gate is held by another worker");
    }
    @Override public List<AcceptedEvent> pending(int limit) {
        gateRequired();
        if (limit<1 || limit>100) throw new IllegalArgumentException("Bounded selection required");
        return jdbc.query("""
            SELECT i.* FROM aegisflow.inbox_message i
            WHERE NOT EXISTS (SELECT 1 FROM aegisflow.inbox_projection_progress p
                WHERE p.tenant_id=i.tenant_id AND p.source_system=i.source_system AND p.event_id=i.event_id)
            ORDER BY i.received_at,i.tenant_id,i.source_system,i.event_id LIMIT ?
            """,(rs,n) -> new AcceptedEvent(new Key(rs.getString("tenant_id"),rs.getString("source_system"),
                rs.getObject("event_id",UUID.class),rs.getString("business_transaction_id")),
                rs.getString("event_type"),rs.getString("schema_version"),rs.getTimestamp("occurred_at").toInstant(),
                rs.getTimestamp("received_at").toInstant(),rs.getString("payload"),rs.getString("payload_hash"),
                rs.getString("payload_hash_version")),limit);
    }
    @Override public void applied(Key key,Instant at) { record(key,"APPLIED",null,at); }
    @Override public void blocked(Key key,String reason,Instant at) { record(key,"BLOCKED",reason,at); }
    private void record(Key key,String status,String reason,Instant at) {
        gateRequired();
        jdbc.update("""
            INSERT INTO aegisflow.inbox_projection_progress
            (tenant_id,source_system,event_id,business_transaction_id,projector_version,status,reason_code,recorded_at)
            VALUES (?,?,?,?,1,?,?,?)
            """,key.tenant(),key.source(),key.eventId(),key.transaction(),status,reason,Timestamp.from(at));
    }
    @Override public boolean hasUnsettled(String tenant,String transaction) {
        gateRequired();
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT EXISTS (SELECT 1 FROM aegisflow.inbox_message i
            LEFT JOIN aegisflow.inbox_projection_progress p
              ON (p.tenant_id,p.source_system,p.event_id)=(i.tenant_id,i.source_system,i.event_id)
            WHERE i.tenant_id=? AND i.business_transaction_id=? AND (p.status IS NULL OR p.status<>'APPLIED'))
            """,Boolean.class,tenant,transaction));
    }
}

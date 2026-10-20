package com.aegisflow.core.eventing.internal;

import java.sql.Timestamp;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** All durable processing completes before return. Database uniqueness arbitrates concurrent deliveries. */
public final class InboxStore {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final EventValidator validator;
    private final String channel;

    public InboxStore(JdbcTemplate jdbc, PlatformTransactionManager manager, EventValidator validator, String channel) {
        this.jdbc = jdbc; this.validator = validator; this.channel = EventValidator.identifier(channel);
        transaction = new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        transaction.setTimeout(5);
    }

    public String accept(ConsumerRecord<byte[], byte[]> record) {
        if (record.partition() < 0 || record.offset() < 0 || record.topic() == null || record.topic().length() > 249)
            throw new IllegalArgumentException("Invalid broker coordinates");
        EventValidator.Accepted accepted;
        try { accepted = validator.validate(record); }
        catch (EventValidator.Rejected invalid) {
            return transaction.execute(status -> quarantine(record, invalid.reason));
        }
        var event = accepted;
        return transaction.execute(status -> {
            int inserted = jdbc.update("""
                    INSERT INTO aegisflow.inbox_message
                    (tenant_id,source_system,event_id,event_type,business_transaction_id,schema_version,occurred_at,
                     correlation_id,channel_identity,topic,partition_id,offset_id,payload,payload_hash,payload_hash_version,raw_payload_hash,status)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?::jsonb,?,?,?,'ACCEPTED')
                    ON CONFLICT (tenant_id,source_system,event_id) DO NOTHING
                    """, event.tenant(), event.source(), event.id(), event.type(), event.transaction(), event.schema(),
                    Timestamp.from(event.occurred()), event.correlation(), channel, record.topic(), record.partition(),
                    record.offset(), event.envelope(), event.hash(), EventValidator.HASH_VERSION, EventValidator.hash(record.value()));
            if (inserted == 1) return "ACCEPTED";
            // At READ COMMITTED the conflicting insert waits for the winner; this new statement sees its commit.
            String original = jdbc.queryForObject("""
                    SELECT payload_hash FROM aegisflow.inbox_message
                    WHERE tenant_id=? AND source_system=? AND event_id=?
                    """, String.class, event.tenant(), event.source(), event.id());
            return event.hash().equals(original) ? "DUPLICATE" : quarantine(record, "EVENT_ID_CONFLICT");
        });
    }

    private String quarantine(ConsumerRecord<byte[], byte[]> record, String reason) {
        // Store only digest/coordinates/reason, never untrusted claimed tenant fields or raw potentially secret bytes.
        jdbc.update("""
                INSERT INTO aegisflow.quarantine_record
                (quarantine_id,channel_identity,topic,partition_id,offset_id,raw_payload_hash,payload_bytes,reason_code)
                VALUES (?,?,?,?,?,?,?,?) ON CONFLICT DO NOTHING
                """, UUID.randomUUID(), channel, record.topic(), record.partition(), record.offset(),
                EventValidator.hash(record.value()), record.value() == null ? -1 : record.value().length, reason);
        return "QUARANTINED";
    }
}

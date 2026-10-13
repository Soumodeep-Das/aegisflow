package com.aegisflow.simulator.payment.persistence.internal;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "outbox_event", schema = "aegisflow")
class OutboxEventEntity {

    @EmbeddedId
    private OutboxEventEntityId id;

    @Column(name = "aggregate_id", nullable = false, updatable = false)
    private UUID aggregateId;

    @Column(name = "aggregate_version", nullable = false, updatable = false)
    private long aggregateVersion;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "event_type", nullable = false, length = 64, updatable = false)
    private String eventType;

    @Column(name = "schema_version", nullable = false, length = 16, updatable = false)
    private String schemaVersion;

    @Column(name = "source_system", nullable = false, length = 64, updatable = false)
    private String sourceSystem;

    @Column(name = "business_transaction_id", nullable = false, length = 128, updatable = false)
    private String businessTransactionId;

    @Column(name = "correlation_id", nullable = false, length = 128, updatable = false)
    private String correlationId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "envelope", nullable = false, columnDefinition = "jsonb", updatable = false)
    private String envelope;

    @Column(name = "payload_hash", nullable = false, length = 64, updatable = false)
    private String payloadHash;

    @Column(name = "payload_hash_version", nullable = false, length = 32, updatable = false)
    private String payloadHashVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private OutboxStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "publish_attempts", nullable = false)
    private int publishAttempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "last_error_code", length = 64)
    private String lastErrorCode;

    @Column(name = "lease_owner", length = 128)
    private String leaseOwner;

    @Column(name = "lease_until")
    private Instant leaseUntil;

    @Column(name = "fencing_token", nullable = false)
    private long fencingToken;

    protected OutboxEventEntity() {}

    OutboxEventEntity(
            OutboxEventEntityId id,
            NormalizedPaymentCommand command,
            long aggregateVersion,
            UUID eventId,
            PaymentJsonFactory.PaymentEvent event,
            Instant now) {
        this.id = id;
        this.aggregateId = command.paymentId();
        this.aggregateVersion = aggregateVersion;
        this.eventId = eventId;
        this.eventType = event.eventType();
        this.schemaVersion = PaymentJsonFactory.SCHEMA_VERSION;
        this.sourceSystem = PaymentJsonFactory.SOURCE_SYSTEM;
        this.businessTransactionId = command.businessTransactionId();
        this.correlationId = command.correlationId();
        this.envelope = event.envelope();
        this.payloadHash = event.payloadHash();
        this.payloadHashVersion = CanonicalJson.PROFILE;
        this.status = OutboxStatus.PENDING;
        this.createdAt = now;
        this.publishAttempts = 0;
        this.nextAttemptAt = now;
        this.fencingToken = 0;
    }
}

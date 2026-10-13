package com.aegisflow.simulator.warehouse.reservation;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "reservations", schema = "aegisflow")
class ReservationEntity {

    @EmbeddedId
    private ReservationEntityId id;

    @Column(name = "business_transaction_id", nullable = false, length = 128)
    private String businessTransactionId;

    @Column(name = "order_id", nullable = false, length = 128)
    private String orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ReservationStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "rejection_reason", length = 32)
    private RejectionReason rejectionReason;

    /** Hibernate stores zero initially; the public domain/event version is lockVersion + 1. */
    @Version
    @Column(name = "lock_version", nullable = false)
    private Long lockVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ReservationEntity() {
    }

    ReservationEntity(CreateReservationCommand command, Instant now) {
        this.id = new ReservationEntityId(command.tenantId(), command.reservationId());
        this.businessTransactionId = command.businessTransactionId();
        this.orderId = command.orderId();
        this.status = command.status();
        this.rejectionReason = command.rejectionReason();
        this.createdAt = Objects.requireNonNull(now);
        this.updatedAt = now;
    }

    String tenantId() {
        return id.tenantId();
    }

    UUID reservationId() {
        return id.reservationId();
    }

    long exposedVersion() {
        return lockVersion == null ? 1 : Math.addExact(lockVersion, 1);
    }
}

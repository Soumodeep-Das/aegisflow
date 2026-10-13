package com.aegisflow.simulator.warehouse.reservation;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
class ReservationLineEntityId implements Serializable {

    @Column(name = "tenant_id", nullable = false, length = 128)
    private String tenantId;

    @Column(name = "reservation_id", nullable = false)
    private UUID reservationId;

    @Column(name = "sku", nullable = false, length = 128)
    private String sku;

    protected ReservationLineEntityId() {
    }

    ReservationLineEntityId(String tenantId, UUID reservationId, String sku) {
        this.tenantId = Objects.requireNonNull(tenantId);
        this.reservationId = Objects.requireNonNull(reservationId);
        this.sku = Objects.requireNonNull(sku);
    }

    String tenantId() {
        return tenantId;
    }

    UUID reservationId() {
        return reservationId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ReservationLineEntityId that)) {
            return false;
        }
        return tenantId.equals(that.tenantId)
                && reservationId.equals(that.reservationId)
                && sku.equals(that.sku);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantId, reservationId, sku);
    }
}

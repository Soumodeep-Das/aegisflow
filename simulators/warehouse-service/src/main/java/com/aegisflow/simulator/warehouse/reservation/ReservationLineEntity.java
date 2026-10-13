package com.aegisflow.simulator.warehouse.reservation;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "reservation_lines", schema = "aegisflow")
class ReservationLineEntity {

    @EmbeddedId
    private ReservationLineEntityId id;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    protected ReservationLineEntity() {
    }

    ReservationLineEntity(String tenantId, java.util.UUID reservationId, ReservationLine line) {
        this.id = new ReservationLineEntityId(tenantId, reservationId, line.sku());
        this.quantity = line.quantity();
    }

    String tenantId() {
        return id.tenantId();
    }

    java.util.UUID reservationId() {
        return id.reservationId();
    }
}

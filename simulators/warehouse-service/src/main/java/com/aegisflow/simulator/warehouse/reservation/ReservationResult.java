package com.aegisflow.simulator.warehouse.reservation;

import java.util.UUID;

public record ReservationResult(
        String tenantId,
        UUID reservationId,
        UUID eventId,
        long reservationVersion,
        ReservationStatus status) {
}

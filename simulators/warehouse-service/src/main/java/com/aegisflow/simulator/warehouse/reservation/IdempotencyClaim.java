package com.aegisflow.simulator.warehouse.reservation;

import java.util.UUID;

record IdempotencyClaim(
        boolean newlyClaimed,
        UUID reservationId,
        UUID eventId,
        long reservationVersion,
        ReservationStatus status) {
}

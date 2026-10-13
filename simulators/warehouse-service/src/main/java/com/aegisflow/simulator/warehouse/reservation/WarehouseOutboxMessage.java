package com.aegisflow.simulator.warehouse.reservation;

import java.time.Instant;
import java.util.UUID;

record WarehouseOutboxMessage(
        UUID eventId,
        String eventType,
        String envelope,
        String payloadHash,
        String payloadHashVersion,
        Instant occurredAt) {
}

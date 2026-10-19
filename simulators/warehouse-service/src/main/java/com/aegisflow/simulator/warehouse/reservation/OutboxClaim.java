package com.aegisflow.simulator.warehouse.reservation;

import java.util.UUID;

/** A lease capability, not a new event identity. Never log its envelope. */
record OutboxClaim(String tenantId, UUID outboxId, UUID eventId, String envelope,
                   String payloadHash, String payloadHashVersion, String workerId,
                   long fencingToken, int attempts) {
    @Override public String toString() { return "OutboxClaim[content redacted]"; }
}

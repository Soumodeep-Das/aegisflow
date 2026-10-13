package com.aegisflow.simulator.payment;

import java.time.Instant;
import java.util.UUID;

/** Stable result returned for both the winning create and an identical replay. */
public record RecordedPayment(
        String tenantId,
        UUID paymentId,
        PaymentStatus status,
        long businessVersion,
        UUID eventId,
        Instant occurredAt) {}

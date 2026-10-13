package com.aegisflow.simulator.warehouse.reservation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record CreateReservationCommand(
        String tenantId,
        String idempotencyKey,
        UUID reservationId,
        String businessTransactionId,
        String orderId,
        String correlationId,
        ReservationStatus status,
        RejectionReason rejectionReason,
        List<ReservationLine> lines) {

    public static final int MAX_OPAQUE_ID_LENGTH = 128;
    public static final int MAX_LINES = 100;

    public CreateReservationCommand {
        tenantId = requireOpaque("tenantId", tenantId);
        idempotencyKey = requireOpaque("idempotencyKey", idempotencyKey);
        Objects.requireNonNull(reservationId, "reservationId must not be null");
        businessTransactionId = requireOpaque("businessTransactionId", businessTransactionId);
        orderId = requireOpaque("orderId", orderId);
        correlationId = requireOpaque("correlationId", correlationId);
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(lines, "lines must not be null");

        if (status == ReservationStatus.RESERVED && rejectionReason != null) {
            throw new IllegalArgumentException("a reserved outcome must not have a rejection reason");
        }
        if (status == ReservationStatus.REJECTED && rejectionReason != RejectionReason.OUT_OF_STOCK) {
            throw new IllegalArgumentException("a rejected outcome requires OUT_OF_STOCK");
        }
        if (lines.isEmpty() || lines.size() > MAX_LINES) {
            throw new IllegalArgumentException("lines must contain between 1 and 100 items");
        }

        var copiedLines = new ArrayList<ReservationLine>(lines.size());
        var seenSkus = new HashSet<String>();
        for (var line : lines) {
            Objects.requireNonNull(line, "lines must not contain null items");
            if (!seenSkus.add(line.sku())) {
                throw new IllegalArgumentException("duplicate sku: " + line.sku());
            }
            copiedLines.add(line);
        }
        copiedLines.sort(Comparator.comparing(ReservationLine::sku));
        lines = List.copyOf(copiedLines);
    }

    private static String requireOpaque(String name, String value) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank() || value.length() > MAX_OPAQUE_ID_LENGTH) {
            throw new IllegalArgumentException(name + " must be non-blank and at most 128 characters");
        }
        return value;
    }
}

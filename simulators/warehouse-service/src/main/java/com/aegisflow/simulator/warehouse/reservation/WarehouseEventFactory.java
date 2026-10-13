package com.aegisflow.simulator.warehouse.reservation;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Component
final class WarehouseEventFactory {

    private static final String SOURCE_SYSTEM = "warehouse-service";
    private static final String SCHEMA_VERSION = "1.0";

    private final ObjectMapper objectMapper;
    private final CanonicalJson canonicalJson;

    WarehouseEventFactory(ObjectMapper objectMapper, CanonicalJson canonicalJson) {
        this.objectMapper = objectMapper;
        this.canonicalJson = canonicalJson;
    }

    WarehouseOutboxMessage create(
            CreateReservationCommand command,
            UUID eventId,
            long reservationVersion,
            Instant occurredAt) {
        var normalizedTime = occurredAt.truncatedTo(ChronoUnit.MICROS);
        var eventType = command.status() == ReservationStatus.RESERVED
                ? "InventoryReserved"
                : "InventoryRejected";

        var envelope = objectMapper.createObjectNode();
        envelope.put("tenantId", command.tenantId());
        envelope.put("eventId", eventId.toString());
        envelope.put("sourceSystem", SOURCE_SYSTEM);
        envelope.put("businessTransactionId", command.businessTransactionId());
        envelope.put("schemaVersion", SCHEMA_VERSION);
        envelope.put("occurredAt", normalizedTime.toString());
        envelope.put("correlationId", command.correlationId());
        envelope.put("eventType", eventType);
        envelope.set("details", details(command, reservationVersion));

        return new WarehouseOutboxMessage(
                eventId,
                eventType,
                canonicalJson.canonicalString(envelope),
                canonicalJson.sha256(envelope),
                CanonicalJson.HASH_VERSION,
                normalizedTime);
    }

    private ObjectNode details(CreateReservationCommand command, long reservationVersion) {
        var details = objectMapper.createObjectNode();
        details.put("reservationId", command.reservationId().toString());
        details.put("orderId", command.orderId());
        if (command.status() == ReservationStatus.RESERVED) {
            var items = details.putArray("items");
            for (var line : command.lines()) {
                var item = items.addObject();
                item.put("sku", line.sku());
                item.put("quantity", line.quantity());
            }
        } else {
            details.put("reasonCode", command.rejectionReason().name());
        }
        details.put("reservationVersion", reservationVersion);
        return details;
    }
}

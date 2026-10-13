package com.aegisflow.simulator.warehouse.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

class WarehouseEventContractTests {

    private static final UUID RESERVATION_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID EVENT_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final Instant OCCURRED_AT = Instant.parse("2026-10-12T06:30:00.123456789Z");

    private ReservationRequestFingerprint fingerprint;
    private WarehouseEventFactory eventFactory;

    @BeforeEach
    void setUp() {
        var objectMapper = new ObjectMapper();
        var canonicalJson = new CanonicalJson(objectMapper);
        fingerprint = new ReservationRequestFingerprint(objectMapper, canonicalJson);
        eventFactory = new WarehouseEventFactory(objectMapper, canonicalJson);
    }

    @Test
    void createsCanonicalInventoryReservedEnvelopeAndStableHash() {
        var message = eventFactory.create(reserved("corr-original"), EVENT_ID, 1, OCCURRED_AT);

        assertThat(message.eventType()).isEqualTo("InventoryReserved");
        assertThat(message.occurredAt()).isEqualTo(Instant.parse("2026-10-12T06:30:00.123456Z"));
        assertThat(message.envelope()).isEqualTo("""
                {"businessTransactionId":"txn-order-1","correlationId":"corr-original","details":{"items":[{"quantity":1,"sku":"SKU-A"},{"quantity":2,"sku":"SKU-B"}],"orderId":"order-1","reservationId":"10000000-0000-0000-0000-000000000001","reservationVersion":1},"eventId":"20000000-0000-0000-0000-000000000002","eventType":"InventoryReserved","occurredAt":"2026-10-12T06:30:00.123456Z","schemaVersion":"1.0","sourceSystem":"warehouse-service","tenantId":"tenant-demo"}""");
        assertThat(message.payloadHash()).isEqualTo("ef5eea63a400d55dd0c531f0a470d3314617ee413753665c5fb4dfe17472db44");
        assertThat(message.payloadHashVersion()).isEqualTo("aegisflow-json-v1");
    }

    @Test
    void createsContractShapedInventoryRejectedEnvelope() {
        var command = new CreateReservationCommand(
                "tenant-demo",
                "idem-warehouse-1",
                RESERVATION_ID,
                "txn-order-1",
                "order-1",
                "corr-rejected",
                ReservationStatus.REJECTED,
                RejectionReason.OUT_OF_STOCK,
                List.of(new ReservationLine("SKU-A", 1)));

        var message = eventFactory.create(command, EVENT_ID, 1, OCCURRED_AT);

        assertThat(message.eventType()).isEqualTo("InventoryRejected");
        assertThat(message.envelope())
                .contains("\"reasonCode\":\"OUT_OF_STOCK\"")
                .doesNotContain("\"items\"");
    }

    @Test
    void requestFingerprintSortsLinesAndExcludesCorrelationId() {
        var first = reserved("corr-one");
        var reordered = new CreateReservationCommand(
                first.tenantId(),
                first.idempotencyKey(),
                first.reservationId(),
                first.businessTransactionId(),
                first.orderId(),
                "corr-two",
                first.status(),
                first.rejectionReason(),
                List.of(new ReservationLine("SKU-A", 1), new ReservationLine("SKU-B", 2)));
        var changedQuantity = new CreateReservationCommand(
                first.tenantId(),
                first.idempotencyKey(),
                first.reservationId(),
                first.businessTransactionId(),
                first.orderId(),
                first.correlationId(),
                first.status(),
                first.rejectionReason(),
                List.of(new ReservationLine("SKU-A", 1), new ReservationLine("SKU-B", 3)));

        assertThat(fingerprint.canonicalMaterial(first)).isEqualTo("""
                {"businessTransactionId":"txn-order-1","lineItems":[{"quantity":1,"sku":"SKU-A"},{"quantity":2,"sku":"SKU-B"}],"operation":"CREATE_RESERVATION","orderId":"order-1","rejectionReason":null,"reservationId":"10000000-0000-0000-0000-000000000001","status":"RESERVED","tenantId":"tenant-demo"}""");
        assertThat(fingerprint.hash(first)).isEqualTo(fingerprint.hash(reordered));
        assertThat(fingerprint.hash(first)).isNotEqualTo(fingerprint.hash(changedQuantity));
    }

    private static CreateReservationCommand reserved(String correlationId) {
        return new CreateReservationCommand(
                "tenant-demo",
                "idem-warehouse-1",
                RESERVATION_ID,
                "txn-order-1",
                "order-1",
                correlationId,
                ReservationStatus.RESERVED,
                null,
                List.of(new ReservationLine("SKU-B", 2), new ReservationLine("SKU-A", 1)));
    }
}

package com.aegisflow.simulator.warehouse.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class CreateReservationCommandTests {

    private static final UUID RESERVATION_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");

    @Test
    void validatesAndSortsUniqueLinesBySku() {
        var command = command(
                ReservationStatus.RESERVED,
                null,
                List.of(new ReservationLine("SKU-B", 2), new ReservationLine("SKU-A", 1)));

        assertThat(command.lines()).extracting(ReservationLine::sku)
                .containsExactly("SKU-A", "SKU-B");
        assertThatThrownBy(() -> command.lines().add(new ReservationLine("SKU-C", 3)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsDuplicateSkuAndOutOfRangeCollections() {
        assertThatThrownBy(() -> command(
                ReservationStatus.RESERVED,
                null,
                List.of(new ReservationLine("SKU-A", 1), new ReservationLine("SKU-A", 2))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate sku");

        assertThatThrownBy(() -> command(ReservationStatus.RESERVED, null, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 1 and 100");

        var tooMany = java.util.stream.IntStream.rangeClosed(1, 101)
                .mapToObj(index -> new ReservationLine("SKU-" + index, 1))
                .toList();
        assertThatThrownBy(() -> command(ReservationStatus.RESERVED, null, tooMany))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 1 and 100");
    }

    @Test
    void enforcesQuantityAndOutcomeReasonRules() {
        assertThatThrownBy(() -> new ReservationLine("SKU-A", 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReservationLine("SKU-A", ReservationLine.MAX_QUANTITY + 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> command(
                ReservationStatus.RESERVED,
                RejectionReason.OUT_OF_STOCK,
                List.of(new ReservationLine("SKU-A", 1))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> command(
                ReservationStatus.REJECTED,
                null,
                List.of(new ReservationLine("SKU-A", 1))))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(command(
                ReservationStatus.REJECTED,
                RejectionReason.OUT_OF_STOCK,
                List.of(new ReservationLine("SKU-A", 1))).rejectionReason())
                .isEqualTo(RejectionReason.OUT_OF_STOCK);
    }

    private static CreateReservationCommand command(
            ReservationStatus status,
            RejectionReason reason,
            List<ReservationLine> lines) {
        return new CreateReservationCommand(
                "tenant-demo",
                "idem-warehouse-1",
                RESERVATION_ID,
                "txn-order-1",
                "order-1",
                "corr-1",
                status,
                reason,
                lines);
    }
}

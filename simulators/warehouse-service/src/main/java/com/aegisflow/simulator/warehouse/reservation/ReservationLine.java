package com.aegisflow.simulator.warehouse.reservation;

import java.util.Objects;

public record ReservationLine(String sku, int quantity) {

    public static final int MAX_SKU_LENGTH = 128;
    public static final int MAX_QUANTITY = 1_000_000;

    public ReservationLine {
        Objects.requireNonNull(sku, "sku must not be null");
        if (sku.isBlank() || sku.length() > MAX_SKU_LENGTH) {
            throw new IllegalArgumentException("sku must be non-blank and at most 128 characters");
        }
        if (quantity < 1 || quantity > MAX_QUANTITY) {
            throw new IllegalArgumentException("quantity must be between 1 and " + MAX_QUANTITY);
        }
    }
}

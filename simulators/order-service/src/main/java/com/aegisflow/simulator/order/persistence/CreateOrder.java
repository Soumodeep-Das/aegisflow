package com.aegisflow.simulator.order.persistence;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.Currency;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Internal trusted caller contract; it is deliberately not an unauthenticated HTTP endpoint. */
public record CreateOrder(String tenantId, UUID orderId, String businessTransactionId, String correlationId,
                          String idempotencyKey, BigDecimal amount, String currency, List<Line> lines) {
    public CreateOrder {
        tenantId = text(tenantId, "tenantId"); Objects.requireNonNull(orderId, "orderId");
        businessTransactionId = text(businessTransactionId, "businessTransactionId");
        correlationId = text(correlationId, "correlationId"); idempotencyKey = text(idempotencyKey, "idempotencyKey");
        Objects.requireNonNull(amount, "amount");
        if (currency == null || !currency.matches("[A-Z]{3}")) throw new IllegalArgumentException("ISO currency required");
        int scale = Currency.getInstance(currency).getDefaultFractionDigits();
        if (scale < 0 || scale > 4) throw new IllegalArgumentException("Unsupported currency scale");
        amount = amount.setScale(scale, RoundingMode.UNNECESSARY);
        if (amount.signum() <= 0 || amount.compareTo(new BigDecimal("1000000000000000")) >= 0)
            throw new IllegalArgumentException("Positive amount must fit numeric(19,4)");
        if (lines == null || lines.isEmpty() || lines.size() > 100) throw new IllegalArgumentException("1..100 lines required");
        lines = lines.stream().sorted(Comparator.comparing(Line::sku)).toList();
        var skus = new HashSet<String>();
        for (Line line : lines) if (!skus.add(line.sku())) throw new IllegalArgumentException("Duplicate SKU");
    }
    static String text(String value, String field) {
        if (value == null || value.isBlank() || value.length() > 128 || !value.equals(value.trim()))
            throw new IllegalArgumentException(field + " must be a nonblank bounded identifier");
        return value;
    }
    public record Line(String sku, int quantity) {
        public Line {
            sku = text(sku, "sku");
            if (quantity < 1 || quantity > 1000000) throw new IllegalArgumentException("Quantity must be 1..1000000");
        }
    }
}

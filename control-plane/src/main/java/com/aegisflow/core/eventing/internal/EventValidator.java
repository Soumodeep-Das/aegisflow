package com.aegisflow.core.eventing.internal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** Supported initial schemas only. No correlation, state inference, or executable payload interpretation. */
public final class EventValidator {
    public static final int MAX_BYTES = 262_144;
    public static final String HASH_VERSION = "aegisflow-inbox-json-v1";
    private static final JsonMapper JSON = JsonMapper.builder(JsonFactory.builder()
            .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(24).maxStringLength(4096)
                    .maxNumberLength(32).build())
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();
    private static final Map<String, Set<String>> TYPES = Map.of(
            "order-service", Set.of("OrderCreated"),
            "payment-service", Set.of("PaymentAuthorized", "PaymentDeclined"),
            "warehouse-service", Set.of("InventoryReserved", "InventoryRejected"));
    private final IntakePolicy policy;

    public EventValidator(IntakePolicy policy) { this.policy = policy; }

    public Accepted validate(ConsumerRecord<byte[], byte[]> record) {
        if (record.value() == null || record.value().length > MAX_BYTES) throw rejected("SIZE_OR_TOMBSTONE");
        JsonNode root;
        try {
            String input = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(record.value())).toString();
            // readTree alone can accept a valid first value followed by another document.
            try (var parser = JSON.createParser(input)) {
                root = JSON.readTree(parser);
                if (parser.nextToken() != null) throw rejected("MALFORMED_JSON");
            }
        } catch (Rejected invalid) { throw invalid; }
        catch (Exception invalid) { throw rejected("MALFORMED_JSON"); }
        if (root == null || !root.isObject()) throw rejected("SCHEMA_INVALID");
        String source = text(root, "sourceSystem");
        if (!source.equals(IntakePolicy.SOURCES.get(record.topic()))) throw rejected("SOURCE_NOT_TRUSTED");
        String tenant = text(root, "tenantId");
        if (!policy.permits(source, tenant)) throw rejected("TENANT_NOT_TRUSTED");
        String version = text(root, "schemaVersion");
        if (!version.matches("1\\.[0-9]{1,4}")) throw rejected("SCHEMA_UNSUPPORTED");
        String type = text(root, "eventType");
        if (!TYPES.get(source).contains(type)) throw rejected("EVENT_UNSUPPORTED");
        String eventId = text(root, "eventId");
        UUID id;
        Instant occurred;
        try {
            id = UUID.fromString(eventId);
            if (!id.toString().equalsIgnoreCase(eventId)) throw new IllegalArgumentException();
            String time = text(root, "occurredAt");
            if (!time.endsWith("Z")) throw new IllegalArgumentException();
            occurred = Instant.parse(time);
            if (occurred.isBefore(Instant.parse("0001-01-01T00:00:00Z"))
                    || occurred.isAfter(Instant.parse("9999-12-31T23:59:59Z"))) throw new IllegalArgumentException();
        } catch (RuntimeException invalid) { throw rejected("IDENTITY_OR_TIME_INVALID"); }
        String transaction = text(root, "businessTransactionId");
        String correlation = text(root, "correlationId");
        byte[] expectedKey = (tenant + ":" + transaction).getBytes(StandardCharsets.UTF_8);
        if (!Arrays.equals(record.key(), expectedKey)) throw rejected("KEY_MISMATCH");
        JsonNode details = root.get("details");
        if (details == null || !details.isObject()) throw rejected("SCHEMA_INVALID");
        var normalized = (Map<String, Object>) canonical(root);
        var normalizedDetails = (Map<String, Object>) normalized.get("details");
        text(details, "orderId");
        switch (type) {
            case "OrderCreated" -> { positive(details, "orderVersion"); money(details, normalizedDetails); items(details, "lineItems"); }
            case "PaymentAuthorized", "PaymentDeclined" -> {
                text(details, "paymentId"); positive(details, "paymentVersion");
                // Existing Payment envelopes include amount/currency for both initial outcomes.
                money(details, normalizedDetails);
                if (type.equals("PaymentDeclined") && !Set.of("INSUFFICIENT_FUNDS", "PAYMENT_METHOD_REJECTED",
                        "RISK_REJECTED", "SIMULATED_DECLINE").contains(text(details, "reasonCode")))
                    throw rejected("SCHEMA_INVALID");
            }
            case "InventoryReserved", "InventoryRejected" -> {
                text(details, "reservationId"); positive(details, "reservationVersion");
                if (type.equals("InventoryReserved")) items(details, "items");
                if (type.equals("InventoryRejected") && !text(details, "reasonCode").equals("OUT_OF_STOCK"))
                    throw rejected("SCHEMA_INVALID");
            }
            default -> throw rejected("EVENT_UNSUPPORTED");
        }
        normalized.put("eventId", id.toString());
        normalized.put("occurredAt", occurred.toString());
        String envelope = JSON.writeValueAsString(normalized);
        if (envelope.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) throw rejected("SIZE_OR_TOMBSTONE");
        return new Accepted(tenant, source, id, type, transaction, version, occurred, correlation,
                envelope, hash(envelope.getBytes(StandardCharsets.UTF_8)));
    }

    private static void money(JsonNode details, Map<String, Object> normalized) {
        String value = text(details, "amount"), currency = text(details, "currency");
        try {
            if (!currency.matches("[A-Z]{3}") || !value.matches("[0-9]{1,15}(\\.[0-9]{1,4})?"))
                throw new IllegalArgumentException();
            int scale = Currency.getInstance(currency).getDefaultFractionDigits();
            if (scale < 0 || scale > 4) throw new IllegalArgumentException();
            BigDecimal amount = new BigDecimal(value).setScale(scale, RoundingMode.UNNECESSARY);
            if (amount.signum() <= 0 || amount.compareTo(new BigDecimal("1000000000000000")) >= 0)
                throw new IllegalArgumentException();
            normalized.put("amount", amount.toPlainString());
        } catch (RuntimeException invalid) { throw rejected("SCHEMA_INVALID"); }
    }

    private static void items(JsonNode details, String field) {
        JsonNode items = details.get(field);
        if (items == null || !items.isArray() || items.isEmpty() || items.size() > 100) throw rejected("SCHEMA_INVALID");
        Set<String> seen = new HashSet<>();
        for (JsonNode item : items) {
            if (!item.isObject() || !seen.add(text(item, "sku"))) throw rejected("SCHEMA_INVALID");
            long quantity = positive(item, "quantity");
            if (quantity > 1_000_000) throw rejected("SCHEMA_INVALID");
        }
    }
    private static long positive(JsonNode object, String name) {
        JsonNode value = object.get(name);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() < 1)
            throw rejected("SCHEMA_INVALID");
        return value.longValue();
    }
    private static String text(JsonNode object, String name) {
        JsonNode node = object.get(name);
        if (node == null || !node.isString()) throw rejected("SCHEMA_INVALID");
        return identifier(node.stringValue());
    }
    static String identifier(String value) {
        if (value == null || value.isBlank() || value.length() > 128 || !value.equals(value.trim())
                || value.chars().anyMatch(c -> Character.isISOControl(c) || c == 0))
            throw rejected("SCHEMA_INVALID");
        return value;
    }
    private static Object canonical(JsonNode value) {
        if (value.isObject()) {
            Map<String, Object> map = new TreeMap<>();
            value.properties().forEach(entry -> {
                safeJsonText(entry.getKey());
                map.put(entry.getKey(), canonical(entry.getValue()));
            });
            return map;
        }
        if (value.isArray()) {
            List<Object> list = new ArrayList<>();
            value.forEach(item -> list.add(canonical(item)));
            return list;
        }
        // Exact values avoid merging changed optional numeric content through double rounding.
        if (value.isNumber()) {
            var number = value.decimalValue();
            if (number.precision() > 32 || number.scale() < -32 || number.scale() > 32)
                throw rejected("SCHEMA_INVALID");
            return number.stripTrailingZeros();
        }
        if (value.isString()) safeJsonText(value.stringValue());
        return JSON.convertValue(value, Object.class);
    }
    private static void safeJsonText(String value) {
        if (value.length() > 4096) throw rejected("SCHEMA_INVALID");
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == 0 || Character.isLowSurrogate(ch)) throw rejected("SCHEMA_INVALID");
            if (Character.isHighSurrogate(ch)) {
                if (++i == value.length() || !Character.isLowSurrogate(value.charAt(i))) throw rejected("SCHEMA_INVALID");
            }
        }
    }
    static String hash(byte[] value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value == null ? new byte[0] : value)); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    static Rejected rejected(String reason) { return new Rejected(reason); }
    static final class Rejected extends IllegalArgumentException {
        final String reason;
        Rejected(String reason) { super(reason); this.reason = reason; }
    }
    public record Accepted(String tenant, String source, UUID id, String type, String transaction,
            String schema, Instant occurred, String correlation, String envelope, String hash) {
        @Override public String toString() { return "Accepted[content redacted]"; }
    }
}

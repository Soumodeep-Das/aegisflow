package com.aegisflow.simulator.order.persistence;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import tools.jackson.databind.json.JsonMapper;

/** aegisflow-json-v1: sorted object keys, stable array order, pre-normalized decimal/time strings. */
final class CanonicalJson {
    static final String PROFILE = "aegisflow-json-v1";
    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private CanonicalJson() { }
    static String write(Object value) { return MAPPER.writeValueAsString(sorted(value)); }
    private static Object sorted(Object value) {
        if (value instanceof Map<?, ?> map) {
            var result = new TreeMap<String, Object>();
            map.forEach((key, entry) -> result.put((String) key, sorted(entry)));
            return result;
        }
        if (value instanceof List<?> list) return list.stream().map(CanonicalJson::sorted).toList();
        return value;
    }
    static String hash(String json) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    static String requestHash(CreateOrder command) {
        return hash(write(Map.of("profile", PROFILE, "operation", "CREATE_ORDER", "tenantId", command.tenantId(),
                "orderId", command.orderId().toString(), "businessTransactionId", command.businessTransactionId(),
                "amount", command.amount().toPlainString(), "currency", command.currency(), "lines", items(command.lines()))));
    }
    static List<Map<String, Object>> items(List<CreateOrder.Line> lines) {
        return lines.stream().map(line -> Map.<String, Object>of("sku", line.sku(), "quantity", line.quantity())).toList();
    }
}

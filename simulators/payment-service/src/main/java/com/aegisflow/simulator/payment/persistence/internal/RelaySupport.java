package com.aegisflow.simulator.payment.persistence.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import tools.jackson.databind.json.JsonMapper;

/** Uses this owner's existing canonical representation; no dependency on another deployable. */
final class RelaySupport {
    static final String PROFILE = "aegisflow-json-v1";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final CanonicalJson CANONICAL = new CanonicalJson(JSON);
    static String write(Object value) { return CANONICAL.write(value); }
    static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException failure) { throw new IllegalStateException("SHA-256 required", failure); }
    }
    static String text(String value, String field) {
        if (value == null || value.isBlank() || value.length() > 128 || !value.equals(value.trim()))
            throw new IllegalArgumentException(field + " must be a nonblank bounded identifier");
        return value;
    }
}

package com.aegisflow.simulator.payment.persistence.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@Profile("postgres")
final class CanonicalJson {

    static final String PROFILE = "aegisflow-json-v1";

    private final ObjectMapper objectMapper;

    CanonicalJson(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    String write(Object value) {
        try {
            return objectMapper.writeValueAsString(canonicalize(value));
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Unable to create canonical JSON", exception);
        }
    }

    String sha256(String canonicalJson) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonicalJson.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", exception);
        }
    }

    private Object canonicalize(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sorted = new TreeMap<>();
            map.forEach((key, nested) -> sorted.put(String.valueOf(key), canonicalize(nested)));
            return sorted;
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> ordered = new ArrayList<>();
            iterable.forEach(item -> ordered.add(canonicalize(item)));
            return ordered;
        }
        return value;
    }
}

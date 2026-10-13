package com.aegisflow.simulator.warehouse.reservation;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.TreeMap;

import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
final class CanonicalJson {

    static final String HASH_VERSION = "aegisflow-json-v1";

    private final ObjectMapper objectMapper;

    CanonicalJson(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    String canonicalString(JsonNode value) {
        return objectMapper.writeValueAsString(sortRecursively(value));
    }

    String sha256(JsonNode value) {
        return HexFormat.of().formatHex(newDigest().digest(
                objectMapper.writeValueAsBytes(sortRecursively(value))));
    }

    private JsonNode sortRecursively(JsonNode value) {
        if (value.isObject()) {
            var sortedProperties = new TreeMap<String, JsonNode>();
            value.forEachEntry((name, child) -> sortedProperties.put(name, sortRecursively(child)));
            var result = objectMapper.createObjectNode();
            sortedProperties.forEach(result::set);
            return result;
        }
        if (value.isArray()) {
            var result = objectMapper.createArrayNode();
            value.forEach(child -> result.add(sortRecursively(child)));
            return result;
        }
        return value.deepCopy();
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Java runtime does not provide SHA-256", exception);
        }
    }
}

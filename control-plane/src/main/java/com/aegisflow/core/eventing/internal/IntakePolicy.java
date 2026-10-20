package com.aegisflow.core.eventing.internal;

import java.util.Map;
import java.util.Set;

/** Operator configuration plus source-topic ACLs establish authority; payload/header claims do not. */
public record IntakePolicy(Map<String, Set<String>> tenantsBySource) {
    public static final Map<String, String> SOURCES = Map.of(
            "aegisflow.order.events.v1", "order-service",
            "aegisflow.payment.events.v1", "payment-service",
            "aegisflow.warehouse.events.v1", "warehouse-service");
    public IntakePolicy {
        if (tenantsBySource == null || tenantsBySource.isEmpty()) throw new IllegalArgumentException("Trusted bindings required");
        var copy = new java.util.HashMap<String, Set<String>>();
        tenantsBySource.forEach((source, tenants) -> {
            if (!SOURCES.containsValue(source) || tenants == null || tenants.isEmpty())
                throw new IllegalArgumentException("Unknown source or empty tenant binding");
            tenants.forEach(EventValidator::identifier);
            copy.put(source, Set.copyOf(tenants));
        });
        tenantsBySource = Map.copyOf(copy);
    }
    boolean permits(String source, String tenant) {
        return tenantsBySource.getOrDefault(source, Set.of()).contains(tenant);
    }
}

package com.aegisflow.simulator.warehouse.reservation;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

/** At-least-once: a crash after Kafka acknowledgement but before the fenced update may resend. */
class WarehouseOutboxRelay {
    static final String TOPIC = "aegisflow.warehouse.events.v1";
    static final Duration LEASE = Duration.ofSeconds(60);
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final WarehouseOutboxStore store;
    private final Publisher publisher;
    private final String worker;
    private final int maxPerPoll;
    private final Set<String> allowedTenants;

    @FunctionalInterface interface Publisher { void publish(String key, String envelope) throws Exception; }

    WarehouseOutboxRelay(WarehouseOutboxStore store, Publisher publisher, String worker, int maxPerPoll, Set<String> allowedTenants) {
        this.store = store; this.publisher = publisher;
        this.worker = RelaySupport.text(worker, "workerId");
        if (maxPerPoll < 1 || maxPerPoll > 100) throw new IllegalArgumentException("Poll limit must be 1..100");
        if (allowedTenants == null || allowedTenants.isEmpty()) throw new IllegalArgumentException("Explicit tenants required");
        allowedTenants.forEach(tenant -> RelaySupport.text(tenant, "allowedTenant"));
        this.allowedTenants = Set.copyOf(allowedTenants);
        this.maxPerPoll = maxPerPoll;
    }

    @Scheduled(fixedDelay = 1000)
    public void tick() {
        try { poll(); }
        catch (RuntimeException failure) {
            // Do not log JDBC/Kafka exception strings that can contain connection configuration or content.
            LoggerFactory.getLogger(WarehouseOutboxRelay.class).warn("Warehouse relay pass failed; durable intent retained; inspect database/connection health");
        }
    }

    int poll() {
        if (TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Relay publishing must run outside a database transaction");
        int acknowledged = 0;
        for (int i = 0; i < maxPerPoll && !Thread.currentThread().isInterrupted(); i++) {
            // Claim incrementally, so slow sends do not consume another queued row's lease.
            var next = store.claim(worker, LEASE);
            if (next.isEmpty()) break;
            var claim = next.orElseThrow();
            if (!allowedTenants.contains(claim.tenantId())) { store.fail(claim, "TENANT_NOT_ALLOWED"); continue; }
            Message message;
            try { message = message(claim); }
            catch (RuntimeException invalid) { store.fail(claim, "INVALID_STORED_EVENT"); continue; }
            try { publisher.publish(message.key(), message.envelope()); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); break; }
            catch (Exception unconfirmed) {
                store.retry(claim, backoff(claim.attempts()), "PUBLISH_UNCONFIRMED");
                continue;
            }
            // A DB failure here deliberately propagates; no fabricated success or new event identity.
            if (store.published(claim)) acknowledged++;
        }
        return acknowledged;
    }

    static Duration backoff(int attempts) {
        if (attempts < 1) throw new IllegalArgumentException("Positive attempt count required");
        return Duration.ofSeconds(Math.min(60L, 1L << Math.min(attempts - 1, 6)));
    }

    @SuppressWarnings("unchecked")
    static Message message(OutboxClaim claim) {
        if (claim.envelope() == null || claim.envelope().getBytes(StandardCharsets.UTF_8).length > 262_144
                || !RelaySupport.PROFILE.equals(claim.payloadHashVersion())) throw new IllegalArgumentException("Invalid stored event");
        // jsonb changes whitespace/key order. Compare the existing sorted-map digest, never raw jsonb::text.
        Map<String, Object> envelope = JSON.readValue(claim.envelope(), Map.class);
        String canonical = RelaySupport.write(envelope);
        if (!RelaySupport.hash(canonical).equals(claim.payloadHash())
                || !claim.tenantId().equals(envelope.get("tenantId"))
                || !claim.eventId().toString().equals(envelope.get("eventId"))
                || !"warehouse-service".equals(envelope.get("sourceSystem"))
                || !Set.of("InventoryReserved", "InventoryRejected").contains(envelope.get("eventType"))
                || !"1.0".equals(envelope.get("schemaVersion"))) throw new IllegalArgumentException("Invalid stored event");
        String transaction = RelaySupport.text((String) envelope.get("businessTransactionId"), "businessTransactionId");
        RelaySupport.text((String) envelope.get("correlationId"), "correlationId");
        java.time.Instant.parse((String) envelope.get("occurredAt"));
        if (!(envelope.get("details") instanceof Map<?, ?>)) throw new IllegalArgumentException("Invalid details");
        return new Message(claim.tenantId() + ":" + transaction, canonical);
    }

    record Message(String key, String envelope) {
        @Override public String toString() { return "Message[content redacted]"; }
    }
}

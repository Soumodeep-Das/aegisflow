package com.aegisflow.core.correlation;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Read-only, bounded, transaction-required view. Caller must hold the shared projection gate. */
public interface DetectionProjections {
    List<Snapshot> scanAfter(String tenant, String transaction, int limit);
    record Fact(UUID id, String source, String type, String entity, long version, String orderId,
                Map<String,Integer> items, String reason, Instant occurred, Instant observed) {}
    record Snapshot(String tenant, String transaction, long version, String contract, int contractVersion,
                    String configuration, int duration, int lateness, Instant due, Instant enforceAfter,
                    Fact order, boolean orderConflict, Fact inventory, boolean inventoryConflict,
                    List<Fact> facts) {}
}

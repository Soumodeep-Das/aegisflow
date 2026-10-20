package com.aegisflow.core.eventing;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Bounded, transaction-only access to accepted intake. Callers never mutate accepted payloads.
 * The shared transaction-scoped gate coordinates local projectors and future detector freshness checks.
 */
public interface ProjectionInbox {
    boolean tryLock();
    List<AcceptedEvent> pending(int limit);
    void applied(Key key, Instant at);
    void blocked(Key key, String reason, Instant at);
    /** Pending OR permanently blocked work prevents a definitive absence evaluation. */
    boolean hasUnsettled(String tenant, String transaction);

    record Key(String tenant, String source, UUID eventId, String transaction) {}
    record AcceptedEvent(Key key, String type, String schema, Instant occurred, Instant received,
                         String payload, String hash, String hashVersion) {
        @Override public String toString() { return "AcceptedEvent[content redacted]"; }
    }
}

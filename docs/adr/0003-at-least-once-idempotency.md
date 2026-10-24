# ADR 0003: At-Least-Once Delivery and Database-Enforced Idempotency

> **Implementation evidence update — 2026-10-24:** Durable uniqueness, stable event IDs, replay and post-database-commit offset handling are implemented. At-least-once redelivery remains possible; no cross-database/Kafka exactly-once or external recovery guarantee is claimed. See [current status](../STATUS.md) and the [preserved evidence ledger](../evidence/TEST_RESULTS.md). The original decision, date, context and consequences below are unchanged.

- **Status:** Accepted
- **Date:** 2026-10-11

## Context

Kafka redelivery, relay crashes, consumer crashes, HTTP timeouts, and worker failover make duplicates normal. Broker “exactly once” features cannot guarantee exactly one external business effect across independent databases and HTTP services.

Apache Kafka's official [delivery-semantics design notes](https://kafka.apache.org/41/design/design/#message_delivery_semantics) describe at-least-once redelivery and explain that exactly-once writes to external systems require coordination with those systems. AegisFlow therefore does not extend Kafka's Kafka-to-Kafka guarantees to simulator HTTP effects.

## Decision

State the guarantee honestly: messages and action attempts are delivered **at least once**, while accepted facts and supported target actions are made idempotent through durable database constraints and stable identities.

- Consumer inbox uniqueness is `(tenantId, sourceSystem, eventId)` plus canonical payload hash.
- Projection and incident creation use deterministic keys/unique constraints and transactional compare-and-set updates.
- A recovery action receives one idempotency key at plan creation; every retry reuses it.
- Target APIs persist `(tenant, operation, idempotencyKey, requestHash)` and return the original terminal result. Same key/different hash is a conflict.
- Worker leases use increasing fencing tokens. A stale worker cannot finalize a result.
- Timeout after a call produces an unknown outcome and mandatory reconciliation before retry.

## Consequences

The system can demonstrate zero duplicate side effects for action types whose target implements this contract, but it does not promise universal exactly-once behavior. Unsupported legacy APIs must be manual-only or wrapped by a separately justified adapter. Idempotency records require retention longer than the maximum retry/replay window.

## Verification

Tests will redeliver messages, race consumers/workers, crash after target commit, expire leases, and replay a request with both identical and changed bodies. Assertions cover one fact, one active incident episode, one business effect, rejection of changed requests, and fencing of stale completion. These are future tests, not current results.

## Alternatives considered

- **Assume Kafka exactly-once solves all duplicates:** rejected; it does not encompass HTTP effects and independent stores.
- **In-memory deduplication:** rejected; it fails on restart and across replicas.
- **New idempotency key per attempt:** rejected; it defeats business-effect deduplication.

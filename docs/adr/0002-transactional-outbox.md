# ADR 0002: Transactional Outbox for Durable Kafka Events

- **Status:** Accepted for initial implementation
- **Date:** 2026-10-11

## Context

Each simulator must update its authoritative PostgreSQL state and publish a business event. A database commit followed by a failed Kafka publish would otherwise leave AegisFlow unaware; publishing first risks an event describing rolled-back state. A distributed transaction across PostgreSQL and Kafka is inappropriate for this project.

Spring Modulith's Event Publication Registry can durably track transactional application listeners. Its default asynchronous broker externalization is pragmatic, but the [2.1 event documentation](https://docs.spring.io/spring-modulith/reference/events.html) explicitly distinguishes it from an actual outbox and provides separate outbox integrations. The externally visible Kafka contract needs an immutable envelope, source-owned relay state, retry/lag observability, and the same easily inspected pattern in three independently deployed simulators.

The options were evaluated as follows:

| Option | Strength | Cost/risk for this project | Decision |
|---|---|---|---|
| Spring Modulith publication registry + default Kafka externalization | little custom code; durable record of transactional listener work; fits in-process module events | broker externalization is listener-based rather than the explicit source outbox this learning project needs; relay schema/lag behavior is less directly demonstrated | retain only for internal module events if useful; do not make it the simulator's external contract |
| Spring Modulith 2.1 `outbox` externalization integration | connects Modulith selection/routing to an actual outbox implementation | adds another integration/library whose operational behavior still needs to be learned and tested | valid future implementation alternative, but not the default for P0 |
| Explicit outbox table + small application relay | makes transaction boundary, stored envelope, claims, attempts, lag, and crash tests visible; no Kafka Connect dependency | requires relay code, cleanup, concurrency tests, and monitoring | **selected for P0** |
| Explicit outbox + Debezium CDC | mature CDC relay and strong separation from application publisher | adds Kafka Connect/Debezium infrastructure and operational scope | defer until a measured need |

## Decision

In each simulator, write the business aggregate change and an immutable outbox row in one local transaction. A separate relay publishes the stored envelope to the source-owned Kafka topic and marks it published only after broker acknowledgement. It retries with backoff and preserves the original `eventId`.

Use an explicit outbox table for external Kafka integration. Spring Modulith may still be used for internal module event publication, but its publication registry is not the external event contract and must not create a second ambiguous delivery path.

## Consequences

- A committed change is eventually publishable after Kafka or relay recovery.
- Publication can occur more than once, so consumers require an inbox and idempotency.
- Outbox growth, poison events, relay concurrency, retention, and lag need metrics and operational handling.
- There is a delay between business commit and event visibility; deadline logic uses `occurredAt`, while lateness handling records ingestion time.

## Required implementation properties

- Unique `(tenant_id, event_id)` and immutable canonical payload/hash.
- Safe concurrent relay claim using row locking or compare-and-set; relay crash cannot lose the row.
- Publish attempts, next-attempt time, last error code, created/published timestamps, and lag metric.
- Contract validation before commit where feasible; invalid stored records move to an operator-visible failure state rather than being silently skipped.
- Integration test: commit while Kafka is unavailable, restart relay/broker, observe eventual event with unchanged ID; also test crash around acknowledgement to produce a harmless duplicate.

## Alternatives considered

- **Best-effort publish after commit:** rejected because it loses events.
- **Publish before commit:** rejected because it can emit nonexistent state.
- **Kafka/PostgreSQL distributed transaction:** rejected for complexity and misleading external exactly-once claims.
- **Change-data capture:** viable later, but deferred because it adds infrastructure and does not remove contract/outbox design work.

This decision does not reject Spring Modulith. Modulith remains the control-plane module-boundary and internal-event tool; the explicit outbox is the simulator-to-Kafka durability contract.

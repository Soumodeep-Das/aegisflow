# ADR 0008: P0.2 persistence primitives and database roles

- **Status:** Accepted implementation choices within approved P0.2; verification gate pending
- **Date:** 2026-10-13
- **Complements:** ADRs 0001, 0002, 0003, 0004, and 0007; historical decisions are preserved

## Decision and scope

Use four separate existing PostgreSQL instances/databases, one per deployable. Each database has a bootstrap administrator, a non-superuser migration role owning its `aegisflow` schema, and a runtime role with explicit table/column privileges. Flyway uses a separately configured connection; the application's DataSource uses the runtime identity. Core receives only its own credentials. `scripts/db/bootstrap-roles.sql` is repeatable on existing volumes; it creates/updates roles and privileges without deleting business data. Run it explicitly after Compose starts, rather than relying on fresh-volume init scripts. Existing bootstrap users/passwords and named volumes are preserved.

Core V1 records an independent migration history and schema ownership boundary without speculative business tables. Its inbox/projections/incident model is deferred to the milestones that implement those behaviors.

The implemented simulator primitives are internal services: create a `CREATED` order; record one synthetic initial `AUTHORIZED`/`DECLINED` payment; record one initial `RESERVED`/`REJECTED` reservation. Their state, lines where applicable, request replay record, and complete outbox envelope are one local transaction. There are no business HTTP endpoints or external payment provider calls. Initial outcomes are synthetic inputs to persistence, not event choreography or recovery operations. Future transitions must extend these boundaries with their own tests.

## IDs, amounts, versions, and fingerprints

- Aggregate IDs are caller-assigned UUIDs. Tenant, order references in downstream services, business transaction, correlation, and request keys are opaque bounded strings. A downstream order reference deliberately has no cross-database foreign key.
- New outbox rows receive random UUID event/outbox IDs. A committed replay record returns its stored event/resource IDs. UUID order has no correctness meaning. Event UUIDs are additionally unique inside each owner's database; probabilistic UUID generation supplies cross-owner uniqueness, not a shared global registry.
- Money is `BigDecimal`/PostgreSQL `numeric(19,4)`, positive and bounded to fit the column. Application validation accepts supported ISO 4217 currencies and exact currency fractional precision without rounding. JSON money is a normalized decimal string. No floating-point arithmetic or real money movement is involved.
- Hibernate `@Version Long lockVersion` starts at zero in storage. The business/event version is `lockVersion + 1`, so the first event exposes version 1. This avoids inventing Hibernate initialization semantics. Version checks prevent stale ORM writes; they do not authenticate callers or provide recovery policy.
- `aegisflow-json-v1` uses UTF-8 compact JSON, recursively sorted object keys, stable arrays, SHA-256, normalized decimal strings, and UTC time strings. SKU arrays are sorted by SKU before fingerprinting. Request digests include operation, tenant, resource identity, transaction/order, amount/items/outcome/reason as applicable; correlation is diagnostic and excluded. Per-service code remains independent. Consumer canonicalization for arbitrary received JSON and approval digests remain future work.

## Transaction and concurrency

The replay record's `(tenant_id, operation, idempotency_key)` primary key arbitrates a concurrent `INSERT ... ON CONFLICT DO NOTHING`. Under PostgreSQL READ COMMITTED the losing insert waits for the winner; the following query can read its committed result. Same key/same material digest returns the stored result. Changed content raises a conflict. If the first transaction rolls back, no replay record remains and another request may claim the key. Deferred same-tenant foreign keys allow the claim to be written before its aggregate/outbox while enforcing their existence at commit.

Spring's JPA transaction manager coordinates Hibernate and JDBC work over the same runtime DataSource. State is flushed before return; an outbox/constraint failure propagates and rolls back state, lines, replay record, and publication intent. Tests intentionally throw after an actual outbox insert to exercise rollback of both sides. No network call occurs while the transaction is open.

Outbox identity, envelope, hash, and creation time are immutable by trigger and runtime column grants. Mutable publication metadata is stored for P0.3; all current events remain `PENDING`. No relay or fake publisher is added. A row's durable presence is not evidence of broker delivery, and the FM-003/FM-004 Kafka scenarios remain unrun.

## Consequences and gate

This is a small persistence checkpoint that preserves the upcoming vertical slice. It adds no shared deployable domain model, Kafka consumer/inbox, order decision choreography, cancellation/release, recovery, Keycloak, AI, or frontend.

Composite tenant primary/foreign keys and tenant-required service/repository methods enforce storage boundaries. HTTP/workload authentication and PostgreSQL RLS remain future decisions; internal APIs must only be wired to an authenticated ingress in a later phase. Migration credentials remain available to the application startup process for Flyway, while its normal DataSource has runtime privileges; an external migration job is a future stronger separation.

Acceptance requires a Java 21 clean reactor build, real PostgreSQL migration/commit/rollback/replay/concurrency/privilege tests with zero skips, startup checks, and current continuity records. Current evidence and environment blockers are recorded in [TEST_RESULTS.md](../evidence/TEST_RESULTS.md). This decision does not declare those tests passed or authorize P0.3.

# As-built data model

Current schema is defined by the committed Flyway migrations, not the broader Phase 0 logical model. [Status](../STATUS.md) and [test evidence](../evidence/TEST_RESULTS.md) distinguish implementation from observed verification.

## Owners, roles and schema histories

Each deployable owns an independent PostgreSQL database and `aegisflow` schema. Database roles are distinct bootstrap, migrator and runtime identities. The migrator owns schema changes; runtime receives table/column-specific application grants, not schema CREATE or broad administration. Core receives no simulator credentials. Application tenant scoping and composite keys are implemented; PostgreSQL RLS is not.

| Owner | Database | Migration source |
|---|---|---|
| Core | aegisflow_core | [Core migrations](../../control-plane/src/main/resources/db/migration) |
| Order | aegisflow_order | [Order migrations](../../simulators/order-service/src/main/resources/db/migration) |
| Payment | aegisflow_payment | [Payment migrations](../../simulators/payment-service/src/main/resources/db/migration) |
| Warehouse | aegisflow_warehouse | [Warehouse migrations](../../simulators/warehouse-service/src/main/resources/db/migration) |

Do not modify an applied migration or recreate storage to reconcile a documentation statement. [ADR 0008](../adr/0008-persistence-primitives-and-database-roles.md) records identifiers, money, version and role trade-offs.

## Simulator tables

| Owner | Actual business records | Shared local pattern |
|---|---|---|
| Order | `orders`, `order_lines` | `idempotency_record`, `outbox_event` |
| Payment | `payments` | `idempotency_record`, `outbox_event` |
| Warehouse | `reservations`, `reservation_lines` | `idempotency_record`, `outbox_event` |

The initial typed operation validates material input, claims an idempotency identity, writes its aggregate/lines and appends an immutable event envelope in **one local transaction**. Same key/material returns the stored result; changed material conflicts. UUID aggregate/event identities are explicit. Event IDs are unique within each owner database; no cross-database global registry is claimed. Money uses exact numeric representation and currency-normalized decimal strings in events, not binary floating point. Optimistic storage counters and exposed business versions are distinct.

Each outbox contains tenant/outbox/aggregate/version/event identity, event type, immutable JSON envelope and digest/profile, status, creation/publication times, attempts, next retry, error code, lease owner/expiry and fencing token. Tenant-aware foreign keys connect local aggregates. `PENDING`, `PUBLISHED` and `FAILED` describe delivery, not business lifecycle. A relay's fenced completion follows broker acknowledgement; status/claim fields change, event identity/content does not.

No simulator `consumer_inbox`, `order_decisions` or cancellation-marker choreography table is implemented. Those appeared in the original design, preserved in [reference snapshots](../history/REFERENCE_THROUGH_P05.md).

## Core tables and ownership

| Migration | Module | Tables / purpose |
|---|---|---|
| V1 | infrastructure | Schema/runtime boundary; independent Flyway history |
| V2 | eventing | `inbox_message`: immutable accepted payload/provenance; `quarantine_record`: durable technical rejection |
| V3 | eventing | `inbox_projection_progress`: immutable APPLIED/BLOCKED completion |
| V3 | correlation | `observed_fact`: immutable derived fact; `business_transaction`: tenant-scoped current projection and pinned deadline |
| V4 | detection | `invariant_evaluation`: immutable snapshot/result; `detection_sweep`: durable cyclic cursor |
| V5 | detection | `incident`, `incident_timeline`, `incident_fact_reference` |

### Intake and derived state are different commits

Inbox identity is `(tenant_id, source_system, event_id)`; canonical content comparison distinguishes replay from conflicting reuse. Invalid transport input is quarantined by configured channel/topic/partition/offset and digest, without trusting a claimed tenant or storing raw rejected payload. Runtime may SELECT/INSERT intake, not rewrite it.

One later projector transaction commits the fact, current projection and APPLIED marker together. A failed projection leaves accepted intake retryable; unsupported/over-budget work can acquire a durable BLOCKED marker and remains unsettled for detection. The fact's foreign key binds tenant/source/event/transaction provenance. Current projections may update; original facts do not.

### Evaluations, episodes and timeline

Evaluations retain rule/configuration/contract versions, projection version, phase, unsettled flag, due/enforcement/evaluation times, result/reason and bounded observed facts. Their unique key includes tenant/transaction/invariant/version/projection-version/phase/unsettled. Runtime cannot UPDATE/DELETE evaluation history.

An incident has a stable persisted UUID, tenant/transaction, invariant/contract/configuration, episode key, initial reason/severity/evaluation, timestamps, `state_version` and `incident_revision`. PostgreSQL enforces one initial open episode per structured contract identity. Only `DETECTED` is currently allowed; `state_version` stays 1. Runtime may update only revision/updated time, not resolve it.

Timeline uniqueness by tenant/incident/evaluation is also its completion marker. Incident creation/revision, timeline and fact references commit together. Composite foreign keys preserve tenant AND transaction scope. A later SATISFIED evaluation appends evidence; it does not delete the original violation.

## Durable scheduling and concurrency

Projector/detector short database transactions use a shared transaction-scoped advisory gate; detection's repeatable-read pass persists its bounded cyclic cursor with evaluations. The incident recorder uses a separate gate and discovers unlinked evaluations with an anti-join. No network operation is held under these locks. They favor understandable correctness over measured parallel throughput.

Source relays instead require DB-clock leases and increasing fencing tokens because Kafka I/O occurs outside the claim transaction. Do not confuse these publication claims with future recovery-action leases.

## Planned records and retention

Authoritative evidence collection attempts, investigations, plans, approvals, action attempts, verification records, audit platform and terminal/recurring incident transitions are **not implemented**. Their original logical model remains in [reference history](../history/REFERENCE_THROUGH_P05.md) and the [planned state machine](RECOVERY_STATE_MACHINE.md).

No production retention/backup/restore policy is verified. Broker retention is seven days; that does not define safe database idempotency retention or authorize deleting evidence. Runtime append-only permissions are not administrator-proof tamper resistance.

# ADR 0011: Clock-driven immutable invariant evaluations

- **Status:** Accepted; real PostgreSQL gate passed on892ac17 (2026-10-21), see TEST_RESULTS for scope
- **Date:** 2026-10-21
- **Builds on:** ADR0010 and verified P0.4-A; no intake or projection contract replacement

## Decision

Detection imports only correlation's public DetectionProjections API and eventing's ProjectionInbox API. Correlation owns snapshot SQL/reduction; detection owns V4 invariant_evaluation and detection_sweep. No internal cross-module import, simulator dependency, inbox mutation, network call, or incident implementation is added.

A bounded cyclic scan uses a durable tenant/transaction cursor, persisted with evaluations in one REPEATABLE_READ transaction under the existing transaction-scoped advisory gate. Unlike an irreversible high-water mark, the cursor wraps: unchanged projections become due with time, and earlier or late-visible keys are revisited. Restart and rollback retain work in projections and cursor storage. An empty tail resets the cursor; it may take another callback to resume at the beginning. Scan latency scales with workload; no 5-second SLO is claimed. This simple scan is the initial demo scheduler, not an optimized large-scale priority queue.

The same snapshot and gate cover projection reading and pending/BLOCKED intake checks. Unsettled work yields UNKNOWN. Intake committed after that snapshot is later evidence, not something the detector can already know. A repeatable-read serialization failure rolls back the pass and retries next callback. No application lease/fencing scheme is needed while all effects stay in one local transaction.

Each immutable evaluation records INV-002/v1, pinned contract/config, duration/lateness, due/enforcement time, evaluatedAt, projection version, phase, unsettled flag and the exact bounded observed fact snapshot. Unique tenant/transaction/invariant/version/projection-version/phase/unsettled prevents duplicate effects across polling/concurrency/restart. Phase changes once at enforcement; later projected evidence gets another version/evaluation. Runtime may SELECT/INSERT but not UPDATE/DELETE history.

## Rule semantics

- Before enforceAfter, missing/mismatched/contradictory/late outcomes are UNKNOWN awaiting enforcement; valid on-time outcomes can already be SATISFIED.
- At or after enforceAfter, complete trusted observations with no outcome are VIOLATED MISSING_OBSERVED_OUTCOME, explicitly not proof of Warehouse operation failure.
- Exact order and SKU quantities are required for InventoryReserved. InventoryRejected with the existing OUT_OF_STOCK reason and matching order is valid without invented item fields.
- Contradictory effective Warehouse outcomes, wrong order, and wrong quantities yield distinct violations after enforcement; ambiguous/missing Order, unsupported shape or outcome-before-Order time inconsistency yield UNKNOWN.
- An outcome occurring after dueAt is late even if delivered within the lateness interval. Lateness is transport grace, applied once; it does not extend the business-operation deadline.
- An on-time event delivered after an earlier violation may make a new evaluation SATISFIED. Original violation/evaluatedAt and actual observation timestamps remain intact; this does not resolve any future incident or assert current authoritative state.

## Acceptance and scope

Focused pure tests use explicit times. PostgreSQL tests must execute real migration, projection, snapshot, uniqueness, concurrency, cursor rollback/retry, restart by worker recreation, backlog/blocked suppression and history/tenant guards without 60-second sleeps. Synthetic database-read failure is fault injection, not a measured physical database restart. Calling the production scheduled callback is not an executable-process restart test. P0.4-C is blocked until this real gate passes; C must consume immutable evaluations without losing work between evaluation and incident transactions.

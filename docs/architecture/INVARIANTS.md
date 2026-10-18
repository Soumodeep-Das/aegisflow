# Business and Safety Invariants

**Status:** Phase 0 specification. Deadlines are configurable design assumptions until product values are chosen. No invariant engine or measured result exists yet.

**P0.3-B verified subset, 2026-10-19:** the opt-in Order relay's SAFE-005 claim/completion mechanism uses DB-clock leases and tenant/owner/fencing compare-and-set. All nine real PostgreSQL relay ITs now pass on the host, proving the recorded SQL concurrency/stale-completion cases. Actual Kafka replay and inbox idempotency remain unverified; no recovery worker or invariant engine was added. Older no-worker text below is superseded only for this Order transport worker.

P0.2 adds storage guards relevant to SAFE-001/002/004 and INV-005: separate database roles, composite tenant references, stable replay/event identities, optimistic versions, and same-aggregate/version outbox uniqueness. Producer input/hash vectors and real PostgreSQL assertions passed at the 2026-10-16 host gate. This proves the recorded storage scenarios, not an invariant engine or complete tenant authentication. No invariant evaluation, authenticated API, worker, recovery or resolution implementation exists yet. See [ADR 0008](../adr/0008-persistence-primitives-and-database-roles.md) and [test evidence](../evidence/TEST_RESULTS.md).

## 1. Evaluation model

An invariant is a versioned, machine-evaluable rule with:

- a tenant and workflow-contract version;
- triggering authoritative fact(s);
- correlation keys and required entity versions;
- a due time derived from an authoritative `occurredAt` plus configured duration;
- clock-skew and maximum-lateness allowances;
- allowed outcomes and explicit `SATISFIED`, `VIOLATED`, or `UNKNOWN` result;
- evidence freshness requirements, severity, and permitted recovery classes.

The evaluator records the exact fact IDs, invariant version, configuration version, `evaluatedAt`, base `dueAt`, and `enforceAfter = dueAt + applicable lateness allowance`. It does not silently reinterpret old incidents when rules change. Until `enforceAfter`, an absent outcome is pending. The decision lateness allowance is applied exactly once; closure/cleanup have no additional allowance in this contract. Contradictory facts or insufficient authority produce an ambiguity incident and prohibit automatic recovery.

## 2. Initial `ECOM_ORDER_V1` workflow

The happy path is `OrderCreated → PaymentAuthorized + InventoryReserved → OrderConfirmed`. Payment and Warehouse decisions can arrive in either order. Allowed unsuccessful paths end in `OrderCancelled` when Payment declines or Warehouse rejects, with financial exposure handled only through an approved/manual flow in the MVP.

Suggested short demo durations make tests observable; they are not production promises:

| Parameter | Demo default proposal | Starts at |
|---|---:|---|
| `paymentOutcomeDuration` | 30 seconds | `OrderCreated.occurredAt` |
| `inventoryOutcomeDuration` | 45 seconds | `OrderCreated.occurredAt` |
| `orderClosureDuration` | 30 seconds | first unsuccessful decision, or later successful Payment/Warehouse outcome |
| `cancellationCleanupDuration` | 60 seconds | `OrderCancelled.occurredAt` |
| `eventLatenessAllowance` | 15 seconds | Payment and inventory decision deadlines |
| `evidenceMaxAge` | 30 seconds at execution | authoritative read `collectedAt` |

All values are stored in the workflow contract used for an evaluation. Tests use a controllable clock rather than sleeping.

## 3. Business invariants

### `INV-001 PAYMENT_OUTCOME_BY_DEADLINE` v1

For each `OrderCreated`, exactly one effective payment outcome—`PaymentAuthorized` or `PaymentDeclined`—must exist for the same tenant, order/business transaction, amount, and currency by the payment deadline.

- **Violation:** no outcome after allowance; both outcomes at the same effective payment version; or identity/value mismatch.
- **Authoritative verification:** Payment read API plus Order read API for amount/currency.
- **Permitted automated class:** evidence refresh and incident creation only in MVP; any financial mutation needs human approval.
- **Prohibited:** synthesizing a payment result, retrying payment authorization without a business-approved idempotency contract, or confirming the order.

### `INV-002 INVENTORY_OUTCOME_BY_DEADLINE` v1

For each `OrderCreated`, exactly one effective `InventoryReserved` or `InventoryRejected` outcome must cover the order's SKU quantities by the inventory deadline. The decision is independent of event arrival order and does not by itself permit order confirmation.

- **Violation:** missing or contradictory outcome, partial/wrong reservation, or reservation for another tenant/order.
- **Authoritative verification:** Warehouse reservation read and Order lines read.
- **Potential safe recovery:** retry a known idempotent reservation request only when authoritative state proves it was not applied, policy allows it, request identity is stable, and evidence is fresh.
- **Prohibited:** direct stock adjustment, duplicate reservation, or using an event alone as the mutation precondition.

### `INV-003 ORDER_TERMINAL_CONSISTENCY` v1

Within `orderClosureDuration` after the first unsuccessful decision, or after both successful decisions (whichever condition applies):

- authorized payment plus complete reservation allows only `OrderConfirmed`;
- declined payment allows only `OrderCancelled`;
- rejected inventory allows only `OrderCancelled`;
- a confirmed order must not coexist with a declined payment, rejected/released reservation, or mismatched amount/items.

A late correction cannot delete the incident history. It triggers fresh evidence and verification before resolution.

### `INV-004 CANCELLATION_CLEANUP_REQUIRED` v1

Within `cancellationCleanupDuration` after `OrderCancelled`, no active warehouse reservation may remain. If a payment authorization remains active, it must be durably flagged and assigned to an authorized human-controlled compensation path; assignment does not prove the funds were voided.

- **Violation:** active reservation at the deadline, or an active authorization with no valid human owner/path.
- **MVP behavior:** attempt only policy-approved idempotent non-financial cleanup; financial exposure creates a high-severity `NEEDS_REVIEW` path with human approval/manual handling and no automatic void/refund/capture.
- **Prohibited:** AI-authorized financial action, automatically treating order cancellation as proof of reservation release/payment void, or reporting financial recovery before Payment confirms state.

### `INV-005 NO_CONTRADICTORY_EFFECTIVE_TERMINALS` v1

One source aggregate version cannot assert mutually exclusive terminal outcomes. Higher-version corrections may supersede state only when the service contract explicitly permits the transition; both facts remain in history.

- **Violation:** same-version contradiction, illegal state regression, or duplicate `eventId` with altered payload.
- **Behavior:** quarantine/integrity incident and manual review; automated mutation is prohibited.

## 4. Platform safety invariants

| ID | Always-required property | Enforced by | Planned assertion |
|---|---|---|---|
| `SAFE-001` | AegisFlow never writes a simulator database | network/DB credentials and architecture test | control-plane role cannot connect or mutate; all actions appear in API audit |
| `SAFE-002` | Tenant scope is authenticated and preserved end to end | token claims, application authorization, composite DB keys, Kafka key/ACL | cross-tenant read, reference, claim, approval, and action attempts fail |
| `SAFE-003` | Each delivered event creates at most one accepted inbox fact | unique `(tenant, source, eventId)` and payload hash | repeated delivery leaves one fact; altered duplicate quarantines |
| `SAFE-004` | A logical recovery action has one stable target idempotency key | immutable action row and target idempotency store | retries/crash yield one business effect |
| `SAFE-005` | Only the current fenced worker can finalize work | lease, monotonically increasing token, optimistic version | stale worker completion update affects zero rows |
| `SAFE-006` | Unknown remote outcome is reconciled before retry | action state machine and retry guard | timeout-after-commit does not issue a second effect |
| `SAFE-007` | Action execution uses fresh authoritative preconditions | evidence expiry and target expected version | stale/mismatched version returns to investigation |
| `SAFE-008` | Approval authorizes one immutable plan/policy/evidence binding | digest, version, hash, scope, expiry, actor roles | any bound-field change invalidates readiness |
| `SAFE-009` | AI cannot authorize, execute, expand scope, or declare verified success | no credentials/tools; schema validation; deterministic policy | malicious evidence/model output cannot create an eligible action |
| `SAFE-010` | Resolution requires an independent authoritative postcondition | separate verification attempt and state guard | HTTP success/event/AI claim alone cannot set `RESOLVED` |
| `SAFE-011` | Significant transitions are attributable and append-only | transactional audit insert and restricted DB role | actor, reason, prior/new state, trace and bindings exist for each transition |
| `SAFE-012` | Unsupported/invalid input fails closed | schema/semantic validation and quarantine | incompatible schema never changes projection or reaches recovery |

## 5. Evidence sufficiency and precedence

For a recovery decision, the minimum evidence set contains the triggering facts, current Order state/version, current relevant Payment state/version, current Warehouse state/version, collection times, authenticated source identities, and hashes. Missing evidence is `UNKNOWN`, not negative evidence.

When sources disagree, precedence is:

1. a fresh authenticated authoritative state read for the source-owned entity;
2. the source's higher-version committed event;
3. lower-version/older events and control-plane projections;
4. logs, traces, operator notes, and AI interpretation.

Lower-ranked material can explain an incident but cannot override higher-ranked state. Even a fresh read can be inconclusive if the service declares eventual consistency; its consistency semantics and observed version must be recorded.

## 6. AI-related invariants

- The deterministic investigator runs and is stored as the baseline before comparing an AI investigator.
- Every AI factual claim and proposed action cites evidence IDs from the same tenant/incident; nonexistent or cross-tenant citations reject the output.
- AI input is redacted and bounded; embedded instructions in events/logs are quoted as untrusted evidence.
- A model may return `ABSTAIN`. Unavailability, parse failure, low support, or injection detection falls back to deterministic/manual processing.
- Accuracy, supported-claim rate, abstention quality, latency, and cost are evaluation metrics. They are future targets, never inferred from architecture.

## 7. Traceability and acceptance

Event fields and allowed outcomes are specified in [EVENT_CONTRACTS.md](EVENT_CONTRACTS.md). Durable data and constraints are in [DATA_MODEL.md](DATA_MODEL.md). Enforcement transitions are in [RECOVERY_STATE_MACHINE.md](RECOVERY_STATE_MACHINE.md). Fault injection scenarios should map each mechanism to at least one business or safety invariant in `../evaluation/FAILURE_MATRIX.md`.

| Invariant | PRD trace | Minimum executable assertion |
|---|---|---|
| `INV-001` | `FR-004`, `FR-005` | advance a controllable clock beyond payment deadline + grace with no decision; exactly one incident is durable; valid late permutations within grace create no false positive |
| `INV-002` | `FR-004`, `FR-005`, `AT-002` | permute reservation/payment arrival while preserving event/source versions; equivalent facts converge; missing/wrong-item outcome breaches only after deadline + grace |
| `INV-003` | `FR-004`, `FR-005` | evaluate the full Payment × Warehouse × Order terminal truth table; only authorized + reserved + confirmed and unsuccessful + cancelled combinations satisfy |
| `INV-004` | `FR-009`–`FR-012`, `AT-004` | after cancellation deadline, an active reservation or unowned financial exposure remains a violation; timeout after cleanup commit produces one effect and requires authoritative verification |
| `INV-005` | `FR-001`, `FR-004`, `AT-010` | same-version contradictory outcomes or altered duplicate payload enter quarantine/`NEEDS_REVIEW`, produce no automatic recovery call, and remain in immutable history |

At phase gates, automated tests must report factual observed counts for detection recall/false positives, latency, duplicate effects, unauthorized writes, and verified recoveries. Until those tests run, every value in this document is a rule or target—not a result.

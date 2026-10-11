# Product requirements document

**Document status:** Phase 0 baseline
**Product status:** not implemented
**Audience:** repository owner, reviewers, future coding agents
**Primary constraint:** build a rigorous, explainable portfolio system within limited assisted-development time; prefer a thin verified slice over broad incomplete code.

## 1. Product intent

AegisFlow helps an operator answer four questions about a distributed business transaction:

1. What outcome was promised and by when?
2. What trustworthy evidence shows actually happened?
3. Is the state inconsistent or merely late/ambiguous?
4. What constrained action, if any, is allowed—and did it really repair the state?

The MVP exercises those questions against simulated e-commerce Order, Payment, and Warehouse services. Its value is the demonstrable engineering of correlation, durable failure handling, evidence provenance, authorization, approval binding, idempotent action, and independent verification. AI may improve investigation quality later; it is not the authority for safety.

## 2. Users and jobs

| User | Job to be done | MVP need |
|---|---|---|
| Operations analyst | understand a stuck order without searching three databases | incident timeline, evidence links, invariant explanation |
| Approver | decide whether a proposed repair is safe | exact proposal, policy result, evidence snapshot, expiry and blast radius |
| Platform engineer | reproduce and diagnose failure handling | durable states, traces/metrics/logs, fault scenarios, replay-safe design |
| Security reviewer | prove a tenant or AI cannot bypass controls | scoped identities, tenant predicates, audit trail, deterministic policy |
| Student/recruiter | understand architecture and trade-offs | runnable thin slice, honest results, codebase guide, interview-ready rationale |

## 3. Business workflow and contract

### 3.1 Happy path

1. Order accepts a request and records `OrderCreated` in the same local transaction as an outbox row.
2. Payment consumes `OrderCreated` with a local idempotent inbox and reaches `AUTHORIZED` or `DECLINED`; its local state and outcome outbox commit together.
3. Warehouse independently consumes `OrderCreated` and reaches `RESERVED` or `REJECTED` for the requested items, using the same local inbox/state/outbox boundary.
4. Order consumes their outcome events. It becomes `CONFIRMED` only when payment is `AUTHORIZED` and inventory is `RESERVED`; it becomes `CANCELLED` when either decision fails. Warehouse consumes cancellation and releases any reservation, including a reservation that finishes after cancellation was observed.
5. AegisFlow consumes facts, correlates strictly within a tenant, and closes no incident unless fresh service-owned APIs confirm an allowed outcome.

### 3.2 Initial service-level contract

The numbers below are configurable test defaults, not a production SLA:

| Contract | Test default | Allowed terminal evidence |
|---|---:|---|
| Payment decision after `OrderCreated.occurredAt` | 30 s + 15 s late-arrival grace | `AUTHORIZED` or `DECLINED` |
| Inventory decision after `OrderCreated.occurredAt` | 45 s + 15 s late-arrival grace | `RESERVED` or `REJECTED` |
| Order terminalization after a failed decision or the successful pair | 30 s | `CONFIRMED` for the successful pair; otherwise `CANCELLED` |
| Required cleanup after cancellation | 60 s | no active reservation; authorized payment is flagged for human-controlled compensation |

Deadlines use event time plus a documented allowed-lateness policy. A missing event opens an investigation; it is not proof that the underlying action failed. Full rules and allowed combinations are in `docs/architecture/INVARIANTS.md`.

### 3.3 Example unsafe ambiguity

Warehouse receives a reservation request, commits it, and the HTTP response is lost. The caller sees a timeout. AegisFlow must not treat the timeout as failure or immediately repeat the request. It records an unknown outcome, enters `VERIFYING`, queries warehouse state using tenant/order identifiers, and either confirms the first action, safely retries with the same idempotency key when the API contract permits, or routes to `NEEDS_REVIEW`.

## 4. Scope

### 4.1 First implementation slice (P0)

- independently deployed Order, Payment, and Warehouse simulators, each with its own PostgreSQL database;
- a modular Spring Boot AegisFlow control plane with its own PostgreSQL database;
- versioned JSON events over Kafka with transactional outbox publishing and idempotent inbox consumption;
- deterministic correlation and invariant detection for one order workflow;
- one deliberate fault that yields a durable incident and an automated test;
- local Docker Compose and reproducible test commands;
- documentation and handoff required by `AGENTS.md`.

P0 detects and explains; it does not execute recovery.

### 4.2 Later, only after phase approval

- P1: deterministic recovery policy, human approval, scoped API action, concurrency control, and post-action authoritative verification;
- P2: evidence-backed Spring AI investigator, operational UI, and observability/evaluation dashboards;
- P3: additional integrations and Kubernetes or richer UI only when measurement justifies them.

### 4.3 Explicit non-goals

- real payment, customer, or production credentials/data;
- autonomous financial compensation in the MVP;
- arbitrary LLM tool use, shell/SQL execution, or policy generation;
- direct AegisFlow writes to service databases;
- a universal workflow/BPM engine, SIEM, APM, ticketing platform, or data warehouse;
- cross-region active-active correctness, formal verification, or production certification;
- universal exactly-once side effects.

## 5. Competitor-aware position

This comparison guides scope; it is not a claim that an unbuilt student project replaces mature products.

| Category/example | Established strength | AegisFlow learning focus | Deliberate response |
|---|---|---|---|
| Durable workflow engines (for example Temporal) | durable execution, retries, timers, workflow history | reconstructing business truth from independently owned systems | do not build a general engine; consider Temporal only after P1 complexity proves a need |
| BPM/process orchestration (for example Camunda) | modeled processes, human tasks, connectors | invariant breach detection and evidence-bound repair for event-driven transactions | use a small code-defined state machine first; no BPMN scope in MVP |
| Cloud workflow services (for example Step Functions) | managed orchestration and cloud integrations | locally reproducible Java/Kafka/PostgreSQL portfolio | remain cloud-neutral for the first slice |
| Incident/APM platforms | telemetry aggregation, alerts, operational collaboration | domain-level outcomes such as paid-but-unreserved | export telemetry and integrate later; do not recreate alerting/ticketing suites |
| AIOps assistants | summarization and suggested remediation | measured evidence citation, abstention, and deterministic action controls | baseline against a deterministic investigator; AI remains optional and advisory |

AegisFlow differentiates its demo through the closed loop **detect → evidence → constrained proposal → policy/approval → action → independent verification**, while keeping claims bounded to the simulated domain.

Reference points were checked against official product documentation on 2026-10-11: [Temporal durable execution](https://docs.temporal.io/temporal), [Camunda process orchestration](https://docs.camunda.io/docs/components/concepts/processes/), and [AWS Step Functions state-machine workflows](https://docs.aws.amazon.com/step-functions/latest/dg/welcome.html). The comparison above is scope positioning, not a benchmark or feature-equivalence claim.

## 6. Functional requirements

| ID | Requirement | Acceptance signal |
|---|---|---|
| FR-001 | Accept only authenticated, allowlisted, versioned business events with the required envelope. | Invalid or unsupported input is quarantined with a reason and never mutates a transaction projection. |
| FR-002 | Persist an inbox record and projection change atomically; deduplicate by database constraint within tenant/source/event identity. | Replaying the same event produces one logical effect and an observable duplicate count. |
| FR-003 | Correlate by `(tenantId, businessTransactionId)` and retain `correlationId` for tracing, never as the tenant boundary. | Identical business IDs in two tenants cannot share a projection, evidence, or incident. |
| FR-004 | Evaluate versioned invariants using event time, configured deadlines, allowed lateness, and service-owned state. | Duplicate or reordered facts converge; time-based checks use a controllable clock. |
| FR-005 | Create one active durable incident per tenant/transaction/invariant violation episode and enforce the documented transition graph. | Concurrent detection cannot create duplicate active incidents; terminal recurrence creates a linked successor; invalid transitions fail atomically. |
| FR-006 | Store evidence provenance: source, capture time, subject, schema/API version, locator, content hash, freshness, and collection outcome. | Every finding/proposal cites stored evidence; stale or failed collection is visible. |
| FR-007 | Provide a deterministic investigator that maps known evidence patterns to findings before AI is enabled. | The full P0/P1 safety test set can run with no model/network access. |
| FR-008 | Later AI investigation consumes a sanitized evidence bundle and returns a schema-constrained finding/proposal with citations, confidence, and abstention. | Unsupported citations or actions are rejected; AI failure does not block deterministic detection. |
| FR-009 | Evaluate allowlist, current state/version, risk, tenant, actor, approval need, retry budget, and action parameters in deterministic policy code. | LLM text cannot change a deny to allow; each result records policy version/hash. |
| FR-010 | Bind an approval to tenant, material incident revision, proposal/action hash, evidence snapshot hash, policy version/hash, approver, and expiry. | Any material mutation, expiry, tenant mismatch, or replay into a different execution prevents dispatch; safe retries of the same action retain their original consumed binding. |
| FR-011 | Invoke only versioned simulator APIs with scoped credentials, an idempotency key, and state-version preconditions. | Runtime AegisFlow has no simulator DB credentials/privileges; isolated test-harness inspection identities are separate; duplicate requests cannot duplicate a supported effect. |
| FR-012 | Treat timeouts as unknown and independently reconcile using an authoritative read API before retrying or resolving. | Timeout-after-success produces one side effect and a verified resolution or `NEEDS_REVIEW`. |
| FR-013 | Claim work durably so racing workers cannot execute the same approved action; leases must be recoverable after a crash. | A concurrency test observes at most one active claim and no duplicate side effect. |
| FR-014 | Record an append-only audit trail for detection, evidence, model calls, policy, approvals, attempts, responses, verification, and manual decisions. | Timeline reconstruction includes actor and correlation IDs without exposing secrets. |
| FR-015 | Enforce tenant-aware authentication/authorization at API, service, query, cache, metric, and messaging boundaries. | Cross-tenant API and crafted-event tests return no data and perform zero writes/actions. |
| FR-016 | Expose health, traces, structured logs, and metrics without high-cardinality raw tenant/order labels. | A scenario can be traced by protected correlation search; dashboards show aggregate outcomes. |
| FR-017 | Incrementally provide explicit fault injection for every `FM-*` scenario when its associated phase implements the behavior. | Each implemented mechanism is deterministic, resettable, and asserted; the journal identifies exactly which matrix subset has run. |

## 7. Nonfunctional requirements

Values here are **future acceptance targets**, not observed measurements.

| ID | Attribute | Target / constraint |
|---|---|---|
| NFR-001 | Correctness | 100% of mandatory fixtures for each implemented invariant classified as specified; at least 95% recall and at most 1% false positives on any larger declared fuzz/variation corpus; zero duplicate external effects in the required fault suite. |
| NFR-002 | Security | zero unauthorized writes or cross-tenant reads in the required suite; deny by default; secrets never logged. |
| NFR-003 | Durability | no accepted event is lost after its source transaction commits under the outbox recovery test; incident transitions survive restart. |
| NFR-004 | Detection latency | p95 under 5 s after the decisive event is consumed; deadline breaches detected within 5 s after deadline + grace in the local reference workload. |
| NFR-005 | Recovery latency | measure p50/p95 from `READY_TO_EXECUTE` to independently verified `RESOLVED`; initial target p95 under 30 s excluding separately reported human wait and injected outage duration, including ordinary retry backoff. |
| NFR-006 | Availability degradation | Kafka, AI, or one simulator outage must not corrupt state; backlog/retry/needs-review state is observable. |
| NFR-007 | Explainability | every incident names violated `INV-*`, observed facts, missing/stale evidence, and allowed next transitions. |
| NFR-008 | Maintainability | Spring Modulith module verification passes; public module APIs are documented; no cyclic domain dependencies. |
| NFR-009 | Reproducibility | a clean machine with documented prerequisites can start dependencies and run the slice using repository commands only. |
| NFR-010 | Testability | clocks, IDs, fault switches, and external clients are injectable; integration tests use isolated containers/databases. |
| NFR-011 | Observability | trace/correlation context crosses Kafka and HTTP; metric names/units are documented; audit records are durable. |
| NFR-012 | AI quality/cost | report accuracy, unsupported-claim rate, citation precision, abstention quality, latency, token use, and estimated cost on a versioned dataset. |

## 8. Cross-phase acceptance catalog

| ID | First required stage | Given / when / then |
|---|---|---|
| AT-001 | P0 | Given one valid event delivered twice, when both deliveries finish, then one inbox business effect exists and the duplicate is acknowledged/observed. |
| AT-002 | P0 | Given valid events in multiple supported orders, when projections settle, then invariant decisions and final projection are identical. |
| AT-003 | P0 | Given a source DB commit followed by publisher failure, when publishing resumes, then the outbox event eventually appears and is consumed once logically. |
| AT-004 | P1 | Given a warehouse timeout after its commit, when recovery reconciles, then no blind duplicate action occurs and authoritative state drives the result. |
| AT-005 | P1 | Given two workers racing for one approved action, when claims are attempted, then only one executes and both observe the durable outcome. |
| AT-006 | P0 reads/events; P1 actions | Given tenant A credentials and tenant B identifiers/events, when reads or actions are attempted, then access is denied/quarantined with zero tenant-B disclosure or mutation. |
| AT-007 | P2 | Given evidence containing imperative malicious text, when deterministic/AI investigation runs, then it is treated as quoted data and cannot expand tools/policy/actions. |
| AT-008 | P1 | Given an approval and then any material proposal/evidence/policy/tenant change, when execution is requested, then the approval is rejected as stale or mismatched. |
| AT-009 | P2 | Given the AI provider unavailable, when a known invariant is breached, then deterministic detection/investigation and operator routing continue. |
| AT-010 | P0 | Given an unsupported schema major/version, when consumed, then it is quarantined with replay metadata and causes no projection or recovery action. |

Detailed fault mechanisms and assertions are in `docs/evaluation/FAILURE_MATRIX.md`.

## 9. Critical assumptions and risks

- Simulator APIs can expose authoritative, tenant-scoped state and support idempotency/preconditions. This must be designed before P1.
- Source events faithfully represent committed local state. The outbox test must prove, not assume, this property.
- Event time can be skewed. Allowed lateness and a controllable test clock reduce false positives but do not solve arbitrary bad clocks.
- A single-region local demonstration cannot prove production availability, scale, or hostile multi-tenancy.
- Spring portfolio versions are compatible according to official documentation, but the first implementation (P0) dependency set still needs a generated dependency tree and smoke/integration tests.
- AI quality is dataset- and model-dependent. It must be evaluated separately from deterministic detection and may be excluded without breaking safety.
- Limited assisted-development time makes over-scoping the primary delivery risk. P0 exits only on one end-to-end tested invariant, not on service count or UI breadth.

## 10. Traceability and change control

Requirements map to `INV-*`, `FM-*`, ADRs, and future test display names. A behavioral change updates the affected contract and its tests in the same coherent milestone. Targets remain labeled until a dated, commit-specific benchmark is recorded. Unresolved product decisions live in `docs/OPEN_QUESTIONS.md`; known missing behavior lives in `docs/KNOWN_LIMITATIONS.md` rather than optimistic prose.

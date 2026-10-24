# Implementation plan

## Status and how to read this plan

Phase 0 and P0.1–P0.5 are complete within their recorded scopes. [STATUS](../STATUS.md) is the current summary; [TEST_RESULTS](../evidence/TEST_RESULTS.md) records observed commands, counts and failures. This plan describes delivery boundaries, not a new authorization or a claim that every original cross-phase requirement passed.

The [reference archive](../history/REFERENCE_THROUGH_P05.md) preserves the full pre-refinement plan, original exit specifications, intermediate checkpoints and provisional commands. In particular, simulator consumer choreography and the proposed `e2e` Maven profile are not current delivered features. Use the real scripts in the [local runbook](../LOCAL_DEPLOYMENT.md).

“Phase 0” means the initial documentation baseline. P0 is the functional detection stage, subdivided into P0.1–P0.5; P1 is a later recovery stage, not the owner's original “Phase 1” implementation approval.

## Delivery principles

1. Demonstrate one thin path through actual ownership boundaries before adding breadth.
2. Enforce correctness with deterministic rules and durable database constraints.
3. Preserve uncertain outcomes; never equate missing evidence with a failed remote effect.
4. Keep AI removable and outside authorization, policy and execution.
5. Exit through reproducible tests and the continuity protocol in [AGENTS](../../AGENTS.md).
6. Add infrastructure only when a measured limitation justifies it.

## Completed delivery and acceptance boundaries

| Milestone | Delivered | Observed acceptance boundary |
|---|---|---|
| Phase 0 | Requirements, architecture, ADRs, threat/failure catalogs, continuity documentation | Documentation review only; no runtime claim at that time |
| P0.1 | Java 21 reactor, four applications, ten Core module boundaries, foundation profiles | Build, boundary and application health checks |
| P0.2 | Four owner-managed PostgreSQL schemas; atomic business-state/outbox writes; replay keys, versions and roles | Real PostgreSQL rollback, uniqueness, replay/concurrency and migration checks |
| P0.3 | Three leased/fenced relays; authenticated Kafka topics; Core durable intake/quarantine and post-commit offset acknowledgement | Real broker/database fault and replay gates; separate packaged scheduler/Core-restart acceptance |
| P0.4-A | Immutable observed facts, tenant-scoped projections and independent durable projection progress | PostgreSQL projection/restart/backlog tests |
| P0.4-B | Versioned Clock-driven INV-002 evaluations with durable deadline discovery | Deterministic unit and PostgreSQL timing/concurrency tests |
| P0.4-C | Deduplicated DETECTED incidents, immutable evaluations and evidence timeline | PostgreSQL rollback, concurrency and separate-JVM restart tests |
| P0.4-D | Real simulator-to-incident pipeline and packaged scheduling | Healthy/missing/late/reordered/failure coverage recorded across distinct gates, not one invented combined run |
| P0.5-A | Four pinned Java 21 application images | Non-root/read-only execution, health probes, graceful stop |
| P0.5-B | Stable generated credentials, persistent owner databases and Kafka | Role/network/authentication boundaries and retained restart; persistent forbidden-write probe remains deferred |
| P0.5-C | Unified nine-service stack and safe PowerShell lifecycle | Health, boundaries, application restart and retained identity/storage |
| P0.5-D | Owner-only command fixtures, healthy/missing/late-evidence demonstration, Core/full-stack restart | Six committed events through the persistent pipeline, durable incident/history, and separate fresh 182-test reactor |

The [evidence ledger](../evidence/TEST_RESULTS.md#p05-acceptance-evidence) is authoritative for exact runs. A healthy container, a compiled test, or a skipped infrastructure test is not end-to-end acceptance. The current demonstration uses explicit service-owned business commands, not autonomous simulator-to-simulator Kafka choreography.

## Future delivery — requires separate approval

The following specifications remain design targets. They are not implemented or authorized by this documentation task. Completing P0 does not authorize P1.

## P1: deterministic safe recovery

### Required design sequence

1. Implement authoritative, tenant-scoped read APIs and versioned recovery APIs in simulators.
2. Define a typed action catalog and constrain each MVP plan to exactly one action. Start with a non-financial action such as release a reservation or reconcile/cancel an unconfirmed order. Multi-action compensation/partial-success orchestration is deferred.
3. Generate a deterministic proposal from current evidence; record its canonical hash. If the first action releases a reservation on a cancelled order, implement and test `INV-004` as the corresponding P1 invariant before dispatch is enabled.
4. Evaluate policy outside AI and persist policy version/hash and decision.
5. Require a human approval bound to the exact tenant/incident revision/proposal/evidence/policy/expiry.
6. Claim execution with a durable lease/fencing token; invoke with idempotency key and expected resource version.
7. Record request fingerprint and outcome. A timeout becomes `UNKNOWN`, then `VERIFYING`.
8. Query authoritative state through a separate client/request; only that check may yield `RESOLVED`.

### P1 exit scenarios

- timeout before service receipt;
- timeout after service success (`AT-004`);
- worker crash after external success but before local completion;
- racing workers (`AT-005`);
- stale/mutated/expired approval (`AT-008`);
- stale evidence, token expiry, policy denial, and ambiguous state;
- cross-tenant read/action (`AT-006`);
- retry budget exhausted without duplicate external effects.

All must assert incident state, action-attempt state, effect count, audit entries, and prohibited behavior—not only HTTP codes.

## P2: optional AI and operational surface

### Deterministic baseline first

Freeze a versioned evaluation dataset and deterministic investigator results before adding a model. This separates event/recovery correctness from model quality.

### AI integration

- construct a minimal evidence bundle from allowlisted structured fields;
- delimit and label service text as untrusted;
- request a strict result schema: findings, evidence citations, missing evidence, candidate action type, confidence, and abstention reason; deterministic code derives all execution parameters from authoritative evidence;
- reject unknown citations/actions/fields and re-run deterministic policy;
- log model/provider/version, prompt-template version, evidence hash, latency, usage, validation outcome, and cost estimate without secrets;
- implement outage, timeout, malformed output, malicious log, and stale evidence cases.

### Operational UI and telemetry

A small read-focused Next.js interface may show incident queue, evidence timeline, proposal/policy, approval, attempts, and verification. The control-plane API remains the authorization boundary. OpenTelemetry traces connect Kafka intake, investigation, API action, and verification; Prometheus/Grafana show low-cardinality aggregate metrics.

P2 exits only when the deterministic path still passes with AI disabled and `AI_EVALUATION.md` contains observed, reproducible dataset results labeled by commit/model/configuration.

## P3: decision-driven options

Candidates—not commitments:

- Temporal if home-grown recovery scheduling/state complexity becomes unsafe or obscures the learning goal;
- Redis only for a measured cache/rate-limit/coordination need, never as the sole correctness store;
- pgvector only if evaluated retrieval over a meaningful evidence corpus beats simple structured selection;
- Kubernetes only after Compose is stable and deployment behavior itself becomes the learning objective;
- additional business systems/invariants only after the first path remains understandable and tested.

Each addition requires an ADR with measurements, alternatives, operational cost, removal path, and updated failure scenarios.

## Test strategy by risk

| Layer | Fast feedback | Boundary proof | Fault/e2e proof |
|---|---|---|---|
| Event schema | serialization fixtures, compatibility rules | producer/consumer contract tests | incompatible-version quarantine |
| Invariants/state | table-driven unit/property tests, fake clock | JPA/Flyway constraints | duplicates, reorder, delayed/missing facts, restart |
| Messaging | key/topic/handler tests | Kafka Testcontainer | outage, redelivery, poison record, backlog recovery |
| Persistence | aggregate/transition tests | PostgreSQL Testcontainer | outbox gap, claim race, lease expiry, crash recovery |
| Security | policy/claim unit tests | Keycloak/API integration | cross-tenant, expired token, confused deputy, tampered approval |
| Recovery API | request fingerprint/idempotency tests | simulator HTTP + DB observation | timeout before/after success, duplicate request, stale precondition |
| AI | schema/citation validators | provider stub/recorded fixture | outage, malformed result, prompt injection, stale/insufficient evidence |

# Implementation plan

**Current milestone status,2026-10-20:** P0.3 verified and stopped. Final packaged acceptance on `860dbf9` passed1/1,0 failures/errors/skips at19:17:57IST; earlier clean134 and Core50/source-relay gates remain passed. No repeat commands required. P0.4 requires explicit approval.

## Historical continuation notes

Earlier dated status notes below are preserved history, superseded by this completion. The phase sections describe current boundaries and future acceptance.

**Current final gate,2026-10-20:** packaged scheduling/Core restart assertions ran on the host, but its final Flyway-history inspection used the wrong role and caused1 error. Test-only repair uses Core migrator for metadata and explicitly checks runtime cannot read it. Resume only `verify-p03-final.ps1 -PackagedOnly`; clean134 tests remain passed. Close P0.3 only after an actual complete packaged PASS; P0.4 remains unapproved.

**Final acceptance continuation,2026-10-20:** source-relay/Core50 gates and the final clean reactor134 tests passed. The outer verifier then rejected Boot's normalized JAR timestamp before the packaged case started. Repaired freshness/content checks pass6 regressions and all4 existing artifact pairs. Run `scripts/verify-p03-final.ps1 -PackagedOnly` for the sole remaining scheduled pipeline/Core process restart case; no rebuild is needed for this script-only repair. Only after actual live PASS/documentation close P0.3; P0.4 requires approval.

**Current continuation,2026-10-20:** source-relay B is verified; Core durable intake C and end-to-end D scenarios are implemented with36 unit/boundary/port passes. Run `scripts/verify-core-inbox.ps1` for14 actual Core migration/pipeline cases, then record results/fix first failure and finish final reactor/packaged scheduling acceptance. Docker denial caused all local live discoveries to skip; no complete P0.3 claim. Older source checkpoints below are historical.

**Latest continuation, 2026-10-20:** all three source-relay gates now passed; Payment/Warehouse on `c887490` passed40+24 tests with no failures/errors/skips. Proceed to Core inbox C and full end-to-end D. No owner command or repeat of completed B gates needed. Older checkpoints below are history; packaged scheduling/final acceptance remain.

**Planning horizon:** preserve a useful, teachable repository even if assisted coding stops after 6–7 days.
**Current authorization:** P0.2 verified; P0.3 approved. P0.3-A infrastructure and Order's27default+17PostgreSQL host gate passed. The3 real Order DB→Kafka delivery/fault tests also passed on `a4a66de` at2026-10-19 20:57:40IST. Next: remaining source relays B, Core inbox C, final end-to-end D.
**Next approval gate:** stop after P0.3; P0.4 and product P1 recovery are not authorized. New remote pushes/deployments are not authorized.

**Exact continuation checkpoint (2026-10-19):** Payment/Warehouse relays now implemented;38 new unit+2 boundary tests passed,24 DB/Kafka ITs compiled but skipped locally. Next run `scripts/verify-simulator-relays.ps1` on host for40+24 zero-skip results. Then Core durable inbox/quarantine with trusted source/tenant binding and post-commit offsets, then one actual simulator→Core end-to-end demonstration/fault replay and final reactor. Order's real gate passed3/0/0/0; no repeat merely to resume. Manual polls do not replace packaged/scheduled startup verification. Core remains unwritten; P0.3 incomplete.

## Delivery principles

1. Build one thin path through real boundaries before adding breadth.
2. Prefer deterministic behavior and database constraints over timing assumptions.
3. Make uncertain outcomes explicit states, not exceptions hidden in logs.
4. Keep simulator ownership real: separate deployment and database per service.
5. Keep AI removable. Detection, policy, approval, execution, and verification must work without it.
6. Every phase exits through tests plus the continuity protocol in `AGENTS.md`.
7. Defer Redis, Temporal, Kubernetes, pgvector, and extra services until a measured limitation justifies one.

## Phase map

Naming note: **Phase 0** was the documentation kickoff. The owner's **Phase 1 approval** authorized the first implementation milestone, named **P0.1** below. **P0/P1/P2/P3** are product-priority stages; product **P1 recovery** is later than the initial implementation foundation.

| Phase | Outcome | Not included | Exit gate |
|---|---|---|---|
| Phase 0 — architecture baseline | internally consistent, reviewable specification and continuation point | application source, containers, deployment, observed runtime metrics | all required documents exist; links/scenarios/terms reconcile; local documentation commit; clean status |
| P0 — functional detection slice | one event-driven order path produces a durable correlated incident under a fault | recovery execution, AI, production UI | build and all tests pass; outbox/inbox/idempotency and one invariant demonstrated; docs/handoff updated |
| P1 — deterministic safe recovery | policy + approval + idempotent API action + authoritative verification | automatic financial compensation, LLM authority | timeout-after-success, racing worker, stale approval, crash-after-effect, and tenant tests pass |
| P2 — evidence-backed assistance and operations | optional AI investigator, operational read UI, telemetry and evaluation | arbitrary tools, broad workflow designer | deterministic baseline retained; AI dataset results reported; prompt-injection tests pass; dashboard traces a scenario |
| P3 — earned expansion | additional invariant/integration or deployment sophistication based on evidence | speculative service sprawl | decision ADR cites measured need; previous suites remain green |

## Phase 0 checklist (completed baseline)

- [x] Establish product, scope, workflow, functional/nonfunctional requirements, and measurable acceptance tests.
- [x] Define system/module boundaries, trust boundaries, deployment ownership, and end-to-end flows.
- [x] Define event envelope, domain events, ordering/compatibility/idempotency rules.
- [x] Define durable data, evidence provenance, incident/action state machines, and concurrency rules.
- [x] Define invariants, deadlines, and allowed outcomes.
- [x] Threat-model tenants, credentials, approvals, APIs, evidence, Kafka, AI, and audit.
- [x] Define required fault mechanisms, assertions, and quantitative evaluation metrics.
- [x] Record ADRs for modular control plane, outbox, delivery semantics, API recovery, AI boundary, and version pinning.
- [x] Persist roadmap, learning guide, limitations, open questions, devlog, and handoff.
- [x] Complete final cross-document/local-link/Mermaid-source review and record factual results (rendering unavailable, explicitly recorded).
- [x] Create a coherent local commit; baseline `2bae1f3`, with a following continuity checkpoint; no push.

## P0: functional detection vertical slice

### P0.1 Repository and build foundation

**Status:** implemented and verified on Windows/Java 21; PostgreSQL execution remains environment-dependent and unverified. See [test evidence](../evidence/TEST_RESULTS.md) and [ADR 0007](../adr/0007-foundation-profiles-and-stage-boundaries.md).

Delivered:

- root Maven Wrapper 3.3.4 scripts with Maven 3.10.0 distribution checksum and an aggregator/parent POM;
- four independent deployables agreed in ADR 0001: Core, Order, Payment, Warehouse;
- Java 21 release/enforcer, BOM-managed dependencies, and automated artifact/package/module boundary checks;
- exactly ten explicit Core Modulith modules, each denying cross-module dependencies in the foundation;
- `.gitignore`, `.env.example` with blank password placeholders, loopback health, and foundation/postgres profiles;
- four separate PostgreSQL Compose services, networks, and volumes with a pinned image digest;
- 14 passing default automated tests, standalone packaged-JAR startup checks, and an optional PostgreSQL Testcontainers check that reports unavailable Docker as a skip;
- actual classes/paths, execution instructions, a debugging exercise, and five additional answered interview questions in the codebase guide.

Actual P0.1 layout:

```text
aegisflow/
├── pom.xml
├── mvnw, mvnw.cmd, .mvn/
├── control-plane/
├── simulators/
│   ├── order-service/
│   ├── payment-service/
│   └── warehouse-service/
├── compose.yaml
├── scripts/verify-startup.ps1
└── docs/
```

Contract fixtures remain future work; no shared domain-model module was introduced.

P0.1 acceptance evidence:

- Windows `mvnw.cmd --version` reported Java 21 and Maven 3.10.0; Wrapper 3.3.4 and the distribution checksum are committed separately.
- Root `clean verify` passed; a later `verify` with caller `SPRING_PROFILES_ACTIVE=postgres` also passed because every Spring context test explicitly selects `foundation`. Both runs executed 14 tests, zero failures/errors/skips. A fresh-machine checkout rehearsal has not been performed.
- Dependency tree and effective POM commands succeeded with Boot 4.1.1/Modulith 2.1.1 and their intentional managed library versions.
- Modulith verification and module-set/dependency assertions passed; all four standalone packaged applications returned HTTP 200/health `UP`.
- Compose configuration validation passed. Optional PostgreSQL integration execution was skipped because Docker was unavailable, so database readiness is not claimed.
- Commands, failures, results, learning notes, and continuation state are recorded. No Kafka, AI, recovery, or business persistence was added.

### P0.2 Service-owned persistence and outbox

**Implemented and verified, 2026-10-16.** Four independent V1 migrations, role bootstrap/profile separation, and internal Order-create/initial Payment-outcome/initial Warehouse-outcome transactions are present. The final clean root host reactor passed 38 default tests and 29 PostgreSQL ITs with zero failures/errors/skips, followed by four packaged foundation health checks and zero-issue docs validation. The report aggregation correction is checked independently against all four actual XML reports. [ADR 0008](../adr/0008-persistence-primitives-and-database-roles.md) freezes the minimal schema, identifiers, money, version, and digest choices. Preserve the implementation; no repeat build is required to record completion. P0.3 was subsequently approved; its separate persistent-Compose prerequisite passed on 2026-10-17. Continue only its unfinished tasks; P0.4 requires approval.

Implement the smallest state transitions needed for the event choreography in SYSTEM_ARCHITECTURE.md. Each simulator gets its own Flyway history and PostgreSQL database/schema ownership. Business-state mutation and resulting outbox row commit together; consumed-event inbox joins this transaction when consumers arrive in P0.3. Publication will be asynchronous and replayable; actual Kafka processing remains P0.3, not P0.2.

Acceptance:

- a PostgreSQL integration test proves rollback leaves neither state nor outbox row;
- a committed-outbox/repeated-command test preserves stable IDs and pending publication intent without another state effect; no fake publisher is added; the real Kafka commit-then-publisher-failure scenario (`FM-004`) is completed with P0.3;
- event IDs and aggregate/business IDs remain stable across publish retries;
- runtime AegisFlow receives no simulator DB credentials or privileges; test-harness DB inspection identities are isolated and never passed to the application;
- event JSON matches `EVENT_CONTRACTS.md` fixtures.

### P0.3 Kafka delivery and control-plane inbox

**Implemented and verified,2026-10-20.** P0.3-A infrastructure, all3 owner relays, Core durable validated inbox/quarantine, fault/replay/concurrency/isolation checks and actual packaged scheduling/Core process restart passed. Final evidence combines the clean134-test reactor and later1-test packaged gate with earlier source/Core suites; no single combined-run total is claimed. Local SASL/PLAIN has no TLS. Preserve evidence and stop before P0.4.

Use `(tenantId, businessTransactionId)` as the Kafka key for per-transaction partition ordering, while remaining correct when producers or topics create reordering. Reuse the ten-module Core's existing `eventing` package. Persist validated accepted inbox receipt in one Core transaction, deduplicated by `(tenantId, sourceSystem, eventId)` and canonical content. The owner's explicit P0.3 request supersedes the older projection wording here: correlation/projection/detection stays P0.4. When those consumers arrive, their derived effects must join the relevant local inbox transaction, not a best-effort later dual write.

P0.3 must enforce a trusted source/tenant mapping or block untrusted input; a payload tenant is not authenticated by topic ACL alone. Relay claims need short local transactions, expiry/fencing, bounded batches/backoff and post-Kafka-ack status updates. A broker acknowledgement followed by a relay crash can produce a duplicate with the same event ID. No cross-PostgreSQL/Kafka exactly-once guarantee is promised.

Acceptance for the owner's approved P0.3 intake scope:

- simulator state/outbox → Kafka → durable Core inbox, duplicates/reordering-safe receipt, restart/replay and bounded invalid-event handling are verified; broader `AT-001`, `AT-002`, `AT-010`, `FM-001`, `FM-002`, and `FM-012` business/projection outcomes remain subject to their later phase rather than being claimed wholesale;
- offset acknowledgement occurs only after durable handling/quarantine;
- poison/unsupported events have bounded validation and durable quarantine inspectable in Core's database; an operator UI/replay workflow is deferred;
- payload limits are enforced; dedicated metric labels/observability are not introduced in this milestone.

### P0.4 Correlation, clocked invariant, and incident

Create a tenant-scoped transaction projection and implement `INV-002` for a deliberately missing warehouse outcome first. Use an injected clock and explicit deadline scheduler. Create incidents with a uniqueness constraint and append evidence references. Other invariants remain documented backlog until separately tested.

Acceptance:

- happy path reaches no false incident;
- the chosen fault deterministically creates one incident naming its `INV-*` rule;
- duplicate and reordered facts converge;
- restart does not lose deadline work or incident state;
- the incident timeline is explainable from stored data.

### P0.5 Packaging, fault scenario, and preservation

Provide Docker Compose for Kafka, separate PostgreSQL databases, and the four applications. One command starts infrastructure/apps; one runs the verification suite. Prefer health-based waits to sleeps.

P0 exit commands (final spelling may differ and must be corrected in docs):

These POSIX examples use `./mvnw`; on the owner's Windows/PowerShell machine use `.\mvnw.cmd` with the same arguments. Wrapper execution is verified on Windows; POSIX requires `unzip` and remains unverified. The `e2e` profile and application/Kafka Compose deployment below are future P0.5 deliverables, not current runnable commands. Current P0.1 commands are in the evidence/learning documents.

```text
./mvnw verify
docker compose config --quiet
docker compose up --build
./mvnw -P e2e verify
docker compose down
```

The milestone is incomplete until the development journal contains actual exit codes/counts, the codebase guide names real classes/paths and traces the scenario, five new interview Q&As exist, and `docs/HANDOFF.md` is current.

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

## Suggested 6–7 day focus

This is a prioritization aid, not a promise:

| Day | Focus | Preservation checkpoint |
|---|---|---|
| 1 | approve P0; build skeleton and module verification | local green build + codebase map |
| 2 | one simulator mutation/outbox and event fixture | transaction/outbox tests + ADR update |
| 3 | remaining minimal simulators and Kafka publication | contract tests + Compose notes |
| 4 | inbox, projection, deterministic invariant | duplicate/reorder tests + flow explanation |
| 5 | fault scenario and durable incident | end-to-end evidence + failure matrix result |
| 6 | security/observability polish, clean-machine rehearsal | exact commands, results, bugs, handoff |
| 7 | buffer; start P1 only if P0 gate is fully green | coherent local commit; no speculative half-phase |

If time collapses, finish and document the smallest working path. Do not start UI, AI, Kubernetes, or extra invariants while the P0 gate is incomplete.

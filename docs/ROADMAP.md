# Prioritized roadmap

**Last reviewed:** 2026-10-20

**Latest gate repair:** host Payment run on `b78a2d3` failed12/12 with Flyway default-port connection errors; Warehouse was not run. Four new relay/Kafka ITs now override both datasource and migration URLs with their container URL. Both modules compile; the repaired live gate is still pending. Rerun the same focused verifier before Core inbox work; do not repeat completed Order tests.

**Observed product state:** P0.2 and P0.3-A infrastructure are complete. Order's27default+17PostgreSQL host tests passed; now all3 real Order→Kafka delivery/fault tests also passed on `a4a66de`,2026-10-19 20:57:40IST,0 failures/errors/skips. Earlier startup/port failures remain in TEST_RESULTS. Payment/Warehouse relays and Core inbox are unfinished. P0.3 is incomplete; P0.4 remains unapproved.

**Current first unfinished gate:** Payment/Warehouse relays are now implemented;38 new unit+2 boundary checks passed,24 live ITs skipped in this restricted session. Run `scripts/verify-simulator-relays.ps1` on the Docker-capable host; require40+24 actual passes and no skips. Do not repeat the completed Order suite. Then implement Core durable inbox/quarantine with trusted topic/source/tenant binding, database uniqueness/canonical duplicate comparison and post-DB-commit offsets. Prioritize one complete actual simulator→outbox→Kafka→Core inbox demonstration and failure/replay cases, then final reactor. Core is still unwritten. Packaged scheduling remains separate from manual-poll tests; P0.3 incomplete.

**Final P0.2 result:** the full clean reactor succeeded at 19:45:47 IST (2:17), 38 default + 29 real PostgreSQL tests with zero failures/errors/skips, followed by four packaged foundation JARs returning HTTP 200/UP and a zero-issue documentation check. The report counter's incomplete glob is corrected using all four real XML reports; no business code changed after verification. The separate persistent-Compose prerequisite subsequently passed on 2026-10-17. Earlier dated failures below remain historical.

**Latest 2026-10-16 result:** six Core default tests passed and PostgreSQL started; JDBC bootstrap then failed on the host legacy timezone alias. Both Maven test JVMs now explicitly use UTC, and two image/timezone regression tests passed. Rerun the same host persistence gate; real migrations/atomicity remain pending.

**2026-10-16 host update:** Docker and five Core default tests now passed on the owner's host. The persistence run then failed before PostgreSQL startup at Testcontainers' digest-image compatibility guard. All four image declarations were fixed without changing the pin, and one Core initialization regression passed locally. The single next task is rerunning the same host persistence script, rather than reinstalling Docker or repeating implementation.

**Local checkpoints:** verified P0.1 source `cb7bfaf` and continuity `c2c93e3` preserved; P0.2 source checkpoint `14e04f6`, fixes through `7a90c57`, evidence checkpoint `66282a9`, followed by this local closing commit. Resolve current HEAD in Git. No push or external deployment occurred.

## Completed — Phase 0

The documentation baseline is committed locally at `2bae1f3`, with 25 Markdown files, six ADRs, 16 complete failure scenarios, and ten interview Q&As. Reproducible documentation checks report zero issues; runtime metrics remain unmeasured. Mermaid source review is complete but rendering is unverified. See the journal/handoff for exact commands and review limits.

## Completed — P0.1 foundation

The owner approved P0.1 and resumed it after a pause. Four independent Spring Boot 4.1.1 applications compile and package with Maven 3.10.0 through Wrapper 3.3.4 on Java 21. The latest root verification ran 14 tests with zero failures, errors, or skips. All four packaged JARs separately returned HTTP 200 and health `UP` in the infrastructure-free `foundation` profile; the check then stopped only its own processes. Core's ten Modulith 2.1.1 modules are verified, and deployables cannot depend on one another's artifacts.

Four service-owned PostgreSQL configurations and a digest-pinned Compose definition exist. Compose configuration validation passed, but the Docker engine was unavailable: the optional PostgreSQL integration test was **skipped**, not passed. No database or business workflow has been demonstrated. See [test evidence](evidence/TEST_RESULTS.md), [the codebase guide](learning/CODEBASE_GUIDE.md), and [ADR 0007](adr/0007-foundation-profiles-and-stage-boundaries.md).

## Completed — P0.2 service-owned persistence and outbox

Four V1 histories, separate migration/runtime roles, and minimal internal simulator state/replay/outbox operations are implemented. The Java 21 full clean reactor and real PostgreSQL migration/atomicity/replay/concurrency/constraint/locking/privilege suites passed with zero skips. Packaged foundation startup and documentation checks passed. Do not repeat completed implementation or architectural review. Earlier sandbox failures remain recorded separately from host successes.

The role design is implemented in bootstrap SQL/Compose/profile configuration; the real PostgreSQL suites exercise privileges. Core receives only its own runtime/Flyway credentials. Local credentials remain ignored environment configuration. Persistent Compose bootstrap/helper/existing-volume behavior was separately verified on the host on 2026-10-17, not inferred from Testcontainers. All outbox events remain durable pending publication intents; no Kafka delivery is claimed.

The final integrated root invocation passed 38 default and 29 PostgreSQL tests. Compose parsing and script syntax also passed locally. The outer cross-PowerShell quoted command emitted an initial `Stop` error; the actual build/startup/docs checks nevertheless completed successfully. Future instructions avoid nested command quoting. See evidence/handoff for the exact distinction and reporting correction.

## Next — P0 functional detection slice

The single highest-value outcome is one reproducible flow from a simulator's committed state, through durable event delivery and idempotent ingestion, to a correlated invariant incident.

Priority order:

1. **P0.3-A prerequisite complete:** real isolated fresh/restarted-volume Compose bootstrap/roles/JAR/history checks passed on the owner's host, 2026-10-17. Do not repeat merely to resume; coding-session pipe denial is separate.
2. **P0.3-A complete:** the real Kafka host gate passed on 2026-10-17, including readiness, provisioning twice, both listener routes and explicit auth/ACL rejection. SASL/PLAIN is local-development-only and unencrypted; tenant provenance still needs explicit enforcement before intake. Ordinary persistent-stack restart and business crash/retry tests are not covered by this smoke.
3. **P0.3-B in progress:** Order-only source and database gate passed27default+17PostgreSQL tests on the host. Real DB→Kafka fault tests and enabled-profile startup are next, then Payment/Warehouse equivalents. Do not infer business delivery from the standalone infrastructure smoke or the acknowledged test publisher.
4. **P0.3-C:** existing Core `eventing` validation, durable inbox uniqueness and technical quarantine; offset acknowledgement after committed acceptance/quarantine only. No projection/invariant engine yet.
5. **P0.3-D:** real end-to-end/failure tests, metrics where feasible, learning/evidence/handoff and final local commit; then stop for P0.4 approval.
6. **Unapproved P0.4:** correlation/projection, one clock-driven invariant and durable incident/evidence timeline.
7. Broader packaging/observability and recovery remain later work; preserve working thin slices rather than expand scope.

P0 is not complete with compilation alone; its exit gate is in `docs/roadmap/IMPLEMENTATION_PLAN.md`.

## Then — P1 deterministic recovery

1. Authoritative state APIs and a non-financial, idempotent recovery API.
2. Typed proposal and deterministic deny-by-default policy.
3. Approval bound to proposal/evidence/policy/tenant/expiry.
4. Durable claim/lease/fencing and action-attempt journal.
5. Unknown-outcome reconciliation and independent verification.
6. Required concurrency, crash, timeout, token, stale-evidence, and tenant tests.

## Later — P2 evidence-backed assistance and operations

1. Freeze deterministic investigator baseline and evaluation dataset.
2. Add schema-constrained, citation-checked, abstaining Spring AI investigator.
3. Run injection, outage, stale-evidence, quality, latency, token, and cost evaluation.
4. Add a small read-focused incident dashboard and approval experience.
5. Complete OpenTelemetry/Prometheus/Grafana scenario trace and dashboards.

## Parked until evidence justifies them

- Temporal or another durable workflow engine;
- Redis;
- pgvector/RAG;
- Kubernetes;
- multiple control-plane microservices;
- sophisticated Next.js design system;
- additional domains or real third-party integrations.

## Definition of roadmap truth

- A checkbox or “done” claim requires a commit-specific test result in the development journal.
- Targets are not results.
- Work that is started but not exit-gated remains in progress and is described in `docs/HANDOFF.md`.
- Reorder this file when risk or evidence changes; do not preserve a stale plan for appearance.

# Prioritized roadmap

**Last reviewed:** 2026-10-16
**Observed product state:** P0.1 remains the last fully verified milestone. Each owner's P0.2 PostgreSQL suite now passes on the host; final integrated reactor and packaged-startup acceptance remain pending.

**Latest progress:** source `7a90c57` passed the Warehouse-only host build at 19:39:12 IST: 10 default + 8 PostgreSQL tests, no failures/errors/skips. Core/Order/Payment previously passed 21 PostgreSQL tests, so all 29 now pass across separate runs; all four V1 migrations applied. The Warehouse proxy defect is verified fixed. Next is one final integrated persistence run and packaged startup, without repeating implementation. Earlier dated results below remain historical.

**Latest 2026-10-16 result:** six Core default tests passed and PostgreSQL started; JDBC bootstrap then failed on the host legacy timezone alias. Both Maven test JVMs now explicitly use UTC, and two image/timezone regression tests passed. Rerun the same host persistence gate; real migrations/atomicity remain pending.

**2026-10-16 host update:** Docker and five Core default tests now passed on the owner's host. The persistence run then failed before PostgreSQL startup at Testcontainers' digest-image compatibility guard. All four image declarations were fixed without changing the pin, and one Core initialization regression passed locally. The single next task is rerunning the same host persistence script, rather than reinstalling Docker or repeating implementation.

**Local checkpoints:** verified P0.1 source `cb7bfaf` and continuity `c2c93e3` preserved; P0.2 source checkpoint `14e04f6`, followed by the handoff documentation continuity commit. P0.2's full gate is pending. No push or external deployment occurred.

## Completed — Phase 0

The documentation baseline is committed locally at `2bae1f3`, with 25 Markdown files, six ADRs, 16 complete failure scenarios, and ten interview Q&As. Reproducible documentation checks report zero issues; runtime metrics remain unmeasured. Mermaid source review is complete but rendering is unverified. See the journal/handoff for exact commands and review limits.

## Completed — P0.1 foundation

The owner approved P0.1 and resumed it after a pause. Four independent Spring Boot 4.1.1 applications compile and package with Maven 3.10.0 through Wrapper 3.3.4 on Java 21. The latest root verification ran 14 tests with zero failures, errors, or skips. All four packaged JARs separately returned HTTP 200 and health `UP` in the infrastructure-free `foundation` profile; the check then stopped only its own processes. Core's ten Modulith 2.1.1 modules are verified, and deployables cannot depend on one another's artifacts.

Four service-owned PostgreSQL configurations and a digest-pinned Compose definition exist. Compose configuration validation passed, but the Docker engine was unavailable: the optional PostgreSQL integration test was **skipped**, not passed. No database or business workflow has been demonstrated. See [test evidence](evidence/TEST_RESULTS.md), [the codebase guide](learning/CODEBASE_GUIDE.md), and [ADR 0007](adr/0007-foundation-profiles-and-stage-boundaries.md).

## Now — finish verification of the existing P0.2 checkpoint

The owner approved P0.2 and allowed stopping at a meaningful checkpoint. Code now includes four V1 histories, separate migration/runtime roles, and minimal internal simulator state/replay/outbox operations. Continue from these actual paths rather than repeating the design review or implementation. The highest-priority task is the Java 21 clean reactor plus real PostgreSQL migration/atomicity/replay/concurrency/privilege gate with zero skips. This session's sandbox blocked global Maven-cache writes, Maven Central DNS, and the Docker engine pipe; successful host Docker use does not prove sandbox access. See evidence/handoff for exact attempts and commands.

The role design is implemented in bootstrap SQL/Compose/profile configuration; the real PostgreSQL suites now exercise privileges. Core receives only its own runtime/Flyway credentials. Local credentials remain ignored environment configuration. Persistent Compose bootstrap remains unexecuted. P0.2 is not complete until its final gate passes; P0.3 remains unapproved.

Latest per-module host evidence totals 38 default tests and 29 PostgreSQL tests across separate runs, all passing with zero skips. These are not one successful integrated root invocation. Compose parsing and script syntax passed locally. Earlier sandbox loopback/cache/Docker failures remain in the evidence history; they do not invalidate the new host results. Finish the final normal host gate before adding new code.

## Next — P0 functional detection slice

The single highest-value outcome is one reproducible flow from a simulator's committed state, through durable event delivery and idempotent ingestion, to a correlated invariant incident.

Priority order:

1. Verify the existing P0.2 source on Java 21 and real PostgreSQL; correct any actual migration/transaction defects.
2. Review the recorded gate/evidence and obtain P0.3 approval.
3. Kafka event contract fixtures and publisher recovery.
4. Control-plane inbox uniqueness, tenant correlation, projection, and schema quarantine.
5. One clock-driven invariant and durable incident/evidence timeline.
6. Duplicate, reorder, Kafka outage, commit-before-publish, and incompatible-schema tests.
7. Compose rehearsal, observability minimum, learning guide, journal, and handoff.

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

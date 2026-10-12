# Prioritized roadmap

**Last reviewed:** 2026-10-12
**Observed product state:** P0.1 foundation implemented and verified on Java 21; business functionality is not implemented.

**Local source checkpoint:** `cb7bfaf`, followed by the documentation continuity checkpoint described in the handoff. No push or external deployment occurred.

## Completed — Phase 0

The documentation baseline is committed locally at `2bae1f3`, with 25 Markdown files, six ADRs, 16 complete failure scenarios, and ten interview Q&As. Reproducible documentation checks report zero issues; runtime metrics remain unmeasured. Mermaid source review is complete but rendering is unverified. See the journal/handoff for exact commands and review limits.

## Completed — P0.1 foundation

The owner approved P0.1 and resumed it after a pause. Four independent Spring Boot 4.1.1 applications compile and package with Maven 3.10.0 through Wrapper 3.3.4 on Java 21. The latest root verification ran 14 tests with zero failures, errors, or skips. All four packaged JARs separately returned HTTP 200 and health `UP` in the infrastructure-free `foundation` profile; the check then stopped only its own processes. Core's ten Modulith 2.1.1 modules are verified, and deployables cannot depend on one another's artifacts.

Four service-owned PostgreSQL configurations and a digest-pinned Compose definition exist. Compose configuration validation passed, but the Docker engine was unavailable: the optional PostgreSQL integration test was **skipped**, not passed. No database or business workflow has been demonstrated. See [test evidence](evidence/TEST_RESULTS.md), [the codebase guide](learning/CODEBASE_GUIDE.md), and [ADR 0007](adr/0007-foundation-profiles-and-stage-boundaries.md).

## Now — stop at P0.1 and prepare the P0.2 gate

Do not start P0.2 without new owner approval. The highest-priority preparation is a working local Docker engine, then rerunning the PostgreSQL test so the persistence milestone starts from real infrastructure evidence. Use Java 21 via `JAVA_HOME`; the host's Java 26 is outside this repository's enforced baseline. No additional Maven installation is needed because the wrapper pins Maven.

P0.2 must design separate migration/runtime database roles before business persistence uses the current local bootstrap owners. Core must receive only its own database credentials. Local credentials belong in ignored environment files and must never be sent in chat or committed.

## Next — P0 functional detection slice

The single highest-value outcome is one reproducible flow from a simulator's committed state, through durable event delivery and idempotent ingestion, to a correlated invariant incident.

Priority order:

1. Obtain P0.2 approval and prove PostgreSQL connectivity with Docker available.
2. Design restricted database roles, then add service-owned Flyway migrations and one transactional state/outbox path.
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

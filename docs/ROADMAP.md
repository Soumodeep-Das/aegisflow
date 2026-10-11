# Prioritized roadmap

**Last reviewed:** 2026-10-11
**Observed product state:** documentation only; no runtime exists.

## Now — close Phase 0

1. Finish cross-document consistency and local-link checks.
2. Confirm all mandatory fault scenarios and safety boundaries are traceable.
3. Record factual review results in the journal/handoff.
4. Commit the documentation baseline locally. Do not push.
5. Request explicit approval for the first implementation milestone.

## Next — P0 functional detection slice

The single highest-value outcome is one reproducible flow from a simulator's committed state, through durable event delivery and idempotent ingestion, to a correlated invariant incident.

Priority order:

1. Java 21/Maven/Spring Boot multi-deployable foundation and Modulith boundary test.
2. Separate simulator databases, Flyway migrations, and one transactional outbox path.
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

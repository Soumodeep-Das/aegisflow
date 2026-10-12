# Known Limitations

**Status:** P0.1 foundation; business runtime remains planned

**Last reviewed:** 2026-10-12

This project is intentionally a production-oriented **student portfolio project**, not an enterprise-ready transaction-recovery product. This list separates designed intent from demonstrated capability and must be updated at every significant milestone.

## 1. Current P0.1 reality

- Four application shells, Maven Wrapper, Java 21 enforcement, health endpoints, ten Core module declarations, and architectural boundary tests exist. The latest root build ran 14 tests with zero failures, errors, or skips; four packaged JARs returned HTTP 200 and health `UP`. See [test evidence](evidence/TEST_RESULTS.md) for dates, commands, counts, and earlier failures.
- Boot 4.1.1 and Modulith 2.1.1 dependency resolution, compilation, testing, and foundation-profile startup were verified. Spring AI is deliberately absent; its Phase 0 candidate remains deferred until P2.
- Default `foundation` startup excludes JDBC/Flyway configuration. Its health `UP` proves process readiness only, not database connectivity, business correctness, authentication, or event durability.
- Four PostgreSQL configurations and Compose services exist, but no Docker engine was available. Compose syntax validation passed; the PostgreSQL Testcontainers test was skipped. PostgreSQL-profile application startup and real database connectivity remain unverified.
- No business tables/migrations, business APIs, event producer/consumer, UI, recovery workflow, model integration, or external deployment exist. No business fault scenario, security scan, benchmark, or AI evaluation has run.
- POSIX wrapper execution was not tested on this Windows host; it requires `unzip` for the checksum-pinned Maven ZIP. Windows wrapper execution was verified. The packaged-startup helper currently targets Windows/PowerShell.
- Business architecture controls, state transitions, metrics, and thresholds remain specifications or future targets, not observed product results.
- Documentation may still contain open design decisions; see [OPEN_QUESTIONS.md](OPEN_QUESTIONS.md).

## 2. Scope limitations

- The first implementation covers simulated Order, Payment and Warehouse services, not real commerce, banking, ERP, logistics or regulated integrations.
- The first vertical slice should implement one narrow business invariant and one safe, non-financial recovery action. It will not prove a general recovery platform.
- No automatic refund, charge, transfer or other financial compensation is allowed in the MVP.
- A sophisticated Next.js UI, Kubernetes, multi-region operation, Redis, Temporal, pgvector and additional microservices are deferred until justified by a measured requirement.
- Local Docker Compose and Testcontainers configuration exists, but successful container execution remains an outstanding check. It will demonstrate reproducibility on declared hardware, not production scalability or availability.

## 3. Distributed-systems limitations

- Kafka delivery and internal processing are designed for at-least-once behavior. Duplicates remain possible and must be made harmless with database uniqueness and idempotency.
- AegisFlow cannot guarantee universal exactly-once external business effects. That property requires cooperating APIs with durable idempotency and authoritative status/state queries.
- Network timeouts cannot distinguish “request never arrived” from “effect committed but response was lost.” Such outcomes require VERIFYING and may end in NEEDS_REVIEW.
- Event absence is not proof that a business event did not happen. Kafka outage, publication delay, schema rejection or producer failure can leave evidence incomplete.
- Kafka ACLs authorize a principal to a topic; they do not inspect payload `tenantId`/`sourceSystem`, and consumed records do not expose a trustworthy original-producer identity. Shared-topic tenant provenance therefore needs an additional trusted binding or stronger isolation that is not yet selected or implemented.
- Ordering is only meaningful within explicitly chosen keys/partitions. Cross-topic/global arrival order is not guaranteed.
- Contract deadlines, allowed lateness, clock skew and evidence freshness are domain configuration that remain to be selected and tested.
- A completely compromised or incorrect authoritative simulator API can make independent verification wrong; AegisFlow does not create truth beyond its sources.
- Backup/restore, Kafka replay boundaries, disaster recovery and long-running partition behavior are not yet designed or tested end to end.

## 4. Recovery limitations

- The AegisFlow runtime will have no simulator-database credentials or privileges, including read-only access; all state reads and recovery writes use versioned owning-service APIs. A separately scoped test-harness identity may inspect databases only for assertions, and this separation is not yet implemented or verified.
- Integrations that lack stable idempotency keys, optimistic preconditions and authoritative queries cannot safely support automatic recovery.
- RESOLVED means the configured invariant was confirmed by an authoritative read; it does not prove every downstream consequence or business obligation is correct.
- Human approval reduces but does not eliminate error. An authorized human can approve a poor plan; approval must therefore remain bound to evidence, policy, parameters and resource version.
- Racing-worker, crash-after-success and stale-approval behavior is specified but unproven until the failure matrix passes.
- Some ambiguity is intentionally not automated. NEEDS_REVIEW is a safety result, not necessarily a system defect.

## 5. Security and tenancy limitations

- The threat model is a design document; Keycloak, RBAC, tenant filters, Kafka ACLs, TLS, least-privilege database roles, redaction, and business audit controls are not implemented yet. P0.1 exposes only loopback-bound health; do not expose the foundation publicly.
- Compose requires local passwords and assigns a separate bootstrap owner per database. These privileged local owners are not a finished runtime-role design; restricted migration/runtime roles and privilege assertions are required in P0.2 before business persistence.
- Application-layer tenant authorization and tenant-aware database constraints are mandatory but unimplemented. PostgreSQL row-level security is an optional defense-in-depth decision, not a current requirement or capability; a single local deployment cannot establish strong hostile multi-tenancy.
- Malformed records may lack a validated tenant or event ID. The broker-coordinate/digest-based technical quarantine, protected payload handling, replay authorization, and distinction between security signals and business incidents remain unimplemented.
- Canonicalization profiles and shared test vectors for event, request, and approval digests remain open. Until they are implemented, byte-format differences or inconsistent normalization could break deduplication or approval binding.
- The project does not protect against a fully compromised host, identity-provider administrator, database administrator, authoritative service, build pipeline or dependency.
- Local developer secrets and sample credentials will not be appropriate for shared or public deployment.
- Audit logs are not inherently immutable against privileged infrastructure administrators. Tamper-evident or external archival is out of MVP scope unless later justified.
- Data classification, retention, deletion, backup encryption, privacy review and regulatory obligations are not complete. Use synthetic data only.
- Dependency/image vulnerability posture changes over time; a passing scan is dated evidence, not a permanent guarantee.

## 6. AI limitations

- No model or provider is selected. Spring AI integration is roadmap work, not a current feature.
- AI output can be plausible, biased, inconsistent, prompt-injected or factually wrong even when it cites evidence.
- Prompt instructions cannot secure a model. Safety depends on minimal privileges, curated evidence, strict schemas, deterministic policy/authorization, human approval where required, and independent verification.
- Hosted-model use can expose minimized input to a third party and incur nondeterministic latency, availability, retention and cost. Provider terms require explicit review.
- The proposed accuracy, abstention, latency, token and cost thresholds are unmeasured future gates.
- The synthetic evaluation corpus will have coverage and labeling limits. Strong results on it will not establish performance on unseen enterprises or adversaries.
- The deterministic investigator is the required baseline and fallback. If AI adds no measured value or fails safety gates, explain-only mode or no AI is an acceptable outcome.

## 7. Evaluation and performance limitations

- The [failure matrix](evaluation/FAILURE_MATRIX.md) is a test plan only; every result currently remains “not run.”
- Proposed detection/recovery latency applies to a future declared local environment and excludes separately reported contract waiting, injected outage duration and human approval time.
- Small synthetic datasets can produce unstable recall and false-positive estimates. Future reports must publish counts, denominators and confidence limitations.
- Live model outputs may change across dates even with a stable model name. Evaluations must record provider/model, prompt, date and repetitions.
- Zero duplicate effects and zero unauthorized writes are release gates for the tested scenarios, not proof across all possible failures.
- No load, soak, chaos, penetration, accessibility or usability testing exists yet.

## 8. Operational and learning limitations

- There is no operations team, support rotation, incident-response process, SLO/error budget, capacity plan, upgrade plan or production runbook.
- Observability has not been implemented. Planned Prometheus/Grafana/OpenTelemetry components do not imply useful dashboards or trace completeness.
- Documentation and learning guides can drift from code. The continuity protocol requires updates at each milestone, but later contributors must enforce it.
- Comments and JavaDoc will explain non-obvious invariants and concurrency behavior; they are not a substitute for executable tests and current architecture documentation.

## 9. Claims the project must not make

Until supported by dated reproducible evidence, do not claim:

- “production ready,” “enterprise safe,” “exactly once,” “zero downtime,” or “secure multi-tenant”;
- guaranteed root-cause identification or safe autonomous recovery;
- measured AI accuracy, cost savings, latency or reliability;
- compliance with PCI DSS, GDPR, SOC 2 or another regulatory/control framework;
- support for real financial recovery.

Future milestones should convert a limitation to a demonstrated capability only by linking the implementing paths, exact test/benchmark command, factual result, environment and remaining boundary.

# Known Limitations

**Status:** P0.2 host-verified; P0.3 approved/incomplete; P0.3-A persistence and Kafka infrastructure host gates passed

**Latest Kafka checkpoint, recorded 2026-10-18:** the owner's 2026-10-17 real run passed, project `aegisflow-kafka-verify-ff292985977442009ee4e3fd306c28a6`, actual report Passed=true/Stage=complete. Broker readiness, provisioning twice and both routes' topic/metadata, delivery/offset and selected auth/ACL checks passed. Source-owned outbox relay and durable Core inbox remain unimplemented. Ordinary `start-kafka.ps1` reuse, Kafka restart durability and broker-outage/crash tests remain unverified; smoke success is not full P0.3 completion.

**Completed 2026-10-17 persistence result:** complete host persistence rehearsal passed: eight postgres-profile health gates, eight owner SQL-boundary groups, 24 sibling-login denial attempts and four unchanged sentinel/full-history snapshots across repeated bootstrap/restart. Actual report is Passed=true/Stage=complete for `aegisflow-p03-verify-93dcfedb932a45a1b5d28241d4c5098b`. This supersedes earlier partial/failed attempts without erasing them. It is not a Kafka/exactly-once proof, a production security audit or another Maven atomicity run. Preserve all verification volumes; no persistence rerun/cleanup is required now.

**Last reviewed:** 2026-10-17

This project is intentionally a production-oriented **student portfolio project**, not an enterprise-ready transaction-recovery product. This list separates designed intent from demonstrated capability and must be updated at every significant milestone.

## 1. Current P0.2 reality

- **Historical 2026-10-17 P0.3-A failure, superseded by the latest successful host gate:** the real host initialized/bootstrapped all four isolated databases, but failed before the first Java startup at the original loopback port parser. Raw port output was not retained. Internal-only Docker networking is a likely cause, consistent with upstream Moby reports, not a host-proven packet diagnosis. The repaired harness uses same-JAR verification containers on the existing owner-internal networks and in-container health; `internal: true`, database roles, V1 migrations and application code are unchanged. The image is pinned from official Docker Hub metadata. Its pull and complete real startup/SQL/retained-volume restart rehearsal subsequently passed on the host on 2026-10-17, as recorded above; this is distinct from the coding session's infrastructure-free helper/probe checks. Old failed-run volumes are retained. Do not rerun the old published-port approach or delete evidence to conceal the failure.

- **Current execution limit:** this coding session cannot access Docker's engine pipe; earlier Java loopback access was also restricted, despite successful owner-host persistence and Kafka gates. New live relay/inbox tests must run where Docker is accessible; none is passed by the infrastructure smoke. No application Java/SQL migration or application Maven dependency changed in P0.3-A; the separate smoke project uses Boot-managed Kafka clients4.2.1. P0.2's recorded38+29 successes remain historical. Verification volumes are retained. Filtered child environments and tested grants are not a security sandbox against a compromised JVM/administrator.

- Final P0.2 host evidence: full clean reactor **BUILD SUCCESS at 2026-10-16 19:45:47 IST**, **2:17**, entry `66282a9`/application source `7a90c57`; **38 default + 29 real PostgreSQL tests**, zero failures/errors/skips. All four V1 migrations and the recorded atomicity/replay/constraint/privilege assertions passed. All four packaged foundation JARs returned HTTP 200/UP and docs had zero issues. The report helper's incomplete `tests=4` summary is corrected against actual XML; the outer cross-PowerShell command emitted an initial quoting error, but independent checks all passed. No host rerun is needed for closing records. Persistent Compose bootstrap and retained-volume rehearsal passed separately on 2026-10-17; this does not relabel the historical Maven result as a new run.

- Historical host failures (resolved by later runs): a run passed 37 default tests and 21 Core/Order/Payment PostgreSQL ITs but Warehouse context startup failed because its Repository adapters were final. Making them subclassable and adding a focused proxy regression enabled the successful Warehouse run above. An earlier run started PostgreSQL and passed six Core default tests, then JDBC rejected the host JVM timezone alias. Test forks now use UTC and two configuration regressions passed. Manual postgres-profile launches still require the documented JVM UTC option; test settings do not change production JVM defaults.

- Historical image setup failure (2026-10-16, resolved by later runs): Docker/Ryuk and five Core default tests passed, then PostgreSQL IT failed at static image compatibility validation before database startup. Four IT declarations now explicitly mark the unchanged digest reference compatible with postgres; its no-Docker initialization regression also passed. The later PostgreSQL suites supersede the old pending-migrations status without erasing this failure record.

- P0.1's four application shells, Maven Wrapper, Java 21 enforcement, health endpoints, ten Core module declarations, and architectural tests are preserved. Its historical successful build ran 14 tests and its four packaged JARs answered health `UP`; those results do not validate the new persistence changes. See [test evidence](evidence/TEST_RESULTS.md) for each dated run.
- Order, Payment, and Warehouse now have source for local transactional business-state, durable idempotency, and immutable outbox publication-intent writes. Core has its own independent V1 schema-boundary migration, without future inbox/projection/incident tables. Application entry points and actual classes are explained in [the codebase guide](learning/CODEBASE_GUIDE.md).
- The current dependency baseline remains Boot 4.1.1/Modulith 2.1.1, with JPA/Hibernate in the simulators and Boot's Flyway starter/PostgreSQL module. Compatibility and behavior claims depend on the latest build/test record. Spring AI remains absent and deferred until P2.
- The first resumed offline reactor run on 2026-10-13 ended at 00:39:17 IST: Core's four architecture tests passed, and its HTTP smoke test errored with sandbox loopback `connect: Permission denied`. The reactor stopped before simulator compilation/tests. Later successful runs are recorded separately in the evidence document; this failed run remains a failure.
- Historical focused checks on 2026-10-13 passed 21 actual pure validation/fingerprint/event-fixture tests and 4 Core architecture tests. Four Core PostgreSQL ITs skipped because the Docker pipe was denied; uncached JPA dependencies and Maven Central DNS blocked simulator compilation. Compose configuration and script syntax passed. Later host execution compiled the modules and passed real PostgreSQL suites, resolving those earlier execution blockers; session Docker access must still be assessed independently from the user's PowerShell.
- Default `foundation` startup suppresses database/JPA auto-configuration and selects no persistence components. Its health `UP` proves process readiness only. `postgres` selects real owning-database connections, migrations, and Hibernate schema validation. Host test contexts and all four packaged foundation JARs started successfully. The same packaged JARs also passed the separate 2026-10-17 persistent Compose postgres-profile rehearsal inside owner-network Java 21 containers, not through host-published database ports; direct host-to-internal-network connections were not verified.
- The four databases' independent Flyway migrations, runtime grants, transaction rollback/commit, concurrency, and constraints passed the final full host Testcontainers run. The separate 2026-10-17 Compose rehearsal passed real role bootstrap, fresh migrations and retained disposable-volume restart/history checks. Neither result validates unrelated existing developer volumes, password rotation, backup/restore or general persistent deployment operations.
- No business HTTP endpoints, event publisher/consumer, UI, recovery workflow, model integration, or external deployment exists. Database-focused failure tests are written; they have not yet demonstrated the broader business failure matrix. No security scan, benchmark, or AI evaluation has run.
- POSIX wrapper execution was not tested on this Windows host; it requires `unzip` for the checksum-pinned Maven ZIP. Windows wrapper execution was verified. The packaged-startup helper currently targets Windows/PowerShell.
- Business architecture controls, state transitions, metrics, and thresholds remain specifications or future targets, not observed product results.
- Documentation may still contain open design decisions; see [OPEN_QUESTIONS.md](OPEN_QUESTIONS.md).

## 2. Scope limitations

- The first implementation covers simulated Order, Payment and Warehouse services, not real commerce, banking, ERP, logistics or regulated integrations.
- The first vertical slice should implement one narrow business invariant and one safe, non-financial recovery action. It will not prove a general recovery platform.
- No automatic refund, charge, transfer or other financial compensation is allowed in the MVP.
- A sophisticated Next.js UI, Kubernetes, multi-region operation, Redis, Temporal, pgvector and additional microservices are deferred until justified by a measured requirement.
- The final root Testcontainers build executed successfully on the host across all four deployables. Its report helper now checks all four explicit report directories and rejects missing/empty/error/failed/skipped suites. Persistent Compose bootstrap and the retained disposable-volume rehearsal also passed on 2026-10-17. Recorded local results demonstrate only their environment and scenarios, not production scalability or availability.

## 3. Distributed-systems limitations

- Kafka delivery and internal processing are designed for at-least-once behavior. Local broker configuration, topic provisioning and a transport smoke client are implemented but have not passed live host verification. The single broker/controller and replication factor one offer no high availability or spare replica. The local idempotency/outbox code and PostgreSQL uniqueness tests exist; relay/inbox and duplicate handling across later message and external-effect boundaries remain unfinished.
- AegisFlow cannot guarantee universal exactly-once external business effects. That property requires cooperating APIs with durable idempotency and authoritative status/state queries.
- Network timeouts cannot distinguish “request never arrived” from “effect committed but response was lost.” Such outcomes require VERIFYING and may end in NEEDS_REVIEW.
- Event absence is not proof that a business event did not happen. Kafka outage, publication delay, schema rejection or producer failure can leave evidence incomplete.
- Source-topic and Core read/group ACLs are now defined in `scripts/kafka/provision.sh`; runtime enforcement is pending the real smoke test. Kafka ACLs authorize a principal to a topic; they do not inspect payload `tenantId`/`sourceSystem`, and consumed records do not expose a trustworthy original-producer identity. Shared-topic tenant provenance therefore needs an additional trusted binding or stronger isolation that is not yet selected or implemented.
- Ordering is only meaningful within explicitly chosen keys/partitions. Cross-topic/global arrival order is not guaranteed.
- Contract deadlines, allowed lateness, clock skew and evidence freshness are domain configuration that remain to be selected and tested.
- A completely compromised or incorrect authoritative simulator API can make independent verification wrong; AegisFlow does not create truth beyond its sources.
- Backup/restore, Kafka replay boundaries, disaster recovery and long-running partition behavior are not yet designed or tested end to end.

## 4. Recovery limitations

- The application configurations provide each deployable only its owning database's runtime/migration connection. AegisFlow Core has no simulator-database credentials or direct persistence path. Bootstrap owners and IT connections inspect databases for administration/assertions. Real PostgreSQL privilege assertions passed in the separate host suites; they establish the tested grants, not authenticated cross-service APIs or hostile multi-tenant isolation. Future state reads/recovery writes must use authenticated owning-service APIs.
- Integrations that lack stable idempotency keys, optimistic preconditions and authoritative queries cannot safely support automatic recovery.
- RESOLVED means the configured invariant was confirmed by an authoritative read; it does not prove every downstream consequence or business obligation is correct.
- Human approval reduces but does not eliminate error. An authorized human can approve a poor plan; approval must therefore remain bound to evidence, policy, parameters and resource version.
- Racing-worker, crash-after-success and stale-approval behavior is specified but unproven until the failure matrix passes.
- Some ambiguity is intentionally not automated. NEEDS_REVIEW is a safety result, not necessarily a system defect.

## 5. Security and tenancy limitations

- Keycloak, RBAC, authenticated tenant ingress, TLS and business audit controls remain unimplemented. Kafka configuration now defines distinct broker/admin/source/Core SASL principals and default-deny source-topic/Core-group ACLs; their actual authentication/authorization behavior awaits host verification. SASL/PLAIN over SASL_PLAINTEXT is unencrypted and restricted to synthetic local development. Verification tooling redacts generated secrets, but this is not a complete product redaction system. The only application HTTP route is loopback-bound health; persistence methods are trusted Java-call boundaries, not authenticated business APIs. Do not expose the foundation publicly.
- Compose requires twelve private owner/migrator/runtime passwords across four databases. `scripts/db/bootstrap-roles.sql` and per-service migrations define restricted runtime grants, separate migration ownership, and immutable outbox intent; ephemeral PostgreSQL privilege assertions passed. Persistent Compose bootstrap and restricted-role assertions passed on 2026-10-17 using fresh synthetic credentials and disposable retained volumes, not the owner's private `.env` or unrelated developer data. Applications still receive their own migration credentials for startup; separating those into a deployment migration job is future operational work.
- Tenant-keyed entities, lookups, unique keys, and foreign keys are implemented in source. They prevent some accidental cross-tenant persistence links; they do not authenticate caller-supplied tenant values. Application-layer tenant authorization remains future work. PostgreSQL row-level security is an optional defense-in-depth decision, not a current capability; this local deployment does not establish strong hostile multi-tenancy.
- Malformed records may lack a validated tenant or event ID. The broker-coordinate/digest-based technical quarantine, protected payload handling, replay authorization, and distinction between security signals and business incidents remain unimplemented.
- Simulator request/event canonicalization now uses the explicit `aegisflow-json-v1` profile with module-local unit vectors. These implementations need ongoing cross-service compatibility checks; a changed profile requires deliberate migration/replay behavior. Future approval digests, adversarial input coverage, and external integration normalization remain unimplemented.
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

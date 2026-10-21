# Prioritized roadmap

**Current C repair checkpoint,2026-10-21:** host21-test run had20 passes and one separate-JVM failure: PostgreSQL rejected the child's Asia/Calcutta timezone. Test launcher now pins UTC;3 launcher regressions pass, production code unchanged. Run only `scripts/verify-incidents.ps1 -RestartOnly` for the unfinished1-test gate. Do not repeat the20 passed cases. C is not complete; D follows only after actual restart success. Prior pending/21-test commands below are historical.

**Current P0.4-C checkpoint,2026-10-21:** A/B remain verified. C durable incident/timeline implementation has10 focused policy/architecture passes;21 database checks skipped due Docker access denial. Run `scripts/verify-incidents.ps1` for the pending17 C +4 Core PostgreSQL checks. No permanent .env or dedicated Compose apps needed. C/D and full P0.4 are not complete; no P0.5/recovery/AI/push. Earlier checkpoints below are history.

**Latest checkpoint,2026-10-21:** P0.4-A's real PostgreSQL gate passed on `2b2e885`:12 projection +4 Core migration/startup tests, zero failures/errors/skips. Earlier43 focused tests passed separately. This supersedes the pending/owner-command statements below; no rerun is needed. Next approved work is B clock-driven INV-002 with backlog freshness and restart proof, then C incidents and D full integration. P0.4 overall is incomplete. See HANDOFF/TEST_RESULTS for exact saved evidence.

**Current status,2026-10-20:** P0.3 is implemented and verified. Final packaged scheduled pipeline/Core process restart passed1/1 on `860dbf9` at19:17:57IST,0 failures/errors/skips. Clean reactor134 and earlier Core50/source-relay/infrastructure evidence remain passed across their separate runs. No verification rerun is needed now.

**Next authorized action,2026-10-21:** P0.4 is approved. A facts/projection code is implemented; 43 focused tests passed, 16 PostgreSQL checks skipped (Docker pipe denied). Run `scripts/verify-projection.ps1` on the host and resolve failures before B clock-driven INV-002. Then prove B scheduler/backlog freshness, implement C durable incident/evidence, and D full pipeline/failure verification. A is not verified and P0.4 is not complete. Recovery/AI and remote pushes remain unapproved.

## Historical checkpoint notes

The dated intermediate statuses below preserve the path to completion; their retry commands and incomplete claims are superseded by the current status above.

**Current next gate,2026-10-20:** host packaged-only run on `c0e5423` passed archive checks and reached the final assertion after delivery/Core restart; it errored reading Flyway history with the runtime account. The test now asserts that restriction and inspects history with the migration account. Rerun only `verify-p03-final.ps1 -PackagedOnly`; require1 pass/0 failures/errors/skips. Clean134 and Core50/source-relay evidence remain valid. P0.3 stays open; earlier entries below record preceding checkpoints.

**Next gate,2026-10-20:** final clean reactor/JAR packaging on `7d8f473` passed134 tests,0 failures/errors/skips. A verifier timestamp error stopped before the packaged test; Boot's normalized executable dates are now handled using original-archive freshness and matching application content. Six regression groups and all4 actual artifact pairs pass. Run only `scripts/verify-p03-final.ps1 -PackagedOnly` for the actual scheduled pipeline/Core process restart case. Core50/source-relay results remain passed. P0.3 remains open until final live PASS; P0.4 unapproved. Older checkpoints below are history.

**Current continuation,2026-10-20:** combined Core50-case gate passed across `c96dc5c` (36+10) and `42b247d` (4 normal startup checks,11:45:16IST). Do not rerun those completed live gates merely to resume. Next: final clean default reactor and packaged scheduled intake/Core process restart acceptance; P0.3 incomplete. Older checkpoints below are historical.


**Last reviewed:** 2026-10-20

**Latest gate passed:** Payment/Warehouse host verification on `c887490`,2026-10-20 10:57:43IST:40 unit/boundary +24 real PostgreSQL/Kafka tests passed, zero failures/errors/skips. The Flyway URL repair is live-verified. Earlier failure is retained in TEST_RESULTS; no repeat needed.

**Observed product state:** P0.2/P0.3-A and all three source-relay gates passed. Core durable intake and the actual three-owner pipeline test are now implemented;36 unit/boundary/port tests pass, live Core gate is pending. P0.3 incomplete; P0.4 unapproved.

**Current first unfinished gate:** real Core pipeline passed10/10 and36 unit/boundary/port checks passed on `c96dc5c`. Four ordinary Core startup checks failed from test-only simulator migrations on the classpath. Verifier phases are now isolated; run `scripts/verify-core-inbox.ps1 -CoreStartupOnly` for only those4 checks, then final reactor/packaged scheduling acceptance. Do not repeat the passed pipeline for this script-only fix. P0.3 incomplete.

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

## Completed P0.3; next proposed P0 functional detection slice

The single highest-value outcome is one reproducible flow from a simulator's committed state, through durable event delivery and idempotent ingestion, to a correlated invariant incident.

Priority order:

1. **P0.3-A prerequisite complete:** real isolated fresh/restarted-volume Compose bootstrap/roles/JAR/history checks passed on the owner's host, 2026-10-17. Do not repeat merely to resume; coding-session pipe denial is separate.
2. **P0.3-A complete:** real Kafka readiness, repeated provisioning, both listeners and explicit auth/ACL rejection passed. Local SASL/PLAIN remains unencrypted; Core independently enforces configured source/tenant trust.
3. **P0.3-B complete:** Order, Payment and Warehouse owner-local relays passed PostgreSQL/Kafka commit, retry, outage/restart and stable-ID ambiguous-completion tests. Publication metadata commits only after successful broker acknowledgement.
4. **P0.3-C complete:** Core `eventing` validates bounded schemas/identities/source/tenant mappings, commits immutable inbox or quarantine, and then acknowledges offsets. Database uniqueness handles concurrent duplicates; fault/replay scenarios passed.
5. **P0.3-D complete:** real three-source pipeline/failure tests, clean134-test reactor and separately packaged scheduled intake/Core process restart passed; learning/evidence/handoff preserved. Dedicated metrics/load/replicated-failover and operator quarantine UI remain documented limitations.
6. **Approved P0.4:** complete the A database gate, then B scheduler, C incident/evidence and D integration gates, in that order.
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

# Prioritized roadmap

2026-10-23 database verifier repair: host run on740201b failed Core before any completed database pass/restart; retained .cache/p05b-db/6258f061279843eda909221ee008acec/results.json has Passed=false and empty Passes. Repeated infrastructure/role/topic bootstrap succeeded. Probe used loopback TCP, which can match initdb trusted-local HBA rules and is unsuitable for proving deployed password authentication. Changed PGHOST to the explicit owner service DNS address and added allowlisted FAIL_STAGE evidence without printing raw potentially private errors. No pg_hba, role, credential or storage changes. Root cause of the prior generic failure remains unconfirmed; no database pass claimed. PowerShell parse and whitespace checks pass. Next rerun verify-development-databases.ps1; B/C remain incomplete. Following local checkpoint starts from740201b; no push.

Latest2026-10-23: persistent infrastructure startup PASS reported by owner. Next verify-development-databases.ps1 for TCP auth/role boundaries and four-database retained-storage restart; implemented but not live verified. Kafka auth/restart and cross-service boundaries follow before C.

## 2026-10-23 — fix empty environment overrides

Host startup on ce590c3 created the private file but failed Compose interpolation before containers started. Reproduced safely: SetEnvironmentVariable(null) left18 present empty password keys, overriding --env-file. Changed scoped cleanup to Remove-Item Env: and restore presence/value separately. Added presence regression and actual Docker Compose config --quiet against disposable credentials.22 checks passed; independently validated existing .env.development and actual Compose interpolation with unchanged file hash. No secret values printed, credentials rotated, containers started or volumes touched. No Maven/A repeat. Next: rerun scripts/dev-infrastructure-up.ps1 with bundled PS7; existing credentials are reused. B remains incomplete until startup/auth/permission/restart gates execute; C/D pending. This following local repair checkpoint starts from clean ce590c3; resolve hash via git rev-parse HEAD. No push.

2026-10-23 latest: owner read-only initialization preflight PASS. Protected credential creation and persistent five-service startup helper implemented;20 focused checks pass. Next host dev-infrastructure-up.ps1, then B authentication/permission/restart acceptance. C/D remain pending.

P0.5-B partial2026-10-23: credential guards implemented,14 pure checks pass; Docker inventory denied here. Next owner dev-init.ps1 -CheckOnly is read-only. Complete file/ACL, actual Compose precedence and existing bootstrap/Kafka/auth/restart verification before C. A remains complete; D is not authorized before B/C pass.

**2026-10-23: P0.5-A COMPLETE on581da54.** All4 live container gates passed; next is B guarded private credentials/bootstrap, then C lifecycle and D persistent business acceptance. No A rerun needed. See latest HANDOFF for evidence and preservation requirements; older pending-A entries are historical.

2026-10-23: Core health cause confirmed and repaired: JSON groups-before-status was rejected.13 probe regressions pass. Retry only verify-application-images.ps1 -UseExistingArtifacts; A live gate remains pending, then B/C/D.

**2026-10-23 next gate:** retain172 passing reactor tests and4 successful builds. Diagnose Core health timeout using newly retained State/Health.Log; retry verify-application-images.ps1 -UseExistingArtifacts. No proven cause yet. A remains incomplete; B/C/D follow actual A success.

**Current P0.5 authorization/checkpoint,2026-10-22:** P0.4 is complete; P0.5 persistent local deployment is approved. A four-image packaging/static checks implemented; actual Docker gate blocked by coding-session engine access and awaits owner verify-application-images.ps1. Then B guarded credentials/bootstrap, C owner-scoped app Compose/lifecycle, D ordinary persistent business/restart demonstration and final regression. None of B/C/D is implemented or verified yet. Preserve existing project/volumes/private configuration. No P1/recovery/AI/push. See LOCAL_DEPLOYMENT and latest HANDOFF; older stop/P0.4 retry instructions below are historical.

**P0.4 COMPLETE — 2026-10-21.** Final packaged acceptance on `ab1089c` passed1/0/0/0 at22:55:29IST: actual simulator relays, Core intake/projection/detection/incidents, healthy workflow, missing observed outcome, and Core process restart with late evidence. Component pipeline7 cases passed across6+1 runs; A/B/C gates remain verified. Prior clean reactor168 plus scoped clock-fix6 regressions are separate results, not a new combined run. Historical failed attempts below are preserved and superseded by their documented successful reruns. No further verification command is required now. STOP: P0.5, recovery, AI, deployment and pushes require new authorization.

**Latest checkpoint,2026-10-21:** all7 D pipeline cases now passed across6 earlier cases plus the corrected contradiction1/0/0/0 on26624f5. Packaged1-case gate failed during Core startup: Modulith Moments could not select among3 Clock beans. Added primary UTC applicationClock and explicit projectionClock qualifier; real configuration/architecture6 tests passed and Core JAR rebuilt. Run `scripts/verify-p04-final.ps1 -Gate Packaged` only. Clean168 remains prior verified evidence, not a new full regression of this wiring fix. P0.4 remains incomplete pending actual packaged success. No schema, deadline semantics, permanent storage or push changes; older retry instructions below are historical.

**Latest repair checkpoint,2026-10-21:** owner host on57ad5b1 passed the clean168-test reactor and6 of7 D pipeline cases. The sole error was a faulty contradiction fixture attempting a second reservation forbidden by uq_reservations_business_transaction. Test-only repair now injects an explicitly contradictory Warehouse Kafka claim while preserving the single authoritative RESERVED row. Two fixture unit tests passed; live rerun pending. Use `scripts/verify-p04-final.ps1 -Gate Remaining` for only the corrected1-case gate followed by the unverified packaged1-case gate. Do not repeat clean168 or the6 passing cases. P0.4 remains incomplete; older full-rerun instructions below are historical. No production/schema/grant changes.

**Current checkpoint, 2026-10-21: P0.4-A/B/C verified; D acceptance implemented but NOT verified.** C's restart-only host report passed 1/0/0/0 at11:10:45IST on `a7a4761`, completing the earlier20 successful cases without repeating them. The failed first attempt remains historical evidence. New D tests use real owner operations, shared business identity, real Kafka and PostgreSQL, deterministic component clocks, and a separate executable-service scenario. Docker access is denied in this coding session; skipped discovery is not a pass. Run `scripts/verify-p04-final.ps1` on the host for the clean reactor and new D gates. P0.4 remains incomplete until actual zero-skip acceptance and evidence review. No permanent .env, existing storage, P0.5, recovery, AI or push. Older statuses/commands below are superseded history.

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

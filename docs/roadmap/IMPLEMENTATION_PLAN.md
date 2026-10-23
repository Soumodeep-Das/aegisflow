# Implementation plan

## 2026-10-24 — D fixture database gate prepared; host execution pending

Continued clean local4703cb5 without repeating A/B/C. Added simulators/warehouse-service/src/test/java/com/aegisflow/simulator/warehouse/LocalDemoIT.java: four real PostgreSQL cases for held-event replay/repeated release with unchanged envelope/hash/creation time, outer rollback of reservation/lines/outbox/idempotency, same-aggregate cross-tenant release isolation, and missing-outcome release refusal. Reuses existing restricted runtime/migrator bootstrap, pinned PostgreSQL and dynamic Testcontainers ports. No skip-on-missing-Docker annotation. Production change only exposes LocalDemo.execute package-locally for tests; no new endpoint or changed business rules.

Actual Java21 command: mvnw.cmd -o -B -ntp -Dmaven.repo.local=.cache/maven-repository -pl simulators/warehouse-service -Dtest=LocalDemoTests test. Exit0,15.738s; all Warehouse test sources compile;2 guard tests pass,0 failures/errors/skips. The four new database cases have NOT executed. docker info exits1 with engine-pipe permission denied. PowerShell verifier parser passed. No images rebuilt, running containers changed, credentials edited, persistent data mutated or push.

Next owner command: PowerShell7 -NoProfile -File ./scripts/verify-local-demo-driver.ps1 with JAVA_HOME set to Java21. The script selects only LocalDemoIT, preserves Maven log/fresh XML under .cache/p05d-driver/<run>, and requires exactly4 tests with0 failures/errors/skips. This is fixture safety evidence, NOT a substitute for ordinary-stack D acceptance or complete CLI JVM wiring.

Exact continuation: inspect host fixture result, fix any failure, then implement guarded persistent D runner with explicit per-run synthetic tenant trust, on-time held Warehouse outcome, real owner CLI operations and real worker processing; Core-only restart/release/history assertions and full-stack retained restart. Final clean reactor and rebuilt image identity remain required. D/P0.5 INCOMPLETE. Prior C deployment remains unchanged. Local checkpoint follows4703cb5; resolve hash/tree using git rev-parse HEAD and git status --short --branch.

## 2026-10-24 — P0.5-D partial CLI driver checkpoint, NOT acceptance

Entry clean7188975, main ahead3; owner explicitly approved persistent D. Read current continuity/plan and existing owner business methods/P0.4 packaged fixture; no A/B/C rerun. Added package-local LocalDemo.java beside each simulator main, entered ONLY with --aegisflow.local-demo=true. Normal startup unchanged. Explicit single run UUID, healthy/missing scenario and allowed mode create (Warehouse also hold/release); tenant demo-<UUID>, deterministic per-run order/resource/transaction/idempotency identities. Calls existing OrderPersistenceService, PaymentCreation and ReservationApplicationService. Separate non-web CLI context forces postgres, disables its relay and Flyway; migrations must already be applied by ordinary apps. No public mutation endpoint or Core writes/credentials introduced.

Warehouse hold wraps the existing business call plus scoped next_attempt_at delay in one local transaction before its outbox becomes visible to the ordinary relay. Release finds the same tenant/aggregate/version event and advances only its retry schedule; event identity/occurredAt are not rewritten. This intentionally withholds observed delivery of an on-time outcome. No clock override, backdating, incident resolution or recovery behavior. Actual PostgreSQL hold/release/replay behavior remains UNTESTED; no success claim. SQL release lookup was corrected during inspection to actual outbox aggregate_id (there is no business_transaction_id column there).

Actual verification: Java21 Maven -o -B -ntp -Dmaven.repo.local=.cache/maven-repository -pl simulators/order-service,simulators/payment-service,simulators/warehouse-service -Dtest=LocalDemoTests test passed6 cases (2/module),0 failures/errors/skips in27.673s initially; final focused rerun after SQL correction also6/0/0/0, XML inspected. These are argument/operation guard tests, not database or full reactor acceptance. docker info remains denied at engine pipe. No container/image rebuild, tenant allowlist change, private-file modification, volume mutation or push. Existing running images remain C versions; source now newer, do not claim images contain this driver.

Exact next task: implement guarded host D orchestrator and owner-local DB-backed driver tests before asking owner to execute. It must explicitly configure one new synthetic tenant for all source/Core allowlists without altering private credentials; keep operation occurrence inside deadline (parallel/warmed owner contexts or fail timing assertions); invoke each CLI under its own container credentials; assert real outbox/Kafka/Core facts/projection/deadline/evaluation/incident timeline using read-only Core queries; restart only actual Core, release Warehouse then prove retained DETECTED history plus SATISFIED; verify repeated evaluation and full-stack stop/start preservation. Restrict every query/assertion to new tenant/transaction IDs. Add clean final Java21 reactor/report capture and rebuild affected images before D execution. Current six tests do NOT cover CLI context wiring, transactional hold/release, replay or end-to-end. No runnable full D command exists yet; no owner action needed at this partial checkpoint. Negative Kafka write test remains deferred unless safe. P0.5 remains INCOMPLETE; no P1/recovery/AI/frontend/cloud. Following local commit resolves with git rev-parse HEAD.

## 2026-10-23 — P0.5-C live nine-service acceptance complete

Owner ran verify-development-stack.ps1 on a3e4a2b successfully. Independently inspected .cache/p05c/1e5c08a6611643ae96a8a41cf93d1abe/results.json: Passed=true, StaticOnly=false, static policy plus8 app/pass records. Host output confirms all9 persistent services healthy,4 app Java21/own-only configuration/network/host HTTP checks before and after clean application-only shutdown/restart. Infrastructure container IDs/start times/volume names and private-file hash stayed unchanged during app restart. Core fixed group/channel and demo-tenant allowlists checked. This is8 app/pass records, not8 JUnit tests. Prior failed lifecycle attempt remains preserved. No passed A/B suites or Java reactor repeated for closure.

C COMPLETE within deployment scope. Nine long-running containers were left healthy/running; kafka-init is an additional exited one-shot job. Containers do not auto-start on laptop boot by design. Lifecycle: scripts/dev-stack.ps1 -Action status|up|stop|down|restart-apps. stop/down preserve containers/networks/volumes; up completes prerequisites before apps. Initialization remains dev-init.ps1. No credential values displayed/changed, storage deleted, source business logic changed or push performed. Earlier helper console messages saying B incomplete are historical generic script wording, not current milestone status.

Next phase is D ONLY in a subsequent continuation: ordinary persistent healthy/missing-Warehouse business demonstration, incident/evidence and late-event history, retained full-stack verification as required. No D fixtures/events implemented or executed in this closure. Persistent negative-write Kafka test remains explicitly deferred: a mistakenly allowed probe can contaminate business topics; B exercised exact ACL inventory plus explicit metadata denials instead. HTTP/configuration health does not establish end-to-end event processing, universal exactly-once effects, production capacity or authoritative recovery. No recovery/AI/cloud. No additional owner command required now; status is optional. This local documentation closure follows clean a3e4a2b; resolve final hash/tree with git rev-parse HEAD and git status --short --branch.

## 2026-10-23 — P0.5-C nine-service deployment implemented, live gate pending

Entry clean main114b945 synchronized with origin/main; A/B remain complete within recorded scopes. Added compose.apps.yaml with four verified :local images, postgres profile, UID10001/read-only/cap-drop/no-new-privileges/resource limits, loopback8080–8083, own-only DB migration/runtime credentials and networks plus kafka-events. Internal JDBC uses owner-db:5432; Kafka kafka:9092. Enables all3 relays and Core inbox/projection/detection/incidents for fixed synthetic demo-tenant, stable aegisflow-core-inbox-v1/local-kafka-v1 identities. No private-file or infrastructure Compose changes. Existing schemas are migrated by existing Flyway when apps start, never reset. No application Java changes or reactor rebuild.

scripts/dev-stack.ps1 actions: up runs existing infrastructure readiness/bootstrap/provisioning before --no-deps app startup; status lists unified project; stop/down stop apps then infrastructure without deleting containers/networks/volumes; restart-apps stops/starts only four apps. Use this orchestration rather than direct compose up: DB role bootstrap is intentionally reused as an explicit prerequisite. No automatic OS-start restart policy. Existing port conflicts fail without killing another process.

scripts/verify-development-stack.ps1 checks expanded Compose in memory only, starts stack, checks all9 services healthy, app Java21/HTTP/non-root/read-only/owner-only config and networks, performs clean app stop/restart, compares infrastructure container/start-time/volume identities and private-file hash, leaves stack running. StaticOnly does no engine mutations. Actual local static Compose check passed at .cache/p05c/9ee11cb8099a4643ba846ce8546a4b87; first static attempt467c901bffec42879202119755204bab failed a verifier expression precedence bug, corrected before pass. Both PS scripts parse; Docker info exits1 named-pipe permission denied. LIVE C UNVERIFIED; no startup/restart claim. No passed A/B suites repeated.

Next owner command: bundled PS7 -NoProfile -File .\scripts\verify-development-stack.ps1. This starts four permanent app containers using retained infrastructure/credentials; expect nine healthy running services, eight app/pass records and clean restart. Preserve evidence on failure; do not delete volumes, rotate credentials or run --remove-orphans. On pass review real evidence before C closure; do not begin D until then. Persistent negative-write Kafka check deferred to D: a mistakenly permitted test could inject invalid business-topic data; prior persistent tests cover exact ACL inventory and explicit metadata denials, not new write denial. Application health/configuration is not full event/business correctness. This following local checkpoint resolves with git rev-parse HEAD. No push/recovery/AI.

## 2026-10-23 — P0.5-B infrastructure acceptance complete

Owner065944f restarted existing infrastructure after stopped-Core discovery and ran boundary gate successfully. Independently inspected .cache/p05b-boundaries/7c3f9d343aad494cb4534848f0da5108/results.json (Passed=true,4 networks,12 denied sibling routes) and .cache/p05b-kafka/d44c817e01e94593b0af7a50af1287e7/results.json (Passed=true,7 explicit metadata-operation denials,4 positive controls). Standalone client build7.499s; no JUnit tests in that build. Four own-database reachability controls passed in host output. Prior failed discovery runs remain preserved; database was stopped, not an evidenced network-isolation failure.

B closure combines, without summing unlike test counts:22 credential/file/ACL/environment/Compose checks; real generated private-file reuse; database8 owner/pass records with authentication, role/schema limits, wrong-password rejection, repeat bootstrap and stable storage identity; Kafka2 restart passes with stable broker/topic identity, exact17 ACL inventory and source/Core authentication; live network/metadata denial evidence above. All private credentials/volumes preserved. No write-producer records or consumer offsets inserted by new persistent Kafka verifiers; exercised denials are metadata/ACL administration, not forbidden-write claims. Prior P0.3 disposable write-ACL tests remain separate historical evidence. This is infrastructure acceptance, not production security certification or full P0.5.

Next authorized task C: add compose.apps.yaml and safe dev-up/status/verify/down lifecycle using verified four images, own-only database credentials/networks, kafka:9092, explicit postgres/workers/tenant allowlists, bounded startup and safe retained-volume shutdown. Verify actual nine long-running services and application credential/network boundaries; no D before C passes, no recovery/AI/push. Existing5 infrastructure services remain running; Kafka-init is one-shot. Compose Kafka-only orphan warning names legitimate databases: never --remove-orphans. No B rerun required merely to resume. This local documentation closure follows clean065944f; resolve final hash with git rev-parse HEAD. No source/credential changes or remote action in closure.

2026-10-23: A verified at729463c. B credential guard partial implementation has14 passing pure checks; Docker preflight denied. Finish safe-file/ACL/reuse and real bootstrap/provision/auth/restart tests before C; D remains outside this continuation until B/C pass.

**Current P0.5 authorization/checkpoint,2026-10-22:** P0.4 is complete; P0.5 persistent local deployment is approved. A four-image packaging/static checks implemented; actual Docker gate blocked by coding-session engine access and awaits owner verify-application-images.ps1. Then B guarded credentials/bootstrap, C owner-scoped app Compose/lifecycle, D ordinary persistent business/restart demonstration and final regression. None of B/C/D is implemented or verified yet. Preserve existing project/volumes/private configuration. No P1/recovery/AI/push. See LOCAL_DEPLOYMENT and latest HANDOFF; older stop/P0.4 retry instructions below are historical.

**P0.4 COMPLETE — 2026-10-21.** Final packaged acceptance on `ab1089c` passed1/0/0/0 at22:55:29IST: actual simulator relays, Core intake/projection/detection/incidents, healthy workflow, missing observed outcome, and Core process restart with late evidence. Component pipeline7 cases passed across6+1 runs; A/B/C gates remain verified. Prior clean reactor168 plus scoped clock-fix6 regressions are separate results, not a new combined run. Historical failed attempts below are preserved and superseded by their documented successful reruns. No further verification command is required now. STOP: P0.5, recovery, AI, deployment and pushes require new authorization.

**Latest checkpoint,2026-10-21:** all7 D pipeline cases now passed across6 earlier cases plus the corrected contradiction1/0/0/0 on26624f5. Packaged1-case gate failed during Core startup: Modulith Moments could not select among3 Clock beans. Added primary UTC applicationClock and explicit projectionClock qualifier; real configuration/architecture6 tests passed and Core JAR rebuilt. Run `scripts/verify-p04-final.ps1 -Gate Packaged` only. Clean168 remains prior verified evidence, not a new full regression of this wiring fix. P0.4 remains incomplete pending actual packaged success. No schema, deadline semantics, permanent storage or push changes; older retry instructions below are historical.

**Latest repair checkpoint,2026-10-21:** owner host on57ad5b1 passed the clean168-test reactor and6 of7 D pipeline cases. The sole error was a faulty contradiction fixture attempting a second reservation forbidden by uq_reservations_business_transaction. Test-only repair now injects an explicitly contradictory Warehouse Kafka claim while preserving the single authoritative RESERVED row. Two fixture unit tests passed; live rerun pending. Use `scripts/verify-p04-final.ps1 -Gate Remaining` for only the corrected1-case gate followed by the unverified packaged1-case gate. Do not repeat clean168 or the6 passing cases. P0.4 remains incomplete; older full-rerun instructions below are historical. No production/schema/grant changes.

**Current checkpoint, 2026-10-21: P0.4-A/B/C verified; D acceptance implemented but NOT verified.** C's restart-only host report passed 1/0/0/0 at11:10:45IST on `a7a4761`, completing the earlier20 successful cases without repeating them. The failed first attempt remains historical evidence. New D tests use real owner operations, shared business identity, real Kafka and PostgreSQL, deterministic component clocks, and a separate executable-service scenario. Docker access is denied in this coding session; skipped discovery is not a pass. Run `scripts/verify-p04-final.ps1` on the host for the clean reactor and new D gates. P0.4 remains incomplete until actual zero-skip acceptance and evidence review. No permanent .env, existing storage, P0.5, recovery, AI or push. Older statuses/commands below are superseded history.

**Current P0.4-C checkpoint,2026-10-21:** A/B remain verified. C durable incident/timeline implementation has10 focused policy/architecture passes;21 database checks skipped due Docker access denial. Run `scripts/verify-incidents.ps1` for the pending17 C +4 Core PostgreSQL checks. No permanent .env or dedicated Compose apps needed. C/D and full P0.4 are not complete; no P0.5/recovery/AI/push. Earlier checkpoints below are history.

**Latest checkpoint,2026-10-21:** V3/P0.4-A database gate passed on `2b2e885`:12 projection +4 Core startup/migration tests, zero failures/errors/skips. Pending statuses below are historical. Continue approved B clock-driven INV-002/freshness/restart proof before C incidents and D full integration; P0.4 is not complete. No owner rerun needed.

**Current milestone status,2026-10-21:** P0.4 approved from completed P0.3 `d4d4b7c`. A durable projection implementation exists; 43 focused tests pass, 16 database tests skipped due to denied Docker access. Host `scripts/verify-projection.ps1` must pass before B detector/C incidents. B scheduler/backlog freshness must pass before C; D adds full event-to-incident verification. Historical pending/unapproved statuses below are superseded. No P0.5 or push.

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

Use `(tenantId, businessTransactionId)` as the Kafka key while remaining correct across topics/reordering. Eventing commits immutable validated ACCEPTED intake, deduplicated by tenant/source/event and canonical content, before offset acknowledgement. P0.4 uses a separate transaction for fact, projection/deadline and durable progress marker; it never mutates intake or requires Kafka replay. ADR0010 supersedes the older same-intake-transaction requirement.

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

# Handoff — P0.2 database suites passed; final combined gate pending

**Latest snapshot:** 2026-10-16T14:10:12Z, Codex host-evidence continuation. Existing repository/branch `main`, entry/source HEAD `7a90c57`, clean on entry. This following documentation-only local commit records the user's successful rerun; resolve its hash/status with `git rev-parse HEAD` and `git status --short --branch`. No source changes, push, deployment, history rewrite or volume deletion.

**New observed result:** Warehouse-only `mvnw.cmd -B -ntp -pl simulators/warehouse-service -Pinfrastructure-tests clean verify` succeeded on the owner's host at 19:39:12 IST, 45.668 seconds, **10 default tests + 8 real PostgreSQL ITs**, zero failures/errors/skips. Docker 29.8.2/PostgreSQL 18.6, role bootstrap, Flyway V1, Hibernate validation and postgres context all ran. The four-adapter proxy fix is verified; do not repeat its implementation or diagnosis.

Together with the previous Core/Order/Payment successes, latest suites total **38 default tests and 29 PostgreSQL ITs across separate runs**, zero failures/errors/skips. All four owners' database suites now pass. This is not yet a successful single clean integrated root run; P0.1 remains the last fully exit-gated milestone, P0.2 only remains approved, and P0.3 is unapproved/unstarted. Exact per-module counts, previous failures and commands are in `docs/evidence/TEST_RESULTS.md`; implemented entry points/flow below are unchanged.

**Single next task:** final host acceptance run, in the existing repository with Java 21 and Docker Desktop running. Use bundled PowerShell 7 because the owner's ordinary terminal is 5.1:

```powershell
$ps7 = 'C:\Users\User\.cache\codex-runtimes\codex-primary-runtime\dependencies\native\powershell\pwsh.exe'
& $ps7 -NoProfile -Command '$ErrorActionPreference = "Stop"; & .\scripts\verify-persistence.ps1; & .\scripts\verify-startup.ps1; & .\scripts\verify-docs.ps1'
```

Acceptance: full reactor success; all **29 PostgreSQL ITs execute with zero failures/errors/skips**; all four packaged foundation JARs return HTTP 200/UP; documentation checker has zero issues. The combined command stops at the first error. Do not repeat successful targeted diagnostics or redesign code. Preserve final summaries/first underlying error if any, then update evidence/handoff and make the final local milestone checkpoint. Only after this gate may P0.2 be marked complete and P0.3 approval requested.

**Local checkpoint validation:** documentation checker exited 0 (29 Markdown, 20 required, 109 links, 7 Mermaid, 1 JSON, 16 scenarios, 20 Q&As, 8 ADRs, zero issues); whitespace check passed. No application changes or redundant application test reruns.

**Not yet executed:** this final integrated run, current packaged startup, and persistent Compose bootstrap. The latter is a separately recorded rehearsal limitation; temporary Testcontainers do not validate the Compose helper. Sandbox Docker/loopback restrictions remain distinct from host success. No credentials or `.env` are needed for the test-container gate. Do not delete volumes or edit already-applied V1 migrations. Older snapshots below are historical and superseded by this latest record.

**Latest Warehouse update:** 2026-10-16T14:06:48Z (2026-10-16 IST). Entry HEAD `bd3a1be` was clean; this scoped follow-up fixes Warehouse proxyability and records new host evidence. Resolve this following local commit with `git rev-parse HEAD`.

**Now proven on host:** all 37 default tests passed; real PostgreSQL Core 4 + Order 8 + Payment 9 ITs passed (21 total), no skips. All four V1 migrations applied. Host root run still exited 1 at 19:31:15 IST after 2:48 because Warehouse's 8 ITs could not create their common Spring context: final IdempotencyRepository blocked CGLIB exception translation. No Warehouse behavioral assertion passed.

Removed final from exactly Warehouse's four `@Repository` adapters, keeping package visibility, tenant guards, SQL and methods intact. New `WarehouseRepositoryProxyTests` creates a real Spring context and verifies all four CGLIB proxies without a database. Focused Java 21 module run compiled all Warehouse source/tests and passed 8 proxy/validation/contract/architecture tests, zero failures/errors/skips, at 19:35:28 IST (18.406 seconds).

**Single next diagnostic task (ordinary host PowerShell, existing repository, Java 21):**

```powershell
.\mvnw.cmd -B -ntp -pl simulators/warehouse-service -Pinfrastructure-tests clean verify
```

Require all 8 Warehouse ITs to run with zero failures/errors/skips. Preserve the first underlying error if any. Do not repeat the already successful Core/Order/Payment suites while diagnosing Warehouse. Once Warehouse passes, run the full persistence script once and packaged startup/docs checks to close the final gate. Persistent Compose bootstrap remains unexecuted; no volume deletion. P0.3 remains unapproved. Earlier snapshots below are historical and superseded by this latest evidence.

**Latest timezone fix:** 2026-10-16T13:57:47Z (2026-10-16 IST). Entry HEAD `79f46a5` was clean. The following local fix commit adds UTC to both Maven test fork arguments and a regression; resolve current HEAD with `git rev-parse HEAD`.

Host rerun: 6 Core default tests passed and pinned PostgreSQL started. The first JDBC admin/bootstrap connection failed because PgJDBC sent the host JVM alias `Asia/Calcutta`, rejected by this PostgreSQL image. Root exit 1 at 19:24:20 IST, 35.342 seconds; 4 Core IT errors, 0 failures/skips; migrations/simulators not reached. Surefire and Failsafe now start with `-Duser.timezone=UTC` (Mockito retained). This does not change Windows or production timezone settings. The image/UTC regression ran on Java 21: **2 passed, 0 failures/errors/skips**, exit 0 at 19:26:26 IST, 10.720 seconds. PostgreSQL assertions still require the normal host rerun.

**Continue now:** from the existing repository/Java 21 host terminal, run `& $ps7 -NoProfile -File .\scripts\verify-persistence.ps1` again. The earlier image failure is resolved. Preserve any new first underlying error; do not repeat implementation. For later manual postgres-profile Java launches use `-Duser.timezone=UTC` before `-jar`, as documented in README. P0.2 remains gate-pending; P0.3 stays unapproved. No pushes/deployment or history rewrite.

**Latest update:** 2026-10-16T13:46:12Z (2026-10-16 IST), targeted host-test failure fix.
**Entry HEAD:** `98a670d`, clean before this fix; prior source checkpoint `14e04f6` preserved. This next local fix commit changes only the four IT image declarations, one Core regression test and continuity documents. Resolve current HEAD/status with the commands below; no push/deployment is authorized.

Owner's host Docker 29.8.2 works and all 5 Core default tests passed. The full persistence run failed at 19:10:03 IST (47.696 seconds) on the Core PostgreSQLContainer's static image compatibility guard, before PostgreSQL startup. One Failsafe initialization error, zero failures/skips; simulators not reached. All four declarations now use `DockerImageName.parse(existingTagAndDigest).asCompatibleSubstituteFor("postgres")`; no pin/version/test was weakened. `PostgresImageConfigurationTests` initializes the actual Core container declaration without Docker and verifies the exact reference/compatibility: **1 passed, 0 failures/errors/skips**, Java 21, 19:13:57 IST, 13.184 seconds. Core's test sources compiled. Real migrations/state-outbox assertions remain unverified; sandbox engine access remains denied.

**Exact next task:** run the same host command in the existing repository:

```powershell
$ps7 = 'C:\Users\User\.cache\codex-runtimes\codex-primary-runtime\dependencies\native\powershell\pwsh.exe'
& $ps7 -NoProfile -File .\scripts\verify-persistence.ps1
```

Keep Java21 selected. On success, run `verify-startup.ps1` and `verify-docs.ps1` through the same executable. On failure, preserve the first underlying error and final reactor/test summary; fix that exact issue within P0.2. Do not repeat completed inspection or recreate code. P0.3 remains unapproved.

The previous checkpoint below is historical context; this latest result supersedes its host-environment assumptions. Detailed new evidence is first in `docs/evidence/TEST_RESULTS.md`.

**Closing snapshot:** 2026-10-13T03:58:40Z, Codex resumed P0.2 checkpoint (2026-10-13 IST).
**Authorized scope:** P0.2 only. Owner requested a meaningful, tested where available, understandable local checkpoint and exact local follow-up actions. P0.3 is unapproved and unstarted.
**Branch/repository:** `main`, original `C:\Users\User\Documents\Codex\2026-10-11\referenced-chatgpt-conversation-this-is-an\outputs\aegisflow`.
**Source checkpoint:** `14e04f6d0cb96520ae5ba1451c10890860df3379`, `feat(persistence): checkpoint P0.2 state and transactional outbox`, committed locally; Git immediately reported clean `main`. This following documentation-only continuity commit records the source hash without changing application code. Resolve its current HEAD/status with `git rev-parse HEAD`, `git status --short --branch`, and `git log -3 --oneline`. Generated cache/work/target and private values are excluded. No push, deployment, remote change or history rewrite occurred.

## Implemented source and exact entry points

Four independently managed V1/Flyway histories and separate bootstrap/migration/runtime roles exist. Core has its own schema boundary and no simulator credentials/business tables. Simulators have initial local state/replay/outbox primitives, composite tenant keys/FKs, optimistic counters, request-key/event/version uniqueness, immutable pending envelopes, canonical hashes, and restricted grants.

Actual flow: internal typed command → validated material digest → PostgreSQL unique replay claim → owned JPA state/lines → immutable outbox append → one Spring transaction commit. Same material request returns stored resource/event IDs; changed material conflicts; append failure must roll everything back. No business HTTP ingress, Kafka consumer/relay, recovery, AI, Keycloak or UI exists.

| Owner | Actual entry and important tests |
|---|---|
| Order | `simulators/order-service/src/main/java/com/aegisflow/simulator/order/persistence/OrderPersistenceService.java#create`; `OrderPersistenceTests` (6), `OrderPersistenceIT` (8), OrderCreated fixture |
| Payment | `PaymentCreation.recordInitialOutcome` interface; `simulators/payment-service/src/main/java/com/aegisflow/simulator/payment/persistence/internal/TransactionalPaymentCreationService.java`; validator/hash tests (9), `PaymentPersistenceIT` (9) |
| Warehouse | `simulators/warehouse-service/src/main/java/com/aegisflow/simulator/warehouse/reservation/ReservationApplicationService.java#createReservation`; command/event tests (6), `WarehousePersistenceIT` (8) |
| Core/roles | `control-plane/src/main/resources/db/migration/V1__core_database_boundary.sql`; `PostgresInfrastructureIT` (4); `scripts/db/bootstrap-roles.sql`, `bootstrap-databases.ps1`, `verify-persistence.ps1` |

Surviving work was reused. Payment schema/profile/hash review fixes were completed, including JPA-compatible VARCHAR columns. Warehouse's explicit Flyway URL now targets its Testcontainer. Documentation includes actual classes/flow, JPA/Hibernate/transaction explanations, debugging exercise, Q16–Q20, ADR 0008, accurate evidence/limitations and roadmap.

## Actually verified versus pending

- **21 pure unit tests passed**, zero failures/errors/skips, from actual source/test files via an ignored cached-library diagnostic using unchanged Boot 4.1.1/Java 21. Covers input/money, canonical digests/material request rules and producer envelope/hash fixtures. This is not JPA/database/full-reactor evidence.
- **4 Core architecture tests passed** in a focused module run. Its **4 PostgreSQL ITs skipped** due denied Docker pipe; exit 0 did not satisfy the persistence gate.
- Compose configuration passed quietly with temporary private values. Three PowerShell scripts parsed with zero errors. Documentation checker: 29 Markdown, 20 required docs, 108 local links, 7 Mermaid, 1 JSON, 16 scenarios, 20 Q&As, 8 ADRs, zero issues. Whitespace checks passed. No Mermaid rendering/security scan/load/Kafka fault result is claimed.
- Full offline reactor **failed** at 00:39:17 IST: Core 4 pass/1 HTTP error/0 assertion failures/0 skips, because Tomcat's loopback connection was denied. Simulators were not reached.
- Later test-compile **failed** on uncached JPA starter 4.1.1; online retry **failed** Maven Central DNS at 09:21:36 IST. Core new test sources compiled. Full simulator/JPA/IT compilation is not proven.
- Real migrations, state/outbox atomicity, rollback/replay/races, constraints/locking/privileges, PostgreSQL startups and current four-JAR startup regression remain **unverified here**. P0.1 is the last completed milestone.

Exact commands/timestamps/failures/counts and user-observed P0.1 build remain in `docs/evidence/TEST_RESULTS.md` and the journal. No test was disabled, faked or relabeled. The 25 simulator ITs have not run.

## Single highest-priority next task: normal host verification gate

Use this existing repository in ordinary PowerShell, with Java 21, Maven Central/container-registry access and Docker Desktop's Linux engine available. The sandbox's engine/network/loopback failures do not establish a fault in the owner's Docker installation.

```powershell
Set-Location 'C:\Users\User\Documents\Codex\2026-10-11\referenced-chatgpt-conversation-this-is-an\outputs\aegisflow'
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot'
.\scripts\verify-persistence.ps1
.\scripts\verify-startup.ps1
.\scripts\verify-docs.ps1
```

The persistence script runs the **normal clean reactor plus real PostgreSQL tests** and rejects all IT skips. It uses temporary containers; no .env/shared passwords/persistent Compose bootstrap is needed. On failure, preserve the final reactor/test summary and first underlying error; fix that exact issue within P0.2. Do not recreate these paths. Share no private .env/tokens/passwords.

Acceptance: full reactor/unit/HTTP/module checks; all owners' real migrations/validation and commit/rollback/replay/concurrency/tenant/version/immutable-payload/privilege assertions; zero IT skips; startup/docs checks; accurate follow-up evidence. Only then mark P0.2 complete and request P0.3 approval.

**Safe recovery/hazards:** no reset/checkout overwrite/volume deletion. Bootstrap explicitly preserves existing identities/volumes. Runtime has limited grants; startup Flyway credentials remain in its own process (not a security sandbox). Initial V1 files are unexecuted here; do not edit an applied migration after deployment. Internal methods require later trusted authenticated ingress; no complete public tenant/authentication proof exists.

---

## Archived interrupted-entry snapshot (current closing record above supersedes it)

**Current snapshot:** 2026-10-13T03:46:50Z, Codex resumed continuation (2026-10-13 IST).
**Authorization:** P0.2 persistence, independent Flyway migrations, and atomic state/outbox writes. Owner resumed after “stop for today,” with instructions to preserve progress and stop at a meaningful documented checkpoint. P0.3 is not authorized.
**Existing repository:** `C:\Users\User\Documents\Codex\2026-10-11\referenced-chatgpt-conversation-this-is-an\outputs\aegisflow`, branch `main`.
**Current HEAD:** `c2c93e3028be6929bd7894864bcd6bc3f8d1be3b`. P0.2 changes survived as modified/untracked files and are not committed yet. No push/deployment or destructive history change occurred.

## Surviving implementation — do not recreate

- Database roles/bootstrap: root Compose/.env example; `scripts/db/bootstrap-roles.sql`, `bootstrap-databases.ps1`, `verify-persistence.ps1`; root inherited infrastructure-tests/Failsafe profile.
- All four postgres profiles separate runtime/Flyway credentials. Core V1 establishes its independent schema/history boundary; Core `PostgresInfrastructureIT` tests migration, health and privileges, with no Core business tables.
- Order `persistence/OrderPersistenceService.java`, typed command, JPA entity/repository, canonical JSON, outbox appender, V1 orders/lines/replay/outbox schema, frozen OrderCreated fixture, six new unit tests and eight PostgreSQL IT tests.
- Payment `PaymentCreation` interface and `persistence/internal/TransactionalPaymentCreationService.java`, command/entities/repositories/hash/outbox code, V1 migration, unit tests and `persistence/internal/PaymentPersistenceIT.java`. Final schema review fixes were unfinished at interruption (tenant length consistency, fail-closed JSON/reason checks, same-version outbox uniqueness).
- Warehouse `reservation/ReservationApplicationService.java`, typed command/entity/repository/hash/outbox code, V1 migration, six unit tests and eight `WarehousePersistenceIT` tests. Deferred tenant aggregate/outbox FK, same-version uniqueness and fail-closed envelope/reason checks were applied before interruption.
- ADR 0008 and current-source/authorization updates in AGENTS, README, roadmap, implementation plan, data model, event contracts, and system architecture.

No business HTTP API, Kafka relay/consumer, recovery, AI, Keycloak or frontend was introduced. Source exists; PostgreSQL results remain unproven. P0.1 is the last completed milestone.

## Last observed tests and blockers

Java 21/Maven were already confirmed: Temurin 21.0.12.1 at `C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot`, wrapper Maven 3.10.0. Earlier approved Docker info reported engine 29.8.2; the subsequent sandbox denied its engine pipe. Do not confuse host Docker availability with coding-session access.

Global `.m2` writes failed at Maven lock files; automatic escalation was rejected by the granular policy. Existing artifacts were copied read-only into ignored `.cache/maven-repository`. An early build before that copy finished failed to resolve the parent/Maven Central DNS. The completed-copy offline root run was:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot'
$env:TEMP = Join-Path (Get-Location) '.cache\tmp'
$env:TMP = $env:TEMP
.\mvnw.cmd -B -ntp -o '-Dmaven.repo.local=.cache/maven-repository' -l .cache/p0.2-clean-verify-offline.log clean verify
```

It exited **1** at **2026-10-13 00:39:17 IST**, 24.594 seconds. Core compiled and executed five default tests: **4 passed, 0 assertion failures, 1 error, 0 skips**. All four architecture tests passed. Core health startup failed with `Unable to establish loopback connection` caused by `java.net.SocketException: Permission denied: connect`. Simulator reactor modules were not reached. New simulator compilation, unit tests, real PostgreSQL IT, Compose runtime and startup regressions remain unverified.

The previous handoff update failed atomically because an evidence-file heading did not match; this current snapshot repairs that missing continuity record before further work.

## Exact next task

Finish the existing Payment schema review fixes; review only new Warehouse configuration/test issues as needed. Resolve compilation, then run available unit/architecture checks and the full Java 21 reactor. Run real PostgreSQL via `.\scripts\verify-persistence.ps1` only with accessible Docker; the script rejects skipped ITs. Record actual counts/errors and preserve failures. Complete the guide (JPA/Hibernate/transactions/Flyway/outbox, actual paths, debugging, five answered Q&As), limitations, evidence, journal, roadmap and handoff, then make a coherent local checkpoint. Stop before P0.3.

For normal local verification: `.\mvnw.cmd -B -ntp clean verify`, `.\scripts\verify-persistence.ps1`, `.\scripts\verify-startup.ps1`, `.\scripts\verify-docs.ps1`. For persistent local DBs fill ignored .env privately, validate Compose quietly, start the four databases, then run the repeatable bootstrap. Never delete existing volumes or pass simulator credentials to Core.

Preserve the working tree; no reset/checkout/volume deletion. Ignored cache/logs are diagnostics. Resolve current state with `git status --short --branch` and `git rev-parse HEAD`.

---

## Historical P0.1 handoff (preserved; superseded by current snapshot above)

**Snapshot:** 2026-10-12T04:57:16Z (10:27:16 IST), implementation completion session.
**Authorization:** Phase 1 milestone P0.1 only. Owner approved it, paused, then requested continuation. **Stop here: P0.2 is not approved.**
**Repository:** independent local Git repository `outputs/aegisflow`, branch `main`.
**Remote / push / external deployment:** none configured or performed.

## Current commit and status

Phase 0 baseline `2bae1f3e13686194011c005c202a872f01a99815` and continuity checkpoint `6f6daafe58b2eb916c373e4c49853d78d8909562` are preserved. P0.1 source milestone commit is **`cb7bfafed49a58f08cba2352aafb8d7ecf8f6e33`**, `feat(foundation): establish verified P0.1 application skeletons`. After it committed, Git reported branch `main` with a clean working tree. This handoff/evidence/journal/roadmap update forms the following documentation-only continuity commit; resolve its HEAD with the commands below. Generated `target/`, `.cache/`, toolchains and private `.env` files are not committed.

```text
git status --short --branch
git rev-parse HEAD
git log -3 --oneline
git remote -v
```

The follow-up handoff commit identifies the preceding source milestone commit, avoiding a self-referential hash. Always inspect the actual state and preserve unknown changes before continuing.

## Last completed milestone / features that actually work

P0.1 is a **health-only foundation**, not commerce or recovery functionality:

- Java 21 / Maven 3.10.0 through checksum-pinned Wrapper 3.3.4; Boot 4.1.1 and Modulith 2.1.1 resolve and compile.
- Four independent executable JARs: Core, Order, Payment, Warehouse. Separate mains/POMs/configuration/tests; no deployable imports another's implementation.
- Ten explicit Core Modulith modules, zero allowed cross-module dependencies, `ApplicationModules.verify()` and three additional Core checks. Simulator coupling tests and Maven dependency bans provide separate checks.
- Final root clean build: **14 tests, 0 failures/errors/skips**, finished 2026-10-12 10:21:16 IST. All four packaged JARs separately returned **HTTP 200 / health UP** in the following startup check. Tests explicitly activate `foundation`, even with a caller's `postgres` profile.
- Four optional separate PostgreSQL profiles and Compose services/volumes/networks, required private passwords, and a JDBC/Flyway Testcontainers test are configured. Compose syntax validation passed.
- **Docker engine unavailable:** PostgreSQL IT was **1 skipped**, not passed. Database connectivity and `postgres` application startup remain unverified.

No applications or databases are intentionally left running: the startup helper stops only its own children. No business migrations/APIs, persisted commerce data, Kafka processing, incidents, recovery, AI, authentication, UI or dashboards exist. Every business `FM-*` scenario remains not run.

## In-progress work / stopping reason

No P0.2 implementation is in progress. This session closes evidence and the local checkpoint, then stops at the owner's milestone boundary. The next infrastructure blocker is a usable Docker engine; no install, WSL reset or volume deletion was attempted. Runtime/migration privilege design and database-backed startup tests remain later obligations.

## Reproduce the checks

Use Java 21, not the host default Java 26. This session used isolated Temurin 21.0.12.1 at `../../work/toolchains/jdk-21.0.12.1+1` relative to repository root; it is outside Git. On another machine substitute your own Java 21 JDK. No separately installed Maven is required.

```powershell
$env:JAVA_HOME = 'C:\path\to\your\jdk-21'
New-Item -ItemType Directory -Force .cache/tmp | Out-Null
$env:TEMP = Join-Path (Get-Location) '.cache/tmp'
$env:TMP = $env:TEMP
.\mvnw.cmd --version
.\mvnw.cmd -B -ntp clean verify
.\scripts\verify-startup.ps1
.\scripts\verify-docs.ps1
.\mvnw.cmd -B -ntp dependency:tree
.\mvnw.cmd -B -ntp -pl control-plane -Pinfrastructure-tests verify
```

Windows/PowerShell 7.6.5 was verified. Exact dated commands, resolved versions, counts, failures and raw-report locations are in [TEST_RESULTS.md](evidence/TEST_RESULTS.md). The optional infrastructure command exited 0 with **1 skipped test**; inspect Failsafe counts rather than treating the exit code as DB readiness. POSIX was not executed and needs `unzip` for the pinned Maven ZIP.

After privately filling an ignored `.env` from `.env.example`, `docker compose --profile postgres config --quiet` validates configuration without printing secrets. Do not share expanded Compose configuration. Java processes do not automatically load Compose's `.env`: pass only each application's own DB variables. **Core must not receive simulator credentials.** See [README](../README.md) and [ADR 0007](adr/0007-foundation-profiles-and-stage-boundaries.md).

## Entry points and working flow

| Path | Purpose |
|---|---|
| `pom.xml`, `.mvn/wrapper/maven-wrapper.properties` | Java/version rules, management, reactor, artifact bans, checksum |
| `control-plane/src/main/java/com/aegisflow/core/AegisFlowCoreApplication.java` | Boot/Modulith root and ten annotated subpackages |
| `control-plane/src/test/java/com/aegisflow/core/ControlPlaneArchitectureTests.java` | module discovery, dependencies and verification |
| `control-plane/src/test/java/com/aegisflow/core/PostgresInfrastructureIT.java` | optional real JDBC/Flyway check |
| `simulators/order-service/src/main/java/com/aegisflow/simulator/order/OrderSimulatorApplication.java` | independent order bootstrap; payment/warehouse exact paths in guide |
| each deployable's `src/main/resources/application*.yaml` | loopback ports, foundation exclusions, own PostgreSQL configuration |
| `compose.yaml`, `.env.example`, `scripts/verify-startup.ps1` | database bootstrap definitions and owned-process JAR verification |
| `docs/learning/CODEBASE_GUIDE.md`, `docs/architecture/MODULE_BOUNDARIES.md` | actual paths, lifecycle, debugging exercise, Q11–Q15 with answers, boundary rules |

Working flow: `main` → `SpringApplication.run` → foundation excludes JDBC/Flyway → embedded Tomcat on loopback → Actuator `/actuator/health` → status-only JSON. Tests use `@ActiveProfiles("foundation")`, `@SpringBootTest(RANDOM_PORT)` and `@LocalServerPort`. This flow creates no durable business state.

## Remaining defects, limitations and risks

- **Next-stage blocker:** Docker engine is not running/accessible. The installed CLI alone is insufficient. Real image/JDBC/Flyway execution is unverified.
- Compose owners are privileged bootstrap administrators, not restricted runtime identities. P0.2 must separate migration/runtime roles and assert privilege boundaries before business persistence.
- Health has no authentication and binds to loopback. Do not expose this foundation publicly or claim implemented tenant isolation. Planned API-only recovery/security rules remain mandatory.
- Mockito runtime attachment initially hung; Surefire's startup agent fixed it without disabling tests. Future coverage agents must compose JVM arguments intentionally.
- Initial wrapper/cache attempts, a locked diagnostic JAR, and helper byte-array JSON parsing failed; fixes and successful reruns are separately recorded. No destructive reset or fake pass was used.
- POSIX, clean-machine checkout, CI, vulnerability/load tests and Mermaid rendering are unverified.
- Event canonicalization/schema, relay, tenant and recovery choices remain in [OPEN_QUESTIONS.md](OPEN_QUESTIONS.md). No automatic financial compensation is authorized.

## Highest-priority next task and acceptance criteria

**Obtain new P0.2 approval and unblock real PostgreSQL testing**, then implement service-owned persistence and one atomic state/outbox path. Kafka transport stays P0.3.

1. Docker runs the PostgreSQL test with **zero skips**; separately test all four applications' `postgres` profiles.
2. Independent databases/Flyway histories and restricted migration/runtime roles; Core has no simulator credentials/privileges, and test-harness inspection identities stay separate.
3. Minimal approved simulator state/versioned request behavior: business mutation and event outbox row commit in **one local transaction**.
4. Real PostgreSQL tests prove rollback leaves neither state nor event, commit persists both, and retry retains stable event IDs without another business effect. No premature Kafka implementation.
5. Event fixtures match `EVENT_CONTRACTS.md`; foundation/boundary checks stay green.
6. Update actual flow/path explanations, evidence, five new answered interview questions, journal, roadmap and handoff; commit a coherent local checkpoint.

## Owner preparation for smoother continuation

1. Open Docker Desktop manually, wait for its engine, then run `docker info`. If it fails, share the error section, **not passwords/tokens**. Do not reset WSL or delete volumes blindly.
2. Keep Java 21 selected. Reuse the isolated JDK if available; `.\mvnw.cmd --version` should show Java 21 / Maven 3.10.0. Another Maven installation is unnecessary.
3. Optionally run the default build/startup checks and read the guide's debugging exercise. Share exact failing commands and final errors, redacting secrets.
4. Explicitly approve **P0.2 only** when ready. Kafka, Keycloak and AI keys are not needed now. Keep these files available if moving to ordinary ChatGPT; repository evidence replaces remembered chat history.

## Safe recovery and final checkpoint

Inspect status first and preserve unknown edits. Use new commits/revert review instead of destructive reset or force operations. Do not delete local database volumes casually; none were created here in P0.1. No push or external deployment is authorized.

At the source checkpoint, final Surefire XML totals were independently summed: 14 tests, 0 failures/errors/skips. The documentation checker reported 28 Markdown files, 20 required documents, 97 resolving local links, 7 Mermaid fences, 1 valid JSON example, 16 complete failure scenarios, 15 interview Q&As, 7 ADRs and 0 issues. Staged whitespace checks passed; `mvnw` is stored executable (`100755`). Git contains 73 scoped source/configuration/document changes in the milestone. There is no remote configured. The final documentation-only checkpoint is followed by a clean-status check before stopping; no application source changes follow the verified milestone.

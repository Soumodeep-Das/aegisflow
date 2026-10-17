# Test evidence — P0.3 checkpoint and preserved completed milestones

## 2026-10-17 — Kafka infrastructure implemented; real host verification pending

Entry `1043a51`, existing repository clean/main. Added Kafka-only companion Compose, pinned Apache 4.2.2, three source topics, dual client listeners plus authenticated controller, separate SASL credentials/default-deny ACLs and standalone client/provisioning scripts. Existing PostgreSQL Compose/application POMs/migrations are unchanged; no prior runtime gate was repeated. Official Docker Hub tag metadata was fetched with read-only `Invoke-RestMethod` after turn-scoped network access: index `sha256:1213eb3943d551e5ed1fca7a4e109001cee35770b66a02a0c37a8964efe09b69`, amd64 manifest `sha256:daa39dc85acd37b148fad74d6b22b60fa5f7fa05048a5c8aa3b654140c63dd58`, arm64 manifest `sha256:031e51202f27021e6afcc1f55d1d0efd9c54864a96d57506e7c1af7dc97839dd`. Metadata/source review does not establish image execution or vulnerability clearance; primary links are in ADR 0009.

| Exact command/check | Actual outcome |
|---|---|
| `docker info --format '{{.ServerVersion}}'` | exit1, Docker engine pipe permission denied; CLI `docker compose version --short` returns5.5.1 |
| Java21 `scripts/build-kafka-smoke.ps1` (wrapper `-B -ntp -Dmaven.repo.local=<workspace-cache> -f tools/kafka-smoke/pom.xml package`) | **BUILD SUCCESS/exit0**, 20:17:59 IST / 13.935s; compile/package standalone client against Boot-managed kafka-clients4.2.1, slf4j-nop2.0.18. No JUnit suite in this helper; self-checks run separately |
| Java21 `scripts/test-kafka-infrastructure.ps1` | exit0 at14:49:50Z; **10 PowerShell guard groups + 15 Java self-checks**, zero failed; no broker/network |
| `scripts/verify-kafka.ps1 -StaticOnly` | exit0; real Compose config parse/guard pass, report explicitly StaticOnly/Passed=false; no broker assertion |
| New Kafka PowerShell `Parser.ParseFile`, shell-file LF byte check | zero parser errors; shell sources LF |
| Git Bash `-n scripts/kafka/...` | exit1 before syntax parsing; MSYS `NtCreateDirectoryObject` denied (`0xC0000022`). Shell execution not verified here |
| Java21 `scripts/verify-kafka.ps1` | **exit1 at docker-engine**, project `aegisflow-kafka-verify-640d32dcef43411695f40b0f0b8e953b`; Passed=false, no Kafka resource created |

The initial subagent Maven attempt could not resolve `repo.maven.apache.org`; it failed before compilation. The later network-authorized parent build above succeeded without dependency/version substitutions. Independent review found that the broker API-version CLI could print per-broker errors while exiting0, so readiness was corrected before runtime use to an authenticated `kafka-topics.sh --list` request. No false readiness result is accepted.

Written live assertions remain **unexecuted**: authenticated broker readiness, two provisioning passes, each listener's metadata/topic settings, three source-published/Core-consumed exact markers and committed offsets, six sibling-topic write denials plus one Core write denial and one wrong-password rejection. Negative checks require Kafka's explicit authorization/authentication exceptions; timeouts fail. This smoke flow does not implement business outbox relay or database inbox/offset atomicity. The normal local `start-kafka.ps1` path and Kafka restart durability also remain runtime-unverified. No Kafka Testcontainers/fault/end-to-end pass, exactly-once effect or production security claim is made. The owner must run the same verifier through bundled PowerShell7; preserve its final result/first error and ignored sanitized logs. No PostgreSQL volume/configuration, remote repo or deployment was changed.

After an intentional interruption, the 16:15:20Z continuation confirmed the scoped files survived and finished current documentation. `scripts/verify-docs.ps1` exited0: Markdown=30 / Required=20 / LocalLinks=122 / Mermaid=7 / JSON=1 / Scenarios=16 / InterviewQA=30 / ADRs=9 / Issues=0. All Kafka PowerShell files parsed with zero errors; whitespace check passed. An incorrect ADR local filename was corrected before this pass. No previous Maven/PostgreSQL/guard run was repeated and no Kafka success was added by this documentation checkpoint.

## 2026-10-17 — persistent Compose gate PASSED on the owner's host

Test tooling source **`4c6585e`**, unchanged P0.2 applications. Owner ran `& 'C:\Users\User\.cache\codex-runtimes\codex-primary-runtime\dependencies\native\powershell\pwsh.exe' -NoProfile -File .\scripts\verify-compose-persistence.ps1` from the existing repository with Java 21 selected and working Docker Desktop. Final console reports **PASS**; actual `.cache/compose-persistence/aegisflow-p03-verify-93dcfedb932a45a1b5d28241d4c5098b/results.json` independently confirms **Passed=true / Stage=complete / VolumesRetained=true**. Captured/reviewed here at 05:40:58Z; transcript does not include numeric process exit, duration or exact run-end timestamp. This supersedes the pending host gate, not the preserved failures below.

| Observed assertion group | Result |
|---|---|
| Existing role bootstrap against four fresh databases, then the same retained volumes | Two complete passes |
| Same four packaged postgres-profile JARs on owner-only internal networks | Eight HTTP 200/UP checks; no foundation fallback |
| Flyway/history startup | Four fresh V1 applications; four second starts validate V1/up-to-date, no additional migration |
| Restricted runtime DML/catalog/DDL/history-write boundaries and separated roles | Eight owner SQL assertion groups passed |
| Three sibling runtime credentials rejected per owner, per pass | 24 denial attempts passed, not 24 separate JUnit tests |
| Immutable fixture sentinel and full Flyway-history snapshots across restart | All four identical, including history checksums/timestamps |

Actual report records official Java runtime `eclipse-temurin:21-jre-jammy@sha256:8d584faeef02c22f6b58ad8a3cee8e1e53ae52bdf2224f4afb989cc35983b8ae`; four PostgreSQL owners use the existing pinned 18.6 image. Per-owner redacted `*-pass-1.log`/`*-pass-2.log` corroborate real migrations/startup and repeated validation. Application artifact SHA-256 values:

| Owner | SHA-256 |
|---|---|
| Core | `2630576EE4FFDC5D03B1A2673C2E5C8695E83E8873D493E6E0FB6795B26C746F` |
| Order | `996E3BFFE1162A6F1F119611606812B27B10B84E60C7DB5EC6591C8FD1B0BDCF` |
| Payment | `C168C1602200164D351A82F4584B8DDBD4E8BCB1EFF3BE10D733B753A4741227` |
| Warehouse | `A44F4350C15BD7094D5220F9531AE23A0E150F234DD29228728E24D7210FB461` |

This is operational Compose/bootstrap/role/migration/persistence evidence, **not** a new Maven/Testcontainers business atomicity run, general security proof or Kafka/exactly-once result. P0.2's historical 38+29 tests remain unchanged. Independent focused evidence review found report/log/counts consistent, without running Docker/Maven again. All successful and failed verification volumes remain; no deletion/push/deployment performed. P0.3 persistence prerequisite is complete; Kafka infrastructure, relay/inbox and all Kafka fault/end-to-end assertions remain unimplemented/unverified. No further persistence rerun is needed merely to record this checkpoint.

Documentation-only closing check at **05:43:44Z**: `scripts/verify-docs.ps1` exit 0, Markdown=29 / Required=20 / LocalLinks=111 / Mermaid=7 / JSON=1 / Scenarios=16 / InterviewQA=25 / ADRs=8 / Issues=0; whitespace check passed. Current stale pending-rehearsal statements were reconciled without removing historical evidence. No runtime test was rerun to update these records.

## 2026-10-17 — Core really migrates/starts; health-probe format false-negative repaired

Source entry `99b908f`; owner's same Java 21/bundled PowerShell 7 `-File .\scripts\verify-compose-persistence.ps1` command. Project `aegisflow-p03-verify-ef3015775b284312a6be9d62b6176fb7` passed configuration, pinned JRE preparation, four database startups and first role bootstrap. The saved Core log at **05:23:58Z** records successful V1 migration against PostgreSQL 18.6; **05:23:59Z** records Tomcat/app startup in **11.418 seconds**; **05:24:00Z** records a successful runtime Hikari connection during health probing. These are observed startup/migration subsets, not a full readiness/privilege pass. The helper timed out at 120 seconds; result is **Passed=false / pass-1-core-application**. Other JARs, SQL fixtures/role assertions and second pass were not reached. All four verification volumes remain; no cleanup was performed.

Original health matcher rejected every body except a bare root UP. Existing `probes.enabled: true` and managed Boot 4.1.1 `SystemHealthDescriptor.getGroups()`/`HealthEndpointSupport.getCompositeDescriptor` bytecode establish that groups are not hidden by `show-details: never`. Additional root groups are the likely timeout cause; no actual failed response body was captured. The repaired matcher accepts root UP plus optional string-array groups in either member order and remains strict about duplicate/nested status, HTTP 200, valid detail-free shape and 4096-byte maximum. It never treats nested UP as root readiness. Fixed-category diagnostics now survive a future timeout without exposing raw bodies/secrets. No timeout, application configuration, database schema or dependency changed.

Exact available checks through bundled PowerShell 7, completed **2026-10-17T05:29:18Z**, combined exit 0:

| Command/check | Actual result |
|---|---|
| `-NoProfile -File .\scripts\test-rehearsal-probe.ps1` with Temurin JDK 21 | `javac --release 21` and Java test main succeeded; **23 passed / 0 failures**, including groups/order, duplicates, DOWN/nested status, malformed/trailing JSON, detail leakage and safe diagnostics; no network |
| `-NoProfile -File .\scripts\test-compose-persistence.ps1` | **19 passed / 0 failures**; no engine/PostgreSQL |
| `-NoProfile -File .\scripts\verify-compose-persistence.ps1 -StaticOnly` | exit 0; actual Compose configuration only, explicitly no container/database assertion |
| All-script PowerShell `Parser.ParseFile` | 0 parse errors |

A focused existing Core foundation JAR attempt with ephemeral port failed before HTTP with `SocketException: Permission denied: connect` / `Unable to establish loopback connection`; owned child stopped, ignored `.cache/core-health-payload.stdout.log`/`.stderr.log` retained. This is a coding-session restriction, not the owner's successful container startup. No successful HTTP reproduction, repaired Docker run, new Maven pass or Kafka test is claimed. Owner reruns the same host command; preserve volumes/first failure and do not repeat P0.2 implementation/builds merely to resume.

Closing `scripts/verify-docs.ps1` at **05:30:44Z** exited 0: Markdown=29 / Required=20 / LocalLinks=111 / Mermaid=7 / JSON=1 / Scenarios=16 / InterviewQA=25 / ADRs=8 / Issues=0. `git diff --check` passed. Independent focused tooling review found no blocker; review is not runtime verification.

## 2026-10-17 — real bootstrap succeeds; internal-network rehearsal repair

Entry/source `9c3b8c6`. Owner's exact host command, with Java 21 selected: `& $ps7 -NoProfile -File .\scripts\verify-compose-persistence.ps1`. Host timestamp/duration were not supplied. Project `aegisflow-p03-verify-e26461bf21cc43df8d20d260881a7808`: real Compose startup and **first role bootstrap of all four PostgreSQL databases succeeded**. It then terminated at `Get-RehearsalPublishedPort` before the first application JAR, Flyway startup or fixture/SQL/restart assertions. Result file has `Passed=false`; all four JAR hashes were recorded, not executed. Containers were stopped; the four project volumes were retained. This is a failed rehearsal with a successful bootstrap subset, not a database migration/privilege/retained-volume pass.

The original raw `compose port` output is unavailable. A direct local probe of the actual `Invoke-RehearsalDocker` function using `docker compose version --short` returned exactly one String `5.5.1`, not pipeline pollution. All four DBs are attached only to `internal: true` networks. A [Moby upstream report](https://github.com/moby/moby/discussions/53256) describes the same healthy internal-only container but missing host publication; the exact host cause remains inferred. Docker's [PostgreSQL networking guide](https://docs.docker.com/guides/postgresql/immediate-setup-and-data-persistence/) distinguishes host-published access from same-network container access. No security guard was loosened and no internal network was made external.

The repaired runner mounts each unchanged built JAR into an owner-network Java 21 verification container and uses `owner-db:5432`. No host port lookup is required. Its in-container probe is standalone Java 21 under `scripts/java/`, not an application/Maven change. Five owner environment names only (values passed through a filtered Docker CLI child environment), read-only JAR/probe/root, unprivileged UID, bounded tmpfs, no extra capabilities/privileges/host ports/socket, and exact-ID/label checks precede cleanup. Progress `Stage` and successful actual bootstrap checks are now saved even when a later gate fails. Only these temporary app containers may be removed; all database volumes remain.

Official [Docker Hub metadata](https://hub.docker.com/v2/repositories/library/eclipse-temurin/tags/21-jre-jammy), fetched with read-only `Invoke-RestMethod` on 2026-10-17, supplied index digest **`sha256:8d584faeef02c22f6b58ad8a3cee8e1e53ae52bdf2224f4afb989cc35983b8ae`** and Linux/amd64 manifest `sha256:8c2dddf1bb2a8455160f4e23080059de5003eddc5cb839130b177c6be0c2cfe0`. The runner pins tag+index, not a floating tag. Metadata retrieval is not an image pull, runtime or supply-chain security verification.

After the repair, exact local commands through bundled PowerShell 7:

| Command | Observed outcome |
|---|---|
| `-NoProfile -File .\scripts\test-compose-persistence.ps1` | exit 0; **19 passed / 0 failures**, includes four hardened owner-network container argv cases and exact-label cleanup rejection; no engine |
| `-NoProfile -File .\scripts\test-rehearsal-probe.ps1` | exit 0; real JDK 21 `javac --release 21` + `java` ran **10 passed / 0 failures** for strict healthy/unhealthy/malformed/duplicate/oversized responses; no HTTP/Docker |
| `-NoProfile -File .\scripts\verify-compose-persistence.ps1 -StaticOnly` | exit 0; real Compose merge/config checks only; no database/container startup |
| PowerShell all-script `Parser.ParseFile` | zero errors |

The guarded combined rerun exited 0 at **2026-10-17T05:19:55Z**. An initial new argv test failed because it expected the canonical `no-new-privileges` token while the helper used `no-new-privileges=true`; canonical spelling was aligned and all 19 passed, without removing the protection. Application entrypoint is explicitly `java`; root/mounts remain read-only. Live Docker still denies this session's engine pipe. No Maven/app regression run was performed for unchanged production code/POMs. **Real repaired JRE pull/startup, SQL grants/history/sentinel, retained-volume restart and all Kafka scenarios remain unverified.** Owner must rerun the corrected host command; no old volume deletion or completed P0.2 inspection/build is required.

## 2026-10-16 — P0.3-A tooling/static checkpoint; real Compose rehearsal blocked

Entry `86452ef`, clean `main` tracking `origin/main`; no Java/POM/business migration changes. The owner approved P0.3 and requested persistent-Compose validation before adding Kafka. Baseline P0.2 results below remain historical and valid. Compose CLI **5.5.1** is available. Direct `docker info --format '{{.ServerVersion}}'` **exit 1**, permission denied on `npipe:////./pipe/dockerDesktopLinuxEngine`, including a retry after the owner reported a version from their normal PowerShell. Host availability is not sandbox authorization.

| New exact command/check | Actual result |
|---|---|
| `& $ps7 -NoProfile -File .\scripts\test-compose-persistence.ps1` | 14 helper/safety tests passed; 0 failures; no Docker engine/PostgreSQL used |
| `& $ps7 -NoProfile -File .\scripts\verify-compose-persistence.ps1 -StaticOnly` | exit 0; actual Compose config merge validated 4 unique project volumes/networks and ephemeral loopback ports; explicitly no bootstrap/Java/migration/restart execution |
| `& $ps7 -NoProfile -File .\scripts\verify-compose-persistence.ps1` | exit 1 at Docker info after static config PASS; no Java/bootstrap/container/volume operation reached |
| In-process snapshot of all 24 owner env settings, `& .\scripts\verify-compose-persistence.ps1 -StaticOnly`, then equality assertions; repeat around caught real-mode engine failure | exit 0 after fix at 2026-10-16T15:20:31Z; exact original absence/empty/value states restored in both paths; no credential values printed |
| PowerShell `Parser.ParseFile` across all `scripts/**/*.ps1` | 0 parse errors; this is not PostgreSQL SQL validation |
| `& $ps7 -NoProfile -File .\scripts\verify-docs.ps1` | exit 0: Markdown=29 Required=20 LocalLinks=111 Mermaid=7 JSON=1 Scenarios=16 InterviewQA=25 ADRs=8 Issues=0 |
| `git diff --check` | passed; only Windows LF-to-CRLF notices |

`$ps7` is the existing bundled PowerShell 7 path documented in README/handoff. The 14 named regression groups check fresh project uniqueness, forbidden development/path targets, explicit Compose inputs, loopback endpoint acceptance/rejection, all four owner-only child environments, missing/foreign connection rejection, and exact absent/empty/existing credential cleanup. They test real helper functions with synthetic unit values, not substitutes for database/transport tests.

**Observed development failure preserved:** the first same-process restoration assertion exited 1 after static success: 24 originally absent environment keys were restored as present empty strings by the .NET/PowerShell string binding. No secrets were printed; this was inside a disposable tool process. `Restore-RehearsalEnvironment` now explicitly removes originally absent owned keys and restores strings otherwise. Three new regression groups and the successful whole-run equality assertions cover the correction. Static review also corrected SQL CASE syntax and used the harness administrator for fixture setup/history snapshots: a restricted migrator correctly cannot create another schema. Those SQL/runtime paths remain unexecuted, not passed by review.

The harness retains all verification volumes and emits only an informational explicit later cleanup command; no volume deletion was performed or authorized. Fresh bootstrap, actual role/grant SQL, eight postgres JAR startups, data/history comparison and foreign-credential denial are **written but unverified** pending the owner's normal host run. No Maven/build regression was rerun because Java/POMs are unchanged. No Kafka infrastructure, relay, inbox, metrics, broker outages, duplicate/crash/quarantine or end-to-end P0.3 scenario has been implemented/tested. **P0.3 incomplete; stop at this recoverable checkpoint and execute the first host gate before Kafka.**

## 2026-10-16 — final P0.2 integrated gate passed

Owner-supplied full host transcript, entry HEAD `66282a9`, application source `7a90c57`: Temurin Java 21.0.12.1, Maven wrapper 3.10.0, Docker Desktop 29.8.2, digest-pinned PostgreSQL 18.6. `verify-persistence.ps1` ran the exact root command `.\mvnw.cmd -B -ntp -Pinfrastructure-tests clean verify`. **All five reactor projects SUCCESS; BUILD SUCCESS / Maven exit 0**, finished **2026-10-16T19:45:47+05:30**, **02:17 min**.

| Owner | Default tests | PostgreSQL ITs | Failures / errors / skips |
|---|---:|---:|---|
| Core | 7 | 4 | 0 / 0 / 0 |
| Order | 9 | 8 | 0 / 0 / 0 |
| Payment | 12 | 9 | 0 / 0 / 0 |
| Warehouse | 10 | 8 | 0 / 0 / 0 |
| Integrated total | 38 | 29 | 0 / 0 / 0 |

All four independently managed V1 migrations, Hibernate validation where applicable, postgres contexts and the recorded database atomicity/rollback/replay/concurrency/constraint/optimistic-lock/privilege/immutable-outbox cases executed successfully. This is the final integrated invocation, not the earlier sum of separate runs. No Kafka delivery or universal exactly-once effect is claimed.

The same host transcript next records `verify-startup.ps1`: Core, Order, Payment and Warehouse executable JARs each returned **HTTP 200 / health UP**, respectively on temporary ports 55079/55083/55086/55088. The helper stops only its child processes. `verify-docs.ps1` passed: **Markdown=29 Required=20 LocalLinks=109 Mermaid=7 JSON=1 Scenarios=16 InterviewQA=20 ADRs=8 Issues=0**. No business HTTP functionality is inferred from health.

Two verification-wrapper issues are recorded rather than hidden:

- The exact outer host command was `& $ps7 -NoProfile -Command '$ErrorActionPreference = "Stop"; & .\scripts\verify-persistence.ps1; & .\scripts\verify-startup.ps1; & .\scripts\verify-docs.ps1'`. Windows PowerShell 5.1 native argument handling removed the nested quotes; the child emitted `Stop: The term 'Stop' is not recognized`. Therefore the claimed outer fail-fast setup did not work. The subsequent actual build/startup/docs all passed independently. The transcript did not separately print the outer process exit code. Future instructions use `& $ps7 -NoProfile` to enter PowerShell 7, then direct script invocations, avoiding this nested-quote boundary.
- The persistence helper printed **tests=4**, despite successful per-owner Maven results of 4/8/9/8. Its wildcard-with-filter report enumeration omitted simulator directories. The helper now enumerates four explicit owner directories, requires each nonempty suite, validates count fields, and rejects failures/errors/skips per owner. Its reporting function is verified against the four existing actual host XML reports as 29, without rerunning the database tests. No test is skipped/disabled/faked and no Java or migration was changed after this successful host build.

Local reporting verification: PowerShell parsed `scripts/verify-persistence.ps1`, extracted its actual `Get-PersistenceReportSummary` function via `FunctionDefinitionAst`, and invoked `Get-PersistenceReportSummary -RepositoryRoot (Get-Location).Path`. **Exit 0: Tests=29, Failures=0, Errors=0, Skipped=0** from the existing four real Failsafe XML files. Independent enumeration of all four Surefire XML directories gave **38/0/0/0**. No Java test rerun or substitute database was involved.

Negative reporting checks loaded the same actual function: a nonexistent report root was correctly rejected as missing Core reports; an isolated four-owner XML fixture with Warehouse `skipped=1` was correctly rejected. Temporary fixtures were removed without touching real reports. The initial function draft's live ordered-dictionary key iteration error was corrected to a fixed field-name list before these successful checks. Script parsing and `git diff --check` passed. Closing `& $ps7 -NoProfile -File .\scripts\verify-docs.ps1` **exited 0**: Markdown=29 Required=20 LocalLinks=111 Mermaid=7 JSON=1 Scenarios=16 InterviewQA=20 ADRs=8 Issues=0 (two new links since the host check).

**Milestone outcome:** P0.2 implementation and recorded build/PostgreSQL/startup gate complete; stop before unapproved P0.3. Persistent Compose bootstrap/manual operational rehearsal remains unexecuted; Compose parsing passed earlier but is not bootstrap execution. Prior failures and skips below remain historical evidence, not current blockers. No further owner-side rerun is needed merely for closing documentation/reporting.

## 2026-10-16 — Warehouse PostgreSQL rerun succeeds

Owner-supplied host output at source `7a90c57`, Java 21.0.12.1, Maven wrapper 3.10.0, Docker Desktop 29.8.2, digest-pinned PostgreSQL 18.6. Exact command from the existing repository:

```powershell
.\mvnw.cmd -B -ntp -pl simulators/warehouse-service -Pinfrastructure-tests clean verify
```

**BUILD SUCCESS / exit 0**, finished **2026-10-16T19:39:12+05:30**, **45.668 seconds**. Warehouse default tests: **10 passed, 0 failures/errors/skips** (command 3, event contract 3, proxy 1, foundation HTTP/context 2, architecture 1). `WarehousePersistenceIT`: **8 passed, 0 failures/errors/skips**, 15.48 seconds. The actual PostgreSQL container/bootstrap and Flyway V1 migration succeeded; Hibernate validated the schema and the `postgres` application context started. This verifies the proxy correction against a real database, not only the no-Docker regression.

| Latest successful host suite | Default tests | PostgreSQL ITs |
|---|---:|---:|
| Core, preceding root attempt | 7 | 4 |
| Order, preceding root attempt | 9 | 8 |
| Payment, preceding root attempt | 12 | 9 |
| Warehouse, this targeted run | 10 | 8 |
| Total across separate runs | 38 | 29 |

Every suite in this table has zero failures/errors/skips, but **this is not a successful full integrated root invocation**: the earlier root attempt exited 1 on the Warehouse defect. The default total counts each module's latest suite once; it does not add overlapping diagnostic reruns. The 29 PostgreSQL tests now provide observed owner-specific migration/state/outbox/rollback/replay/concurrency/constraint/locking/privilege evidence. The prior failures/skips below remain historical facts. Maven's two remote prefix-metadata HTTP 401 warnings were non-fatal in this run; no additional repository credentials were required for the successful build.

Local documentation verification through bundled PowerShell 7 (`& $ps7 -NoProfile -File .\scripts\verify-docs.ps1`) **exited 0**: Markdown=29 Required=20 LocalLinks=109 Mermaid=7 JSON=1 Scenarios=16 InterviewQA=20 ADRs=8 Issues=0. `git diff --check` also passed. This is documentation consistency evidence, not Mermaid rendering or another database run.

**Remaining:** run `verify-persistence.ps1` once for the final clean reactor plus all 29 ITs with zero skips; run current packaged `verify-startup.ps1` and repeat documentation validation after closing updates. Persistent Compose role-bootstrap execution remains unverified separately; no persistent volumes were changed. This checkpoint updates evidence only, without repeating implementation or starting P0.3.

## 2026-10-16 — real persistence passes for three owners; Warehouse proxy fix

Host `verify-persistence.ps1` rerun after UTC fix, ending **19:31:15 IST**, **2 minutes 48 seconds**, root **exit 1**:

| Owner | Default tests | PostgreSQL ITs | Result |
|---|---:|---:|---|
| Core | 7 passed | 4 passed | SUCCESS |
| Order | 9 passed | 8 passed | SUCCESS |
| Payment | 12 passed | 9 passed | SUCCESS |
| Warehouse | 9 passed | 8 errors | context startup failed |

All **37 default tests passed**, zero failures/errors/skips. **21 real PostgreSQL ITs passed**, zero skips; the **8 Warehouse ITs errored** because their common application context could not start. All four deployables compiled and packaged; all four V1 migrations were applied to real PostgreSQL. Warehouse's migration completed, but its behavioral assertions did not run.

The first Warehouse cause was `Cannot subclass final class ...IdempotencyRepository`. All four Warehouse adapters use `@Repository` without a proxy interface, so Spring's persistence exception translation requires a subclass. Removed `final` from `IdempotencyRepository`, `OutboxEventRepository`, `ReservationRepository`, and `ReservationLineRepository`; methods/visibility/tenant checks/transactions/SQL are unchanged. Utility components remain final.

Added `WarehouseRepositoryProxyTests`: a small real Spring context with the four actual repositories, exception-translation postprocessor and mocked JDBC/EntityManager dependencies asserts all four beans are CGLIB proxies. It exercises bean startup without claiming database execution.

Incrementally copied only newly cached managed artifacts from the read-only global cache into the existing ignored workspace cache; no dependency version change. Exact Java 21 local command:

```powershell
.\mvnw.cmd -B -ntp -o '-Dmaven.repo.local=.cache/maven-repository' -pl simulators/warehouse-service '-Dtest=WarehouseRepositoryProxyTests,CreateReservationCommandTests,WarehouseEventContractTests,WarehouseSimulatorArchitectureTest' -l .cache/p0.2-warehouse-proxy-regression.log test
```

**Exit 0**, finished **19:35:28 IST**, **18.406 seconds**, **8 passed/0 failures/errors/skips**: proxy context (1), command (3), event contract (3), architecture (1). Warehouse's 22 production and 6 test source files compiled with the actual managed JPA dependencies. Existing HTTP tests had already passed on the host; they were not selected in this focused sandbox run. No database was substituted in the integration tests or disabled.

Next diagnostic host command is Warehouse-only `mvnw.cmd -B -ntp -pl simulators/warehouse-service -Pinfrastructure-tests clean verify`, requiring its 8 ITs to execute without skips. After correction is proven, run the full persistence/startup/docs gate once to close P0.2. Prior Core/Order/Payment successes are retained; no need to repeat them while diagnosing Warehouse. Production Compose role-bootstrap execution and the new packaged startup rehearsal remain unverified.

## 2026-10-16 — PostgreSQL startup succeeds; JDBC timezone failure fixed

The next user host run confirmed the image-declaration fix: the pinned PostgreSQL container **started successfully**. Core's **6 default tests passed**, zero failures/errors/skips. The first administrative JDBC connection in `PostgresInfrastructureIT.configure` then failed with `FATAL: invalid value for parameter "TimeZone": "Asia/Calcutta"`. The other context errors were consequences of that first failure. Failsafe reported **4 errors, 0 failures/skips**; no migration or business database assertion passed. Root **exit 1**, **35.342 seconds**, finished **2026-10-16 19:24:20 IST**; simulators were not reached.

PgJDBC 42.7.13 sends `TimeZone.getDefault().getID()` in its connection-startup packet. Added `-Duser.timezone=UTC` to **both root Surefire and Failsafe fork arguments**, preserving Mockito instrumentation and every test. This changes only the Maven test JVMs, not Windows timezone, the Maven parent process or production launches. A new assertion in `PostgresImageConfigurationTests` verifies the actual fork default is UTC.

Exact coding-session regression command on Java 21:

```powershell
.\mvnw.cmd -B -ntp -o '-Dmaven.repo.local=.cache/maven-repository' -pl control-plane -Dtest=PostgresImageConfigurationTests -l .cache/p0.2-utc-handshake-regression.log test
```

**Exit 0**, **2 tests passed**, 0 failures/errors/skips, **10.720 seconds**, finished **2026-10-16 19:26:26 IST**. Core test sources compiled. The test fork log uses UTC (13:56:25), while Maven's final timestamp remains IST. Whitespace checks passed. Real PostgreSQL assertions after this fix still require the same host persistence-script rerun; no integration pass is inferred from the no-Docker regression.

## 2026-10-16 — host Docker run and pinned-image initialization fix

User ran the existing `scripts/verify-persistence.ps1` through bundled PowerShell 7.6.5 on the host, with Java 21.0.12.1. This is new host evidence, not a rerun inside the restricted coding session:

- Docker engine **29.8.2** was reachable through Testcontainers' local named pipe; Ryuk 0.14.0 pulled and started. The earlier sandbox Docker denial did not describe the host installation.
- Core main/test compilation and its **five default tests passed**, zero failures/errors/skips: HTTP health (1), architecture (4). Core packaged successfully.
- Failsafe then failed at class initialization: **1 error, 0 failures/skips**, before PostgreSQL startup/migrations or any database assertion. `PostgreSQLContainer(String)` rejected the exact `postgres:18.6@sha256:74935e72241653ca55e0414067e6d8763aceb8a810eb51b452253ec3dcfc4336` reference as not compatible with its `postgres` family.
- Root verification **exit 1**, **47.696 seconds**, finished **2026-10-16 19:10:03 IST**. Simulator reactor modules were not reached. The optional repository-prefix HTTP 401 warnings were not the failing operation.

Corrected all four IT declarations to pass `DockerImageName.parse(existingPin).asCompatibleSubstituteFor("postgres")` into the container constructor. The exact tag/digest, server version, test enablement and assertions are unchanged. This is an explicit image-family declaration, not a mutable-tag fallback or disabled compatibility test.

Added `control-plane/src/test/java/com/aegisflow/core/PostgresImageConfigurationTests.java`. Accessing the real IT's image field also executes its static PostgreSQLContainer constructor without starting Docker; the test checks successful initialization, exact canonical reference and compatibility. Exact local command:

```powershell
.\mvnw.cmd -B -ntp -o '-Dmaven.repo.local=.cache/maven-repository' -pl control-plane -Dtest=PostgresImageConfigurationTests -l .cache/p0.2-image-configuration-regression.log test
```

With the existing Java 21/workspace TEMP setup: **exit 0**, **1 test passed**, zero failures/errors/skips, **13.184 seconds**, finished **2026-10-16 19:13:57 IST**. Core's four test sources compiled. This proves the initialization fix in Core; it does not prove PostgreSQL startup/migration or simulator/JPA execution. Coding-session `docker info` still reports pipe permission denied, so no full integration rerun is claimed here. The next command is the same host `verify-persistence.ps1`; P0.2 remains gate-pending.

## P0.2 interruption evidence — 2026-10-13

This source checkpoint is incomplete and uncommitted at resumed entry. No PostgreSQL assertion has run yet. Prior P0.1 evidence below is historical.

- User-observed baseline: `./mvnw.cmd clean verify` on Java 21/Temurin 21.0.12.1, five reactor modules SUCCESS, 53.197 seconds, finished 2026-10-12 12:29:48 IST. The supplied reactor summary did not state test counts.
- `mvnw.cmd --version`: Maven 3.10.0, Java 21.0.12.1. The user JDK path exists.
- `docker info --format '{{.ServerVersion}}'`: initial sandbox attempt denied named-pipe access; the approved earlier-turn check returned 29.8.2. After permissions changed, sandbox engine access was denied again. No container result is inferred from the engine version.
- Initial targeted Maven compile: exit 1, protected global Maven-cache lock-file access failure. Automatic escalation was rejected by the granular approval policy.
- Existing Maven artifacts copied into ignored workspace `.cache/maven-repository`; no protected global cache mutation. Premature workspace compile while copy was incomplete: exit 1, unresolved Boot parent and Maven Central DNS. No compilation result.
- Exact completed-copy command: `.\mvnw.cmd -B -ntp -o '-Dmaven.repo.local=.cache/maven-repository' -l .cache/p0.2-clean-verify-offline.log clean verify`, with Java 21 and workspace TEMP/TMP.
- Result: **exit 1**, 24.594 seconds, finished **2026-10-13 00:39:17 IST**. Core main/test sources compiled. Five default tests executed: **4 pass, 0 failures, 1 error, 0 skips**. The four architecture tests passed. Health-context startup failed because Tomcat could not establish its internal loopback connection: `java.net.SocketException: Permission denied: connect`. Simulator modules were not reached.
- New simulator compile/unit/IT results, real PostgreSQL migrations/atomicity/constraints, Compose validation/runtime, and P0.2 packaged startup are **not executed/verified** at this checkpoint. P0.2 cannot be marked complete. No test was disabled or faked.

## Resumed P0.2 verification

### Resumed P0.2 checks — 2026-10-13 (Java 21)

All commands ran in the existing repository. `JAVA_HOME` selected the installed Temurin 21 path above; TEMP/TMP used `.cache/tmp`. Existing cached dependencies were copied into the writable ignored `.cache/maven-repository`; versions and the normal reactor were unchanged.

| Exact command/check | Observed result |
|---|---|
| `.\mvnw.cmd -B -ntp -o '-Dmaven.repo.local=.cache/maven-repository' -l .cache/p0.2-test-compile.log test-compile` | exit 1, 09:17:53 IST, 6.559 s; Core compilation succeeded/up-to-date; Order blocked by absent `spring-boot-starter-data-jpa:4.1.1` in the offline cache; remaining simulators not reached |
| `.\mvnw.cmd -B -ntp -o '-Dmaven.repo.local=.cache/maven-repository' -pl control-plane -Pinfrastructure-tests -Dtest=ControlPlaneArchitectureTests -l .cache/p0.2-core-available-checks.log verify` | exit 0, 09:19:44 IST, 31.152 s; **4 architecture tests passed**; **4 PostgreSQL ITs skipped**, zero failures/errors; Docker named-pipe access denied. This focused exit 0 is not the P0.2 gate |
| `.\mvnw.cmd -B -ntp '-Dmaven.repo.local=.cache/maven-repository' -pl simulators/order-service -am -l .cache/p0.2-online-test-compile.log test-compile` | exit 1, 09:21:36 IST, 3.917 s; dependency descriptor download blocked by `No such host is known (repo.maven.apache.org)`; no simulator compilation claimed |
| `docker info --format '{{.ServerVersion}}'` | exit 1, current engine pipe permission denied |
| `docker compose --profile postgres config --quiet` | exit 0; twelve temporary process-only password values, existing process values restored; no expanded secrets or persistent containers |
| PowerShell Parser.ParseFile over `bootstrap-databases.ps1`, `verify-persistence.ps1`, `verify-startup.ps1` | 0 syntax errors; static parse only, not database/runtime execution |
| `git diff --check` | exit 0; LF/CRLF conversion notices only |

An ignored diagnostic Maven harness at `work/persistence-unit-check/pom.xml` compiled the **actual existing pure source/test files** using the same Boot 4.1.1 parent, Java 21, and cached managed MVC/JDBC/test libraries. It did not replace the reactor, use substitute implementations, modify POM baselines, or exercise JPA/HTTP/database behavior. Initial harness compilation failed because its selected-source list omitted `IdempotencyRecordStore`; adding that existing source fixed the harness selection. No production test was disabled.

Successful diagnostic command (repository root; `$repoCache` is the absolute workspace cache path):

```powershell
$repoCache = Join-Path (Get-Location) '.cache\maven-repository'
.\mvnw.cmd -B -ntp -o "-Dmaven.repo.local=$repoCache" -f work/persistence-unit-check/pom.xml -l .cache/p0.2-isolated-pure-unit-rerun.log clean test
```

Exit **0**, **09:21:09 IST**, **11.055 s**, **21 tests, 0 failures/errors/skips**:

| Actual test class | Pass count |
|---|---:|
| `OrderPersistenceTests` | 6 |
| Payment `CanonicalJsonTest` | 3 |
| `PaymentCommandValidatorTest` | 6 |
| `CreateReservationCommandTests` | 3 |
| `WarehouseEventContractTests` | 3 |

The harness and raw logs are ignored local diagnostics; the original test classes are committed source and run in the normal reactor on a host with dependencies. The canonical producer fixtures/hash vectors, exact money/input validation and request material-change rules passed. These 21 unit passes do not prove transaction atomicity, persisted constraints, JPA wiring, or event delivery.

The resumed code review corrected Payment tenant-length/profile/fail-closed-check gaps and Warehouse's explicit Flyway Testcontainer URL override. All simulator PostgreSQL integration tests are written but **not executed** here. The full reactor, foundation/postgres startups, real migrations, rollback/commit/replay/concurrency/locking/privileges remain the next local gate. `scripts/verify-persistence.ps1` runs the normal clean reactor plus all Testcontainers tests and rejects any skipped IT; it must pass before marking P0.2 complete.

Pre-checkpoint documentation validation passed: **Markdown=29 Required=20 LocalLinks=108 Mermaid=7 JSON=1 Scenarios=16 InterviewQA=20 ADRs=8 Issues=0**. Working/staged whitespace checks passed and no private/generated/cache/work paths were staged. One initial checkpoint shell wrapper exited 1 after mistakenly reading unset native `$LASTEXITCODE` for a successful PowerShell documentation script; it stopped before staging. The corrected wrapper used terminating PowerShell errors and native status checks only for native commands, and passed. This was a wrapper guard error, not a documentation-check failure.

`git commit -m 'feat(persistence): checkpoint P0.2 state and transactional outbox'` exited 0 as **`14e04f6d0cb96520ae5ba1451c10890860df3379`**, 101 scoped files, 4,778 insertions and 135 deletions. `git status --short --branch` then reported clean `main`. No private/generated diagnostic files were committed; no remote is configured and no push/deployment occurred. The following docs-only continuity commit records the source identifier and changes no tested source. This is a recoverable checkpoint with explicit failed/skipped/unverified gates, not a completed P0.2 milestone.

## Historical P0.1 results (unchanged)

**Observed:** 2026-10-12, Windows / PowerShell 7.6.5. This is foundation evidence, not production certification or product evaluation. Source milestone: `cb7bfafed49a58f08cba2352aafb8d7ecf8f6e33`; tests ran against its exact source before committing. [HANDOFF.md](../HANDOFF.md) records continuation status. Phase 0 history remains at `2bae1f3` and `6f6daaf`.

## Environment and resolved baseline

Host defaults were Java 26 and Maven 3.9.15; neither is the accepted build runtime. This session used an isolated Temurin **21.0.12.1+1-LTS** and the committed wrapper selecting **Maven 3.10.0**. No global Java/Maven settings were changed. Docker CLI 29.8.2 and Compose 5.5.1 were installed, but the Docker engine was unavailable.

Actual resolved versions: Boot 4.1.1, Framework 7.0.9, Modulith 2.1.1, Flyway 12.4.0, PostgreSQL JDBC 42.7.13, JUnit 6.0.3, Testcontainers 2.0.5, Mockito 5.23.0. Compiler plugin 3.15.0 and Surefire/Failsafe 3.5.6 are Boot-managed. Enforcer 3.6.2 is explicit. AI, Kafka and Spring Security are not application dependencies in P0.1. [ADR 0006](../adr/0006-technology-baseline-and-version-pinning.md) preserves official compatibility references.

The local JDK used was `../../work/toolchains/jdk-21.0.12.1+1` relative to this repository, outside its Git history. For a different machine, install a Java 21 JDK and substitute its location. The [official Temurin download](https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.12.1%2B1/OpenJDK21U-jdk_x64_windows_hotspot_21.0.12.1_1.zip) was SHA-256 checked against `f9d6e191ab098c0d416e7d588a24420a8621cd2f4720dab2459b8b7b2d2d8b4e`. Java is not vendored.

Maven Wrapper implementation 3.3.4 uses `only-script`; `.mvn/wrapper/maven-wrapper.properties` pins the Maven ZIP and SHA-256 `1f6d9909266510f039f59aa0e13dcd2c66da85f043e56276e41f2918f8bddaff`. The downloaded ZIP was also compared with the official SHA-512. POSIX requires `unzip`; only Windows wrapper execution was tested.

## Reproduce the acceptance checks

Run from the repository root. On this host the exact JDK path was `C:\Users\User\Documents\Codex\2026-10-11\referenced-chatgpt-conversation-this-is-an\work\toolchains\jdk-21.0.12.1+1`. First downloads need network access and a writable Maven cache.

```powershell
$env:JAVA_HOME = 'C:\path\to\your\jdk-21'
New-Item -ItemType Directory -Force .cache/tmp | Out-Null
$env:TEMP = Join-Path (Get-Location) '.cache/tmp'
$env:TMP = $env:TEMP
.\mvnw.cmd --version
# Deliberately test isolation from an unrelated caller profile:
$env:SPRING_PROFILES_ACTIVE = 'postgres'
.\mvnw.cmd -B -ntp -l .cache/p0.1-final-clean-verify.log clean verify
if ($LASTEXITCODE -ne 0) { throw 'Build/test gate failed' }
.\scripts\verify-startup.ps1
.\scripts\verify-docs.ps1
Remove-Item Env:SPRING_PROFILES_ACTIVE
.\mvnw.cmd -B -ntp -l .cache/p0.1-dependencies.log dependency:tree
.\mvnw.cmd -B -ntp -N help:effective-pom -Doutput=.cache/effective-pom.xml
.\mvnw.cmd -B -ntp -l .cache/p0.1-infrastructure-tests.log -pl control-plane -Pinfrastructure-tests verify
```

The last command can exit 0 **with a skipped infrastructure test**. Read Failsafe's test counts; do not infer PostgreSQL readiness from the exit code. `target/` reports and `.cache/` logs are ignored/generated; the factual summaries here survive checkout. `clean` removes previous target reports.

## Command ledger

Times are IST (UTC+05:30) on 2026-10-12.

| Command | Observed result |
|---|---|
| `.\mvnw.cmd --version` with Java 21 selected | exit 0; Maven 3.10.0 / Java 21.0.12.1 |
| `.\mvnw.cmd -B -ntp -l .cache/p0.1-verify-final.log verify` | exit 0, finished 01:59:19; all five reactor projects successful; 14 tests, 0 failures/errors/skips |
| `.\mvnw.cmd -B -ntp -l .cache/p0.1-clean-verify.log clean verify` | exit 0, finished 10:03:06; 14 tests, 0 failures/errors/skips; four executable JARs |
| `.\mvnw.cmd -B -ntp -l .cache/p0.1-infrastructure-tests.log -pl control-plane -Pinfrastructure-tests verify` | exit 0, finished 10:03:42; Core's 5 default tests passed; PostgreSQL IT: 1 test, **1 skipped**, 0 failures/errors |
| `.\mvnw.cmd -B -ntp -l .cache/p0.1-dependencies.log dependency:tree` | exit 0, finished 10:06:20; four child-module dependency trees inspected |
| `.\mvnw.cmd -B -ntp -N help:effective-pom -Doutput=.cache/effective-pom.xml` | exit 0, finished 10:06:32; parent/BOM/plugin inheritance inspected |
| `.\scripts\verify-startup.ps1` | exit 0; first complete four-JAR check finished 10:09:08; all HTTP 200 / `UP` |
| `.\mvnw.cmd -B -ntp -l .cache/p0.1-profile-isolation-verify.log verify` with caller profile `postgres` | exit 0, finished 10:09:57; all four test contexts selected `foundation`; 14 tests, 0 failures/errors/skips |
| `.\mvnw.cmd -B -ntp -l .cache/p0.1-final-clean-verify.log clean verify` with caller profile `postgres` | exit 0, finished **10:14:33**; final source clean-compiled, repackaged; **14 tests, 0 failures/errors/skips** |
| `.\scripts\verify-startup.ps1` immediately after final clean build | exit 0, finished **10:14:58**; all four new executable JARs HTTP 200 / `UP`; owned processes stopped |
| `.\mvnw.cmd -B -ntp -l .cache/p0.1-checkpoint-clean-verify.log clean verify` with caller profile `postgres`, after literal-loopback test fix | exit 0, finished **10:21:16**; final checkpoint source clean-compiled, repackaged; **14 tests, 0 failures/errors/skips** |
| `.\scripts\verify-startup.ps1` after checkpoint build | exit 0; Core, Order, Payment, Warehouse all HTTP 200 / `UP`; owned children stopped |
| `.\scripts\verify-docs.ps1` | exit 0 at 10:25:09; Markdown=28, required=20, local links=97, Mermaid fences=7, JSON examples=1, failure scenarios=16, interview Q&A=15, ADRs=7, issues=0 |
| `git diff --check` | exit 0; LF/CRLF conversion notices are informational, not failures |
| `git diff --cached --check` / `git commit -m "feat(foundation): establish verified P0.1 application skeletons"` | both exit 0; milestone `cb7bfaf`, 73 scoped files; clean working tree immediately after source commit |
| `docker compose --profile postgres config --quiet` with four temporary password environment values | exit 0; configuration parsed; no expanded credential output stored |
| `docker info` | exit 1; Docker engine named pipe unavailable; no database startup possible |
| `docker desktop start` | did not establish a usable engine within the bounded attempt; interrupted, exit 1; no installation/WSL changes made |

For Compose validation, either privately fill an ignored `.env` from `.env.example`, or generate validation-only process-environment values without printing them:

```powershell
$passwordVariables = 'CORE_DB_PASSWORD','ORDER_DB_PASSWORD','PAYMENT_DB_PASSWORD','WAREHOUSE_DB_PASSWORD'
try {
  foreach ($name in $passwordVariables) {
    [Environment]::SetEnvironmentVariable($name, [Guid]::NewGuid().ToString('N'), 'Process')
  }
  docker compose --profile postgres config --quiet
  if ($LASTEXITCODE -ne 0) { throw 'Compose configuration failed' }
} finally {
  foreach ($name in $passwordVariables) { [Environment]::SetEnvironmentVariable($name, $null, 'Process') }
}
```

Use that temporary-value check in a **fresh terminal with no existing credential variables**; it is configuration validation, not persistent database setup. Real local databases need stable private credentials. Compose does not pass `.env` automatically to independently started Java processes.

## Default tests: final Surefire XML counts

| Suite | Tests | Failures | Errors | Skips |
|---|---:|---:|---:|---:|
| `AegisFlowCoreApplicationTests` | 1 | 0 | 0 | 0 |
| `ControlPlaneArchitectureTests` | 4 | 0 | 0 | 0 |
| `OrderSimulatorApplicationTests` / `OrderSimulatorArchitectureTest` | 2 + 1 | 0 | 0 | 0 |
| `PaymentSimulatorApplicationTests` / `PaymentSimulatorArchitectureTest` | 2 + 1 | 0 | 0 | 0 |
| `WarehouseSimulatorApplicationTests` / `WarehouseSimulatorArchitectureTest` | 2 + 1 | 0 | 0 | 0 |
| **Total** | **14** | **0** | **0** | **0** |

`control-plane/src/test/java/com/aegisflow/core/ControlPlaneArchitectureTests.java` executed `ApplicationModules.verify()`, exact ten-module discovery, explicit zero allowed cross-module dependencies, and Core-to-simulator import rejection. Each simulator's architecture test inspected production bytecode for Core/sibling references. Enforcer passed Java/Maven and banned-deployable-dependency rules in every module. These are build-time architectural checks, **not tenant/security or distributed correctness tests**.

## Standalone packaged application check

The final check launched separate Java processes from the four executable JARs, not only test contexts. Ports were allocated dynamically, so a pre-existing service could not satisfy the check.

| Application | Final check port | HTTP / health | Profile |
|---|---:|---|---|
| Core | 61175 | 200 / `UP` | foundation |
| Order | 61179 | 200 / `UP` | foundation |
| Payment | 49256 | 200 / `UP` | foundation |
| Warehouse | 59585 | 200 / `UP` | foundation |

The check finished at 10:21:43 IST. The script also rejected internal health `components`/`details`. Its `finally` block stopped only its own children; nothing is intentionally left running. Dynamic ports are observations, not ports to copy into configuration. Default manual ports remain 8080–8083.

## Failures and fixes retained honestly

1. Initial wrapper attempts encountered restricted-host DNS/download, temporary-directory rename, and Maven-cache write failures. Network/cache access and a workspace temporary directory allowed the official distribution to resolve. No artifact was assumed downloaded from an unsuccessful attempt.
2. An unsupported Java 26 diagnostic was rejected by Enforcer. A later diagnostic temporarily bypassed Enforcer by command-line flag, not by changing the rule; it is **not acceptance evidence**. The Java 21 gate remains enabled in the repository.
3. The first Java 21 build compiled Core and started its context but hung during Mockito runtime instrumentation attachment (`javatool` named-pipe access). It was interrupted, exit 1, with no completed test success. Startup `-javaagent` instrumentation in Surefire fixed the restricted-host attachment problem; subsequent runs passed without disabling tests.
4. A clean attempt then failed on a locked `target/m2-repository/...logback.jar` from a diagnostic process. The owned diagnostic process was stopped; the later clean builds succeeded. No destructive Git cleanup/reset was used.
5. Two initial standalone checks received HTTP 200 from Core but failed interpreting JSON because PowerShell returned response content as bytes. The helper now UTF-8 decodes byte content before JSON assertions; the full four-app reruns passed. Those initial attempts were failures, not four-app successes.
6. Review caught the POSIX wrapper's ZIP-to-tar fallback conflicting with the ZIP checksum. It now explicitly requires `unzip`. POSIX behavior has been inspected but **not executed**. Review also caught simulator tests depending on the caller's default profile; explicit `@ActiveProfiles("foundation")` and the two `postgres`-caller reruns verify isolation.
7. A final read-only review reported no blockers, but noted a portability risk from tests calling `localhost` while servers bind `127.0.0.1`. All HTTP tests now use the literal loopback address; the checkpoint clean build and standalone startup rerun passed. This was a preventive fix, not an observed failing IPv6 test.

## What this does not prove / next gate

- PostgreSQL-profile application startup, Docker image execution, JDBC/Flyway integration, migrations and database permissions are **unverified**; the optional IT skip is recorded above.
- No fresh-machine checkout, Linux/POSIX execution, CI, deployment, load/security test or Mermaid rendering was performed.
- There are no business APIs, durable business records, Kafka processing, AI, recovery, authentication or verified business effects. All `FM-*` failure scenarios and product quality/performance metrics remain future work, not passed tests or measured results.
- The next milestone is **P0.2, only after new approval**: unblock Docker, pass real PostgreSQL tests without skips, establish restricted service-owned persistence and migrations, then prove atomic state/outbox commit and rollback. Kafka transport remains P0.3.

# Test evidence — current P0.2 checkpoint and preserved P0.1

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

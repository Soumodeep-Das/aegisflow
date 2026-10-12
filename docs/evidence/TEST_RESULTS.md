# P0.1 test evidence

**Observed:** 2026-10-12, Windows / PowerShell 7.6.5. This is foundation evidence, not production certification or product evaluation. The source checkpoint is identified in [HANDOFF.md](../HANDOFF.md); tests ran against its source before committing. Phase 0 history remains at `2bae1f3` and `6f6daaf`.

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

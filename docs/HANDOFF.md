# Handoff — P0.1 foundation

**Snapshot:** 2026-10-12, completion session; final UTC checkpoint timestamp below.
**Authorization:** Phase 1 milestone P0.1 only. Owner approved it, paused, then requested continuation. **Stop here: P0.2 is not approved.**
**Repository:** independent local Git repository `outputs/aegisflow`, branch `main`.
**Remote / push / external deployment:** none configured or performed.

## Current commit and status

Phase 0 baseline `2bae1f3e13686194011c005c202a872f01a99815` and continuity checkpoint `6f6daafe58b2eb916c373e4c49853d78d8909562` are preserved. The actual P0.1 source commit and final status are recorded in the completion entry below. Generated `target/`, `.cache/`, toolchains and private `.env` files are not committed.

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

The local source commit, final UTC timestamp and clean-tree verification are recorded after the checkpoint succeeds.

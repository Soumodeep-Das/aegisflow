# Development journal

## 2026-10-17 — repair rehearsal's host-versus-internal-network mismatch

Continued at clean `9c3b8c6`, main ahead1, without redoing P0.2. Owner's real run created project `aegisflow-p03-verify-e26461bf21cc43df8d20d260881a7808` and bootstrapped all four PostgreSQL role sets. It then failed the original loopback-port guard before Java/migrations/SQL/second pass; `Passed=false` and four retained volumes preserved. Raw port text was not captured. Local actual-function CLI probe returned one clean String `5.5.1`; upstream Moby reports match internal-only networks not yielding host publications, but exact host packet cause is not proven. No network guard was relaxed or volume removed.

Repaired the test harness, not business code: same built JAR now runs in a pinned Java 21 container on only its owner's existing internal network, JDBC `owner-db:5432`. Owner-only five env values are copied through filtered CLI environment, not password argv; RO root/mounts, non-root UID, tmpfs, drop-all/no-new-privileges, no exposed application port/socket, and exact-ID/project-label cleanup preserve boundaries. Added standalone Java health probe and no-network contract tests; progress stages/actual successful bootstrap checks now survive later failures. Official Docker Hub metadata pinned the runtime index; image pull/real execution is still pending.

Guarded bundled-PS7 rerun at 2026-10-17T05:19:55Z: `test-compose-persistence.ps1` **19 pass/0 fail**, `test-rehearsal-probe.ps1` compiled with actual JDK21 and **10 pass/0 fail**, `verify-compose-persistence.ps1 -StaticOnly` exit0, all PS scripts parsed. Initial argv spelling mismatch test failed, then canonical no-new-privileges token aligned and all passed. These are not PostgreSQL/Kafka/HTTP integration passes. Engine pipe remains denied here; exact pipe permission request could not be represented, and no alias/security-setting bypass was attempted. No production Java/POM/V1 changes, no Maven rerun or remote push.

Updated evidence, README/architecture/learning/limitations/roadmap/handoff to the new actual flow and known partial host result. Highest-priority next task: owner reruns corrected isolated rehearsal (same command) and shares summary/first error; keep old failed-run volumes. Only after eight in-container JAR health checks plus SQL/retained-history assertions pass may Kafka infrastructure begin. P0.3 incomplete; P0.4 unapproved. Commit scoped work locally, no push/deployment.

## 2026-10-16 — approve P0.3; prepare isolated Compose persistence gate

Resumed from clean `main` at `86452ef`, now tracking `origin/main` (owner reports pushing P0.2). Owner explicitly approved P0.3 only and asked for limited-credit, small recoverable checkpoints. No new remote push/deployment/P0.4 authority. Targeted inspection reused existing bootstrap, credentials/profiles, module/event/data contracts, ADRs and failure requirements; no P0.2 code or architectural audit was recreated. The prompt's ingestion module maps to existing Core `eventing`; its explicit deferral of correlation/projection overrides older P0.3 plan wording, recorded without adding another module.

First task is persistent-Compose validation before Kafka. `docker info --format '{{.ServerVersion}}'` exited 1 with engine-pipe permission denied, while `docker compose version --short` reported 5.5.1. Owner later reported a version in their normal terminal; a fresh coding-session probe still denied access. Explained that Resource Saver/idle containers differ from sandbox permissions; starting another project's container is not a prerequisite. Did not request unsafe pipe/administrator changes.

Implemented a unique-project/ephemeral-loopback override, explicit optional Compose inputs on the existing bootstrap helper, and a two-pass real host rehearsal using unchanged built Java 21 JARs. Child environments strip sibling DB/Spring/JVM overrides and receive only their owner's connection credentials. Test-only SQL commits a sentinel and checks actual runtime/role grants; subsequent same-volume recreation compares sentinel and complete Flyway history. Credentials are random/transient, logs redacted, private `.env` untouched. Owned children/containers are stopped; all volumes remain, with explicit manual cleanup only after separate approval.

Bundled PowerShell 7 `-File scripts/test-compose-persistence.ps1` passed 14 safety/helper groups. `-File scripts/verify-compose-persistence.ps1 -StaticOnly` exited 0, real Compose config/isolation check only. Full `-File scripts/verify-compose-persistence.ps1` exited 1 at Docker info, before Java/bootstrap/resources. First same-process env assertion failed because originally absent variables became empty; fixed explicit owned-key removal vs string restoration and added absent/empty/existing regressions. Retest at 2026-10-16T15:20:31Z passed all 24-key restoration assertions after both static success and denied-engine failure, plus 14 helper tests and all-script parsing. Static review fixed SQL CASE syntax and correctly used test admin rather than migrator for test-schema setup/history. SQL/bootstrap/restart branches remain unexecuted.

Documentation check passed: 29 Markdown, 20 required, 111 links, 7 Mermaid, 1 JSON, 16 scenarios, 25 Q&As, 8 ADRs, zero issues; whitespace passed. Exact commands/results and limitations are in evidence/handoff. README, plan/roadmap, architecture/data/event scope, guide exercise and Q21–Q25, limitations and working agreement are updated. No new Java/Maven dependencies, no Maven rerun, no Kafka or transport test claim. P0.2's 38+29 passes are preserved as history, not recounted as new tests.

Checkpoint these scoped changes locally, then await the owner's real isolated rehearsal. Highest-priority next task: four successful fresh/restarted bootstraps/migrations, eight postgres JAR health checks, runtime/sibling credential assertions and identical retained-volume data/history. Preserve the exact first failure if any. Only after that gate add minimum Kafka infrastructure and continue B/C/D; P0.3 is incomplete. No volume deletion, history rewrite, push or deployment occurred.

## 2026-10-16 — P0.2 exit gate complete; stop before P0.3

Continued from clean `66282a9` using the owner's final full transcript; application source remains `7a90c57`. The host `verify-persistence.ps1` ran `.\mvnw.cmd -B -ntp -Pinfrastructure-tests clean verify`: all five projects SUCCESS, Maven exit 0 at **19:45:47 IST**, **2:17**. **38 default + 29 real PostgreSQL tests** passed, zero failures/errors/skips (Core 7/4, Order 9/8, Payment 12/9, Warehouse 10/8). All four V1 migrations and the recorded database guards/transactions passed. The same invocation then passed all four executable-JAR foundation health checks and documentation validation (29 Markdown, 20 required, 109 links, 7 Mermaid, 1 JSON, 16 scenarios, 20 Q&As, 8 ADRs, zero issues).

The suggested cross-PowerShell `-Command` wrapper lost quotes around Stop on the owner's Windows PowerShell 5.1, emitting an initial command error. This did not invalidate the independent successful checks, but its outer fail-fast claim was incorrect. Future instructions enter bundled PowerShell 7 first and run scripts directly. The helper's printed IT total was 4 because wildcard/filter enumeration missed simulator reports. Corrected it to explicit required owner directories, nonempty suites and validated zero-failure/error/skip counts; checked reporting against actual existing XML without rerunning the database suite. No Java/SQL/domain behavior changed after the successful host run.

Loaded the actual `Get-PersistenceReportSummary` definition via PowerShell AST (without executing Docker/Maven startup) and called `Get-PersistenceReportSummary -RepositoryRoot (Get-Location).Path`: exit 0, 29/0/0/0 from real Failsafe XML. Independent real Surefire XML totals were 38/0/0/0. Missing report root and isolated Warehouse-skipped-report fixtures were correctly rejected; temporary fixtures were removed. A draft live-dictionary-key iteration error was corrected to a fixed field-name list before these successful checks. Closing `& $ps7 -NoProfile -File .\scripts\verify-docs.ps1` exited 0: Markdown=29 Required=20 LocalLinks=111 Mermaid=7 JSON=1 Scenarios=16 InterviewQA=20 ADRs=8 Issues=0; whitespace/script parsing passed. No database tests were repeated or faked.

Updated current README, architecture/data model/invariants, guide/limitations, ADR status, roadmap/plan, evidence and handoff to the completed P0.2 state while preserving historical failures. The original repository/history and source progress remain intact. Persistent Compose bootstrap remains an unexecuted operational rehearsal, not silently claimed as tested. All changes stay local; no pushes, deployments, secrets or volume operations. Stop here and require explicit P0.3 approval; the owner need not rerun completed tests merely to record the milestone.

## 2026-10-16 — Warehouse database verification passes; final acceptance remains

At 2026-10-16T14:10:12Z, continued from clean `main` at `7a90c57` using the owner's new host result, without repeating implementation or architectural review. Exact host command: `.\mvnw.cmd -B -ntp -pl simulators/warehouse-service -Pinfrastructure-tests clean verify`. **BUILD SUCCESS / exit 0**, finished 19:39:12 IST, 45.668 seconds: **10 default + 8 real PostgreSQL tests**, zero failures/errors/skips. Docker Desktop 29.8.2/PostgreSQL 18.6, bootstrap, Flyway V1, Hibernate validation and postgres context ran. The proxy defect is now verified fixed against the database.

Latest per-module successes total **38 default tests and 29 PostgreSQL ITs across separate host invocations**. This is not a single successful root reactor run; the earlier root Warehouse failure remains recorded below. Recorded the exact result and final acceptance command in README/evidence/handoff/roadmap/guide/limitations, preserving earlier failures. No source or migration changes were needed. Maven's metadata HTTP 401 warnings were non-fatal; no credential change is warranted by this successful run.

Local `& $ps7 -NoProfile -File .\scripts\verify-docs.ps1` exited 0: Markdown=29 Required=20 LocalLinks=109 Mermaid=7 JSON=1 Scenarios=16 InterviewQA=20 ADRs=8 Issues=0; `git diff --check` passed. No application tests were rerun for documentation-only edits.

Next: one final host `verify-persistence.ps1`, then current packaged `verify-startup.ps1` and `verify-docs.ps1` through bundled PowerShell 7, stopping at the first error. Require root success, all 29 ITs without skips, four packaged foundation health checks, and zero documentation issues before marking P0.2 complete. Persistent Compose bootstrap remains a separate unexecuted rehearsal limitation. This documentation-only checkpoint stays local; no push/deployment/volume operation or P0.3 work.

## 2026-10-16 — Warehouse repository proxy fix after three persistence suites pass

Host root run after UTC fix ended at 19:31:15 IST, 2:48, exit 1. All 37 default tests passed. Real PostgreSQL ITs passed for Core (4), Order (8), Payment (9), with zero skips; all four V1 migrations applied. Warehouse's 8 ITs errored during shared context startup, before behavioral assertions, because its final IdempotencyRepository could not be subclassed for Spring exception translation.

From clean `bd3a1be`, removed final from exactly the four Warehouse Repository adapters; no methods, access scopes, tenant guards, SQL or transaction boundaries changed. Added a small Spring-context regression creating all four exception-translation proxies with mock persistence dependencies. A focused read-only review confirmed the three final utility components have no advisor and need no change.

Reused the existing writable Maven cache, adding only newly available managed artifacts from the owner's successful host build. `mvnw.cmd -B -ntp -o '-Dmaven.repo.local=.cache/maven-repository' -pl simulators/warehouse-service '-Dtest=WarehouseRepositoryProxyTests,CreateReservationCommandTests,WarehouseEventContractTests,WarehouseSimulatorArchitectureTest' -l .cache/p0.2-warehouse-proxy-regression.log test` exited 0 at 19:35:28 IST, 18.406 seconds: **8 passed/0 failures/errors/skips**. All Warehouse main/test sources compiled. Real Warehouse database tests after the proxy fix still require host Docker; the new context test is not a database pass.

Updated current evidence/handoff/guide/limitations/roadmap and preserved failures. Next: Warehouse-only host verify; when that passes, one final full persistence/startup/docs gate. P0.2 remains incomplete, P0.3 unapproved, all commits local. No history rewrite, credential exposure or database volume operation.

## 2026-10-16 — normalize test JVM timezone before JDBC startup

The owner's rerun passed 6 Core default tests and started the pinned PostgreSQL container, confirming the previous image fix. It then failed on the first JDBC bootstrap connection: PostgreSQL rejected the startup TimeZone value Asia/Calcutta. Four IT errors were reported (0 failures/skips), reactor exit 1 at 19:24:20 IST after 35.342 seconds; migrations and simulators were not reached.

Starting from clean local fix commit `79f46a5`, added `-Duser.timezone=UTC` to root Surefire and Failsafe argLine while retaining the Mockito agent. PgJDBC reads the default JVM timezone before authentication, so a container-only timezone or a later SQL SET would not solve this startup error. The host Windows timezone and production process defaults remain unchanged.

Added a fork-timezone assertion beside the existing image-declaration regression. `mvnw.cmd -B -ntp -o '-Dmaven.repo.local=.cache/maven-repository' -pl control-plane -Dtest=PostgresImageConfigurationTests -l .cache/p0.2-utc-handshake-regression.log test` on Java 21 exited 0 at 19:26:26 IST, 10.720 seconds, **2 passed/0 failures/errors/skips**. Core test sources compiled. No full PostgreSQL rerun was possible in the restricted coding session; next task remains the same host verification script. Updated README/guide/evidence/handoff/roadmap/limitations; no application behavior, migration, dependency or P0.3 change.

## 2026-10-16 — fix Testcontainers digest image declaration after host run

The owner's first host persistence run used bundled PowerShell 7.6.5 and Java 21.0.12.1. Docker 29.8.2 and Ryuk worked; five Core default tests passed. Core's PostgreSQL IT then failed during static initialization because Testcontainers did not accept the tag-plus-digest image reference as its postgres family. Failsafe recorded one initialization error (zero assertion failures/skips), root exit 1 at 19:10:03 IST after 47.696 seconds; simulators and PostgreSQL migrations were not reached.

Reused the clean existing repository at `98a670d`; no repeated architecture review or business implementation. Fixed all four IT constructors with explicit `DockerImageName.parse(pin).asCompatibleSubstituteFor("postgres")`, preserving the existing immutable pin. Added a no-Docker regression test that initializes Core's actual IT container declaration and verifies the unchanged canonical image name/compatibility.

`mvnw.cmd -B -ntp -o '-Dmaven.repo.local=.cache/maven-repository' -pl control-plane -Dtest=PostgresImageConfigurationTests -l .cache/p0.2-image-configuration-regression.log test` on Java 21 exited 0 at 19:13:57 IST, 13.184 seconds, 1 pass/0 failures/errors/skips. Core test sources compiled. Docker info still fails in the coding sandbox; this is distinct from the successful host engine connection. The remaining gate is the same host persistence script, followed only on success by packaged startup/docs checks. No real PostgreSQL result or P0.2 completion is claimed.

Updated evidence, handoff, roadmap, guide and limitations with this failure/fix and exact next step. No migration, application behavior, dependency version, secret, Kafka or P0.3 change was made. Commit details are recorded in the following continuity note.

Chronological factual record. Commands and results belong here; aspirations belong in the roadmap. Timestamps use UTC, with local context noted when useful.

## 2026-10-11 — Phase 0 kickoff

**Session timestamp:** started before 2026-10-11T15:50:23Z (21:20:23 IST)
**Authorization:** documentation-only Phase 0; no application source, deployment, or remote push.

### Repository observation

- The provided workspace contained only generated `work/` and `outputs/` directories.
- It was not a Git repository and contained no project or `AGENTS.md` guidance.
- To avoid modifying unrelated repositories, created the isolated project at `outputs/aegisflow` and initialized local branch `main`.
- No remote was added.

### Work performed

- Defined the product boundary, business workflow, measurable requirements, and competitor-aware position.
- Specified system/data/event/state/invariant architecture and recorded architectural decisions.
- Specified threat controls, failure injection, deterministic and AI evaluation, limitations, and open questions.
- Added a phased implementation plan that prioritizes a thin P0 detection slice.
- Added continuity, learning, roadmap, and handoff documents so work can continue without chat history.
- Verified proposed Spring generation compatibility against official documentation; exact implementation dependency resolution remains a P0 smoke-test obligation.

### Commands and factual outcomes

| Command/check | Outcome |
|---|---|
| workspace listing and recursive file/guidance search | success; no project files or `AGENTS.md` found |
| `git status --short --branch` in original workspace | failed as expected: not a Git repository |
| `git init --initial-branch=main` in `outputs/aegisflow` | success; empty local repository initialized |
| documentation consistency/link/scenario review | pending at this journal entry; final outcome must be appended below |
| automated application tests | not run; no application or build exists in Phase 0 |

### Decisions and corrections

- Use a modular control plane and independently deployed simulator services.
- Assume at-least-once delivery and database-enforced idempotency; avoid universal exactly-once language.
- Resolve remote timeouts by verification, not blind retry.
- Keep AI advisory, evidence-bounded, schema-validated, and outside policy/approval/execution authority.
- Treat all stated metrics as future targets until a dated, reproducible run records measurements.

### End-of-session verification

The initial review was interrupted before a commit. The continuation and final evidence are recorded below.

## 2026-10-12 — Phase 0 continuation and completion review

**Review snapshot:** 2026-10-11T20:01:42Z (2026-10-12 01:31:42 IST).

### Corrections from the independent audit

- Unified the valid-event transaction as inbox + fact + projection/deadline + processed status, before Kafka acknowledgement.
- Reconciled canonical event names, base deadlines/grace, invariant IDs, failure IDs, and latency targets.
- Defined the normal commerce trigger path: Payment/Warehouse consume OrderCreated; Order consumes their outcomes; Warehouse retains cancellation intent across reordering.
- Separated material incident revision from lifecycle CAS version so normal state transitions do not invalidate their own approval.
- Unified canonical plan/approval bindings and persisted policy hashes, evidence collection outcomes, and schema/API provenance.
- Added pre-dispatch invalidation, cancellation eligibility, no-action self-resolution, and definitive no-effect rejection paths.
- Constrained MVP plans to one action and AI advice to an action type; deterministic code derives all execution parameters.
- Defined technical quarantine for unparseable input without trusting a claimed tenant; documented the limits of shared Kafka topic ACLs and producer headers.
- Distinguished action-level idempotency keys from per-invocation attempt IDs and excluded invocation metadata from the stable request fingerprint.
- Added a reproducible documentation review, ten interview Q&As, and clearer phase/priority naming.
- Corrected Maven distribution/Wrapper terminology and mutable image-tag pinning; images must use digests when implemented.

### Exact commands and factual results

From the repository root, execute:

```text
$review = Get-Content -Raw docs/evaluation/PHASE0_REVIEW.md
$check = [regex]::Match($review, '(?ms)^```powershell\r?\n(.*?)^```').Groups[1].Value
& ([scriptblock]::Create($check))
```

Observed result on Windows / PowerShell 7.6.5 / Git 2.48.1.windows.1: **exit 0**.

```text
Markdown=25 Required=18 LocalLinks=55 Mermaid=6 JSON=1 Scenarios=16 InterviewQA=10 ADRs=6 Issues=0
```

All five mandatory fault fields occur in all 16 scenarios (80 field-presence assertions). Required envelope fields are present. Fences balance and the one event JSON example parses. This is documentation verification, not evidence that any fault scenario has run.

- `git diff --check`: exit 0 before staging; repeat staged check before commit.
- First `git diff --cached --check` after staging: non-zero, reporting ten extra blank lines at EOF in the architecture/ADR drafts. Removed those blank lines and reran the staged check; final result is recorded below. Git also reported LF-to-CRLF working-copy conversion notices under the existing Windows Git configuration; these are not application failures.
- `rg --pcre2` terminology search: no stale OrderPlaced event name, two-digit FM ID, old incident-state-version binding, AI parameter authority, or immutable version-tag claim. A general logical-delivery phrase in the outage assertion is valid and was retained.
- Initial `rg` search using lookahead without `--pcre2`: failed with a regex parse error; rerun with `--pcre2` succeeded. This diagnostic failure did not change project files.
- The initial project-setup command used a working directory that did not yet exist and failed; the corrected command created the isolated directory first and initialized Git successfully.
- `mmdc` availability check: not installed. All six Mermaid sources were structurally reviewed; parser/rendering/visual layout are **not verified**.
- Application unit/integration/e2e tests, vulnerability scans, benchmarks, and AI evaluations: **not run**; there is no application. No application pass/fail/skip counts or observed product metrics exist.

### Commit and continuation

The documentation review is complete and ready for a coherent local baseline commit. Record the actual baseline ID and checkpoint outcome after the commit; do not push. The next authorized decision is the owner's Phase 1 approval for P0.1 (Java/Maven/Spring foundation). P1 recovery remains a later product stage.

### Local baseline commit completed

At 2026-10-11T20:03:43Z (2026-10-12 01:33:43 IST):

- Final staged validator and whitespace checks: exit 0 with the same 25/18/55/6/1/16/10/6 counts and zero issues.
- `git commit -m "docs: establish AegisFlow phase 0 architecture baseline"`: exit 0, baseline `2bae1f3e13686194011c005c202a872f01a99815`, 25 Markdown files and 2,920 insertions.
- `git status --porcelain=v1`: empty (clean after baseline).
- `git remote -v`: empty (no remote configured).
- Application source, deployment, push, and publication: none.

The following local continuity checkpoint updates `HANDOFF.md`, this journal, `ROADMAP.md`, and the plan checklist with the committed baseline and approval gate. It deliberately references the baseline rather than attempting to embed its own future hash. Run `git rev-parse HEAD` and `git status --short --branch` to resolve the current checkpoint and status. Phase 0 is complete; the highest-priority next task is explicit Phase 1 approval for P0.1.

## 2026-10-12 — P0.1 foundation implementation, pause, and completion

**Entry snapshot:** 2026-10-12T04:43:38Z (10:13:38 IST).
**Authorization:** the owner explicitly approved Phase 1 milestone P0.1, paused work, then requested continuation. P0.2 remains unapproved. Phase 0 history at `2bae1f3` and `6f6daaf` was preserved; no remote push or external deployment occurred.

### Implementation and choices

- Added a Maven aggregator/parent and four separately executable Spring Boot applications. Simulators have no dependency on Core or one another; Maven Enforcer bans deployable-to-deployable artifacts, with package tests providing a second check.
- Declared ten Core packages as Modulith modules. Package-private marker classes make them discoverable without inventing domain behavior; explicit empty `allowedDependencies` forbids cross-module imports until a later intentional design update.
- Added default `foundation` startup that deliberately excludes JDBC/Flyway, plus a separate real-PostgreSQL profile per application. Only loopback-bound Actuator health is exposed. `UP` is not a business/database/security result.
- Added four separate PostgreSQL Compose services, networks, and volumes, with PostgreSQL 18.6 pinned by digest. Required local password variables have no committed values. Local bootstrap database owners must be replaced by restricted runtime/migration roles before P0.2 business persistence.
- Added HTTP context tests, architecture tests, optional PostgreSQL connectivity/Flyway infrastructure test, and `scripts/verify-startup.ps1`, which checks the four packaged JARs on ephemeral ports and stops only its own child processes.
- Added ADR 0007 for startup/infrastructure staging without changing the modular-control-plane, service-ownership, outbox, or API-only recovery decisions. No business migrations, Kafka processing, security workflow, AI, recovery, or frontend was introduced.

### Toolchain and reproducibility

The host's default Java 26 and Maven 3.9.15 do not satisfy this repository's baseline. Used isolated Temurin Java 21.0.12.1+1-LTS from the workspace toolchain directory without changing global settings. Maven Wrapper 3.3.4 downloads Maven 3.10.0 from the checksum-pinned distribution. Verified actual Boot 4.1.1, Spring 7.0.9, Modulith 2.1.1, Flyway 12.4.0, PostgreSQL JDBC 42.7.13, JUnit 6.0.3, Testcontainers 2.0.5, and Mockito 5.23.0 resolution.

Successful commands below ran from repository root with `JAVA_HOME` pointing to Java 21. In this restricted host, `TEMP`/`TMP` pointed to repository `.cache/tmp` to avoid default-temp wrapper rename restrictions. Reusable setup and portable invocation details are in the codebase guide and test evidence.

| Exact command | Factual outcome / evidence |
|---|---|
| `.\mvnw.cmd -B -ntp -l .cache/p0.1-verify-final.log verify` | exit 0 at 2026-10-11T20:29:19Z (01:59:19 IST); 14 tests, zero failures/errors/skips; four runnable JARs |
| `.\mvnw.cmd -B -ntp -l .cache/p0.1-clean-verify.log clean verify` | exit 0 at 2026-10-12T04:33:06Z (10:03:06 IST); all five reactor projects successful; 14 tests, zero failures/errors/skips |
| `.\mvnw.cmd -B -ntp -l .cache/p0.1-infrastructure-tests.log -pl control-plane -Pinfrastructure-tests verify` | exit 0 at 2026-10-12T04:33:42Z (10:03:42 IST); five Core default tests passed; one PostgreSQL integration test skipped, zero failures/errors; Docker was unavailable |
| `.\mvnw.cmd -B -ntp -l .cache/p0.1-dependencies.log dependency:tree` | exit 0 at 2026-10-12T04:36:20Z (10:06:20 IST); resolved all four applications' dependency trees |
| `.\mvnw.cmd -B -ntp -N help:effective-pom -Doutput=.cache/effective-pom.xml` | exit 0 at 2026-10-12T04:36:32Z (10:06:32 IST); effective parent/plugin management captured |
| `docker compose --profile postgres config --quiet` | exit 0 with temporary local password variables; no credentials recorded; configuration validation only |
| `& .\scripts\verify-startup.ps1` | final run exit 0; Core, Order, Payment, Warehouse each returned HTTP 200/`UP` in `foundation`; `.cache/startup/results.json` records ports/process IDs; owned children stopped in `finally` |
| `$env:SPRING_PROFILES_ACTIVE='postgres'; .\mvnw.cmd -B -ntp -l .cache/p0.1-profile-isolation-verify.log verify` | exit 0 at 2026-10-12T04:39:57Z (10:09:57 IST); 14 tests, zero failures/errors/skips; all four context tests explicitly activated `foundation` despite the caller profile |

Raw logs/reports are ignored local artifacts. The tracked evidence document preserves the important results for a future checkout; it does not imply those ignored logs are committed.

### Failures, interruption, and corrections

- Initial test execution compiled Core and started its context, but Mockito runtime self-attachment hung on the restricted host's Java attach pipe. It was interrupted with exit 1 and is not a passing run. Surefire now loads the Boot-managed Mockito agent explicitly at JVM startup and bounds fork duration; subsequent runs passed with Enforcer and all tests still enabled.
- The next clean-build attempt failed because a diagnostic process held a JAR inside a temporary local-repository directory under root `target`. The diagnostic process was stopped; the later clean build passed. No destructive cleanup or test suppression was used.
- Other diagnostic attempts encountered restricted DNS, temp-directory rename, Maven-cache lock/rename, unsupported Java 26 enforcement, and a misquoted Maven property. A Java 26/Enforcer-skip diagnostic was interrupted rather than accepted; it does not satisfy any milestone gate. The final Java 21 builds ran with Enforcer enabled.
- The owner pause interrupted ongoing work. A checkpoint attempt at that pause was blocked by an approval-review usage limit; therefore no P0.1 commit existed at the pause. Continuation completed verification and the coherent checkpoint is recorded in the handoff.
- The startup helper initially received JSON as a byte array from PowerShell and failed parsing after Core had already returned HTTP 200. Added explicit UTF-8 decoding, reran, and all four applications passed; earlier helper failures were not counted as four-application startup success.
- Docker CLI/Compose were present, but the engine's named pipe was unavailable. A bounded Docker Desktop start attempt did not produce an available engine and was interrupted. PostgreSQL execution is still unverified; the optional integration test's skip is retained, not relabeled as a pass.
- Independent review found that simulator tests relied on the default profile and that the POSIX wrapper could switch archive type despite a ZIP checksum. Tests now explicitly select `foundation`; the wrapper now requires `unzip` instead of silently switching to a differently checksummed archive. Profile-isolation regression passed; POSIX execution itself was not run.

### Exit and continuation

P0.1's infrastructure-free foundation is verified: 14 passing tests, ten verified Core module boundaries, and four independently started packaged JARs. Database/container execution remains an explicit environment limitation, not demonstrated readiness. Updated learning, architecture, evidence, roadmap, limitations, and handoff distinguish implemented shells from planned product behavior. The checkpoint remains local only; resolve its identifier with `git rev-parse HEAD` and the handoff's commit convention.

Stop after P0.1. The next task is **P0.2 approval plus working Docker/PostgreSQL evidence**, followed by restricted database-role design and the smallest Flyway-backed transactional state/outbox slice. Do not start Kafka before P0.3 or recovery/AI before their separately approved stages.

### Final review and checkpoint source verification

- Fresh `clean verify` with caller `SPRING_PROFILES_ACTIVE=postgres` finished at 2026-10-12T04:44:33Z (10:14:33 IST), exit 0, all 14 tests passing. Four packaged JAR checks then passed at 04:44:58Z (10:14:58 IST), exit 0.
- Final independent read-only review found no blocking foundation defects. Preventively changed simulator HTTP tests from `localhost` to `127.0.0.1`, matching the actual bind address and avoiding IPv6 name-resolution ambiguity.
- `.\mvnw.cmd -B -ntp -l .cache/p0.1-checkpoint-clean-verify.log clean verify` reran after that change with the same caller profile: exit 0 at 2026-10-12T04:51:16Z (10:21:16 IST), 14 tests, zero failures/errors/skips, all five reactor projects successful.
- `.\scripts\verify-startup.ps1` reran after the checkpoint build: exit 0; four separate executable JARs returned HTTP 200/`UP`, no internal health details, and the helper stopped its own children.
- Added a current `scripts/verify-docs.ps1` gate for required documents, local links, fences, JSON, failure-matrix field coverage and interview Q&A presence. The Phase 0 Markdown-only checker remains historical, not the current source-tree gate.
- Documentation check at 2026-10-12T04:55:09Z: exit 0, Markdown=28 Required=20 LocalLinks=97 Mermaid=7 JSON=1 Scenarios=16 InterviewQA=15 ADRs=7 Issues=0. This proves documentation consistency, not Mermaid rendering or fault execution. `git diff --check` passed.
- Wrapper line endings are explicit in `.gitattributes` (POSIX LF, Windows CRLF); the POSIX script is staged executable. This preserves portable checkout behavior without claiming POSIX runtime verification.

### Local milestone checkpoint completed

At 2026-10-12T04:57:16Z (10:27:16 IST), `git commit -m "feat(foundation): establish verified P0.1 application skeletons"` succeeded (exit 0) as `cb7bfafed49a58f08cba2352aafb8d7ecf8f6e33`, 73 files, 2,545 insertions and 163 deletions. Final XML totals were independently summed as 14 tests with zero failures/errors/skips; staged whitespace verification passed and the wrapper's executable mode is `100755`. Git status then showed clean `main`. No generated/private paths were staged and no remote exists.

The following documentation-only continuity checkpoint records this actual source hash in the handoff, evidence and roadmap. It changes no application source. After it commits, rerun the current documentation check and inspect clean Git status; source tests remain those of `cb7bfaf`. Stop before P0.2 and request new milestone approval. Owner preparation is recorded in the handoff (Docker engine, Java 21 selection, optional local rehearsal; no AI keys needed).

## 2026-10-13 — approved P0.2 persistence checkpoint, interrupted and resumed

The owner approved P0.2 only, requested exact continuation without repeating work, allowed a meaningful checkpoint, stopped work for the day, then resumed it. Original repository/history at `c2c93e3` and `cb7bfaf` were preserved. Entry inspection confirmed surviving modified/untracked source; the interrupted handoff had not saved because a patch's evidence heading mismatched. A current handoff was written before continuing. No P0.3 work, push, deployment, history rewrite or volume deletion occurred.

### Source changes and decisions

- Kept four separately owned PostgreSQL instances/databases/networks/volumes. Added explicit bootstrap/migration/runtime role variables, repeatable non-destructive role SQL/script, and separate runtime/Flyway connections. Core V1 establishes only its own schema/history; no speculative Core business tables.
- Implemented initial internal Order creation, synthetic Payment outcome and Warehouse reservation outcome services. Each local Spring transaction joins owned JPA state/lines, JDBC durable request replay claim, and complete immutable outbox intent. No HTTP business ingress, consumer/relay, financial provider/recovery, AI, Keycloak or frontend was added.
- Added composite tenant keys/FKs, request-key constraints, pending indexes, optimistic counters, stable UUID results, exact currency amounts, sorted/versioned SHA-256 producer/request JSON, outbox identity/payload protection and restricted table/column grants. ADR 0008 records the minimal scope and tradeoffs; planned inbox/decisions/cancellation/recovery tables remain deferred.
- Wrote PostgreSQL tests for migrations, commit/rollback after actual append, replay/conflict/concurrent keys, tenant/event/version constraints, locking, privileges and postgres health. Warehouse Testcontainers now overrides its explicit Flyway URL. Payment's profile gating, tenant lengths and fail-closed nullable/JSON checks were corrected during resumed review.
- Updated current architecture/contracts/decisions, README/roadmap/limitations, actual learning paths and flow, rollback debugging exercise and Q16–Q20 with answers. Prior history/evidence and Q1–Q15 remain preserved.

### Actual checks and limits

Java 21/Temurin 21.0.12.1 and Maven 3.10.0 were confirmed earlier. Global `.m2` writes and subsequent Docker pipe access were denied by the sandbox; automatic escalation was rejected by its granular policy. Existing Maven artifacts were copied into ignored workspace cache. Host Docker Desktop working is distinct from sandbox engine access.

| Exact command/check | Result |
|---|---|
| `.\mvnw.cmd -B -ntp -o '-Dmaven.repo.local=.cache/maven-repository' -l .cache/p0.2-clean-verify-offline.log clean verify` | exit 1, 00:39:17 IST, 24.594 s; Core 4 architecture passes/1 health error/0 assertion failures/0 skips; Tomcat loopback permission denied; simulators not reached |
| `.\mvnw.cmd -B -ntp -o '-Dmaven.repo.local=.cache/maven-repository' -l .cache/p0.2-test-compile.log test-compile` | exit 1, 09:17:53 IST, 6.559 s; Core compiles; Order lacks cached JPA starter 4.1.1; remaining modules not reached |
| `.\mvnw.cmd -B -ntp -o '-Dmaven.repo.local=.cache/maven-repository' -pl control-plane -Pinfrastructure-tests -Dtest=ControlPlaneArchitectureTests -l .cache/p0.2-core-available-checks.log verify` | exit 0, 09:19:44 IST, 31.152 s; **4 architecture passes**, **4 PostgreSQL skips**, 0 failures/errors; engine pipe denied |
| `.\mvnw.cmd -B -ntp '-Dmaven.repo.local=.cache/maven-repository' -pl simulators/order-service -am -l .cache/p0.2-online-test-compile.log test-compile` | exit 1, 09:21:36 IST, 3.917 s; uncached JPA descriptor download blocked by Maven Central DNS |
| `mvnw.cmd -B -ntp -o "-Dmaven.repo.local=$repoCache" -f work/persistence-unit-check/pom.xml -l .cache/p0.2-isolated-pure-unit-rerun.log clean test` | exit 0, 09:21:09 IST, 11.055 s; **21 pure source unit tests passed**, 0 failures/errors/skips |
| `docker compose --profile postgres config --quiet` | exit 0 with twelve temporary secret process values, restored after check; no expanded configuration output |
| PowerShell Parser.ParseFile of three operational scripts | 0 syntax errors; static only |
| `git diff --check` | exit 0, line-ending notices only |

The isolated ignored diagnostic POM compiled the actual pure source/test files with unchanged Boot 4.1.1/Java 21 cached libraries. First harness compile failed on an omitted existing source (`IdempotencyRecordStore`); the source-selection list was repaired and its rerun passed. This is validation/fingerprint/fixture evidence only, not JPA, HTTP, transaction, database or reactor acceptance. The normal committed tests remain enabled; the full local gate still needs to run. The original failed runs are retained in evidence.

The user also supplied a later P0.1 local reactor success at 2026-10-12 12:29:48 IST, 53.197 seconds, all five modules successful, Temurin 21.0.12.1. That report's excerpt did not supply counts and is distinguished from these session checks.

### Meaningful checkpoint and exact next step

Checkpoint the current coherent source/tests/docs locally, with full P0.2 completion explicitly pending. The next task is a normal host PowerShell Java 21 run of `scripts/verify-persistence.ps1`, which runs the clean full reactor and real PostgreSQL ITs and rejects skips; then `verify-startup.ps1` and `verify-docs.ps1`. It uses temporary test containers and needs no shared credentials or persistent Compose bootstrap. Preserve the reports and fix actual failures within P0.2. Do not repeat the design review or recreate the source. Request approval before P0.3.

Real PostgreSQL constraints/atomicity/concurrency/privileges, all simulator/JPA compilation, both startup profiles and packaged regression remain unverified here. Documentation check/commit identifiers and final working-tree state are recorded in the closing handoff checkpoint.

Pre-checkpoint documentation checker passed with Markdown=29 Required=20 LocalLinks=108 Mermaid=7 JSON=1 Scenarios=16 InterviewQA=20 ADRs=8 Issues=0. Working/staged whitespace checks passed. The first shell checkpoint wrapper stopped before staging because it wrongly checked unset native LASTEXITCODE after a PowerShell script; its corrected guard passed. Staging contains 101 scoped source/configuration/document files and excludes cache/work/target/private .env. No full P0.2 completion is claimed by the local checkpoint.

### Local checkpoint created

`git commit -m 'feat(persistence): checkpoint P0.2 state and transactional outbox'` exited 0 as `14e04f6d0cb96520ae5ba1451c10890860df3379` (101 files, 4,778 insertions/135 deletions). Status immediately afterward was clean `main`. The following docs-only continuity commit records this source identifier; no application changes follow the checkpoint. All commits stay local, with no remote/push/deployment. P0.2 gate remains pending; normal host Java 21/Docker verification is the next task, with P0.3 unapproved.

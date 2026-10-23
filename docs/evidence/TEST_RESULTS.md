# Test evidence — P0.4 and preserved completed milestones

2026-10-23 database verifier repair: host run on740201b failed Core before any completed database pass/restart; retained .cache/p05b-db/6258f061279843eda909221ee008acec/results.json has Passed=false and empty Passes. Repeated infrastructure/role/topic bootstrap succeeded. Probe used loopback TCP, which can match initdb trusted-local HBA rules and is unsuitable for proving deployed password authentication. Changed PGHOST to the explicit owner service DNS address and added allowlisted FAIL_STAGE evidence without printing raw potentially private errors. No pg_hba, role, credential or storage changes. Root cause of the prior generic failure remains unconfirmed; no database pass claimed. PowerShell parse and whitespace checks pass. Next rerun verify-development-databases.ps1; B/C remain incomplete. Following local checkpoint starts from740201b; no push.

## 2026-10-23 — persistent database verification prepared

Owner startup on fa359ba passed: five persistent infrastructure services healthy, four database role bootstraps and Kafka topic/ACL provisioning completed, credentials unchanged. This is observed startup, not full B acceptance. Added scripts/verify-development-databases.ps1: real TCP bootstrap/migration/runtime login, non-superuser/non-create-role checks, schema USAGE and migrator-only CREATE, explicit wrong-password authentication rejection; repeat real bootstrap around stop/start of only the four databases and compare PostgreSQL system identifiers and private-file hash. Kafka remains running; no volume removal or application containers. Results persist under ignored .cache/p05b-db even on failure. The probe reads passwords inside each database container; no password arguments/output.

Local verification: new PowerShell parser passed; existing22 credential/file/ACL/environment/actual Compose checks passed again (not additive). Docker execution remains unavailable here, so database assertions/restart have NOT executed. Next owner: bundled PS7 -NoProfile -File .\scripts\verify-development-databases.ps1. This temporarily stops/restarts the four dedicated databases, preserves storage and repeats provisioning. If it fails, preserve evidence and resources. Remaining B after database success: Kafka authentication/ACL and retained-broker evidence, cross-service credential/network checks. Do not reuse the disposable Kafka smoke blindly on permanent topics: its synthetic non-business records need explicit scope/isolation consideration. C/D not begun. This local checkpoint follows clean fa359ba; resolve commit with git rev-parse HEAD. No push or private-file modifications.

## 2026-10-23 — fix empty environment overrides

Host startup on ce590c3 created the private file but failed Compose interpolation before containers started. Reproduced safely: SetEnvironmentVariable(null) left18 present empty password keys, overriding --env-file. Changed scoped cleanup to Remove-Item Env: and restore presence/value separately. Added presence regression and actual Docker Compose config --quiet against disposable credentials.22 checks passed; independently validated existing .env.development and actual Compose interpolation with unchanged file hash. No secret values printed, credentials rotated, containers started or volumes touched. No Maven/A repeat. Next: rerun scripts/dev-infrastructure-up.ps1 with bundled PS7; existing credentials are reused. B remains incomplete until startup/auth/permission/restart gates execute; C/D pending. This following local repair checkpoint starts from clean ce590c3; resolve hash via git rev-parse HEAD. No push.

## 2026-10-23 — persistent infrastructure startup prepared, live gate pending

Owner dev-init.ps1 -CheckOnly passed: no existing private files or matching volumes block new initialization. No private file was created by that check. Starting from clean799a77b, added protected current-user-only Windows DACL at CreateNew time, before writing credential bytes; privileged administrators/Docker users remain trusted. Added dev-infrastructure-up.ps1: explicit project/files/environment, bounded180s five-service health wait, existing database role bootstrap, bounded180s Kafka one-shot provisioning wait and unchanged-private-file hash assertion. It leaves persistent infrastructure and evidence intact on failure, never deletes volumes or overwrites credentials. No four-app C overlay yet.

Executed verify-development-credentials.ps1:20 checks passed (generation/validation, guards, environment restoration, real disposable file roundtrip, overwrite rejection/hash preservation, protected ACL, four script parses). Disposable random files are ignored under .cache/credential-policy with protected ACL; no permanent .env* created. Docker live execution remains unavailable here; startup/provisioning/authentication/restart is NOT claimed passed. Earlier14 checks are superseded by this20-check run, not additive.

Next owner command: bundled PS7 -NoProfile -File .\scripts\dev-infrastructure-up.ps1 from repo root. This intentionally creates .env.development and starts five permanent infrastructure services for project aegisflow-foundation. Keep the private file stable and private; back it up securely with persistent storage. No manual password copying. On failure preserve everything and share only the summary, not config/environment contents. After actual startup success, implement/run authentication, permission and retained-volume restart acceptance before declaring B complete or proceeding to C. D prohibited until B/C verified. No push. This following local checkpoint resolves via git rev-parse HEAD.

## 2026-10-23 — P0.5-B credential guard partial checkpoint

Resumed clean main729463c; A closure and its source/evidence preserved. Added scripts/lib/development-credentials.ps1, scripts/dev-init.ps1 and scripts/verify-development-credentials.ps1. Generate18 distinct CSPRNG256-bit passwords plus stable random Kafka cluster ID into explicitly selected ignored .env.development using CreateNew, never overwrite. Missing private file with named/project-labelled retained volumes or legacy .env/.env.kafka fails closed. Existing private file validates without regeneration. Fixed Compose files/project and scoped shell-variable removal/restoration prevent interpolation overrides; config --quiet avoids printing secrets. No container startup is implemented in this checkpoint. Existing PostgreSQL/Kafka files, role identities, networks, topics and grants are unchanged.

Actual verification: verify-development-credentials.ps1 exit0,14 generation/duplicate/weak-password/missing-credential/environment-restoration/parser checks. dev-init.ps1 -CheckOnly exit1 at Docker inventory: named-pipe access denied; no credentials/resources changed. No A/Maven suites repeated. File-writing/reuse and actual Compose precedence still need live/integration verification;14 pure checks do not prove them. Existing-file migration/import is deliberately not automatic: if legacy configuration exists, stop and design safe reuse rather than replace or require password copying.

Next single owner action (read-only): bundled PS7 -NoProfile -File .\scripts\dev-init.ps1 -CheckOnly. It checks engine/inventory, ignores unrelated projects, and either reports safe new initialization or a retained-storage/legacy-config blocker without printing values. Do NOT run legacy start-kafka.ps1 to bypass a blocker. Next implementation: complete B bootstrap/provision/authentication/restart gate using existing helpers, test file reuse and failure cases; only then C nine-service app overlay/lifecycle. B remains INCOMPLETE; C/D not started. No private files generated, volumes deleted, push or deployment. This following local partial commit starts from729463c; resolve hash with git rev-parse HEAD. Windows private files currently inherit repository user ACLs: access by privileged/local users is not prevented; review restrictive ACL creation before recommending permanent credential generation.

## 2026-10-23 — P0.5-A verified closure

Owner ran verify-application-images.ps1 -UseExistingArtifacts on581da54. All4 Core/Order/Payment/Warehouse gates passed: Java21, UID10001, read-only startup, image/archive identity, host loopback HTTP and separate bridge DNS/HTTP. Independently inspected all4 owner JSON records and State snapshots in .cache/application-image-verification/aegisflow-images-08c67f1250654717ac98f2b33f08f0c4: allUP/BridgeProbe Passed; all exited143, OOMKilled false after bounded shutdown. Earlier172 reactor tests and13 probe regressions remain separate passed evidence, not rerun or summed. Previous health-parser failures are retained.

P0.5-A is COMPLETE, not full P0.5. Next authorized task is B guarded private credentials/bootstrap: preserve existing files/volumes, refuse missing credentials over retained storage, validate explicit environment precedence and independently verify owner roles/Kafka topics and ACLs. Then C nine-service lifecycle and D persistent business/restart acceptance. No permanent credentials generated, personal files changed, persistent storage targeted or push performed. No further A owner command is needed. This documentation checkpoint follows clean581da54; resolve closing hash using git rev-parse HEAD. P0.4 remains complete; no P1/recovery/AI.

## 2026-10-23 — confirmed health field-order repair

Owner retry on2912bf2 rebuilt all4 images without repeating Maven. Evidence .cache/application-image-verification/aegisflow-images-ac40eb5d74c94d8dbdccfe9b9cc351cf records Core running, not OOM-killed, and repeated rejected HTTP200 bodies with groups before status UP. The probe incorrectly required status first. docker/HealthProbe.java now accepts either field order for the details-disabled status/optional groups contract, rejecting duplicate/nested status, malformed/trailing data and DOWN. Thirteen probe checks pass, including the exact observed response; archive/port/policy/PowerShell checks also pass via scripts/verify-image-policy.ps1 (exit0). No application JAR, ports, timeout, database or business behavior changed. The earlier172 reactor passes remain valid evidence, not rerun.

Next owner command: scripts/verify-application-images.ps1 -UseExistingArtifacts with Java21 and bundled PS7. This rebuilds the probe layer and reruns the unverified four-container gate. A is NOT complete until real success; B/C/D remain pending. Prior failed evidence retained. This following local repair commit starts from clean2912bf2; resolve its hash with git rev-parse HEAD. No push/private files/volumes changed.

## 2026-10-23 — image host result and diagnostic continuation

Owner output on fd9c75d:172 reactor tests passed,0 failures/errors/skips; all4 images built (.cache/application-images/53a9f1e95d564ca2b69323093ea15980). Core runtime health timeout stopped A before remaining3 applications. Retained .cache/application-image-verification/aegisflow-images-e76d1f8b61ec42e4a1d8be75c44a5c4a log independently shows successful Core startup in16.588s, not the health response. Cause unproven. Added probe diagnostics and State-only capture before cleanup. Java21 scripts/verify-image-policy.ps1 exit0:6 probe cases,4 archive identities and existing port/policy/PowerShell checks pass. No reactor repeated. Local JAR diagnostic could not bind due sandbox SocketException permission denial; initial unquoted JVM argument failed parsing, corrected launch reached restricted socket operation. Live diagnostic rerun still needed: verify-application-images.ps1 -UseExistingArtifacts. No Docker pass inferred.

## 2026-10-22 — P0.5-A static packaging checkpoint, Docker unavailable

Entry1d0b1f5 clean main/origin. Java21 existing artifacts inspected, no completed P0.4 suite repeated. Command (JAVA_HOME Temurin21.0.12.101-hotspot, bundled PowerShell7): scripts/verify-image-policy.ps1. Exit0:6 Java health-response checks;4 actual executable owner manifests/classes and forbidden-private-entry checks; distinct4 mappings; loopback valid case and5 rejected forms; digest/user/COPY/context policies;4 PowerShell parser checks. This is static/Java verification, NOT image build/start.

Command scripts/verify-application-images.ps1 -UseExistingArtifacts exited1 during dev-build-images.ps1 Docker preflight. docker info returned permission denied connecting npipe:////./pipe/dockerDesktopLinuxEngine. No images built, containers started, credentials created or volumes touched. All live A acceptance remains unverified; no skipped result counted passed. Default host command (without switch) will run clean verify once, preserve old/new reports, then build/start all4 images and record exact artifact hashes and container results.

Source paths: docker/Dockerfile, docker/HealthProbe.java, docker/HealthProbeTests.java, .dockerignore; scripts/dev-build-images.ps1, verify-application-images.ps1, verify-image-policy.ps1 and lib/application-images.ps1. Existing packaged-artifact guard reused. Health/status has only foundation scope; B/C/D persistent credentials, lifecycle, business flows and restart are not implemented or verified. Existing historical P0.4 evidence remains below unchanged.

Closing rerun after adding image-JAR hash verification and prior child-log preservation: static/image-policy command exit0 with the same checks. verify-docs.ps1 exit0:35 Markdown,20 required,106 local links,6 Mermaid source blocks,1 JSON,16 scenarios,80 answered interview questions,13 ADRs,0 issues. git diff --check clean. Mermaid rendering not performed. README obsolete checkpoint prose removed from current instructions; historical records remain in Git/journal/evidence/handoff.

## 2026-10-21 — P0.4 final packaged PASS and closure

Owner command from repository root, JAVA_HOME Java21:

```powershell
& 'C:\Users\User\.cache\codex-runtimes\codex-primary-runtime\dependencies\native\powershell\pwsh.exe' -NoProfile -File .\scripts\verify-p04-final.ps1 -Gate Packaged
```

Source ab1089c, Docker29.8.2. Saved selected XML independently checked: PackagedDetectionScenario1 test,0 failures/errors/skips,189.185s suite. Maven exit0,3:21 at2026-10-21T22:55:29+05:30. Evidence .cache/p04d/efc50ad38f9a42bca0d1f4dd343d5da4/PackagedDetectionScenario.log and selected reports. Actual executable logs and incident-evidence.txt: .cache/packaged-detection/e8419af8-9ec7-447f-a726-cbfd4ed150ef.

The successful test proves actual scheduled owner relays/Core intake/projector/detector/recorder, not manually invoked workers. Healthy transaction has3 facts, ENFORCED/SATISFIED and no incident. Missing Warehouse observation has2 facts and exactly1 DETECTED incident. Actual Core process stop/restart consumes the held Warehouse record, leaves6 accepted inbox events/zero quarantine, adds late evidence while retaining incident UUID and DETECTED state. Component fault tests separately cover actual broker replay, reorder/backlog, quantity mismatch, contradiction injection, tenant quarantine/offset progression, context restart and concurrent incident recording.

Inspected synthetic timeline: incident bb75cded-e6e1-46f8-8144-c1a62bf84c91, tenant pipeline-a; INV-002v1, ECOM_ORDER_V1v1, demo-v1,45+15 seconds. Order occurred17:23:35.099408Z; due17:24:20.099408Z; enforce17:24:35.099408Z. At17:24:36.634627Z: VIOLATED/MISSING_OBSERVED_OUTCOME, MEDIUM, Order/Payment evidence, inventory null. Warehouse occurred17:23:35.268482Z but was observed after Core restart at17:25:22.215016Z. Evaluation17:25:22.245794Z is UNKNOWN/PROJECTION_UNSETTLED;17:25:25.303550Z is SATISFIED/VALID_OBSERVED_OUTCOME with matching sku-1 quantity2. Initial DETECTED and historical evaluations persist. Output rows are not chronologically sorted; order by evaluated_at when explaining them. No claim of a failed remote operation or authoritative recovery.

Acceptance basis across separate runs: A43 focused+16 database; B18 focused+21 database; C focused10/launcher3 and21 database across20+1 corrected runs; D pipeline7 across6+1; clean reactor168 on57ad5b1; fixture repair2 and clock wiring6 separately; packaged1 onab1089c. Counts overlap across regressions and must not be summed into one invented suite. No full-reactor rerun after clock wiring; scoped regressions and rebuilt executable acceptance cover that delta. Earlier failures/skips remain recorded. All P0.4 gates are now accepted; no command or P0.5 work follows automatically.

## 2026-10-21 — pipeline complete across6+1; packaged Clock ambiguity repaired

Owner Remaining run on26624f5: DetectionPipelineScenario selected contradiction1/0/0/0, Maven exit0/2:02,21:23:44IST. PackagedDetectionScenario1 test,0 failures,1 error,0 skips;106.7s suite, Maven exit1/2:01,21:25:51IST. Selected saved XML/logs inspected at .cache/p04d/1d2a692dc0304d0d9c047137c10aa17d (do not count copied/stale XML twice). All7 component pipeline cases passed across the prior6 plus this1; not a new single7 run.

Child failure log .cache/packaged-detection/897966b9-d519-45de-ace9-4a6553b030a5/core.log: MomentsAutoConfiguration.moments fails with NoUniqueBeanDefinitionException, three Clock candidates. New primary UTC applicationClock resolves infrastructure injection; worker clocks remain named and explicitly qualified. No invariant time values changed.

Command with JAVA_HOME21/workspace TEMP: .\mvnw.cmd -B -ntp -o '-Dmaven.repo.local=.cache/maven-repository' -pl control-plane '-Dtest=CoreClockConfigurationTests,ControlPlaneArchitectureTests' -l .cache/p04d-clock.log package. Actual6 tests passed (configuration2+architecture4),0 failures/errors/skips, exit0,30.907s at22:50:19IST. Core executable and original archive rebuilt. No real infrastructure or full reactor rerun; host packaged-only1 gate pending. Historical168 clean reactor remains valid for previous source; this wiring delta has scoped regression evidence.

## 2026-10-21 — host reactor168 passed; D6 passed, contradiction fixture repaired

Owner ran verify-p04-final.ps1 on57ad5b1, Java21/Docker29.8.2. Independently summed saved reactor/surefire XML:168 tests,0 failures/errors/skips; build exit0,1:12 at11:35:30IST. DetectionPipelineScenario XML:7 tests,0 failures,1 error,0 skips (6 passed);88.89s suite, Maven exit1/1:41 at11:37:18IST. Evidence .cache/p04d/86777e661f2d4104a5804de6d907c68c retains both logs/reports. Packaged test was NOT reached.

Only failed case: invalidBusinessOutcomesCannotMasqueradeAsHealthy(String)[2]. It attempted a second Warehouse reservation for the same tenant/transaction and correctly hit uq_reservations_business_transaction before detector assertions. Repair preserves that constraint and injects a faulty source claim over authenticated Kafka, with a fresh event ID but conflicting same-reservation/version outcome. This is explicitly a fault fixture, not a valid second business operation or claimed authoritative remote failure.

Focused command (JAVA_HOME21, workspace TEMP/TMP): .\mvnw.cmd -B -ntp -o '-Dmaven.repo.local=.cache/maven-repository' -pl control-plane '-Dtest=WarehouseContradictionFixtureTests' -l .cache/p04d-contradiction-unit.log test. Result2 passed,0 failures/errors/skips,0.390s suite,15.657s Maven exit0 at21:11:17IST. Tests assert identity/time preservation, fresh UUID, no mutation of original envelope/details, and invalid input rejection. New verifier parser PASS.

The6 host successes cover healthy/replay, missing/late evidence/concurrent recording, reordered/backlogged projection, wrong quantities, application-context restart, and tenant quarantine/offset isolation. Contradiction rerun and actual packaged scheduler/restart evidence remain unverified. Resume -Gate Remaining:1 named contradiction case followed by1 packaged case. Existing clean168 production packaging remains valid: no production source or configuration changed; two new test-only unit cases passed separately (not a claimed170-case reactor run).

Closing compile command: .\mvnw.cmd -B -ntp -o '-Dmaven.repo.local=.cache/maven-repository' -pl control-plane -l .cache/p04d-contradiction-compile.log test-compile. Latest test assertion compiled successfully, exit0,10.987s at21:12:53IST. Documentation checker passed33 Markdown/75 Q&As/12 ADRs/0 issues; whitespace check passed. No live corrected gate executed here.

## 2026-10-21 — C complete; D implemented, infrastructure/final reactor unverified

C restart-only owner command on a7a4761:

```powershell
& 'C:\Users\User\.cache\codex-runtimes\codex-primary-runtime\dependencies\native\powershell\pwsh.exe' -NoProfile -File .\scripts\verify-incidents.ps1 -RestartOnly
```

Inspected .cache/p04c/ef77263efc0549c8a79762e0979e645a/postgres.log and selected DurableIncidentIT XML:1 test,0 failures/errors/skips,14.484s suite; Maven25.942s, exit0 at11:10:45IST. Together with earlier20 successful cases this satisfies C's21 cases across separate runs, NOT a single new21-test run. The original failed attempt and UTC launcher fix remain documented below. No completed C suite repeated.

D local commands used Java21.0.12.1/Maven3.10.0, workspace TEMP/TMP=.cache/tmp and offline cache:

```powershell
.\mvnw.cmd -B -ntp -o '-Dmaven.repo.local=.cache/maven-repository' -pl control-plane '-Pinfrastructure-tests,pipeline-tests' '-Dit.test=DetectionPipelineScenario' -l .cache/p04d-first.log test-compile failsafe:integration-test failsafe:verify
.\mvnw.cmd -B -ntp -o '-Dmaven.repo.local=.cache/maven-repository' -pl control-plane '-Pinfrastructure-tests,pipeline-tests' '-Dit.test=DetectionPipelineScenario,PackagedDetectionScenario' -l .cache/p04d-compiled.log test-compile failsafe:integration-test failsafe:verify
.\mvnw.cmd -B -ntp -o '-Dmaven.repo.local=.cache/maven-repository' -pl control-plane '-Dtest=ControlPlaneArchitectureTests,PackagedProcessTests' -l .cache/p04d-boundaries.log test
.\mvnw.cmd -B -ntp -o '-Dmaven.repo.local=.cache/maven-repository' -l .cache/p04d-agent-final/reactor.log clean verify
```

- First D discovery:4 placeholders, ALL4 skipped,0 failures/errors; exit0,24.024s,11:22:36IST. Docker named-pipe AccessDeniedException. No scenario executed.
- Second D discovery before adding restart/tenant cases:5 placeholders, ALL5 skipped,0 failures/errors; exit0,27.942s,11:25:14IST. Same Docker denial. Parameterized cases are not expanded under class-level skip; do not confuse placeholders with expected live counts.
- Focused architecture/packaged-process regression:8 tests passed,0 failures/errors/skips; exit0,20.093s,11:27:11IST.
- Final clean reactor attempt compiled the latest test sources, ran77 Core tests:76 passed,0 failures,1 error,0 skips; exit1,31.220s,11:28:07IST. AegisFlowCoreApplicationTests.servesActuatorHealthOverHttp could not start Tomcat: IOException Unable to establish loopback connection caused by SocketException Permission denied: connect. Simulator modules were NOT reached. This is not a successful reactor or packaging gate. Original Core JAR was removed by the normal clean target; host must rebuild, not bypass artifact guards.
- Previous reports and child logs saved before clean under .cache/p04d-agent-final; new failing Core reports copied to failed-core-surefire. Both discovery logs and historical C evidence retained.
- New verifier PowerShell parser: PASS. Live invocation is not possible here; docker info itself returns engine-pipe permission denied. No ports, tests or security checks disabled to bypass it.

Expected host gates, not observed results: clean default reactor (verifier reports actual total), DetectionPipelineScenario7 live cases, PackagedDetectionScenario1 actual packaged case. Default script invokes each separately and checks fresh XML counts, zero failures/errors/skips. Selected partial modes do not validate omitted gates. No new end-to-end scenario or packaged incident timeline is claimed demonstrated yet. Existing A/B/C and P0.3 real evidence remains valid; new transport-to-detection composition needs its own live pass.

Full host command: scripts/verify-p04-final.ps1 under PS7 with JAVA_HOME21 and Docker Desktop. Preserve .cache/p04d/<id>, .cache/packaged-detection/<id> and incident-evidence.txt. Resume only a failed gate using -Gate Pipeline or -Gate Packaged after successful earlier gates and unchanged source; use -Gate Reactor for the default reactor alone. No permanent .env or existing Compose volume targeted.

Closing documentation command: PowerShell7 -NoProfile -File scripts/verify-docs.ps1 passed33 Markdown/20 required/124 local links/7 Mermaid source blocks/1 JSON/16 scenarios/75 answered Q&As/12 ADRs/0 issues. git diff --check passed. No Mermaid rendering was performed.

## 2026-10-21 — C host20 passes; one child-timezone failure, test-only repair

Repair closing static checks: docs validator exit0 (33 Markdown/70 Q&As/12 ADRs/0 issues); verifier parser and whitespace checks passed. No live rerun claimed.

Owner ran verify-incidents.ps1 on58eed3e, Java21/Docker29.8.2. Saved log/selected XML confirm Maven exit1/BUILD FAILURE at11:06:10IST,48.843s: DurableIncidentIT17 with1 failure (16 passes), PostgresInfrastructureIT4 passes, no errors/skips. Overall gate failed. Evidence .cache/p04c/9443ec38b21746b6a0bc5b613a41d279/postgres.log and reports.

Only separateJvmRestartPreservesIdentityAndAddsLateEvidence failed. Child log control-plane/target/incident-process/c3113041-c149-4beb-9ed0-500b54833cf4.log shows PostgreSQL FATAL invalid TimeZone Asia/Calcutta while opening the JDBC transaction. The argument-file launcher omitted the UTC option already present in the packaged harness. Added -Duser.timezone=UTC to test-only IncidentProcessLaunch and adjusted its regression assertion. Production code and V5 are unchanged;20 live passes remain valid.

Exact local repair command (Java21, workspace TEMP/TMP): `./mvnw.cmd -B -ntp -o -Dmaven.repo.local=.cache/maven-repository -pl control-plane -Dtest=IncidentProcessLaunchTests -l .cache/p04c-launch-utc.log test`. Exit0 at11:08:08IST,12.781s,3 passed,0 failures/errors/skips. This repeats the relevant launcher checks only; no actual child/DB success inferred.

verify-incidents.ps1 -RestartOnly parses successfully and selects only the failed method, requiring1 fresh executed pass. That live rerun is pending; preserve new evidence alongside the20 passes, do not sum stale copied reports or claim a new21-test run.

## 2026-10-21 — P0.4-C implementation; actual database gate pending

Closing static checks: incident verifier parses; verify-docs.ps1 exit0 (33 Markdown/20 required/124 links/7 Mermaid/1 JSON/16 scenarios/70 Q&As/12 ADRs/0 issues); git diff --check passes. No Mermaid rendering or host verifier execution claimed.

Baseline43c18ac plus C working tree. Java21.0.12.1, Maven3.10.0, offline repository cache; JAVA_HOME documented Temurin21, TEMP/TMP=.cache/tmp.

```powershell
.\mvnw.cmd -B -ntp -o '-Dmaven.repo.local=.cache/maven-repository' -pl control-plane '-Dtest=IncidentPolicyTests,ControlPlaneArchitectureTests' -l .cache/p04c-unit.log test
.\mvnw.cmd -B -ntp -o '-Dmaven.repo.local=.cache/maven-repository' -pl control-plane -Pinfrastructure-tests '-Dit.test=DurableIncidentIT,PostgresInfrastructureIT' -l .cache/p04c-postgres-available.log test-compile failsafe:integration-test failsafe:verify
.\mvnw.cmd -B -ntp -o '-Dmaven.repo.local=.cache/maven-repository' -pl control-plane '-Dtest=IncidentProcessLaunchTests' -l .cache/p04c-process-launch.log test
```

First exit0/BUILD SUCCESS at00:40:38IST,20.380s:6 policy +4 architecture =10 tests, zero failures/errors/skips. Second exit0/BUILD SUCCESS at00:41:45IST,19.333s but21 SKIPPED (17 incident +4 Core PostgreSQL),0 executed: Testcontainers Docker named-pipe access denied. No C database/process-restart success claimed.

The third command checks test-only Windows argument-file escaping/line injection/environment isolation added during continuation; its final result is recorded below. No production source changed after the10-pass run. verify-incidents.ps1 requires fresh exact21 passes/no skips and preserves reports/logs under .cache/p04c/<id>. Only selected suites count; copied old reports are not new evidence.

Launcher follow-up: exit0/BUILD SUCCESS at2026-10-21 11:01:00IST,21.421s; IncidentProcessLaunchTests3 passed,0 failures/errors/skips (suite0.401s). This is3 additional tests, not a repeated13-test invocation or an actual child-process/database pass. All C SQL/restart cases still require the host gate.

New SQL tests exercise production intake/projector/detector/recorder, incident uniqueness, atomic rollback after timeline insertion, concurrent replay, explicit tenant/transaction FKs, immutable grants, prior-context backfill, late satisfaction without resolution, bounded batches and two separate Java worker processes. They remain unverified here; full packaged scheduler/Kafka business integration is D, not this gate. V1–V4 unchanged; old test migration counts/TRUNCATE lists updated mechanically for V5 FK compatibility.

## 2026-10-21 — P0.4-B host gate PASSED on 892ac17

Evidence-only closing checks: verify-docs.ps1 exit0 (32 Markdown/65 Q&As/11 ADRs/0 issues); git diff --check passed. No executable suite repeated.

Owner command: Java21 JAVA_HOME followed by bundled PowerShell7 `-NoProfile -File .\scripts\verify-detection.ps1`. Docker29.8.2. Saved Maven log: BUILD SUCCESS at00:32:20IST (2026-10-20T19:02:20Z),46.189s, exit0. Independently inspected selected XML: DeadlineDetectionIT17/18.223s and PostgresInfrastructureIT4/10.954s,21 executed with zero failures/errors/skips. Script printed complete PASS; numeric outer-shell exit not supplied.

Evidence: `.cache/p04b/0eacae6f641c4c51ba4e57178bc155c2/postgres.log` and the two selected reports. Other copied historical reports are excluded. Earlier18 focused tests were not rerun, and previous21 skips remain historical evidence, superseded by this actual host pass.

This verifies the recorded B database scheduling/evaluation scenarios, V4 migration/startup, concurrency/idempotency, rollback, worker recreation, backlog/blocked suppression, late history and tenant/grant guards. Synthetic read outage/direct callback do not prove physical DB/process restart or packaged wiring. No incidents or full P0.4 completion claimed.

## 2026-10-21 — P0.4-B focused pass, PostgreSQL NOT verified

Closing static checks: verify-detection.ps1 parses without errors; scripts/verify-docs.ps1 exit0 (32 Markdown/20 required/124 local links/7 Mermaid/1 JSON/16 scenarios/65 Q&As/11 ADRs/0 issues); git diff --check passes. No Mermaid rendering claim. V4's observation identity NULL check was hardened after the skipped attempt; SQL remains unverified, not a passed migration.

Source baseline b46e758 plus new B working tree, Temurin21.0.12.1, Maven3.10.0; JAVA_HOME set to documented Java21 and TEMP/TMP to repository .cache/tmp. Exact commands:

```powershell
.\mvnw.cmd -B -ntp -o '-Dmaven.repo.local=.cache/maven-repository' -pl control-plane '-Dtest=InventoryInvariantTests,ControlPlaneArchitectureTests' -l .cache/p04b-unit.log test
docker info --format '{{.ServerVersion}}'
.\mvnw.cmd -B -ntp -o '-Dmaven.repo.local=.cache/maven-repository' -pl control-plane -Pinfrastructure-tests '-Dit.test=DeadlineDetectionIT,PostgresInfrastructureIT' -l .cache/p04b-postgres-available.log test-compile failsafe:integration-test failsafe:verify
```

First exit0/BUILD SUCCESS at00:25:20IST,27.059s:14 evaluator +4 architecture =18 tests, zero failures/errors/skips. Proves controlled-time boundaries, reservation/rejection, mismatch/conflict/ambiguity/backlog decisions, late-event semantics and documented module dependencies. No real60-second sleeps.

Docker CLI exit1, permission denied on dockerDesktopLinuxEngine. Third command compiled all tests but Testcontainers could not open docker_engine: AccessDeniedException. Maven exit0/BUILD SUCCESS at00:27:06IST,25.680s;17 DeadlineDetectionIT +4 PostgresInfrastructureIT =21 SKIPPED,0 executed. This is NOT B gate success.

Host verifier `scripts/verify-detection.ps1` selects only these21, saves prior/current reports and log under .cache/p04b/<id>, checks freshness/exact counts/zero skips. Tests cover production intake/projector/reader/detector with real SQL: due threshold, worker recreation, repeated/concurrent processing, rollback after evaluation insert, cyclic scan, missing/backlogged/blocked/rejected/conflicting/wrong-order evidence, late-history preservation, tenant/immutable grants and snapshot/config. Synthetic read outage is not physical database shutdown; direct production scheduled callback is not a packaged-process test. No C incidents or final P0.4 proof. Old A suites were not rerun; migration count assertions and A fixture TRUNCATE list were mechanically updated for V4's additional FK.

## 2026-10-21 — P0.4-A host PostgreSQL gate PASSED on 2b2e885

Documentation-only closing checks: verify-docs.ps1 exit0 (31 Markdown,60 Q&As,10 ADRs,0 issues); git diff --check passed. No executable tests repeated for this evidence update.

Owner used the documented Java21/bundled PowerShell7 command `scripts/verify-projection.ps1`; Docker29.8.2. Maven BUILD SUCCESS at00:16:53IST (2026-10-20T18:46:53Z),50.239s, exit0. Independently inspected selected XML: DurableProjectionIT12/11.927s and PostgresInfrastructureIT4/9.241s;16 executed, zero failures/errors/skips. Script printed complete PASS. Numeric outer PowerShell exit not supplied.

Evidence: `.cache/p04a/b69f72644f3a4835a25eab14af095e69/postgres.log` and selected `reports/TEST-*.xml`. The copied old PackagedPipelineScenario XML is not part of this run; do not count it. No43-test repeat or completed P0.3 live suite repeat. Prior Docker-denied16 skips remain historical evidence, superseded by this actual host pass.

Real V2-to-V3 backfill, atomic derived-state/progress rollback, retry/idempotency, concurrency, tenant/provenance constraints, late-visible input, runtime grants, pinned config and BLOCKED freshness now have passing database tests. This meets the A architecture gate, not B scheduling/C incident/D end-to-end acceptance.

## 2026-10-20/21 — P0.4-A implemented; real PostgreSQL gate NOT verified

Checkpoint static checks on continuation: PowerShell parser accepted verify-projection.ps1 with zero errors; `scripts/verify-docs.ps1` exit0:31 Markdown/20 required/126 local links/7 Mermaid/1 JSON/16 scenarios/60 answered interview questions/10 ADRs/0 issues. `git diff --check` passed. These are static checks, not host script or Mermaid rendering proof.

Baseline `d4d4b7c`; new working-tree A source/tests. Java21.0.12.1, Maven wrapper3.10.0, offline workspace cache. JAVA_HOME set to the documented Temurin21 directory; TEMP/TMP set to repository `.cache/tmp`.

Executed from repository root:

```powershell
.\mvnw.cmd -B -ntp -o '-Dmaven.repo.local=.cache/maven-repository' -pl control-plane '-Dtest=ProjectionModelTests,ControlPlaneArchitectureTests,EventValidatorTests,KafkaInboxConsumerTests' -l .cache/p04a-unit.log test
.\mvnw.cmd -B -ntp -o '-Dmaven.repo.local=.cache/maven-repository' -pl control-plane -Pinfrastructure-tests '-Dit.test=DurableProjectionIT,PostgresInfrastructureIT' -l .cache/p04a-postgres-available.log test-compile failsafe:integration-test failsafe:verify
```

First: exit0, BUILD SUCCESS at2026-10-20 21:38:30IST,30.678s. Architecture4 + ProjectionModel11 + EventValidator23 + KafkaInboxConsumer5 =43 executed, zero failures/errors/skips. Pure reduction covers ordering/versions/conflicts/rejection/deadline math; this does not prove SQL durability.

Second: exit0/BUILD SUCCESS at21:39:17IST,18.740s, but **0 executed /16 skipped** (DurableProjectionIT12, PostgresInfrastructureIT4). Testcontainers could not open `\\.\pipe\docker_engine`: AccessDeniedException. Maven's green exit is NOT gate success. No real PostgreSQL proof exists for A. Source/test compilation succeeded. A subsequent migration-only hardening makes BLOCKED reason explicitly NOT NULL in its conditional CHECK; that SQL still awaits the same host gate.

New `scripts/verify-projection.ps1` runs only these16 database checks, preserves prior/new reports and logs under `.cache/p04a/<id>`, requires fresh exact counts and rejects skips. It uses Testcontainers' disposable PostgreSQL/dynamic ports, no Kafka/pipeline combined classpath, no fixed HTTP port and no existing Compose volumes. Completed43 tests and old P0.3 live suites are not repeated. Require the actual host result before implementing B/C.

## 2026-10-20 — P0.3 final packaged acceptance PASSED on `860dbf9`

Owner command from the existing repository, with JAVA_HOME=`C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot`:

```powershell
& 'C:\Users\User\.cache\codex-runtimes\codex-primary-runtime\dependencies\native\powershell\pwsh.exe' -NoProfile -File .\scripts\verify-p03-final.ps1 -PackagedOnly
```

Docker29.8.2; all4 artifact freshness/content checks passed. Maven BUILD SUCCESS at19:17:57IST /13:47:57Z,2:40. Fresh saved XML independently confirms **1 test,0 failures,0 errors,0 skips**,148.296s. Maven exit0 is enforced before the script prints PASS; the outer-shell numeric exit was not separately printed. Host docs checker:30 Markdown/20 required/125 links/7 Mermaid/1 JSON/16 scenarios/55 Q&As/9 ADRs/0 issues.

The executed scenario proves all4 packaged postgres applications' HTTP health and real scheduling; Order/Payment/Warehouse committed intents published and were accepted into Core; a fourth Order intent remained held until Core stopped, published while Core was offline, then reached the restarted Core on its existing database/group. Final inbox4,quarantine0; Core runtime still lacks history SELECT and the migrator sees2 successful migrations. No existing Compose project/volume was targeted.

Evidence: `.cache/p03-final/8e10091e666a46bd90c77a5bb50d3844/packaged.log`, copied fresh XML/text in `reports/`, child logs `.cache/packaged-pipeline/8be2d159-8e6b-42c0-bfcd-e4122d70c3c8/`. Prior failed identity/timestamp runs remain below; neither was silently relabelled.

This closes P0.3 with the earlier clean reactor134, Core50 across recorded runs, and all3 source-relay/infrastructure gates. Counts overlap between suites/runs; they are not summed into an invented single-run total. The final packaged command did not rerun the default reactor. No production source changed after its build; later repairs were test/script-only. P0.4 business correlation/incidents and production load/replicated-failover claims remain outside this completion.

Closing documentation-only session: `scripts/verify-docs.ps1` exit0,30 Markdown/20 required/126 local links/7 Mermaid/1 JSON/16 scenarios/55 interview Q&As/9 ADRs/0 issues. `git diff --check` passed. No Maven/Docker suites rerun.

## 2026-10-20 — packaged pipeline reached final check; migration-history identity repair

Owner ran bundled PowerShell7 `-NoProfile -File .\scripts\verify-p03-final.ps1 -PackagedOnly` on `c0e5423`, Java21/Docker29.8.2. All4 timestamp/content guards passed. Maven BUILD FAILURE at19:11:02IST /13:41:02Z,3:00; saved Failsafe XML/text: **1 test,0 failures,1 error,0 skips**, suite149.2s, test84.49s. Numeric outer-shell exit was not printed.

The error was PostgreSQL `permission denied for table flyway_schema_history` at PackagedPipelineScenario.java:94. The test's preceding assertions completed: all4 healthy executable applications, three source intents PUBLISHED/accepted, held Order intent absent until Core stopped, publication while Core offline, restarted Core acceptance, inbox4/quarantine0 and final health. These are partial observations from a failed test, not a final acceptance PASS.

The test now asserts `has_table_privilege(current_user,'aegisflow.flyway_schema_history','SELECT')` is false for Core runtime, then performs the existing two-successful-migrations assertion using a separate Core migration-role connection. No grants/production components changed. Retry only the packaged phase.

Local Java21/Maven3.10 validation: `.\mvnw.cmd -B -ntp -o '-Dmaven.repo.local=.cache/maven-repository' -pl control-plane -Ppipeline-tests -l .cache/p03-packaged-history-compile.log test-compile` exited0 at19:12:49IST,16.871s. This compiled the correction and executed0 tests; corrected DB behavior remains unverified. `scripts/verify-docs.ps1` exit0:30 Markdown/20 required/125 links/7 Mermaid/1 JSON/16 scenarios/55 Q&As/9 ADRs/0 issues. Whitespace check passed.

Preserved evidence: `.cache/p03-final/70ac080a2361488380d7a7e83b466108/packaged.log` and `reports/` (copied failing XML/text); `.cache/packaged-pipeline/287006c5-7885-491b-a1a8-0b3bfafc992b/{core,core-restart,order,payment,warehouse}.log`. Child logs confirm separate Core process startup and schema already up to date. No clean reactor or completed live suite repeated.

## 2026-10-20 — clean reactor134 passed; reproducible JAR timestamp guard repaired

Owner ran bundled PowerShell7 `-NoProfile -File .\scripts\verify-p03-final.ps1`, JAVA_HOME=Temurin21.0.12.1, Docker29.8.2, source `7d8f473`. Its Maven `-B -ntp "-Dmaven.repo.local=<repo>/.cache/maven-repository" -l <evidence>/reactor.log clean verify` finished BUILD SUCCESS at12:08:57IST /06:38:57Z,51.820s. Saved XML independently sums Core43 +Order31 +Payment31 +Warehouse29 =**134 tests,0 failures/errors/skips**. The five-module reactor and all4 executable packages succeeded. Evidence directory: `.cache/p03-final/0cdf2d63e84f49fea1385e806effaf8b`.

The outer verifier then threw `Executable JAR is older than its production inputs: control-plane` before invoking the packaged scenario. No numeric outer-shell exit was printed. Inspection found executable mtime1980-02-01 versus Core `.jar.original` actual build time2026-10-20T06:38:24Z. Pinned Boot repackaging normalizes the executable timestamp; the guard was invalid for these reproducible artifacts. This was not a failed Maven build or a Kafka/port failure.

The repaired `scripts/lib/packaged-artifact.ps1` uses the required original archive's build time and compares SHA256 for its application/resources/Maven metadata against the executable archive, rejecting missing/changed/extra application content. Production input freshness remains enforced. This local resume check does not independently establish dependency provenance.

Actual repair checks with bundled PowerShell7 on2026-10-20:

- `-NoProfile -File .\scripts\test-packaged-artifact.ps1`: exit0,6 regression groups passed (normalized executable timestamp, changed input, same-length byte mismatch, missing resource, extra resource, missing archive pair).
- Dot-source `scripts/lib/packaged-artifact.ps1`; invoke `Assert-RepackagedJarCurrent -JarPath <module executable> -InputPaths <all module src/main files plus module/root pom.xml>` for each of the4 modules: exit0,4 existing host-built archive pairs passed.
- No Maven/Java/Docker test suites repeated; all original reports and artifacts retained.
- All3 changed/new PowerShell scripts parse with0 errors. `scripts/verify-docs.ps1` exit0:30 Markdown,20 required,125 links,7 Mermaid,1 JSON,16 scenarios,55 interview Q&As,9 ADRs,0 issues. `git diff --check` exit0.

Next: `scripts/verify-p03-final.ps1 -PackagedOnly` must execute the remaining1 real packaged case with0 failures/errors/skips. That live result is still unverified, so P0.3 remains open.

## 2026-10-20 — final packaged acceptance prepared after Core50 passed

No production/relay/schema changes. Java21/Maven3.10 offline workspace cache; TEMP/TMP=`.cache/tmp`. `M` is `.\mvnw.cmd -B -ntp -o '-Dmaven.repo.local=.cache/maven-repository'`.

| Exact command after M | Actual result |
|---|---|
| `-pl control-plane '-Dtest=PackagedProcessTests' -l .cache/p03-packaged-process-unit.log test` | exit1,11:53:21IST,14.015s;4 errors/0 failures/0 skips from eager HttpClient initialization requiring sandbox-denied loopback; corrected with lazy transport |
| same selector `-l .cache/p03-packaged-process-unit-rerun.log test` | exit0,11:54:54IST,16.850s;4 passed,0 failures/errors/skips |
| `-pl control-plane '-Pinfrastructure-tests,pipeline-tests' '-Dit.test=PackagedPipelineScenario' -l .cache/p03-packaged-available.log test-compile failsafe:integration-test failsafe:verify` | exit0,11:58:09IST,21.012s;1 skipped,0 executed under Docker denial; not a pass |
| `-pl control-plane -Ppipeline-tests -l .cache/p03-packaged-final-compile.log test-compile` | exit0,12:00:28IST,7.547s after PENDING-source preconditions; compile only |

New process unit checks cover owner-only child configuration, separate executable-JAR classpath/port0, extracting actual HTTP ports, and health UP with optional Actuator groups while rejecting malformed/duplicate/trailing/nested false positives. Live case targets actual packaged schedulers for3 source events and real Core process restart with a fourth committed intent published during its absence. The seed helper only creates real business/outbox transactions and never invokes relay polling; preconditions require PENDING outboxes and empty Core inbox.

Bundled PowerShell7 `scripts/verify-p03-final.ps1` with Java21 exited1 at Docker pipe denial before Maven/clean; its parser reports0 errors. Final reactor and packaged scenario remain unverified. Host script backs up existing reports, builds normal default reactor/JARs (no infrastructure profile), checks every fresh default report, then requires1 actual packaged pass. It does not rerun completed source relay or Core manual pipeline ITs. Detailed Maven logs are under ignored `.cache/p03-final/<id>`; child logs under `.cache/packaged-pipeline/<id>`. `-PackagedOnly` resumes only the new scenario with fresh-enough existing production JARs after a successful build. Earlier Core50 evidence remains passed separately.

Closing parser0 errors; runtime production-input enumeration/freshness setup passed for all4 modules. Documentation checker exit0:30 Markdown/20 required/125 links/7 Mermaid/1 JSON/16 scenarios/55 Q&As/9 ADRs/0 issues. Whitespace passed. No final reactor/live packaged result is claimed by static checks.


## 2026-10-20 — ordinary Core startup gate passed on `42b247d`

Owner ran bundled PowerShell7 `-NoProfile -File .\scripts\verify-core-inbox.ps1 -CoreStartupOnly`, JAVA_HOME=Temurin21.0.12.1, Docker29.8.2. BUILD SUCCESS at11:45:16IST /06:15:16Z;24.232s. Final verifier PASS and saved XML agree: **4 passed,0 failures/errors/skips**,12.549s. Numeric outer-shell exit not printed. No test rerun.

This proves ordinary Core-only classpath startup, V1+V2 migration/validation, HTTP200/UP and existing runtime-role/no-foreign-database checks. The classpath repair is now live-verified. Combined with earlier36 unit/boundary/port and10 real pipeline passes on `c96dc5c`, all50 Core-gate cases have actual passing evidence across separate runs; earlier failures remain history. Packaged scheduler/process restart and final clean reactor still remain before P0.3 completion.


## 2026-10-20 — real pipeline passed on `c96dc5c`; ordinary Core check classpath repair

Host Java21.0.12.1/Docker29.8.2, bundled PowerShell7 `scripts/verify-core-inbox.ps1`; BUILD FAILURE at11:35:39IST /06:05:39Z,2:03. Independently read all six saved XML suites: **36 unit/boundary/port +10 real pipeline cases passed**,0 failures/errors/skips there;4 ordinary Core checks errored,0 failures/0 skips. Numeric outer shell exit not printed. No test rerun to record this.

| Suite | Executed result | Seconds |
|---|---|---:|
| ControlPlaneArchitectureTests |4 passed|3.939|
| EventValidatorTests |23 passed|0.872|
| KafkaInboxConsumerTests |5 passed|1.084|
| PipelinePortTests |4 passed|0.075|
| CorePipelineScenario |10 passed|89.205|
| PostgresInfrastructureIT |4 errors from common context failure|6.092|

The passed pipeline proves actual three-owner business transactions/outboxes/relays → Kafka → Core inbox with both negative outcomes, duplicate/conflict, consumer restart/commit-before-ack, real DB rollback/retry, poison/schema/source/tenant rejection, real concurrent consumers across two groups, immutable grants/identity constraints and quarantine uniqueness. Core V2 is exercised through the real JDBC-manager context. This does not prove packaged scheduling, process kill, replication, or ordinary-profile startup.

First ordinary Core failure: Flyway `Found more than one migration with version 1`, offenders include Warehouse/Order V1 resources. The special pipeline profile's additional classpath was incorrectly shared with the ordinary Core IT. The verifier now separates pipeline from normal Core invocations. Core migrations remain V1/V2; source V1s and production configurations are unchanged.

New pure plan regression command: bundled PowerShell7 `-NoProfile -File .\scripts\test-core-inbox-verification.ps1`, exit0,5 guard groups passed. PowerShell parser across verifier/helper/test:0 errors. Bundled PowerShell7 `scripts/verify-core-inbox.ps1 -CoreStartupOnly` with Java21 exited1 at this session's Docker named-pipe denial before tests. Original failed and passed host reports remain preserved. No new live success claimed.

Next host command uses `-CoreStartupOnly`, selecting only the remaining4 cases under `-Pinfrastructure-tests`, without pipeline-tests. Its partial PASS covers exactly4 fresh zero-skip reports; previous36+10 are preserved independent evidence, not relabeled as a new full run. Default verifier still requires all50 cases, checking each phase's reports immediately. Final reactor/packaged scheduler acceptance remains before P0.3 completion.

Closing documentation checker exited0:30 Markdown/20 required/125 local links/7 Mermaid/1 JSON/16 scenarios/50 interview Q&As/9 ADRs/0 issues. Whitespace passed; scoped script/doc diff reviewed. No Java/build/database test rerun for this orchestration-only repair.


## 2026-10-20 — Core inbox implementation from `4cb4ee6`; real pipeline pending

Java21.0.12.1/Maven3.10, offline workspace cache, TEMP/TMP=`.cache/tmp`. `M` means `.\mvnw.cmd -B -ntp -o '-Dmaven.repo.local=.cache/maven-repository'`. Commands ran from the repository root. No completed source relay suites rerun.

| Exact command after M | Log / observed outcome |
|---|---|
| `-pl control-plane -l .cache/p03-core-inbox-compile.log test-compile` | exit0,11:08:26IST,9.316s; Core implementation compiled |
| `-pl control-plane '-Dtest=EventValidatorTests,KafkaInboxConsumerTests,ControlPlaneArchitectureTests' -l .cache/p03-core-inbox-unit.log test` | exit1,11:10:06IST,8.886s; three new-test lambda captures did not compile; corrected |
| same selection `-l .cache/p03-core-inbox-unit-rerun.log test` | exit0,11:10:51IST,17.974s;30 tests passed,0 failures/errors/skips |
| `-Ppipeline-tests -l .cache/p03-pipeline-compile.log test-compile` | exit0,11:13:10IST,11.331s; all five reactor modules compiled, no tests run |
| `-pl control-plane -Ppipeline-tests '-Dtest=EventValidatorTests,KafkaInboxConsumerTests,ControlPlaneArchitectureTests' -l .cache/p03-core-inbox-final-unit.log test` | exit0,11:14:36IST,28.998s;31 passed:22 validation,5 consumer,4 boundaries;0 failures/errors/skips; includes exact-number regression |
| `-pl control-plane '-Pinfrastructure-tests,pipeline-tests' '-Dit.test=CorePipelineScenario,PostgresInfrastructureIT' -l .cache/p03-core-pipeline-available.log test-compile failsafe:integration-test failsafe:verify` | exit0,11:16:49IST,32.148s; **12 skipped**,0 executed:8 disabled pipeline methods (parameterized method not expanded to3 cases without Docker),4 existing Core DB/HTTP checks; no live pass |
| `-pl control-plane '-Dtest=PipelinePortTests' -l .cache/p03-core-port-regression.log test` | exit0,11:20:36IST,11.862s;4 port/URL checks passed,0 failures/errors/skips |

A first unquoted comma-separated profile argument was rejected by PowerShell parsing before Maven started; rerun above quotes it. No test was disabled to resolve an assertion failure. Docker CLI/API probe and bundled PowerShell7 `scripts/verify-core-inbox.ps1` exit1 at named-pipe permission denial. Script parser reports0 errors; new JSON Schema parses as JSON. These results do not grant this session Docker access.

Core unit coverage: required fields, initial schemas/reasons, major/minor handling, parser budgets/malformed UTF-8/duplicate keys/trailing JSON, key/source/tenant authority, canonical amount/time and exact numeric identity, DB-before-offset sequencing, failure rewind, ambiguous offset outcome and scoped consumer settings. Port tests verify explicit IPv4 host binding, rejected invalid ports and mapped JDBC URLs for BOTH runtime and Flyway in all four apps.

Written/compiled live targets: actual simulator service transaction→own outbox→configured relay→real authenticated broker→Core PostgreSQL;3 positive sources,2 negative outcomes, equivalent/changed duplicates, consumer restart after commit-before-ack, actual DB trigger rollback/redelivery, malformed/unsupported/source-spoofed/other-source-only tenant claims, concurrent processing/DB uniqueness, immutable runtime evidence and quarantine receipt uniqueness. Ten expected pipeline invocations plus four relevant existing Core migration/startup tests; none passed locally. The broker fixture retains the verified permission and explicit stable-port fixes. Consumer restart uses a new consumer instance/same group; crash windows are injected exceptions rather than OS process kills. Scheduling is manual in the harness.

Final reviewed unit command: `M -pl control-plane '-Dtest=EventValidatorTests,KafkaInboxConsumerTests,PipelinePortTests,ControlPlaneArchitectureTests' -l .cache/p03-core-reviewed-unit.log test` exited0 at11:25:31IST,18.929s: **36 passed,0 failures/errors/skips** (23 validator,5 consumer,4 boundaries,4 port). The added poison regression rejects NUL/unpaired-surrogate strings and extreme optional numbers before JSONB insertion. Earlier compile-only refinement `M -pl control-plane -Ppipeline-tests -l .cache/p03-core-checkpoint-compile.log test-compile` exited0 at11:26:27IST,9.580s. Subsequent concurrency harness refinement uses two actual Kafka clients in separate groups to race normal intake and replay; it is compile-checked, not live-verified.

Host command: bundled PowerShell7 `scripts/verify-core-inbox.ps1` with Java21 and Docker Desktop. It first compiles all existing modules, then requires36 Core unit/boundary/port +14 actual live results with fresh reports and zero skips. It selects no completed relay tests or existing Compose volumes. P0.3 remains incomplete; final reactor/packaged scheduler acceptance remains after this gate.

Closing refinement: `M -pl control-plane -Ppipeline-tests -l .cache/p03-core-closing-compile.log test-compile` exited0 at11:29:55IST,9.237s. This compiles the final two-real-consumer concurrency case and Core-only JDBC-manager guard; it executes no tests. Final verifier parser0 errors; documentation checker exit0 (30 Markdown/20 required/125 links/7 Mermaid/1 JSON/16 scenarios/50 Q&As/9 ADRs/0 issues). Whitespace passed. Source simulators, existing broker/Compose configuration and V1 migrations unchanged.


## 2026-10-20 — Payment/Warehouse live relay gate passed on `c887490`

Owner ran bundled PowerShell7 `-NoProfile -File .\scripts\verify-simulator-relays.ps1` with JAVA_HOME set to Temurin21.0.12.1 and Docker29.8.2. BUILD SUCCESS at10:57:43IST /05:27:43Z, total3:43. Final verifier PASS: **40 unit/boundary +24 actual PostgreSQL/Kafka tests,0 failures/errors/skips**. Independently read all ten saved XML reports; no rerun. Outer-shell numeric exit not supplied.

| Owner | Unit/boundary passes | PostgreSQL relay passes | Kafka delivery passes |
|---|---:|---:|---:|
| Payment |20 (1 architecture+3 publisher+16 relay)|9 (6.377s)|3 (88.128s)|
| Warehouse |20 (1 architecture+3 publisher+16 relay)|9 (6.379s)|3 (85.859s)|

This validates the repaired Flyway URLs against actual disposable databases, DB claim/fencing/tenant/concurrency behavior and each source's actual Kafka acknowledgement, bounded outage/restart and immutable-ID redelivery after completion rollback. Existing Order evidence remains separate and passed. Core-identity observers are test consumers, not durable Core ingestion; full simulator-to-Core, packaged scheduling and final reactor acceptance are still outstanding.

The log includes nonfatal metadata401 warnings and Kafka disconnect/reconnect messages during broker fault/teardown; all selected cases and final verifier passed. No credential change or warning suppression was performed. Earlier failed host results below remain historical. No existing Compose volumes targeted.


## 2026-10-20 — repair separate Flyway test URLs after host failure

Host run on `b78a2d3` using bundled PowerShell7 `scripts/verify-simulator-relays.ps1` failed at2026-10-19 21:25:17IST,1:47. Payment:12 tests,0 failures,12 errors,0 skips; Warehouse reactor skipped. Both saved Payment Failsafe text reports identify Flyway SQL state08001, connection refused at127.0.0.1:5435. Context-threshold errors are downstream; no relay assertion pass is inferred.

Cause: the new tests set `spring.datasource.url` but not the profiles' independent `spring.flyway.url`. All four new Payment/Warehouse relay/Kafka ITs now explicitly point both URLs to their own Testcontainers PostgreSQL; runtime/migration credentials remain distinct. Existing persistence tests already use this pattern. No production profile, dependency, port, migration or volume change.

With Java21 and TEMP/TMP=`.cache/tmp`, ran:

```powershell
.\mvnw.cmd -B -ntp -o '-Dmaven.repo.local=.cache/maven-repository' -pl simulators/payment-service,simulators/warehouse-service -l .cache/p03-relay-flyway-url-compile.log test-compile
```

Exit0, BUILD SUCCESS,2026-10-20 10:51:15IST,17.139s; Payment10 and Warehouse11 test source files compiled. **Compile only: no new tests executed.** Existing deprecation/unchecked warnings remain nonfatal. Read-only `docker version --format '{{.Server.Version}}'` again returned Docker engine pipe permission denial; no new live run attempted and the failed host reports remain intact. Next: unchanged host verifier; require40+24 zero-skip passes before recording success.


Closing checks: bundled PowerShell7 `scripts/verify-docs.ps1` exited0 (30 Markdown,20 required,124 links,7 Mermaid,1 JSON,16 scenarios,45 interview Q&As,9 ADRs,0 issues). `git diff --check` passed; four test URL overrides and continuity-only documentation reviewed. No completed database tests rerun.

## 2026-10-19 — Payment/Warehouse relay source checkpoint from `c47edbb`

Java21.0.12.1/Maven3.10, offline workspace `.cache/maven-repository`, TEMP/TMP=`.cache/tmp`. Existing Order source/tests and V1s unchanged. `M` below abbreviates `.\mvnw.cmd -B -ntp -o '-Dmaven.repo.local=.cache/maven-repository' -pl simulators/payment-service,simulators/warehouse-service`.

| Exact command | Observed outcome |
|---|---|
| `M '-Dtest=*OutboxRelayTests,KafkaOutboxPublisherTests' -l .cache/p03-sibling-relay-unit.log test` | exit0,21:11:47IST,45.655s;38 new tests passed,0 failures/errors/skips (16 relay+3 publisher each) |
| `M -Pinfrastructure-tests '-Dtest=*ArchitectureTest' '-Dit.test=*OutboxRelayIT,*KafkaDeliveryIT' -l .cache/p03-sibling-relay-available.log test failsafe:integration-test failsafe:verify` | exit0,21:13:01IST,36.356s;2 module-boundary tests passed;**24 ITs skipped**,0 executed, because Docker access denied |
| Bundled PowerShell7 `-NoProfile -File .\scripts\verify-simulator-relays.ps1` with Java21 | exit1 at Docker pipe denial; does not claim success |
| PowerShell parser for new verifier |0 syntax errors |

Unit coverage includes each owner's actual factory for both initial outcome types, existing canonical hash/JSONB representation, wrong owner/tenant/event ID/hash/schema rejection, fixed owner SASL principal/topic, acknowledgement before completion, bounded retries, stale completion, ambient transaction rejection, interruption and disabled profiles. Boundary checks prove no production dependency on Core/sibling implementation packages. Owner-local copies keep deployables independent; no shared database or shared domain artifact introduced.

Written/compiled but NOT passed here:9 DB cases per owner for committed claims, concurrency, expiry/fence/tenant/worker guards, immutable payload/identity and synthetic acknowledged-send replay;3 Kafka cases per owner for positive-outcome actual publication, broker stop/restart and actual ack/local-completion rollback redelivery. Tests use each actual application service and owner runtime role; broker observer has Core read identity but is not a durable inbox. Existing verified Order fixtures were adapted with the permission/explicit-port fixes intact. Negative outcome wire delivery is not separately live-tested by these3 cases.

`verify-simulator-relays.ps1` targets only these new paths plus boundaries and requires40 unit/boundary +24 live tests, fresh reports and zero failures/errors/skips. It never targets existing Compose volumes or completed Order tests. Current host result remains pending; no full reactor or completed P0.3 claim. Core inbox and full simulator→Core demonstration are still unwritten.

After owner-specific bean-method naming cleanup, `M -l .cache/p03-sibling-relay-final-compile.log test-compile` exited0 at21:15:57IST,13.864s. Both modules and all test sources compile. Bundled PowerShell7 `scripts/verify-docs.ps1` exited0:30 Markdown/20 required/124 links/7 Mermaid/1 JSON/16 scenarios/45 Q&As/9 ADRs/0 issues; whitespace passed. No Kafka/PostgreSQL result inferred from these closing checks.

## 2026-10-19 — real Order PostgreSQL-to-Kafka host gate passed on `a4a66de`

Owner ran, with Java21 selected, from the repository:

```powershell
& 'C:\Users\User\.cache\codex-runtimes\codex-primary-runtime\dependencies\native\powershell\pwsh.exe' -NoProfile -File .\scripts\verify-order-kafka.ps1
```

BUILD SUCCESS finished20:57:40IST /15:27:40Z; Maven1:34. Final verifier PASS and independently read saved Failsafe XML agree: **3 passed,0 failures/errors/skips**, suite80.913s. Numeric outer-shell exit not supplied. No test rerun performed to record this evidence.

| Actual test method | Seconds | Result |
|---|---:|---|
| committedOutboxReachesRealKafkaBeforeDurablePublishedCompletion |2.388|PASS|
| actualKafkaAckThenLocalCompletionFailureRedeliversOriginalEvent |0.782|PASS|
| brokerOutagePreservesCommittedIntentAndRecoversWithSameIdentityAfterRestart |16.0|PASS|

This proves actual Order aggregate/outbox commit, real acknowledged publication, independent database completion visibility, consumer identity/hash/key/offset checks, retained intent and bounded retry while broker is stopped, stable endpoint/data across same-container restart, eventual retry delivery, and duplicate unchanged content after actual broker ack plus injected DB-completion rollback. Startup permissions and explicit port fixes are exercised. These3 tests are separate from the previously passed27default+17PostgreSQL tests, not a new47-test reactor run.

Limits: manual polling with real enabled Spring configuration, not packaged scheduled startup; rollback injection, not process kill; single broker, not replicated failover; Core-identity observer, not durable inbox. Payment/Warehouse relays, Core inbox and full P0.3 acceptance remain next. Prior failures/skips below remain historical; host success does not alter restricted-session Docker access. No existing Compose volumes were targeted and no cleanup/push was requested.

Documentation-only closing check: bundled PowerShell7 `scripts/verify-docs.ps1` exited0:30 Markdown/20 required/125 local links/7 Mermaid/1 JSON/16 scenarios/40 Q&As/9 ADRs/0 issues. Whitespace passed; no test rerun.

## 2026-10-19 — host startup repair confirmed; restart port instability repaired in test fixture

On `2834b47`, owner's same focused verifier reached Docker29.8.2, Kafka startup (12.532s), private/source mode assertions, topic/ACL provisioning, PostgreSQL18.6 migration and enabled Spring producer startup. It then failed:3 tests/0 assertion failures/**3 errors**/0 skips, Maven BUILD FAILURE20:50:16IST,1:55. Saved Failsafe text/XML agree. The outage method failed the exact-port check after same-container restart; two later methods timed out against the old bootstrap endpoint. A pre-outage actual marker round trip necessarily completed before the failing restart path, but no whole test passed. Do not infer that every outage assertion passed: the old finally block could mask a prior failure.

Cause boundary: Docker was given an empty HostPort (random allocation), which did not retain the expected mapping across restart. Test-only fix chooses a free local loopback port, explicitly requests it in Docker config, and supplies the same advertised port before startup. This retains same broker/storage/clients and does not weaken the restart assertion. Rare selection-to-bind collision remains a safe startup failure. Outage runs last; earlier runtime/assertion failures are preserved with restart failure suppressed. Production relay/SQL/Compose and existing storage unchanged.

Exact local check with Java21/TEMP/TMP workspace cache: `.\mvnw.cmd -B -ntp -o '-Dmaven.repo.local=.cache/maven-repository' -pl simulators/order-service '-Dtest=OrderKafkaFixtureTests' -l .cache/p03-kafka-restart-port.log test` exited0 at20:53:54IST,23.038s. **2 unit checks passed**,0 failures/errors/skips;11 test sources compiled. These inspect Docker binding configuration/invalid-port rejection, not live port allocation or restart. No full/default/database rerun. Next host command remains `scripts/verify-order-kafka.ps1`; real repaired restart and all3 delivery/fault methods remain unverified.

Closing bundled-PowerShell7 `scripts/verify-docs.ps1` exited0:30 Markdown/20 required/124 local links/7 Mermaid/1 JSON/16 scenarios/40 Q&As/9 ADRs/0 issues; whitespace passed.

## 2026-10-19 — real host startup failure and read-only configuration repair

Owner ran bundled PowerShell7 `-NoProfile -File .\scripts\verify-order-kafka.ps1` on `24df774`, Java21/Docker29.8.2. Docker discovery/Ryuk succeeded. Kafka exited1 and its90s health wait expired; Maven BUILD FAILURE at03:07:54IST,1:46 total. Actual XML confirms `tests=1 failures=0 errors=1 skipped=0` (class initialization, none of the3 delivery methods reached). Outer-shell numeric exit not supplied; verifier threw. The first logged failure is inability to append `/tmp/aegisflow-kafka/server.properties`; later missing controller JAAS is a consequence. Nonfatal metadata401 warnings are unrelated; no credentials were added.

The fixture copies its template with mode0444. `startup.sh` previously applied chmod0600 only AFTER appending dynamic listener/JAAS settings. It now secures/makes writable only the private copy BEFORE that append, and copy/chmod/append each explicitly fail closed. The fixture retains0444 and adds real source/private mode assertions, so a future host pass exercises this condition. No root workaround,777 permissions, source chmod or auth weakening.

Repair verification from repository root, Java21, workspace TEMP/TMP/cache:

| Command | Actual outcome |
|---|---|
| `.\mvnw.cmd -B -ntp -o '-Dmaven.repo.local=.cache/maven-repository' -pl simulators/order-service '-Dtest=KafkaStartupScriptTests' -l .cache/p03-kafka-startup-permissions.log test` | exit0 at20:45:58IST,13.475s;2 new source-level checks pass,0 failures/errors/skips;10 test sources compile |
| `& 'C:\Program Files\Git\bin\bash.exe' -n scripts/kafka/startup.sh` | exit-1073741502; MSYS `NtCreateDirectoryObject` access denied before syntax parsing; NOT a syntax pass or a shell-code failure |
| `git diff --check` | exit0 |

No live test rerun in the restricted session and no prior44-test rerun. The host must rerun the focused verifier; all3 real delivery/fault tests remain unverified. Source-level guards are not evidence of real Linux permission or Kafka behavior. Original host failure remains recorded rather than replaced with an inferred pass.

Closing bundled-PowerShell7 `scripts/verify-docs.ps1` exited0:30 Markdown/20 required/124 links/7 Mermaid/1 JSON/16 scenarios/40 Q&As/9 ADRs/0 issues; whitespace passed.

## 2026-10-19 — focused real Order-to-Kafka test checkpoint, host execution pending

Entry `f6709fd`; production source/SQL/Compose untouched. Same Java21.0.12.1/Maven3.10/workspace cache as the preceding gate; TEMP/TMP point to `.cache/tmp`. No completed44-test rerun. Here `M` means `.\mvnw.cmd -B -ntp -o '-Dmaven.repo.local=.cache/maven-repository' -pl simulators/order-service`.

| Exact command | Actual outcome |
|---|---|
| `M -l .cache/p03-order-kafka-compile.log test-compile` | exit0,02:58:51IST,10.524s; new test/helper compiled |
| `M -Pinfrastructure-tests -l .cache/p03-order-kafka-it.log test-compile failsafe:integration-test failsafe:verify '-Dit.test=OrderKafkaDeliveryIT'` | exit0,03:02:02IST,22.735s;3 discovered,0failures,0errors,**3 skipped**; denied Docker environment, no actual publication |
| Bundled PowerShell7 `-NoProfile -File .\scripts\verify-order-kafka.ps1` with Java21 | exit1 at denied Docker engine pipe; correctly does not report PASS |
| PowerShell parser for new verifier |0 syntax errors |

The three new ITs target FM-003/004: independent-connection observations of committed PENDING until real producer acknowledgement; unavailable broker with bounded send and persisted retry time, future-time eligibility, same-container restart and retained broker marker; real acknowledgement followed by a deliberately rolled-back local completion transaction, expired lease and identical event/key/content at a later broker offset. Runtime Order DB credentials and Order-only Kafka publishing credentials are used; the test observer has Core read/group credentials, not a durable inbox. The enabled Spring configuration is instantiated, but scheduled callbacks are suppressed and polls invoked manually. Test fault injection is not an OS process kill. No universal exactly-once, packaged startup, or successful live Kafka claim is made.

The host verifier uses fresh report timestamps and requires exactly3 tests with zero failures/errors/skips. It invokes direct Failsafe goals, not the completed default/PostgreSQL suites. Testcontainers provisions independent PostgreSQL/Kafka resources using existing pinned images and Kafka scripts; ownership labels guard broker stop/start. Existing Compose containers/volumes and private env files are never used. Host command is in HANDOFF. Initial compile and discovery above preceded a test-only port-file location refinement; final compile/static checks are recorded in the closing entry below.

Closing: `M -l .cache/p03-order-kafka-final-compile.log test-compile` exited0 at03:04:12IST,9.004s, compiling all9 Order test sources. Bundled PowerShell7 `scripts/verify-docs.ps1` exited0:30 Markdown/20 required/124 links/7 Mermaid/1 JSON/16 scenarios/40 Q&As/9 ADRs/0 issues. `git diff --check` passed. These are compile/documentation checks, not actual Kafka acceptance. Test-only helper keeps the advertised-port handoff file under `/tmp` (no tmpfs in this fixture), separate from broker data and retained across same-container restart.

## 2026-10-19 — Order host gate passed on `b0f1a16`

Owner ran from the existing repository, with `JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot`:

```powershell
& 'C:\Users\User\.cache\codex-runtimes\codex-primary-runtime\dependencies\native\powershell\pwsh.exe' -NoProfile -File .\scripts\verify-order-relay.ps1
```

The script ran Order-only Java21/Maven3.10 `clean verify -Pinfrastructure-tests` using the workspace Maven cache. **BUILD SUCCESS**, finished2026-10-19T02:48:14+05:30 / 2026-10-18T21:18:14Z, reported duration1:00, Docker29.8.2, Testcontainers2.0.5, PostgreSQL18.6 with the existing image pin. Final verifier PASS; no numeric outer-shell exit code supplied. Metadata401 warnings from Flyway community/Redgate repositories were nonfatal; no credentials or dependency changes were needed.

Independently checked all saved XML suite counts under Order `target/surefire-reports` and `target/failsafe-reports` without rerunning anything:

| Suite | Passed | Failures / errors / skipped |
|---|---:|---|
| OrderSimulatorApplicationTests | 2 | 0 / 0 / 0 |
| OrderSimulatorArchitectureTest | 1 | 0 / 0 / 0 |
| KafkaOutboxPublisherTests | 3 | 0 / 0 / 0 |
| OrderOutboxRelayTests | 15 | 0 / 0 / 0 |
| OrderPersistenceTests | 6 | 0 / 0 / 0 |
| OrderOutboxRelayIT | 9 | 0 / 0 / 0 |
| OrderPersistenceIT | 8 | 0 / 0 / 0 |

Total **44 passed:27 default +17 real PostgreSQL**. Both independent test databases applied V1; foundation and postgres HTTP checks passed. This supersedes the earlier environment-limited Order result, preserving its failures/skips below. Relay SQL/fencing/concurrency and the synthetic-publisher ack/crash replay passed on PostgreSQL; it is **not actual Kafka relay delivery**, enabled-relay profile verification, a full root reactor, Payment/Warehouse relay or Core inbox acceptance. Real DB→Kafka outage/ack-crash tests are the next unfinished gate. No existing Compose storage was targeted; no runtime test repeated to record this evidence.

Documentation-only closing check at2026-10-18T21:21:33Z exited0: Markdown=30 / Required=20 / LocalLinks=123 / Mermaid=7 / JSON=1 / Scenarios=16 / InterviewQA=35 / ADRs=9 / Issues=0. Whitespace passed; no source/configuration changes or runtime rerun.

## 2026-10-19 — Order-only relay source/unit checkpoint, PostgreSQL gate pending

Source starts from `3bd9189`, with interrupted Order-only edits preserved. Java21 Temurin21.0.12.1, Maven3.10; workspace `.cache/maven-repository`, `TEMP`/`TMP` set to repository `.cache/tmp`. No changes to V1, other applications or Compose. Commands below ran from the existing repository; `M` abbreviates `.\mvnw.cmd -B -ntp -o '-Dmaven.repo.local=.cache/maven-repository' -pl simulators/order-service`.

| Exact command (using M prefix above) | Observed outcome |
|---|---|
| `M '-Dtest=OrderOutboxRelayTests,KafkaOutboxPublisherTests' -l .cache/p03-order-relay-unit.log test` | exit1,02:19:35IST,13.157s; new tests used obsolete three-argument MockProducer constructor; production source compiled, tests did not run |
| Same selection with `-l .cache/p03-order-relay-unit-rerun.log test` after four-argument constructor fix | exit0,02:20:21IST,16.018s;18 new tests passed,0failures/errors/skips |
| `M -Pinfrastructure-tests '-Dit.test=OrderOutboxRelayIT' -l .cache/p03-order-relay-verify.log verify` | exit1,02:21:34IST,14.999s;27default tests:25passed,0assertion failures,2errors,0skips. Existing HTTP test class initialization failed because Java loopback connect was denied; ITs not reached |
| `M -Pinfrastructure-tests '-Dtest=OrderSimulatorArchitectureTest,OrderPersistenceTests,OrderOutboxRelayTests,KafkaOutboxPublisherTests' '-Dit.test=OrderOutboxRelayIT' -l .cache/p03-order-relay-available.log verify` | exit0,02:22:38IST,28.967s;25selected tests passed,0failures/errors/skips; JAR packaged. **Nine relay PostgreSQL tests skipped**,0executed, due Docker pipe denial. Maven success is not acceptance |
| Bundled PowerShell7 `-NoProfile -File .\scripts\verify-order-relay.ps1` with Java21 | exit1 at explicit Docker gate before Maven; named-pipe permission denied, correctly not PASS |
| PowerShell parser for new verifier | zero parse errors; not host runtime validation |

New unit tests prove orchestration/serialization/configuration decisions with test doubles, not actual broker acknowledgement or PostgreSQL locks. Nine real PostgreSQL tests are written for durable/disjoint/skipped-lock claims, lease expiry/reclaim/fencing, tenant/owner/token denial, terminal states, timed retry, independent claim commit and simulated ack/completion-crash replay outside the DB transaction. The last uses an acknowledged test publisher, not real Kafka. Old eight Order persistence ITs remain enabled; both IT contexts explicitly disable the new scheduler so a caller's opt-in cannot steal fixture rows.

The new host verifier runs only Order `clean verify -Pinfrastructure-tests`, checks both fresh IT reports and rejects all skips. Expected current counts are27default+17PostgreSQL, not observed host results yet. No full root reactor, enabled-relay startup, actual DB→Kafka event, outage/restart/duplicate delivery, Payment/Warehouse relay or Core inbox pass is claimed. Prior infrastructure host results remain valid and need no repetition.

Closing documentation/whitespace checks at 2026-10-18T20:56:27Z passed: Markdown=30 / Required=20 / LocalLinks=123 / Mermaid=7 / JSON=1 / Scenarios=16 / InterviewQA=35 / ADRs=9 / Issues=0. These are documentation checks, not additional runtime tests.

## 2026-10-17 — actual Kafka host verification passed on `0b7a707`

Owner ran from the existing repository:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot'
& 'C:\Users\User\.cache\codex-runtimes\codex-primary-runtime\dependencies\native\powershell\pwsh.exe' -NoProfile -File .\scripts\verify-kafka.ps1
```

Independently read the saved `results.json`, `host-smoke.log` and `container-smoke.log` in `.cache/kafka/aegisflow-kafka-verify-ff292985977442009ee4e3fd306c28a6/`: **Passed=true, Stage=complete**, final PASS. Start2026-10-17T16:18:40.7629035Z; finish16:21:53.7753090Z; Docker29.8.2; pinned broker4.2.2 and Boot-managed client4.2.1. Host listener was `127.0.0.1:59766`. Numeric outer exit code was not captured in the transcript/report and is not invented.

| Actual boundary | Result |
|---|---|
| Broker authenticated readiness; topic/ACL provisioning | passed; provisioning executed twice |
| HOST and INTERNAL metadata/topic checks | both passed: three topics, configured partitions/replication/retention/settings |
| Source producer → Core smoke consumer | three exact acknowledged markers and committed offsets per route; six total round trips |
| Unauthorized writes | six sibling-topic attempts plus one Core attempt rejected per route; fourteen explicit authorization denials total |
| Incorrect password | explicit SASL authentication rejection per route; two total |
| Scoped cleanup | final gate passed; verification containers stopped; Kafka volume retained; PostgreSQL not targeted |

This closes the Kafka infrastructure gate, not P0.3 overall. No business relay, database inbox, broker outage/restart, crash-after-ack or Testcontainers Kafka scenario was executed. Ordinary persistent `start-kafka.ps1` reuse remains unverified. Previous sandbox engine failure below remains a real failed attempt; this host pass does not change sandbox permissions. Continuation on 2026-10-18 IST records evidence only, without rerunning completed runtime tests or changing source/configuration. No push, deployment or volume deletion.

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

# Known Limitations

**Latest checkpoint,2026-10-21:** P0.4-A's real PostgreSQL gate passed on `2b2e885`:12 projection +4 Core migration/startup tests, zero failures/errors/skips. Earlier43 focused tests passed separately. This supersedes the pending/owner-command statements below; no rerun is needed. Next approved work is B clock-driven INV-002 with backlog freshness and restart proof, then C incidents and D full integration. P0.4 overall is incomplete. See HANDOFF/TEST_RESULTS for exact saved evidence.

## Active checkpoint — P0.4-A, 2026-10-21

P0.4 is approved but incomplete. Facts/projection/deadline/progress implementation has 43 passing focused unit/boundary/intake-regression tests; 12 new projection and 4 Core PostgreSQL tests were all skipped due to this session's Docker named-pipe AccessDeniedException. No real V3 migration, rollback, concurrency or backfill success is claimed. Host command: `scripts/verify-projection.ps1`.

The projector defaults disabled, globally serializes short Core transactions, caps batches at100 and facts per transaction at1000, and cannot automatically repair/requeue immutable BLOCKED progress. Those limits favor correctness over throughput; no SLO measured. No business detector, durable evaluation, incidents, query API or P0.4 end-to-end proof yet. Current packaged JARs predate production V3 changes and must be rebuilt for the later packaged regression. A passing P0.3 build does not verify these new inputs. P0.5/recovery/AI remain out of scope.

The following P0.3 completion record is preserved history, not a claim that P0.4 needs no owner action.

**Current status,2026-10-20:** P0.3 is verified. The final packaged scenario on `860dbf9` passed1 test,0 failures/errors/skips at19:17:57IST. All4 executable apps, automatic source/Core scheduling, real Core process restart/backlog and migration/runtime privilege checks passed. Earlier clean134, Core50 and all3 source-relay gates remain valid; no owner verification action remains.

**Remaining limits:** the broker is single-node with plaintext local SASL; no load/SLO, replicated failover or abrupt OS/power-loss guarantee was measured. Source tenant allowlists trust each permitted producer within its configured tenant set. ACCEPTED inbox rows do not implement business correlation or incidents. Quarantine is database-inspectable metadata with no operator UI/replay/retention automation; no automatic failed-outbox requeue or dedicated operational metrics/alerting. Simulator-to-simulator choreography, P0.4 detection and later recovery/AI remain future work.

## Historical checkpoints and earlier limitations

Prior pending/failure statements below are dated history and superseded where the final evidence above verifies the behavior. They do not instruct another rerun.

**Current packaged checkpoint,2026-10-20:** timestamp repair passed on the host. The packaged scenario reached its final metadata assertion after source delivery/Core restart, then errored because runtime cannot SELECT Flyway history. The test now verifies that restriction and reads history with Core migrator; production grants are unchanged. Corrected live rerun remains pending via `verify-p03-final.ps1 -PackagedOnly`. This failed run does not close P0.3.

**Final acceptance checkpoint,2026-10-20:** all50 Core cases passed across recorded runs; the clean reactor on `7d8f473` subsequently passed134 tests and packaged all4 applications. Boot's normalized JAR timestamp caused a verifier failure before the packaged case began. Repaired original-archive freshness/application-content checks pass6 regressions and all4 real artifact pairs; they are local resume checks, not full dependency provenance. Only `verify-p03-final.ps1 -PackagedOnly` remains for final host acceptance. P0.3 is incomplete. The packaged case covers a single local three-source intake and Core restart, not business correlation, load/SLO or replicated failover.

**Latest host evidence,2026-10-20:** all10 real Core pipeline cases and36 unit/boundary/port checks passed on `c96dc5c`. Four ordinary Core startup cases failed because the special test classpath exposed sibling V1 migrations; verification phases are now isolated and that4-case rerun is pending. Current Docker session remains restricted. Core intake itself is exercised through real JDBC/PostgreSQL/Kafka; packaged scheduling/final acceptance remains. Older unverified pipeline statements below are historical.

**Core source checkpoint,2026-10-20:** Core policy/validation/inbox/quarantine/offset handling and actual three-owner pipeline tests implemented;36 local unit/boundary/port checks passed. V2 migration and live Core behavior remain unverified:12 reported skips under Docker denial correspond to10 planned pipeline invocations plus4 Core DB/HTTP cases (parameterized test not expanded when disabled). Host verifier rejects skips. P0.3 incomplete; final reactor/packaged scheduling remains.

Core currently accepts only the five existing initial event types, with supported major1 minor extensions. Status ACCEPTED means durable intake only. One record per poll limits throughput; no fairness/load/SLO claim. Local topic/tenant allowlists assume a trusted source within its permitted tenant set; signed/per-tenant channels are not implemented. Quarantine stores only immutable receipt metadata/hash/reason, with no raw capture, replay/review API, security-signal workflow or retention automation. Rotate intake channel identity when recreating broker history. Harness uses actual separate Spring contexts/DBs and real broker but manual polls, client restart and injected crash-window exceptions. It does not prove separately packaged scheduling, process-kill or replicated failover. Explicit loopback binding prevents the earlier Docker random-port-on-restart problem; selection/bind races can still fail safely.

**Latest verified status, 2026-10-20:** Payment/Warehouse host gate on `c887490` passed40 unit/boundary +24 real DB/Kafka tests with zero failures/errors/skips. All three sources' relay gates are complete. Remaining: Core durable inbox/quarantine/safe offsets, full simulator-to-Core demonstration, packaged scheduler acceptance and final reactor. Negative-outcome wire delivery, actual process kill and replicated failover are not covered by these positive-outcome/manual-poll tests. Prior skipped/failed snapshots below are historical, not current owner instructions.

**Newest source checkpoint, 2026-10-19:** Payment/Warehouse relays implemented,38 new unit+2 architecture checks passed,24 new DB/Kafka tests compiled but all skipped under Docker denial. Host gate `verify-simulator-relays.ps1` remains pending. Both positive/negative catalogs are factory-tested in unit tests; live cases cover positive outcomes and are unverified. Core durable inbox/quarantine/safe-offset ingestion and the full simulator→Core test remain unwritten. All relays opt out by default; complete tenant allowlists required before enablement. No failed-intent requeue tool, packaged scheduler acceptance or full reactor claim. This supersedes older statements that sibling relay source is absent, without relabeling skipped tests as passed.

**Current 2026-10-19 status:** the real Order PostgreSQL-to-Kafka host gate PASSED on `a4a66de` at20:57:40IST:3 tests,0 failures/errors/skips. Read-only-template startup, stable restart endpoint, acknowledged publication, outage/retry recovery and ack/local-rollback duplicate delivery are now exercised. Remaining: Payment/Warehouse relays, Core inbox/quarantine, packaged scheduling, actual process-kill/replicated-failover checks and final P0.3 end-to-end acceptance. No immediate owner action. Historical unverified/failure statements below are superseded by this evidence, not deleted.

**Newest 2026-10-19 host result:** startup/mode/provisioning and enabled producer initialization succeeded on `2834b47`; same-container broker restart changed its randomly allocated port, causing3 test errors. Explicit test-only loopback binding now replaces the empty/random HostPort, with2 passing unit checks. Live repaired restart and the complete3-test gate remain unverified. Selecting then releasing a free port has a small bind-race window; collision fails safely. No production listener change or existing volume is involved.

**2026-10-19 focused host failure:** real Order Kafka startup failed on `24df774` before delivery tests (1 class-level error). Read-only copied-config permission ordering is now repaired, with2 passing source-level tests; actual fixed startup and all3 delivery scenarios await host rerun. No successful live delivery is implied by the repair. The host Docker engine itself worked.

**Status:** P0.2 host-verified; P0.3 approved/incomplete; P0.3-A persistence and Kafka infrastructure host gates passed

**Latest test-tooling checkpoint, 2026-10-19, from `f6709fd`:** real Order-to-Kafka tests are written and compile; all3 skipped under this session's Docker denial. Run the focused `scripts/verify-order-kafka.ps1` on the host; no live success is claimed yet. It exercises real enabled configuration with manual polls, not separately packaged startup/scheduling; completion failure is an injected real DB rollback after a real ack, not a process kill. Disposable single-broker restart is not replicated-cluster failover/disk-loss coverage. Payment/Warehouse relays and Core inbox remain unwritten. This supersedes older "no owner action" / "tests next to implement" statements below without changing prior44-test successes.

**Current Order-only slice, 2026-10-19:** the host gate passed27default+17real PostgreSQL tests, zero failures/errors/skips, including all nine relay ITs. This supersedes earlier sandbox failures/skips without erasing them. Real enabled-profile startup/DB→Kafka publication and broker crash/outage tests remain unverified; the passing DB crash test uses a synthetic publisher. Payment/Warehouse relays and Core inbox remain unwritten. Current `OrderCreated` only;256KiB preflight bound; complete tenant allowlist required before activation. Invalid/disallowed intent is retained FAILED with no requeue tool/alerting. Retry rate is bounded, lifetime attempts are not. No full root reactor pass is claimed. No owner rerun is needed; real Kafka relay tests are next. Older statements below are history.

**Latest Kafka checkpoint, recorded 2026-10-18:** the owner's 2026-10-17 real run passed, project `aegisflow-kafka-verify-ff292985977442009ee4e3fd306c28a6`, actual report Passed=true/Stage=complete. Broker readiness, provisioning twice and both routes' topic/metadata, delivery/offset and selected auth/ACL checks passed. Source-owned outbox relay and durable Core inbox remain unimplemented. Ordinary `start-kafka.ps1` reuse, Kafka restart durability and broker-outage/crash tests remain unverified; smoke success is not full P0.3 completion.

**Completed 2026-10-17 persistence result:** complete host persistence rehearsal passed: eight postgres-profile health gates, eight owner SQL-boundary groups, 24 sibling-login denial attempts and four unchanged sentinel/full-history snapshots across repeated bootstrap/restart. Actual report is Passed=true/Stage=complete for `aegisflow-p03-verify-93dcfedb932a45a1b5d28241d4c5098b`. This supersedes earlier partial/failed attempts without erasing them. It is not a Kafka/exactly-once proof, a production security audit or another Maven atomicity run. Preserve all verification volumes; no persistence rerun/cleanup is required now.

**Last reviewed:** 2026-10-17

This project is intentionally a production-oriented **student portfolio project**, not an enterprise-ready transaction-recovery product. This list separates designed intent from demonstrated capability and must be updated at every significant milestone.

## 1. Current P0.2 reality

- **Historical 2026-10-17 P0.3-A failure, superseded by the latest successful host gate:** the real host initialized/bootstrapped all four isolated databases, but failed before the first Java startup at the original loopback port parser. Raw port output was not retained. Internal-only Docker networking is a likely cause, consistent with upstream Moby reports, not a host-proven packet diagnosis. The repaired harness uses same-JAR verification containers on the existing owner-internal networks and in-container health; `internal: true`, database roles, V1 migrations and application code are unchanged. The image is pinned from official Docker Hub metadata. Its pull and complete real startup/SQL/retained-volume restart rehearsal subsequently passed on the host on 2026-10-17, as recorded above; this is distinct from the coding session's infrastructure-free helper/probe checks. Old failed-run volumes are retained. Do not rerun the old published-port approach or delete evidence to conceal the failure.

- **Current execution limit:** this coding session cannot access Docker's engine pipe; earlier Java loopback access was also restricted, despite successful owner-host persistence and Kafka gates. New live relay/inbox tests must run where Docker is accessible; none is passed by the infrastructure smoke. No application Java/SQL migration or application Maven dependency changed in P0.3-A; the separate smoke project uses Boot-managed Kafka clients4.2.1. P0.2's recorded38+29 successes remain historical. Verification volumes are retained. Filtered child environments and tested grants are not a security sandbox against a compromised JVM/administrator.

- Final P0.2 host evidence: full clean reactor **BUILD SUCCESS at 2026-10-16 19:45:47 IST**, **2:17**, entry `66282a9`/application source `7a90c57`; **38 default + 29 real PostgreSQL tests**, zero failures/errors/skips. All four V1 migrations and the recorded atomicity/replay/constraint/privilege assertions passed. All four packaged foundation JARs returned HTTP 200/UP and docs had zero issues. The report helper's incomplete `tests=4` summary is corrected against actual XML; the outer cross-PowerShell command emitted an initial quoting error, but independent checks all passed. No host rerun is needed for closing records. Persistent Compose bootstrap and retained-volume rehearsal passed separately on 2026-10-17; this does not relabel the historical Maven result as a new run.

- Historical host failures (resolved by later runs): a run passed 37 default tests and 21 Core/Order/Payment PostgreSQL ITs but Warehouse context startup failed because its Repository adapters were final. Making them subclassable and adding a focused proxy regression enabled the successful Warehouse run above. An earlier run started PostgreSQL and passed six Core default tests, then JDBC rejected the host JVM timezone alias. Test forks now use UTC and two configuration regressions passed. Manual postgres-profile launches still require the documented JVM UTC option; test settings do not change production JVM defaults.

- Historical image setup failure (2026-10-16, resolved by later runs): Docker/Ryuk and five Core default tests passed, then PostgreSQL IT failed at static image compatibility validation before database startup. Four IT declarations now explicitly mark the unchanged digest reference compatible with postgres; its no-Docker initialization regression also passed. The later PostgreSQL suites supersede the old pending-migrations status without erasing this failure record.

- P0.1's four application shells, Maven Wrapper, Java 21 enforcement, health endpoints, ten Core module declarations, and architectural tests are preserved. Its historical successful build ran 14 tests and its four packaged JARs answered health `UP`; those results do not validate the new persistence changes. See [test evidence](evidence/TEST_RESULTS.md) for each dated run.
- Order, Payment, and Warehouse now have source for local transactional business-state, durable idempotency, and immutable outbox publication-intent writes. Core has its own independent V1 schema-boundary migration, without future inbox/projection/incident tables. Application entry points and actual classes are explained in [the codebase guide](learning/CODEBASE_GUIDE.md).
- The current dependency baseline remains Boot 4.1.1/Modulith 2.1.1, with JPA/Hibernate in the simulators and Boot's Flyway starter/PostgreSQL module. Compatibility and behavior claims depend on the latest build/test record. Spring AI remains absent and deferred until P2.
- The first resumed offline reactor run on 2026-10-13 ended at 00:39:17 IST: Core's four architecture tests passed, and its HTTP smoke test errored with sandbox loopback `connect: Permission denied`. The reactor stopped before simulator compilation/tests. Later successful runs are recorded separately in the evidence document; this failed run remains a failure.
- Historical focused checks on 2026-10-13 passed 21 actual pure validation/fingerprint/event-fixture tests and 4 Core architecture tests. Four Core PostgreSQL ITs skipped because the Docker pipe was denied; uncached JPA dependencies and Maven Central DNS blocked simulator compilation. Compose configuration and script syntax passed. Later host execution compiled the modules and passed real PostgreSQL suites, resolving those earlier execution blockers; session Docker access must still be assessed independently from the user's PowerShell.
- Default `foundation` startup suppresses database/JPA auto-configuration and selects no persistence components. Its health `UP` proves process readiness only. `postgres` selects real owning-database connections, migrations, and Hibernate schema validation. Host test contexts and all four packaged foundation JARs started successfully. The same packaged JARs also passed the separate 2026-10-17 persistent Compose postgres-profile rehearsal inside owner-network Java 21 containers, not through host-published database ports; direct host-to-internal-network connections were not verified.
- The four databases' independent Flyway migrations, runtime grants, transaction rollback/commit, concurrency, and constraints passed the final full host Testcontainers run. The separate 2026-10-17 Compose rehearsal passed real role bootstrap, fresh migrations and retained disposable-volume restart/history checks. Neither result validates unrelated existing developer volumes, password rotation, backup/restore or general persistent deployment operations.
- No business HTTP endpoints, event publisher/consumer, UI, recovery workflow, model integration, or external deployment exists. Database-focused failure tests are written; they have not yet demonstrated the broader business failure matrix. No security scan, benchmark, or AI evaluation has run.
- POSIX wrapper execution was not tested on this Windows host; it requires `unzip` for the checksum-pinned Maven ZIP. Windows wrapper execution was verified. The packaged-startup helper currently targets Windows/PowerShell.
- Business architecture controls, state transitions, metrics, and thresholds remain specifications or future targets, not observed product results.
- Documentation may still contain open design decisions; see [OPEN_QUESTIONS.md](OPEN_QUESTIONS.md).

## 2. Scope limitations

- The first implementation covers simulated Order, Payment and Warehouse services, not real commerce, banking, ERP, logistics or regulated integrations.
- The first vertical slice should implement one narrow business invariant and one safe, non-financial recovery action. It will not prove a general recovery platform.
- No automatic refund, charge, transfer or other financial compensation is allowed in the MVP.
- A sophisticated Next.js UI, Kubernetes, multi-region operation, Redis, Temporal, pgvector and additional microservices are deferred until justified by a measured requirement.
- The final root Testcontainers build executed successfully on the host across all four deployables. Its report helper now checks all four explicit report directories and rejects missing/empty/error/failed/skipped suites. Persistent Compose bootstrap and the retained disposable-volume rehearsal also passed on 2026-10-17. Recorded local results demonstrate only their environment and scenarios, not production scalability or availability.

## 3. Distributed-systems limitations

- Kafka delivery and internal processing are designed for at-least-once behavior. Local broker configuration, topic provisioning and a transport smoke client are implemented but have not passed live host verification. The single broker/controller and replication factor one offer no high availability or spare replica. The local idempotency/outbox code and PostgreSQL uniqueness tests exist; relay/inbox and duplicate handling across later message and external-effect boundaries remain unfinished.
- AegisFlow cannot guarantee universal exactly-once external business effects. That property requires cooperating APIs with durable idempotency and authoritative status/state queries.
- Network timeouts cannot distinguish “request never arrived” from “effect committed but response was lost.” Such outcomes require VERIFYING and may end in NEEDS_REVIEW.
- Event absence is not proof that a business event did not happen. Kafka outage, publication delay, schema rejection or producer failure can leave evidence incomplete.
- Source-topic and Core read/group ACLs are now defined in `scripts/kafka/provision.sh`; runtime enforcement is pending the real smoke test. Kafka ACLs authorize a principal to a topic; they do not inspect payload `tenantId`/`sourceSystem`, and consumed records do not expose a trustworthy original-producer identity. Shared-topic tenant provenance therefore needs an additional trusted binding or stronger isolation that is not yet selected or implemented.
- Ordering is only meaningful within explicitly chosen keys/partitions. Cross-topic/global arrival order is not guaranteed.
- Contract deadlines, allowed lateness, clock skew and evidence freshness are domain configuration that remain to be selected and tested.
- A completely compromised or incorrect authoritative simulator API can make independent verification wrong; AegisFlow does not create truth beyond its sources.
- Backup/restore, Kafka replay boundaries, disaster recovery and long-running partition behavior are not yet designed or tested end to end.

## 4. Recovery limitations

- The application configurations provide each deployable only its owning database's runtime/migration connection. AegisFlow Core has no simulator-database credentials or direct persistence path. Bootstrap owners and IT connections inspect databases for administration/assertions. Real PostgreSQL privilege assertions passed in the separate host suites; they establish the tested grants, not authenticated cross-service APIs or hostile multi-tenant isolation. Future state reads/recovery writes must use authenticated owning-service APIs.
- Integrations that lack stable idempotency keys, optimistic preconditions and authoritative queries cannot safely support automatic recovery.
- RESOLVED means the configured invariant was confirmed by an authoritative read; it does not prove every downstream consequence or business obligation is correct.
- Human approval reduces but does not eliminate error. An authorized human can approve a poor plan; approval must therefore remain bound to evidence, policy, parameters and resource version.
- Racing-worker, crash-after-success and stale-approval behavior is specified but unproven until the failure matrix passes.
- Some ambiguity is intentionally not automated. NEEDS_REVIEW is a safety result, not necessarily a system defect.

## 5. Security and tenancy limitations

- Keycloak, RBAC, authenticated tenant ingress, TLS and business audit controls remain unimplemented. Kafka configuration now defines distinct broker/admin/source/Core SASL principals and default-deny source-topic/Core-group ACLs; their actual authentication/authorization behavior awaits host verification. SASL/PLAIN over SASL_PLAINTEXT is unencrypted and restricted to synthetic local development. Verification tooling redacts generated secrets, but this is not a complete product redaction system. The only application HTTP route is loopback-bound health; persistence methods are trusted Java-call boundaries, not authenticated business APIs. Do not expose the foundation publicly.
- Compose requires twelve private owner/migrator/runtime passwords across four databases. `scripts/db/bootstrap-roles.sql` and per-service migrations define restricted runtime grants, separate migration ownership, and immutable outbox intent; ephemeral PostgreSQL privilege assertions passed. Persistent Compose bootstrap and restricted-role assertions passed on 2026-10-17 using fresh synthetic credentials and disposable retained volumes, not the owner's private `.env` or unrelated developer data. Applications still receive their own migration credentials for startup; separating those into a deployment migration job is future operational work.
- Tenant-keyed entities, lookups, unique keys, and foreign keys are implemented in source. They prevent some accidental cross-tenant persistence links; they do not authenticate caller-supplied tenant values. Application-layer tenant authorization remains future work. PostgreSQL row-level security is an optional defense-in-depth decision, not a current capability; this local deployment does not establish strong hostile multi-tenancy.
- Malformed records may lack a validated tenant or event ID. The broker-coordinate/digest-based technical quarantine, protected payload handling, replay authorization, and distinction between security signals and business incidents remain unimplemented.
- Simulator request/event canonicalization now uses the explicit `aegisflow-json-v1` profile with module-local unit vectors. These implementations need ongoing cross-service compatibility checks; a changed profile requires deliberate migration/replay behavior. Future approval digests, adversarial input coverage, and external integration normalization remain unimplemented.
- The project does not protect against a fully compromised host, identity-provider administrator, database administrator, authoritative service, build pipeline or dependency.
- Local developer secrets and sample credentials will not be appropriate for shared or public deployment.
- Audit logs are not inherently immutable against privileged infrastructure administrators. Tamper-evident or external archival is out of MVP scope unless later justified.
- Data classification, retention, deletion, backup encryption, privacy review and regulatory obligations are not complete. Use synthetic data only.
- Dependency/image vulnerability posture changes over time; a passing scan is dated evidence, not a permanent guarantee.

## 6. AI limitations

- No model or provider is selected. Spring AI integration is roadmap work, not a current feature.
- AI output can be plausible, biased, inconsistent, prompt-injected or factually wrong even when it cites evidence.
- Prompt instructions cannot secure a model. Safety depends on minimal privileges, curated evidence, strict schemas, deterministic policy/authorization, human approval where required, and independent verification.
- Hosted-model use can expose minimized input to a third party and incur nondeterministic latency, availability, retention and cost. Provider terms require explicit review.
- The proposed accuracy, abstention, latency, token and cost thresholds are unmeasured future gates.
- The synthetic evaluation corpus will have coverage and labeling limits. Strong results on it will not establish performance on unseen enterprises or adversaries.
- The deterministic investigator is the required baseline and fallback. If AI adds no measured value or fails safety gates, explain-only mode or no AI is an acceptable outcome.

## 7. Evaluation and performance limitations

- The [failure matrix](evaluation/FAILURE_MATRIX.md) is a test plan only; every result currently remains “not run.”
- Proposed detection/recovery latency applies to a future declared local environment and excludes separately reported contract waiting, injected outage duration and human approval time.
- Small synthetic datasets can produce unstable recall and false-positive estimates. Future reports must publish counts, denominators and confidence limitations.
- Live model outputs may change across dates even with a stable model name. Evaluations must record provider/model, prompt, date and repetitions.
- Zero duplicate effects and zero unauthorized writes are release gates for the tested scenarios, not proof across all possible failures.
- No load, soak, chaos, penetration, accessibility or usability testing exists yet.

## 8. Operational and learning limitations

- There is no operations team, support rotation, incident-response process, SLO/error budget, capacity plan, upgrade plan or production runbook.
- Observability has not been implemented. Planned Prometheus/Grafana/OpenTelemetry components do not imply useful dashboards or trace completeness.
- Documentation and learning guides can drift from code. The continuity protocol requires updates at each milestone, but later contributors must enforce it.
- Comments and JavaDoc will explain non-obvious invariants and concurrency behavior; they are not a substitute for executable tests and current architecture documentation.

## 9. Claims the project must not make

Until supported by dated reproducible evidence, do not claim:

- “production ready,” “enterprise safe,” “exactly once,” “zero downtime,” or “secure multi-tenant”;
- guaranteed root-cause identification or safe autonomous recovery;
- measured AI accuracy, cost savings, latency or reliability;
- compliance with PCI DSS, GDPR, SOC 2 or another regulatory/control framework;
- support for real financial recovery.

Future milestones should convert a limitation to a demonstrated capability only by linking the implementing paths, exact test/benchmark command, factual result, environment and remaining boundary.

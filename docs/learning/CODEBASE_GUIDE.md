# Codebase guide

## P0.4-B: time-driven decisions, not incidents

B implementation has18 focused tests passed;21 PostgreSQL checks skipped due to this session's Docker denial. Run scripts/verify-detection.ps1 on the host. A remains verified; no completed A suite rerun. This section supersedes earlier pending A/current-next-B statements.

Actual new paths:

```text
control-plane/src/main/java/com/aegisflow/core/
  correlation/DetectionProjections.java
  correlation/internal/DetectionProjectionConfiguration.java
  detection/internal/InventoryInvariant.java
  detection/internal/DeadlineDetector.java
  detection/internal/DetectionConfiguration.java
control-plane/src/main/resources/db/migration/V4__invariant_evaluation.sql
control-plane/src/test/java/com/aegisflow/core/
  detection/internal/InventoryInvariantTests.java
  detection/internal/DeadlineDetectionIT.java
  correlation/internal/DetectionFixture.java
scripts/verify-detection.ps1
```

Flow: persisted A projection/deadline → scheduled DetectionTick → DeadlineDetector's REQUIRES_NEW/REPEATABLE_READ transaction → shared advisory gate → bounded correlation snapshot API + unsettled inbox check → pure InventoryInvariant → immutable evaluation and durable sweep cursor commit together. No Kafka call, simulator write or incident creation happens here. The callback wakes work; PostgreSQL owns work and completion. Empty tail wraps the cursor so unchanged deadlines become due and earlier new keys are not lost.

CORE_DETECTION_ENABLED defaults false; CORE_DETECTION_BATCH_SIZE defaults25, maximum100. Callback interval is1 second. The injected detectionClock supplies decision time; A's pinned45-second duration plus15-second lateness is read from each snapshot, not reloaded as a new rule. Public APIs preserve module boundaries; JDBC/TransactionTemplate makes the multi-table boundary explicit without self-invocation proxy surprises.

SATISFIED means a matching observed on-time reservation or supported rejection, not current remote authority. UNKNOWN means pending enforcement or insufficient/unfinished evidence. At enforcement, missing/mismatched/contradictory or late-occurring outcomes have distinct VIOLATED reasons. An on-time event delivered later can yield a new SATISFIED evaluation, but the earlier violation remains. Future C must not equate this with incident resolution.

Debug exercise after host SQL pass: run DeadlineDetectionIT.pendingWarehouseBacklogIsUnknownThenSatisfied with a breakpoint before fixture.project(). Intake already contains the Warehouse event but progress does not. Advance MutableClock to60 seconds and inspect invariant_evaluation: UNKNOWN/PROJECTION_UNSETTLED, zero violations. Project the backlog and advance to61: a new projection version yields SATISFIED while the original UNKNOWN remains. In failureAfterEvaluationInsertRollsBackCursorAndResult, the test-owned database trigger fails the cursor update; both evaluation and cursor progress roll back. Retry with a newly constructed worker succeeds. No actual60-second sleep.

### Q61. Why can a scheduled method alone lose deadline work?

An in-memory timer disappears on restart. Here projections retain deadlines and detection_sweep retains scan position; restarting a worker resumes and wraps the scan without needing a new business event.

### Q62. Why use REPEATABLE_READ plus the shared gate?

The gate serializes projection/detection effects; the snapshot keeps projection and intake-freshness observations consistent while intake continues. Serialization conflicts roll back and retry. Events committed after that snapshot are later evidence, not already-known facts.

### Q63. How are repeated time-driven evaluations idempotent?

A unique database key includes tenant, transaction, rule/version, projection version, deadline phase and unsettled flag. Repeated polling of the same state cannot append another logical decision; enforcement or new projected evidence permits a new one.

### Q64. Does the15-second allowance extend the inventory operation deadline?

No. dueAt is occurrence plus inventory duration. enforceAfter adds transport grace once. A business outcome occurring after dueAt is late; an on-time event delivered later can change observed evidence without rewriting the original evaluation.

### Q65. Why keep a violated evaluation after later valid evidence arrives?

It records what was known at its evaluatedAt and the checked fact snapshot. Replacing it would hide detection history. A later decision appends new evidence; future incident resolution still needs the separately authorized authoritative verification workflow.

**Latest checkpoint,2026-10-21:** P0.4-A's real PostgreSQL gate passed on `2b2e885`:12 projection +4 Core migration/startup tests, zero failures/errors/skips. Earlier43 focused tests passed separately. This supersedes the pending/owner-command statements below; no rerun is needed. Next approved work is B clock-driven INV-002 with backlog freshness and restart proof, then C incidents and D full integration. P0.4 overall is incomplete. See HANDOFF/TEST_RESULTS for exact saved evidence.

**Current status,2026-10-21:** P0.3 complete; approved P0.4-A implemented with43 focused tests passed but16 PostgreSQL checks skipped. Run the host projection gate before B/C. Older status paragraphs are history.

## P0.4-A: from accepted message to durable projection

Actual paths, relative to repository root:

```text
control-plane/src/main/
  java/com/aegisflow/core/
    eventing/ProjectionInbox.java
    eventing/internal/JdbcProjectionInbox.java
    eventing/internal/ProjectionInboxConfiguration.java
    correlation/internal/ProjectionModel.java
    correlation/internal/CorrelationProjector.java
    correlation/internal/ProjectionConfiguration.java
  resources/db/migration/V3__durable_projection.sql
control-plane/src/test/java/com/aegisflow/core/correlation/internal/
  ProjectionModelTests.java
  DurableProjectionIT.java
scripts/verify-projection.ps1
```

An accepted message is durable transport evidence; a fact extracts its business meaning without claiming fresh remote authority. `ProjectionModel` preserves aggregate version, order identity, quantities, hash and source/intake times. A projection summarizes the fact set by tenant/businessTransactionId, not tracing correlationId. Lower versions cannot replace higher ones; unsupported terminal changes and identity conflicts stay visible.

Flow: existing `InboxStore` commits ACCEPTED and `KafkaInboxConsumer` acknowledges → `ProjectionConfiguration` schedules bounded polling every5 seconds when enabled → `CorrelationProjector` starts a REQUIRES_NEW transaction → `ProjectionInbox` adapter takes the transaction-scoped advisory gate and selects an unmarked inbox row → fact, projection/deadline and APPLIED progress commit together. Intake remains immutable. Failure before commit leaves all derived effects absent and the inbox eligible again. Late-visible old rows are found through an anti-join, not a timestamp cursor. Permanent shape/constraint/budget failures roll back and get a separate BLOCKED marker; they still count as unsettled evidence.

This slice uses Spring JDBC and explicit `TransactionTemplate`, not JPA entities or `@Transactional` self-invocation. The database manager binds the same data source/connection to the thread; the eventing port refuses calls outside a transaction. Global advisory coordination is easy to reason about but serializes projector throughput. UUID facts and database unique/composite foreign keys preserve idempotency and tenant provenance. Runtime can append facts/progress but not change them; only projection result columns are mutable.

Defaults: `CORE_PROJECTION_ENABLED=false`, `CORE_PROJECTION_CONFIG_VERSION=demo-v1`, `CORE_INVENTORY_DURATION_SECONDS=45`, `CORE_EVENT_LATENESS_SECONDS=15`, `CORE_PROJECTION_BATCH_SIZE=25` (maximum100). A projection freezes config when first created, even if Payment arrives before Order. `due_at=order occurredAt+duration`; `enforce_after=due_at+lateness`. Injected `Clock` controls processing timestamps. These dates schedule future B evaluation; INVENTORY_OBSERVED is not SATISFIED and no incident exists in A.

Debugging exercise (after host gate passes): in the disposable `DurableProjectionIT` rollback case, break immediately after `inbox.applied` in the forwarding test port, then allow its injected exception. Query counts using that test's inspected connection: inbox1, facts0, projections0, progress0. Retry produces one of each derived row and keeps inbox status ACCEPTED. For a visible pending Warehouse message, `hasUnsettled` must be true until it is projected. A future detector must honor this flag under the same gate; simply seeing an expired deadline is insufficient. No simulator database or existing Compose volume is needed for this exercise.

### Q56. Why not mark inbox rows PROCESSED?

P0.3 promises immutable accepted evidence and already acknowledges Kafka. Separate durable progress records completion without changing that contract; projection can catch up even when Kafka will not replay.

### Q57. Why can a timestamp cursor lose an inbox message?

A transaction may obtain an early receive time but commit after a later row was processed. Advancing past the time skips that row permanently. The anti-join keeps every unmarked committed identity eligible.

### Q58. What commits atomically in this projector?

One observed fact, its transaction projection/deadline and APPLIED progress share a Core database transaction. A rollback removes them together; the accepted input persists. This is database idempotency, not universal exactly-once transport.

### Q59. Why pin configuration and inject Clock?

Pinned duration/lateness avoids changing an existing transaction's deadline after a deployment. Clock makes processing and future deadline decisions deterministic in tests; it does not change source occurrence or intake timestamps.

### Q60. Why must future detection consider pending projection work?

A valid Warehouse outcome may already be accepted but not projected. Declaring absence from the summary alone would be false. Pending/BLOCKED work suppresses definitive absence evaluation; later uncommitted or undelivered evidence cannot be known in advance.

## Final executable-JAR scheduling check (verified2026-10-20)

The final host run on `860dbf9` passed1/1,0 failures/errors/skips at19:17:57IST. `PackagedPipelineScenario.java` uses its runtime connection to assert absence of history SELECT, and a separate Core-migrator connection to inspect migration history. Business inbox/outbox observations use runtime identities. The earlier failed attempt and its correction remain in TEST_RESULTS.

Debugging lesson: `BadSqlGrammarException` is Spring's exception category here; its underlying PostgreSQL message was `permission denied for table flyway_schema_history`. Inspect that cause and the connection identity before changing SQL or grants. Runtime applications need their domain tables, while migration metadata belongs to the migration role. The test must respect the same separation.

New actual paths are `control-plane/src/test/java/com/aegisflow/core/eventing/internal/{PackagedApplicationProcess,PackagedProcessTests,PackagedPipelineScenario}.java` and `scripts/verify-p03-final.ps1`. Existing Core50 and source-relay gates are passed. The remaining check executes packaged processes instead of manually invoking worker polls.

PackagedPipelineScenario starts fresh PostgreSQL instances/broker, uses existing real service contexts to seed3 initial business/outbox events plus a held Order intent, then closes those contexts. It asserts all source intents remain PENDING and Core inbox empty. Four separate `java -jar` processes use their real postgres profile and schedulers. Each receives essential OS variables plus only its own generated DB/Kafka configuration; no inherited Spring/JVM/private owner configuration or .env is loaded. Source schedulers must mark3 intents PUBLISHED and Core must durably accept them. Core is then stopped; owner-runtime retry metadata releases the held intent, its source scheduler publishes while Core is offline, and a new Core process with the same DB/group must accept that fourth event.

Each process requests HTTP port0, reads its actual bound port from its own fresh log and proves health200/UP. Strict health parsing accepts optional Actuator groups, preserving the earlier readiness-parser lesson. Kafka retains its verified explicit loopback endpoint; PostgreSQL uses mapped URLs. Cleanup holds only Process objects created by this test and never enumerates foreign PIDs or deletes existing volumes. Child logs survive in `.cache/packaged-pipeline/<id>`.

The final script preserves prior reports under `.cache/p03-final/<id>/prior` before `clean verify`. This normal reactor runs default unit/foundation HTTP checks and builds current executable JARs; expensive completed relay/Core pipeline ITs are not selected. A separate pipeline-profile Maven invocation runs only PackagedPipelineScenario. `-PackagedOnly` resumes that final phase after a test/script-only repair using existing verified artifacts. Actual counts come from fresh XML; skips cannot pass.

The host clean build passed134 tests on2026-10-20, but the original freshness guard stopped before the live packaged case: Boot sets the executable archive's filesystem timestamp to1980-02-01 for reproducibility. `scripts/lib/packaged-artifact.ps1` now checks `.jar.original` against main sources/resources/POMs, then compares SHA256 application/resource/Maven metadata bytes with their repackaged entries. Boot rewrites the manifest and adds loader/dependency entries, so those are not equated to the plain archive. This is a local resume check, not full dependency provenance. All4 actual archive pairs and6 regression groups pass; the subsequent packaged-only acceptance also passed.

Timestamp debugging exercise: inspect both archive mtimes, then run `scripts/test-packaged-artifact.ps1`. Expect an old normalized executable date to pass with a fresh matching original; changed production inputs, same-length changed bytes, missing/extra resources or a missing archive must fail. Do not touch timestamps or rebuild solely to make the old date appear current.

Debug exercise: inspect printed child log paths if the case fails. Expected source state before process launch is PENDING, first Core inbox count3, held intent still PENDING, then PUBLISHED while Core is stopped, then Core count4 after restart. If an application exited, inspect its own log before changing any port. A health response containing groups is valid; a root DOWN/nested UP response is not.

Four pure process/env/port/health guards, the final134-test host reactor and the live packaged scenario pass across recorded runs. The timestamp and role-inspection repairs changed scripts/tests only.

### Q51. Why verify executable JARs after a Spring-context integration test?

An executable artifact has its own dependency/resource classpath and normal application lifecycle. It can expose packaging, profile, scheduler or migration-discovery problems that manual worker polls inside a shared test JVM do not exercise.

### Q52. Why use HTTP port0 but an explicit Kafka advertised host port?

The HTTP caller discovers the bound endpoint from its own process's startup log. Kafka clients also learn broker endpoints from metadata, so the advertised address must match the broker's bound endpoint and stay valid across restart.

### Q53. How does the test prove schedulers published the seed events?

It requires PENDING rows before JAR launch, closes the seed contexts, and never calls relay.poll afterward. Publication and Core acceptance can then occur only through the running applications' scheduled workers.

### Q54. Why hold an outbox row instead of delaying the business transaction?

The business change and intent are already committed atomically. Updating only test-owned retry metadata controls publication timing without altering authoritative state, payload or event ID. This creates a durable backlog while Core is stopped.

### Q55. How does process cleanup avoid interfering with another project?

The test retains only the Process handles it created, stops those boundedly and preserves their logs. It does not look up broad Java PIDs, reclaim occupied ports or stop unrelated Docker resources.


## Core ordinary startup verified (2026-10-20)

The host `-CoreStartupOnly` run on `42b247d` passed4/4 checks, confirming that separating the ordinary Core JVM/classpath from pipeline-tests resolves sibling V1 discovery. Together with the prior36+10 passes, durable intake, failure/replay and normal Core startup now have real evidence. The remaining acceptance work is the final normal reactor build and separately packaged applications running their schedulers; the existing pipeline uses manual polls.


## Pipeline verification classpath isolation (2026-10-20)

The host run on `c96dc5c` passed all36 Core unit/boundary/port checks and10 real pipeline cases. Actual three-source delivery, rollback/replay, poison/isolation and concurrent duplicate handling now have live evidence. The remaining4 ordinary Core startup cases failed because pipeline-tests places simulator target/classes on the test runtime classpath: all services legitimately have their own V1 migrations, and a general `classpath:db/migration` scan saw several version1 files.

`scripts/lib/core-inbox-verification.ps1` now produces separate compile, pipeline and normal Core plans. `scripts/verify-core-inbox.ps1` invokes each Maven process and immediately checks that phase's fresh zero-skip reports. PipelineApplications retains explicit per-owner migration paths. Ordinary PostgresInfrastructureIT now runs without pipeline-tests, using Core's normal JDBC dependencies/resources. `scripts/test-core-inbox-verification.ps1` checks these selections without Docker (5 passed guard groups).

Debug exercise: compare the saved Core startup report's offending Warehouse/Order V1 paths with `control-plane/target/classes/db/migration`, which contains Core V1/V2 only. The extra migrations were classpath entries, not files copied into Core or histories needing deletion. Resume with `verify-core-inbox.ps1 -CoreStartupOnly`; expected4 fresh passes. Do not repeat the successful pipeline or rename historical migrations. The prior implementation checkpoint below is preserved history; its local-only/unverified statements are superseded by this real pipeline evidence.


## Core durable intake implementation (2026-10-20; live gate pending)

Actual paths:

```text
control-plane/src/main/java/com/aegisflow/core/eventing/internal/
  IntakePolicy.java        configured source-to-tenant authority
  EventValidator.java      bounded parsing, initial schemas, canonical identity
  InboxStore.java          independent Core transaction and unique-key arbitration
  KafkaInboxConsumer.java  serial polling, durable processing, then offset commit
  InboxConfiguration.java  opt-in postgres wiring and Core-only Kafka identity
control-plane/src/main/resources/
  db/migration/V2__durable_event_inbox.sql
  contracts/initial-events-v1.schema.json
control-plane/src/test/java/com/aegisflow/core/eventing/internal/
  EventValidatorTests.java / KafkaInboxConsumerTests.java / PipelinePortTests.java
  CoreKafkaFixture.java / PipelineApplications.java / CorePipelineScenario.java
scripts/verify-core-inbox.ps1
```

The existing simulators use Spring Data JPA/Hibernate for their authoritative aggregates. Core intake uses JdbcTemplate and TransactionTemplate because the operation is an append with PostgreSQL `ON CONFLICT`, rather than a mutable ORM aggregate. InboxStore opens a five-second REQUIRES_NEW READ COMMITTED transaction. The primary key `(tenant_id,source_system,event_id)` decides concurrent acceptance; a loser waits for the winning insert, then compares canonical content in a subsequent statement. An equal duplicate returns without another insert. A changed duplicate writes quarantine in that transaction and preserves the original row. The runtime role has SELECT/INSERT and cannot update/delete either evidence table. V1 remains unchanged; Flyway alone applies V2 with the migrator role.

Flow: actual owner service call → its business/replay/outbox transaction → its existing relay → authenticated own Kafka topic → Core topic/source plus operator tenant binding → strict schema/identity/key validation → committed Core inbox or technical quarantine → explicit Kafka offset commit. A failure before Core commit rolls back and rewinds the fetched position. A failure after DB commit but before confirmed offset commit permits redelivery; database idempotency handles it. Ingestion records `ACCEPTED`; facts, projections, deadlines and incidents remain P0.4.

The consumer uses raw bytes so malformed UTF-8/JSON can be quarantined without a deserializer poison loop. Limits are256KiB,24 nesting levels,4096-character parsed strings,32-character JSON numbers,100 items and bounded identifiers/quantities. Duplicate JSON keys and trailing documents are rejected. Five existing initial event types are supported, including PaymentDeclined's four existing reason codes. Major1 minor extensions retain unknown optional fields; later event types/major versions quarantine. The JSON Schema is the structural contract; Java additionally checks currency precision, unique SKUs, trust, key binding, parser budgets and positive integral version tokens.

Canonical profile `aegisflow-inbox-json-v1` sorts object keys, retains array order/extensions, normalizes supported amount strings and UTC timestamps, and represents numbers exactly instead of rounding through double. Raw SHA-256 is stored separately. Quarantine stores only receipt coordinates, digest, byte count and fixed reason; raw invalid content and claimed tenant fields are not stored. Reviewing/replaying those bytes needs a separately approved protected capture mechanism. There is no automatic quarantine replay.

Enable only in postgres with `CORE_INBOX_ENABLED=true`, `CORE_KAFKA_BOOTSTRAP_SERVERS`, `KAFKA_CORE_PASSWORD`, explicit `CORE_INBOX_ORDER_TENANTS`, `CORE_INBOX_PAYMENT_TENANTS`, `CORE_INBOX_WAREHOUSE_TENANTS`, and optional `CORE_INBOX_GROUP` / `CORE_INBOX_CHANNEL`. Default group is `aegisflow-core-inbox-v1`; group names must stay in the broker's `aegisflow-core-` ACL prefix. Empty bindings deny that source; all empty fails startup. Headers do not authorize a tenant. A source trusted for several tenants is still trusted to emit for that set; this local model does not attest each aggregate against a compromised authorized producer.

Polling processes one record at a time and commits only its exact partition/offset+1. This deliberately trades throughput for a small, reviewable failure boundary. Polling and close are serialized; transient failures are rate-bounded by the one-second scheduler and retain the offset. It does not infer business state from event absence.

The pipeline profile adds simulator target/classes only to the integration-test runtime and adds JPA as a test dependency. PipelineApplications uses reflection to start separate owner Spring contexts with separate PostgreSQL containers, own migration locations and explicit runtime/migration credentials; production artifacts keep their existing dependencies. Contexts instantiate the real enabled configurations but mock scheduling for deterministic manual polls. CorePipelineScenario is explicitly selected by the verifier; normal Surefire/Failsafe naming does not select this expensive scenario. Its target is10 live invocations, including all three positive source flows and both negative outcomes.

Port lessons carried forward: CoreKafkaFixture uses one selected IPv4 loopback port as both explicit Docker binding and advertised host port, checks the initial binding, and preserves the same endpoint on owned-broker restart. Both datasource and Flyway receive each container's mapped JDBC URL. Pipeline contexts use web NONE with port0 as a fallback; the separate existing HTTP integration test uses RANDOM_PORT. No fixed development database/application port is required. Port selection releases its socket before Docker binds; a rare competing bind still fails safely. Earlier readiness failures also included a valid Actuator groups field rejected by an old probe, fixed separately; increasing timeouts would not have fixed that parser.

Debug exercise: run the host verifier and inspect Core Failsafe reports. The rollback case installs a test-owned AFTER INSERT trigger that raises an error for one UUID. Expected: zero inbox rows for that UUID, no acknowledged offset beyond it, then one accepted row after removing the trigger and restarting the consumer. The commit-before-ack case expects an already durable row, an unacknowledged offset, and DUPLICATE after restart. Preserve the first failure and do not alter development ports or delete volumes.

Actual local results:36 final checks passed (23 validator,5 consumer,4 module boundaries,4 port/URL guards). PostgreSQL-unsafe NUL/surrogate and extreme-number inputs are rejected before insertion; exact optional numeric changes preserve different hashes. Pipeline/updated migration/startup cases compiled but were skipped under denied Docker access; no live success claimed. See TEST_RESULTS and HANDOFF for exact commands.

### Q46. What authorizes an event's tenant?

Core combines a fixed source-owned topic mapping with an operator-controlled tenant set for that source. Payload/header claims must match those bindings. The existing broker ACLs restrict source principals to their topics; they do not inspect tenants or provide a signed original-producer identity.

### Q47. Why commit PostgreSQL before Kafka offsets?

Kafka and PostgreSQL do not share a local transaction. Committing an offset first could permanently lose an event if the DB then fails. DB-first can repeat delivery after a crash; a stable identity and database uniqueness make that repetition safe for intake.

### Q48. How does concurrent duplicate acceptance work without a cache?

The unique composite primary key arbitrates inserts in PostgreSQL. ON CONFLICT waits for the winner; under READ COMMITTED the next statement observes that committed canonical hash. Equal content is duplicate; changed content is technical quarantine.

### Q49. Why store a canonical digest and a raw digest?

Equivalent key order, whitespace, supported decimal formatting and UTC representations should identify the same event. Canonical hashing normalizes those representations. The separate raw digest identifies the bytes actually received; exact number handling prevents accidental collision through floating-point rounding.

### Q50. Why does poison handling use a separate table?

Malformed input may have no trustworthy tenant or event ID. A broker-coordinate/digest receipt can be persisted without granting a payload claim tenant authority. Only after that transaction commits does Core acknowledge it; no business fact or incident is invented.


## Verified source-relay boundary (2026-10-20)

The host gate on `c887490` passed40 Payment/Warehouse unit/boundary and24 real PostgreSQL/Kafka tests with zero failures/errors/skips. The four URL overrides described below are now live-verified. Each actual simulator service commits business state and its outbox; the source-owned relay claims, sends using its own Kafka principal, waits for acknowledgement, then records fenced completion. Kafka fault/rollback tests show why the same event can be delivered more than once.

The consumer in these tests is only a Core-identity observer. There is no durable Core inbox yet: that is the exact next implementation boundary, including database idempotency and offsets only after durable acceptance/quarantine. Do not rerun these completed source tests merely to resume. Older local skipped-test statements below describe their original checkpoint, not the current host result.


## Relay test startup debugging (2026-10-20 repair)

In Payment `persistence/internal/{PaymentOutboxRelayIT,PaymentKafkaDeliveryIT}` and Warehouse `reservation/{WarehouseOutboxRelayIT,WarehouseKafkaDeliveryIT}` under each simulator's `src/test/java/com/aegisflow/simulator/<owner>/`, the dynamic properties now bind **both** `spring.datasource.url` and `spring.flyway.url` to `POSTGRES::getJdbcUrl`. The production postgres profile has a separate migration URL; replacing the runtime datasource URL does not replace that independent value. Both connections target one disposable database but keep different runtime/migrator roles.

Debug exercise: read the first `Caused by` in the preserved Payment Failsafe report from2026-10-19. Expected: Flyway SQL state08001 at fixed port5435 before relay assertions. Compare the existing Payment/Warehouse persistence-test URL registrations. On the corrected host rerun, Flyway must use the container's mapped URL; all24 live tests must execute without skips. Successful compilation alone does not prove this. Do not start a development database on5435 to hide the faulty test isolation.


## Payment/Warehouse source relays (2026-10-19)

Implemented, with38 new unit tests and2 boundary tests passed;24 new PostgreSQL/Kafka tests compiled but skipped here. This does not complete P0.3. Run `scripts/verify-simulator-relays.ps1` on the Docker-capable host; it never repeats the completed Order suite or targets existing Compose storage.

Actual paths under each `simulators/<owner>-service/src/main/java/com/aegisflow/simulator/`:

```text
payment/persistence/internal/
  PaymentOutboxStore.java, PaymentOutboxRelay.java, PaymentRelayConfiguration.java
  KafkaOutboxPublisher.java, OutboxClaim.java, RelaySupport.java
warehouse/reservation/
  WarehouseOutboxStore.java, WarehouseOutboxRelay.java, WarehouseRelayConfiguration.java
  KafkaOutboxPublisher.java, OutboxClaim.java, RelaySupport.java
```

Matching test packages contain `<Owner>OutboxRelayTests`, `KafkaOutboxPublisherTests`, `<Owner>OutboxRelayIT`, `<Owner>KafkaDeliveryIT`, `<Owner>KafkaFixture`. Application-postgres YAML and POMs supply opt-in settings and Boot-managed Kafka clients, with no schema change. Worker code remains owner-local rather than introducing a deployable-to-deployable dependency. `RelaySupport` delegates sorting to each existing canonicalizer; raw SHA256 is applied to its canonical output, never raw JSONB formatting.

Flow: Payment `PaymentCreation.recordInitialOutcome` / Warehouse `ReservationApplicationService.createReservation` commits aggregate/replay/outbox through existing JPA/JDBC local transaction → relay short JDBC claim commits → canonical identity/source/type/tenant checks → own-topic authenticated send outside DB transaction → broker ack → tenant/owner/fence/live-lease completion transaction. Outbox failures retain intent and back off1..60seconds. Supported catalog: PaymentAuthorized/PaymentDeclined and InventoryReserved/InventoryRejected. No later financial/stock transitions added.

Set owner-specific `PAYMENT_OUTBOX_RELAY_ENABLED` or `WAREHOUSE_OUTBOX_RELAY_ENABLED` only with the matching `<OWNER>_KAFKA_BOOTSTRAP_SERVERS`, `KAFKA_<OWNER>_PASSWORD`, and complete `<OWNER>_OUTBOX_ALLOWED_TENANTS`. Defaults remain off; max-per-poll defaults10, range1..100. Excluded tenants become retained FAILED intents. Never load another service's DB/Kafka credentials into the application. Tests use disposable generated credentials, not private env files.

Debug exercise: compare each owner's `acceptsBothExistingOwnerOutcomesWithOriginalCanonicalDigest` fixture to its event factory, then inspect `unconfirmedSendRetainsIntentAndSchedulesBoundedRetry`. Expected: the original event/key survives; no PUBLISHED update on timeout; a fixed error category and retry time are persisted. For live debugging use only the owned test resources in the host verifier. Its Core-identity observer is NOT the still-unwritten durable Core inbox.

### Q41. Why reuse the algorithm without importing Order's classes?

Each simulator remains independently deployable. Importing another application artifact would couple release/runtime boundaries; small owner-local worker copies reuse the verified mechanics while keeping credentials, factories and lifecycle separate.

### Q42. Why reuse two different canonicalizer implementations?

The existing owners already persist digests from their own factories. Changing that format would risk rejecting historical intents. RelaySupport delegates to the owner implementation; factory-based tests check both allowed outcome types against the unchanged stored digest contract.

### Q43. Why do no new database migrations appear in this relay checkpoint?

V1 already has status, attempts, retry time, lease owner/expiry and fencing token, with runtime column-scoped UPDATE privileges. The relay needs only those fields; immutable business intent and least-privilege grants remain unchanged.

### Q44. Why are24 skipped integration tests not evidence of correct delivery?

Compilation and discovery prove source wiring, not PostgreSQL locking or broker behavior. The host script requires all expected reports fresh and zero skips; only actual execution can close the live gate.

### Q45. What remains between successful source delivery and a reliable whole system?

Core needs a committed, unique inbox receipt for each tenant/source/event identity, canonical duplicate comparison, fail-closed quarantine and offsets committed only after that local DB transaction. A read-only test consumer cannot substitute for these crash/replay guarantees.

## Verified Order outbox-to-Kafka gate (2026-10-19)

**Current evidence:** the host ran `verify-order-kafka.ps1` on `a4a66de` successfully at20:57:40IST:3 passed,0 failures/errors/skips, independently confirmed in Failsafe XML. The real flow below now works under manual polling: commit state/intent → claim → Order-authenticated broker acknowledgement → DB completion → Core-identity observer. Outage/restart and ack-before-completion-rollback redelivery also pass. Earlier repair notes below explain the failures leading here; their pending-rerun statements are superseded. No rerun needed now. Extend Payment/Warehouse next, then implement actual Core inbox; a test observer is not that inbox.

**Next host finding:** private-copy repair succeeded, but same-container restart changed Docker's randomly assigned host port. `OrderKafkaFixture.configure` now selects an available loopback port and puts the explicit value in Docker's binding and KAFKA_HOST_PORT before startup. Clients retain their endpoint while the same broker/storage restarts. `OrderKafkaFixtureTests.java` adds2 passing binding/validation unit checks; real restart remains unverified. This test-local fixed binding is an exception to normal random mapping because restart stability is under test ([Testcontainers networking](https://java.testcontainers.org/features/networking/)). The probe socket must close before Docker binds, so a rare port collision fails startup safely. No foreign process is stopped. The old handoff-file design described in historical evidence is removed. Outage runs last to isolate cascading errors, with primary runtime/assertion failures preserved if restart also fails.

**Host failure and repair:** the first actual run failed before all3 delivery methods. `startup.sh` copied the fixture's0444 template and attempted to append JAAS settings before chmod; its private destination was not writable. It now sets that copy to0600 immediately after copying and explicitly stops on copy/chmod/append failure. The original template stays0444; the fixture verifies both modes at startup. `KafkaStartupScriptTests.java` in the test package below adds2 passing source-level guards, not Linux permission tests. Real repaired startup still needs the focused host verifier. Debug from the first `Permission denied`, not the later missing-JAAS message; do not change credentials or run as root to conceal it.

Added actual paths:

```text
scripts/verify-order-kafka.ps1
simulators/order-service/src/test/java/com/aegisflow/simulator/order/persistence/
  OrderKafkaDeliveryIT.java
  OrderKafkaFixture.java
```

These compile, but all3 live tests skipped in the restricted session; the owner must run the focused verifier before calling this path verified. It does not rerun the already-passed44 tests. The helper creates an isolated pinned broker, reuses `scripts/kafka/` authentication/topics/ACLs, publishes only a random loopback host port, and label-checks the exact test container before stop/start. It never opens private env files or existing Compose storage. Fresh database roles use the unchanged Order V1 migration.

Trace the test path: `OrderPersistenceService.create` commits aggregate+outbox → real `OrderOutboxStore` claim → real `KafkaOutboxPublisher` waits for broker acknowledgement → independent database completion → Core-identity **test observer** checks canonical payload hash, tenant, event ID, key and committed consumer offset. The real opt-in Spring producer configuration is loaded; a test scheduler suppresses automatic polls so fault placement is deterministic. No inbox or separately launched packaged application is claimed.

Debugging exercise: in `actualKafkaAckThenLocalCompletionFailureRedeliversOriginalEvent`, stop at the injected exception inside the completion transaction. Expected: a real Kafka record exists, but rollback leaves the row PENDING with its lease. The test expires only that test lease, polls again, and requires a second offset with identical key/value/event ID, followed by PUBLISHED. Never edit an actual immutable payload or delete a development volume to reproduce this. A healthy second record is an at-least-once outcome, not an exactly-once guarantee.

### Q36. Why is the broker test separate from the passed database tests?

The database tests prove claims and local transaction rules. Only a real producer/broker/consumer round trip can test acknowledgement, listener/authentication configuration and network failure. A skipped integration test proves neither.

### Q37. Why use a disposable broker for an outage?

Stopping a shared development broker can disrupt unrelated work. The fixture owns a unique container and verifies its label/ID before interruption; existing Compose storage is never targeted.

### Q38. Does an idempotent Kafka producer remove the outbox duplicate window?

No. It handles certain transport retries, not a new application send after an acknowledged message's database completion rolled back. The test requires a later offset containing the original event; Core still needs database-backed inbox idempotency.

### Q39. Why suppress scheduled callbacks in this test?

Manual calls place the broker outage and completion rollback at precise points without another polling thread racing the assertions. This exercises real configured publishing but leaves wall-clock scheduling and packaged startup as separate unverified gates.

### Q40. How is bounded retry checked without a fragile sleep?

The outage poll has a duration bound and persists a retry timestamp between DB-clock observations plus the first backoff. Setting that test row's retry time in the future proves repeated polls do not claim it; making it due after broker restart allows delivery with unchanged identity. This bounds retry rate, not total lifetime attempts.

## Current P0.3-B slice: Order outbox relay (2026-10-19)

The Order host gate now passes on source `b0f1a16`: **27 default +17 real PostgreSQL tests**, zero failures/errors/skips, including all nine relay ITs. Saved XML reports independently confirm the owner's run finished2026-10-19 at02:48:14IST. Earlier sandbox loopback errors/skips remain in evidence; no rerun is needed. Actual Order DB→Kafka delivery/outage/ack-crash testing and enabled-profile startup are next, followed by Payment/Warehouse relays and Core inbox. This is not a completed P0.3 delivery flow.

Actual new files under `simulators/order-service/src/main/java/com/aegisflow/simulator/order/persistence/`:

| Class | Responsibility |
|---|---|
| `OutboxClaim` | immutable lease capability; redacted `toString`, not a new event identity |
| `OrderOutboxStore` | JDBC one-row SKIP LOCKED claim; DB-clock expiry, independent local transaction and fenced completion |
| `OrderOutboxRelay` | bounded incremental poll, tenant/content checks, send outside transaction, ack then published; fixed error categories |
| `KafkaOutboxPublisher` | fixed Order topic, producer future acknowledgement, bounded close |
| `OrderRelayConfiguration` | postgres+explicit opt-in only, fixed Order SASL identity and local endpoint, scheduling |

Tests in the matching test package are `OrderOutboxRelayTests`, `KafkaOutboxPublisherTests`, and `OrderOutboxRelayIT`. The first two run without a broker/database and have18tests. The IT has nine real-PostgreSQL cases; its crash-window publisher is a test double, not a broker. `scripts/verify-order-relay.ps1` runs all Order defaults and both IT suites with fresh reports, not the whole reactor.

Flow: existing `OrderPersistenceService.create` commits business state plus immutable intent → a short `REQUIRES_NEW` JDBC claim transaction commits owner/60s lease/increased fence+attempt → validate explicit allowed tenant and canonical stored envelope → Kafka `send(...).get(...)` outside the database transaction → separate compare-and-set update to PUBLISHED with DB timestamp, only for the same tenant/outbox/owner/fence and live lease. Each next row is claimed only when ready to send, so a queued batch does not age its own leases. Original V1 metadata/grants suffice; no migration is changed.

PostgreSQL JSONB reorders/pretty-prints content. The relay parses it to maps and reuses `CanonicalJson.write` before checking the existing `aegisflow-json-v1` digest; hashing raw `jsonb::text` would falsely reject valid events. This preflight checks size (256KiB), stored hash/profile, identity/source/schema/type, bounded identifiers/time and details object. It supports existing `OrderCreated` only and is not a general consumer JSON-schema validator.

Kafka uses `acks=all`, idempotence,5s request/max-block and15s delivery limits, then a20s future wait; these are below the60s lease in normal execution. The database transaction is never held over network waits. Unconfirmed sends retain PENDING with exponential1/2/4/8/16/32/60s retries, capped in rate but not lifetime attempts. An interrupted worker leaves its lease to expire. Invalid content or an unconfigured tenant becomes retained FAILED intent with a fixed reason, not a deleted record. No automated requeue, metrics/alerting or operator API exists yet.

Activation is deliberately separate from ordinary postgres startup. Only when ready for a synthetic local trial, supply `ORDER_OUTBOX_RELAY_ENABLED=true`, `ORDER_KAFKA_BOOTSTRAP_SERVERS` (`127.0.0.1:<port>` or Docker `kafka:9092`), the Order-only `KAFKA_ORDER_PASSWORD`, and `ORDER_OUTBOX_ALLOWED_TENANTS` (comma-separated exact IDs, no padding). `ORDER_OUTBOX_MAX_PER_POLL` defaults10 and is bounded1..100. Keep that process's database credentials Order-only; never load broker/admin/Core/sibling credentials into it. Configure every intended tenant before enabling: excluded rows become FAILED. This application startup path has not yet passed a real DB/Kafka test. Host-to-base internal PostgreSQL networking remains a known limitation; do not weaken networks to work around it.

Debugging exercise (disposable test DB only): inspect `expiredClaimIsFencedBeforeAndAfterReclaimWithSameImmutableEvent` in `OrderOutboxRelayIT`. The test expires the lease using DB time without sleeps, proves old completion affects zero rows, then reclaims the same immutable ID/content with a higher fence. Next inspect `acknowledgedSendThenCompletionCrashReplaysSameIdentityOutsideDatabaseTransaction`: after injected completion failure, the row remains pending; reclaim sends the same key/envelope again, then marks it published. Both passed on real PostgreSQL in the owner's2026-10-19 run. The publisher is synthetic: this is not observed real Kafka duplicate handling.

### Q31. Why use a lease and fencing token rather than a boolean claimed flag?

A dead process cannot clear a boolean. Expiry allows another worker to reclaim; the increasing token prevents an old resumed worker from finalizing that newer claim. DB time avoids host-clock disagreement. Tenant/owner/token/status/expiry all participate in the final update.

### Q32. Why is the claim transaction separate from Kafka publishing?

Holding a row lock or connection during network waits consumes database capacity and couples outage duration to transactions. `TransactionTemplate` commits each short claim/update independently. The relay rejects an ambient database transaction before polling, so its caller cannot accidentally enclose the broker wait.

### Q33. Does producer idempotence eliminate the ack-crash duplicate window?

No. Kafka may acknowledge before PostgreSQL records PUBLISHED. A restarted process can resend the same event under a new producer session. The later durable inbox must deduplicate the original event identity; this slice does not implement that inbox.

### Q34. Why not mark every send timeout FAILED or PUBLISHED?

A timeout leaves the broker outcome unknown. PUBLISHED would invent success; dropping the intent would risk loss. The relay retains PENDING with bounded backoff and the same ID. Only deterministic preflight rejection produces retained FAILED here; raw exception text is not saved as an error code.

### Q35. What do the passing18unit tests actually prove?

They prove local orchestration, digest representation, bounded configuration/retry and explicit acknowledgement ordering using doubles. They do not prove SQL locking/grants or actual Kafka delivery. Nine PostgreSQL ITs and a future real DB→Kafka fault test cover different boundaries. The verifier treats skipped ITs as failure, regardless of Maven's own exit code.

Primary references for this slice: [PostgreSQL locking clauses](https://www.postgresql.org/docs/18/sql-select.html), [Kafka producer configuration](https://kafka.apache.org/42/configuration/producer-configs/) and [KafkaProducer acknowledgement API](https://kafka.apache.org/42/javadoc/org/apache/kafka/clients/producer/KafkaProducer.html). No production exactly-once or high-availability claim follows.

---

**Latest Kafka checkpoint, recorded 2026-10-18:** the owner's real 2026-10-17 host run passed both listener routes, topic settings, producer/consumer round trips, committed offsets and selected authentication/ACL denials. The saved report is Passed=true/Stage=complete. This follows the standalone build and 25 no-broker checks; the coding-session engine restriction remains separate. No owner rerun is needed; source-owned relay and durable Core inbox are next, not yet implemented.

**Completed 2026-10-17 persistence host result:** the complete persistence rehearsal **passed**, actual report Passed=true/Stage=complete for `aegisflow-p03-verify-93dcfedb932a45a1b5d28241d4c5098b`. Eight postgres-profile HTTP 200/UP startups, eight owner SQL-boundary groups, 24 sibling-credential denial attempts and identical sentinel/full Flyway histories after retained-volume restart prove this operational slice. Fresh V1 runs once per owner; the second startup validates existing V1 without another migration. This does not repeat or replace P0.2 atomicity tests and does not prove Kafka delivery. No persistence rerun is needed to continue.

**Health correction lesson, preserved:** `scripts/java/RehearsalHealthProbe.java` accepts detail-free Actuator root UP plus optional probe-group array in either order, with HTTP 200/4096-byte limit/no redirects or duplicate/nested status/details. Group names remain visible with `show-details: never`. Its 23 no-network checks are distinct from the eight real host health gates. For future timeouts read safe last-check diagnostics/redacted owner logs before changing timeouts or business code; no response/credential is printed. Earlier Core migration/startup succeeded but the bare-UP matcher timed out; that failure remains in evidence.

**Earlier same-day network correction:** the first runner failed before Java at its host-port assumption (raw output unavailable; not a proven IPv6 defect). The current runner executes the same built JAR in a pinned Java 21 test container on only its owner's internal network, using `owner-db:5432`. `Get-RehearsalApplicationArguments` builds hardening/read-only mounts and five owner-only credential names; `Assert-RehearsalOwnedContainer` guards cleanup by exact ID and label. Base networks/roles/application Java/V1 remain unchanged; standalone Java is test harness only.

**Current P0.3-A checkpoint:** P0.3 is approved/incomplete; the persistence prerequisite is verified and Kafka setup/provisioning/smoke tooling is written. Application Java, migrations and application Maven dependencies are unchanged; `tools/kafka-smoke` is a separate verification project. Once actual Kafka startup, both listeners, topic settings and credential boundaries pass, continue the approved relay/Core inbox and real fault tests. No application relay, inbox, correlation or recovery is implemented here; P0.4 remains unapproved.

**P0.2 verification passed (2026-10-16):** the final clean full reactor reported `BUILD SUCCESS` at **19:45:47 IST**, **2 min 17 s**, at entry HEAD `66282a9` with implementation source `7a90c57`. **38 default tests and 29 real PostgreSQL ITs passed**, zero failures/errors/skips: Core 7/4, Order 9/8, Payment 12/9, Warehouse 10/8. All four packaged foundation JARs returned HTTP 200 / `UP`; the documentation checker reported **Issues 0** (29/20/109/7/1/16/20/8 checks). These remain preserved P0.2 results. Subsequently approved P0.3's separate persistent-Compose/postgres-profile prerequisite passed on 2026-10-17; broker/relay/inbox remain next.

**Verification-wrapper caveats:** the successful host run's helper summary counted only 4 PostgreSQL tests because its report glob missed simulator reports. The reporting fix independently aggregates the actual XML as 29; no business-source change or host test rerun is needed. An outer Windows PowerShell 5.1 `-Command` invocation also lost the quotes around `Stop` and emitted `Stop not recognized`. Actual checks passed, but that wrapper's fail-fast setup was not demonstrated. For future runs, enter PowerShell 7 using `& $ps7 -NoProfile` (`$ps7` is the installed `pwsh.exe` path), then execute the `.ps1` files directly inside it; avoid the old nested native-command quoting example.

**Resolved Warehouse startup lesson:** an earlier host run passed 37 default tests and 21 Core/Order/Payment PostgreSQL ITs, but Warehouse failed context startup because its four `@Repository` adapters were final. Spring wraps these beans to translate persistence exceptions; without interfaces it subclasses them, which final prevents. The adapters remain package-private but are now subclassable. `simulators/warehouse-service/src/test/java/com/aegisflow/simulator/warehouse/reservation/WarehouseRepositoryProxyTests.java` checks all four proxies. The successful Warehouse host run above supersedes the earlier pending assertion status; the failure remains recorded in the evidence.

**Resolved JDBC startup lesson (2026-10-16):** PgJDBC sent the default Java timezone `Asia/Calcutta` before authentication and the server rejected it. Root Surefire/Failsafe now fork with `-Duser.timezone=UTC`, retaining Mockito. `PostgresImageConfigurationTests` checks the actual default timezone and unchanged image reference: two tests passed. Windows timezone is unchanged. A manual postgres-profile Java launch should include that JVM option before `-jar`; changing Hibernate's SQL timezone after connection startup is too late.

**Resolved image setup lesson (2026-10-16):** an earlier host run passed five Core default tests and connected Testcontainers to Docker, then failed PostgreSQL image compatibility validation before database startup. All four ITs now explicitly declare the unchanged digest pin compatible with `postgres`. `control-plane/src/test/java/com/aegisflow/core/PostgresImageConfigurationTests.java` checks container initialization without Docker. That regression alone cannot prove transactions; the later real PostgreSQL results above supply the database evidence.

## Read this first: the P0.2 checkpoint

P0.1's four application foundations and ten control-plane module boundaries are preserved. P0.2 now has source for independent Flyway migrations, database role bootstrap, and local transactional state/idempotency/outbox paths in Order, Payment, and Warehouse. These application methods are Java boundaries for later HTTP workflows; no business HTTP controller, authentication, Kafka publisher/consumer, incident detection, recovery, or AI behavior exists.

P0.2 implementation and verification are complete after correcting the helper's report aggregation. The interrupted-session recovery preserved progress in the original repository. Earlier restricted-host and setup failures remain historical evidence; the final host reactor, PostgreSQL assertions, packaged startup, and documentation checks passed. Read [`docs/evidence/TEST_RESULTS.md`](../evidence/TEST_RESULTS.md) for dated commands, counts, failures, and the remaining operational rehearsals.

Historical restricted-session evidence, 2026-10-13: an isolated cached-library diagnostic passed **21 pure validation/canonical-envelope tests** with zero failures/errors/skips. A focused Core run passed **4 architecture tests**, while its **4 PostgreSQL ITs skipped** because Docker's pipe was denied. Compose configuration and script syntax passed; the uncached JPA starter and Maven Central DNS then blocked simulator compilation. Later host runs resolved those blockers, culminating in the full passing gate above. `scripts/verify-persistence.ps1` remains the repeatable gate and rejects skipped integration tests.

## Actual repository map

```text
aegisflow/
├── pom.xml                                  # parent + four-module Maven reactor
├── mvnw / mvnw.cmd                         # POSIX / Windows Maven wrappers
├── .mvn/wrapper/maven-wrapper.properties   # Maven 3.10.0, wrapper 3.3.4, SHA-256
├── compose.yaml / .env.example             # four PostgreSQL services; three roles per database
├── scripts/db/bootstrap-roles.sql          # non-destructive bootstrap for fresh/existing volumes
├── scripts/bootstrap-databases.ps1         # run role bootstrap on all four Compose services
├── scripts/verify-persistence.ps1          # Docker + Java 21 gate; rejects skipped ITs
├── scripts/verify-startup.ps1              # Windows executable-JAR health verification
├── scripts/verify-docs.ps1                 # continuity-document consistency check
├── control-plane/
│   ├── pom.xml                              # MVC, Actuator, JDBC/Flyway, Modulith, Testcontainers
│   └── src/
│       ├── main/java/com/aegisflow/core/
│       │   ├── AegisFlowCoreApplication.java
│       │   └── {audit,correlation,detection,eventing,evidence,identity,
│       │       investigation,operations,recovery,verification}/
│       │       ├── package-info.java        # @ApplicationModule declaration
│       │       └── FoundationModule.java    # marker type for discovery
│       ├── main/resources/
│       │   ├── application.yaml
│       │   ├── application-foundation.yaml
│       │   ├── application-postgres.yaml
│       │   └── db/migration/V1__core_database_boundary.sql
│       └── test/java/com/aegisflow/core/
│           ├── AegisFlowCoreApplicationTests.java
│           ├── ControlPlaneArchitectureTests.java
│           └── PostgresInfrastructureIT.java
├── simulators/
│   ├── order-service/src/main/java/com/aegisflow/simulator/order/persistence/
│   │   ├── CreateOrder.java / OrderPersistenceService.java
│   │   ├── OrderEntity.java / OrderKey.java / OrderRepository.java
│   │   └── OrderOutbox.java / CanonicalJson.java
│   ├── payment-service/src/main/java/com/aegisflow/simulator/payment/
│   │   ├── PaymentCreation.java / CreatePaymentCommand.java / RecordedPayment.java
│   │   └── persistence/internal/            # transaction service, JPA stores, outbox, validation
│   └── warehouse-service/src/main/java/com/aegisflow/simulator/warehouse/reservation/
│       ├── CreateReservationCommand.java / ReservationApplicationService.java
│       ├── ReservationEntity.java / ReservationLineEntity.java / repositories
│       └── IdempotencyRepository.java / WarehouseEventFactory.java / OutboxEventRepository.java
│       # each module also owns application*.yaml, db/migration/V1__*.sql,
│       # foundation/architecture tests, unit contract tests, and persistence ITs
├── docs/                                    # architecture, ADRs, evidence, handoff, learning
└── AGENTS.md                                # continuity and safety protocol
```

The source of truth is the repository, not this abbreviated tree. In particular, each simulator lives in a separate Maven module and Java package; it is not an in-process control-plane package.

The concrete path index is:

| Module | POM and main source | Runtime resources | Test paths |
|---|---|---|---|
| Control plane | `control-plane/pom.xml`; `control-plane/src/main/java/com/aegisflow/core/AegisFlowCoreApplication.java` | `control-plane/src/main/resources/application*.yaml` | `control-plane/src/test/java/com/aegisflow/core/{AegisFlowCoreApplicationTests,ControlPlaneArchitectureTests,PostgresInfrastructureIT}.java` |
| Order | `simulators/order-service/pom.xml`; `simulators/order-service/src/main/java/com/aegisflow/simulator/order/OrderSimulatorApplication.java` | `simulators/order-service/src/main/resources/application*.yaml`; `db/migration/V1__order_state_and_outbox.sql` under the same resources root | Foundation/architecture tests under `simulators/order-service/src/test/java/com/aegisflow/simulator/order`; `persistence/OrderPersistenceTests.java` and `persistence/OrderPersistenceIT.java` |
| Payment | `simulators/payment-service/pom.xml`; `simulators/payment-service/src/main/java/com/aegisflow/simulator/payment/PaymentSimulatorApplication.java` | `simulators/payment-service/src/main/resources/application*.yaml`; `db/migration/V1__payment_state_and_outbox.sql` | Foundation/architecture tests under `simulators/payment-service/src/test/java/com/aegisflow/simulator/payment`; `persistence/internal/{PaymentCommandValidatorTest,CanonicalJsonTest,PaymentPersistenceIT}.java` |
| Warehouse | `simulators/warehouse-service/pom.xml`; `simulators/warehouse-service/src/main/java/com/aegisflow/simulator/warehouse/WarehouseSimulatorApplication.java` | `simulators/warehouse-service/src/main/resources/application*.yaml`; `db/migration/V1__warehouse_reservation_and_outbox.sql` | Foundation/architecture tests under `simulators/warehouse-service/src/test/java/com/aegisflow/simulator/warehouse`; `reservation/{CreateReservationCommandTests,WarehouseEventContractTests,WarehousePersistenceIT}.java` |

## Maven reactor and four deployables

The root `pom.xml` is both the parent POM and the aggregator. Parent inheritance gives all children one Java and dependency baseline; aggregation tells Maven which modules to build together.

| Maven module | Artifact and entry point | Default address | Foundation responsibility |
|---|---|---|---|
| `control-plane` | `aegisflow-core`; `com.aegisflow.core.AegisFlowCoreApplication` | `127.0.0.1:8080` | Hosts the modular AegisFlow control plane. |
| `simulators/order-service` | `order-service`; `com.aegisflow.simulator.order.OrderSimulatorApplication` | `127.0.0.1:8081` | Independent shell for the future order system. |
| `simulators/payment-service` | `payment-service`; `com.aegisflow.simulator.payment.PaymentSimulatorApplication` | `127.0.0.1:8082` | Independent shell for the future payment system. |
| `simulators/warehouse-service` | `warehouse-service`; `com.aegisflow.simulator.warehouse.WarehouseSimulatorApplication` | `127.0.0.1:8083` | Independent shell for the future warehouse system. |

The root build pins Spring Boot `4.1.1`, compiles with Java release `21`, and imports Spring Modulith BOM `2.1.1`. Maven Enforcer requires Java `[21,22)` and Maven `[3.10.0,3.11.0)`. The wrapper properties select Maven `3.10.0`, wrapper implementation/plugin `3.3.4`, and a distribution checksum. The distinction matters: `3.3.4` is the Maven Wrapper version, while `3.10.0` is the Maven distribution that executes the build.

The child POMs share web MVC, Actuator, Flyway, PostgreSQL runtime support, tests, and the Spring Boot Maven plugin. Core retains JDBC and adds Spring Modulith. The three simulators now use `spring-boot-starter-data-jpa`; all four modules have PostgreSQL/JUnit Testcontainers dependencies. Boot's Flyway starter supplies the auto-configuration, while `flyway-database-postgresql` supplies PostgreSQL support. The root `infrastructure-tests` profile activates Maven Failsafe for `*IT` classes throughout the reactor. Spring AI, Kafka, and Spring Security remain absent from these application deployables. The separate `tools/kafka-smoke` project now uses Boot-managed Kafka clients for infrastructure verification.

The parent also bans dependencies on all four deployable artifacts. That prevents a module from quietly importing another service's implementation instead of using a future versioned API or event contract.

## What the application classes do

Each entry point has `@SpringBootApplication` and calls `SpringApplication.run(...)`. `@SpringBootApplication` combines three ideas:

1. the class is a Spring configuration source;
2. Spring Boot may apply auto-configuration based on dependencies and configuration; and
3. component scanning starts at the entry point's package and searches downward.

Package placement is therefore architectural. `OrderSimulatorApplication` scans `com.aegisflow.simulator.order`, not the payment, warehouse, or control-plane packages. The control-plane entry point also has `@Modulithic(systemName = "AegisFlow Control Plane")`, making it the root used by Spring Modulith's model and verification tests.

There are no user-written controllers at this milestone. The HTTP route that exists comes from Actuator auto-configuration.

## Foundation and PostgreSQL profiles

Every deployable has the same profile pattern:

- `application.yaml` sets its application name, loopback address, default port, graceful shutdown, the `foundation` default profile, and health-only Actuator exposure.
- `application-foundation.yaml` excludes data-source and Flyway auto-configuration; the simulator files also exclude Hibernate JPA and Spring Data JPA repository auto-configuration. Persistence components belong to `postgres`, keeping the infrastructure-free foundation contract.
- `application-postgres.yaml` selects that deployable's PostgreSQL runtime connection and a separate Flyway migration connection. Simulator Hibernate configuration uses `ddl-auto: validate`, not schema generation; `open-in-view: false` prevents an HTTP view from keeping a persistence context open.

| Deployable | PostgreSQL URL default | Required password variables |
|---|---|---|
| Control plane | `jdbc:postgresql://127.0.0.1:5433/aegisflow_core` | `CORE_DB_RUNTIME_PASSWORD`, `CORE_DB_MIGRATION_PASSWORD` |
| Order | `jdbc:postgresql://127.0.0.1:5434/aegisflow_order` | `ORDER_DB_RUNTIME_PASSWORD`, `ORDER_DB_MIGRATION_PASSWORD` |
| Payment | `jdbc:postgresql://127.0.0.1:5435/aegisflow_payment` | `PAYMENT_DB_RUNTIME_PASSWORD`, `PAYMENT_DB_MIGRATION_PASSWORD` |
| Warehouse | `jdbc:postgresql://127.0.0.1:5436/aegisflow_warehouse` | `WAREHOUSE_DB_RUNTIME_PASSWORD`, `WAREHOUSE_DB_MIGRATION_PASSWORD` |

Each prefix also has optional `*_DB_URL`, `*_DB_RUNTIME_USER`, and `*_DB_MIGRATION_USER` overrides. Default role names are `aegisflow_<service>_runtime` and `aegisflow_<service>_migrator`. Payment additionally permits `PAYMENT_DB_MIGRATION_URL`; use the same owning database. The older `*_DB_PASSWORD` variables are bootstrap-owner passwords for Compose administration, not application runtime passwords.

The explicit `postgres` profile needs existing databases, roles, and the `aegisflow` schema. `scripts/db/bootstrap-roles.sql` provisions these via the local owner before Flyway runs. All four profiles now require migration locations; each database has its own `aegisflow.flyway_schema_history`. Similar table names in different databases do not imply shared ownership. Core's V1 establishes its schema boundary without implementing future inbox/projection/incident tables. Order/Payment/Warehouse V1 files create only their own business state, idempotency record, and outbox tables.

The runtime account has table-specific business grants and only publication-metadata update grants on the outbox. The migrator owns schema changes and history. The owner remains administration-only. Separate host PostgreSQL suites passed privilege assertions; the full persistent-Compose bootstrap/runtime/role/retained-volume rehearsal also passed on 2026-10-17. These demonstrate tested grants/operational cases, not complete deployment security or authenticated tenant isolation.

## Actuator request and test flow

The default runtime path is intentionally small:

```text
wrapper command
  -> module main(...)
  -> SpringApplication builds the context
  -> foundation profile suppresses JDBC/Flyway auto-configuration
  -> embedded MVC server binds to 127.0.0.1
  -> Actuator registers GET /actuator/health
  -> health response exposes status but no internal details
```

The application tests use `@SpringBootTest(webEnvironment = RANDOM_PORT)`. This creates the complete Boot context and a real embedded HTTP server while asking the operating system for a free port, avoiding conflicts with ports `8080`–`8083` and with concurrently running tests. `@LocalServerPort` injects that chosen port after the server starts, so the Java `HttpClient` calls the correct URL rather than guessing.

All four application test classes explicitly select `@ActiveProfiles("foundation")`, even if a caller's environment selects `postgres`. They request `/actuator/health`; the simulator tests also check that internal component/details fields are absent and verify their unique `spring.application.name`. These are foundation smoke tests, not evidence that any business workflow works.

During startup, Boot reads configuration and profile-specific overrides, creates the application context, applies eligible auto-configurations, scans components, and starts embedded Tomcat. Only after the server has bound can `@LocalServerPort` be injected and the HTTP assertion run. Closing the test context stops the server. For packaged applications, `scripts/verify-startup.ps1` launches each JAR on an ephemeral port, checks its own process's health, and stops only the child processes it created in a `finally` block.

The root Surefire configuration supplies Mockito's test instrumentation as a startup `-javaagent`. This avoids runtime self-attachment, which hung on this restricted Windows host. It is test-only, not an application dependency or a disabled assertion. A bounded fork timeout prevents a hung test JVM from waiting indefinitely. See the evidence record for the failed attempt and successful reruns.

## Ten explicit control-plane boundaries

The control plane currently declares exactly ten top-level Spring Modulith application modules. They are empty seams for later behavior, not implemented services.

| Package | Intended ownership |
|---|---|
| `audit` | Append-only security- and business-significant transitions. |
| `correlation` | Tenant-scoped business-transaction timelines and projections. |
| `detection` | Versioned invariant and contract-deadline evaluation. |
| `eventing` | Event validation, durable inbox, and quarantine. |
| `evidence` | Immutable evidence with authoritative provenance. |
| `identity` | Authenticated actors, tenants, roles, and workload scopes. |
| `investigation` | Deterministic investigation and later evidence-bounded AI advice. |
| `operations` | Operational health and later bounded metrics/tracing integration. |
| `recovery` | Typed plans, policy, approval, worker claims, and action orchestration. |
| `verification` | Independent authoritative-state checks before resolution. |

Each package contains:

- `package-info.java`, whose `@ApplicationModule(displayName = ..., allowedDependencies = {})` declares the boundary and currently permits no cross-module dependency; and
- a package-private empty `FoundationModule` marker, which gives the otherwise behavior-free package a compiled type that Modulith can discover.

`ControlPlaneArchitectureTests` then applies four checks:

1. `ApplicationModules.verify()` asks Spring Modulith to verify its discovered model.
2. A set assertion requires exactly the ten documented identifiers—no silent omission or accidental eleventh module.
3. Reflection checks each marker package has `@ApplicationModule` and an empty allowed-dependency list, then checks the discovered model agrees.
4. An ArchUnit rule prevents production classes under `com.aegisflow.core` from referencing simulator packages.

Each simulator has a separate architecture test that inspects compiled main-class bytecode for references to the control plane or sibling simulators. At the build level, Maven's dependency ban provides another layer. These checks overlap deliberately: the POM blocks artifact coupling, Modulith checks internal control-plane boundaries, and bytecode/ArchUnit checks catch package references.

## Reproducible commands

Run these from the repository root with `JAVA_HOME` pointing to a **Java 21 JDK**, not the host's Java 26 installation. A first wrapper invocation needs network access and a writable Maven cache. On POSIX, `unzip` is required: the wrapper intentionally fails instead of silently switching to a tarball with a different checksum. Only Windows execution was verified here.

Windows PowerShell:

```powershell
.\mvnw.cmd --version
.\mvnw.cmd clean verify
.\mvnw.cmd -pl control-plane -am test
.\mvnw.cmd -Pinfrastructure-tests clean verify
# Prefer the gate helper: fails when Docker is unavailable or an IT is skipped.
.\scripts\verify-persistence.ps1
# After verify has produced all four runnable JARs (PowerShell 7 on Windows):
.\scripts\verify-startup.ps1
.\scripts\verify-docs.ps1

# Start one foundation app; stop it with Ctrl+C.
.\mvnw.cmd -pl control-plane spring-boot:run
Invoke-RestMethod http://127.0.0.1:8080/actuator/health

# Equivalent simulator example.
.\mvnw.cmd -pl simulators/order-service spring-boot:run
Invoke-RestMethod http://127.0.0.1:8081/actuator/health
```

POSIX shell:

```bash
./mvnw --version
./mvnw clean verify
./mvnw -pl control-plane -am test
./mvnw -Pinfrastructure-tests clean verify

# Start one foundation app; stop it with Ctrl+C.
./mvnw -pl control-plane spring-boot:run
curl --fail --silent http://127.0.0.1:8080/actuator/health

# Equivalent simulator example.
./mvnw -pl simulators/order-service spring-boot:run
curl --fail --silent http://127.0.0.1:8081/actuator/health
```

The infrastructure command uses Testcontainers and therefore requires a usable Docker-compatible runtime. Whether it ran, failed, or was skipped must be read from [`docs/evidence/TEST_RESULTS.md`](../evidence/TEST_RESULTS.md), not assumed here.

To prepare local databases, copy `.env.example` to an ignored `.env` and fill its twelve distinct passwords privately: owner, migrator, and runtime for each of four services. Validate using `docker compose --profile postgres config --quiet`, then run `docker compose --profile postgres up -d --wait` and `.\scripts\bootstrap-databases.ps1`. The bootstrap helper also supports existing volumes without deleting their data. Do not print expanded Compose configuration because it contains credentials. `docker compose --profile postgres down` preserves volumes; do not use `down -v` unless deliberately discarding local data.

Compose reads `.env`, but an independently launched Java process does **not** automatically load that file. Supply only that application's owning-database credentials through its environment. For example, after preparing its database, prompt locally for Core's runtime and migration passwords:

```powershell
$env:SPRING_PROFILES_ACTIVE = "postgres"
$env:CORE_DB_RUNTIME_PASSWORD = Read-Host "Local Core runtime password" -MaskInput
$env:CORE_DB_MIGRATION_PASSWORD = Read-Host "Local Core migration password" -MaskInput
.\mvnw.cmd -pl control-plane spring-boot:run
Remove-Item Env:CORE_DB_RUNTIME_PASSWORD, Env:CORE_DB_MIGRATION_PASSWORD, Env:SPRING_PROFILES_ACTIVE
```

```bash
read -r -s -p 'Local Core runtime password: ' CORE_DB_RUNTIME_PASSWORD
read -r -s -p 'Local Core migration password: ' CORE_DB_MIGRATION_PASSWORD
export CORE_DB_RUNTIME_PASSWORD CORE_DB_MIGRATION_PASSWORD
SPRING_PROFILES_ACTIVE=postgres ./mvnw -pl control-plane spring-boot:run
unset CORE_DB_RUNTIME_PASSWORD CORE_DB_MIGRATION_PASSWORD
```

## Practical debugging exercise: identify a profile failure

Goal: distinguish “the application foundation is broken” from “optional infrastructure was selected but is unavailable.”

1. Start the control plane with no active-profile override. Confirm from the startup log that `foundation` is active, then request `/actuator/health`.
2. Stop it. Set `SPRING_PROFILES_ACTIVE=postgres` but deliberately omit `CORE_DB_RUNTIME_PASSWORD` and `CORE_DB_MIGRATION_PASSWORD`, then start it again.
3. Read the earliest useful cause in the exception chain. Look for unresolved database configuration before changing Java code.
4. Supply a non-secret local value but leave PostgreSQL stopped. Compare the new failure: configuration can now resolve, but the connection should be unavailable.
5. Remove the environment variables and return to `foundation`.

The learning point is that profiles select different runtime contracts. The default profile proves the Boot shell and health route without infrastructure; the PostgreSQL profile promises that the named database and credential exist. Do not “fix” the latter by hard-coding a password or disabling Flyway. Record actual observations in the evidence file because exact diagnostics depend on the local runtime.

## P0.2: how state and publication intent become durable

The simulator transaction methods accept validated commands from trusted Java callers. They do not call another database or service. A tenant is part of every aggregate key and idempotency lookup. This gives tenant-aware persistence; it does not authenticate the caller or authorize an HTTP request. A future ingress must derive tenant identity from a trusted authentication boundary rather than accepting an arbitrary caller-supplied tenant.

### Spring Data JPA, Hibernate, and explicit SQL

JPA describes object-to-table mapping and persistence operations. Hibernate is the JPA provider used by these simulator modules: it tracks entities and creates SQL when a persistence context flushes. Spring Data JPA supplies repository implementations such as `OrderRepository` and `PaymentJpaRepository`; their `saveAndFlush` saves the entity and forces pending SQL to run before the method proceeds. Warehouse uses its own `ReservationRepository`/`ReservationLineRepository` around `EntityManager.persist` and an explicit final flush instead of Spring Data interfaces. These choices fit the same local JPA transaction model.

`JdbcTemplate` executes the SQL that benefits from explicit PostgreSQL behavior: `INSERT ... ON CONFLICT DO NOTHING` for durable idempotency, and Order/Warehouse outbox inserts. Under Spring's JPA transaction manager, JPA and `JdbcTemplate` configured for the same application `DataSource` participate in the same transaction and connection. The `@Transactional` application method is invoked through Spring's proxy; an unchecked exception escaping that boundary causes rollback. An independently constructed service or a self-invoked method does not automatically create this boundary. No `REQUIRES_NEW` or second connection is used to append the outbox.

A flush is not a commit. It can reveal a constraint violation early, but rows remain uncommitted until the outer application method finishes and Spring successfully commits. This distinction makes the failure-after-outbox test meaningful: all inserts have reached PostgreSQL, yet one injected exception must remove the business state, idempotency record, and outbox together. Deferred foreign keys allow claiming the idempotency key before its referenced aggregate and event exist; they must all be present by commit.

### Keys, money, and versions

Commands carry explicit aggregate UUIDs. The database primary key pairs `tenant_id` with the resource ID, and line/event foreign keys include the tenant. Event IDs are UUIDs generated once for a successful new command; a replay returns the stored ID instead of inventing another event. Global event-ID uniqueness is enforced within each service-owned outbox database, not by a cross-service global registry.

Order and Payment represent money as `BigDecimal`/PostgreSQL `numeric(19,4)`. Their command validation checks positive amounts, ISO currency, fit, and the currency's supported fraction digits without silent rounding; event amounts use decimal strings. Binary floating-point money would make equality and request fingerprints unreliable. Warehouse records reservation outcomes and lines; it does not implement a stock ledger or a remote order foreign key.

The aggregate `@Version Long lockVersion` is a Hibernate optimistic-lock token. A new persisted row begins at `0`; the externally meaningful business version is explicitly `lockVersion + 1`, beginning at `1`. Order/Payment read it after `saveAndFlush`; Warehouse checks it after its final flush. A stale write must fail instead of silently overwriting a newer row. Later business transitions still require dedicated domain guards and atomic event appends; this checkpoint does not implement them just because an enum or SQL constraint names a future state.

### Durable replay and immutable outbox intent

Each service binds `(tenant_id, operation, idempotency_key)` to a versioned request fingerprint, resource/version, result, and event ID. `ON CONFLICT ... DO NOTHING` lets the PostgreSQL unique key serialize a race: a losing transaction waits for the competing insert to finish, then reads the committed stored result. Equal material content replays that result; changed material content under the same key raises a conflict. If the winner rolls back, its claim disappears. This design depends on the local database transaction, not an in-memory set or catching an insert error in an already-aborted transaction.

`CanonicalJson` implementations use the `aegisflow-json-v1` profile, sorted object keys, normalized values, stable line ordering, and UTF-8 SHA-256. Material request fields determine replay identity; diagnostic correlation identifiers do not create a second business operation. The profile is recorded beside hashes so a later normalization change needs deliberate compatibility handling.

Outbox rows retain the complete versioned event envelope, stable event ID, aggregate version, hash/profile, and `PENDING` publication status. Runtime grants and immutable-payload triggers separate envelope/identity fields from future publication metadata. This is durable publication intent. No worker publishes it, advances a lease, or retries Kafka in P0.2. A later relay may deliver duplicates after a crash; durable replay here does not establish universal exactly-once delivery or external effects.

### Trace the actual Order create call

Start at `simulators/order-service/src/main/java/com/aegisflow/simulator/order/persistence/OrderPersistenceService.java`, method `create(CreateOrder)`:

1. `CreateOrder` validates and normalizes the command; Spring's proxy opens the local transaction when `create` is called.
2. `CanonicalJson.requestHash` computes the material request fingerprint. `JdbcTemplate` claims the tenant/`CREATE_ORDER`/key row in `aegisflow.idempotency_record` using `ON CONFLICT DO NOTHING`.
3. On a replay, the service compares the stored hash/profile and returns the stored order ID, version, and event ID. It performs no new order or outbox insert.
4. On a new claim, `orders.saveAndFlush(new OrderEntity(...))` inserts the order and establishes business version `1`. Explicit SQL inserts its `order_lines`.
5. `OrderOutbox.append` builds `OrderCreated` and inserts one `PENDING` envelope and its hash into the owning database's `outbox_event`.
6. The returned `Result` becomes usable only after Spring commits successfully. A line, outbox, deferred-FK, or injected failure causes rollback of the claim and business/event rows together.

No HTTP ingress or Kafka output is hidden in this trace. `OrderPersistenceService.status(tenantId, orderId)` is a tenant-keyed Java read, not an authenticated public API.

### Payment and Warehouse entry points

Payment's public Java interface is `simulators/payment-service/src/main/java/com/aegisflow/simulator/payment/PaymentCreation.java`, method `recordInitialOutcome(CreatePaymentCommand)`. Its package-private implementation is `persistence/internal/TransactionalPaymentCreationService.java`. `PaymentCommandValidator` normalizes a simulated `AUTHORIZED` or `DECLINED` outcome, `IdempotencyRecordStore` claims or replays, `PaymentJpaRepository.saveAndFlush` inserts the payment, and `JpaPaymentOutboxAppender` saves the corresponding `PaymentAuthorized`/`PaymentDeclined` envelope in the same transaction. `RecordedPayment` retains the stored identity and occurrence time on replay. No real payment provider, charge, refund, or compensation exists.

Warehouse's entry is `simulators/warehouse-service/src/main/java/com/aegisflow/simulator/warehouse/reservation/ReservationApplicationService.java`, method `createReservation(CreateReservationCommand)`. `IdempotencyRepository` claims or replays; `ReservationRepository` and `ReservationLineRepository` persist the reservation outcome/lines; `WarehouseEventFactory` produces `InventoryReserved`/`InventoryRejected`; `OutboxEventRepository` inserts the envelope; the final flush checks pending entity SQL and version before commit. This records a supplied simulated outcome; it does not calculate stock availability. The aggregate/outbox foreign key is deferred because the explicit SQL outbox insert may run before Hibernate's final entity flush.

## P0.2 debugging exercise: fail after the outbox insert

Prerequisite: Java 21, an accessible Docker engine, and an IDE running the actual Testcontainers integration test. The Order PostgreSQL suite, including its rollback assertion, passed on the host. The interactive debugger steps below are an exercise; do not claim that a debugger session was performed merely because the test passed.

1. Open `simulators/order-service/src/test/java/com/aegisflow/simulator/order/persistence/OrderPersistenceIT.java` and select `failureAfterOutboxInsertRollsBackAllRows`.
2. Set a breakpoint after `OrderOutbox.append` executes its `JdbcTemplate.update`. The spy in the test calls the real append, then throws `IllegalStateException` before the transaction commits.
3. From the transactional connection, the order, line, idempotency claim, and outbox have been inserted. A separate connection must not see these uncommitted rows; do not mistake that invisibility for evidence that the inserts never ran.
4. Continue past the injected exception. The test calls the proxied service, catches the escaped failure, then queries the database after rollback. Counts for `orders`, `order_lines`, `idempotency_record`, and `outbox_event` under the test tenant must all be `0`.
5. Run `commitPersistsStateAndContractEnvelopeTogether` separately. The success path must retain business state and one matching event. Record the actual run, Docker environment, counts, and exit status in the evidence document.

If rows remain after the failure, first check proxy invocation, the `DataSource` shared by JPA/JDBC, and accidental independent transactions/connections. Do not move the outbox insert outside the transaction or suppress the exception to make the test pass.

## Five P0.2 backend interview questions and answers

### Q16. Why combine Spring Data JPA with JdbcTemplate in one transaction?

JPA/Hibernate provides entity mapping and optimistic locking for aggregate state. Explicit SQL gives PostgreSQL's conflict handling for a durable idempotency claim. With one application `DataSource` and Spring's JPA transaction manager, both participate in the same local transaction. Separate connections or `REQUIRES_NEW` would undermine the state/outbox rollback guarantee. The passing simulator PostgreSQL suites exercise that wiring and its rollback behavior in the recorded host environment.

### Q17. Why flush an entity before emitting its version, and why is the event version one higher?

Hibernate initializes the new entity's optimistic lock version when it persists/flushes. The SQL token begins at `0`, while the business/event contract begins at `1`, so the code explicitly exposes `lockVersion + 1`. `saveAndFlush` reveals generated version state and early SQL errors without committing. The surrounding transaction still owns commit or rollback.

### Q18. How does ON CONFLICT make two same-key commands safe?

The unique `(tenant, operation, key)` index arbitrates competing claims inside PostgreSQL. The loser does not create another aggregate; it reads the winner's committed result and compares the canonical material request hash. A changed request conflicts, an equal request returns the same resource/version/event ID, and a rolled-back winner leaves no durable claim. The passing PostgreSQL suites cover replay, conflict, concurrency, and rollback scenarios; those observed cases do not establish universal exactly-once effects.

### Q19. Why does each deployable need separate Flyway history and migration credentials?

Each service owns one database, including its schema evolution. Separate histories avoid coupling deployment order or schema versions across services. The migrator may change tables and history; the runtime may use only explicit business/publication-metadata grants. Core has no simulator credentials or database access path. The final host reactor passed migration/privilege assertions, and the separate 2026-10-17 persistent-Compose rehearsal verified bootstrap and retained-volume behavior.

### Q20. What does a successful transactional outbox write guarantee?

It makes the local business change and immutable publication intent durable together, or rolls them back together. It does not prove Kafka received anything, authenticate the tenant, or prevent every external duplicate effect. A later relay must preserve event identity across retries, and consumers/recovery APIs need their own durable idempotency and authoritative verification.

## P0.3-A: a safe persistence-infrastructure rehearsal

Actual new paths:

```text
compose.persistence-test.yaml              # replace static ports, mount test-only SQL
scripts/verify-compose-persistence.ps1       # real two-pass host rehearsal; -StaticOnly separately
scripts/bootstrap-databases.ps1             # existing helper; optional explicit Compose inputs
scripts/lib/compose-persistence.ps1          # pure project/port/environment guards
scripts/test-compose-persistence.ps1        # no-Docker safety regression tests
scripts/db/persistence-rehearsal.sql         # test-only schema/sentinel and role assertions
scripts/java/RehearsalHealthProbe.java       # Java 21 in-container loopback health check
scripts/java/RehearsalHealthProbeTests.java  # no-network response contract tests
scripts/test-rehearsal-probe.ps1             # compile/run standalone Java probe tests
```

The runner selects a random `aegisflow-p03-verify-<guid>` project, explicitly uses `.env.example`, and generates twelve independent test credentials without overwriting private `.env`. `Get-RehearsalComposeArguments` rejects the normal development project. `Get-RehearsalPublishedPort` accepts only one IPv4 loopback endpoint. `New-RehearsalChildEnvironment` removes inherited database/Spring/JVM overrides and supplies only one owner's five runtime/migration connection values. Core must never inherit simulator database credentials.

Actual rehearsal flow: existing four Compose PostgreSQL definitions → existing `bootstrap-databases.ps1`/role SQL → same existing JAR mounted read-only into an owner-network Java 21 test container (`postgres`, UTC, `owner-db:5432`) → real Flyway/Hibernate startup → `RehearsalHealthProbe` checks `127.0.0.1:8080` inside that container → test-only SQL grants/committed sentinel/history snapshot → remove only exact-label test application containers and stop database containers without deleting volumes → repeat bootstrap/JAR startup against retained volumes → compare sentinel and complete history snapshots. `AegisFlowCoreApplication` and simulator main classes are unchanged. The local JDK only compiles the test health probe; the application itself uses the pinned Linux Java 21 runtime. Fixture administration is test-harness activity, not a Core cross-service write. A sentinel proves only its recorded persistence case, not all recovery behavior. Ignored redacted logs/results live under `.cache/compose-persistence/<project>/`; `Stage` identifies the failing gate and successful bootstrap progress is recorded before Java starts.

`-StaticOnly` parses/merges Compose and checks project/port/volume/network isolation; it starts no containers or Java processes. `test-compose-persistence.ps1` checks pure safety decisions, not database privileges. Real execution fails if Docker is unavailable. The script retains disposable volumes; its printed exact cleanup command is informational and must not be executed without explicit deletion approval. No ordinary development volumes are addressed.

### Practical debugging exercise: distinguish static configuration from real persistence

For an optional future learning exercise (not a required rerun), use PowerShell 7 in the existing repository: run `scripts/test-compose-persistence.ps1`, then `scripts/verify-compose-persistence.ps1 -StaticOnly`. Inspect static-only output/results; do not call it a database pass. Real `scripts/verify-compose-persistence.ps1` requires Docker access and existing Java 21 JARs. Its expected observations were all actually confirmed on the owner's host on 2026-10-17: two postgres-profile startups per owner, restricted runtime SQL, denied sibling credentials and unchanged sentinel/Flyway snapshots after retained-volume restart. If a future second pass fails, compare its owner's redacted log/history instead of deleting volumes, rerunning `clean` or editing V1. The script filters inherited `SPRING_APPLICATION_JSON`/JDBC overrides so a caller profile cannot satisfy this check through foundation fallback.

### Q21. Why not use the normal Compose project to test first initialization?

An existing PostgreSQL volume skips fresh initialization and may contain valuable data or old credentials. A unique guarded Compose project plus ephemeral ports creates an independent storage/network namespace; restarting the same verification project tests persistence without deleting development data. Project/port/environment guards are pure helper tests; only executing the real bootstrap and JARs can prove their operational interaction.

### Q22. Why is Kafka still needed if the database already stores an outbox?

The outbox durably couples one owner's state and publication intent. Kafka will transport that committed event to independently restarting consumers and retain records for replay. `OrderPersistenceService.create` already commits state/outbox; the relay and transport are not implemented at this checkpoint. Broker outage must leave the committed row pending with bounded retry/backoff, not make the business transaction disappear.

### Q23. What does a producer acknowledgement prove, and why can an event repeat?

It proves the configured Kafka acknowledgement condition, not that PostgreSQL recorded relay completion. A crash between broker acknowledgement and the local published-status commit can resend the stable event ID. Producer idempotence does not make independent PostgreSQL/Kafka transactions atomic. The planned consumer must enforce `(tenant, source, eventId)` uniqueness and quarantine changed-content duplicates. These are future P0.3 tests, not current results. See [Kafka delivery semantics](https://kafka.apache.org/41/design/design/#message-delivery-semantics).

### Q24. When may a consumer acknowledge an offset relative to its inbox commit?

Only after durable accepted-inbox or technical-quarantine commit. A crash before database commit should roll back; a crash after commit but before offset acknowledgement can redeliver, so the unique inbox must prevent a second logical observation. Kafka offset state and an independent PostgreSQL transaction are not one atomic resource. P0.3 must implement this in existing Core `eventing`; no consumer currently exists. Later reconciliation must use durable identities/provenance, never infer business success from an offset alone.

### Q25. Does a transaction key make events globally ordered or trusted?

No. Kafka orders records within a topic partition; different topics/partitions do not share one total order. The existing key is `tenantId:businessTransactionId`; that workflow identifier differs from the immutable event ID. Out-of-order source versions must remain attributable evidence, not overwrite newer state blindly. A tenant inside payload bytes is still a claim: ingestion needs an enforced trusted source/tenant binding or must block it. Future correlation/recovery cannot authorize actions from unvalidated payload identity. See [Kafka design](https://kafka.apache.org/41/design/design/).

## P0.3-A: local Kafka infrastructure and verification

`compose.kafka.yaml` adds a companion stack that can run independently or merge with the existing Compose file. It leaves the four PostgreSQL services, networks and volumes unchanged. The broker is pinned to `apache/kafka:4.2.2@sha256:1213eb3943d551e5ed1fca7a4e109001cee35770b66a02a0c37a8964efe09b69`; one process acts as broker and KRaft controller, so ZooKeeper is unnecessary. One broker and replication factor one are a small local development choice, not high availability.

The actual new paths are:

```text
compose.kafka.yaml                         # broker, readiness, provisioning, dedicated network/volume
scripts/
├── start-kafka.ps1                         # normal local startup; creates ignored .env.kafka once
├── verify-kafka.ps1                        # isolated broker and both listener smoke checks
├── build-kafka-smoke.ps1                   # Java 21 build with repository-local Maven cache
├── test-kafka-infrastructure.ps1           # infrastructure-free guard checks
├── lib/kafka-infrastructure.ps1            # configuration, environment and cleanup guards
└── kafka/
    ├── server.properties                  # KRaft, SASL, default-deny authorizer, topic defaults
    ├── common.sh / startup.sh             # private generated configuration and broker startup
    ├── healthcheck.sh                     # authenticated broker readiness
    └── provision.sh                       # repeatable topic creation and additive source/Core ACLs
tools/kafka-smoke/
├── pom.xml                                # standalone child of root parent; outside reactor modules
└── src/main/java/com/aegisflow/tools/kafka/KafkaSmoke.java
```

Kafka's bootstrap address is only the first contact. The broker then returns its advertised address, which must be reachable from that caller. The `HOST` listener binds container port 19092 and advertises `127.0.0.1:<KAFKA_HOST_PORT>`; the ordinary host default is 19092, while the rehearsal selects an available loopback port. Containers on `kafka-events` use the `INTERNAL` listener at `kafka:9092`. The controller endpoint `kafka:9093` is private to that Docker network and has no host port. The Kafka network is separate from the owner database networks; clients do not need database network membership for this smoke test.

Six distinct generated passwords belong to `broker`, `admin`, `order`, `payment`, `warehouse`, and `core`. Local SASL/PLAIN authenticates these principals, but SASL_PLAINTEXT does not encrypt their traffic: this configuration is for synthetic local development. Broker/admin privileges are infrastructure-only. Each source principal may write/describe its own `aegisflow.<source>.events.v1` topic and use producer idempotence. Core may read/describe those three topics and use groups starting with `aegisflow-core-`; it receives no topic-write grant. The broker denies operations without an ACL. These source boundaries do not authenticate a `tenantId` inside event bytes; trusted tenant provenance and fail-closed intake still belong to the unfinished inbox work.

Provisioning creates `aegisflow.order.events.v1`, `aegisflow.payment.events.v1`, and `aegisflow.warehouse.events.v1`, each with three partitions, one replica, minimum in-sync replicas one, seven-day delete retention and a 1 MiB message limit. Automatic topic creation is disabled. Repeated provisioning creates missing topics and adds ACLs; it does not silently change existing topic settings or remove ACLs. The smoke client checks settings against the expected contract.

The traceable verification flow is `verify-kafka.ps1` → isolated Compose broker → authenticated health check → `provision.sh` → host `KafkaSmoke` → container `KafkaSmoke`. The Java client checks advertised metadata and topic configuration, sends one unique synthetic record per authorized source with `acks=all` and idempotence, then reads those exact acknowledged offsets as Core. It disables auto-commit, commits the consumed offsets explicitly, and reads them back from the coordinator. It also requires explicit Kafka authorization rejection for six sibling writes and a Core write, plus authentication rejection for an incorrect password. A timeout cannot satisfy a denial assertion. This is transport verification; the synthetic client does not consume real outbox rows or write a Core inbox.

From the repository root in PowerShell 7 with `JAVA_HOME` pointing to Java 21:

```powershell
# Infrastructure-free build and guard checks:
.\scripts\build-kafka-smoke.ps1
.\scripts\test-kafka-infrastructure.ps1
& "$env:JAVA_HOME\bin\java.exe" -cp 'tools/kafka-smoke/target/classes;tools/kafka-smoke/target/lib/*' com.aegisflow.tools.kafka.KafkaSmoke --self-test
.\scripts\verify-kafka.ps1 -StaticOnly

# Actual isolated startup, provisioning and both listener tests; Docker engine required:
.\scripts\verify-kafka.ps1

# After verification, optional normal local Kafka startup:
.\scripts\start-kafka.ps1
```

The standalone Maven package build passed on 2026-10-17 at 14:47:59 UTC in 13.935 seconds using Java 21 and Boot-managed Kafka clients 4.2.1. The 10 PowerShell guard groups and 15 Java guard checks passed at 14:49:50 UTC. The sandbox rehearsal first failed at Docker engine access, project `aegisflow-kafka-verify-640d32dcef43411695f40b0f0b8e953b`. The later owner's host run passed at16:21:53Z, project `aegisflow-kafka-verify-ff292985977442009ee4e3fd306c28a6`; its report and both smoke logs confirm broker readiness, repeated provisioning and both routes' exact markers/offset commits and denial checks. These are real transport checks, not business relay/inbox or crash tests. Reports remain ignored under `.cache/kafka/<project>/`; verification containers stopped, Kafka volume retained and PostgreSQL untouched. See [test evidence](../evidence/TEST_RESULTS.md) for full outcomes.

### Kafka debugging exercise: bootstrap succeeds but later traffic cannot connect

On a disposable rehearsal, examine the sanitized listener smoke log and compare `scripts/kafka/startup.sh` with `KafkaSmoke.verifyMetadata`. A host can reach its bootstrap port yet receive `kafka:9092` in metadata, an address intended for Docker DNS. Predict the result: the host smoke must reject the advertised host/port, and other clients may fail subsequent requests even though the initial socket connected. The correct host metadata is `127.0.0.1:<selected host port>`; the container metadata is `kafka:9092`. Keep any intentional fault injection confined to a disposable setup, then restore the expected metadata and rerun the isolated smoke. Increasing a timeout does not repair an unreachable advertised address. This is a debugging exercise, not a recorded fault-test pass.

### Q26. Why does this broker need two client listeners?

The host and containers resolve different addresses. Host applications reach a loopback-published port; containers reach the Docker service name `kafka`. Kafka returns a listener-specific address after bootstrap, so changing only the initial bootstrap URL cannot correct a wrong advertised listener. `KafkaSmoke.verifyMetadata` checks that returned address before sending records.

### Q27. What does a single-node KRaft broker simplify, and what does it give up?

The same process stores Kafka records and participates in the metadata quorum, avoiding a separate ZooKeeper deployment. With only one broker/controller and replication factor one, an outage stops service and there is no second replica to survive storage loss. `acks=all` and minimum ISR one still mean only one in-sync replica here; they do not make this development stack highly available.

### Q28. How do source ACLs differ from tenant provenance?

An Order credential can publish only to the Order topic, so broker authorization constrains source identity. It does not inspect or authorize a tenant field in arbitrary JSON. The later Core inbox must validate the envelope and trusted tenant/source binding, or refuse intake; accepting `tenantId` solely because a source authenticated would weaken tenant isolation.

### Q29. Why explicitly provision topics instead of enabling automatic creation?

Explicit provisioning records the intended partitions, replicas, retention and size limits and avoids a misspelled name quietly creating another topic. Repeating the current bootstrap preserves existing data and settings; the verification client detects configuration drift. Topic creation success alone does not demonstrate correct source permissions or consumer delivery, so those receive separate real checks.

### Q30. Why do a smoke-test timeout and an authorization rejection mean different things?

A `TopicAuthorizationException` or `SaslAuthenticationException` is an explicit broker/client authentication outcome. A timeout can mean an unreachable listener, an unavailable broker or an unknown operation result, so it cannot prove that a forbidden write was denied. The client tests this distinction without infrastructure and requires the specific denial during a real run. Its committed-offset check proves a transport observation, not an atomic PostgreSQL inbox transaction or exactly-once business effect.

## Historical Phase 0 design concepts

The following material is retained from the documentation-only phase. The local simulator state/outbox write is now implemented in source; the remaining distributed flow is still planned and unverified.

### Planned end-to-end business flow

1. Order writes business state and an outbox event in one PostgreSQL transaction.
2. An asynchronous publisher sends the versioned envelope to Kafka. A retry keeps the same `eventId`.
3. AegisFlow consumes the event and, in one local transaction, inserts an inbox key and updates its tenant-scoped projection.
4. The invariant evaluator uses facts plus a durable deadline to decide whether an `INV-*` rule is violated.
5. Incident creation is protected by a unique invariant-instance key; evidence records point to the source event/API observation.
6. Deterministic policy and an exact human approval gate a typed API action.
7. An action timeout records “unknown,” and a separate authoritative read decides whether to retry, review, or resolve.

### 1. Transactional outbox

Writing a database row and publishing to Kafka are two different systems, so one can succeed while the other fails. An outbox stores the event beside the business change in the same database transaction. A later publisher may retry it. The trade-off is duplicate delivery and extra cleanup/monitoring, so consumers need idempotency.

### 2. Inbox idempotency

“Idempotent” means repeating an input has the same business result as doing it once. AegisFlow will insert a unique event receipt and its projection change in the same database transaction. A database constraint makes two workers racing on the same event safe; an in-memory set would fail after restart or across replicas.

### 3. Event ordering and time

Kafka orders records only inside a partition, and distributed facts may still be produced or observed late. The projection must compare business versions/timestamps and evaluate missing-event deadlines after an allowed-lateness window. “Not seen yet” is not the same as “did not happen.”

### 4. Unknown remote outcome

An HTTP timeout says only that the caller did not receive a response. The server may have committed the action. AegisFlow queries authoritative state and operation status, then permits a same-key retry only when the service's validated idempotency and precondition contract makes it safe. A missing resource alone does not prove that a delayed request cannot still commit.

### 5. AI as evidence-bounded advice

A model is good at organizing messy evidence but can invent claims or follow malicious text in logs. AegisFlow will validate its structured output and citations. Code—not the model—controls identity, tenant, allowed action types, approval, retries, and execution. The deterministic investigator remains the comparison baseline and outage fallback.

### Historical Phase 0 debugging exercise: trace a timeout-after-success

1. In `evaluation/FAILURE_MATRIX.md`, find the warehouse “timeout after success” scenario.
2. Follow its expected incident/action states into `architecture/RECOVERY_STATE_MACHINE.md`.
3. Confirm that `architecture/DATA_MODEL.md` can retain request fingerprint, idempotency key, unknown outcome, claim owner/lease, and verification observation after a crash.
4. Confirm the API/event fields needed to query the authoritative reservation in `architecture/EVENT_CONTRACTS.md`.
5. Confirm `security/THREAT_MODEL.md` prevents an AI response or stale approval from forcing a retry.

Expected design observation: a timeout must enter `VERIFYING`; it must not transition directly to `FAILED` (which invites a duplicate retry) or `RESOLVED` (which trusts an absent response).

## Historical Phase 0 interview questions and answers

### Q1. Why use a modular monolith for AegisFlow but separate simulator services?

The control plane's features need strong internal consistency and are maintained by one owner, so in-process modules reduce deployment and failure complexity. Separate simulators preserve the important network, database-ownership, timeout, and event-delivery boundaries the project exists to study. This is a deliberate split, not “microservices everywhere.”

### Q2. Why can Kafka's producer idempotence not guarantee exactly-once business recovery?

Kafka can prevent certain duplicate records and atomically handle Kafka reads/writes, but a warehouse or payment HTTP effect is outside Kafka's transaction. A crash or lost response can leave the external effect committed while local state is uncertain. The system therefore assumes at-least-once messages and uses service idempotency keys, database uniqueness, state preconditions, and reconciliation.

### Q3. What is the difference between correlation and authorization?

Correlation groups facts that belong to a business transaction. Authorization decides whether an actor may access or change them. A matching order ID or correlation ID is never proof of tenant access; every storage and API operation must also carry and enforce the authenticated tenant.

### Q4. Why bind an approval to hashes and versions?

An approval for one proposal must not be reusable after its parameters, evidence, policy, tenant, or incident state changes. Canonical hashes and versions make that exact decision immutable and machine-checkable. Expiry and single-use consumption further prevent replay.

### Q5. How do you evaluate whether AI adds value here?

First freeze incidents, evidence, expected findings/actions, and the deterministic investigator's result. Then measure AI accuracy, citation validity, unsafe proposals, abstention when evidence is insufficient, latency, token use, and cost on the same versioned cases. Event detection and recovery safety tests remain separate, so a fluent model answer cannot hide a broken distributed system.

### Q6. What happens if PostgreSQL commits but Kafka is unavailable?

The business change and its outbox row are already durable in the same PostgreSQL transaction. The relay retains the row, backs off, and publishes it when Kafka returns. A crash after broker acknowledgement may cause a duplicate publish, so the consumer's durable inbox still matters.

### Q7. How would you stop two recovery workers from executing the same action?

Use one conditional database claim with a lease and increasing fencing token. Only the current token can finalize local work. Because a paused worker can still send a late HTTP request, the simulator also enforces the stable action idempotency key and expected resource version. A lease alone is insufficient.

### Q8. How do you detect a missing event without generating false alarms for ordinary delays?

Persist the base business deadline and add the contract's allowed-lateness interval exactly once to compute the enforcement time. Use a controllable clock in tests. After the window, open an incident about the missing outcome and query authoritative state; event absence by itself cannot justify a repair.

### Q9. Why does an HTTP 200 response not resolve the incident?

It reports the result of one command, which may still be insufficient for the business invariant. A fresh independent query checks the owning service's current state and version, records evidence, and evaluates the required postcondition. Contradictory or unavailable state leaves the incident in review instead of reporting success.

### Q10. What makes a useful integration test different from a unit test here?

A unit test can exercise a transition table or deadline decision without infrastructure. An integration test proves a real boundary: PostgreSQL uniqueness under concurrent inserts, transaction rollback, Kafka redelivery, or the simulator's idempotency record after a lost response. Both are needed; mocking those boundaries alone cannot prove their crash behavior.

## Five new P0.1 Java backend interview questions and answers

### Q11. What is the difference between a Maven parent POM and an aggregator POM here?

A parent supplies inherited configuration such as Java 21, dependency management, and Enforcer rules. An aggregator lists `<modules>` so one reactor command builds the four projects in dependency order. The root `pom.xml` performs both roles, but those are separate Maven concepts.

### Q12. What does `@SpringBootApplication` do, and why does its package matter?

It combines configuration, auto-configuration, and component scanning. Scanning begins at the annotated class's package and descends into subpackages. Keeping each simulator entry point in its own package prevents ordinary component scanning from merging sibling services, while the control-plane entry point defines the root that Modulith analyzes.

### Q13. Why combine `@SpringBootTest(RANDOM_PORT)` with `@LocalServerPort`?

`RANDOM_PORT` starts the actual embedded web stack on an available port, which exercises HTTP serialization, routing, and Actuator configuration without a fixed-port collision. `@LocalServerPort` supplies the selected port to the test after startup. A mocked MVC test would be faster but would not prove that the packaged web application can bind and answer over HTTP.

### Q14. Why have a no-database `foundation` profile when JDBC and Flyway are dependencies?

Dependencies describe capabilities available to the artifact; a profile selects which runtime contract is active. Excluding data-source/Flyway auto-configuration lets the P0.1 shell start reproducibly before infrastructure exists. The trade-off is that foundation tests cannot prove database wiring or migrations, so the separate Testcontainers integration test and later PostgreSQL-profile tests are essential.

### Q15. How does this foundation enforce service and module boundaries?

It uses several independent layers: Maven modules avoid compile-time implementation sharing; Enforcer bans deployable artifacts as dependencies; package placement limits component scanning; Spring Modulith verifies ten control-plane modules and their allowed dependencies; and ArchUnit/bytecode tests reject forbidden package references. No single mechanism proves everything, so the layers cover different accidental-coupling paths.

## Honest next step

P0.4-A now needs actual PostgreSQL proof through `verify-projection.ps1`;43 focused tests do not replace it. After that gate, implement B clock-driven INV-002 and prove restart/backlog freshness before C durable incidents/evidence, then D full integration. Intake acceptance is not an incident or an authoritative business outcome. Stop before P0.5.

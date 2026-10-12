# Codebase guide

## Read this first: what exists at P0.1

P0.1 contains a compilable **application foundation**, not business functionality. There are four Spring Boot deployables, Maven build rules, health-only runtime configuration, foundation tests, and explicit control-plane module boundaries. There are still no order/payment/warehouse business APIs, database migrations, event contracts in Java, Kafka producers or consumers, incident detection, recovery actions, authentication, or AI integration.

The Windows/Java 21 reactor build has passed all 14 foundation tests, and all four executable JARs have answered HTTP health checks. PostgreSQL integration remains unverified because its Docker-dependent test was skipped. The factual commands, failures, skips, and scope of these observations are in [`docs/evidence/TEST_RESULTS.md`](../evidence/TEST_RESULTS.md).

## Actual repository map

```text
aegisflow/
├── pom.xml                                  # parent + four-module Maven reactor
├── mvnw / mvnw.cmd                         # POSIX / Windows Maven wrappers
├── .mvn/wrapper/maven-wrapper.properties   # Maven 3.10.0, wrapper 3.3.4, SHA-256
├── compose.yaml / .env.example             # optional four-database local infrastructure
├── scripts/verify-startup.ps1              # Windows executable-JAR health verification
├── scripts/verify-docs.ps1                 # continuity-document consistency check
├── control-plane/
│   ├── pom.xml                              # MVC, Actuator, JDBC/Flyway, Modulith, tests
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
│       │   └── application-postgres.yaml
│       └── test/java/com/aegisflow/core/
│           ├── AegisFlowCoreApplicationTests.java
│           ├── ControlPlaneArchitectureTests.java
│           └── PostgresInfrastructureIT.java
├── simulators/
│   ├── order-service/
│   ├── payment-service/
│   └── warehouse-service/
│       # each simulator has its own pom.xml, Application class,
│       # three application*.yaml files, HTTP test, and architecture test
├── docs/                                    # architecture, ADRs, evidence, handoff, learning
└── AGENTS.md                                # continuity and safety protocol
```

The source of truth is the repository, not this abbreviated tree. In particular, each simulator lives in a separate Maven module and Java package; it is not an in-process control-plane package.

The concrete path index is:

| Module | POM and main source | Runtime resources | Foundation tests |
|---|---|---|---|
| Control plane | `control-plane/pom.xml`; `control-plane/src/main/java/com/aegisflow/core/AegisFlowCoreApplication.java` | `control-plane/src/main/resources/application*.yaml` | `control-plane/src/test/java/com/aegisflow/core/{AegisFlowCoreApplicationTests,ControlPlaneArchitectureTests,PostgresInfrastructureIT}.java` |
| Order | `simulators/order-service/pom.xml`; `simulators/order-service/src/main/java/com/aegisflow/simulator/order/OrderSimulatorApplication.java` | `simulators/order-service/src/main/resources/application*.yaml` | `simulators/order-service/src/test/java/com/aegisflow/simulator/order/{OrderSimulatorApplicationTests,OrderSimulatorArchitectureTest}.java` |
| Payment | `simulators/payment-service/pom.xml`; `simulators/payment-service/src/main/java/com/aegisflow/simulator/payment/PaymentSimulatorApplication.java` | `simulators/payment-service/src/main/resources/application*.yaml` | `simulators/payment-service/src/test/java/com/aegisflow/simulator/payment/{PaymentSimulatorApplicationTests,PaymentSimulatorArchitectureTest}.java` |
| Warehouse | `simulators/warehouse-service/pom.xml`; `simulators/warehouse-service/src/main/java/com/aegisflow/simulator/warehouse/WarehouseSimulatorApplication.java` | `simulators/warehouse-service/src/main/resources/application*.yaml` | `simulators/warehouse-service/src/test/java/com/aegisflow/simulator/warehouse/{WarehouseSimulatorApplicationTests,WarehouseSimulatorArchitectureTest}.java` |

## Maven reactor and four deployables

The root `pom.xml` is both the parent POM and the aggregator. Parent inheritance gives all children one Java and dependency baseline; aggregation tells Maven which modules to build together.

| Maven module | Artifact and entry point | Default address | Foundation responsibility |
|---|---|---|---|
| `control-plane` | `aegisflow-core`; `com.aegisflow.core.AegisFlowCoreApplication` | `127.0.0.1:8080` | Hosts the modular AegisFlow control plane. |
| `simulators/order-service` | `order-service`; `com.aegisflow.simulator.order.OrderSimulatorApplication` | `127.0.0.1:8081` | Independent shell for the future order system. |
| `simulators/payment-service` | `payment-service`; `com.aegisflow.simulator.payment.PaymentSimulatorApplication` | `127.0.0.1:8082` | Independent shell for the future payment system. |
| `simulators/warehouse-service` | `warehouse-service`; `com.aegisflow.simulator.warehouse.WarehouseSimulatorApplication` | `127.0.0.1:8083` | Independent shell for the future warehouse system. |

The root build pins Spring Boot `4.1.1`, compiles with Java release `21`, and imports Spring Modulith BOM `2.1.1`. Maven Enforcer requires Java `[21,22)` and Maven `[3.10.0,3.11.0)`. The wrapper properties select Maven `3.10.0`, wrapper implementation/plugin `3.3.4`, and a distribution checksum. The distinction matters: `3.3.4` is the Maven Wrapper version, while `3.10.0` is the Maven distribution that executes the build.

The child POMs deliberately share web MVC, Actuator, JDBC, Flyway, PostgreSQL runtime support, tests, and the Spring Boot Maven plugin. Only the control plane currently adds Spring Modulith and Testcontainers dependencies. Spring AI, Kafka, and Spring Security are not dependencies yet because P0.1 does not implement their behavior.

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
- `application-foundation.yaml` excludes data-source and Flyway auto-configuration. This lets the process and HTTP tests start without pretending a database exists.
- `application-postgres.yaml` opts into that deployable's own PostgreSQL URL, username, password, and Flyway location.

| Deployable | PostgreSQL URL default | Required password variable |
|---|---|---|
| Control plane | `jdbc:postgresql://127.0.0.1:5433/aegisflow_core` | `CORE_DB_PASSWORD` |
| Order | `jdbc:postgresql://127.0.0.1:5434/aegisflow_order` | `ORDER_DB_PASSWORD` |
| Payment | `jdbc:postgresql://127.0.0.1:5435/aegisflow_payment` | `PAYMENT_DB_PASSWORD` |
| Warehouse | `jdbc:postgresql://127.0.0.1:5436/aegisflow_warehouse` | `WAREHOUSE_DB_PASSWORD` |

The `postgres` profile is optional in P0.1 and must be selected explicitly. Its configuration does not create PostgreSQL or its databases. There are no business migration files yet; `fail-on-missing-locations: false` is temporary foundation behavior, and `PostgresInfrastructureIT` is limited to JDBC/Flyway connectivity.

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
.\mvnw.cmd verify
.\mvnw.cmd -pl control-plane -am test
.\mvnw.cmd -pl control-plane -Pinfrastructure-tests verify
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
./mvnw verify
./mvnw -pl control-plane -am test
./mvnw -pl control-plane -Pinfrastructure-tests verify

# Start one foundation app; stop it with Ctrl+C.
./mvnw -pl control-plane spring-boot:run
curl --fail --silent http://127.0.0.1:8080/actuator/health

# Equivalent simulator example.
./mvnw -pl simulators/order-service spring-boot:run
curl --fail --silent http://127.0.0.1:8081/actuator/health
```

The infrastructure command uses Testcontainers and therefore requires a usable Docker-compatible runtime. Whether it ran, failed, or was skipped must be read from [`docs/evidence/TEST_RESULTS.md`](../evidence/TEST_RESULTS.md), not assumed here.

To prepare optional local databases, copy `.env.example` to an ignored `.env`, fill all four passwords privately, and use `docker compose --profile postgres config --quiet` before `docker compose --profile postgres up -d --wait`. Do not print the expanded Compose configuration because it contains credentials. `docker compose --profile postgres down` stops containers without deleting volumes; do not use `down -v` unless deliberately discarding local data.

Compose reads `.env`, but an independently launched Java process does **not** automatically load that file. Supply only that application's own credentials through its environment. For example, after preparing its database, prompt locally for the Core password:

```powershell
$env:SPRING_PROFILES_ACTIVE = "postgres"
$env:CORE_DB_PASSWORD = Read-Host "Local Core database password" -MaskInput
.\mvnw.cmd -pl control-plane spring-boot:run
```

```bash
read -r -s -p 'Local Core database password: ' CORE_DB_PASSWORD
export CORE_DB_PASSWORD
SPRING_PROFILES_ACTIVE=postgres ./mvnw -pl control-plane spring-boot:run
unset CORE_DB_PASSWORD
```

## Practical debugging exercise: identify a profile failure

Goal: distinguish “the application foundation is broken” from “optional infrastructure was selected but is unavailable.”

1. Start the control plane with no active-profile override. Confirm from the startup log that `foundation` is active, then request `/actuator/health`.
2. Stop it. Set `SPRING_PROFILES_ACTIVE=postgres` but deliberately omit `CORE_DB_PASSWORD`, then start it again.
3. Read the earliest useful cause in the exception chain. Look for unresolved database configuration before changing Java code.
4. Supply a non-secret local value but leave PostgreSQL stopped. Compare the new failure: configuration can now resolve, but the connection should be unavailable.
5. Remove the environment variables and return to `foundation`.

The learning point is that profiles select different runtime contracts. The default profile proves the Boot shell and health route without infrastructure; the PostgreSQL profile promises that the named database and credential exist. Do not “fix” the latter by hard-coding a password or disabling Flyway. Record actual observations in the evidence file because exact diagnostics depend on the local runtime.

## Historical Phase 0 design concepts

The following material is retained from the documentation-only phase. It explains the intended distributed-systems behavior; it does **not** claim that P0.1 implements it.

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

After new owner approval, P0.2 adds service-owned persistence, restricted runtime/migration roles, Flyway migrations, and one atomic business-change/outbox path. Kafka transport starts in P0.3, not this foundation. First unblock Docker and run the PostgreSQL check without a skip. Update this guide with actual classes, migrations, flow, and results at that milestone. Until then, the repository proves only a foundation.

# Codebase guide

**2026-10-16 verification update:** the host passed five Core default tests and connected Testcontainers to Docker. A PostgreSQL image declaration then failed before database startup. All four ITs now explicitly declare the unchanged digest pin compatible with `postgres`. The new `control-plane/src/test/java/com/aegisflow/core/PostgresImageConfigurationTests.java` initializes the actual Core IT's container without Docker and passed its canonical-reference/compatibility assertion. It cannot prove database startup or transactions; the next step is the normal host persistence script.

## Read this first: the P0.2 checkpoint

P0.1's four application foundations and ten control-plane module boundaries are preserved. P0.2 now has source for independent Flyway migrations, database role bootstrap, and local transactional state/idempotency/outbox paths in Order, Payment, and Warehouse. These application methods are Java boundaries for later HTTP workflows; no business HTTP controller, authentication, Kafka publisher/consumer, incident detection, recovery, or AI behavior exists.

This is an implementation checkpoint, not a completed PostgreSQL verification gate. The interrupted-session recovery found existing progress and continued it in the original repository. Real PostgreSQL behavior remains unverified until the Docker-dependent tests execute successfully. The first resumed offline reactor run also encountered a restricted-host loopback connection error in the Core HTTP test; passing P0.1 evidence remains historical evidence, not a substitute for a P0.2 rerun. Read [`docs/evidence/TEST_RESULTS.md`](../evidence/TEST_RESULTS.md) for the final dated commands, test counts, failures, and unexecuted checks.

On 2026-10-13, an isolated cached-library diagnostic ran the actual pure validation/canonical-envelope test classes: **21 passed, zero failures/errors/skips**. A focused Core run passed **4 architecture tests**, while its **4 PostgreSQL ITs skipped** because Docker's pipe was denied. Compose configuration and script syntax passed. Full simulator compilation is blocked by the uncached Boot-managed JPA starter and Maven Central DNS; JPA/transaction/database/startup behavior is still unverified. The standard local gate remains `scripts/verify-persistence.ps1`, which rejects skipped integration tests.

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

The child POMs share web MVC, Actuator, Flyway, PostgreSQL runtime support, tests, and the Spring Boot Maven plugin. Core retains JDBC and adds Spring Modulith. The three simulators now use `spring-boot-starter-data-jpa`; all four modules have PostgreSQL/JUnit Testcontainers dependencies. Boot's Flyway starter supplies the auto-configuration, while `flyway-database-postgresql` supplies PostgreSQL support. The root `infrastructure-tests` profile activates Maven Failsafe for `*IT` classes throughout the reactor. Spring AI, Kafka, and Spring Security remain absent.

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

The runtime account has table-specific business grants and only publication-metadata update grants on the outbox. The migrator owns schema changes and history. The owner remains administration-only. These rules exist in SQL and have assertion tests; successful PostgreSQL execution is still required before describing their isolation as demonstrated security.

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

Prerequisite: Java 21, an accessible Docker engine, and an IDE running the actual Testcontainers integration test. This exercise is runnable test design until local PostgreSQL verification succeeds; do not label its expected observations as observed evidence.

1. Open `simulators/order-service/src/test/java/com/aegisflow/simulator/order/persistence/OrderPersistenceIT.java` and select `failureAfterOutboxInsertRollsBackAllRows`.
2. Set a breakpoint after `OrderOutbox.append` executes its `JdbcTemplate.update`. The spy in the test calls the real append, then throws `IllegalStateException` before the transaction commits.
3. From the transactional connection, the order, line, idempotency claim, and outbox have been inserted. A separate connection must not see these uncommitted rows; do not mistake that invisibility for evidence that the inserts never ran.
4. Continue past the injected exception. The test calls the proxied service, catches the escaped failure, then queries the database after rollback. Counts for `orders`, `order_lines`, `idempotency_record`, and `outbox_event` under the test tenant must all be `0`.
5. Run `commitPersistsStateAndContractEnvelopeTogether` separately. The success path must retain business state and one matching event. Record the actual run, Docker environment, counts, and exit status in the evidence document.

If rows remain after the failure, first check proxy invocation, the `DataSource` shared by JPA/JDBC, and accidental independent transactions/connections. Do not move the outbox insert outside the transaction or suppress the exception to make the test pass.

## Five P0.2 backend interview questions and answers

### Q16. Why combine Spring Data JPA with JdbcTemplate in one transaction?

JPA/Hibernate provides entity mapping and optimistic locking for aggregate state. Explicit SQL gives PostgreSQL's conflict handling for a durable idempotency claim. With one application `DataSource` and Spring's JPA transaction manager, both participate in the same local transaction. Separate connections or `REQUIRES_NEW` would undermine the state/outbox rollback guarantee. The integration tests must prove that wiring against real PostgreSQL.

### Q17. Why flush an entity before emitting its version, and why is the event version one higher?

Hibernate initializes the new entity's optimistic lock version when it persists/flushes. The SQL token begins at `0`, while the business/event contract begins at `1`, so the code explicitly exposes `lockVersion + 1`. `saveAndFlush` reveals generated version state and early SQL errors without committing. The surrounding transaction still owns commit or rollback.

### Q18. How does ON CONFLICT make two same-key commands safe?

The unique `(tenant, operation, key)` index arbitrates competing claims inside PostgreSQL. The loser does not create another aggregate; it reads the winner's committed result and compares the canonical material request hash. A changed request conflicts, an equal request returns the same resource/version/event ID, and a rolled-back winner leaves no durable claim. This is stronger than a process-local cache, but it still needs concurrency and rollback tests.

### Q19. Why does each deployable need separate Flyway history and migration credentials?

Each service owns one database, including its schema evolution. Separate histories avoid coupling deployment order or schema versions across services. The migrator may change tables and history; the runtime may use only explicit business/publication-metadata grants. Core has no simulator credentials or database access path. The SQL is implemented, while real role-isolation assertions remain part of the unpassed PostgreSQL gate.

### Q20. What does a successful transactional outbox write guarantee?

It makes the local business change and immutable publication intent durable together, or rolls them back together. It does not prove Kafka received anything, authenticate the tenant, or prevent every external duplicate effect. A later relay must preserve event identity across retries, and consumers/recovery APIs need their own durable idempotency and authoritative verification.

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

Finish the documented P0.2 verification gate in an environment with Java 21, usable loopback HTTP, and Docker. `scripts/verify-persistence.ps1` requires an executed PostgreSQL test suite with zero failures/errors/skips; follow it with `scripts/verify-startup.ps1` for the packaged foundation processes and the documented Compose/PostgreSQL-profile startup checks. Use the handoff's exact next task and preserve existing progress. P0.3 remains unapproved; Kafka transport requires a new owner decision after P0.2 evidence is complete.

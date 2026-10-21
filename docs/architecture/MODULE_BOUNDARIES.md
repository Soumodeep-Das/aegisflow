# Module boundaries — foundation through P0.4-B

P0.5-A packaging adds no application module/deployable dependency. docker/HealthProbe.java is a standalone JDK-only image health tool, compiled outside the reactor and not imported by Core or simulators. Each staged image receives exactly its own executable archive; the four mappings validate Start-Class and class presence. Persistent apps will retain owner-only DB networks/credentials and Kafka event connectivity; that overlay is still B/C work. Existing domain and Modulith boundaries remain unchanged.

**Current,2026-10-21:** ten modules remain. Eventing intake and correlation A are verified. B detection is implemented with18 focused tests passed, real PostgreSQL gate pending. Evidence and other business modules remain placeholders. Detection depends only on correlation/eventing public APIs; correlation only on eventing; eight others deny module dependencies.

## Deployable boundaries versus Java modules

The root [`pom.xml`](../../pom.xml) is both an aggregator (builds four children) and parent (shares Java/dependency/plugin rules). It is not a fifth running application. Four child modules produce separate Spring Boot executable JARs:

| Maven module | Main class | Java base package |
|---|---|---|
| `control-plane` | [AegisFlowCoreApplication](../../control-plane/src/main/java/com/aegisflow/core/AegisFlowCoreApplication.java) | `com.aegisflow.core` |
| `simulators/order-service` | [OrderSimulatorApplication](../../simulators/order-service/src/main/java/com/aegisflow/simulator/order/OrderSimulatorApplication.java) | `com.aegisflow.simulator.order` |
| `simulators/payment-service` | [PaymentSimulatorApplication](../../simulators/payment-service/src/main/java/com/aegisflow/simulator/payment/PaymentSimulatorApplication.java) | `com.aegisflow.simulator.payment` |
| `simulators/warehouse-service` | [WarehouseSimulatorApplication](../../simulators/warehouse-service/src/main/java/com/aegisflow/simulator/warehouse/WarehouseSimulatorApplication.java) | `com.aegisflow.simulator.warehouse` |

Sharing a Maven parent does not share Java implementation classes. Each application has its own source tree and runtime classpath. The inherited Maven Enforcer `bannedDependencies` rule rejects direct or transitive dependencies on any of the four deployable artifacts. Simulator-to-Core communication will use versioned APIs/events, not a dependency on Core's domain classes. No shared contract artifact is needed for empty application shells.

Inside Core, Spring Modulith recognizes packages immediately below `com.aegisflow.core`. [`AegisFlowCoreApplication.java`](../../control-plane/src/main/java/com/aegisflow/core/AegisFlowCoreApplication.java) marks the application with `@Modulithic`; `@SpringBootApplication` supplies the component-scan root. The main class is bootstrap code, not another business module.

## Declared Core packages

Each directory below `control-plane/src/main/java/com/aegisflow/core/` contains a `package-info.java` and a package-private `FoundationModule.java` marker. The marker makes an otherwise empty package discoverable; it exposes no interface, bean, or business behavior.

| Package suffix | Intended owner of future behavior |
|---|---|
| `audit` | append-only transition/security audit |
| `correlation` | tenant-scoped transaction facts and projections |
| `detection` | invariant evaluations and incident creation |
| `eventing` | envelope validation, durable inbox, quarantine |
| `evidence` | provenance-rich authoritative observations |
| `identity` | tenant/actor/scopes resolution |
| `investigation` | deterministic and later AI-backed analysis |
| `operations` | operational read APIs and telemetry |
| `recovery` | policy, approval, claims, constrained API action |
| `verification` | independent postcondition checks |

For example, [`eventing/package-info.java`](../../control-plane/src/main/java/com/aegisflow/core/eventing/package-info.java) declares:

```java
@org.springframework.modulith.ApplicationModule(
        displayName = "Eventing",
        allowedDependencies = {})
package com.aegisflow.core.eventing;
```

The explicit empty `allowedDependencies` forbids module dependencies in eight modules. Correlation → eventing uses public ProjectionInbox, whose implementation stays internal. Detection → correlation/eventing uses public DetectionProjections for bounded snapshots and ProjectionInbox for shared gate/freshness. No cross-module internal import; this is not an unrestricted annotation default. Test-only fixture reuse is not a production dependency.

## Automated enforcement

[`ControlPlaneArchitectureTests.java`](../../control-plane/src/test/java/com/aegisflow/core/ControlPlaneArchitectureTests.java) has four tests:

1. `ApplicationModules.of(AegisFlowCoreApplication.class).verify()` checks cycles, access to module internals, and declared dependency rules.
2. Discovery must return exactly the ten documented module identifiers; a missing/new package cannot silently change the architecture.
3. Correlation declares only eventing; detection declares only correlation/eventing; all eight other allowlists remain empty. The resolved Modulith model must match.
4. ArchUnit imports compiled Core main classes and rejects references to simulator packages.

Each simulator's `src/test/java/com/aegisflow/simulator/<name>/<Name>SimulatorArchitectureTest.java` also scans its compiled main classes for forbidden Core/sibling package references. For example, [`OrderSimulatorArchitectureTest.java`](../../simulators/order-service/src/test/java/com/aegisflow/simulator/order/OrderSimulatorArchitectureTest.java) rejects Core, Payment, and Warehouse references. This small bytecode check supplements the Maven artifact ban; it is not a complete runtime isolation or credential test.

Run from repository root with Java 21:

```powershell
.\mvnw.cmd -B -ntp verify
# Core architecture tests alone:
.\mvnw.cmd -B -ntp -pl control-plane -Dtest=ControlPlaneArchitectureTests test
```

On POSIX use `./mvnw` instead. The full reactor passed 14 tests on Windows, including four Core architecture tests and one architecture test per simulator. All application-context tests explicitly activate `foundation`, so an inherited `SPRING_PROFILES_ACTIVE=postgres` cannot accidentally turn this gate into an infrastructure test. The optional Docker integration gate is separate.

## What these checks do not prove

Build-time boundaries prevent accidental code coupling; they do not sandbox JVM code, enforce tenant authorization, validate service credentials, or make empty modules functional. Actuator health currently comes from framework auto-configuration, not from domain code in `operations`. Separate database ownership is configured but runtime privilege isolation still requires P0.2 tests. Nothing in this foundation proves outbox delivery, Kafka durability, incident detection, safe recovery, or AI correctness.

Keep [ADR 0001](../adr/0001-modular-control-plane.md) and [ADR 0007](../adr/0007-foundation-profiles-and-stage-boundaries.md) as the decision trail. The next persistence milestone still requires owner approval.

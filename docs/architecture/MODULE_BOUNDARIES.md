# Application and module boundaries

Current P0.5 has four deployables and ten Core Java modules. [ADR 0001](../adr/0001-modular-control-plane.md) explains why internal Core concerns remain in one process.

## Deployables

The root [pom.xml](../../pom.xml) is a Maven parent and aggregator, not a fifth application. Each child produces its own executable JAR, source tree, configuration and classpath.

| Module | Main class |
|---|---|
| control-plane | [AegisFlowCoreApplication](../../control-plane/src/main/java/com/aegisflow/core/AegisFlowCoreApplication.java) |
| simulators/order-service | [OrderSimulatorApplication](../../simulators/order-service/src/main/java/com/aegisflow/simulator/order/OrderSimulatorApplication.java) |
| simulators/payment-service | [PaymentSimulatorApplication](../../simulators/payment-service/src/main/java/com/aegisflow/simulator/payment/PaymentSimulatorApplication.java) |
| simulators/warehouse-service | [WarehouseSimulatorApplication](../../simulators/warehouse-service/src/main/java/com/aegisflow/simulator/warehouse/WarehouseSimulatorApplication.java) |

No deployable imports another's implementation. Kafka carries versioned events; a shared domain-model dependency is not used. The local command driver invokes only its own service's business code.

## Core modules as built

Root packages under `com.aegisflow.core` are Modulith modules. `FoundationModule` markers keep placeholders discoverable; a marker is not an implemented service.

| Module | Implemented behavior | Public module API / allowed dependencies |
|---|---|---|
| eventing | Intake validation, immutable inbox/quarantine, projection work/progress adapter | [ProjectionInbox](../../control-plane/src/main/java/com/aegisflow/core/eventing/ProjectionInbox.java); no other Core modules |
| correlation | Immutable facts, reduction and tenant/transaction projections with pinned deadlines | [DetectionProjections](../../control-plane/src/main/java/com/aegisflow/core/correlation/DetectionProjections.java); depends only on eventing |
| detection | INV-002 evaluations, durable sweep, incidents/timelines/fact references | No exported application port; depends only on correlation and eventing |
| evidence | Placeholder for future authoritative observations | No dependencies |
| investigation | Placeholder for future deterministic/AI investigation | No dependencies |
| recovery | Placeholder for future policy/approval/API action | No dependencies |
| verification | Placeholder for future independent postcondition checks | No dependencies |
| identity | Placeholder for future tenant/actor/scopes resolution | No dependencies |
| audit | Placeholder for future transition/security audit | No dependencies |
| operations | Placeholder for future operational APIs/telemetry | No dependencies |

Persisted incident evidence belongs to **detection**, not the still-empty `evidence` module. Actuator health is framework configuration, not domain implementation in `operations`.

`correlation.internal.CorrelationProjector` uses `ProjectionInbox`; `detection.internal.DeadlineDetector` uses both public ports. Repositories/adapters stay internal to their module. `IncidentRecorder` reads immutable detection evaluations rather than importing correlation internals.

## Enforcement

[ControlPlaneArchitectureTests](../../control-plane/src/test/java/com/aegisflow/core/ControlPlaneArchitectureTests.java) verifies Modulith dependencies/cycles/internal access, exact ten-module discovery, declared allowlists and no simulator references. Simulator architecture tests reject Core/sibling references. Maven Enforcer bans dependencies on the deployable artifacts.

These are build-time boundaries, not JVM security isolation. Runtime owner networks/credentials and database grants supply separate deployment controls, verified in the recorded local gates. Core's shared process can still access its own injected capabilities.

For an intentional architecture check, not required for this documentation task:

```powershell
.\mvnw.cmd -B -ntp -pl control-plane -Dtest=ControlPlaneArchitectureTests test
```

The final default reactor passed 182 tests in total; that number is not the architecture-test count. See [evidence](../evidence/TEST_RESULTS.md#p05-acceptance-evidence). Foundation-profile HTTP tests do not substitute for PostgreSQL/Kafka or persistent-deployment acceptance.

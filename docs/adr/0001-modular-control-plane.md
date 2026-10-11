# ADR 0001: Modular Spring Boot Control Plane

- **Status:** Accepted for initial implementation
- **Date:** 2026-10-11
- **Decision owners:** AegisFlow project

## Context

The project needs durable ingestion, correlation, invariant detection, evidence collection, investigation, policy/approval, recovery, verification, identity, and audit behavior. These concerns need clear boundaries, but the student-maintained project has a six-to-seven-day Codex window and should optimize for a coherent vertical slice rather than operational service count. The Order, Payment, and Warehouse simulators must still fail independently and own separate data.

## Decision

Build AegisFlow itself as one Java 21 Spring Boot control-plane deployment with Spring Modulith-checked domain modules and one control-plane PostgreSQL database. Use package/module APIs and application events; prohibit cross-module repository access. Deploy the Order, Payment, and Warehouse simulators as separate Spring Boot processes with separate databases.

Use the documentation-verified bootstrap set in [ADR 0006](0006-technology-baseline-and-version-pinning.md): Spring Boot 4.1.1, Spring Modulith 2.1.1, and Spring AI 2.0.1 only when P2 begins. P0 must still prove the resolved build and integration compatibility; module architecture does not depend on pretending that documentation research is a passing build.

## Consequences

**Positive:** one build, transaction boundary, debugger, deployment, and integration-test harness; module boundaries remain visible; fewer distributed failure modes obscure the actual recovery problem; later extraction remains possible.

**Negative:** modules share process resources and a deployment cadence; a control-plane process failure affects all modules; careless code can bypass logical boundaries unless architecture tests enforce them.

## Guardrails

- Architecture tests validate allowed package dependencies.
- Each module owns its tables/repositories and exposes narrow interfaces.
- Long-running remote work does not hold database transactions open.
- Simulator databases/users are inaccessible to the control-plane role.
- Split a module only with evidence such as independent scaling, security isolation, ownership, or availability requirements.

## Alternatives considered

- **Control-plane microservice per concern:** rejected initially due to operational and consistency overhead without demonstrated benefit.
- **Single monolith including simulators:** rejected because it removes the ownership and failure boundaries the project must demonstrate.
- **Workflow engine first:** deferred; Temporal or similar is justified only after the explicit state machine exposes a proven orchestration need.

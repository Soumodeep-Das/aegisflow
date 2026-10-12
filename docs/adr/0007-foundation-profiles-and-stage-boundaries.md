# ADR 0007: P0.1 startup profiles and infrastructure boundary

- **Status:** Accepted within the owner's P0.1 authorization
- **Date:** 2026-10-12
- **Complements:** ADRs 0001, 0004, and 0006; no earlier decision is superseded

## Context and decision

P0.1 establishes four independently runnable applications before P0.2 introduces business persistence. A default `foundation` profile deliberately excludes JDBC and Flyway auto-configuration so health/startup and module tests can run without Docker. This profile contains no business endpoints, database substitutes, or mock commerce behavior. HTTP binds to loopback by default and only Actuator health is exposed.

Each application also has an explicit `postgres` profile with its own URL/username/password environment variables. `compose.yaml` provisions four separate PostgreSQL services, volumes, credentials, and networks; it contains no application deployment, Kafka, Keycloak, AI, or UI. PostgreSQL is pinned by image digest. No business tables or migrations exist yet. Flyway/JDBC dependencies are present and an optional Testcontainers connectivity test is separate from the default test gate.

Docker-dependent tests run with `-P infrastructure-tests`; unavailable Docker is reported as skipped, never as a pass. The test uses real PostgreSQL/JDBC/Flyway when Docker is available. Kafka connectivity belongs to P0.3 and remains a required later P0 acceptance gate under ADR 0006; this does not weaken the event durability design or invent a Kafka result for P0.1.

## Consequences

- The default build can verify all application contexts, HTTP health, and logical boundaries on Java 21 without infrastructure.
- A default health `UP` proves only process readiness. It does not prove database connectivity, business correctness, authentication, or Kafka readiness.
- Activating `postgres` opts into a required real database and password; misconfiguration must fail or show unhealthy status rather than fall back to an in-memory store.
- Local Compose database owners are bootstrap administrators for their separate instances. Before P0.2 persistence becomes application behavior, separate runtime/migration roles and restricted privileges must be designed and tested. Runtime Core never receives simulator database credentials.
- Only health is available in P0.1 and is loopback-bound. Keycloak/tenant authorization remains explicit future work; this foundation is not suitable for shared/public exposure.
- All ten planned Core modules are declared now, with no cross-module dependencies allowed. Package-private marker types make boundaries discoverable; no domain methods or invented interfaces are added. Introducing a real interaction requires an intentional dependency declaration and architecture/test update.

## Acceptance evidence

See `docs/evidence/TEST_RESULTS.md` for actual build, health/startup, dependency, boundary, and infrastructure-check outcomes. Business event handling, outbox migrations, recovery, AI, and external deployment remain outside this milestone.

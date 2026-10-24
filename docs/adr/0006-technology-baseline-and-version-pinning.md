# ADR 0006: technology baseline and version pinning

> **Implementation evidence update — 2026-10-24:** The current Java 21, Maven wrapper, Boot/Modulith and Kafka dependency/image selections are pinned in the repository. Candidate AI/frontend/observability technologies in the original baseline are not installed runtime capabilities. See [current status](../STATUS.md) and the [preserved evidence ledger](../evidence/TEST_RESULTS.md). The original decision, date, context and consequences below are unchanged.

- **Status:** Accepted for P0 implementation bootstrap; revalidate on implementation day
- **Date:** 2026-10-11
- **Decision owners:** repository owner / implementation lead
- **Related requirements:** NFR-008, NFR-009, NFR-010

## Context

The requested Spring generation is newer than many remembered compatibility tables, and “latest” versions from every upstream project do not necessarily form a tested stack. The first implementation needs reproducible coordinates that preserve Java-backend learning value without spending the limited project window on avoidable dependency drift.

Phase 0 therefore checked official project documentation and release information on 2026-10-11. This is a compatibility decision, not a claim that a build has already resolved or run; there is no POM yet.

## Decision

Use this P0 bootstrap baseline:

| Layer | P0 implementation choice | Management rule |
|---|---|---|
| Java | Java 21 LTS, compiler/toolchain release 21 | do not compile against a newer JDK API accidentally |
| Maven | Apache Maven 3.10.0, invoked through Maven Wrapper | pin the Maven distribution URL and checksum plus the wrapper scripts/JAR or wrapper-plugin version separately; the wrapper is the reproducible entry point, not the Maven distribution version |
| Spring Boot | `spring-boot-starter-parent` 4.1.1 | primary dependency/plugin management source |
| Spring Modulith | BOM 2.1.1 | import BOM; use boundary verification before runtime features |
| Spring AI | BOM 2.0.1, added only in P2 | import BOM; no AI dependency in the P0 detection slice unless needed for compile-only planning (normally it is not) |
| Spring Security | Boot-managed 7.1.1 | OAuth2 Resource Server/OIDC with Keycloak; no legacy Keycloak adapter |
| Kafka client / Spring Kafka | Boot-managed (client 4.2.1 / Spring Kafka 4.1.1 at decision date) | do not override merely to match the newest broker |
| PostgreSQL JDBC | Boot-managed 42.7.13 | pin server image independently |
| Flyway | Boot-managed 12.4.0 plus PostgreSQL database module | do not jump to upstream 13.x without a tested reason |
| JUnit / Testcontainers | Boot-managed 6.0.3 / 2.0.5 | account for Testcontainers 2.x artifact names |
| OpenTelemetry / Micrometer | Boot-managed libraries when P2 begins | prefer Spring observability integration; revalidate at P2 |

Initial local infrastructure candidates, recorded with a readable version tag and pinned to an immutable image digest when their phase starts (tags are mutable):

- Apache Kafka broker 4.2.2; keep the client Boot-managed and verify client/broker integration.
- PostgreSQL 18.6 for each separately owned database.
- Keycloak 26.8.0 from its official image.
- Prometheus 3.13.4 LTS and Grafana 13.2.2 only when P2 starts; revalidate then.
- Docker Compose authored to the Compose Specification without relying on a recent-only feature unless documented.

The P0 bootstrap must capture `./mvnw dependency:tree`, run the full smoke/build test, and update this ADR if the resolved stack differs. It must pin the Maven distribution checksum and the wrapper implementation/configuration independently. Docker references record a readable version tag and an immutable digest; CI actions and Maven plugins must also be pinned explicitly. Floating `latest` tags are prohibited, and a version tag alone is not an immutable lock.

## Official compatibility evidence (accessed 2026-10-11)

- [Spring Boot system requirements](https://docs.spring.io/spring-boot/system-requirements.html) report that Boot 4.1.1 supports Java 17 through 26 and requires Spring Framework 7.0.9 or newer.
- [Spring Boot stable projects](https://docs.spring.io/spring-boot/spring-projects.html) and the [4.1.1 release announcement](https://spring.io/blog/2026/08/20/spring-boot-4-1-1-available-now/) identify 4.1.1 as a stable release.
- [Spring Modulith reference](https://docs.spring.io/spring-modulith/reference/index.html) identifies 2.1.1 and its BOM; the [official release history](https://github.com/spring-projects/spring-modulith/releases) records the Boot 4.1/4.1.1 upgrade. Its appendix can lag the current release and is not used alone.
- [Spring AI getting started](https://docs.spring.io/spring-ai/reference/getting-started.html) explicitly states that Spring AI 2.0.x supports Boot 4.0.x and 4.1.x; the [stable project list](https://docs.spring.io/spring-ai/reference/spring-projects.html) and [2.0.1 announcement](https://spring.io/blog/2026/08/21/spring-ai-2-0-1-available-now/) support the 2.0.1 choice.
- [Spring Boot managed coordinates](https://docs.spring.io/spring-boot/appendix/dependency-versions/coordinates.html) are the source for managed patch versions above.
- [Apache Maven downloads/history](https://maven.apache.org/download.cgi) identifies Maven 3.10.0 as stable while Maven 4 remains a release candidate at the decision date. [Maven Wrapper documentation](https://maven.apache.org/tools/wrapper/) distinguishes the selected Maven distribution in `.mvn/wrapper/maven-wrapper.properties` from the wrapper implementation and documents distribution and wrapper checksum properties.
- [Kafka downloads](https://kafka.apache.org/community/downloads/) and [Kafka 4.2 Java support](https://kafka.apache.org/42/operations/java-version/) support the broker/Java candidate; client compatibility still needs an integration test.
- [PostgreSQL versioning](https://www.postgresql.org/support/versioning/) and [18.6 release notes](https://www.postgresql.org/docs/release/18.6/) support the server candidate; PostgreSQL 19 is not selected while it is pre-GA.
- [Flyway release notes](https://documentation.red-gate.com/flyway/release-notes-and-older-versions/release-notes-for-flyway-engine) and [PostgreSQL driver guidance](https://documentation.red-gate.com/flyway/reference/database-driver-reference/postgresql-database) explain the separate database module; Boot management wins over a newer upstream line initially.
- [Keycloak downloads](https://www.keycloak.org/downloads.html) and [supported configurations](https://www.keycloak.org/server/supported-configurations) support the candidate and Java/PostgreSQL combination.
- [Testcontainers Java documentation](https://java.testcontainers.org/) and [release history](https://github.com/testcontainers/testcontainers-java/releases) support the Boot-managed line.
- [Prometheus downloads](https://prometheus.io/download/) and its [release-cycle/LTS policy](https://prometheus.io/docs/introduction/release-cycle/) support the 3.13.4 LTS candidate; revalidate its support window when P2 begins.
- [Grafana downloads](https://grafana.com/grafana/download) identifies 13.2.2 as the P2 candidate at the decision date; revalidate before adding its image.

## Alternatives considered

### Spring Boot 3.5.x and older Spring generations

This is a lower migration-risk option with more existing examples. It is rejected for the initial attempt because the requested 4.1/2.1/2.0 combination is now officially released and compatible, and learning the current Spring generation is valuable. It remains the rollback alternative only if P0 produces a concrete, documented incompatibility that cannot be fixed within the time budget.

### Upgrade every dependency to its newest upstream version

Rejected. Boot's dependency set is tested as a coordinated platform. Independently overriding Kafka, Flyway, OpenTelemetry, or test libraries increases compatibility work with little portfolio value.

### Use milestone, snapshot, beta, or release-candidate versions

Rejected for the main path. Specifically avoid Boot 4.2 milestones, Modulith 2.2 milestones, Spring AI 2.1 milestones, Maven 4 release candidates, and PostgreSQL 19 pre-releases unless a future ADR identifies a required feature and isolation plan.

## Consequences

### Positive

- The requested core baseline is officially available, not hypothetical.
- BOM/parent management reduces accidental version skew.
- Java 21 remains the explicit learning/runtime contract even though Boot accepts newer JDKs.
- Deferring AI and observability runtime dependencies keeps P0 focused.

### Costs and risks

- Spring Boot 4 is a newer major generation, so older tutorials and imports may be wrong.
- Spring AI documentation examples can show an older 2.0.0 BOM even when the stable patch is 2.0.1; dependency resolution must be inspected.
- Boot-managed Kafka client and the chosen broker are not identical patch lines; this is normal but must be integration-tested.
- Container releases and security advisories can change after this decision date.

## Verification obligations

Before accepting the P0 foundation:

1. print Java and Maven distribution versions, and inspect the pinned wrapper configuration/version and checksums;
2. generate and inspect the effective POM/dependency tree;
3. compile all deployables and run a Spring context smoke test;
4. run one Kafka and one PostgreSQL Testcontainers integration test on Java 21;
5. run Spring Modulith structure verification;
6. record exact coordinates, OS/architecture, commands, exit codes, failures/skips, and any deviations in the journal/handoff;
7. re-check official release/security notes before pinning external container images.

If any check fails, record the evidence and make the smallest compatible adjustment through a superseding ADR. Do not silently switch major generations or omit a failing integration test.

## P0.1 implementation evidence — 2026-10-12

The historical decision above is preserved. Actual Maven resolution and Java 21 compilation now verify Boot 4.1.1, Framework 7.0.9, Modulith 2.1.1, Flyway 12.4.0, PostgreSQL JDBC 42.7.13, JUnit 6.0.3, Testcontainers 2.0.5, and Mockito 5.23.0. Maven Wrapper 3.3.4 selects checksum-pinned Maven 3.10.0. All four foundation applications have started and the 14 default tests passed, including Modulith verification.

PostgreSQL 18.6 is pinned in Compose and the optional test by multi-platform digest `sha256:74935e72241653ca55e0414067e6d8763aceb8a810eb51b452253ec3dcfc4336`. Docker is unavailable on the implementation host: the connectivity test is explicitly skipped, not passed. Kafka, security, AI, and telemetry versions above remain planned candidates, not resolved application dependencies. Full P0 integration obligations remain open; [ADR 0007](0007-foundation-profiles-and-stage-boundaries.md) separates the P0.1 foundation gate from later infrastructure/business gates. Exact commands and limitations are in [test evidence](../evidence/TEST_RESULTS.md).

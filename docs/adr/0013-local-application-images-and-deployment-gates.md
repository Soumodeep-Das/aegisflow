# ADR 0013: Local application images and progressive persistent deployment

> **Implementation evidence update — 2026-10-24:** P0.5 A/B/C/D acceptance is complete within documented local scopes: verified images, persistent credentials/infrastructure, nine long-running services, healthy/missing/late-evidence demonstration and retained restarts. Final closure combines a fresh 182-test reactor with separate Docker acceptance evidence; these are not one aggregate test count. Original pending-gate statements below are historical. See [current status](../STATUS.md) and the [preserved evidence ledger](../evidence/TEST_RESULTS.md). The original decision, date, context and consequences below are unchanged.

- Status: accepted for P0.5 implementation; real A image gate pending
- Date: 2026-10-22
- Baseline: clean main1d0b1f5, synchronized with origin/main

## Decision

Build/test the existing reactor once on the Java21 host, then package its four executable artifacts with one parameterized Dockerfile. Reuse the official digest-pinned Temurin21 JRE already exercised by the PostgreSQL rehearsal; do not float a tag or introduce a second dependency build architecture. Artifact identity and original/executable content/freshness guards fail closed. Each build stages only Dockerfile, .dockerignore, app.jar and HealthProbe.class into a fresh ignored directory. Docker never receives the workspace, private config, Git metadata or test evidence.

Numeric UID/GID10001 runs Java directly as PID1 with UTC timezone and bounded heap percentage. Container-only SERVER_ADDRESS=0.0.0.0/SERVER_PORT=8080 overrides host-safe defaults. The image defaults to foundation; future app overlay explicitly selects postgres and workers. Read-only root, capability drop, no-new-privileges, limited writable /tmp and resource bounds are runtime requirements enforced by the A verifier and to be retained by the future overlay. No package installation or downloads at container startup.

HealthProbe is a compiled JRE-only bounded HTTP probe. A localhost probe cannot prove bridge reachability, so A additionally tests a separate client on its unique bridge and host-published dynamic loopback HTTP. HTTP UP is not Kafka/business readiness. Four image tags aegisflow/{core,order,payment,warehouse}:local are mutable development tags; build metadata records source revision/dirty state and executable SHA256, and the live verifier compares image JAR hash to the selected artifact.

## Persistent deployment design (B/C/D planned, not implemented at A)

Retain aegisflow-foundation project, existing core/order/payment/warehouse-db-data and kafka-data names, compose.yaml/compose.kafka.yaml and database networks. Add compose.apps.yaml later; each app joins only its own internal data network and kafka-events. No sibling DB credentials/networks for Core. Internal JDBC uses owner-db:5432; Kafka uses kafka:9092. Host publication remains loopback.

Use explicitly selected ignored private development files, never accidental .env discovery or secret shell overrides. Validate distinct random credentials and stable cluster identity; if any persistent volume exists and required private material is missing, stop with recovery instructions rather than generate replacements. Existing .env/.env.kafka remain untouched. Reuse role bootstrap and topic provisioning only after actual readiness, and distinguish broker health from ACL/topic completion. Existing start-kafka.ps1 lacks the new missing-file/retained-volume guard and is not the unified P0.5 entrypoint.

Do not create a late Warehouse business outcome after its45-second deadline and expect SATISFIED: D must preserve the P0.4 distinction between occurrence and observation. A controlled service-owned fixture should commit an on-time outcome and withhold transport until after Core restart, then release the same event. This is demo infrastructure, not a new recovery/lifecycle rule.

## Gates and limits

A verifies images/non-root/content/networking; B credentials/roles/topics/reuse; C nine-service lifecycle; D ordinary persistent business workflow/restart and final regression. Isolated A foundation containers do not prove permanent-stack business behavior. Coding-session Docker denial is a blocker to live A acceptance, not permission to fake/skip it. No new production application modules, migrations, business APIs or domain changes in A.

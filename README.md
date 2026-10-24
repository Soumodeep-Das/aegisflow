# AegisFlow

A Java 21/Spring Boot control plane that observes independently owned Order, Payment and Warehouse events, correlates tenant-scoped facts, detects inventory deadline violations, and preserves explainable incident history.

## Current status

2026-10-24: **P0.5 complete within its local-development scope.** Fresh Java21 reactor:182 tests, zero failures/errors/skips. The actual nine-service deployment passed healthy, missing-Warehouse, Core-restart/late-evidence and full retained-stack restart acceptance on6939e3e. No rerun needed. Incident history remains DETECTED even when later evidence is SATISFIED; no authoritative recovery is claimed.

Lifecycle commands use scripts/dev-stack.ps1 -Action up, status, stop, down or restart-apps; storage is retained. Nine services were left healthy/running after acceptance. kafka-init is an exited one-shot provisioning job. A deliberate new demonstration uses scripts/verify-development-pipeline.ps1; it rebuilds images and briefly restarts the stack. Preserve .env.development and named volumes.

The stack includes Core, Order, Payment, Warehouse, four owner databases and Kafka. Permanent-broker forbidden-write probing remains a documented limitation; prior authentication/ACL inventory/metadata-denial checks are not a write test. Stop at P0.5; no recovery, AI or cloud deployment is implemented.

Read the [local deployment guide](docs/LOCAL_DEPLOYMENT.md) and [exact handoff](docs/HANDOFF.md) before running commands. Historical results and failures are preserved in [test evidence](docs/evidence/TEST_RESULTS.md) and the [development journal](docs/devlog/DEVELOPMENT_JOURNAL.md); they are not current retry instructions.

## Image verifier reference (already passed; no rerun needed)

From the existing repository, with Docker Desktop running and PowerShell7 available:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot'
& 'C:\Users\User\.cache\codex-runtimes\codex-primary-runtime\dependencies\native\powershell\pwsh.exe' -NoProfile -File .\scripts\verify-application-images.ps1 -UseExistingArtifacts
```

The owner already passed172 reactor tests and built four images. This retry skips Maven, refreshes the diagnostic probe image layer, and verifies temporary foundation containers using dynamic loopback ports and a private bridge. Core previously started but timed out waiting for health; the cause is not established. Health history is now saved before cleanup. Only owned temporary containers/network are removed; no persistent volumes or private files are used. This is **not** a nine-service deployment or business-readiness claim.

## Implemented business path

Order/Payment/Warehouse service-owned operation → PostgreSQL state plus transactional outbox → authenticated Kafka source topic → Core durable inbox → observed facts and projection → INV-002 evaluation → durable incident/evidence timeline.

All four databases have separate migration/runtime identities. Core has no simulator database credentials. Delivery is at-least-once with database idempotency, not universal exactly-once effects. Late valid Warehouse evidence can change current evaluation to SATISFIED without erasing history or declaring an incident RESOLVED.

P0.4 evidence comprises separate runs: clean reactor168, component pipeline7 across6+1, clock/configuration regressions6 and packaged restart1. Do not add overlapping runs into a single invented total.

## Foundation development

The default `foundation` profile remains infrastructure-free and host-loopback-bound. Only Actuator health is exposed. With Java21 set:

```powershell
.\mvnw.cmd -B -ntp clean verify
```

Root Maven wrapper uses Maven3.10.0. First build may need downloads; the deployment wrapper uses the existing ignored .cache/maven-repository. Container images use internal HTTP8080; ordinary host JAR defaults remain Core8080/Order8081/Payment8082/Warehouse8083.

## Safety and current limits

Synthetic trusted-local development only. No public authentication, TLS deployment, business mutation HTTP APIs, automatic recovery, AI or permanent-stack readiness claim. Kafka's existing SASL/PLAIN is authenticated but unencrypted. Never commit .env files, credentials, caches or private logs; never remove volumes to fix startup. Existing project identity is aegisflow-foundation.

P0.5 will preserve the four internal database networks and source-specific Kafka ACLs. Persistent credential initialization and dev-up/status/verify/down commands are not yet implemented; do not treat older standalone helpers as the finished P0.5 lifecycle.

## Engineering documentation

- [Architecture](docs/architecture/SYSTEM_ARCHITECTURE.md), [module boundaries](docs/architecture/MODULE_BOUNDARIES.md), [event contracts](docs/architecture/EVENT_CONTRACTS.md), [invariants](docs/architecture/INVARIANTS.md)
- [Requirements](docs/requirements/PRD.md), [threat model](docs/security/THREAT_MODEL.md), [known limitations](docs/KNOWN_LIMITATIONS.md)
- [Roadmap](docs/ROADMAP.md), [implementation plan](docs/roadmap/IMPLEMENTATION_PLAN.md), [codebase learning guide](docs/learning/CODEBASE_GUIDE.md)
- [P0.5 deployment decision](docs/adr/0013-local-application-images-and-deployment-gates.md)

Do not push or start recovery/AI work without explicit authorization.

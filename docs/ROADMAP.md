# Roadmap

[Current status](STATUS.md) defines what is implemented; [evidence](evidence/TEST_RESULTS.md) records what was actually tested.

| Stage | Status | Delivered scope |
|---|---|---|
| Phase 0 | Complete | Requirements, architecture, threat/fault specifications and ADRs |
| P0.1 | Complete | Java 21 reactor, four applications and module boundaries |
| P0.2 | Complete | Service-owned persistence, roles, migrations and atomic state/outbox |
| P0.3 | Complete | Kafka relays, durable Core inbox/quarantine and replay handling |
| P0.4 | Complete | Correlation, INV-002, immutable evaluations and durable incident timeline |
| P0.5 | Complete within local-development scope | Persistent nine-service deployment and real business/restart acceptance |
| P1 | Planned; not authorized | Authoritative service APIs, deterministic recovery, policy/approval and independent verification |
| P2 | Planned; not authorized | Optional AI investigator, operational UI and observability |
| Later options | Decision-dependent | Additional integrations, workflow engine or deployment platforms only with measured need |

## Next decisions, not implementation permission

1. Decide whether and how to begin P1; preserve the detection-only baseline.
2. Define authoritative reads and a narrow non-financial action before implementing any executor.
3. Specify API authentication, resource versions, stable action idempotency and approval binding.
4. Address backup/restore and public deployment requirements before extending local security claims.
5. Evaluate AI only after a deterministic investigator and evaluation corpus exist.

The deferred persistent Kafka forbidden-write probe is a documented limitation, not a reason to rewrite passing delivery components. No performance or production SLO is established.

See the [delivery plan](roadmap/IMPLEMENTATION_PLAN.md) for phase gates, [open decisions](OPEN_QUESTIONS.md), and [historical roadmap snapshots](history/REFERENCE_THROUGH_P05.md).

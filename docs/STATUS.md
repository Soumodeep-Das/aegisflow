# Current implementation and verification status

**Reviewed:** 2026-10-24. **Verified implementation:** `6939e3e`; closure documentation: `a32a727`. This is the authoritative current-status summary; other guides explain rather than duplicate it.

## Implemented and verified within P0 scope

Phase 0 and P0.1–P0.5 are complete within the [recorded verification scopes](evidence/TEST_RESULTS.md#p05-acceptance-evidence).

| Capability | As built | Evidence boundary |
|---|---|---|
| Four Java 21 applications | Modular Core plus independent Order, Payment and Warehouse simulators | Maven/build/module and packaged-process checks |
| Owned persistence | Four PostgreSQL databases, independent Flyway histories, restricted runtime and migration roles | State/outbox transactions, replay, rollback and database constraints |
| Event delivery | Three source outbox relays, Kafka, Core immutable inbox and technical quarantine | Real PostgreSQL/Kafka suites; at-least-once delivery |
| Business observation | Tenant-scoped facts and projections, separately committed projection progress | Durable retry/backfill and concurrency checks |
| Detection | `INV-002`, versioned 45-second inventory deadline plus 15-second transport lateness | Injected-clock tests and real-time persistent demonstration |
| Incidents | Immutable evaluations and fact references; deduplicated `DETECTED` episode with append-only timeline | Uniqueness, rollback, replay and process-restart evidence |
| Local deployment | Four apps, four PostgreSQL instances and one Kafka broker | Nine long-running services; separate exited `kafka-init` provisioning job |

The final P0.5 host run passed **182 clean-reactor tests with zero failures, errors or skips**, built four images, and exercised six real owner events on the ordinary persistent stack. Healthy flow was `SATISFIED` with no incident; missing observed Warehouse evidence opened one incident. After Core restart, the unchanged on-time Warehouse event arrived late: a new evaluation was `SATISFIED`, while the original incident stayed `DETECTED`. Full-stack stop/start preserved facts and timeline.

This is a historical acceptance result, **not a claim that containers are running now**. Use the [runbook](LOCAL_DEPLOYMENT.md) to inspect your machine. The 182 tests are one default reactor run, not a sum of all historical integration suites or proof of all 16 fault scenarios.

## Not implemented

- Simulator-to-simulator commerce choreography, complete customer-facing command APIs, confirmation/cancellation and cleanup workflows.
- Other business invariant evaluators beyond `INV-002`.
- Authoritative state-query/recovery APIs, deterministic investigation/planning, approval, execution or `RESOLVED` transitions.
- Keycloak/OIDC application authorization, AI integration, dashboard, cloud deployment or full observability stack.

P0's local scenario driver invokes real owner business methods. It is not a public mutation API or a recovery worker.

## Explicit limitations

Kafka uses authenticated **unencrypted SASL/PLAIN** for synthetic local data. Topic/source rules and configured tenant allowlists assume trusted producers/operators; they do not prove a compromised allowed producer is truthful. A permanent-broker forbidden-write probe is still deferred: ACL inventory and metadata denials are not write-denial evidence. Single-broker replication factor 1 is not HA. Backup restoration, production capacity, hostile public deployment and universal exactly-once effects are not claimed.

See [limitations](KNOWN_LIMITATIONS.md), [security controls](security/THREAT_MODEL.md#current-control-status), and [scenario coverage](evaluation/FAILURE_MATRIX.md#current-coverage).

## Next boundary

Documentation refinement only is authorized after P0.5. P1 recovery and P2 AI/operational surfaces require separate approval. No push is authorized. [Handoff](HANDOFF.md) records safe continuation; [journal](devlog/DEVELOPMENT_JOURNAL.md) and [archives](history/REFERENCE_THROUGH_P05.md) preserve prior states.

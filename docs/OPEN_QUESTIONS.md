# Open questions and decision register

Reviewed 2026-10-24 against P0.5. This register separates decisions settled for the local demonstration from questions still requiring a design or evidence gate. Original OQ-01–OQ-35 wording, owners and provisional defaults are preserved in the [reference archive](history/REFERENCE_THROUGH_P05.md); IDs are not renumbered. See [current status](STATUS.md) and [limitations](KNOWN_LIMITATIONS.md).

## Settled for the current local scope

| Original ID | Current decision and evidence | Remaining boundary |
|---|---|---|
| OQ-01 | Application polling relays with expiring leases/fencing and stable event IDs; [ADR 0002](adr/0002-transactional-outbox.md), P0.3 evidence | CDC is not implemented or required |
| OQ-02 | Three source topics, three partitions, seven-day retention, RF1/minISR1; [ADR 0009](adr/0009-local-kafka-listeners-and-source-topics.md) | Production sizing/availability remains unmeasured |
| OQ-03 | Versioned JSON schema plus typed validation; [event contracts](architecture/EVENT_CONTRACTS.md) | No schema registry |
| OQ-04 | INV-002, versioned 45-second deadline plus 15-second lateness allowance; Clock-based tests and a held-outbox persistent fixture | Other invariants require approval and tests |
| OQ-05 | Internal owner command replay persists stable result/event identity; [data model](architecture/DATA_MODEL.md) | An authenticated public Order HTTP API is not implemented |
| OQ-12 | Source-owned topic ACLs plus trusted source-to-tenant allowlists at Core | Local synthetic tenant trust is not end-user authentication or cryptographic publisher attestation |
| OQ-14 | Typed producer/request canonicalization and separate Core canonical-content/raw digests | Approval-binding digests and general recovery request profiles remain future work |
| OQ-15 | 18 generated credentials retained in ignored guarded `.env.development`; [runbook](LOCAL_DEPLOYMENT.md) | Rotation, CI secret delivery and production secret management are not delivered |
| OQ-21 | Stable INV-002 incident episode identity and PostgreSQL uniqueness; [ADR 0012](adr/0012-durable-incident-timelines.md) | Terminal lifecycle/recurrence after resolution is not exercised because only DETECTED exists |
| OQ-22 | Durable technical quarantine keyed by broker location; claimed payload IDs are not trusted identity | Authorized quarantine replay/UI, retention policy and operational alerting remain open |
| OQ-32 | Current demo shows healthy, missing observed outcome and late evidence without claiming repair | Future investigator/recovery persona needs separate approval |
| OQ-34 | PostgreSQL, Kafka, four Boot applications and a modular Core are sufficient for P0 | Extra systems require measured need and an ADR |

These are scoped decisions, not claims that the full original questions are permanently closed. See the [test ledger](evidence/TEST_RESULTS.md) for evidence rather than treating this table as a test result.

## Decisions still open

| IDs | Decision still required | Gate / responsible area |
|---|---|---|
| OQ-06, OQ-07, OQ-08 | Recovery API idempotency/preconditions/status, durable dispatch claims and attempt-before-send checkpoints | Before any recovery execution; API + Core architecture. Existing outbox leases do not settle action semantics |
| OQ-09, OQ-10 | Versioned deterministic policy, allowed actions and exact approval/separation-of-duties rules | Before approval or execution; security + product |
| OQ-11 | Whether PostgreSQL RLS adds useful defense beyond current tenant composite keys and application checks | Before broader multi-tenant deployment; security + data |
| OQ-13, OQ-18 | Identity provider, realms/scopes/tenant claims and data-free authorization/404 policy | Before authenticated public APIs; security + API |
| OQ-16, OQ-17 | Evidence retention/deletion/encryption and whether signed envelopes are needed | Before shared/external deployment; security + product |
| OQ-19, OQ-20 | Evidence freshness/authority and bounded authoritative verification after unknown effects | Before investigator/recovery; events alone cannot prove current remote state |
| OQ-23 | Reproducible hardware/workload and latency/throughput measurement | Before performance or SLO claims; evaluation |
| OQ-24 | Redacted, bounded-cardinality metrics/traces and access-controlled operational views | Before observability expansion |
| OQ-25 | Backup/restore consistency across owner databases, outboxes and Kafka offsets | Before disaster-recovery claims; restart success is not restore proof |
| OQ-26–OQ-31 | Provider/model terms, schema/action catalog, evidence minimization, held-out corpus, cost/safety thresholds and tool policy | Optional P2 only; no live AI integration or authorization exists |
| OQ-33 | First non-financial, idempotent recovery action and independent authoritative state query | Before P1; product + security. Automatic financial compensation remains prohibited |
| OQ-35 | License selection and dependency/asset attribution review | Repository owner before public licensing claims; no license is invented here |

## Residual verification questions

- How can an actual forbidden-write check safely run against retained Kafka data? P0.5 verified ACL inventory and metadata denial, not persistent forbidden writes.
- What backup, clean-machine and multi-host rehearsals are worth approving next? Current local restart evidence does not answer them.
- What changes are needed for encrypted transport and non-synthetic tenancy? Do not promote local SASL/PLAIN into a production security claim.

The single next action is an owner scope decision, not automatic implementation. Follow [HANDOFF](HANDOFF.md).

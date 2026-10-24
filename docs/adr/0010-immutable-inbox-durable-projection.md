# ADR 0010: Durable projection after immutable inbox acceptance

> **Implementation evidence update — 2026-10-24:** Immutable ACCEPTED intake remains independent from atomic fact/projection/progress transactions. P0.4 and P0.5 verified durable continuation without reinterpreting the inbox as a mutable PROCESSED queue. See [current status](../STATUS.md) and the [preserved evidence ledger](../evidence/TEST_RESULTS.md). The original decision, date, context and consequences below are unchanged.

- **Status:** Accepted for approved P0.4-A; real PostgreSQL gate required before detection
- **Date:** 2026-10-20
- **Supersedes:** old same-transaction inbox/PROCESSED wording in DATA_MODEL, EVENT_CONTRACTS and FR-002; preserves P0.3 behavior

## Decision

P0.3 commits validated ACCEPTED inbox rows before Kafka offsets. These rows remain append-only; projection does not update them, rewind offsets or depend on an in-memory queue.

Eventing exposes a narrow `ProjectionInbox` port with typed accepted records, bounded pending selection, completion/block markers and an unsettled-work query. Its adapter owns the separate `inbox_projection_progress` table. Correlation depends only on the exported eventing API; eventing has no correlation dependency. Core still cannot import simulator code.

One Core READ_COMMITTED transaction acquires a transaction-scoped advisory lock, reads one accepted row without a marker, derives an immutable fact, rebuilds its tenant/transaction projection and stored deadline, then appends APPLIED progress. A rollback removes all these effects together. The lock serializes short local projector transactions; it is automatically released on rollback/session loss and never covers network I/O. Batches have a configured maximum; no lease/reclaimer is needed for this local transaction-only work. This favors understandable correctness over parallel throughput.

Pending selection is an anti-join over all accepted identities, not a high-water timestamp/sequence cursor. A transaction committed late remains discoverable even if its received time sorts before processed work. Duplicate retries find the durable marker. Fact/inbox/projection references include tenant identity and database uniqueness.

The five implemented event types retain original envelope/hash provenance. Effective source state uses aggregate identity and version, not arrival order. Multiple aggregates for one source/transaction or conflicting same-version content remain explicit conflicts. The initial catalog does not authorize changing terminal outcomes at higher versions: retain highest-version evidence but flag such changes as unsupported, never infer a valid business correction. InventoryRejected has no items in its accepted schema; its supported reason and order identity are retained, without inventing quantities.

Per-transaction fact reduction has an explicit1000-fact safety budget. Unsupported accepted shapes or over-budget transactions receive immutable BLOCKED progress with a fixed reason after the attempted effect transaction rolls back. Such records remain unresolved work for evaluation freshness, rather than being silently discarded or retried as successful. No automatic repair/requeue workflow is introduced.

## Deadline and future detection boundary

P0.4-A snapshots ECOM_ORDER_V1/configuration identity, inventory duration45s and lateness15s (configurable) when a projection is first created. A valid unambiguous effective OrderCreated determines dueAt and enforceAfter, applying lateness once. These are persisted scheduling inputs, not an implemented invariant evaluation. A custom Clock controls projection processing timestamps.

P0.4-B must use the same transaction-scoped gate and check `hasUnsettled(tenant, transaction)` against its consistent observation snapshot before evaluating absence. Both pending and BLOCKED rows suppress definitive missing-outcome evaluation. A visible earlier warehouse inbox row cannot be ignored just because it has not been projected. Concurrent intake after the observation snapshot is later evidence; no mechanism can claim knowledge of uncommitted or not-yet-delivered events. Durable projection deadlines allow a later detector to discover work after a crash; projection does not pretend an incident was already created.

## Verification and limits

Required PostgreSQL cases: V2-to-V3 backfill, duplicate/reordered input, late-visible inserts, transaction rollback after marker insertion, retry after committed processing, concurrent projectors, tenant composite keys, immutable provenance/progress, blocked work and frozen contract configuration. No business incidents until this gate passes. The advisory gate and1000-fact budget are local demo tradeoffs, not throughput/SLO claims. No new dependency or cross-service database access.

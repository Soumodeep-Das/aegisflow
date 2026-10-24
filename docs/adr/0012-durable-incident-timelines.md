# ADR 0012: Recoverable incident creation and immutable timeline

> **Implementation evidence update — 2026-10-24:** The 21 P0.4-C PostgreSQL cases passed across the recorded 20-case run and separate restart run. P0.4-D/P0.5 then demonstrated incidents and late-evidence timelines end-to-end. Lifecycle remains DETECTED; SATISFIED is not authoritative resolution. See [current status](../STATUS.md) and the [preserved evidence ledger](../evidence/TEST_RESULTS.md). The original decision, date, context and consequences below are unchanged.

- **Status:** Accepted; P0.4-C PostgreSQL gate verified across20 earlier passes plus1 successful restart-only rerun on2026-10-21. Full D pipeline gate remains separate.
- **Date:** 2026-10-21
- **Scope:** detection only, based on verified A/B and ADR0010/0011

## Episode identity and lifecycle

One initial ECOM_ORDER_V1 transaction has one still-open INV-002 episode per pinned invariant/contract/configuration version. PostgreSQL enforces both the explicit episode key and its structured identity, scoped by tenant/transaction. Incident UUID is generated once and then persisted, not regenerated on restart. Only VIOLATED evaluations open incidents; UNKNOWN/SATISFIED alone never open one.

Only the canonical DETECTED state is implemented. State version remains1; runtime cannot update state. There is no terminal transition or authoritative resolution in P0.4. Later valid events and repeated violations remain in the same open episode. A future authorized terminal/recurrence workflow must extend schema/guards and link successors; this implementation does not claim that lifecycle. No recovery, approval, compensation or AI work is added.

Initial missing/late-observed-outcome severity is MEDIUM; mismatch/contradiction is HIGH. Initial reason/severity are retained. Later exact reasons/results are immutable timeline evaluations rather than silent overwrites. Material revision increments once for each new evaluation context linked to the incident, including past context discovered after opening; state_version is not an evidence counter.

## Durable bridge

Detection owns V5 incident, incident_timeline and incident_fact_reference. Existing immutable invariant_evaluation remains unchanged except an added composite unique identity for FK support. No new module dependency or direct correlation table read is needed: each evaluation already includes an exact fact snapshot.

An anti-join selects one eligible evaluation with no timeline entry. A violation can create its incident; once one exists, every matching evaluation (including earlier UNKNOWN/SATISFIED and later evidence) becomes eligible. No cursor permanently skips earlier/late-visible records. A separate transaction-scoped advisory gate serializes incident workers only; it does not lock network I/O or require projector activity.

One local transaction commits incident creation or revision update, timeline entry and exact fact references. Timeline uniqueness is the durable completion marker. Rollback leaves the evaluation discoverable; a crash after evaluation commit but before incident processing cannot lose it. Bounded batches cap100 evaluations, one transaction each; snapshot fact references cap1000. Unique episode keys and timeline keys are enforced in PostgreSQL as well as coordinated by the gate.

## Evidence and least privilege

All incident/evaluation/fact FKs carry tenant AND business transaction. Timeline links exact immutable evaluations (rule/config/deadline/result/reason/evaluatedAt and observed facts); fact references link original immutable provenance/hash rows. Missing outcome means absence in that observation snapshot, never an invented Warehouse API read or authoritative operation failure.

Runtime can SELECT/INSERT these records and update only incident_revision/updated_at. It cannot delete history, rewrite timeline/evidence, alter initial reason, or declare RESOLVED. Timeline recordedAt is processing time; evaluation and source/intake timestamps remain separate. Late evidence appends EVIDENCE_CHANGED, never deletes a violation or auto-resolves.

## Acceptance

Require actual PostgreSQL uniqueness, concurrent workers, rollback after timeline insert, tenant/transaction FK and privilege checks, late evidence and process restart. Test-only IncidentProcessMain runs the production recorder in two separate JVMs against the same disposable database; this tests worker process restart, not the full packaged application's scheduling/wiring. D must still prove the complete simulator/Kafka/intake/projection/detection/incident path. No permanent .env or configured Compose app containers are required.

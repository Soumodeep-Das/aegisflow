# Codebase guide

## Read this first

There is **no implementation yet**. Phase 0 intentionally contains Markdown architecture and engineering specifications only. Any class, module, API, migration, topic, or command described as “planned” does not exist and must not be presented as working.

## Actual repository tree at Phase 0

```text
aegisflow/
├── AGENTS.md                         # mandatory continuity/safety protocol
├── README.md                         # project entry point and honest status
└── docs/
    ├── requirements/PRD.md           # FR/NFR/AT requirements
    ├── architecture/                 # system, data, events, invariants, state machine
    ├── security/THREAT_MODEL.md
    ├── evaluation/                   # fault matrix, AI plan, reproducible Phase 0 review
    ├── adr/                          # decisions and trade-offs
    ├── roadmap/IMPLEMENTATION_PLAN.md
    ├── learning/CODEBASE_GUIDE.md    # this file
    ├── devlog/DEVELOPMENT_JOURNAL.md
    ├── OPEN_QUESTIONS.md
    ├── ROADMAP.md
    ├── KNOWN_LIMITATIONS.md
    └── HANDOFF.md
```

Git's `.git/` directory also exists locally and is intentionally omitted from the conceptual tree.

## How to navigate the design

Start with the product question in `requirements/PRD.md`. Follow a requirement ID into:

1. `architecture/INVARIANTS.md` to learn what healthy/unsafe means;
2. `architecture/EVENT_CONTRACTS.md` to see the facts available;
3. `architecture/DATA_MODEL.md` to see what must survive a crash;
4. `architecture/RECOVERY_STATE_MACHINE.md` to see allowed transitions;
5. `security/THREAT_MODEL.md` for abuse boundaries;
6. `evaluation/FAILURE_MATRIX.md` for how the behavior will be disproved/tested; and
7. an ADR for why the chosen architecture beat an alternative.

This chain is more important than the number of documents: it makes a future code path explainable and testable.

## Planned deployment versus planned logical modules

The first implementation is planned as four deployables:

- Order simulator with its own database;
- Payment simulator with its own database;
- Warehouse simulator with its own database; and
- one AegisFlow control plane with its own database.

The control plane is a modular monolith, meaning one process contains strongly checked logical modules (ingestion, correlation, invariants, incidents, evidence, investigation, policy, approval, recovery, verification, audit, tenancy). This avoids distributed operations between AegisFlow's own features while still teaching explicit boundaries. Spring Modulith will test those boundaries. It does **not** turn the three simulators into in-process modules; their network and ownership failures remain real.

## Planned end-to-end event flow

No step below is implemented yet:

1. Order writes business state and an outbox event in one PostgreSQL transaction.
2. An asynchronous publisher sends the versioned envelope to Kafka. A retry keeps the same `eventId`.
3. AegisFlow consumes the event and, in one local transaction, inserts an inbox key and updates its tenant-scoped projection.
4. The invariant evaluator uses facts plus a durable deadline to decide whether an `INV-*` rule is violated.
5. Incident creation is protected by a unique invariant-instance key; evidence records point to the source event/API observation.
6. In P1, deterministic policy and an exact human approval gate a typed API action.
7. An action timeout records “unknown,” and a separate authoritative read decides whether to retry, review, or resolve.

## Five important concepts in plain language

### 1. Transactional outbox

Writing a database row and publishing to Kafka are two different systems, so one can succeed while the other fails. An outbox stores the event beside the business change in the same database transaction. A later publisher may retry it. The trade-off is duplicate delivery and extra cleanup/monitoring, so consumers need idempotency.

### 2. Inbox idempotency

“Idempotent” means repeating an input has the same business result as doing it once. AegisFlow will insert a unique event receipt and its projection change in the same database transaction. A database constraint makes two workers racing on the same event safe; an in-memory set would fail after restart or across replicas.

### 3. Event ordering and time

Kafka orders records only inside a partition, and distributed facts may still be produced or observed late. The projection must compare business versions/timestamps and evaluate missing-event deadlines after an allowed-lateness window. “Not seen yet” is not the same as “did not happen.”

### 4. Unknown remote outcome

An HTTP timeout says only that the caller did not receive a response. The server may have committed the action. AegisFlow queries authoritative state and operation status, then permits a same-key retry only when the service's validated idempotency and precondition contract makes it safe. A missing resource alone does not prove that a delayed request cannot still commit.

### 5. AI as evidence-bounded advice

A model is good at organizing messy evidence but can invent claims or follow malicious text in logs. AegisFlow will validate its structured output and citations. Code—not the model—controls identity, tenant, allowed action types, approval, retries, and execution. The deterministic investigator remains the comparison baseline and outage fallback.

## Phase 0 debugging exercise: trace a timeout-after-success

Goal: learn to spot a dangerous assumption before code exists.

1. In `evaluation/FAILURE_MATRIX.md`, find the warehouse “timeout after success” scenario.
2. Follow its expected incident/action states into `architecture/RECOVERY_STATE_MACHINE.md`.
3. Confirm that `architecture/DATA_MODEL.md` can retain request fingerprint, idempotency key, unknown outcome, claim owner/lease, and verification observation after a crash.
4. Confirm the API/event fields needed to query the authoritative reservation in `EVENT_CONTRACTS.md`.
5. Confirm `THREAT_MODEL.md` prevents an AI response or stale approval from forcing a retry.

Expected observation: a timeout must enter `VERIFYING`; it must not transition directly to `FAILED` (which invites a duplicate retry) or `RESOLVED` (which trusts an absent response). If any document says otherwise, Phase 0 has found a specification bug that must be fixed before implementation.

## Phase 0 interview questions and answers

### Q1. Why use a modular monolith for AegisFlow but separate simulator services?

The control plane's features need strong internal consistency and are maintained by one owner, so in-process modules reduce deployment and failure complexity. Separate simulators preserve the important network, database-ownership, timeout, and event-delivery boundaries the project exists to study. This is a deliberate split, not “microservices everywhere.”

### Q2. Why can Kafka's producer idempotence not guarantee exactly-once business recovery?

Kafka can prevent certain duplicate records and atomically handle Kafka reads/writes, but a warehouse or payment HTTP effect is outside Kafka's transaction. A crash or lost response can leave the external effect committed while local state is uncertain. The system therefore assumes at-least-once messages and uses service idempotency keys, database uniqueness, state preconditions, and reconciliation.

### Q3. What is the difference between correlation and authorization?

Correlation groups facts that belong to a business transaction. Authorization decides whether an actor may access or change them. A matching order ID or correlation ID is never proof of tenant access; every storage and API operation must also carry and enforce the authenticated tenant.

### Q4. Why bind an approval to hashes and versions?

An approval for one proposal must not be reusable after its parameters, evidence, policy, tenant, or incident state changes. Canonical hashes and versions make that exact decision immutable and machine-checkable. Expiry and single-use consumption further prevent replay.

### Q5. How do you evaluate whether AI adds value here?

First freeze incidents, evidence, expected findings/actions, and the deterministic investigator's result. Then measure AI accuracy, citation validity, unsafe proposals, abstention when evidence is insufficient, latency, token use, and cost on the same versioned cases. Event detection and recovery safety tests remain separate, so a fluent model answer cannot hide a broken distributed system.

### Q6. What happens if PostgreSQL commits but Kafka is unavailable?

The business change and its outbox row are already durable in the same PostgreSQL transaction. The relay retains the row, backs off, and publishes it when Kafka returns. A crash after broker acknowledgement may cause a duplicate publish, so the consumer's durable inbox still matters.

### Q7. How would you stop two recovery workers from executing the same action?

Use one conditional database claim with a lease and increasing fencing token. Only the current token can finalize local work. Because a paused worker can still send a late HTTP request, the simulator also enforces the stable action idempotency key and expected resource version. A lease alone is insufficient.

### Q8. How do you detect a missing event without generating false alarms for ordinary delays?

Persist the base business deadline and add the contract's allowed-lateness interval exactly once to compute the enforcement time. Use a controllable clock in tests. After the window, open an incident about the missing outcome and query authoritative state; event absence by itself cannot justify a repair.

### Q9. Why does an HTTP 200 response not resolve the incident?

It reports the result of one command, which may still be insufficient for the business invariant. A fresh independent query checks the owning service's current state and version, records evidence, and evaluates the required postcondition. Contradictory or unavailable state leaves the incident in review instead of reporting success.

### Q10. What makes a useful integration test different from a unit test here?

A unit test can exercise a transition table or deadline decision without infrastructure. An integration test proves a real boundary: PostgreSQL uniqueness under concurrent inserts, transaction rollback, Kafka redelivery, or the simulator's idempotency record after a lost response. Both are needed; mocking those boundaries alone cannot prove their crash behavior.

## What the next milestone must add to this guide

After P0, replace the proposed tree and flow with actual Maven modules, packages, classes, migrations, topic names, and test paths. Include one command-driven debugging exercise and five new Q&As tied to the code. Never delete the honest distinction between implemented behavior and roadmap intent.

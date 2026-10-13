# Open Questions and Decision Register

**Status:** Phase 0 register preserved; P0.2 producer/storage decisions recorded below

**Last reviewed:** 2026-10-13

This register prevents unresolved design choices from becoming accidental implementation. “Provisional default” enables planning; it is not a final ADR. Decisions should be closed with an ADR, affected documentation updates, and explicit acceptance tests.

P0.2 decisions are in [ADR 0008](adr/0008-persistence-primitives-and-database-roles.md): separate bootstrap/migrator/runtime identities and independent histories; explicit UUID aggregate/event IDs; exact numeric money; nonnegative ORM lock counter plus positive exposed version; and `aegisflow-json-v1` for typed producer/request material. OQ-05 now has durable internal Order-create replay storage, while authenticated HTTP submission semantics remain later work. OQ-14's typed producer profile has passed unit vectors; consumer interpretation of arbitrary JSON and recovery/approval binding profiles remain open. OQ-01's durable outbox storage is implemented, with relay behavior deferred to P0.3. RLS/OQ-11 stays optional and unimplemented; tenant composite keys and tenant-required methods are present in source, not a complete authenticated multi-tenant proof.

## 1. Blocking or early implementation decisions

| ID | Question and why it matters | Provisional default | Decision owner | Needed by |
|---|---|---|---|---|
| OQ-01 | Which concrete relay implements the explicit transactional-outbox decision in ADR 0002: a small application poller or an outbox/CDC library? It must not add a second publication path. | Start with the smallest poller that proves FM-003/FM-004 with one database transaction and stable eventId; reconsider only on measured need. | Architecture | Before simulator event code |
| OQ-02 | What partition counts and retention windows fit the domain topics already specified in EVENT_CONTRACTS.md? | Start small locally; keep `tenantId:businessTransactionId` keys; measure lag and document replay windows before increasing partitions. | Architecture | Before P0 Kafka provisioning/topic creation |
| OQ-03 | Will schema governance use JSON Schema, Avro/Protobuf plus a registry, or versioned JSON with application validation for the first slice? | Versioned JSON plus strict contract tests for the first slice unless a registry clearly reduces risk. | Architecture | Before first producer/consumer |
| OQ-04 | Which repeatable fault switch demonstrates the first detector invariant without a timing-dependent test? | `INV-002` missing inventory outcome is the first P0 candidate, using the documented 45s base deadline + 15s grace and a fake clock; finalize the fault harness before coding the detector. | Product + Architecture | Before detector implementation |
| OQ-05 | How does the Order API preserve transaction/correlation identifiers on duplicate order submissions? | Order creates businessTransactionId once; every service preserves it; an API request idempotency contract must return the same created identity for a replay. | Architecture | Before simulator APIs |
| OQ-06 | What exact semantics do simulator recovery APIs provide for idempotency, expected resource version, status lookup and authoritative verification? | One stable action-level idempotency key reused by distinct per-invocation attempt IDs, optimistic precondition, durable status lookup, and separate authoritative resource GET. | Simulator/API | Before recovery implementation |
| OQ-07 | Which PostgreSQL query/locking form implements the accepted versioned claim and lease design? | Atomic conditional update with version and expiring lease; `SKIP LOCKED` may optimize selection but is not the correctness guard; prove one dispatcher under FM-008. | Control-plane architecture | Before workers |
| OQ-08 | At which durable checkpoint is an attempt recorded before dispatch, and how does restart identify an incomplete dispatch? | Persist attempt identity/checkpoint before entering `INVOKING`; restart treats a possibly sent call as attempt `UNKNOWN`, action `OUTCOME_UNKNOWN`, incident `VERIFYING`. | Control-plane architecture | Before recovery state implementation |
| OQ-09 | What policy representation is sufficient and reviewable for MVP? | Typed, versioned deterministic rules in code/configuration; no LLM-generated/executed policy and no general-purpose scripting. | Security + Architecture | Before recovery policy |
| OQ-10 | Which actions always require human approval, and is separation of duties required between investigator and approver? | All state-changing recovery requires approval in the first safe slice; require separate approver for any payment-like action. Automatic financial compensation remains forbidden. | Product + Security | Before approval API |

## 2. Security and tenancy decisions

| ID | Question and why it matters | Provisional default | Decision owner | Needed by |
|---|---|---|---|---|
| OQ-11 | Is application-layer tenant scoping sufficient for the portfolio, or should PostgreSQL row-level security be defense in depth? | Mandatory application checks/composite tenant keys; evaluate RLS after repositories exist and test both if adopted. | Security + Data | Before multi-tenant demo |
| OQ-12 | Are Kafka topics shared across tenants or isolated per tenant/environment, and what trusted mechanism binds a publisher to allowed tenants? | Use source-owned development topics and ACLs to restrict each service credential to its topic. ACLs do not inspect payload tenant/source, consumers do not receive a trustworthy producer identity, and producer headers are untrusted; choose an application/gateway allowlist, protected attestation, or tenant-specific topics before claiming tenant provenance. | Security + Operations | Before Kafka security configuration |
| OQ-13 | What Keycloak realms, clients, roles/scopes and tenant-membership claims are used? | One development realm; separate UI, control-plane and per-simulator service clients; read/investigate/approve/execute/admin scopes; server-derived tenant membership. | Security | Before secured endpoints |
| OQ-14 | Which versioned canonicalization profiles and shared test vectors realize event-payload, idempotent-request, and approval-binding digests? | Specify profile/version, field types, decimal/time normalization, and digest algorithm; bind material `incident_revision` separately from lifecycle `state_version`; prove material changes alter a digest while explicitly insignificant JSON key order/whitespace does not. Store the profile/version with each digest. | Security + Architecture | Event profile before first producer/consumer; request/approval profiles before their persistence |
| OQ-15 | How are secrets supplied locally and in CI, rotated, and kept out of telemetry and prompts? | Uncommitted environment/secret files for local use, CI secret store later, log/prompt redaction and seeded-canary tests. | Operations + Security | Before credentials are added |
| OQ-16 | What encryption, audit retention, deletion and backup policy applies to evidence and model inputs? | Synthetic data only in MVP, bounded retention, no raw secrets; document deletion/backup semantics before persistent shared use. | Security + Product | Before AI or shared deployment |
| OQ-17 | Should event envelopes or evidence be cryptographically signed beyond authenticated Kafka transport? | Defer signing unless the threat model or deployment crosses an untrusted broker boundary; always retain provenance and payload digest. | Security | Before external integrations |
| OQ-18 | What response prevents cross-tenant resource enumeration: 403 or existence-hiding 404? | Choose one documented API policy and test consistent data-free responses/timing; domain state must never change either way. | Security + API | Before API contract freeze |

## 3. Evidence, failure, and operations decisions

| ID | Question and why it matters | Provisional default | Decision owner | Needed by |
|---|---|---|---|---|
| OQ-19 | What makes each evidence type fresh, authoritative, contradictory, or sufficient for action? | Per-evidence-type freshness policy; simulator state API is authoritative for current state; events establish history but absence is not proof. | Architecture + Product | Before investigator |
| OQ-20 | How many authoritative checks, over what backoff and elapsed time, precede NEEDS_REVIEW after unknown outcome? | Small bounded retry budget with jitter and explicit elapsed-time cap; no blind action retry. Exact values come from deterministic tests. | Reliability | Before verifier |
| OQ-21 | How are violation episodes keyed when a recurring violation follows a terminal incident? | One active incident per tenant/invariant/business transaction; terminal records stay immutable and a recurrence creates a linked successor with a new episode key. | Product + Data | Before incident persistence |
| OQ-22 | How are poison messages retained/replayed without exposing sensitive payloads, trusting claimed tenant/event IDs, or causing retry loops? | Key technical quarantine by broker topic/partition/offset plus payload digest and failure code; store optional parseable identifiers only as untrusted claims, protect payloads, authorize manual/version-aware replay, and emit an operational/security signal rather than a business incident until tenant/transaction/invariant linkage validates. | Reliability + Security | Before consumer error handling |
| OQ-23 | What local hardware/dataset define latency targets, and how are clock/outage durations excluded? | Record environment and separate injected wait/approval time; do not generalize local measurements into production SLOs. | Evaluation | Before performance claims |
| OQ-24 | What evidence and audit data appear in Prometheus labels, traces, and Grafana? | No tenant/order/event IDs in unbounded metric labels; redacted IDs only in access-controlled logs/traces. | Observability + Security | Before telemetry |
| OQ-25 | What is the disaster/restore story for the control-plane database, outboxes and Kafka offsets? | Document consistent backup/replay boundaries and add a restore exercise after the vertical slice; no production durability claim before it passes. | Operations | Before resilience claim |

## 4. AI decisions

| ID | Question and why it matters | Provisional default | Decision owner | Needed by |
|---|---|---|---|---|
| OQ-26 | Which Spring AI provider/model is affordable and acceptable for synthetic evidence handling? (The library candidate is fixed to Spring AI 2.0.1 by ADR 0006.) | Keep a provider-neutral interface; choose a model only after retention/training terms, price, structured-output support, and the evaluation plan are checked. | AI + Architecture | Before AI implementation |
| OQ-27 | What failure taxonomy, output schema and action catalog can the model reference? | Small versioned enums/schemas derived from deterministic domain concepts; unknowns force ABSTAIN. | AI + Domain | Before prompt/schema design |
| OQ-28 | Which evidence fields may leave the process for a hosted model, and what is redacted? | Synthetic/minimized allowlist only; no secrets, tokens, unrelated tenant data or full raw logs. | Security + AI | Before first live model call |
| OQ-29 | How is the held-out AI corpus created and protected from prompt tuning leakage? | Split by scenario template, freeze hashes before tuning, keep labels/version history and report exclusions. | Evaluation | Before AI comparison |
| OQ-30 | Do the provisional AI thresholds and US$0.05 cost cap fit the selected model and intended learning value? | Treat as gates to validate, not promised results; explain-only or omit AI if safety/value gates fail. | Product + Evaluation | Before enabling AI candidate plans |
| OQ-31 | Is any read-only follow-up evidence tool exposed to the model? | No direct tools initially; application may deterministically execute an allowlisted read suggestion after authorization and record it as new evidence. | Security + AI | Before agentic behavior |

## 5. Product and scope decisions

| ID | Question and why it matters | Provisional default | Decision owner | Needed by |
|---|---|---|---|---|
| OQ-32 | What exact persona and demo workflow demonstrates value without overstating production readiness? | Operations investigator reviews one injected invariant breach from detection through verified recovery. | Product | Before UI/API polish |
| OQ-33 | Which recovery action is safe enough for the first slice? | Non-financial, reversible/idempotent simulator action with authoritative query; no refund/charge/financial compensation. | Product + Security | Before recovery code |
| OQ-34 | When are Redis, Temporal, pgvector, Kubernetes, extra microservices or a sophisticated UI justified? | Exclude until a measured requirement cannot be met cleanly by PostgreSQL, Kafka, Spring Boot and a minimal UI. | Architecture | At each phase review |
| OQ-35 | What licenses and attribution are required for dependencies, datasets, diagrams and sample data? | Use permissive dependencies/assets and document provenance before publishing. | Repository owner | Before public release |

## 6. Critical assumptions to validate

- Simulator recovery endpoints can implement idempotency and authoritative status/state queries.
- One thin invariant can exercise the whole event-to-verified-recovery path without financial effects.
- The selected event-publication mechanism is compatible with the chosen Spring versions and proves crash durability.
- Tenant isolation can be demonstrated with negative tests in a single local environment.
- Local Testcontainers/Docker resources fit the repository owner's machine and trial timeframe.
- An AI model is optional; the project remains coherent and demonstrable with the deterministic investigator alone.

If an assumption fails, update the architecture/roadmap rather than hiding the limitation.

# AegisFlow Threat Model

**Status:** Phase 0 design; no controls are implemented or verified yet

Current P0.2 source implements separate owner/migration/runtime database configuration, tenant composite storage keys, versioned request fingerprints, and immutable outbox intent. Their database/privilege tests are written but not executed in this restricted session; pure producer validation/hash tests passed. Authentication, Kafka tenant/source provenance, recovery policy and AI controls below remain planned. This narrower snapshot supersedes the historical status line without implying complete security verification.

**Last reviewed:** 2026-10-12

**Scope:** AegisFlow control plane and the simulated Order, Payment, and Warehouse services

This document defines the security properties the implementation must preserve. It is a design and test contract, not a claim that AegisFlow is production-secure. See the [system architecture](../architecture/SYSTEM_ARCHITECTURE.md), [failure matrix](../evaluation/FAILURE_MATRIX.md), [known limitations](../KNOWN_LIMITATIONS.md), and [open questions](../OPEN_QUESTIONS.md).

## 1. Security objectives

1. **Tenant confinement:** a principal, event, incident, query, recovery action, approval, and audit record can affect exactly one authorized tenant.
2. **Recovery integrity:** only allowlisted, policy-permitted, versioned API actions may run, against the resource and version that were reviewed.
3. **Evidence integrity:** every material claim and decision must be traceable to identifiable, time-bounded evidence from an allowed source.
4. **Least privilege:** the AegisFlow runtime receives no simulator-database credentials or privileges. It reads and changes simulator state only through scoped, versioned APIs; each component otherwise receives only the Kafka and AegisFlow-owned database permissions it needs.
5. **Accountability:** security-sensitive reads, policy decisions, approvals, attempts, verification results, and administrative changes are attributable and tamper-evident enough for the project threat level.
6. **Safe uncertainty:** timeouts, stale evidence, incompatible schemas, and AI failure cannot silently become permission to act.
7. **Independent verification:** an incident reaches RESOLVED only after an authoritative state read confirms the intended business outcome.

## 2. Scope, actors, assets, and assumptions

### 2.1 In scope

- Operator UI/API, Spring Security resource server, and Keycloak integration.
- AegisFlow ingestion, correlation, incident, investigation, policy, approval, orchestration, verification, and audit modules.
- Kafka topics and consumer groups used by AegisFlow and the simulators.
- PostgreSQL stores owned separately by AegisFlow and each simulator.
- Versioned recovery and state-query APIs exposed by the simulators.
- The future AI model/provider boundary, prompt construction, structured output, and evaluation.
- Build, configuration, secrets, telemetry, and local Docker Compose surfaces.

### 2.2 Out of scope for the portfolio MVP

- Protecting a fully compromised host, database administrator, Keycloak administrator, or model provider.
- Public-internet multi-region deployment, regulatory certification, payment-card processing, or real financial compensation.
- Arbitrary third-party recovery APIs that lack idempotency and authoritative status queries.
- A claim of universal exactly-once external business effects.

### 2.3 Threat actors and failure sources

- An unauthenticated external caller.
- An authenticated user attempting cross-tenant access or role escalation.
- A malicious or careless operator attempting an unsafe or stale approval.
- A compromised simulator or Kafka producer emitting forged, malformed, or misleading events.
- A compromised service credential.
- Malicious text embedded in logs, event fields, customer input, or evidence and later shown to an LLM.
- A model/provider that hallucinates, leaks input, returns malformed output, or is unavailable.
- Ordinary distributed-systems faults that create security-relevant ambiguity.

### 2.4 High-value assets

- Tenant identifiers and tenant-scoped business records.
- Recovery policies, action catalog, approvals, and approval bindings.
- Service credentials, signing material, Keycloak configuration, and model-provider secrets.
- Event envelopes, evidence snapshots, incident history, action attempts, and audit records.
- Customer/order/payment data carried by evidence or telemetry.
- The integrity and availability of recovery decisions.

### 2.5 Required assumptions

- All production-like traffic uses authenticated encrypted transport.
- Keycloak is the trust anchor for user and service identity; its keys and administration are protected.
- Simulator state APIs return authoritative resource versions and recovery APIs honor an idempotency key.
- System clocks are synchronized closely enough for token validation, deadlines, leases, and evidence freshness; tests inject a clock rather than relying on wall time.
- Kafka and PostgreSQL access are authenticated and constrained by network and application credentials.
- Raw evidence remains untrusted even when it came from an authenticated source.

## 3. Trust boundaries

| Boundary | Data crossing it | Principal risk | Required control |
|---|---|---|---|
| User or operator to AegisFlow API | Tokens, tenant context, approvals, queries | Spoofing, cross-tenant access, approval tampering | OIDC validation, server-derived tenant membership, endpoint and domain authorization, request validation, audit |
| Simulator to Kafka to AegisFlow | Versioned business events | Forged source/tenant, replay, duplicates, malformed or malicious fields | Source-owned topic ACLs, schema/envelope checks, configured topic-to-source mapping, application/gateway tenant binding or cryptographic attestation where required, database deduplication, broker-coordinate and payload-digest provenance |
| AegisFlow to simulator APIs | State reads and recovery commands | Credential theft, confused deputy, duplicate side effects | Per-service scoped credentials, tenant/resource binding, idempotency keys, optimistic preconditions, TLS |
| AegisFlow to model provider | Curated evidence and requested structured analysis | Data leakage, prompt injection, hallucination, outage | Minimization/redaction, untrusted-data delimiters, no privileged tools, strict output schema, deterministic validation, timeouts |
| Application to PostgreSQL | Incidents, evidence, policies, attempts, audits | Cross-tenant reads/writes, tampering, secret leakage | Separate DB roles, mandatory tenant predicates, constraints, encryption and backup policy, no credentials in rows/logs |
| Telemetry and operator views | Logs, traces, metrics, incident details | Sensitive-data disclosure, log injection | Structured logging, escaping, redaction, access control, bounded retention |
| Build/configuration to runtime | Images, dependencies, environment secrets | Supply-chain compromise, accidental credential commit | Pinned versions, dependency scanning plan, secret injection, reviewed configuration, least-privilege runtime |

## 4. STRIDE analysis

All mitigations below are **requirements for later phases** unless a future handoff cites passing tests.

| Category | Threat scenario | Security consequence | Required mitigations | Verification obligation |
|---|---|---|---|---|
| Spoofing | Stolen, forged, expired, wrong-audience, or wrong-issuer JWT is used | User or service impersonation | Validate signature, issuer, audience, expiry, not-before, scopes and tenant membership; short-lived service tokens; no trust in client-supplied role claims outside the token | Negative API tests for each invalid token; token-expiry fault test; audit subject and client ID |
| Spoofing | A producer publishes to a forbidden source topic or claims another sourceSystem/tenant in a record | False evidence influences an incident | Give each simulator credential access only to its source-owned topic; validate the envelope source against the consumed topic; bind service-to-tenant authorization in a trusted gateway/application rule or use signed attestation/tenant-specific topics where the threat warrants it. Kafka ACLs do not inspect payload fields, and producer-set headers are untrusted. | Prove the broker denies a credential publishing to a forbidden topic; separately prove a source/tenant claim that lacks trusted binding enters technical quarantine and cannot drive recovery |
| Tampering | Event, evidence, policy, plan, or action parameters change after review | Approval applies to a different action | Immutable/versioned records; canonical plan digest; bind approval to tenant, incident, action type, parameters, target resource/version, evidence snapshot, policy version and expiry | Mutation between approval and execution invalidates approval and returns to AWAITING_APPROVAL or NEEDS_REVIEW |
| Tampering | Replay or duplicate delivery creates repeated effects | Duplicate recovery or state corruption | Database-enforced event deduplication; durable action-attempt identity; API idempotency key; authoritative verification | Duplicate and crash-replay tests prove one external side effect |
| Repudiation | User denies approval or service denies action | No defensible incident history | Append-only audit events with actor, tenant, action, request/trace IDs, timestamp, result and relevant hashes; synchronized time; restricted audit writes | Audit completeness tests trace incident from detection through verification without secret values |
| Information disclosure | Tenant A reads Tenant B incident/evidence via ID enumeration, search, cache, trace, or export | Cross-tenant data breach | Tenant-scoped repository/API methods, composite tenant/resource keys, object authorization on every access, cache keys including tenant, redaction, database isolation decision | Cross-tenant tests cover direct IDs, lists, search, exports and correlated trace views; expected result is deny and no existence leak |
| Information disclosure | Prompts, logs, traces, or errors contain secrets or excessive business data | Credential or personal-data leakage | Evidence allowlist and minimization; secret/PII redaction; structured safe errors; telemetry filters; provider retention/training controls recorded | Seed canary secrets and assert they do not appear in prompts, logs, traces, API errors, or AI output |
| Denial of service | Event flood, poison event, retry storm, expensive model request, or hot tenant exhausts shared capacity | Delayed detection/recovery for other tenants | Payload and rate limits, bounded retries with jitter, dead-letter/quarantine path, consumer backpressure, per-tenant quotas/fairness, model concurrency and token budgets | Load/fault tests show bounded queues and no unbounded retry; one tenant cannot starve another beyond the agreed SLO |
| Denial of service | Kafka, database, Keycloak, simulator, or model provider is unavailable | Stale evidence and unsafe automation pressure | Durable state, circuit breakers where appropriate, explicit degraded state, expiry/freshness checks, operator visibility, deterministic operation without AI | Failure-matrix tests show safe pause or retry and prohibit acting on stale/partial evidence |
| Elevation of privilege | Viewer or investigator invokes/approves/executes recovery; service token is reused for administration | Unauthorized business effect | Server-side RBAC plus domain authorization, distinct scopes for read/investigate/approve/execute/admin, separate service accounts, deny by default | Role matrix tests every sensitive endpoint and domain transition; unauthorized attempt changes no incident/action state |
| Elevation of privilege | LLM text requests a tool call, policy bypass, credential, or direct database change | Model becomes a privileged confused deputy | Treat model as advisory only; no database credentials; no general-purpose tools; output constrained to schema/action catalog; deterministic policy, approval and executor own authorization | Malicious-log suite yields zero unauthorized calls and all invalid proposals are rejected/audited |

### 4.1 Additional supply-chain and operational threats

- Dependencies and container images can be compromised or unsupported. Pin compatible versions, keep a software bill of materials, scan dependencies/images, and document upgrade decisions.
- Debug endpoints and development defaults can expose data. Production-like profiles must disable unnecessary actuator exposure, sample credentials, verbose errors, and model prompt logging.
- Backups can bypass tenant controls. Encrypt and access-control them, define restore tests, and document retention.
- Operators can misuse legitimate access. Use least privilege, separation of duties for sensitive recovery, reason fields, and audit review.

## 5. Mandatory security invariants

### 5.1 Tenant isolation

Tenant context comes from validated identity and server-side membership, never solely from a request field. Every persisted aggregate and unique/idempotency key includes tenantId where collision or access is possible. Correlation may only join events with the same tenantId and businessTransactionId. Repository methods, caches, Kafka consumption, metrics labels, traces, exports, and administrative tools require a documented tenant strategy.

Kafka authentication and ACLs establish which client may access a topic; they do not validate `tenantId` or `sourceSystem` inside a record, and an ordinary consumer does not receive a trustworthy original-producer identity. Source-owned topics can constrain source credentials, but tenant provenance on a shared topic still needs an application/gateway allowlist, protected attestation, or stronger topic isolation. Producer-supplied record headers alone are not proof of identity or tenant authority.

Whether PostgreSQL row-level security is added as defense in depth remains an [open question](../OPEN_QUESTIONS.md). Application-layer authorization is mandatory even if row-level security is adopted. A missing tenant context fails closed.

### 5.2 Identity, roles, and scoped credentials

The minimum conceptual roles are:

- **Viewer:** read tenant-scoped summaries.
- **Investigator:** inspect evidence and request/revise a candidate plan.
- **Approver:** approve or reject an eligible plan; cannot silently rewrite it.
- **Executor service:** execute only READY_TO_EXECUTE plans allowed by policy.
- **Administrator:** configure scoped policy and membership; administration does not imply unrestricted business recovery.

Exact Keycloak roles/scopes remain to be decided, but endpoint checks alone are insufficient: state-transition and target-tenant authorization must also occur in the domain service. Each simulator integration uses a different service credential limited to required read and recovery endpoints. Kafka producer, consumer, database migration, runtime, and monitoring identities are separate where practical.

### 5.3 No simulator-database access from the AegisFlow runtime

AegisFlow may access its own database and call versioned simulator APIs. Its runtime configuration, containers, and service identities must contain no credentials or privileges for the Order, Payment, or Warehouse databases, including read-only access. State reads and recovery writes both go through the owning service's API. A dedicated integration-test harness may inspect simulator databases to assert outcomes, but that identity is separate and must never be available to AegisFlow runtime code. Recovery APIs must validate:

- tenant and target resource;
- allowed action type and parameters;
- stable action-level idempotency key and a distinct per-invocation action-attempt ID;
- expected resource version or other precondition;
- caller scope;
- request expiry where appropriate.

An external timeout has unknown outcome. The action attempt records outcome UNKNOWN, the recovery action records OUTCOME_UNKNOWN, and the incident moves to VERIFYING; the worker queries authoritative state before any retry. If verification remains inconclusive, the incident moves to NEEDS_REVIEW. RESOLVED is possible only after authoritative verification.

### 5.4 Approval and policy binding

Approval must bind to an immutable canonical digest containing at least:

- tenantId, incidentId and material incident revision (separate from the lifecycle compare-and-set version);
- action type and canonical parameters;
- target service, resource ID and expected resource version;
- evidence snapshot/version and its freshness deadline;
- policy ID/version/hash, policy-decision ID and required approval rule;
- plan creation and expiry time.

Approval bindings, event-payload fingerprints, and idempotent request fingerprints require documented, versioned canonicalization profiles. The stored digest metadata identifies the profile/version, and shared test vectors must prove that semantically material changes alter the digest while explicitly insignificant serialization differences, such as JSON object-key order, do not. Raw serialization bytes are not a stable canonical form unless a contract explicitly makes them so.

Execution re-evaluates current policy, approval validity, target version, evidence freshness, actor/service authorization, and idempotency identity. Any material change invalidates the old approval. Policy rules and approval requirements live in deterministic code/configuration outside LLM control. The MVP forbids automatic financial compensation.

### 5.5 Evidence provenance and trust

Each evidence item records a stable evidence ID, tenant, source, collection method, source timestamp, ingestion/observation timestamp, schema/version, resource version when available, integrity metadata when available, and freshness classification. Authentication establishes origin, not truth: contradictory or implausible evidence must be retained and surfaced.

Evidence used for execution is snapshot-bound. A stale snapshot cannot be refreshed silently while retaining an old approval. Authoritative state reads used for final verification are preserved separately from the initiating event and AI narrative.

Before envelope validation succeeds, claimed `tenantId`, `eventId`, `sourceSystem`, and business identifiers are untrusted and may be absent. Technical quarantine therefore keys the record by trusted broker topic/partition/offset, observation time, content digest, validation failure, and—only as clearly marked claims—any parseable envelope identifiers. It emits an operational/security signal, not a tenant business incident. A business incident may be created only after evidence is validly tied to an authorized tenant, business transaction, and invariant.

### 5.6 AI prompt-injection boundary

Event fields, logs, stack traces, customer text, URLs, model output, and prior summaries are untrusted data. Text such as “ignore policy,” “approve this,” or fake tool instructions has no authority.

The future AI integration must:

1. select and minimize evidence server-side;
2. delimit untrusted evidence and label source/evidence IDs;
3. request a versioned structured response, not executable prose;
4. require citations for factual claims and expose uncertainty;
5. support ABSTAIN when evidence is insufficient or conflicting;
6. have no direct database, Kafka, shell, secret, approval, or recovery capability;
7. validate schema, size, action catalog, parameter types and evidence references;
8. run deterministic policy, authorization, approval, execution and verification after model output;
9. audit the model/prompt-template version, evidence IDs, token use, latency and validation result, subject to data-minimization rules.

Prompt wording is defense in depth, not the security boundary. The enforceable boundary is the model's lack of privilege plus deterministic validation.

### 5.7 Secrets, audit, and observability

- Secrets enter through an approved secret/configuration mechanism, never source control, prompts, event payloads, exception messages, or ordinary audit fields.
- Logs use structured fields and escape untrusted text. Avoid raw tokens, authorization headers, passwords, connection strings and full sensitive payloads.
- Audit records include allow/deny results, not just successful actions. Access to audit/evidence is itself authorized and, for sensitive operations, audited.
- Trace IDs and correlation IDs aid investigation but are not authentication or authorization tokens.
- Metrics avoid unbounded identifiers such as tenantId, eventId or orderId as labels unless the observability design explicitly controls cardinality and disclosure.

## 6. Abuse cases and expected safe response

| Abuse case | Expected safe response |
|---|---|
| Tenant A supplies Tenant B incident ID | Generic not-found or forbidden according to the API policy; no data, timing-sensitive detail, state transition, or model call |
| Approver changes action parameters in the approval request | Reject; only the stored plan digest can be approved |
| Previously approved plan is replayed after policy/resource/evidence changes | Refuse execution; invalidate or expire approval and require investigation/reapproval |
| Log line tells the model to mark the incident safe and call a URL | Preserve as quoted evidence; do not call URL; model proposal cannot bypass schema/policy |
| Recovery endpoint times out | Mark outcome unknown, enter VERIFYING, query authoritative status; never blind retry |
| Service token for Warehouse calls Payment recovery | Payment denies by audience/scope; AegisFlow records failure without escalating privilege |
| Unknown event schema is labeled as a valid payment | Put the broker record in technical quarantine and emit an operational/security signal; untrusted claimed tenant/event fields cannot route it or create a business incident, and it cannot contribute trusted facts to an action |
| AI provider is unavailable | Deterministic investigation remains available; no weakening of policy or approval |

## 7. Verification gates

Before any automatic recovery is enabled, later phases must show:

- all access-control matrix and cross-tenant negative tests pass;
- zero simulator-database credentials or privileges in AegisFlow runtime configuration; any test-harness inspection identity is separate and unavailable to runtime code;
- zero unauthorized writes and zero duplicate external side effects in the required failure suite;
- every executed action is linked to current policy, required approval, scoped identity, attempt ID and authoritative verification;
- token expiry, approval replay, policy/version change, malicious evidence, incompatible schema and timeout ambiguity fail safely;
- seeded secrets do not appear in retained prompts, model output, logs, traces or ordinary API errors;
- audit reconstruction covers every sensitive state transition and denial;
- dependency and container scans have no unreviewed critical finding for the chosen release.

These are acceptance criteria, not observed results. Phase 0 has run none of them.

## 8. Residual risks

Even after planned controls, compromised identity providers, privileged administrators, databases, hosts, dependencies or authoritative simulator APIs can subvert the system. At-least-once messages and remote timeouts always require idempotency and reconciliation; they do not become exactly-once effects. AI may remain convincingly wrong despite citations. The portfolio project therefore defaults uncertainty to NEEDS_REVIEW and must not represent itself as production-ready.

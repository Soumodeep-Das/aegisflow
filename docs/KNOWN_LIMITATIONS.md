# Known limitations and residual risks

Scope: the verified local P0.5 implementation. See [current status](STATUS.md); superseded limitations are preserved in [reference history](history/REFERENCE_THROUGH_P05.md).

## Local-development security

- Kafka authenticates with SASL/PLAIN over unencrypted SASL_PLAINTEXT. Use synthetic local data only.
- Loopback host publication, owner-only database networks, non-root/read-only apps and restricted roles reduce accidental exposure; they do not protect against a privileged Docker/host/database administrator.
- Eighteen generated passwords and stable cluster identity live in ignored `.env.development`. Docker environment credentials remain inspectable by privileged operators. No tested rotation/secret-manager workflow is provided.
- The app process has its own migration and runtime configuration; runtime grants are not a sandbox against arbitrary code in that JVM.
- The permanent-broker **forbidden-write probe remains unverified**. Existing persistent ACL inventory, authentication and metadata-denial checks do not prove denied writes. Disposable Kafka tests exercised write rejection separately.

## Tenant and source trust

- Core binds configured source topics to sources and explicit tenant allowlists; payload tenant IDs and producer headers alone are not trusted authority.
- A compromised allowed producer can still fabricate facts within its admitted tenant set.
- Composite keys, tenant-scoped queries and negative tests do not substitute for public API authentication. Keycloak/OIDC, user roles, endpoint authorization, RLS and hostile multi-tenant deployment are not implemented.

## Durability and concurrency scope

- PostgreSQL and Kafka are independent transactions. Broker acknowledgement followed by failed local completion can redeliver an event; stable identity and database idempotency handle repeats.
- One Kafka broker, replication factor/minimum ISR 1 and seven-day topic retention are local trade-offs, not high availability.
- Retained-volume restart passed; backup restoration, storage loss, multi-host failure and disaster recovery did not.
- Core's serialized transaction gates and bounded scans favor correctness and learning over scale. No throughput, p95 latency, fairness or capacity result is claimed.

## Operational readiness

- Windows 11/PowerShell 7/Java 21 host workflow is verified; a clean arbitrary-machine, Linux or CI deployment is not thereby proven.
- Health indicates process/dependency readiness, not complete business correctness. Containers do not automatically start on laptop boot under the current restart policy.
- No full metrics/dashboard/alerting platform, retention automation or supported quarantine replay UI exists.
- The demonstration temporarily adds an exact synthetic tenant and starts owner command containers. It consumes additional resources, retains synthetic records and briefly restarts the stack. On failure, preserve its overlay/logs before retrying.

## Evidence and lifecycle

- Only INV-002 is implemented. Payment deadline, order terminalization and cancellation-cleanup evaluators remain designs.
- Missing observed evidence is not proof of a failed Warehouse operation.
- Incidents currently remain `DETECTED`. Late on-time evidence can produce `SATISFIED` while retaining the original violation; no authoritative verification or `RESOLVED` transition exists.
- Append-only runtime grants and linked evidence are not cryptographic tamper proof against administrators.

## Future functionality and unmeasured behavior

Full simulator choreography, authoritative APIs, deterministic investigation, recovery/approval execution, automatic compensation, AI, frontend and cloud deployment are absent. The [16-scenario matrix](evaluation/FAILURE_MATRIX.md#current-coverage) separates partial P0 coverage from P1/P2 obligations. The 182-test default reactor is not proof of all those scenarios, universal exactly-once effects, production security or AI safety.

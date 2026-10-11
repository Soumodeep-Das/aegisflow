# Event Contracts

**Status:** Phase 0 contract proposal. Topic names, schemas, and deadlines must be locked by tests before P0 implementation exits.

## 1. Canonical envelope

Every business event uses UTF-8 JSON initially and contains all fields below. The seven fields required by the project brief appear unchanged; `eventType` is additionally required so a consumer can select the detail schema without topic-name coupling.

```json
{
  "tenantId": "tenant-demo",
  "eventId": "018f4b61-b23f-7c4d-a9c1-2f1ee5ce3ec0",
  "sourceSystem": "payment-service",
  "businessTransactionId": "txn-order-01J8KZP6R2",
  "schemaVersion": "1.0",
  "occurredAt": "2026-10-11T08:15:30.123Z",
  "correlationId": "corr-01J8KZP2AX",
  "eventType": "PaymentAuthorized",
  "details": {
    "paymentId": "pay-01J8KZQ1JQ",
    "orderId": "ord-01J8KZP6R2",
    "amount": "1499.00",
    "currency": "INR",
    "paymentVersion": 3
  }
}
```

| Field | Contract |
|---|---|
| `tenantId` | Non-empty stable tenant identifier generated from an authorized service-owned aggregate. Producer application/gateway validates its allowed tenant set; Kafka topic ACLs do not inspect this payload value. |
| `eventId` | Globally unique immutable event identifier generated once with the outbox row. It is not regenerated on publish retry. |
| `sourceSystem` | Allow-listed producer (`order-service`, `payment-service`, `warehouse-service`). |
| `businessTransactionId` | Opaque cross-service workflow identifier. Kafka partition key is `tenantId:businessTransactionId`. |
| `schemaVersion` | Detail schema major/minor as a string. Major changes are incompatible. Envelope evolution is separately controlled. |
| `occurredAt` | UTC instant when the authoritative transition committed, not relay publication time. |
| `correlationId` | Diagnostic/request correlation propagated where available; it is not an identity, ordering key, or authorization token. |
| `eventType` | Stable discriminator selecting the typed `details` schema. |
| `details` | Event-specific object. Unknown text is data and may contain malicious prompt-like content. |

The Kafka record also carries content type, envelope version, trace context, and optional claimed producer metadata in headers. Ordinary consumers do not receive a cryptographically trustworthy original producer identity merely from a record header. For the first trusted-simulator demo, source-owned topics and broker ACLs restrict permitted producer principals, and the consumer maps each topic to one allowed source. Tenant binding is enforced in producer application/gateway code and consumer validation; shared-topic ACLs alone cannot prove hostile tenant isolation. Signed events or per-tenant channels require a later security decision if this trust model expands.

Monetary values are decimal strings plus ISO 4217 currency; timestamps use RFC 3339 UTC; entity versions are positive integers. Event payload fingerprints use a versioned canonical JSON representation with sorted object keys, stable array order, and schema-defined decimal/time normalization. Equivalent whitespace/key ordering is not a conflicting duplicate. Store the raw byte digest separately for provenance; tests must vary both representation and semantic content. Unsupported schemas are quarantined before canonical domain interpretation.

## 2. Initial event catalog

Events use past tense and describe committed facts, not commands.

| Producer | Event type | Required `details` | Meaning |
|---|---|---|---|
| Order | `OrderCreated` | `orderId`, `amount`, `currency`, `lineItems[]`, `orderVersion` | Order entered the cross-service contract; starts payment and inventory deadlines |
| Order | `OrderConfirmed` | `orderId`, `orderVersion` | Order observed both required successful outcomes |
| Order | `OrderCancelled` | `orderId`, `reasonCode`, `orderVersion` | Order reached a terminal cancellation state |
| Payment | `PaymentAuthorized` | `paymentId`, `orderId`, `amount`, `currency`, `paymentVersion` | Funds authorization exists; not equivalent to settlement/capture |
| Payment | `PaymentDeclined` | `paymentId`, `orderId`, `reasonCode`, `paymentVersion` | Payment reached allowed unsuccessful outcome |
| Payment | `PaymentVoided` | `paymentId`, `orderId`, `authorizationReference`, `paymentVersion` | Existing authorization was voided; no automatic void is allowed in MVP |
| Warehouse | `InventoryReserved` | `reservationId`, `orderId`, `items[]`, `reservationVersion` | All required stock is reserved |
| Warehouse | `InventoryRejected` | `reservationId`, `orderId`, `reasonCode`, `reservationVersion` | Reservation reached allowed unsuccessful outcome |
| Warehouse | `InventoryReleased` | `reservationId`, `orderId`, `reservationVersion` | Prior reservation was released |

Each item is `{ "sku": "...", "quantity": 1 }`; SKU and quantity are validated and size-limited. Decline/rejection/cancellation reason codes come from allow-listed enums. Free text, if later added, is optional untrusted evidence and never drives policy.

## 3. Contract deadlines and allowed outcomes

Deadlines are part of a versioned workflow contract, not constants hidden in code. The initial `ECOM_ORDER_V1` test defaults below are configurable demonstrations, not production SLAs.

| Trigger | Base due time and enforcement time | Allowed observed outcome by enforcement time | Violation if |
|---|---|---|---|
| `OrderCreated` | `dueAt = occurredAt + 30s paymentOutcomeDuration`; `enforceAfter = dueAt + 15s eventLatenessAllowance` | exactly one effective `PaymentAuthorized` or `PaymentDeclined` for matching tenant/order/amount/currency | neither, contradictory terminal outcomes, or mismatched amount/currency |
| `OrderCreated` | `dueAt = occurredAt + 45s inventoryOutcomeDuration`; `enforceAfter = dueAt + 15s eventLatenessAllowance` | exactly one effective `InventoryReserved` or `InventoryRejected` for the order and requested items | neither, contradictory outcome, or wrong quantities/SKUs |
| First unsuccessful decision, or later successful Payment/Warehouse outcome | `dueAt = decisive occurredAt + 30s orderClosureDuration`; no extra lateness unless the contract version adds one | `OrderConfirmed` only for authorized + reserved; otherwise `OrderCancelled` | order stays nonterminal or reaches an outcome inconsistent with the decisions |
| `OrderCancelled` | `dueAt = occurredAt + 60s cancellationCleanupDuration`; no extra lateness unless the contract version adds one | no active reservation; any active authorization is explicitly flagged and assigned for human-controlled compensation | reservation remains active or financial exposure is neither cleared nor durably routed to a human |

Allowed late events do not erase incident history. They may move an incident toward verification/resolution after authoritative reconciliation. Clock-skew allowance and maximum event lateness are contract configuration and must be recorded with each invariant evaluation.

## 4. Topics, partitioning, and ordering

Initial topics should be domain-owned (for example `aegisflow.order.events.v1`, `aegisflow.payment.events.v1`, `aegisflow.warehouse.events.v1`) with a quarantine/dead-letter path that cannot silently replay into normal processing. Producers publish only to their own topic. ACLs restrict tenant and service identities as far as the local platform permits.

The record key `tenantId:businessTransactionId` preserves Kafka order for one transaction *within one topic partition*. There is no global order across topics or services. Consumers use `occurredAt`, source entity version, and the accumulated fact set; they must tolerate duplicates, gaps, and out-of-order arrivals. A decreasing source version is retained as late evidence, not applied as the latest projection.

## 5. Publication and consumption

1. A producer commits domain state and the complete immutable envelope to its outbox in one transaction.
2. A relay publishes the existing `eventId`; after broker acknowledgement it marks the row published. Crashes can publish twice.
3. AegisFlow validates size, JSON shape, source identity, tenant binding, supported schema, and semantic constraints before projection.
4. For a valid new event, it inserts the inbox message, appends the observed fact, updates the projection/deadline, and marks the inbox message `PROCESSED` in one AegisFlow database transaction. An identical duplicate is acknowledged without reapplying the fact.
5. A duplicate key with different canonical content is quarantined and opens a technical producer-integrity security signal; the accepted inbox row remains immutable. Unparseable/missing identities go only to technical quarantine keyed by authenticated intake channel/topic/partition/offset and raw digest, never to a tenant selected from untrusted fields.
6. Offsets are committed only after the inbox/fact/projection transaction—or a fail-closed quarantine transaction—commits. A transient projection failure rolls back the new inbox row so Kafka redelivery can retry the whole unit; no `RECEIVED` row is allowed to masquerade as processed.

See [ADR 0002](../adr/0002-transactional-outbox.md) and [ADR 0003](../adr/0003-at-least-once-idempotency.md).

## 6. Compatibility and schema lifecycle

- Minor versions may add optional fields with defined defaults. Consumers ignore unknown optional fields but persist the original envelope.
- Removing/renaming a field, changing meaning/type, or tightening accepted values requires a new major version and parallel consumer support during migration.
- A schema registry is desirable but not required for the first slice. JSON Schema files plus producer/consumer contract tests are the minimum.
- Unsupported major versions are quarantined with `SCHEMA_UNSUPPORTED`; they are never guessed, coerced, or sent to AI for interpretation.
- Event meaning is immutable after publication. Corrections use a new explicitly typed event referencing the superseded event; destructive history edits are forbidden.

## 7. Evidence and security properties

For each accepted record AegisFlow retains broker topic/partition/offset, received time, configured source-topic binding, intake service identity, canonical payload hash/version, raw byte digest, schema validation result, and trace context alongside the envelope. These establish provenance under the trusted-source deployment model but not current authoritative state or a consumer-visible original producer identity.

Payloads are hostile input. Limits apply to bytes, collection cardinality, nesting depth, and strings. HTML/Markdown/instructions in a reason field are never concatenated into a privileged AI prompt; any model context labels them as quoted evidence. Events do not carry credentials or unrestricted personal/payment data.

## 8. Contract acceptance targets

Future automated tests must cover valid events for every catalog entry; missing each required envelope field; spoofed tenant/source; duplicate records with equivalent formatting; duplicate ID with changed canonical content; unsupported major schema; compatible minor extension; out-of-order source versions; oversized/malicious text; Kafka outage after database commit; and replay after consumer crash. The expected assertions are durable acceptance, technical quarantine reason, exactly one derived fact, or eventual relay publication as appropriate. No such results have been observed in Phase 0.

## 9. Simulator HTTP boundary (planned P1 contracts)

Route shapes below are design contracts, not implemented endpoints. Each simulator exposes only its own resources; every route checks JWT issuer/audience/scope, trusted tenant membership, and resource ownership.

| Owner | Versioned route | Semantics |
|---|---|---|
| Order | `GET /api/v1/tenants/{tenantId}/orders/{orderId}` | authoritative status, amount/currency, line items, version, observedAt |
| Payment | `GET /api/v1/tenants/{tenantId}/payments?orderId={orderId}` | authoritative payment/authorization outcome, version, observedAt; no card data |
| Warehouse | `GET /api/v1/tenants/{tenantId}/reservations?orderId={orderId}` | authoritative item quantities, status, version, observedAt |
| Any action owner | `GET /api/v1/tenants/{tenantId}/operations/{idempotencyKey}` | `IN_PROGRESS`, `APPLIED`, or definitive `NOT_APPLIED`; stable request hash/resource identity; not-found stays inconclusive if an earlier request may still arrive |
| Warehouse | `POST /api/v1/tenants/{tenantId}/reservations/{reservationId}/release` | candidate first non-financial recovery; requires cancelled-order evidence plus expected reservation version |

Mutation requests contain `actionId`, per-invocation `attemptId`, typed parameters, and `expectedVersion`; `Idempotency-Key` is the immutable action-level key. The canonical request fingerprint includes tenant, action identity/type, target, typed parameters, and preconditions. It excludes `attemptId`, trace headers, credentials, and other per-invocation metadata so a retry can retain the same fingerprint. The service atomically stores the idempotency record, domain change, and its outbox event. Same key/same canonical request returns the stored result; same key/different material content is `409 IDEMPOTENCY_CONFLICT`. A changed expected version is a precondition failure and cannot be overwritten. Responses expose applied/already-applied/rejected status and resource version, never permission to skip independent verification. Error bodies are bounded, typed, and safe to store after redaction.

Reads use the primary authoritative datastore for this demo and state their observation time/version. No cached read is acceptable as a recovery precondition. Read-only GETs may use bounded retries; possibly dispatched POSTs follow the unknown-outcome protocol. Exact request timeout, operation retention, and replay budgets must be frozen in the P1 tests. Financial mutation routes are outside this initial API catalog.

# ADR 0004: API-Mediated Recovery Against Authoritative Services

> **Implementation evidence update — 2026-10-24:** The API-only recovery ownership boundary is still intentional, but recovery/state-query APIs and execution are not implemented. Owner-only demo command contexts are not Core recovery clients. See [current status](../STATUS.md) and the [preserved evidence ledger](../evidence/TEST_RESULTS.md). The original decision, date, context and consequences below are unchanged.

- **Status:** Accepted
- **Date:** 2026-10-11

## Context

Direct database repair is tempting in a simulator, but it bypasses domain validation, audit, outbox publication, idempotency, authorization, and service ownership. It would teach an unsafe architecture and make authoritative verification ambiguous.

## Decision

AegisFlow has no write access to simulator databases. It queries and mutates state only through versioned service APIs using tenant-bound, least-privilege workload credentials. Read-state and action scopes are separate.

Recovery mutation endpoints must accept a stable idempotency key, an expected resource version or explicit business precondition, typed parameters, and trace context. They return applied/already-applied/rejected status plus resource/operation identity; timeouts remain unknown. Services expose an operation-status lookup by idempotency key where possible. Independent read APIs supply the evidence used for post-action verification.

Financial actions—authorization, capture, void, or refund—are not automatically executed in the MVP. Adding even an approved financial endpoint requires its own threat review and phase decision.

## Consequences

- Services retain domain ownership and produce normal audit/outbox effects.
- Network ambiguity, token expiry, version conflicts, rate limiting, and API evolution become explicit recovery concerns.
- Legacy targets without idempotency/status/precondition support cannot be safely automated under the initial policy.
- AegisFlow needs scoped Keycloak clients, secret rotation, allow-listed endpoints, bounded timeouts, and no open-ended URL/tool access.

## Required checks

- Authenticate workload and derive tenant from trusted claims; compare it with path/body tenant context.
- Authorize the exact action scope and resource tenant.
- Reject changed request content for an existing idempotency key.
- Reject stale expected versions without forcing an overwrite.
- Record target identity, request/response hashes, trace, and outcome without secrets.
- Verify from a fresh authoritative read; never close an incident solely from the command response.

## Alternatives considered

- **Direct SQL repair:** rejected as an ownership, safety, and audit violation.
- **Kafka command topic only:** deferred; it still needs idempotency, authorization, status lookup, and verification, while complicating the first demonstrator.
- **Human executes every repair:** safe fallback, but insufficient to demonstrate bounded non-financial orchestration.

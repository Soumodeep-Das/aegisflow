# ADR 0005: AI Is an Evidence-Bound Advisory Investigator

- **Status:** Accepted
- **Date:** 2026-10-11

## Context

AI can help summarize multi-source evidence and suggest hypotheses, but model output is probabilistic and vulnerable to hallucination, stale context, prompt injection in logs/events, provider failure, and data leakage. Authorization and external mutation require deterministic, reviewable guarantees.

## Decision

Run a deterministic investigator as the baseline. An optional Spring AI investigator receives only a tenant-scoped, minimized, redacted evidence bundle. Untrusted strings are delimited and labeled as evidence, never instructions. The model must return a strict schema containing hypothesis codes, cited evidence IDs, uncertainty, proposed action *types* from an allow-list, and an abstention option.

Validated output is stored as an advisory investigation. It cannot change incident/action state directly. Deterministic code independently verifies citations and preconditions, constructs a typed plan, evaluates versioned policy, obtains required human approval, invokes APIs, and verifies state. The model has no service credentials, database write access, arbitrary URL access, action tools, policy-edit capability, or authority to mark success.

## Consequences

AI downtime or invalid output degrades to deterministic investigation/manual review rather than blocking ingestion or weakening controls. Model value can be evaluated against a reproducible baseline for accuracy, supported claims, appropriate abstention, latency, and cost. Redaction and evidence selection may reduce context; unsupported conclusions must be rejected.

## Prompt-injection and provenance controls

- Treat event details, logs, exception text, operator notes, and retrieved documents as hostile quoted data.
- Use fixed/versioned system instructions and schema validation; cap content size and allowed evidence types.
- Accept citations only when IDs belong to the same tenant and incident and were included in the request.
- Resolve proposed action names through a fixed enum; text cannot select an endpoint or supply credentials.
- Record evidence-set hash, prompt-template version, model/provider identifier, structured output hash, latency, token/cost data, and validation result.
- Do not log full prompts or sensitive evidence by default.

## Evaluation gate

The AI investigator is not enabled for recovery proposals until an evaluation dataset includes ordinary failures, ambiguous/stale evidence, missing facts, contradictory state, and malicious instruction-like log text. Report measured results separately from targets. A low-confidence answer should abstain; no metric can authorize bypassing policy or approval.

## Alternatives considered

- **LLM agent with direct recovery tools:** rejected because prompt injection or model error could cause business effects.
- **LLM decides approval/policy:** rejected because governance must be deterministic and attributable.
- **No AI at all:** retained as the required baseline and safe fallback, but does not explore the project's AI-assisted research question.

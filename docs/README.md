# Documentation index

Start with [current implementation status](STATUS.md). Documents distinguish **as built**, **historical evidence**, and **planned design**; a proposed safety rule is not a claim that its future API exists.

## Reading paths

| Reader | Suggested order |
|---|---|
| First-time visitor / interviewer | [Project introduction](../README.md) → [status](STATUS.md) → [architecture](architecture/SYSTEM_ARCHITECTURE.md) → [acceptance evidence](evidence/TEST_RESULTS.md#p05-acceptance-evidence) |
| Developer setting up locally | [Runbook](LOCAL_DEPLOYMENT.md) → [limitations](KNOWN_LIMITATIONS.md) → [handoff](HANDOFF.md) |
| Architecture reviewer | [System design](architecture/SYSTEM_ARCHITECTURE.md) → [module boundaries](architecture/MODULE_BOUNDARIES.md) → [data model](architecture/DATA_MODEL.md) → [events](architecture/EVENT_CONTRACTS.md) → [ADR index](#architecture-decisions) |
| Correctness/security reviewer | [Invariants](architecture/INVARIANTS.md) → [threat model](security/THREAT_MODEL.md) → [scenario coverage](evaluation/FAILURE_MATRIX.md#current-coverage) → [test ledger](evidence/TEST_RESULTS.md) |
| Backend learner | [Codebase walkthrough](learning/CODEBASE_GUIDE.md#current-end-to-end-walkthrough) → [subsystem navigation](learning/CODEBASE_GUIDE.md#learning-navigation) → its 90 answered questions |
| Future maintainer / agent | [AGENTS.md](../AGENTS.md) → [handoff](HANDOFF.md) → [roadmap](ROADMAP.md) → [open decisions](OPEN_QUESTIONS.md) |

## Document roles

- [PRD](requirements/PRD.md): original requirements with current implementation mapping, not blanket fulfillment.
- [Implementation plan](roadmap/IMPLEMENTATION_PLAN.md): delivered phase gates and separately planned P1/P2 specifications.
- [Recovery state machine](architecture/RECOVERY_STATE_MACHINE.md): future transition design; only `DETECTED` incident persistence exists today.
- [AI evaluation](evaluation/AI_EVALUATION.md): unexecuted future evaluation protocol.
- [Test results](evidence/TEST_RESULTS.md): exact runs, counts, errors, corrections and provenance.
- [Development journal](devlog/DEVELOPMENT_JOURNAL.md): dated development history.
- [Historical handoffs](history/HANDOFF_THROUGH_P05.md) and [reference snapshots](history/REFERENCE_THROUGH_P05.md): preserved superseded material, not operational instructions.
- [Documentation review](DOCUMENTATION_REVIEW.md): findings, refinement decisions and integrity checks.
- [Phase 0 review](evaluation/PHASE0_REVIEW.md): historical baseline review.
- [Kafka smoke client](../tools/kafka-smoke/README.md): tool scope and why disposable write probes must not be run casually against retained business topics.

## Architecture decisions

| ADR | Decision |
|---|---|
| [0001](adr/0001-modular-control-plane.md) | Modular Core, independent simulators |
| [0002](adr/0002-transactional-outbox.md) | Explicit transactional outbox |
| [0003](adr/0003-at-least-once-idempotency.md) | At-least-once and durable idempotency |
| [0004](adr/0004-api-mediated-recovery.md) | Future recovery through owner APIs, not SQL repair |
| [0005](adr/0005-ai-advisory-boundary.md) | Future AI is advisory |
| [0006](adr/0006-technology-baseline-and-version-pinning.md) | Version baseline and pinning |
| [0007](adr/0007-foundation-profiles-and-stage-boundaries.md) | Foundation versus PostgreSQL profiles |
| [0008](adr/0008-persistence-primitives-and-database-roles.md) | Persistence primitives and roles |
| [0009](adr/0009-local-kafka-listeners-and-source-topics.md) | Local listeners, topics and trust |
| [0010](adr/0010-immutable-inbox-durable-projection.md) | Immutable inbox and separate projection |
| [0011](adr/0011-clock-driven-invariant-evaluations.md) | Durable clock-driven evaluation |
| [0012](adr/0012-durable-incident-timelines.md) | Recoverable incident/timeline persistence |
| [0013](adr/0013-local-application-images-and-deployment-gates.md) | Images and progressive local deployment |

## Small glossary

- **Outbox:** publication intent committed in the same local transaction as business state.
- **Inbox:** durable accepted event identity/payload; not a projection completion flag.
- **Projection:** derived tenant-scoped state reconstructed from observed facts.
- **Invariant / evaluation:** a versioned rule / its immutable assessment at a recorded observation time.
- **Incident episode:** deduplicated historical breach context; current P0 episodes remain `DETECTED`.
- **Provenance:** the event, source, broker coordinates, timestamps and hashes supporting a fact.
- **Authoritative verification:** a fresh read from the owning service proving current state; planned, not supplied by Core's projection.
- **Fencing:** a monotonic claim token preventing a stale worker from recording completion.
- **Idempotency:** repeated logical input returns/reuses the original durable result rather than creating another effect.

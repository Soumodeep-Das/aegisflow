# ADR 0009 — Local Kafka listeners, source topics and verification boundary

**Status:** Accepted implementation choice for approved P0.3; real broker verification pending.
**Date:** 2026-10-17
**Builds on:** [ADR 0002](0002-transactional-outbox.md), [ADR 0003](0003-at-least-once-idempotency.md), [ADR 0006](0006-technology-baseline-and-version-pinning.md), and [event contracts](../architecture/EVENT_CONTRACTS.md).

## Context

The persistent PostgreSQL prerequisite passed at checkpoint `1043a51`. The next task is a reproducible Kafka setup that can serve Windows-host applications and container clients while preserving the existing service-owned database networks and volumes. Source topics and restricted producer identities are already part of the accepted event contract. The current coding session cannot access Docker; host execution must establish runtime evidence before delivery/inbox acceptance.

## Decision

`compose.kafka.yaml` is a companion Compose file with the existing `aegisflow-foundation` project name. It can run alone without PostgreSQL credentials, or be merged explicitly with `compose.yaml`. The existing file and its four databases are unchanged. Kafka uses its own `kafka-events` bridge and `kafka-data` volume; no Kafka service joins a database network. A normal bridge is intentional because the host listener must be published. Only IPv4 loopback is published.

Use the planned Apache Kafka **4.2.2** release, pinned to multi-platform image index `sha256:1213eb3943d551e5ed1fca7a4e109001cee35770b66a02a0c37a8964efe09b69`. Keep the Spring Boot 4.1.1-managed client **4.2.1**. The standalone verification project compiles against that client; the four application POMs are unchanged. The image's Java 21 binaries run a single combined KRaft broker/controller with node 1, stable cluster ID and explicit static quorum. Named Kafka data survives container recreation. Startup rejects an existing cluster/node identity mismatch and never deletes or reformats existing metadata.

| Listener | Advertised address | Purpose |
|---|---|---|
| HOST | `127.0.0.1:19092` by default | Windows-host clients; host port configurable |
| INTERNAL | `kafka:9092` | Clients on the Compose Kafka network |
| CONTROLLER | `kafka:9093`, unpublished | Authenticated KRaft control traffic |

All listeners authenticate with SASL/PLAIN over plaintext transport. This is for local synthetic development on a trusted machine; encryption/TLS and multi-host access require a later operational decision. Use six separate generated credentials: broker, admin, order, payment, warehouse, core. Only broker/admin are superusers. `StandardAuthorizer` defaults to deny. Source principals receive Write/Describe on their own exact topic and cluster IdempotentWrite. Core receives Read/Describe on the three topics and Read/Describe on the `aegisflow-core-` consumer-group prefix. No consumer or application currently connects to business events.

Create `aegisflow.order.events.v1`, `aegisflow.payment.events.v1` and `aegisflow.warehouse.events.v1`: **3 partitions, replication factor 1, min ISR 1, 7-day retention, delete cleanup and 1 MiB record-batch limit**. Automatic topic creation is disabled. Provisioning creates absent topics and adds ACLs idempotently; it does not silently resize/reconfigure existing topics or remove permissions. The smoke test rejects topic-setting drift and exercises selected allow/deny boundaries. This resolves OQ-02 for the local slice only. Quarantine storage belongs to later Core inbox work; no speculative dead-letter topic is introduced here.

Broker startup writes restricted generated configuration under a private temporary directory and uses the image's Kafka binaries directly, avoiding configuration-dump startup wrappers. Broker and bootstrap containers use the image UID 1000, read-only root, temporary config/log paths and their explicit mounts. Readiness performs an authenticated topic-list request. A TCP socket or a tool that prints an error while exiting zero does not satisfy readiness.

`start-kafka.ps1` creates `.env.kafka` only if absent, never prints its generated values, then starts the ordinary Kafka stack. `.env.kafka` is ignored and existing `.env` is untouched. `verify-kafka.ps1` uses a fresh guarded project, transient credentials, an available loopback port and a Kafka-only Compose scope. It builds only `tools/kafka-smoke`, runs provisioning twice, then runs the same Java client on the host and inside the Kafka network. It validates advertised endpoints, topic settings, three acknowledged source writes/Core reads and committed offsets per listener, six forbidden sibling writes plus a forbidden Core write, and explicit wrong-password rejection. Timeouts cannot count as denied access. The harness stops only its verification resources and retains Kafka volumes; it never targets PostgreSQL or uses volume pruning.

## Boundaries and consequences

One broker with replication factor 1 provides local reproducibility, not high availability. `acks=all` waits only for this one in-sync replica. Seven-day retention bounds the broker replay window; database idempotency retention is a separate decision. Partition ordering applies within one partition/topic, not across service topics.

Topic ACLs restrict source credentials, but do not authenticate payload tenant claims or producer-set headers. The future Core inbox must validate configured topic/source and trusted allowed-tenant bindings, quarantining or blocking untrusted input before acceptance. P0.3's durable relay/inbox work remains pending; no universal exactly-once effects, consumer offset/database atomicity, production security or Kafka fault-recovery claim follows from a smoke test.

## Verification and sources

The Java 21 standalone client package build and 25 infrastructure-free guard checks passed; Kafka Compose parsing passed. Live verification currently stops at the Docker engine permission boundary. Actual source/commands/errors and host acceptance remain in [test evidence](../evidence/TEST_RESULTS.md) and [handoff](../HANDOFF.md).

- [Apache Kafka releases](https://kafka.apache.org/community/downloads/) lists 4.2.2; [official image metadata](https://hub.docker.com/v2/repositories/apache/kafka/tags/4.2.2) supplied the index digest on 2026-10-17.
- [Kafka 4.2 listener configuration](https://kafka.apache.org/42/security/listener-configuration/) and [SASL documentation](https://kafka.apache.org/42/security/authentication-using-sasl/) describe listener names and authentication/transport choices.
- [Kafka ACL documentation](https://kafka.apache.org/42/security/authorization-and-acls/) documents KRaft StandardAuthorizer/default denial, explicit operations and group patterns.
- [Pinned image Dockerfile](https://github.com/apache/kafka/blob/4.2.2/docker/jvm/Dockerfile) and [storage wrapper](https://github.com/apache/kafka/blob/4.2.2/core/src/main/scala/kafka/docker/KafkaDockerWrapper.scala) informed UID, paths and static-quorum formatting. Source inspection is distinct from runtime verification.

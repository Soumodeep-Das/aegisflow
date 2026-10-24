# Isolated Kafka verification client

This Java 21 command-line client has a disposable broker delivery mode and two explicitly read-only persistent-broker modes. It is outside the five-project application reactor and does not implement an outbox relay, business consumer, inbox, or business event schema.

Build from the repository root with the existing Maven wrapper:

```powershell
.\scripts\build-kafka-smoke.ps1
& "$env:JAVA_HOME\bin\java.exe" -cp 'tools/kafka-smoke/target/classes;tools/kafka-smoke/target/lib/*' com.aegisflow.tools.kafka.KafkaSmoke --self-test
```

Use PowerShell 7 for the build helper. The Maven project inherits Java 21 enforcement and the existing Spring Boot dependency management: `kafka-clients` 4.2.1 and `slf4j-nop` 2.0.18. The package phase copies runtime dependencies to `target/lib`. The silent logging binding prevents Kafka's configuration and authentication diagnostics from printing secrets. Failures report only the fixed verification stage and exception class.

Without `--self-test`, the client requires these process environment variables supplied by the selected repository harness (never copy passwords into commands or reports):

| Variable | Required value |
|---|---|
| `KAFKA_SMOKE_RUN_ID` | 32 lowercase hexadecimal characters |
| `KAFKA_BOOTSTRAP_SERVERS` | One `host:port`, equal to the expected advertised endpoint |
| `KAFKA_SMOKE_EXPECTED_HOST`, `KAFKA_SMOKE_EXPECTED_PORT` | Listener metadata the broker must return to this caller |
| `KAFKA_ADMIN_PASSWORD`, `KAFKA_ORDER_PASSWORD`, `KAFKA_PAYMENT_PASSWORD`, `KAFKA_WAREHOUSE_PASSWORD`, `KAFKA_CORE_PASSWORD` | Five distinct generated passwords, each 24–128 ASCII letters, digits, underscores or hyphens |

The principals are `admin`, `order`, `payment`, `warehouse`, and `core`. Authentication uses local-development SASL/PLAIN over SASL_PLAINTEXT. Each source requires its own topic write permission and cluster `IdempotentWrite`; Core requires topic read and consumer-group permissions for the `aegisflow-core-` prefix. Only the admin principal inspects topic configuration.

The default disposable run (no arguments) checks the advertised listener; exactly three non-internal source topics with three partitions, one replica, seven-day delete retention, minimum ISR one and a 1 MiB record limit; three source-authorized acknowledged records; exact Core reads and explicit committed-offset readback; six rejected sibling-topic writes; one rejected Core write; and rejection of an incorrect password. Source producers use `acks=all` and idempotence. The Core negative producer disables idempotence so it can reach topic authorization without receiving a cluster-write privilege. A timeout never counts as a successful denial.

Markers include the run ID, listener and source. The consumer directly assigns the acknowledged partitions and seeks to their exact offsets, bounding the read even when the two listener checks share one disposable broker. Its group is `aegisflow-core-smoke-<run-id>`; auto-commit and automatic topic creation are disabled. The process has a 180-second overall deadline, bounded API calls, and bounded client shutdown. Individual PASS lines precede exit zero only when all checks complete.

The infrastructure-free `--self-test` checks 15 guard cases, including timeout-versus-denial classification, unexpected successful forbidden operations, credential injection, duplicate or missing credentials, and invalid endpoints. It creates no client and makes no network calls. Passing it is not evidence of broker startup or delivery.

The corresponding Linux container classpath uses `:` instead of `;`, for example `'/opt/kafka-smoke/classes:/opt/kafka-smoke/lib/*'` when the harness mounts `target` at `/opt/kafka-smoke`. Run the default delivery mode only against the harness's fresh disposable broker: it sends synthetic marker records and commits a disposable group. Successful smoke delivery does not establish a business-level exactly-once guarantee.


## Persistent modes and their limits

| Mode | Checks | Mutation boundary |
|---|---|---|
| `--persistent-read-only` | Advertised listener/topic configuration, exact ACL inventory, broker/topic identities, source/Core positive authentication and wrong-password rejection | No records or offsets written |
| `--persistent-boundaries` | Positive own-topic/Core descriptions; six sibling-topic descriptions denied; Core ACL-administration metadata request denied | No records or offsets written; **not a forbidden-write probe** |

Use [verify-development-kafka.ps1](../../scripts/verify-development-kafka.ps1) and [verify-development-boundaries.ps1](../../scripts/verify-development-boundaries.ps1) only when deliberately repeating their infrastructure acceptance gates. The surrounding scripts can start/restart services; the client modes being read-only does not make the entire harness read-only. Routine lifecycle instructions are in the [runbook](../../docs/LOCAL_DEPLOYMENT.md).

P0.5 persistent ACL inventory/metadata denials do not replace the default disposable mode's actual rejected produce requests. Persistent forbidden-write probing remains deferred. Historical evidence and limitations are in [TEST_RESULTS](../../docs/evidence/TEST_RESULTS.md) and [KNOWN_LIMITATIONS](../../docs/KNOWN_LIMITATIONS.md).

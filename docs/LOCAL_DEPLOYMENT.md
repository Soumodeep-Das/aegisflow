# Local development runbook

The [P0.5 acceptance](STATUS.md) verified a persistent local deployment. It does not imply that services are running now on your machine. These commands are for Windows PowerShell 7 in the repository root.

## Prerequisites

- Windows 11, Docker Desktop using Linux containers, and a running engine (`docker info`).
- PowerShell 7; Windows PowerShell 5.1 cannot run scripts requiring version 7.
- Java 21 JDK selected by `JAVA_HOME`; Maven 3.10.0 comes from the repository wrapper.
- First-run dependency/image downloads require network access. Port conflicts must be resolved explicitly, not by killing unrelated processes.

If `pwsh` is not on PATH, launch an installed PowerShell 7 executable. The owner used:

```powershell
& 'C:\Users\User\.cache\codex-runtimes\codex-primary-runtime\dependencies\native\powershell\pwsh.exe' -NoProfile
```

That bundled path is machine-specific, not a prerequisite for other installations.

## First initialization and image build

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot'
.\scripts\dev-init.ps1 -CheckOnly
.\scripts\dev-init.ps1
.\scripts\dev-build-images.ps1
.\scripts\dev-stack.ps1 -Action up
.\scripts\dev-stack.ps1 -Action status
```

Change the JDK path for your installation. `CheckOnly` checks Docker inventory and initialization safety; it does not generate a missing file or start containers. Normal initialization creates `.env.development` only when safe, using cryptographically generated distinct credentials and a stable Kafka cluster identity. It validates an existing file without overwriting it.

No manual password entry or copying into `.env` is required. Existing `.env`/`.env.kafka` can cause a missing-development-file initialization to fail safely; inspect their history privately rather than deleting them.

`dev-build-images.ps1` runs the clean reactor, verifies reports, then builds four pinned-base Java 21 images. `up` does not build missing images. On an existing verified installation, do not repeat builds or acceptance suites merely to start services.

## Startup sequence and configuration

`dev-stack.ps1 -Action up` invokes the existing infrastructure helper:

1. Validate/generate credentials with retained-volume guards.
2. Start four databases and Kafka with bounded health waits.
3. Bootstrap each owner's migration/runtime roles.
4. Complete the one-shot Kafka topic/ACL provisioning job.
5. Start the four app images with bounded health waits.

The project is `aegisflow-foundation`. The unified configuration combines `compose.yaml`, `compose.kafka.yaml` and `compose.apps.yaml`, using explicit `--env-file .env.development`. The helper clears relevant shell interpolation variables during invocation and restores them afterward. It does not print expanded secret-bearing configuration.

There are **nine long-running services** and one additional provisioning job, `kafka-init`, which normally exits successfully. Old helper messages saying “B is NOT complete” describe a historical gate and are not current project status. Infrastructure-only Compose calls may warn that the four app containers are “orphans” because those files omit app definitions; do not use `--remove-orphans`.

## Ports and network boundaries

All published ports bind to `127.0.0.1`.

| Service | Host endpoint | Container endpoint |
|---|---|---|
| Core health | 8080/actuator/health | core:8080 |
| Order health | 8081/actuator/health | order:8080 |
| Payment health | 8082/actuator/health | payment:8080 |
| Warehouse health | 8083/actuator/health | warehouse:8080 |
| Core PostgreSQL | 5433 | core-db:5432 / aegisflow_core |
| Order PostgreSQL | 5434 | order-db:5432 / aegisflow_order |
| Payment PostgreSQL | 5435 | payment-db:5432 / aegisflow_payment |
| Warehouse PostgreSQL | 5436 | warehouse-db:5432 / aegisflow_warehouse |
| Kafka host listener | 19092 | kafka:9092 for container clients |

Inside an app container, `localhost` means that container, not Windows or a database. Each app joins only its own `<owner>-data` network and `kafka-events`. Core receives only Core DB migration/runtime and Core Kafka credentials. Separate bootstrap, migration and runtime identities remain intentional.

## Routine lifecycle

```powershell
.\scripts\dev-stack.ps1 -Action status
.\scripts\dev-stack.ps1 -Action up
.\scripts\dev-stack.ps1 -Action restart-apps
.\scripts\dev-stack.ps1 -Action stop
```

`restart-apps` stops/restarts only the four applications. For a whole-stack restart use `stop`, then `up`.

**Unusual but deliberate:** `-Action down` performs the same non-destructive stop as `stop`. It is not ordinary `docker compose down`: containers, networks, all named volumes and credentials are retained. There is no automatic OS-start restart policy.

Persistent volumes are `aegisflow-foundation_core-db-data`, `order-db-data`, `payment-db-data`, `warehouse-db-data` (each with the same project prefix), and `aegisflow-foundation_kafka-data`. They preserve owner data, Core evidence and broker state across stops.

## Status, health and logs

```powershell
.\scripts\dev-stack.ps1 -Action status
Invoke-RestMethod 'http://127.0.0.1:8080/actuator/health'
docker logs --tail 100 aegisflow-foundation-core-1
docker logs --tail 100 aegisflow-foundation-kafka-init-1
```

The named containers above belong to this fixed project. Inspect bounded logs locally; redact before sharing. Do not paste environment files, raw `docker inspect` output or expanded `docker compose config`, which may expose credentials. A health response does not prove an event completed the pipeline.

## Deliberate business demonstration

```powershell
.\scripts\verify-development-pipeline.ps1
```

This is not a status command. It runs a fresh reactor/build, refreshes app images, adds an exact synthetic tenant using a secret-free temporary overlay, and invokes real owner business operations. It checks healthy and missing-Warehouse flows, restarts Core with a held on-time event, then stops/starts the full retained stack. Temporary owner command containers have no published ports. Successful completion restores the base tenant mapping and leaves the ordinary stack running.

Evidence is retained in `.cache/p05d/<run-id>`; image/reactor reports are in `.cache/application-images/<run-id>`. Synthetic business rows remain in the persistent databases. The approved run is already recorded; do not rerun it merely to reproduce a README screenshot.

## Failure diagnosis without destructive cleanup

| Symptom | Safe next check |
|---|---|
| PowerShell version error | Use PowerShell 7, not 5.1 |
| Docker client version works but engine fails | Check `docker info`; start Docker Desktop's engine. An unrelated project container is not required |
| Port already allocated | Identify the conflicting listener/project; do not kill it blindly |
| App unhealthy | Check bounded app logs, its own database and Kafka provisioning state; preserve failed evidence |
| Missing credentials with retained volumes | Restore the matching private file from a secure backup; do not generate new passwords against initialized storage |
| Schema/bootstrap failure | Preserve migration/role errors; do not edit applied migrations, reset databases or grant broad privileges |
| Pipeline failure with tenant overlay retained | Preserve `tenant.compose.json`, results and command logs; inspect the exact stage before changing allowlists or rerunning |
| Orphan warning in infrastructure helper | Known apps may be omitted from that partial Compose view; do not remove them |

Do not use volume deletion, `down -v`, prune, credential regeneration or orphan removal as routine troubleshooting. Nothing in this runbook authorizes deleting unrelated resources. See [limitations](KNOWN_LIMITATIONS.md) and [handoff](HANDOFF.md) before changing deployment behavior.

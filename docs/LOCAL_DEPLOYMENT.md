# Local deployment — P0.5 in progress

Latest2026-10-23: owner read-only preflight passed. Next run scripts/dev-infrastructure-up.ps1 under bundled PowerShell7. It creates ignored .env.development with18 distinct passwords and a stable cluster ID under protected Windows ACL, starts the four databases and Kafka, bootstraps roles and provisions topics/ACLs. It preserves all storage/configuration and does not start application containers. Do not share the private file. Startup is not yet executed/verified; B auth/restart acceptance and C remain unfinished. Earlier CheckOnly-only advice below is historical.

Current B partial checkpoint: run only scripts/dev-init.ps1 -CheckOnly under PowerShell7 for read-only Docker inventory and initialization safety. No private configuration is created in this mode. Existing legacy configuration or retained volumes without .env.development cause a safe stop; share the error, never delete volumes or replace passwords. Infrastructure startup/restart verification and unified C commands are not implemented yet. Do not use non-CheckOnly initialization until remaining file/ACL verification is complete.

## Status and prerequisites

P0.5-A is verified on581da54: all four real images passed non-root/read-only startup, host/bridge health and shutdown. No repeat A command is needed. Next is B guarded credential/bootstrap implementation. Nine-service persistent startup and business demonstrations are not yet available; do not manually fill credentials to work around unfinished B/C/D.

Use Windows11, Docker Desktop Linux containers, Java21 JDK, PowerShell7 and existing Maven wrapper. Run docker info to verify the engine is actually reachable; a Docker client version alone is insufficient. This coding session cannot access the engine pipe, independently of the owner's working Docker Desktop.

From repository root:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot'
& 'C:\Users\User\.cache\codex-runtimes\codex-primary-runtime\dependencies\native\powershell\pwsh.exe' -NoProfile -File .\scripts\verify-application-images.ps1
```

The first run builds/tests/packages the reactor once, pulls the pinned Java runtime if needed, builds four images and tests each sequentially. It prints evidence locations under .cache/application-images and .cache/application-image-verification. Build staging contains exactly the selected executable plus probe and Docker instructions; no credentials. Logs are foundation-only, but keep all .cache evidence private/ignored.

For a Docker-only retry after an unchanged successful reactor/artifact build, append -UseExistingArtifacts. Freshness/content guards remain active; never use this switch to evade a required source regression. Build-only command is scripts/dev-build-images.ps1; it does not claim startup. Infrastructure-free checks use scripts/verify-image-policy.ps1. Neither build nor static checks are a live acceptance pass.

## What the A verifier does

Images: aegisflow/core:local, aegisflow/order:local, aegisflow/payment:local, aegisflow/warehouse:local. Same Java21 digest, UID/GID10001, direct Java PID1/UTC, read-only root, no added capabilities or socket mount. Temporary /tmp is64MiB. Each application container uses a768MiB memory ceiling,1 CPU and256-process limit; JVM maximum heap is60% of container memory to leave native/metaspace headroom. These are starting bounds, not measured capacity promises; four apps are tested sequentially to reduce load. Image build may still need significant Docker disk/network.

The image's container HTTP port is8080, bound0.0.0.0 inside its bridge; temporary host ports are OS-assigned and loopback-only. The verifier obtains the real binding after Docker starts, avoiding reserve/release port races. A separate bridge client proves service-name resolution and non-loopback interface access. HTTP health is foundation/process-only: no PostgreSQL, Kafka workers, credentials or business mutations.

Docker Desktop shows temporary names beginning aegisflow-images-. The verifier records exact container IDs, then gracefully stops and removes only its labelled test containers/network. It creates no volumes and never touches the existing aegisflow-foundation project. If interrupted before cleanup, inspect the retained evidence/labels; do not prune Docker or delete volumes. Built images and evidence remain.

## Persistent stack — not available yet

Planned baseline is four application containers, four PostgreSQL containers and one Kafka broker; one-shot bootstrap/topic jobs may appear exited. Planned database URLs use owner service DNS5432 and Kafka kafka:9092, not host localhost. Each app receives only its owner runtime/migration credentials and Kafka principal. Four database networks stay internal.

Pending B must create/reuse guarded ignored private files without overwriting .env/.env.kafka, refuse missing secrets over retained volumes and test malformed/duplicate inputs. No private file is created/read by A. Compose environment secrets are still visible to privileged local Docker users; this is synthetic trusted-local deployment, not production secret management or encrypted transport.

Pending C supplies start/status/verify/stop/down wrappers, bounded readiness/provisioning checks and stable volume/project identity. No verified command for these exists yet. Do not use down -v, system prune or volume deletion. Stop retains containers and volumes; ordinary Compose down removes project containers/networks but retains named volumes; deleting volumes destroys data. Keep this distinction in the upcoming wrappers.

Pending D supplies service-owned scenario entrypoints, bounded incident summaries, late-observation/restart demonstrations and full-stack stop/start retention. No unauthenticated mutation HTTP API or fabricated Core rows will be added.

## Diagnosis

- Engine permission denied: run the host command from your normal PowerShell with Docker Desktop ready; do not reinstall Java/Maven.
- Java mismatch: set JAVA_HOME21; the scripts reject Java26.
- Original archive stale: rerun the default image verifier to build once; do not compare normalized executable JAR timestamp alone.
- Container exits: preserve its exact labelled log in the printed evidence directory. Do not change unrelated ports or reset databases.
- Host health passes but bridge fails: inspect container SERVER_ADDRESS override;127.0.0.1 inside a container is not its bridge interface.
- A health pass does not establish database migrations, source ACLs, delivery, invariants or incidents. Those remain B/C/D gates.

See [handoff](HANDOFF.md), [decision](adr/0013-local-application-images-and-deployment-gates.md) and [test evidence](evidence/TEST_RESULTS.md) for the exact next checkpoint.

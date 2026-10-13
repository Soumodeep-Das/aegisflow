#requires -Version 7.0
[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent $PSScriptRoot
Push-Location $repositoryRoot
try {
    docker compose --profile postgres config --quiet
    if ($LASTEXITCODE -ne 0) { throw 'Compose configuration invalid; fill the private .env first.' }
    foreach ($service in @('core-db', 'order-db', 'payment-db', 'warehouse-db')) {
        # Passwords are read by psql inside the container, never copied to output or host arguments.
        docker compose --profile postgres exec -T $service sh -c 'psql -X -q -U "$POSTGRES_USER" -d "$POSTGRES_DB" -f /opt/aegisflow/bootstrap-roles.sql'
        if ($LASTEXITCODE -ne 0) { throw "Role bootstrap failed for $service; existing volumes are preserved." }
        Write-Output "${service}: migration/runtime roles provisioned"
    }
} finally { Pop-Location }

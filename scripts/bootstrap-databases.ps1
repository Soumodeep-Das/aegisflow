#requires -Version 7.0
[CmdletBinding()]
param(
    [string[]]$ComposeFile = @(),
    [string]$ProjectName,
    [string]$EnvFile
)
$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent $PSScriptRoot
Push-Location $repositoryRoot
try {
    # Explicit inputs let an isolated rehearsal reuse this exact administration path.
    # Omitting them preserves the existing local developer workflow and volumes.
    $composeArguments = @('compose')
    foreach ($file in $ComposeFile) { $composeArguments += @('--file', $file) }
    if ($ProjectName) { $composeArguments += @('--project-name', $ProjectName) }
    if ($EnvFile) { $composeArguments += @('--env-file', $EnvFile) }
    $composeArguments += @('--profile', 'postgres')
    & docker @composeArguments config --quiet
    if ($LASTEXITCODE -ne 0) { throw 'Compose configuration invalid; fill the private .env first.' }
    foreach ($service in @('core-db', 'order-db', 'payment-db', 'warehouse-db')) {
        # Passwords are read by psql inside the container, never copied to output or host arguments.
        & docker @composeArguments exec -T $service sh -c 'psql -X -q -U "$POSTGRES_USER" -d "$POSTGRES_DB" -f /opt/aegisflow/bootstrap-roles.sql'
        if ($LASTEXITCODE -ne 0) { throw "Role bootstrap failed for $service; existing volumes are preserved." }
        Write-Output "${service}: migration/runtime roles provisioned"
    }
} finally { Pop-Location }

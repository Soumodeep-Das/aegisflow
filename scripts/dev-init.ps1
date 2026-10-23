#requires -Version 7.0
[CmdletBinding()]
param([switch]$CheckOnly)
$ErrorActionPreference='Stop'
$root=Split-Path -Parent $PSScriptRoot
. "$PSScriptRoot/lib/development-credentials.ps1"
$private=Join-Path $root '.env.development'
Push-Location $root
try {
    & docker info --format '{{.ServerVersion}}' | Out-Null
    if($LASTEXITCODE -ne 0) { throw 'Docker inventory unavailable; no credentials or resources changed.' }
    $all=@(& docker volume ls --format '{{.Name}}')
    if($LASTEXITCODE -ne 0) { throw 'Volume inventory failed; refusing initialization.' }
    $labelled=@(& docker volume ls --filter label=com.docker.compose.project=aegisflow-foundation --format '{{.Name}}')
    if($LASTEXITCODE -ne 0) { throw 'Project volume inventory failed; refusing initialization.' }
    $volumes=@($all | Where-Object { $_ -in @('aegisflow-foundation_core-db-data','aegisflow-foundation_order-db-data','aegisflow-foundation_payment-db-data','aegisflow-foundation_warehouse-db-data','aegisflow-foundation_kafka-data') })+@($labelled | Where-Object { $_ })
    $exists=Test-Path -LiteralPath $private
    $legacy=(Test-Path -LiteralPath "$root/.env") -or (Test-Path -LiteralPath "$root/.env.kafka")
    Assert-DevelopmentInitializationSafe -PrivateExists $exists -Volumes $volumes -LegacyExists $legacy
    & git check-ignore --quiet -- .env.development
    if($LASTEXITCODE -ne 0) { throw 'Private path is not ignored; refusing initialization.' }
    if($exists) { $null=Read-DevelopmentCredentials $private }
    elseif($CheckOnly) { Write-Output 'PASS: new initialization is safe; no private file created. Infrastructure verification remains pending.'; return }
    else {
        $values=New-DevelopmentCredentials
        Assert-DevelopmentCredentials $values
        # CreateNew also protects against another initializer creating the file concurrently.
        # A partial write fails validation on retry; it must never be silently regenerated.
        Write-NewDevelopmentCredentials -Path $private -Values $values
    }
    Invoke-WithDevelopmentEnvironment {
        & docker compose --project-name aegisflow-foundation --env-file $private --file compose.yaml --file compose.kafka.yaml --profile postgres config --quiet
        if($LASTEXITCODE -ne 0) { throw 'Compose validation failed; private file retained. No containers started.' }
    }
    Write-Output 'PASS: private configuration validated and preserved; Compose syntax valid. No containers started. B infrastructure verification remains pending.'
} finally { Pop-Location }

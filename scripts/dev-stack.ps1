#requires -Version 7.0
[CmdletBinding()]
param([ValidateSet('up','status','stop','down','restart-apps')][string]$Action='status')
$ErrorActionPreference='Stop'
$stackOperation=$Action
$root=Split-Path -Parent $PSScriptRoot
. "$PSScriptRoot/lib/development-credentials.ps1"
Push-Location $root
try {
    # up alone may initialize infrastructure; other actions require the retained file.
    if($Action -eq 'up') { & "$PSScriptRoot/dev-infrastructure-up.ps1" }
    else {
        if(-not (Test-Path "$root/.env.development")) { throw 'Missing retained credentials; restore them, do not regenerate.' }
        & "$PSScriptRoot/dev-init.ps1" -CheckOnly
    }
    Invoke-WithDevelopmentEnvironment {
        $compose=@('compose','--project-name','aegisflow-foundation','--env-file',"$root/.env.development",
            '--file',"$root/compose.yaml",'--file',"$root/compose.kafka.yaml",'--file',"$root/compose.apps.yaml",'--profile','postgres','--profile','apps')
        & docker @compose config --quiet
        if($LASTEXITCODE -ne 0) { throw 'Unified Compose configuration invalid.' }
        switch -Exact ($stackOperation) {
            'up' {
                & docker @compose up -d --no-deps --wait --wait-timeout 240 core order payment warehouse
                if($LASTEXITCODE -ne 0) { throw 'Application startup failed; preserve containers, credentials and storage.' }
            }
            'status' {
                & docker @compose ps --all
                if($LASTEXITCODE -ne 0) { throw 'Status failed.' }
            }
            'restart-apps' {
                & docker @compose stop --timeout 30 core order payment warehouse
                if($LASTEXITCODE -ne 0) { throw 'Application stop failed.' }
                & docker @compose up -d --no-deps --wait --wait-timeout 240 core order payment warehouse
                if($LASTEXITCODE -ne 0) { throw 'Application restart failed; infrastructure preserved.' }
            }
            { $_ -in @('stop','down') } {
                # down deliberately means non-destructive stop: no container/network/volume removal.
                & docker @compose stop --timeout 30 core order payment warehouse
                if($LASTEXITCODE -ne 0) { throw 'Application stop failed.' }
                & docker @compose stop --timeout 30 kafka-init kafka core-db order-db payment-db warehouse-db
                if($LASTEXITCODE -ne 0) { throw 'Infrastructure stop failed.' }
                Write-Output 'Stopped; all containers, networks, named volumes and credentials retained.'
            }
            default { throw 'Unknown lifecycle operation; refusing any stop/start action.' }
        }
    }
} finally { Pop-Location }

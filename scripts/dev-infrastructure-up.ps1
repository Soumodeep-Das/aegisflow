#requires -Version 7.0
[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
$root=Split-Path -Parent $PSScriptRoot
. "$PSScriptRoot/lib/development-credentials.ps1"
Push-Location $root
try {
    & "$PSScriptRoot/dev-init.ps1"
    $private=Join-Path $root '.env.development'
    $before=(Get-FileHash -LiteralPath $private -Algorithm SHA256).Hash
    Invoke-WithDevelopmentEnvironment {
        $compose=@('compose','--project-name','aegisflow-foundation','--env-file',$private,
            '--file',"$root/compose.yaml",'--file',"$root/compose.kafka.yaml",'--profile','postgres')
        # Only the five infrastructure services; no down, pruning, or volume replacement.
        & docker @compose up -d --wait --wait-timeout 180 core-db order-db payment-db warehouse-db kafka
        if($LASTEXITCODE -ne 0) { throw 'Infrastructure readiness failed. Preserve storage and private file; no cleanup attempted.' }
        & "$PSScriptRoot/bootstrap-databases.ps1" -ComposeFile @("$root/compose.yaml","$root/compose.kafka.yaml") -ProjectName aegisflow-foundation -EnvFile $private
        # Readiness alone is not topic/ACL provisioning. Bound the one-shot job separately.
        & docker @compose up -d --no-deps kafka-init
        if($LASTEXITCODE -ne 0) { throw 'Kafka provisioning job could not start.' }
        $ids=@(& docker @compose ps --all --quiet kafka-init)
        if($LASTEXITCODE -ne 0 -or $ids.Count -ne 1 -or $ids[0] -notmatch '^[a-f0-9]{64}$') { throw 'Cannot identify provisioning job.' }
        $deadline=[datetime]::UtcNow.AddSeconds(180)
        do {
            $state=(& docker inspect --format '{{json .State}}' $ids[0]) | ConvertFrom-Json
            if($LASTEXITCODE -ne 0) { throw 'Cannot inspect provisioning state.' }
            if($state.Status -eq 'exited') { break }
            Start-Sleep -Seconds 2
        } while([datetime]::UtcNow -lt $deadline)
        if($state.Status -ne 'exited' -or $state.ExitCode -ne 0) { throw 'Kafka provisioning failed/timed out; retained job is available for diagnosis.' }
    }
    if((Get-FileHash -LiteralPath $private -Algorithm SHA256).Hash -ne $before) { throw 'Private configuration changed during startup; investigate before continuing.' }
    Write-Output 'PASS: five persistent infrastructure services healthy; database roles and Kafka topics/ACL provisioning completed; credentials unchanged.'
    Write-Output 'B is NOT complete: authentication/permission and retained-volume restart acceptance remain pending. Four application containers (C) are not started.'
} finally { Pop-Location }

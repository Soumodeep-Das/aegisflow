#requires -Version 7.0
[CmdletBinding()]
param([switch]$StaticOnly)
$ErrorActionPreference='Stop'
$root=Split-Path -Parent $PSScriptRoot
. "$PSScriptRoot/lib/development-credentials.ps1"
. "$PSScriptRoot/lib/development-boundaries.ps1"
$private=Join-Path $root '.env.development'
$null=Read-DevelopmentCredentials $private
$hash=(Get-FileHash $private).Hash
$owners=@('core','order','payment','warehouse')
$infra=@('core-db','order-db','payment-db','warehouse-db','kafka')
$evidence=Join-Path $root ('.cache/p05c/'+[guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force -Path $evidence | Out-Null
$result=[ordered]@{Passed=$false;StaticOnly=[bool]$StaticOnly;Checks=@()}
function Assert-AppConfig($owner,$envMap,$networks,$user,$readOnly) {
    $upper=$owner.ToUpperInvariant()
    if($user -ne '10001:10001' -or -not $readOnly) { throw 'Application security mismatch.' }
    $expectedNetworks=@("${owner}-data",'kafka-events') | Sort-Object
    if((@($networks | Sort-Object) -join ',') -ne ($expectedNetworks -join ',')) { throw 'Unexpected application networks.' }
    if($envMap.SPRING_PROFILES_ACTIVE -ne 'postgres' -or $envMap.SERVER_ADDRESS -ne '0.0.0.0' -or $envMap.SERVER_PORT -ne '8080' -or
        $envMap["${upper}_DB_URL"] -ne "jdbc:postgresql://$owner-db:5432/aegisflow_$owner" -or $envMap["${upper}_KAFKA_BOOTSTRAP_SERVERS"] -ne 'kafka:9092') { throw 'Internal endpoint/profile mismatch.' }
    foreach($key in $envMap.Keys) {
        if($key -match '^(CORE|ORDER|PAYMENT|WAREHOUSE)_DB_' -and -not $key.StartsWith("${upper}_DB_")) { throw 'Sibling database credential/configuration leaked.' }
        if($key -eq "${upper}_DB_PASSWORD" -or ($key -match '^KAFKA_.*_PASSWORD$' -and $key -ne "KAFKA_${upper}_PASSWORD")) { throw 'Bootstrap or sibling Kafka credential leaked.' }
    }
    if($owner -eq 'core') {
        foreach($worker in @('INBOX','PROJECTION','DETECTION','INCIDENTS')) { if($envMap["CORE_${worker}_ENABLED"] -ne 'true') { throw 'Core worker disabled.' } }
        foreach($source in @('ORDER','PAYMENT','WAREHOUSE')) { if($envMap["CORE_INBOX_${source}_TENANTS"] -ne 'demo-tenant') { throw 'Unexpected trusted tenant.' } }
        if($envMap.CORE_INBOX_GROUP -ne 'aegisflow-core-inbox-v1' -or $envMap.CORE_INBOX_CHANNEL -ne 'local-kafka-v1') { throw 'Consumer identity drift.' }
    } elseif($envMap["${upper}_OUTBOX_RELAY_ENABLED"] -ne 'true' -or $envMap["${upper}_OUTBOX_ALLOWED_TENANTS"] -ne 'demo-tenant') { throw 'Relay disabled or trust policy changed.' }
}
Push-Location $root
try {
    Invoke-WithDevelopmentEnvironment {
        $compose=@('compose','--project-name','aegisflow-foundation','--env-file',$private,'--file',"$root/compose.yaml",'--file',"$root/compose.kafka.yaml",'--file',"$root/compose.apps.yaml",'--profile','postgres','--profile','apps')
        # Expanded configuration contains secrets: memory only, never print/save it.
        $raw=& docker @compose config --format json
        if($LASTEXITCODE -ne 0) { throw 'Compose expansion failed.' }
        $config=($raw -join "`n") | ConvertFrom-Json -AsHashtable
        if($config.name -ne 'aegisflow-foundation' -or $config.services.Count -ne 10) { throw 'Expected nine long-running services plus one provisioning job.' }
        foreach($owner in $owners) {
            $app=$config.services[$owner]
            Assert-AppConfig $owner $app.environment @($app.networks.Keys) $app.user $app.read_only
            if($app.ports.Count -ne 1 -or $app.ports[0].host_ip -ne '127.0.0.1' -or $app.ports[0].target -ne 8080) { throw 'HTTP exposure mismatch.' }
        }
        Write-Output 'PASS: unified Compose security/network/credential/worker/tenant configuration (expanded secrets not saved).'
        $result.Checks+='Static Compose policy'
        if($StaticOnly) { return }
        & "$PSScriptRoot/dev-stack.ps1" -Action up
        $baseline=@{}
        foreach($service in $infra) {
            $id=Get-DevelopmentContainerId @(& docker @compose ps --quiet $service) $LASTEXITCODE
            $info=(& docker inspect --format '{{json .}}' $id | ConvertFrom-Json)
            if($LASTEXITCODE -ne 0 -or $info.State.Health.Status -ne 'healthy') { throw 'Infrastructure unhealthy.' }
            $baseline[$service]=@{Id=$id;Started=$info.State.StartedAt;Volumes=(@($info.Mounts | Where-Object Type -eq volume | ForEach-Object Name | Sort-Object) -join ',')}
        }
        foreach($pass in 1..2) {
            foreach($owner in $owners) {
                $id=Get-DevelopmentContainerId @(& docker @compose ps --quiet $owner) $LASTEXITCODE
                $info=(& docker inspect --format '{{json .}}' $id | ConvertFrom-Json)
                if($LASTEXITCODE -ne 0 -or $info.State.Health.Status -ne 'healthy') { throw "$owner unhealthy." }
                $envMap=@{}; foreach($line in $info.Config.Env) { $parts=$line.Split('=',2); $envMap[$parts[0]]=$parts[1] }
                $nets=@($info.NetworkSettings.Networks.PSObject.Properties.Name | ForEach-Object { $_ -replace '^aegisflow-foundation_','' })
                Assert-AppConfig $owner $envMap $nets $info.Config.User $info.HostConfig.ReadonlyRootfs
                $version=& docker exec $id java -version 2>&1
                if($LASTEXITCODE -ne 0 -or ($version | Out-String) -notmatch 'version "21\.') { throw 'Runtime Java21 check failed.' }
                $binding=@($info.NetworkSettings.Ports.'8080/tcp')
                if($binding.Count -ne 1 -or $binding[0].HostIp -ne '127.0.0.1') { throw 'Live port binding mismatch.' }
                $response=Invoke-RestMethod "http://127.0.0.1:$($binding[0].HostPort)/actuator/health" -TimeoutSec 10
                if($response.status -ne 'UP') { throw 'Host HTTP health failed.' }
                Write-Output "PASS: $owner pass $pass healthy, Java21, own-only configuration/networks and host HTTP."
                $result.Checks+="$owner pass $pass"
            }
            if($pass -eq 1) {
                & docker @compose stop --timeout 30 core order payment warehouse
                if($LASTEXITCODE -ne 0) { throw 'Application stop failed.' }
                foreach($owner in $owners) {
                    $id=Get-DevelopmentContainerId @(& docker @compose ps --all --quiet $owner) $LASTEXITCODE
                    $state=(& docker inspect --format '{{json .State}}' $id | ConvertFrom-Json)
                    if($state.Running -or $state.OOMKilled -or $state.ExitCode -notin @(0,143)) { throw 'Application shutdown not clean.' }
                }
                & docker @compose up -d --no-deps --wait --wait-timeout 240 core order payment warehouse
                if($LASTEXITCODE -ne 0) { throw 'Application restart failed.' }
            }
        }
        foreach($service in $infra) {
            $id=Get-DevelopmentContainerId @(& docker @compose ps --quiet $service) $LASTEXITCODE
            $info=(& docker inspect --format '{{json .}}' $id | ConvertFrom-Json)
            $volumes=@($info.Mounts | Where-Object Type -eq volume | ForEach-Object Name | Sort-Object) -join ','
            if($id -ne $baseline[$service].Id -or $info.State.StartedAt -ne $baseline[$service].Started -or $volumes -ne $baseline[$service].Volumes -or $info.State.Health.Status -ne 'healthy') { throw 'Infrastructure identity/state changed during application restart.' }
        }
    }
    if((Get-FileHash $private).Hash -ne $hash) { throw 'Private credentials changed.' }
    $result.Passed=$true
    if(-not $StaticOnly) { Write-Output 'PASS: nine healthy persistent services, application-only restart, stable infrastructure/credentials. D business demonstration not performed.' }
} finally { $result | ConvertTo-Json -Depth 5 | Set-Content "$evidence/results.json"; Write-Output "Evidence: $evidence"; Pop-Location }

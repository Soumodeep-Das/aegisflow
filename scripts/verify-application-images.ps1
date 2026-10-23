#requires -Version 7.0
[CmdletBinding()]
param([string]$JavaHome=$env:JAVA_HOME,[switch]$UseExistingArtifacts)
$ErrorActionPreference='Stop'
$root=Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot 'lib/application-images.ps1')
& (Join-Path $PSScriptRoot 'dev-build-images.ps1') -JavaHome $JavaHome -UseExistingArtifacts:$UseExistingArtifacts
$run='aegisflow-images-'+[guid]::NewGuid().ToString('N')
$evidence=Join-Path $root ".cache/application-image-verification/$run"
New-Item -ItemType Directory -Force -Path $evidence | Out-Null
$owned=[Collections.Generic.List[string]]::new(); $networkCreated=$false
Write-Output "Container evidence: $evidence"
try {
    & docker network create --label "com.aegisflow.verification=$run" $run | Out-Null
    if($LASTEXITCODE -ne 0) { throw 'Cannot create isolated image verification network.' }
    $networkCreated=$true
    foreach($app in (Get-ApplicationImagePlan)) {
        $tag="aegisflow/$($app.Owner):local"
        $raw=& docker image inspect --format '{{json .Config}}' $tag
        if($LASTEXITCODE -ne 0) { throw 'Image inspection failed.' }
        $config=$raw | ConvertFrom-Json
        if($config.User -ne '10001:10001' -or $config.Labels.'com.aegisflow.owner' -ne $app.Owner -or
            $config.Entrypoint[0] -ne 'java' -or $config.Env -notcontains 'SERVER_ADDRESS=0.0.0.0' -or
            $config.Env -notcontains 'SERVER_PORT=8080') { throw 'Unsafe or mismatched runtime image configuration.' }
        $version=& docker run --rm --network none --read-only --cap-drop ALL --security-opt no-new-privileges:true --entrypoint java $tag -version 2>&1
        if($LASTEXITCODE -ne 0 -or ($version | Out-String) -notmatch 'version "21\.') { throw 'Image Java21 check failed.' }
        & docker run --rm --network none --read-only --cap-drop ALL --security-opt no-new-privileges:true --entrypoint sh $tag -c 'test "$(id -u)" = 10001 && test "$(ls -A /app | wc -l)" = 2 && test -f /app/app.jar && test -f /app/HealthProbe.class'
        if($LASTEXITCODE -ne 0) { throw 'Non-root/application-content check failed.' }
        $hashLine=& docker run --rm --network none --read-only --cap-drop ALL --security-opt no-new-privileges:true --entrypoint sha256sum $tag /app/app.jar
        $expected=(Get-FileHash "$root/$($app.Module)/target/$($app.Artifact)-0.1.0-SNAPSHOT.jar" -Algorithm SHA256).Hash.ToLowerInvariant()
        if($LASTEXITCODE -ne 0 -or $hashLine -notmatch ('^'+$expected+'\s+/app/app.jar$')) { throw 'Image JAR differs from verified owner artifact.' }
        $name="$run-$($app.Owner)"
        $id=& docker run --detach --name $name --label "com.aegisflow.verification=$run" --network $run --read-only --cap-drop ALL --security-opt no-new-privileges:true --memory 768m --cpus 1 --pids-limit 256 --tmpfs /tmp:rw,noexec,nosuid,nodev,size=64m,mode=1777 --publish 127.0.0.1::8080 $tag
        if($LASTEXITCODE -ne 0 -or $id -notmatch '^[a-f0-9]{64}$') { throw 'Container start failed.' }
        $owned.Add($id)
        $deadline=[datetime]::UtcNow.AddSeconds(150)
        do {
            $state=(& docker inspect --format '{{json .State}}' $id) | ConvertFrom-Json
            if($LASTEXITCODE -ne 0 -or -not $state.Running) { throw "Owned $($app.Owner) container exited; inspect retained log." }
            if($state.Health.Status -eq 'healthy') { break }
            Start-Sleep -Seconds 1
        } while([datetime]::UtcNow -lt $deadline)
        if($state.Health.Status -ne 'healthy') { throw "Bounded health wait failed: $($app.Owner)" }
        $bindings=@(& docker port $id 8080/tcp)
        if($LASTEXITCODE -ne 0) { throw 'Port inspection failed.' }
        $port=Get-ApplicationLoopbackPort -Bindings $bindings
        $health=Invoke-RestMethod -Uri "http://127.0.0.1:$port/actuator/health" -TimeoutSec 5
        if($health.status -ne 'UP') { throw 'Published loopback health failed.' }
        # A separate bridge client proves 0.0.0.0 binding; self-loopback health alone cannot.
        & docker run --rm --network $run --read-only --cap-drop ALL --security-opt no-new-privileges:true --memory 128m --entrypoint java $tag -cp /app HealthProbe "http://${name}:8080/actuator/health"
        if($LASTEXITCODE -ne 0) { throw 'Container DNS/bridge health probe failed.' }
        @{Owner=$app.Owner;Image=$tag;Container=$id;Port=$port;Health='UP';User='10001:10001';BridgeProbe='Passed';Scope='foundation-only'} |
            ConvertTo-Json | Set-Content "$evidence/$($app.Owner).json"
        Write-Output "PASS: $($app.Owner) Java21/non-root/read-only startup, loopback HTTP and bridge DNS/HTTP."
        & docker stop --time 30 $id | Out-Null
        if($LASTEXITCODE -ne 0) { throw 'Graceful container stop failed.' }
        $exitCode=& docker inspect --format '{{.State.ExitCode}}' $id
        if($LASTEXITCODE -ne 0 -or $exitCode -notin @('0','143')) { throw 'Unexpected shutdown exit; inspect owned container log.' }
    }
    Write-Output 'PASS: P0.5-A four images. Foundation health is NOT persistent PostgreSQL/Kafka/business readiness. B/C/D remain pending.'
} finally {
    # Only exact container IDs returned by this run; never touch Compose volumes or unrelated projects.
    foreach($id in $owned) {
        $label=& docker inspect --format '{{index .Config.Labels "com.aegisflow.verification"}}' $id 2>$null
        if($LASTEXITCODE -eq 0 -and $label -eq $run) {
            # Preserve bounded health failures/OOM/exit evidence before removing this owned container.
            # Do not dump full inspect: its Config.Env may contain private values in future profiles.
            & docker inspect --format '{{json .State}}' $id | Set-Content "$evidence/$id.state.json"
            & docker logs --tail 300 $id 2>&1 | Set-Content "$evidence/$id.log"
            & docker stop --time 30 $id 2>$null | Out-Null
            & docker rm $id 2>$null | Out-Null
        }
    }
    if($networkCreated) {
        $label=& docker network inspect --format '{{index .Labels "com.aegisflow.verification"}}' $run 2>$null
        if($LASTEXITCODE -eq 0 -and $label -eq $run) { & docker network rm $run | Out-Null }
    }
}

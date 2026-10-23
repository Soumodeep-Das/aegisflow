#requires -Version 7.0
[CmdletBinding()]
param([string]$JavaHome=$env:JAVA_HOME)
$ErrorActionPreference='Stop'
$root=Split-Path -Parent $PSScriptRoot
. "$PSScriptRoot/lib/development-credentials.ps1"
$private=Join-Path $root '.env.development'
$evidence=Join-Path $root ('.cache/p05b-boundaries/'+[guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force -Path $evidence | Out-Null
$result=[ordered]@{Passed=$false;Topology=@();DeniedRoutes=@()}
Push-Location $root
try {
    & "$PSScriptRoot/dev-init.ps1" -CheckOnly
    Invoke-WithDevelopmentEnvironment {
        $compose=@('compose','--project-name','aegisflow-foundation','--env-file',$private,'--file',"$root/compose.yaml",'--file',"$root/compose.kafka.yaml",'--profile','postgres')
        $owners=@('core','order','payment','warehouse'); $ids=@{}; $addresses=@{}
        foreach($owner in $owners) {
            $id=& docker @compose ps --quiet "$owner-db"
            if($LASTEXITCODE -ne 0 -or $id -notmatch '^[a-f0-9]{64}$') { throw "Expected running $owner database." }
            $nets=(& docker inspect --format '{{json .NetworkSettings.Networks}}' $id) | ConvertFrom-Json
            $name="aegisflow-foundation_$owner-data"
            if($LASTEXITCODE -ne 0 -or @($nets.PSObject.Properties).Count -ne 1 -or $nets.PSObject.Properties.Name -cne $name) { throw 'Unexpected database network membership.' }
            $internal=& docker network inspect --format '{{.Internal}}' $name
            if($LASTEXITCODE -ne 0 -or $internal -ne 'true') { throw 'Database network is not internal.' }
            $ip=$nets.$name.IPAddress
            if($ip -notmatch '^\d{1,3}(\.\d{1,3}){3}$') { throw 'Expected IPv4 database endpoint.' }
            $ids[$owner]=$id; $addresses[$owner]=$ip
            $result.Topology+=@{Owner=$owner;Network=$name;Internal=$true}
        }
        foreach($owner in $owners) {
            & docker exec $ids[$owner] pg_isready -h $addresses[$owner] -p 5432 -t 3 | Out-Null
            if($LASTEXITCODE -ne 0) { throw 'Positive database network control failed.' }
            foreach($other in $owners) {
                if($other -eq $owner) { continue }
                & docker exec $ids[$owner] pg_isready -h $addresses[$other] -p 5432 -t 3 | Out-Null
                # pg_isready 2 means no response. Accepting OR rejecting a connection proves reachability.
                if($LASTEXITCODE -ne 2) { throw "Unexpected database reachability: $owner to $other." }
                $result.DeniedRoutes+=@{From=$owner;To=$other;Result='NoResponse'}
            }
        }
        Write-Output 'PASS: four owner-only internal database networks, four reachable own endpoints and twelve unreachable sibling endpoints.'
    }
    & "$PSScriptRoot/verify-development-kafka.ps1" -JavaHome $JavaHome -BoundariesOnly
    $result.Passed=$true
    Write-Output 'PASS: infrastructure network and metadata-authorization boundaries. No restart or application-container verification performed; review evidence before B closure.'
} finally { $result | ConvertTo-Json -Depth 5 | Set-Content "$evidence/results.json"; Write-Output "Evidence: $evidence"; Pop-Location }

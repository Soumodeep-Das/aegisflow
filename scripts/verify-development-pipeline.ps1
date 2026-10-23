#requires -Version 7.0
[CmdletBinding()]
param([string]$JavaHome=$env:JAVA_HOME)
$ErrorActionPreference='Stop'
$root=Split-Path -Parent $PSScriptRoot
. "$PSScriptRoot/lib/development-credentials.ps1"
$run=[guid]::NewGuid().ToString()
$tenant="demo-$run"
$evidence=Join-Path $root ".cache/p05d/$run"
$private=Join-Path $root '.env.development'
$result=[ordered]@{Passed=$false;Run=$run;Tenant=$tenant;Checks=@();Scope='ordinary persistent deployment'}
$children=[Collections.Generic.List[object]]::new()
function Wait-Condition([scriptblock]$Check,[int]$Seconds,[string]$Stage) {
    $end=[datetime]::UtcNow.AddSeconds($Seconds)
    do { if(& $Check) { return }; Start-Sleep -Milliseconds 500 } while([datetime]::UtcNow -lt $end)
    throw "Timed out: $Stage. Preserve $evidence."
}
function Invoke-Compose([string[]]$Operation) {
    & docker @compose @Operation
    if($LASTEXITCODE -ne 0) { throw "Compose operation failed: $($Operation[0]); storage preserved." }
}
function Read-Database([string]$Owner,[string]$Sql) {
    # Credentials stay inside the owning DB container. Only SELECTs scoped to the generated tenant.
    if($Sql -notmatch '^SELECT ' -or -not $Sql.Contains($tenant)) { throw 'Unscoped/non-read-only evidence query refused.' }
    $probe='export PGCONNECT_TIMEOUT=5 PGPASSWORD="$RUNTIME_DB_PASSWORD" PGOPTIONS="-c default_transaction_read_only=on"; exec psql -X -h "$1" -U "$RUNTIME_DB_USER" -d "$POSTGRES_DB" -Atq -v ON_ERROR_STOP=1 -c "$2"'
    $output=@(& docker @compose exec -T "$Owner-db" sh -c $probe read-evidence "$Owner-db" $Sql 2>&1)
    if($LASTEXITCODE -ne 0) { throw "Read-only $Owner evidence query failed; raw output withheld." }
    return ($output -join "`n").Trim()
}
function Count-Core([string]$Table,[string]$Scenario,[string]$Extra='') {
    return [int](Read-Database core "SELECT count(*) FROM aegisflow.$Table WHERE tenant_id='$tenant' AND business_transaction_id='$Scenario-$run' $Extra")
}
function Start-Driver([string]$Owner,[string]$Scenario,[string]$Mode,[bool]$Barrier=$true) {
    $name="aegisflow-demo-$($run.Replace('-',''))-$Owner-$Scenario-$Mode"
    $arguments=@($compose)+@('run','--rm','--no-deps','-T','--name',$name,'--entrypoint','java',$Owner,
        '-Xmx384m','-Duser.timezone=UTC','-jar','/app/app.jar','--aegisflow.local-demo=true',
        "--aegisflow.demo.run=$run","--aegisflow.demo.scenario=$Scenario","--aegisflow.demo.mode=$Mode")
    if($Barrier) { $arguments+='--aegisflow.demo.barrier=true' }
    $log=Join-Path $evidence "$Owner-$Scenario-$Mode"
    # Explicit quoting: no shell expansion and no credentials in arguments or process output metadata.
    $quoted=($arguments | ForEach-Object { '"'+$_.Replace('"','\"')+'"' }) -join ' '
    $process=Start-Process -FilePath 'docker' -ArgumentList $quoted -WindowStyle Hidden -PassThru -RedirectStandardOutput "$log.log" -RedirectStandardError "$log.err"
    $null=$process.Handle
    $child=@{Name=$name;Process=$process;Owner=$Owner;Scenario=$Scenario;Log=$log}
    $children.Add($child)
    return $child
}
function Wait-Driver($Child) {
    Wait-Condition { $Child.Process.Refresh(); $Child.Process.HasExited } 120 "driver $($Child.Owner)"
    $Child.Process.WaitForExit()
    if($Child.Process.ExitCode -ne 0) { throw "Owner driver failed; inspect $($Child.Log).log/.err" }
    $marker=@(Get-Content "$($Child.Log).log" | Where-Object { $_ -match '^DEMO_RESULT ' })
    if($marker.Count -ne 1 -or $marker[0] -notmatch 'event=([0-9a-f-]{36})$') { throw 'Missing exact driver result.' }
    return [guid]::Parse($Matches[1]).ToString()
}
function Release-Barrier($Child) {
    & docker exec $Child.Name touch "/tmp/aegisflow-demo-$run-$($Child.Scenario).go"
    if($LASTEXITCODE -ne 0) { throw 'Cannot release ready owner command.' }
}
function Snapshot-Incident {
    return Read-Database core "SELECT COALESCE(json_agg(x ORDER BY evaluated_at,evaluation_id)::text,'[]') FROM (SELECT i.incident_id,i.state,i.reason_code,i.severity,e.evaluation_id,e.invariant_id,e.invariant_version,e.contract_id,e.contract_version,e.configuration_version,e.due_at,e.enforce_after,e.evaluated_at,e.result,e.reason_code AS evaluation_reason,e.observation,t.entry_kind FROM aegisflow.incident i JOIN aegisflow.incident_timeline t USING(tenant_id,business_transaction_id,incident_id) JOIN aegisflow.invariant_evaluation e USING(tenant_id,business_transaction_id,evaluation_id) WHERE i.tenant_id='$tenant' AND i.business_transaction_id='missing-$run') x"
}
Push-Location $root
try {
    if(-not(Test-Path -LiteralPath $private)) { throw 'Retained development credentials required; do not regenerate.' }
    $null=Read-DevelopmentCredentials $private
    $privateHash=(Get-FileHash $private).Hash
    New-Item -ItemType Directory -Force -Path $evidence | Out-Null
    # Fresh complete default reactor, report validation and four images; never reuse stale C artifacts.
    & "$PSScriptRoot/dev-build-images.ps1" -JavaHome $JavaHome
    & "$PSScriptRoot/dev-stack.ps1" -Action up
    Invoke-WithDevelopmentEnvironment {
        $base=@('compose','--project-name','aegisflow-foundation','--env-file',$private,'--file',"$root/compose.yaml",'--file',"$root/compose.kafka.yaml",'--file',"$root/compose.apps.yaml",'--profile','postgres','--profile','apps')
        $services=@{}
        foreach($owner in @('order','payment','warehouse')) {
            $services[$owner]=@{environment=@{("$($owner.ToUpperInvariant())_OUTBOX_ALLOWED_TENANTS")="demo-tenant,$tenant"}}
        }
        $services.core=@{environment=@{CORE_INBOX_ORDER_TENANTS="demo-tenant,$tenant";CORE_INBOX_PAYMENT_TENANTS="demo-tenant,$tenant";CORE_INBOX_WAREHOUSE_TENANTS="demo-tenant,$tenant"}}
        $overlay=Join-Path $evidence 'tenant.compose.json'
        @{services=$services} | ConvertTo-Json -Depth 6 | Set-Content $overlay
        $compose=@($base)+@('--file',$overlay)
        Invoke-Compose @('config','--quiet')
        Invoke-Compose @('up','-d','--no-deps','--wait','--wait-timeout','240','core','order','payment','warehouse')
        $events=@{}
        foreach($scenario in @('healthy','missing')) {
            $drivers=@{}
            foreach($owner in @('order','payment','warehouse')) {
                $mode=if($owner -eq 'warehouse' -and $scenario -eq 'missing') {'hold'} else {'create'}
                $drivers[$owner]=Start-Driver $owner $scenario $mode
            }
            foreach($owner in @('order','payment','warehouse')) {
                $child=$drivers[$owner]
                Wait-Condition {
                    $child.Process.Refresh()
                    if($child.Process.HasExited) { throw "Driver exited before readiness: $owner; inspect logs." }
                    & docker exec $child.Name test -f "/tmp/aegisflow-demo-$run-$scenario.ready" 2>$null
                    return $LASTEXITCODE -eq 0
                } 150 "$owner context readiness"
            }
            Release-Barrier $drivers.order
            $events["order-$scenario"]=Wait-Driver $drivers.order
            Release-Barrier $drivers.payment
            Release-Barrier $drivers.warehouse
            foreach($owner in @('payment','warehouse')) { $events["$owner-$scenario"]=Wait-Driver $drivers[$owner] }
            # No backdating: compare actual owner envelope occurrence timestamps.
            $orderTime=[datetimeoffset]::Parse((Read-Database order "SELECT envelope->>'occurredAt' FROM aegisflow.outbox_event WHERE tenant_id='$tenant' AND event_id='$($events["order-$scenario"])'"))
            $warehouseTime=[datetimeoffset]::Parse((Read-Database warehouse "SELECT envelope->>'occurredAt' FROM aegisflow.outbox_event WHERE tenant_id='$tenant' AND event_id='$($events["warehouse-$scenario"])'"))
            if($warehouseTime -lt $orderTime -or ($warehouseTime-$orderTime).TotalSeconds -gt 45) { throw 'Actual Warehouse outcome was not within the unchanged inventory deadline.' }
        }
        Wait-Condition { (Count-Core incident missing) -eq 1 -and (Count-Core invariant_evaluation healthy "AND phase='ENFORCED' AND result='SATISFIED'") -gt 0 } 150 'enforced healthy/missing decisions'
        foreach($scenario in @('healthy','missing')) {
            if((Count-Core business_transaction $scenario "AND inventory_duration_seconds=45 AND lateness_seconds=15 AND enforce_after=due_at+interval '15 seconds'") -ne 1) { throw 'Unexpected durable deadline configuration.' }
        }
        if((Count-Core incident healthy) -ne 0 -or (Count-Core observed_fact healthy) -ne 3 -or (Count-Core observed_fact missing) -ne 2 -or (Count-Core incident_fact_reference missing) -lt 2) { throw 'Initial business evidence mismatch.' }
        $heldEnvelope=Read-Database warehouse "SELECT envelope::text FROM aegisflow.outbox_event WHERE tenant_id='$tenant' AND event_id='$($events['warehouse-missing'])' AND status='PENDING'"
        if(-not $heldEnvelope) { throw 'Missing held pending Warehouse event.' }
        $initial=Snapshot-Incident
        $initial | Set-Content "$evidence/initial-incident.json"
        $incident=@($initial | ConvertFrom-Json)[0]
        if($incident.state -ne 'DETECTED' -or $incident.reason_code -ne 'MISSING_OBSERVED_OUTCOME') { throw 'Incident misrepresents missing observed evidence.' }
        $result.Checks+='Healthy enforced SATISFIED; missing observed Warehouse produces one DETECTED incident'
        Invoke-Compose @('stop','--timeout','30','core')
        $release=Start-Driver warehouse missing release $false
        if((Wait-Driver $release) -ne $events['warehouse-missing']) { throw 'Released event identity changed.' }
        if((Read-Database warehouse "SELECT envelope::text FROM aegisflow.outbox_event WHERE tenant_id='$tenant' AND event_id='$($events['warehouse-missing'])'") -ne $heldEnvelope) { throw 'Released immutable event envelope changed.' }
        Wait-Condition { (Read-Database warehouse "SELECT status FROM aegisflow.outbox_event WHERE tenant_id='$tenant' AND event_id='$($events['warehouse-missing'])'") -eq 'PUBLISHED' } 60 'offline Core backlog publication'
        Invoke-Compose @('up','-d','--no-deps','--wait','--wait-timeout','240','core')
        Wait-Condition { (Count-Core observed_fact missing) -eq 3 -and (Count-Core invariant_evaluation missing "AND result='SATISFIED'") -gt 0 } 120 'Core restart backlog intake'
        if((Count-Core incident missing "AND incident_id='$($incident.incident_id)' AND state='DETECTED'") -ne 1 -or (Count-Core incident missing) -ne 1 -or (Count-Core incident healthy) -ne 0) { throw 'Historical incident identity/state was not preserved.' }
        foreach($key in $events.Keys) {
            $owner=($key -split '-')[0]
            if((Read-Database $owner "SELECT status FROM aegisflow.outbox_event WHERE tenant_id='$tenant' AND event_id='$($events[$key])'") -ne 'PUBLISHED') { throw "Unpublished owner event: $key" }
            if([int](Read-Database core "SELECT count(*) FROM aegisflow.inbox_message WHERE tenant_id='$tenant' AND event_id='$($events[$key])'") -ne 1) { throw 'Missing or duplicated accepted event identity.' }
        }
        Wait-Condition { @((Snapshot-Incident | ConvertFrom-Json) | Where-Object result -eq 'SATISFIED').Count -gt 0 } 60 'late incident timeline persistence'
        $before=Snapshot-Incident
        $before | Set-Content "$evidence/late-evidence-timeline.json"
        Read-Database core "SELECT COALESCE(json_agg(x)::text,'[]') FROM (SELECT r.*,f.source_system,f.inbox_event_id,f.fact_type,f.effective_at,f.observed_at,f.canonical_value FROM aegisflow.incident_fact_reference r JOIN aegisflow.observed_fact f USING(tenant_id,business_transaction_id,fact_id) WHERE r.tenant_id='$tenant' AND r.business_transaction_id='missing-$run') x" | Set-Content "$evidence/fact-references.json"
        Read-Database core "SELECT COALESCE(json_agg(x)::text,'[]') FROM (SELECT * FROM aegisflow.business_transaction WHERE tenant_id='$tenant') x" | Set-Content "$evidence/projections.json"
        $result.Events=$events
        if(@($before | ConvertFrom-Json | Where-Object result -eq 'SATISFIED').Count -lt 1) { throw 'Late evaluation absent from incident timeline.' }
        $result.Checks+='All six owner events published and accepted; Core restart; same DETECTED incident plus late SATISFIED timeline'
        # Full non-destructive stop/start, preserving the same per-run allowlist for retained work.
        Invoke-Compose @('stop','--timeout','30','core','order','payment','warehouse')
        Invoke-Compose @('stop','--timeout','30','kafka-init','kafka','core-db','order-db','payment-db','warehouse-db')
        & "$PSScriptRoot/dev-infrastructure-up.ps1"
        Invoke-Compose @('up','-d','--no-deps','--wait','--wait-timeout','240','core','order','payment','warehouse')
        if((Snapshot-Incident) -ne $before -or (Count-Core observed_fact healthy) -ne 3 -or (Count-Core observed_fact missing) -ne 3 -or (Count-Core incident healthy) -ne 0) { throw 'Retained-stack evidence changed unexpectedly.' }
        $result.Checks+='Full retained-stack restart preserves facts and complete incident timeline'
        # All six intents drained: remove only this temporary trust extension, retaining base demo tenant.
        $compose=$base
        Invoke-Compose @('up','-d','--no-deps','--wait','--wait-timeout','240','core','order','payment','warehouse')
    }
    if((Get-FileHash $private).Hash -ne $privateHash) { throw 'Private configuration changed.' }
    $result.Passed=$true
    Write-Output 'PASS: persistent healthy/missing/late-observation pipeline and retained full-stack restart. No authoritative recovery claimed.'
} finally {
    foreach($child in $children) {
        $child.Process.Refresh()
        if(-not $child.Process.HasExited) {
            # Only this run\'s explicitly named transient command container; never persistent services.
            & docker stop --time 10 $child.Name 2>$null | Out-Null
        }
        $child.Process.Dispose()
    }
    if(Test-Path $evidence) { $result | ConvertTo-Json -Depth 6 | Set-Content "$evidence/results.json" }
    Write-Output "Evidence: $evidence. On failure preserve tenant.compose.json and driver logs; do not delete volumes or regenerate credentials."
    Pop-Location
}

#requires -Version 7.0
[CmdletBinding()]
param([string]$JavaHome=$env:JAVA_HOME)
$ErrorActionPreference='Stop'
$root=Split-Path -Parent $PSScriptRoot
. "$PSScriptRoot/lib/development-credentials.ps1"
. "$PSScriptRoot/lib/kafka-infrastructure.ps1"
$private=Join-Path $root '.env.development'
$evidence=Join-Path $root ('.cache/p05b-kafka/'+[guid]::NewGuid().ToString('N'))
$result=[ordered]@{Passed=$false;Passes=@();Scope='read-only Kafka ACL/authentication and broker restart'}
New-Item -ItemType Directory -Force -Path $evidence | Out-Null
Push-Location $root
try {
    & "$PSScriptRoot/dev-init.ps1" -CheckOnly
    if(-not $JavaHome) { throw 'Set JAVA_HOME to Java21.' }
    $version=& "$JavaHome/bin/java.exe" -version 2>&1
    if($LASTEXITCODE -ne 0 -or ($version | Out-String) -notmatch 'version "21\.') { throw 'Java21 required.' }
    $priorJava=$env:JAVA_HOME
    try { $env:JAVA_HOME=$JavaHome; & "$PSScriptRoot/build-kafka-smoke.ps1" } finally { $env:JAVA_HOME=$priorJava }
    $values=Read-DevelopmentCredentials $private
    $hash=(Get-FileHash $private).Hash
    $settings=@{KAFKA_BOOTSTRAP_SERVERS='127.0.0.1:19092';KAFKA_SMOKE_EXPECTED_HOST='127.0.0.1';KAFKA_SMOKE_EXPECTED_PORT='19092';KAFKA_SMOKE_RUN_ID=[guid]::NewGuid().ToString('N')}
    foreach($p in @('ADMIN','ORDER','PAYMENT','WAREHOUSE','CORE')) { $settings["KAFKA_${p}_PASSWORD"]=$values["KAFKA_${p}_PASSWORD"] }
    $baseline=$null
    foreach($pass in 1..2) {
        $start=[Diagnostics.ProcessStartInfo]::new("$JavaHome/bin/java.exe")
        $start.UseShellExecute=$false; $start.CreateNoWindow=$true
        $start.RedirectStandardOutput=$true; $start.RedirectStandardError=$true
        $start.Environment.Clear()
        $filtered=New-KafkaSmokeEnvironment -Source ([Environment]::GetEnvironmentVariables('Process')) -Settings $settings
        foreach($key in $filtered.Keys) { $start.Environment[$key]=$filtered[$key] }
        foreach($arg in @('-Duser.timezone=UTC','-cp',"$root/tools/kafka-smoke/target/classes;$root/tools/kafka-smoke/target/lib/*",'com.aegisflow.tools.kafka.KafkaSmoke','--persistent-read-only')) { $start.ArgumentList.Add($arg) }
        $process=[Diagnostics.Process]::new(); $process.StartInfo=$start
        try {
            $null=$process.Start(); $stdout=$process.StandardOutput.ReadToEndAsync(); $stderr=$process.StandardError.ReadToEndAsync()
            if(-not $process.WaitForExit(210000)) { $process.Kill($true); $process.WaitForExit(); throw 'Kafka read-only probe timed out.' }
            $output=$stdout.GetAwaiter().GetResult(); $errors=$stderr.GetAwaiter().GetResult()
            $log=$output+$errors
            foreach($key in Get-DevelopmentSecretKeys) { $log=$log.Replace($values[$key],'[REDACTED]') }
            $log | Set-Content "$evidence/pass-$pass.log"
            if($process.ExitCode -ne 0) { throw "Kafka pass $pass failed; inspect sanitized evidence." }
            $identity=@($output -split '\r?\n' | Where-Object { $_ -match '^(IDENTITY|TOPIC_ID): ' }) -join "`n"
            if(@($identity -split "`n").Count -ne 4 -or $identity -notmatch ([regex]::Escape('IDENTITY: '+$values.KAFKA_CLUSTER_ID))) { throw 'Broker/topic identity evidence invalid.' }
            if($pass -eq 1) { $baseline=$identity } elseif($identity -ne $baseline) { throw 'Broker/topic IDs changed across retained restart.' }
            $result.Passes+=@{Pass=$pass;Identity=$identity;ReadOnlyChecks='Passed'}
            Write-Output "PASS: Kafka pass $pass metadata, exact ACL inventory, source/Core authentication and incorrect-password rejection."
        } finally { $process.Dispose() }
        if($pass -eq 1) {
            Invoke-WithDevelopmentEnvironment {
                $compose=@('compose','--project-name','aegisflow-foundation','--env-file',$private,'--file',"$root/compose.kafka.yaml")
                & docker @compose stop --timeout 30 kafka
                if($LASTEXITCODE -ne 0) { throw 'Broker stop failed; storage retained.' }
                & docker @compose up -d --wait --wait-timeout 180 kafka
                if($LASTEXITCODE -ne 0) { throw 'Broker restart failed; storage retained.' }
            }
        }
    }
    if((Get-FileHash $private).Hash -ne $hash) { throw 'Private file changed.' }
    $result.Passed=$true
    Write-Output 'PASS: persistent Kafka read-only gate and unchanged broker/topic IDs after restart. No messages or offsets written. Cross-service network and active forbidden-operation acceptance still pending; B is incomplete.'
} finally { $result | ConvertTo-Json -Depth 5 | Set-Content "$evidence/results.json"; Write-Output "Evidence: $evidence"; Pop-Location }

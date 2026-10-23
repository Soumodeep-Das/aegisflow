#requires -Version 7.0
[CmdletBinding()]
param([string]$JavaHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($JavaHome)) { throw 'Set JAVA_HOME to Java 21.' }
$java = Join-Path $JavaHome 'bin/java.exe'
if (-not (Test-Path -LiteralPath $java -PathType Leaf)) { throw 'Java executable missing.' }
if ((& $java -version 2>&1 | Out-String) -notmatch 'version "21\.') { throw 'Java 21 required.' }
docker info --format '{{.ServerVersion}}'
if ($LASTEXITCODE -ne 0) { throw 'Docker unavailable; driver database tests cannot pass.' }
$evidence = Join-Path $root ('.cache/p05d-driver/' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $evidence -Force | Out-Null
$savedJava = $env:JAVA_HOME
Push-Location $root
try {
    $env:JAVA_HOME = $JavaHome
    $started = [DateTime]::UtcNow
    Write-Output "Driver evidence: $evidence"
    Write-Output 'Running four new Warehouse fixture safety cases only; no persistent stack/configuration/volumes targeted.'
    & .\mvnw.cmd -B -ntp "-Dmaven.repo.local=$root/.cache/maven-repository" -pl simulators/warehouse-service -Pinfrastructure-tests '-Dit.test=LocalDemoIT' -l "$evidence/postgres.log" test-compile failsafe:integration-test failsafe:verify
    $mavenExit = $LASTEXITCODE
    $report = Join-Path $root 'simulators/warehouse-service/target/failsafe-reports/TEST-com.aegisflow.simulator.warehouse.LocalDemoIT.xml'
    if ((Test-Path -LiteralPath $report) -and (Get-Item -LiteralPath $report).LastWriteTimeUtc -ge $started) {
        Copy-Item -LiteralPath $report -Destination $evidence
    }
    if ($mavenExit -ne 0) { throw "Driver gate failed (Maven exit $mavenExit); preserve $evidence." }
    $copy = Join-Path $evidence 'TEST-com.aegisflow.simulator.warehouse.LocalDemoIT.xml'
    if (-not (Test-Path -LiteralPath $copy)) { throw 'Missing fresh driver test report.' }
    [xml]$xml = Get-Content -LiteralPath $copy -Raw
    foreach ($attribute in @('tests','failures','errors','skipped')) {
        if ($xml.testsuite.GetAttribute($attribute) -notmatch '^\d+$') { throw 'Invalid test counts.' }
    }
    if ([int]$xml.testsuite.tests -ne 4 -or [int]$xml.testsuite.failures -ne 0 -or
        [int]$xml.testsuite.errors -ne 0 -or [int]$xml.testsuite.skipped -ne 0) {
        throw 'All four driver tests must execute successfully with zero skips.'
    }
    Write-Output 'PASS: four real PostgreSQL fixture tests. This is NOT persistent P0.5-D acceptance or full CLI process verification.'
} finally {
    $env:JAVA_HOME = $savedJava
    Pop-Location
}

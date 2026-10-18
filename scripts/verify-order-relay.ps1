#requires -Version 7.0
[CmdletBinding()]
param([string]$JavaHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($JavaHome)) { throw 'Set JAVA_HOME to Java 21.' }
$java = Join-Path $JavaHome 'bin/java.exe'
if (-not (Test-Path -LiteralPath $java -PathType Leaf)) { throw 'Java 21 executable not found.' }
if ((& $java -version 2>&1 | Out-String) -notmatch 'version "21\.') { throw 'Java 21 is required.' }
docker info --format '{{.ServerVersion}}'
if ($LASTEXITCODE -ne 0) { throw 'Docker unavailable; Order relay PostgreSQL gate cannot pass.' }

$saved = @{}
foreach ($name in @('JAVA_HOME','TEMP','TMP')) { $saved[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
Push-Location $repositoryRoot
try {
    $env:JAVA_HOME = $JavaHome
    $temporary = Join-Path $repositoryRoot '.cache/tmp'
    New-Item -ItemType Directory -Force -Path $temporary | Out-Null
    $env:TEMP = $temporary
    $env:TMP = $temporary
    $mavenCache = Join-Path $repositoryRoot '.cache/maven-repository'
    # Clean only this Maven module's generated target; no existing Compose project/volume is used.
    & .\mvnw.cmd -B -ntp "-Dmaven.repo.local=$mavenCache" -pl simulators/order-service -Pinfrastructure-tests clean verify
    if ($LASTEXITCODE -ne 0) { throw 'Order module verification failed; preserve the first error.' }
    $total = 0
    foreach ($suite in @('OrderPersistenceIT','OrderOutboxRelayIT')) {
        $report = Join-Path $repositoryRoot "simulators/order-service/target/failsafe-reports/TEST-com.aegisflow.simulator.order.persistence.$suite.xml"
        if (-not (Test-Path -LiteralPath $report -PathType Leaf)) { throw "Missing fresh $suite report." }
        [xml]$xml = Get-Content -LiteralPath $report -Raw
        foreach ($attribute in @('tests','failures','errors','skipped')) {
            if ($xml.testsuite.GetAttribute($attribute) -notmatch '^\d+$') { throw "Invalid $suite count." }
        }
        if ([int]$xml.testsuite.tests -lt 1 -or [int]$xml.testsuite.failures -ne 0 -or
            [int]$xml.testsuite.errors -ne 0 -or [int]$xml.testsuite.skipped -ne 0) {
            throw "$suite requires executed tests and zero failures/errors/skips."
        }
        $total += [int]$xml.testsuite.tests
    }
    Write-Output "PASS: Order default tests and $total PostgreSQL tests, zero failures/errors/skips."
    Write-Output 'This is NOT real Kafka relay delivery or complete P0.3 verification; those gates remain next.'
} finally {
    Pop-Location
    foreach ($name in $saved.Keys) {
        if ($null -eq $saved[$name]) { [Environment]::SetEnvironmentVariable($name, $null, 'Process') }
        else { [Environment]::SetEnvironmentVariable($name, [string]$saved[$name], 'Process') }
    }
}

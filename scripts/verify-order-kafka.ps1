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
if ($LASTEXITCODE -ne 0) { throw 'Docker unavailable; real Order PostgreSQL-to-Kafka gate cannot pass.' }

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
    $started = [DateTime]::UtcNow
    Write-Output 'Running only three new real Order delivery/fault tests; existing Compose projects and volumes are not targeted.'
    # Direct Failsafe goals deliberately do not rerun the completed default/PostgreSQL suites.
    & .\mvnw.cmd -B -ntp "-Dmaven.repo.local=$mavenCache" -pl simulators/order-service -Pinfrastructure-tests test-compile failsafe:integration-test failsafe:verify '-Dit.test=OrderKafkaDeliveryIT'
    if ($LASTEXITCODE -ne 0) { throw 'Order Kafka verification failed; preserve the first error and Failsafe report.' }
    $report = Join-Path $repositoryRoot 'simulators/order-service/target/failsafe-reports/TEST-com.aegisflow.simulator.order.persistence.OrderKafkaDeliveryIT.xml'
    if (-not (Test-Path -LiteralPath $report -PathType Leaf)) { throw 'Missing OrderKafkaDeliveryIT report.' }
    if ((Get-Item -LiteralPath $report).LastWriteTimeUtc -lt $started) { throw 'Stale OrderKafkaDeliveryIT report.' }
    [xml]$xml = Get-Content -LiteralPath $report -Raw
    foreach ($attribute in @('tests','failures','errors','skipped')) {
        if ($xml.testsuite.GetAttribute($attribute) -notmatch '^\d+$') { throw 'Invalid OrderKafkaDeliveryIT count.' }
    }
    if ([int]$xml.testsuite.tests -ne 3 -or [int]$xml.testsuite.failures -ne 0 -or
        [int]$xml.testsuite.errors -ne 0 -or [int]$xml.testsuite.skipped -ne 0) {
        throw 'OrderKafkaDeliveryIT requires all three tests executed and zero failures/errors/skips.'
    }
    Write-Output 'PASS: three real Order PostgreSQL-to-Kafka tests; acknowledged delivery, bounded outage/restart recovery, and stable-ID redelivery after completion rollback.'
    Write-Output 'Testcontainers owns only its disposable resources. This is not Core durable-inbox or complete P0.3 verification.'
} finally {
    Pop-Location
    foreach ($name in $saved.Keys) {
        [Environment]::SetEnvironmentVariable($name, $saved[$name], 'Process')
    }
}

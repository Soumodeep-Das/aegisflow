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
if ($LASTEXITCODE -ne 0) { throw 'Docker unavailable; Payment/Warehouse real relay gates cannot pass.' }
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
    Write-Output 'Verifying only new Payment/Warehouse relays and their module boundaries. Completed Order tests and existing Compose storage are not targeted.'
    & .\mvnw.cmd -B -ntp "-Dmaven.repo.local=$mavenCache" -pl simulators/payment-service,simulators/warehouse-service -Pinfrastructure-tests '-Dtest=*OutboxRelayTests,KafkaOutboxPublisherTests,*ArchitectureTest' '-Dit.test=*OutboxRelayIT,*KafkaDeliveryIT' test failsafe:integration-test failsafe:verify
    if ($LASTEXITCODE -ne 0) { throw 'Simulator relay verification failed; preserve first error and reports.' }
    foreach ($owner in @('payment','warehouse')) {
        $title = (Get-Culture).TextInfo.ToTitleCase($owner)
        $package = "com.aegisflow.simulator.$owner"
        $internalPackage = if ($owner -eq 'payment') { "$package.persistence.internal" } else { "$package.reservation" }
        $suites = @(
            @('surefire', "$package.${title}SimulatorArchitectureTest", 1),
            @('surefire', "$internalPackage.${title}OutboxRelayTests", 16),
            @('surefire', "$internalPackage.KafkaOutboxPublisherTests", 3),
            @('failsafe', "$internalPackage.${title}OutboxRelayIT", 9),
            @('failsafe', "$internalPackage.${title}KafkaDeliveryIT", 3)
        )
        foreach ($suite in $suites) {
            $report = Join-Path $repositoryRoot "simulators/$owner-service/target/$($suite[0])-reports/TEST-$($suite[1]).xml"
            if (-not (Test-Path -LiteralPath $report -PathType Leaf)) { throw "Missing report: $($suite[1])" }
            if ((Get-Item -LiteralPath $report).LastWriteTimeUtc -lt $started) { throw "Stale report: $($suite[1])" }
            [xml]$xml = Get-Content -LiteralPath $report -Raw
            foreach ($attribute in @('tests','failures','errors','skipped')) {
                if ($xml.testsuite.GetAttribute($attribute) -notmatch '^\d+$') { throw 'Invalid suite count.' }
            }
            if ([int]$xml.testsuite.tests -ne [int]$suite[2] -or [int]$xml.testsuite.failures -ne 0 -or
                [int]$xml.testsuite.errors -ne 0 -or [int]$xml.testsuite.skipped -ne 0) {
                throw "All expected tests must execute with zero failures/errors/skips: $($suite[1])"
            }
        }
    }
    Write-Output 'PASS: 40 Payment/Warehouse unit/boundary tests and 24 real PostgreSQL/Kafka relay tests, zero failures/errors/skips.'
    Write-Output 'This does not verify Core durable inbox or complete P0.3. No existing Compose volumes were targeted.'
} finally {
    Pop-Location
    foreach ($name in $saved.Keys) { [Environment]::SetEnvironmentVariable($name, $saved[$name], 'Process') }
}

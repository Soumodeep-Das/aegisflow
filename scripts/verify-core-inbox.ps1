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
if ($LASTEXITCODE -ne 0) { throw 'Docker unavailable; Core inbox live tests cannot pass.' }
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
    Write-Output 'Compiling the existing reactor; completed simulator test suites are not selected.'
    & .\mvnw.cmd -B -ntp "-Dmaven.repo.local=$mavenCache" -Ppipeline-tests test-compile
    if ($LASTEXITCODE -ne 0) { throw 'Pipeline compilation failed; preserve first error.' }
    Write-Output 'Running Core unit/boundary tests, changed Core migration/startup checks, and actual three-owner pipeline cases.'
    & .\mvnw.cmd -B -ntp "-Dmaven.repo.local=$mavenCache" -pl control-plane '-Pinfrastructure-tests,pipeline-tests' '-Dtest=EventValidatorTests,KafkaInboxConsumerTests,PipelinePortTests,ControlPlaneArchitectureTests' '-Dit.test=CorePipelineScenario,PostgresInfrastructureIT' test failsafe:integration-test failsafe:verify
    if ($LASTEXITCODE -ne 0) { throw 'Core inbox gate failed; preserve Core target/surefire-reports and target/failsafe-reports.' }
    $suites = @(
        @('surefire','com.aegisflow.core.eventing.internal.EventValidatorTests',23),
        @('surefire','com.aegisflow.core.eventing.internal.KafkaInboxConsumerTests',5),
        @('surefire','com.aegisflow.core.eventing.internal.PipelinePortTests',4),
        @('surefire','com.aegisflow.core.ControlPlaneArchitectureTests',4),
        @('failsafe','com.aegisflow.core.eventing.internal.CorePipelineScenario',10),
        @('failsafe','com.aegisflow.core.PostgresInfrastructureIT',4)
    )
    foreach ($suite in $suites) {
        $report = Join-Path $repositoryRoot "control-plane/target/$($suite[0])-reports/TEST-$($suite[1]).xml"
        if (-not (Test-Path -LiteralPath $report -PathType Leaf)) { throw "Missing report: $($suite[1])" }
        if ((Get-Item -LiteralPath $report).LastWriteTimeUtc -lt $started) { throw "Stale report: $($suite[1])" }
        [xml]$xml = Get-Content -LiteralPath $report -Raw
        foreach ($attribute in @('tests','failures','errors','skipped')) {
            if ($xml.testsuite.GetAttribute($attribute) -notmatch '^\d+$') { throw 'Invalid suite count.' }
        }
        if ([int]$xml.testsuite.tests -ne [int]$suite[2] -or [int]$xml.testsuite.failures -ne 0 -or
            [int]$xml.testsuite.errors -ne 0 -or [int]$xml.testsuite.skipped -ne 0) {
            throw "Every expected case must execute with zero failures/errors/skips: $($suite[1])"
        }
    }
    Write-Output 'PASS: 36 Core unit/boundary/port and 14 real infrastructure/pipeline tests, zero failures/errors/skips.'
    Write-Output 'Actual Order/Payment/Warehouse services and relays feed the Core inbox; duplicate/conflict, restart, rollback, commit-before-ack and poison/isolation cases passed.'
    Write-Output 'Packaged scheduled startup and final reactor acceptance remain separate. Existing Compose volumes are not targeted.'
} finally {
    Pop-Location
    foreach ($name in $saved.Keys) { [Environment]::SetEnvironmentVariable($name, $saved[$name], 'Process') }
}

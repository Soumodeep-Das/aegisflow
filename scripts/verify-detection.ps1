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
if ($LASTEXITCODE -ne 0) { throw 'Docker unavailable; real PostgreSQL detection verification cannot pass.' }
$saved = @{}
foreach ($name in @('JAVA_HOME','TEMP','TMP')) { $saved[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
Push-Location $repositoryRoot
try {
    $env:JAVA_HOME = $JavaHome
    $temporary = Join-Path $repositoryRoot '.cache/tmp'
    New-Item -ItemType Directory -Force -Path $temporary | Out-Null
    $env:TEMP = $temporary
    $env:TMP = $temporary
    $evidence = Join-Path $repositoryRoot ('.cache/p04b/' + [Guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $evidence | Out-Null
    foreach ($kind in @('surefire','failsafe')) {
        $previous = Join-Path $repositoryRoot "control-plane/target/$kind-reports"
        if (Test-Path -LiteralPath $previous) {
            Copy-Item -LiteralPath $previous -Destination (Join-Path $evidence "prior-$kind") -Recurse
        }
    }
    Write-Output "Detection evidence: $evidence"
    Write-Output 'Running only the unverified P0.4-B PostgreSQL gate and Core migration/startup regression. The 18 passing focused unit tests are not repeated.'
    $started = [DateTime]::UtcNow
    $mavenCache = Join-Path $repositoryRoot '.cache/maven-repository'
    & .\mvnw.cmd -B -ntp "-Dmaven.repo.local=$mavenCache" -pl control-plane -Pinfrastructure-tests '-Dit.test=DeadlineDetectionIT,PostgresInfrastructureIT' -l (Join-Path $evidence 'postgres.log') test-compile failsafe:integration-test failsafe:verify
    $mavenExit = $LASTEXITCODE
    $reports = Join-Path $repositoryRoot 'control-plane/target/failsafe-reports'
    if (Test-Path -LiteralPath $reports) {
        Copy-Item -LiteralPath $reports -Destination (Join-Path $evidence 'reports') -Recurse
    }
    if ($mavenExit -ne 0) { throw "Detection gate failed (Maven exit $mavenExit); preserve $evidence/postgres.log and reports." }
    foreach ($suite in @(
        @('com.aegisflow.core.detection.internal.DeadlineDetectionIT',17),
        @('com.aegisflow.core.PostgresInfrastructureIT',4)
    )) {
        $report = Join-Path $reports "TEST-$($suite[0]).xml"
        if (-not (Test-Path -LiteralPath $report -PathType Leaf)) { throw "Missing report: $($suite[0])" }
        if ((Get-Item -LiteralPath $report).LastWriteTimeUtc -lt $started) { throw "Stale report: $($suite[0])" }
        [xml]$xml = Get-Content -LiteralPath $report -Raw
        foreach ($attribute in @('tests','failures','errors','skipped')) {
            if ($xml.testsuite.GetAttribute($attribute) -notmatch '^\d+$') { throw 'Invalid suite count.' }
        }
        if ([int]$xml.testsuite.tests -ne $suite[1] -or [int]$xml.testsuite.failures -ne 0 -or
            [int]$xml.testsuite.errors -ne 0 -or [int]$xml.testsuite.skipped -ne 0) {
            throw "All expected tests must execute with zero failures/errors/skips: $($suite[0]). Preserve $evidence."
        }
    }
    Write-Output 'PASS: 17 durable detection tests and 4 Core PostgreSQL migration/startup tests; zero failures/errors/skips.'
    Write-Output 'This verifies P0.4-B only, not incidents or complete P0.4. Testcontainers uses disposable databases and dynamic ports; existing Compose storage is not targeted.'
} finally {
    Pop-Location
    foreach ($name in $saved.Keys) { [Environment]::SetEnvironmentVariable($name, $saved[$name], 'Process') }
}

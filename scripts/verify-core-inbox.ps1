#requires -Version 7.0
[CmdletBinding()]
param([string]$JavaHome = $env:JAVA_HOME, [switch]$CoreStartupOnly)
$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot 'lib/core-inbox-verification.ps1')
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
    $plan = @(Get-CoreInboxVerificationPlan -MavenCache $mavenCache -CoreStartupOnly:$CoreStartupOnly)
    foreach ($phase in $plan) {
        $started = [DateTime]::UtcNow
        Write-Output "Core inbox verification phase: $($phase.Name)"
        $mavenArguments = $phase.Arguments
        & .\mvnw.cmd @mavenArguments
        if ($LASTEXITCODE -ne 0) { throw "Core inbox $($phase.Name) failed; preserve the first error and Core test reports." }
        foreach ($suite in $phase.Suites) {
            $report = Join-Path $repositoryRoot "control-plane/target/$($suite.Kind)-reports/TEST-$($suite.Name).xml"
            if (-not (Test-Path -LiteralPath $report -PathType Leaf)) { throw "Missing report: $($suite.Name)" }
            if ((Get-Item -LiteralPath $report).LastWriteTimeUtc -lt $started) { throw "Stale report: $($suite.Name)" }
            [xml]$xml = Get-Content -LiteralPath $report -Raw
            foreach ($attribute in @('tests','failures','errors','skipped')) {
                if ($xml.testsuite.GetAttribute($attribute) -notmatch '^\d+$') { throw 'Invalid suite count.' }
            }
            if ([int]$xml.testsuite.tests -ne [int]$suite.Tests -or [int]$xml.testsuite.failures -ne 0 -or
                [int]$xml.testsuite.errors -ne 0 -or [int]$xml.testsuite.skipped -ne 0) {
                throw "Every expected case must execute with zero failures/errors/skips: $($suite.Name)"
            }
        }
    }
    if ($CoreStartupOnly) {
        Write-Output 'PASS: four Core-only PostgreSQL migration/startup/role checks, zero failures/errors/skips.'
        Write-Output 'Previously passed pipeline/unit tests were not rerun or revalidated by this partial command.'
    } else {
        Write-Output 'PASS: 36 Core unit/boundary/port and 14 real infrastructure/pipeline tests, zero failures/errors/skips.'
    }
    Write-Output 'Packaged scheduled startup and final reactor acceptance remain separate. Existing Compose volumes are not targeted.'
} finally {
    Pop-Location
    foreach ($name in $saved.Keys) { [Environment]::SetEnvironmentVariable($name, $saved[$name], 'Process') }
}

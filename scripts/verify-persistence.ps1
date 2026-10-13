#requires -Version 7.0
[CmdletBinding()]
param([string]$JavaHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$java = Join-Path $JavaHome 'bin/java.exe'
if (-not (Test-Path -LiteralPath $java)) { throw 'Set JAVA_HOME to an installed Java 21 JDK.' }
if ((& $java -version 2>&1 | Out-String) -notmatch 'version "21\.') { throw 'Java 21 is required.' }
docker info --format '{{.ServerVersion}}'
if ($LASTEXITCODE -ne 0) { throw 'Docker engine unavailable; PostgreSQL verification cannot pass.' }
Push-Location $repositoryRoot
try {
    $env:JAVA_HOME = $JavaHome
    .\mvnw.cmd -B -ntp -Pinfrastructure-tests clean verify
    if ($LASTEXITCODE -ne 0) { throw 'Persistence reactor verification failed.' }
    $reports = @(Get-ChildItem control-plane/target/failsafe-reports, simulators/*/target/failsafe-reports -Filter 'TEST-*.xml' -ErrorAction Stop)
    $counts = @{ Tests = 0; Failures = 0; Errors = 0; Skipped = 0 }
    foreach ($report in $reports) {
        [xml]$xml = Get-Content -LiteralPath $report.FullName -Raw
        foreach ($entry in @(@('Tests','tests'), @('Failures','failures'), @('Errors','errors'), @('Skipped','skipped'))) {
            $counts[$entry[0]] += [int]$xml.testsuite.GetAttribute($entry[1])
        }
    }
    Write-Output "PostgreSQL IT: tests=$($counts.Tests), failures=$($counts.Failures), errors=$($counts.Errors), skipped=$($counts.Skipped)"
    if ($counts.Tests -eq 0 -or $counts.Failures -ne 0 -or $counts.Errors -ne 0 -or $counts.Skipped -ne 0) {
        throw 'P0.2 PostgreSQL gate requires executed tests with zero failures/errors/skips.'
    }
} finally { Pop-Location }

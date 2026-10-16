#requires -Version 7.0
[CmdletBinding()]
param([string]$JavaHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'

function Get-PersistenceReportSummary {
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$RepositoryRoot)

    $owners = @(
        [pscustomobject]@{ Name = 'Core'; Path = Join-Path $RepositoryRoot 'control-plane/target/failsafe-reports' }
        [pscustomobject]@{ Name = 'Order'; Path = Join-Path $RepositoryRoot 'simulators/order-service/target/failsafe-reports' }
        [pscustomobject]@{ Name = 'Payment'; Path = Join-Path $RepositoryRoot 'simulators/payment-service/target/failsafe-reports' }
        [pscustomobject]@{ Name = 'Warehouse'; Path = Join-Path $RepositoryRoot 'simulators/warehouse-service/target/failsafe-reports' }
    )
    $total = [ordered]@{ Tests = 0; Failures = 0; Errors = 0; Skipped = 0 }

    foreach ($owner in $owners) {
        if (-not (Test-Path -LiteralPath $owner.Path -PathType Container)) {
            throw "Missing $($owner.Name) PostgreSQL Failsafe report directory: $($owner.Path)"
        }

        $reports = @(Get-ChildItem -LiteralPath $owner.Path -File -Filter 'TEST-*.xml' -ErrorAction Stop)
        if ($reports.Count -eq 0) {
            throw "Missing $($owner.Name) PostgreSQL Failsafe XML reports in: $($owner.Path)"
        }

        $ownerCounts = [ordered]@{ Tests = 0; Failures = 0; Errors = 0; Skipped = 0 }
        foreach ($report in $reports) {
            [xml]$xml = Get-Content -LiteralPath $report.FullName -Raw
            if ($null -eq $xml.testsuite) {
                throw "Invalid Failsafe XML report: $($report.FullName)"
            }
            foreach ($entry in @(@('Tests','tests'), @('Failures','failures'), @('Errors','errors'), @('Skipped','skipped'))) {
                $value = $xml.testsuite.GetAttribute($entry[1])
                if ($value -notmatch '^\d+$') {
                    throw "Failsafe XML report has an invalid $($entry[1]) count: $($report.FullName)"
                }
                $ownerCounts[$entry[0]] += [int]$value
            }
        }

        if ($ownerCounts.Tests -eq 0) {
            throw "$($owner.Name) PostgreSQL gate requires at least one executed test."
        }
        if ($ownerCounts.Failures -ne 0 -or $ownerCounts.Errors -ne 0 -or $ownerCounts.Skipped -ne 0) {
            throw "$($owner.Name) PostgreSQL gate requires zero failures/errors/skips."
        }
        foreach ($name in @('Tests', 'Failures', 'Errors', 'Skipped')) {
            $total[$name] += $ownerCounts[$name]
        }
    }

    return [pscustomobject]$total
}

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
    $counts = Get-PersistenceReportSummary -RepositoryRoot $repositoryRoot
    Write-Output "PostgreSQL IT: tests=$($counts.Tests), failures=$($counts.Failures), errors=$($counts.Errors), skipped=$($counts.Skipped)"
} finally { Pop-Location }

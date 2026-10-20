#requires -Version 7.0
[CmdletBinding()]
param([string]$JavaHome=$env:JAVA_HOME,[switch]$PackagedOnly)
$ErrorActionPreference='Stop'
$repositoryRoot=Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($JavaHome)) { throw 'Set JAVA_HOME to Java 21.' }
$java=Join-Path $JavaHome 'bin/java.exe'
if (-not (Test-Path -LiteralPath $java -PathType Leaf) -or
    ((& $java -version 2>&1 | Out-String) -notmatch 'version "21\.')) { throw 'Java 21 is required.' }
docker info --format '{{.ServerVersion}}'
if ($LASTEXITCODE -ne 0) { throw 'Docker is unavailable; packaged acceptance cannot pass.' }
$saved=@{}
foreach($name in @('JAVA_HOME','TEMP','TMP')) { $saved[$name]=[Environment]::GetEnvironmentVariable($name,'Process') }
Push-Location $repositoryRoot
try {
    $env:JAVA_HOME=$JavaHome
    $env:TEMP=Join-Path $repositoryRoot '.cache/tmp'
    New-Item -ItemType Directory -Force -Path $env:TEMP | Out-Null
    $env:TMP=$env:TEMP
    $cache=Join-Path $repositoryRoot '.cache/maven-repository'
    $evidence=Join-Path $repositoryRoot ('.cache/p03-final/'+[Guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Force -Path $evidence | Out-Null
    Write-Output "Final acceptance evidence: $evidence"
    $modules=@('control-plane','simulators/order-service','simulators/payment-service','simulators/warehouse-service')
    if (-not $PackagedOnly) {
        # Preserve all existing passing/failing XML/text evidence before the normal Maven clean target.
        foreach($module in $modules) {
            foreach($kind in @('surefire','failsafe')) {
                $source=Join-Path $repositoryRoot "$module/target/$kind-reports"
                if(Test-Path -LiteralPath $source -PathType Container) {
                    $destination=Join-Path $evidence ('prior/'+($module -replace '/','-')+"/$kind-reports")
                    New-Item -ItemType Directory -Force -Path $destination | Out-Null
                    Get-ChildItem -LiteralPath $source -File | Copy-Item -Destination $destination
                }
            }
        }
        $started=[DateTime]::UtcNow
        $buildLog=Join-Path $evidence 'reactor.log'
        Write-Output 'Final clean default reactor: all unit/foundation HTTP checks and executable packaging. Completed infrastructure/relay/pipeline ITs are not selected.'
        & .\mvnw.cmd -B -ntp "-Dmaven.repo.local=$cache" -l $buildLog clean verify
        if($LASTEXITCODE -ne 0) { throw "Final reactor failed; preserve $buildLog" }
        $total=0
        foreach($module in $modules) {
            $reports=@(Get-ChildItem -LiteralPath (Join-Path $repositoryRoot "$module/target/surefire-reports") -Filter 'TEST-*.xml' -File)
            if($reports.Count -eq 0) { throw "No default test reports: $module" }
            foreach($report in $reports) {
                if($report.LastWriteTimeUtc -lt $started) { throw "Stale report: $($report.Name)" }
                [xml]$xml=Get-Content -LiteralPath $report.FullName -Raw
                foreach($name in @('tests','failures','errors','skipped')) {
                    if($xml.testsuite.GetAttribute($name) -notmatch '^\d+$') { throw 'Invalid reactor test count.' }
                }
                if([int]$xml.testsuite.tests -le 0 -or [int]$xml.testsuite.failures -ne 0 -or
                    [int]$xml.testsuite.errors -ne 0 -or [int]$xml.testsuite.skipped -ne 0) { throw "Default tests must pass without skips: $($report.Name)" }
                $total += [int]$xml.testsuite.tests
            }
        }
        Write-Output "PASS: clean default reactor/executable packaging; $total tests, zero failures/errors/skips."
    }
    foreach($module in $modules) {
        $artifact=if($module -eq 'control-plane') { 'aegisflow-core' } else { Split-Path -Leaf $module }
        $jar=Join-Path $repositoryRoot "$module/target/$artifact-0.1.0-SNAPSHOT.jar"
        if(-not (Test-Path -LiteralPath $jar -PathType Leaf)) { throw 'Build executable JARs with the full final verifier first.' }
        $inputs=@(Get-ChildItem -LiteralPath (Join-Path $repositoryRoot "$module/src/main") -Recurse -File)
        $inputs += Get-Item -LiteralPath (Join-Path $repositoryRoot "$module/pom.xml"), (Join-Path $repositoryRoot 'pom.xml')
        $latest=($inputs | Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1).LastWriteTimeUtc
        if((Get-Item -LiteralPath $jar).LastWriteTimeUtc -lt $latest) { throw "Executable JAR is older than its production inputs: $module; run the full verifier." }
    }
    $started=[DateTime]::UtcNow
    $packagedLog=Join-Path $evidence 'packaged.log'
    Write-Output 'Running the new packaged scheduler/Core-process-restart case; detailed output is retained in packaged.log.'
    & .\mvnw.cmd -B -ntp "-Dmaven.repo.local=$cache" -pl control-plane '-Pinfrastructure-tests,pipeline-tests' '-Dit.test=PackagedPipelineScenario' -l $packagedLog test-compile failsafe:integration-test failsafe:verify
    if($LASTEXITCODE -ne 0) { throw "Packaged scheduled gate failed; preserve $packagedLog and .cache/packaged-pipeline logs." }
    $report=Join-Path $repositoryRoot 'control-plane/target/failsafe-reports/TEST-com.aegisflow.core.eventing.internal.PackagedPipelineScenario.xml'
    if(-not (Test-Path -LiteralPath $report -PathType Leaf) -or (Get-Item -LiteralPath $report).LastWriteTimeUtc -lt $started) { throw 'Missing/stale packaged report.' }
    [xml]$xml=Get-Content -LiteralPath $report -Raw
    foreach($name in @('tests','failures','errors','skipped')) {
        if($xml.testsuite.GetAttribute($name) -notmatch '^\d+$') { throw 'Invalid packaged test count.' }
    }
    if([int]$xml.testsuite.tests -ne 1 -or [int]$xml.testsuite.failures -ne 0 -or
        [int]$xml.testsuite.errors -ne 0 -or [int]$xml.testsuite.skipped -ne 0) { throw 'Packaged case must actually execute and pass without skips.' }
    Write-Output 'PASS: four packaged scheduled applications, own databases/credentials, real Kafka intake and actual Core process restart with offline backlog.'
    if($PackagedOnly) { Write-Output 'Only packaged acceptance was run; the default reactor was not repeated or revalidated.' }
    & (Join-Path $PSScriptRoot 'verify-docs.ps1')
    if(-not $?) { throw 'Documentation check failed.' }
    Write-Output 'No existing Compose project/volume was targeted. Preserve the evidence directory and share the final results.'
} finally {
    Pop-Location
    foreach($name in $saved.Keys) { [Environment]::SetEnvironmentVariable($name,$saved[$name],'Process') }
}

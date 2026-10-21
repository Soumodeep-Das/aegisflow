#requires -Version 7.0
[CmdletBinding()]
param([string]$JavaHome=$env:JAVA_HOME,[ValidateSet('All','Pipeline','Packaged','Reactor','Remaining')][string]$Gate='All')
$ErrorActionPreference='Stop'
$repositoryRoot=Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot 'lib/packaged-artifact.ps1')
if ([string]::IsNullOrWhiteSpace($JavaHome)) { throw 'Set JAVA_HOME to Java 21.' }
$java=Join-Path $JavaHome 'bin/java.exe'
if (-not (Test-Path -LiteralPath $java -PathType Leaf) -or
    ((& $java -version 2>&1 | Out-String) -notmatch 'version "21\.')) { throw 'Java 21 is required.' }
docker info --format '{{.ServerVersion}}'
if ($LASTEXITCODE -ne 0) { throw 'Docker unavailable: P0.4 acceptance cannot pass.' }
$saved=@{}
foreach($name in @('JAVA_HOME','TEMP','TMP')) { $saved[$name]=[Environment]::GetEnvironmentVariable($name,'Process') }
Push-Location $repositoryRoot
try {
    $env:JAVA_HOME=$JavaHome
    $env:TEMP=Join-Path $repositoryRoot '.cache/tmp'
    New-Item -ItemType Directory -Force -Path $env:TEMP | Out-Null
    $env:TMP=$env:TEMP
    $cache=Join-Path $repositoryRoot '.cache/maven-repository'
    $evidence=Join-Path $repositoryRoot ('.cache/p04d/'+[Guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $evidence | Out-Null
    $modules=@('control-plane','simulators/order-service','simulators/payment-service','simulators/warehouse-service')
    function Save-Reports([string]$Label) {
        foreach($module in $modules) {
            foreach($kind in @('surefire','failsafe')) {
                $source=Join-Path $repositoryRoot "$module/target/$kind-reports"
                if(Test-Path -LiteralPath $source) {
                    $destination=Join-Path $evidence "$Label/$($module -replace '/','-')/$kind"
                    New-Item -ItemType Directory -Force -Path $destination | Out-Null
                    Get-ChildItem -LiteralPath $source -File | Copy-Item -Destination $destination
                }
            }
        }
    }
    function Assert-Report([string]$Path,[datetime]$Started,[int]$Expected=0) {
        if(-not (Test-Path -LiteralPath $Path) -or (Get-Item -LiteralPath $Path).LastWriteTimeUtc -lt $Started) { throw "Missing/stale report: $Path" }
        [xml]$xml=Get-Content -LiteralPath $Path -Raw
        foreach($attribute in @('tests','failures','errors','skipped')) {
            if($xml.testsuite.GetAttribute($attribute) -notmatch '^\d+$') { throw "Invalid count: $Path" }
        }
        $count=[int]$xml.testsuite.tests
        if($count -le 0 -or ($Expected -gt 0 -and $count -ne $Expected) -or
            [int]$xml.testsuite.failures -ne 0 -or [int]$xml.testsuite.errors -ne 0 -or [int]$xml.testsuite.skipped -ne 0) {
            throw "Expected actual passing tests, zero failures/errors/skips: $Path. Preserve $evidence."
        }
        return $count
    }
    Save-Reports 'prior'
    # Preserve child-process diagnostics before Maven clean removes target/.
    $children=Join-Path $repositoryRoot 'control-plane/target/incident-process'
    if(Test-Path -LiteralPath $children) { Copy-Item -LiteralPath $children -Destination (Join-Path $evidence 'prior-incident-process') -Recurse }
    Write-Output "P0.4-D evidence: $evidence"
    if($Gate -in @('All','Reactor')) {
        $started=[DateTime]::UtcNow
        & .\mvnw.cmd -B -ntp "-Dmaven.repo.local=$cache" -l (Join-Path $evidence 'reactor.log') clean verify
        $code=$LASTEXITCODE; Save-Reports 'reactor'
        if($code -ne 0) { throw "Final reactor failed; preserve $evidence/reactor.log" }
        $total=0
        foreach($module in $modules) {
            $reports=@(Get-ChildItem -LiteralPath "$module/target/surefire-reports" -Filter 'TEST-*.xml' -File)
            if($reports.Count -eq 0) { throw "Missing default reports: $module" }
            foreach($report in $reports) { $total += Assert-Report $report.FullName $started }
        }
        Write-Output "PASS: clean default reactor, $total tests, zero failures/errors/skips; four executable archives built."
    }
    if($Gate -in @('All','Pipeline','Packaged','Remaining')) {
        foreach($module in $modules) {
            $artifact=if($module -eq 'control-plane') { 'aegisflow-core' } else { Split-Path -Leaf $module }
            $inputs=@(Get-ChildItem -LiteralPath "$module/src/main" -Recurse -File)
            $inputs+=Get-Item -LiteralPath "$module/pom.xml",'pom.xml'
            Assert-RepackagedJarCurrent -JarPath (Join-Path $repositoryRoot "$module/target/$artifact-0.1.0-SNAPSHOT.jar") -InputPaths $inputs.FullName
        }
        $suites=@()
        if($Gate -eq 'Remaining') { $suites+=@{ Name='DetectionPipelineScenario'; Tests=1; Selection='DetectionPipelineScenario#contradictoryWarehouseEvidenceCreatesIncident' } }
        if($Gate -in @('All','Pipeline')) { $suites+=@{ Name='DetectionPipelineScenario'; Tests=7 } }
        if($Gate -in @('All','Packaged','Remaining')) { $suites+=@{ Name='PackagedDetectionScenario'; Tests=1 } }
        foreach($suite in $suites) {
            $started=[DateTime]::UtcNow
            $log=Join-Path $evidence "$($suite.Name).log"
            Write-Output "Running $($suite.Name); details in $log"
            $selection=if($suite.ContainsKey('Selection')) { $suite.Selection } else { $suite.Name }
            & .\mvnw.cmd -B -ntp "-Dmaven.repo.local=$cache" -pl control-plane '-Pinfrastructure-tests,pipeline-tests' "-Dit.test=$selection" -l $log test-compile failsafe:integration-test failsafe:verify
            $code=$LASTEXITCODE; Save-Reports $suite.Name
            if($code -ne 0) { throw "Gate failed: $($suite.Name). Preserve $evidence and .cache/packaged-detection." }
            $report=Join-Path $repositoryRoot "control-plane/target/failsafe-reports/TEST-com.aegisflow.core.eventing.internal.$($suite.Name).xml"
            $count=Assert-Report $report $started $suite.Tests
            Write-Output "PASS: $($suite.Name), $count tests, zero failures/errors/skips."
        }
    }
    & (Join-Path $PSScriptRoot 'verify-docs.ps1')
    if(-not $?) { throw 'Documentation check failed.' }
    Write-Output "Selected gate $Gate passed. Partial gates do not revalidate omitted gates; preserve evidence for final P0.4 closure."
    Write-Output 'No permanent .env or existing Compose storage targeted. Testcontainers owns only disposable test infrastructure.'
} finally {
    Pop-Location
    foreach($name in $saved.Keys) { [Environment]::SetEnvironmentVariable($name,$saved[$name],'Process') }
}

#requires -Version 7.0
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'lib/core-inbox-verification.ps1')
$normal = @(Get-CoreInboxVerificationPlan -MavenCache 'C:\test cache\maven')
if ($normal.Count -ne 3 -or ($normal.Name -join ',') -cne 'compile,pipeline,core-startup') { throw 'Full plan order is invalid.' }
if ($normal[0].Arguments -notcontains 'test-compile' -or $normal[0].Arguments -contains 'test') { throw 'Compile must not run completed tests.' }
Write-Output 'PASS: full plan compiles first without running simulator tests.'

$pipeline = $normal[1]
if ($pipeline.Arguments -notcontains '-Pinfrastructure-tests,pipeline-tests' -or
    $pipeline.Arguments -notcontains '-Dit.test=CorePipelineScenario' -or
    ($pipeline.Arguments -join ' ') -match 'PostgresInfrastructureIT') { throw 'Pipeline phase contains the wrong suites/profile.' }
Write-Output 'PASS: pipeline profile selects only the intended pipeline IT.'

$startup = $normal[2]
if ($startup.Arguments -notcontains '-Pinfrastructure-tests' -or
    $startup.Arguments -notcontains '-Dit.test=PostgresInfrastructureIT' -or
    ($startup.Arguments -join ' ') -match 'pipeline-tests|CorePipelineScenario|additionalClasspath') { throw 'Core startup classpath is contaminated.' }
if ($startup.Suites.Count -ne 1 -or $startup.Suites[0].Tests -ne 4) { throw 'Core startup report expectations changed.' }
Write-Output 'PASS: Core startup has its own normal classpath and four required cases.'

$partial = @(Get-CoreInboxVerificationPlan -MavenCache 'C:\test cache\maven' -CoreStartupOnly)
if ($partial.Count -ne 1 -or $partial[0].Name -cne 'core-startup' -or
    ($partial[0].Arguments -join ' ') -cne ($startup.Arguments -join ' ')) { throw 'Partial run must select only the unfinished Core gate.' }
Write-Output 'PASS: resume switch does not repeat successful pipeline/unit suites.'

$total = ($normal.Suites | Measure-Object -Property Tests -Sum).Sum
if ($total -ne 50 -or $normal[1].Arguments -notcontains '-Dmaven.repo.local=C:\test cache\maven') { throw 'Report counts/cache argv lost.' }
Write-Output 'PASS: all 50 expected cases and the single cache argument are preserved.'

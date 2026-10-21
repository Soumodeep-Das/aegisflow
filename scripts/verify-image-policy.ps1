#requires -Version 7.0
[CmdletBinding()]
param([string]$JavaHome=$env:JAVA_HOME)
$ErrorActionPreference='Stop'
$root=Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot 'lib/application-images.ps1')
$plan=@(Get-ApplicationImagePlan)
if($plan.Count -ne 4 -or @($plan.Owner | Select-Object -Unique).Count -ne 4) { throw 'Expected four distinct application mappings.' }
foreach($app in $plan) {
    Assert-ApplicationArchive -Jar "$root/$($app.Module)/target/$($app.Artifact)-0.1.0-SNAPSHOT.jar" -Main $app.Main
}
if((Get-ApplicationLoopbackPort @('127.0.0.1:54321')) -ne 54321) { throw 'Port parse failed.' }
foreach($bad in @('0.0.0.0:8080','[::]:8080','127.0.0.1:0','127.0.0.1:65536','127.0.0.1:8080 extra')) {
    $rejected=$false
    try { Get-ApplicationLoopbackPort @($bad) | Out-Null } catch { $rejected=$true }
    if(-not $rejected) { throw 'Unsafe port binding accepted.' }
}
$dockerfile=Get-Content "$root/docker/Dockerfile" -Raw
if($dockerfile -notmatch '(?m)^FROM eclipse-temurin:21-jre-jammy@sha256:[a-f0-9]{64}$' -or
    $dockerfile -notmatch '(?m)^USER 10001:10001$' -or $dockerfile -match '(?m)^ADD ' -or
    @([regex]::Matches($dockerfile,'(?m)^COPY ')).Count -ne 2) { throw 'Docker packaging policy failed.' }
$ignore=@(Get-Content "$root/.dockerignore" | Where-Object { $_ -and -not $_.StartsWith('#') })
if(($ignore -join ',') -ne '**,!Dockerfile,!.dockerignore,!app.jar,!HealthProbe.class') { throw 'Build-context allowlist drift.' }
foreach($file in @('dev-build-images.ps1','verify-application-images.ps1','verify-image-policy.ps1','lib/application-images.ps1')) {
    $tokens=$null; $parseErrors=$null
    [Management.Automation.Language.Parser]::ParseFile("$root/scripts/$file",[ref]$tokens,[ref]$parseErrors) | Out-Null
    if($parseErrors.Count) { throw "PowerShell parse failed: $file" }
}
$classes=Join-Path $root ('.cache/image-policy/'+[guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force -Path $classes | Out-Null
& "$JavaHome/bin/javac.exe" --release 21 -d $classes "$root/docker/HealthProbe.java" "$root/docker/HealthProbeTests.java"
if($LASTEXITCODE -ne 0) { throw 'Probe compile failed.' }
& "$JavaHome/bin/java.exe" -cp $classes HealthProbeTests
if($LASTEXITCODE -ne 0) { throw 'Probe tests failed.' }
Write-Output 'PASS: 4 executable identities, distinct mappings, loopback parser/5 rejection cases, Dockerfile/context policy and 4 PowerShell parses.'
Write-Output 'Static checks only. Docker image build/start/non-root/network checks remain separate.'

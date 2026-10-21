#requires -Version 7.0
[CmdletBinding()]
param([string]$JavaHome=$env:JAVA_HOME,[switch]$UseExistingArtifacts)
$ErrorActionPreference='Stop'
$root=Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot 'lib/application-images.ps1')
. (Join-Path $PSScriptRoot 'lib/packaged-artifact.ps1')
if([string]::IsNullOrWhiteSpace($JavaHome)) { throw 'Set JAVA_HOME to a Java21 JDK.' }
$java=Join-Path $JavaHome 'bin/java.exe'; $javac=Join-Path $JavaHome 'bin/javac.exe'
if(-not(Test-Path $javac) -or ((& $java -version 2>&1 | Out-String) -notmatch 'version "21\.')) { throw 'Java21 JDK required.' }
& docker info --format '{{.ServerVersion}}' | Out-Null
if($LASTEXITCODE -ne 0) { throw 'Docker unavailable; no images or private configuration changed.' }
$saved=@{}
foreach($key in @('JAVA_HOME','TEMP','TMP')) { $saved[$key]=[Environment]::GetEnvironmentVariable($key,'Process') }
Push-Location $root
try {
    $env:JAVA_HOME=$JavaHome; $env:TEMP=Join-Path $root '.cache/tmp'; $env:TMP=$env:TEMP
    New-Item -ItemType Directory -Force -Path $env:TEMP | Out-Null
    $stage=Join-Path $root ('.cache/application-images/'+[guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $stage | Out-Null
    Write-Output "Image build evidence: $stage"
    $plan=Get-ApplicationImagePlan
    if(-not $UseExistingArtifacts) {
        # Preserve every report before clean; compile/package reactor exactly once, not once per image.
        foreach($app in $plan) {
            foreach($kind in @('surefire','failsafe')) {
                $reports=Join-Path $root "$($app.Module)/target/$kind-reports"
                if(Test-Path $reports) { Copy-Item $reports -Destination (Join-Path $stage "$($app.Owner)-prior-$kind") -Recurse }
            }
        }
        $childLogs=Join-Path $root 'control-plane/target/incident-process'
        if(Test-Path $childLogs) { Copy-Item $childLogs -Destination "$stage/prior-incident-process" -Recurse }
        $started=[datetime]::UtcNow
        & .\mvnw.cmd -B -ntp "-Dmaven.repo.local=$root/.cache/maven-repository" -l "$stage/reactor.log" clean verify
        $code=$LASTEXITCODE; $total=0
        foreach($app in $plan) {
            $reports=Join-Path $root "$($app.Module)/target/surefire-reports"
            if(Test-Path $reports) { Copy-Item $reports -Destination "$stage/$($app.Owner)-surefire" -Recurse }
        }
        if($code -ne 0) { throw "Reactor failed; preserve $stage/reactor.log" }
        foreach($app in $plan) {
            $reports=@(Get-ChildItem "$($app.Module)/target/surefire-reports/TEST-*.xml")
            if($reports.Count -eq 0) { throw 'Missing default test reports.' }
            foreach($report in $reports) {
                [xml]$xml=Get-Content $report.FullName -Raw
                if($report.LastWriteTimeUtc -lt $started -or [int]$xml.testsuite.tests -le 0 -or
                    [int]$xml.testsuite.failures -ne 0 -or [int]$xml.testsuite.errors -ne 0 -or [int]$xml.testsuite.skipped -ne 0) { throw 'Default tests must be fresh, executed and passing.' }
                $total += [int]$xml.testsuite.tests
            }
        }
        Write-Output "PASS: reactor $total tests, zero failures/errors/skips."
    }
    $classes=Join-Path $stage 'probe'; New-Item -ItemType Directory -Path $classes | Out-Null
    & $javac --release 21 -d $classes docker/HealthProbe.java
    if($LASTEXITCODE -ne 0) { throw 'Health probe compilation failed.' }
    $revision=(& git rev-parse HEAD).Trim()
    if($LASTEXITCODE -ne 0) { throw 'Cannot resolve source revision.' }
    $dirty=(@(& git status --porcelain).Count -gt 0)
    foreach($app in $plan) {
        $jar=Join-Path $root "$($app.Module)/target/$($app.Artifact)-0.1.0-SNAPSHOT.jar"
        $inputs=@(Get-ChildItem "$($app.Module)/src/main" -Recurse -File); $inputs+=Get-Item "$($app.Module)/pom.xml",'pom.xml'
        Assert-RepackagedJarCurrent -JarPath $jar -InputPaths $inputs.FullName
        Assert-ApplicationArchive -Jar $jar -Main $app.Main
        $context=Join-Path $stage $app.Owner; New-Item -ItemType Directory -Path $context | Out-Null
        Copy-Item docker/Dockerfile "$context/Dockerfile"; Copy-Item .dockerignore "$context/.dockerignore"
        Copy-Item $jar "$context/app.jar"; Copy-Item "$classes/HealthProbe.class" "$context/HealthProbe.class"
        $tag="aegisflow/$($app.Owner):local"
        & docker build --build-arg "APP_OWNER=$($app.Owner)" --build-arg "SOURCE_REVISION=$revision" --tag $tag $context
        if($LASTEXITCODE -ne 0) { throw "Image build failed for $($app.Owner); preserve $stage" }
        @{Owner=$app.Owner;Tag=$tag;Revision=$revision;Dirty=$dirty;JarSha256=(Get-FileHash $jar -Algorithm SHA256).Hash} |
            ConvertTo-Json | Set-Content "$stage/$($app.Owner)-build.json"
    }
    Write-Output 'Four images built. Startup/network/non-root checks remain a separate gate; no deployment claimed.'
} finally {
    Pop-Location
    foreach($key in $saved.Keys) { [Environment]::SetEnvironmentVariable($key,$saved[$key],'Process') }
}

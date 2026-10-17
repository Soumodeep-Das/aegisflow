#requires -Version 7.0
[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent $PSScriptRoot
Push-Location $repositoryRoot
try {
    $repositoryCache = Join-Path $repositoryRoot '.cache/maven-repository'
    & (Join-Path $repositoryRoot 'mvnw.cmd') -B -ntp "-Dmaven.repo.local=$repositoryCache" -f tools/kafka-smoke/pom.xml package
    if ($LASTEXITCODE -ne 0) { throw 'Standalone Kafka smoke client build failed.' }
} finally { Pop-Location }

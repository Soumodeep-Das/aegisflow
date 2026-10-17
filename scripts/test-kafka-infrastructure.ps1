#requires -Version 7.0
[CmdletBinding()]
param([string]$JavaHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot 'lib/kafka-infrastructure.ps1')
$script:passed = 0
function Check([string]$Label, [scriptblock]$Action) {
    & $Action
    $script:passed++
    Write-Output "PASS: $Label"
}
function Reject([scriptblock]$Action) {
    $rejected = $false
    try { & $Action } catch { $rejected = $true }
    if (-not $rejected) { throw 'Unsafe input was accepted.' }
}
$project = 'aegisflow-kafka-verify-' + [Guid]::NewGuid().ToString('N')
Check 'verification cannot address the ordinary Compose project' {
    Assert-KafkaVerificationProject $project
    Reject { Get-KafkaComposeArguments -RepositoryRoot $repositoryRoot -Project 'aegisflow-foundation' }
}
Check 'verification selects only Kafka companion and nonprivate environment file' {
    $arguments = @(Get-KafkaComposeArguments -RepositoryRoot $repositoryRoot -Project $project)
    if ($arguments -contains (Join-Path $repositoryRoot 'compose.yaml') -or
        $arguments -notcontains (Join-Path $repositoryRoot 'compose.kafka.yaml') -or
        $arguments -notcontains (Join-Path $repositoryRoot '.env.example')) { throw 'Unexpected Compose scope.' }
}
function ConfigurationFixture {
    $image = 'apache/kafka:4.2.2@sha256:1213eb3943d551e5ed1fca7a4e109001cee35770b66a02a0c37a8964efe09b69'
    [pscustomobject]@{
        name = $project
        services = [pscustomobject]@{
            kafka = [pscustomobject]@{ image = $image; networks = [pscustomobject]@{ 'kafka-events' = $null }
                ports = @([pscustomobject]@{ host_ip = '127.0.0.1'; published = '19092'; target = 19092 }) }
            'kafka-init' = [pscustomobject]@{ image = $image; networks = [pscustomobject]@{ 'kafka-events' = $null } }
        }
        volumes = [pscustomobject]@{ 'kafka-data' = [pscustomobject]@{ name = "${project}_kafka-data" } }
        networks = [pscustomobject]@{ 'kafka-events' = [pscustomobject]@{ name = "${project}_kafka-events" } }
    }
}
Check 'wildcard host publication is rejected' {
    $fixture = ConfigurationFixture
    Assert-KafkaVerificationConfiguration $fixture $project 19092
    $fixture.services.kafka.ports[0].host_ip = '0.0.0.0'
    Reject { Assert-KafkaVerificationConfiguration $fixture $project 19092 }
}
Check 'shared volume cannot be targeted' {
    $fixture = ConfigurationFixture
    $fixture.volumes.'kafka-data'.name = 'aegisflow-foundation_kafka-data'
    Reject { Assert-KafkaVerificationConfiguration $fixture $project 19092 }
}
Check 'foreign database network cannot be attached' {
    $fixture = ConfigurationFixture
    $fixture.services.kafka.networks = [pscustomobject]@{ 'core-data' = $null }
    Reject { Assert-KafkaVerificationConfiguration $fixture $project 19092 }
}
Check 'floating image tag cannot replace pinned broker' {
    $fixture = ConfigurationFixture
    $fixture.services.kafka.image = 'apache/kafka:latest'
    Reject { Assert-KafkaVerificationConfiguration $fixture $project 19092 }
}
Check 'client environment excludes DB credentials, broker identity and inherited JVM overrides' {
    $filtered = New-KafkaSmokeEnvironment -Source @{ PATH = 'synthetic-path'; CORE_DB_PASSWORD = 'synthetic';
        PGPASSWORD = 'synthetic'; KAFKA_BROKER_PASSWORD = 'synthetic'; JAVA_TOOL_OPTIONS = 'synthetic';
        SPRING_APPLICATION_JSON = 'synthetic' } -Settings @{ KAFKA_CORE_PASSWORD = 'synthetic-core' }
    if (($filtered.Keys | Sort-Object) -join ',' -cne 'KAFKA_CORE_PASSWORD,PATH') { throw 'Unexpected inherited environment.' }
    Reject { New-KafkaSmokeEnvironment -Source @{} -Settings @{ KAFKA_BROKER_PASSWORD = 'synthetic' } }
}
foreach ($state in @('absent', 'empty', 'existing')) {
    Check "cleanup restores $state environment state" {
        $before = [Environment]::GetEnvironmentVariable('KAFKA_HOST_PORT', 'Process')
        try {
            $expected = switch ($state) { 'absent' { $null }; 'empty' { '' }; 'existing' { '19093' } }
            $env:KAFKA_HOST_PORT = '12345'
            Restore-KafkaVerificationEnvironment @{ KAFKA_HOST_PORT = $expected }
            $actual = [Environment]::GetEnvironmentVariable('KAFKA_HOST_PORT', 'Process')
            if ($actual -cne $expected) { throw 'Environment was not restored exactly.' }
        } finally { Restore-KafkaVerificationEnvironment @{ KAFKA_HOST_PORT = $before } }
    }
}
Write-Output "Kafka infrastructure guard checks: $script:passed passed; no Docker engine exercised."
if (-not $JavaHome) { throw 'Set JAVA_HOME to Java 21; build the standalone smoke client first.' }
$java = Join-Path $JavaHome 'bin/java.exe'
$classpath = (Join-Path $repositoryRoot 'tools/kafka-smoke/target/classes') + [IO.Path]::PathSeparator +
    (Join-Path $repositoryRoot 'tools/kafka-smoke/target/lib/*')
& $java -cp $classpath com.aegisflow.tools.kafka.KafkaSmoke --self-test
if ($LASTEXITCODE -ne 0) { throw 'Kafka smoke guard checks failed.' }

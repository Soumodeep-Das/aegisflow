#requires -Version 7.0
[CmdletBinding()]
param(
    [string]$JavaHome = $env:JAVA_HOME,
    [ValidateRange(60, 600)][int]$TimeoutSeconds = 240,
    [switch]$StaticOnly
)
$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot 'lib/kafka-infrastructure.ps1')
$project = 'aegisflow-kafka-verify-' + [Guid]::NewGuid().ToString('N')
$compose = @(Get-KafkaComposeArguments -RepositoryRoot $repositoryRoot -Project $project)
$logDirectory = Join-Path $repositoryRoot ".cache/kafka/$project"
$docker = (Get-Command docker -ErrorAction Stop).Source
$saved = @{}
$passwords = [Collections.Generic.List[string]]::new()
$resourcesAttempted = $false
$smokeName = "$project-client"
$result = [ordered]@{
    Project = $project; StartedUtc = [DateTimeOffset]::UtcNow.ToString('o'); FinishedUtc = $null
    StaticOnly = [bool]$StaticOnly; Passed = $false; Stage = 'configuration'; Checks = @()
    Image = 'apache/kafka:4.2.2@sha256:1213eb3943d551e5ed1fca7a4e109001cee35770b66a02a0c37a8964efe09b69'
    KafkaClient = '4.2.1 (Spring Boot 4.1.1 managed)'; VolumesRetained = $true
}

function Save-KafkaLog {
    param([string]$Name, [string]$Content)
    foreach ($password in $passwords) { $Content = $Content.Replace($password, '[REDACTED]') }
    Set-Content -LiteralPath (Join-Path $logDirectory $Name) -Value $Content -Encoding utf8
}

function Invoke-KafkaProcess {
    param([string]$Executable = $docker, [string[]]$Arguments, [string]$LogName,
        [System.Collections.IDictionary]$Environment, [switch]$AllowNonzero)
    $start = [Diagnostics.ProcessStartInfo]::new($Executable)
    $start.WorkingDirectory = $repositoryRoot
    $start.UseShellExecute = $false
    $start.CreateNoWindow = $true
    $start.WindowStyle = [Diagnostics.ProcessWindowStyle]::Hidden
    $start.RedirectStandardOutput = $true
    $start.RedirectStandardError = $true
    foreach ($argument in $Arguments) { $start.ArgumentList.Add($argument) }
    if ($null -ne $Environment) {
        $start.Environment.Clear()
        foreach ($key in $Environment.Keys) { $start.Environment[[string]$key] = [string]$Environment[$key] }
    }
    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $start
    try {
        if (-not $process.Start()) { throw 'Could not start verification process.' }
        $stdout = $process.StandardOutput.ReadToEndAsync()
        $stderr = $process.StandardError.ReadToEndAsync()
        if (-not $process.WaitForExit($TimeoutSeconds * 1000)) {
            $process.Kill($true)
            $process.WaitForExit()
            if ($LogName) { Save-KafkaLog $LogName ($stdout.GetAwaiter().GetResult() + $stderr.GetAwaiter().GetResult()) }
            throw 'Verification process exceeded its deadline.'
        }
        $output = $stdout.GetAwaiter().GetResult()
        $errors = $stderr.GetAwaiter().GetResult()
        if ($LogName) { Save-KafkaLog $LogName ($output + $errors) }
        if (-not $AllowNonzero -and $process.ExitCode -ne 0) {
            throw "Verification process exited $($process.ExitCode); inspect its sanitized log."
        }
        [pscustomobject]@{ ExitCode = $process.ExitCode; Output = $output.Trim(); ErrorOutput = $errors.Trim() }
    } finally { $process.Dispose() }
}

function Stop-KafkaSmokeContainer {
    # A timed-out attached Docker client can leave its container running.
    $inspection = Invoke-KafkaProcess -Arguments @('inspect', '--format', '{{.Id}} {{ index .Config.Labels "com.aegisflow.kafka.verification" }}', $smokeName) -AllowNonzero
    if ($inspection.ExitCode -eq 0) {
        $parts = $inspection.Output.Split(' ')
        if ($parts.Count -ne 2 -or $parts[0] -cnotmatch '^[0-9a-f]{64}$' -or $parts[1] -cne $project) {
            throw 'Refusing to clean up a container without the exact verification identity.'
        }
        $null = Invoke-KafkaProcess -Arguments @('stop', '--time', '10', $parts[0]) -AllowNonzero
        $null = Invoke-KafkaProcess -Arguments @('rm', $parts[0]) -AllowNonzero
    }
}

New-Item -ItemType Directory -Force -Path $logDirectory | Out-Null
try {
    foreach ($key in @('ADMIN_PASSWORD','BROKER_PASSWORD','ORDER_PASSWORD','PAYMENT_PASSWORD','WAREHOUSE_PASSWORD','CORE_PASSWORD','HOST_PORT','CLUSTER_ID')) {
        $name = 'KAFKA_' + $key
        $saved[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
    }
    foreach ($principal in @('ADMIN','BROKER','ORDER','PAYMENT','WAREHOUSE','CORE')) {
        $password = [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(32)).ToLowerInvariant()
        $passwords.Add($password)
        [Environment]::SetEnvironmentVariable("KAFKA_${principal}_PASSWORD", $password, 'Process')
    }
    $env:KAFKA_HOST_PORT = '19092'
    $env:KAFKA_CLUSTER_ID = 'MkU3OEVBNTcwNTJENDM2Qk'
    $configResult = Invoke-KafkaProcess -Arguments ($compose + @('config', '--format', 'json'))
    $config = $configResult.Output | ConvertFrom-Json
    Assert-KafkaVerificationConfiguration -Config $config -Project $project -HostPort 19092
    $result.Checks += 'Kafka-only Compose configuration: pinned image, isolated volume/network and one loopback listener'
    Write-Output 'Kafka-only Compose configuration: PASS; PostgreSQL configuration/volumes are not targeted.'
    if ($StaticOnly) {
        $result.Stage = 'configuration-verified'
        Write-Output 'STATIC ONLY: broker startup, topic provisioning, authentication and producer/consumer assertions NOT executed.'
        return
    }

    $result.Stage = 'docker-engine'
    $engine = Invoke-KafkaProcess -Arguments @('info', '--format', '{{.ServerVersion}}') -LogName 'docker-engine.log'
    $result.DockerVersion = $engine.Output
    $result.Stage = 'java-and-client-build'
    if (-not $JavaHome) { throw 'Set JAVA_HOME to Java 21.' }
    $java = Join-Path $JavaHome 'bin/java.exe'
    $version = Invoke-KafkaProcess -Executable $java -Arguments @('-version')
    if (($version.Output + $version.ErrorOutput) -notmatch 'version "21\.') { throw 'Java 21 is required.' }
    # This standalone verification project does not rebuild the application reactor.
    $buildEnvironment = New-KafkaSmokeEnvironment -Source ([Environment]::GetEnvironmentVariables('Process')) -Settings @{}
    $buildEnvironment['JAVA_HOME'] = $JavaHome
    $buildScript = Join-Path $PSScriptRoot 'build-kafka-smoke.ps1'
    $pwsh = (Get-Process -Id $PID).Path
    $null = Invoke-KafkaProcess -Executable $pwsh -Arguments @('-NoProfile', '-File', $buildScript) -Environment $buildEnvironment -LogName 'client-build.log'
    $target = Join-Path $repositoryRoot 'tools/kafka-smoke/target'
    if ($target.Contains(',')) { throw 'Verification bind mount path must not contain commas.' }

    $result.Stage = 'broker-startup'
    # Release an ephemeral loopback port immediately before Compose binds it.
    # A rare concurrent bind conflict fails normally; no existing listener is stopped.
    $reservation = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
    try { $reservation.Start(); $port = $reservation.LocalEndpoint.Port } finally { $reservation.Stop() }
    $env:KAFKA_HOST_PORT = [string]$port
    $configResult = Invoke-KafkaProcess -Arguments ($compose + @('config', '--format', 'json'))
    Assert-KafkaVerificationConfiguration -Config ($configResult.Output | ConvertFrom-Json) -Project $project -HostPort $port
    Write-Output "Disposable Kafka project: $project; host listener 127.0.0.1:$port"
    $resourcesAttempted = $true
    $null = Invoke-KafkaProcess -Arguments ($compose + @('up', '-d', '--wait', '--wait-timeout', '120', 'kafka')) -LogName 'broker-startup.log'
    $result.Checks += 'Broker authenticated health check passed'

    $result.Stage = 'topic-and-acl-provisioning'
    foreach ($pass in 1..2) {
        $null = Invoke-KafkaProcess -Arguments ($compose + @('run', '--rm', '--no-deps', 'kafka-init')) -LogName "provision-$pass.log"
    }
    $result.Checks += 'Topic/ACL provisioning executed successfully twice'
    $settings = @{}
    foreach ($principal in @('ADMIN','ORDER','PAYMENT','WAREHOUSE','CORE')) {
        $settings["KAFKA_${principal}_PASSWORD"] = [Environment]::GetEnvironmentVariable("KAFKA_${principal}_PASSWORD", 'Process')
    }
    $settings['KAFKA_SMOKE_RUN_ID'] = [Guid]::NewGuid().ToString('N')
    $settings['KAFKA_BOOTSTRAP_SERVERS'] = "127.0.0.1:$port"
    $settings['KAFKA_SMOKE_EXPECTED_HOST'] = '127.0.0.1'
    $settings['KAFKA_SMOKE_EXPECTED_PORT'] = [string]$port
    $result.Stage = 'host-client-smoke'
    $hostEnvironment = New-KafkaSmokeEnvironment -Source ([Environment]::GetEnvironmentVariables('Process')) -Settings $settings
    $classpath = (Join-Path $target 'classes') + [IO.Path]::PathSeparator + (Join-Path $target 'lib/*')
    $hostSmoke = Invoke-KafkaProcess -Executable $java -Arguments @('-Duser.timezone=UTC', '-cp', $classpath, 'com.aegisflow.tools.kafka.KafkaSmoke') -Environment $hostEnvironment -LogName 'host-smoke.log'
    Write-Output $hostSmoke.Output
    $result.Checks += 'Host listener: metadata, topics/configuration, three producer/Core-consumer round trips, committed offsets and negative auth/ACL checks passed'

    $result.Stage = 'container-client-smoke'
    $settings['KAFKA_SMOKE_RUN_ID'] = [Guid]::NewGuid().ToString('N')
    $settings['KAFKA_BOOTSTRAP_SERVERS'] = 'kafka:9092'
    $settings['KAFKA_SMOKE_EXPECTED_HOST'] = 'kafka'
    $settings['KAFKA_SMOKE_EXPECTED_PORT'] = '9092'
    $containerEnvironment = New-KafkaSmokeEnvironment -Source ([Environment]::GetEnvironmentVariables('Process')) -Settings $settings
    $runArguments = @('run', '--rm', '--name', $smokeName, '--label', "com.aegisflow.kafka.verification=$project",
        '--network', "${project}_kafka-events", '--read-only', '--cap-drop', 'ALL', '--security-opt', 'no-new-privileges',
        '--tmpfs', '/tmp:rw,nosuid,nodev,size=64m', '--mount', "type=bind,source=$target,target=/opt/aegisflow/smoke,readonly", '--entrypoint', 'java')
    foreach ($key in $settings.Keys) { $runArguments += @('--env', [string]$key) }
    $runArguments += @($result.Image, '-Duser.timezone=UTC', '-cp', '/opt/aegisflow/smoke/classes:/opt/aegisflow/smoke/lib/*', 'com.aegisflow.tools.kafka.KafkaSmoke')
    $containerSmoke = Invoke-KafkaProcess -Arguments $runArguments -Environment $containerEnvironment -LogName 'container-smoke.log'
    Write-Output $containerSmoke.Output
    $result.Checks += 'Container listener: metadata, topics/configuration, three producer/Core-consumer round trips, committed offsets and negative auth/ACL checks passed'
    $result.Stage = 'complete'
    $result.Passed = $true
    Write-Output 'PASS: Kafka startup, repeated provisioning, host/container communication and explicit authentication/ACL rejection checks.'
} catch {
    $result.FailureStage = $result.Stage
    throw "Kafka verification failed at '$($result.Stage)'; inspect sanitized logs under $logDirectory."
} finally {
    if ($resourcesAttempted) {
        try {
            $logs = Invoke-KafkaProcess -Arguments ($compose + @('logs', '--no-color', 'kafka')) -AllowNonzero
            Save-KafkaLog 'broker.log' ($logs.Output + $logs.ErrorOutput)
            Stop-KafkaSmokeContainer
            Assert-KafkaVerificationProject $project
            $null = Invoke-KafkaProcess -Arguments ($compose + @('down', '--timeout', '15')) -LogName 'cleanup.log'
            Write-Output "Stopped only verification containers; retained Kafka volume ${project}_kafka-data."
        } catch {
            $result.Passed = $false
            $result.CleanupFailed = $true
            Write-Warning 'Verification resource cleanup did not complete; retained logs identify the project. No volumes were deleted.'
        }
    }
    $result.FinishedUtc = [DateTimeOffset]::UtcNow.ToString('o')
    $result | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $logDirectory 'results.json') -Encoding utf8
    Restore-KafkaVerificationEnvironment -Saved $saved
    Write-Output "Kafka result: $logDirectory\results.json"
    if ($result.CleanupFailed) { throw 'Kafka verification cleanup failed; the run is not accepted as passed.' }
}

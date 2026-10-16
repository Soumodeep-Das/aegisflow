#requires -Version 7.0
[CmdletBinding()]
param(
    [string]$JavaHome = $env:JAVA_HOME,
    [ValidateRange(15, 180)][int]$TimeoutSeconds = 120,
    [switch]$StaticOnly
)
$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot 'lib/compose-persistence.ps1')
$projectName = New-RehearsalProjectName
$composeArguments = @(Get-RehearsalComposeArguments -RepositoryRoot $repositoryRoot -ProjectName $projectName)
$owners = @('core', 'order', 'payment', 'warehouse')
$jars = @{
    core = 'control-plane/target/aegisflow-core-0.1.0-SNAPSHOT.jar'
    order = 'simulators/order-service/target/order-service-0.1.0-SNAPSHOT.jar'
    payment = 'simulators/payment-service/target/payment-service-0.1.0-SNAPSHOT.jar'
    warehouse = 'simulators/warehouse-service/target/warehouse-service-0.1.0-SNAPSHOT.jar'
}
$savedEnvironment = @{}
$passwords = [Collections.Generic.List[string]]::new()
$ownedProcesses = [Collections.Generic.List[Diagnostics.Process]]::new()
$resourcesAttempted = $false
$logDirectory = Join-Path $repositoryRoot ".cache/compose-persistence/$projectName"
$snapshots = @{}
$jarHashes = @{}
$result = [ordered]@{ Project = $projectName; StaticOnly = [bool]$StaticOnly; Passed = $false; Checks = @(); VolumesRetained = $true }
$dockerExecutable = (Get-Command docker -ErrorAction Stop).Source

function Invoke-RehearsalDocker {
    param([Parameter(Mandatory)][string[]]$Arguments, [string]$InputText)
    $start = [Diagnostics.ProcessStartInfo]::new($dockerExecutable)
    $start.WorkingDirectory = $repositoryRoot
    $start.UseShellExecute = $false
    $start.CreateNoWindow = $true
    $start.WindowStyle = [Diagnostics.ProcessWindowStyle]::Hidden
    $start.RedirectStandardOutput = $true
    $start.RedirectStandardError = $true
    $start.RedirectStandardInput = $true
    foreach ($argument in $Arguments) { $start.ArgumentList.Add($argument) }
    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $start
    try {
        if (-not $process.Start()) { throw 'Could not start Docker CLI.' }
        $outputTask = $process.StandardOutput.ReadToEndAsync()
        $errorTask = $process.StandardError.ReadToEndAsync()
        if ($InputText) { $process.StandardInput.Write($InputText) }
        $process.StandardInput.Close()
        if (-not $process.WaitForExit(($TimeoutSeconds + 15) * 1000)) {
            $process.Kill()
            throw 'Docker command exceeded the bounded rehearsal timeout.'
        }
        $output = $outputTask.GetAwaiter().GetResult()
        $null = $errorTask.GetAwaiter().GetResult()
        # No expanded Compose config, credentials, or raw Docker errors are printed.
        if ($process.ExitCode -ne 0) {
            throw "Docker operation failed (exit $($process.ExitCode)); check engine access/configuration without printing expanded secrets."
        }
        $output.Trim()
    } finally { $process.Dispose() }
}

function Invoke-RehearsalSql {
    param([string]$Owner, [ValidateSet('setup', 'runtime', 'roles', 'snapshot')][string]$Mode)
    $flags = @('setup_fixture', 'runtime_checks', 'role_checks', 'snapshot')
    $activeFlag = @{ setup = 'setup_fixture'; runtime = 'runtime_checks'; roles = 'role_checks'; snapshot = 'snapshot' }[$Mode]
    $shell = if ($Mode -eq 'runtime') {
        'export PGPASSWORD="$RUNTIME_DB_PASSWORD"; exec psql -X -w -qAt -h 127.0.0.1 -U "$RUNTIME_DB_USER" -d "$POSTGRES_DB" "$@"'
    } else {
        # Test fixture schema setup and catalog/history inspection use the harness
        # administrator. The migrator correctly cannot CREATE a database schema.
        'export PGPASSWORD="$POSTGRES_PASSWORD"; exec psql -X -w -qAt -h 127.0.0.1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" "$@"'
    }
    $arguments = $composeArguments + @('exec', '-T', '-e', "REHEARSAL_MARKER=$projectName", "${Owner}-db", 'sh', '-c', $shell, 'rehearsal')
    foreach ($flag in $flags) { $arguments += @('-v', "$flag=$(if ($flag -eq $activeFlag) { 1 } else { 0 })") }
    $arguments += @('-f', '/opt/aegisflow/persistence-rehearsal.sql')
    Invoke-RehearsalDocker -Arguments $arguments
}

function Start-RehearsalApplication {
    param([string]$Owner, [int]$DatabasePort, [int]$Pass)
    $prefix = $Owner.ToUpperInvariant() + '_DB_'
    $connection = @{}
    $connection[$prefix + 'URL'] = "jdbc:postgresql://127.0.0.1:$DatabasePort/aegisflow_$Owner"
    foreach ($suffix in @('RUNTIME_USER','RUNTIME_PASSWORD','MIGRATION_USER','MIGRATION_PASSWORD')) {
        $connection[$prefix + $suffix] = [Environment]::GetEnvironmentVariable($prefix + $suffix, 'Process')
    }
    $childEnvironment = New-RehearsalChildEnvironment -SourceEnvironment ([Environment]::GetEnvironmentVariables('Process')) -Owner $Owner -ConnectionEnvironment $connection
    $start = [Diagnostics.ProcessStartInfo]::new($javaExecutable)
    $start.WorkingDirectory = $repositoryRoot
    $start.UseShellExecute = $false
    $start.CreateNoWindow = $true
    $start.WindowStyle = [Diagnostics.ProcessWindowStyle]::Hidden
    $start.RedirectStandardOutput = $true
    $start.RedirectStandardError = $true
    $start.Environment.Clear()
    foreach ($key in $childEnvironment.Keys) { $start.Environment[$key] = $childEnvironment[$key] }
    foreach ($argument in @('-Duser.timezone=UTC', '-jar', (Join-Path $repositoryRoot $jars[$Owner]),
            '--spring.profiles.active=postgres', '--server.address=127.0.0.1', '--server.port=0')) { $start.ArgumentList.Add($argument) }
    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $start
    $lines = [Collections.Generic.List[string]]::new()
    $lineTask = $null
    $errorTask = $null
    try {
        if (-not $process.Start()) { throw "Could not start $Owner JAR." }
        $ownedProcesses.Add($process)
        $errorTask = $process.StandardError.ReadToEndAsync()
        $lineTask = $process.StandardOutput.ReadLineAsync()
        $deadline = [DateTimeOffset]::UtcNow.AddSeconds($TimeoutSeconds)
        $port = 0
        while ([DateTimeOffset]::UtcNow -lt $deadline) {
            while ($lineTask.IsCompleted) {
                $line = $lineTask.GetAwaiter().GetResult()
                if ($null -eq $line) { break }
                $lines.Add($line)
                if ($line -match 'Tomcat started on port (\d+)') { $port = [int]$Matches[1] }
                $lineTask = $process.StandardOutput.ReadLineAsync()
            }
            if ($process.HasExited) { throw "$Owner postgres JAR exited before readiness; inspect its redacted rehearsal logs." }
            if ($port) {
                $response = Invoke-WebRequest -Uri "http://127.0.0.1:$port/actuator/health" -TimeoutSec 5
                $content = if ($response.Content -is [byte[]]) { [Text.Encoding]::UTF8.GetString($response.Content) } else { [string]$response.Content }
                $health = $content | ConvertFrom-Json
                if ($response.StatusCode -eq 200 -and $health.status -eq 'UP') {
                    if ((Get-FileHash -LiteralPath (Join-Path $repositoryRoot $jars[$Owner]) -Algorithm SHA256).Hash -ne $jarHashes[$Owner]) {
                        throw 'JAR changed during the persistence rehearsal.'
                    }
                    $result.Checks += "$Owner postgres JAR pass $Pass healthy; Flyway/Hibernate validation completed"
                    Write-Output "$Owner postgres JAR pass ${Pass}: HTTP 200/UP; own database credentials only"
                    return
                }
            }
            Start-Sleep -Milliseconds 200
        }
        throw "$Owner postgres JAR did not become healthy within $TimeoutSeconds seconds."
    } finally {
        if ($process.Id -and -not $process.HasExited) { $process.Kill(); $null = $process.WaitForExit(5000) }
        if ($lineTask) {
            $remainingLine = $lineTask.GetAwaiter().GetResult()
            if ($null -ne $remainingLine) { $lines.Add($remainingLine) }
            $lines.Add($process.StandardOutput.ReadToEnd())
        }
        $log = ($lines -join [Environment]::NewLine) + $(if ($errorTask) { $errorTask.GetAwaiter().GetResult() })
        foreach ($password in $passwords) { $log = $log.Replace($password, '[REDACTED]') }
        Set-Content -LiteralPath (Join-Path $logDirectory "$Owner-pass-$Pass.log") -Value $log -Encoding utf8
    }
}

Push-Location $repositoryRoot
try {
    New-Item -ItemType Directory -Force -Path $logDirectory | Out-Null
    # No .env is read or overwritten. Passwords exist only in this process and its
    # explicitly isolated Docker project, never in an argv, evidence file or console.
    foreach ($owner in $owners) {
        $prefix = $owner.ToUpperInvariant() + '_DB_'
        foreach ($suffix in @('USER','PASSWORD','MIGRATION_USER','MIGRATION_PASSWORD','RUNTIME_USER','RUNTIME_PASSWORD')) {
            $name = $prefix + $suffix
            $savedEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
            $value = if ($suffix.EndsWith('PASSWORD')) {
                $secret = [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
                $passwords.Add($secret)
                $secret
            } else {
                'p03_' + $owner + '_' + @{ USER = 'admin'; MIGRATION_USER = 'migrator'; RUNTIME_USER = 'runtime' }[$suffix]
            }
            [Environment]::SetEnvironmentVariable($name, $value, 'Process')
        }
    }
    if (@($passwords | Select-Object -Unique).Count -ne 12) { throw 'Password generation did not produce twelve distinct credentials.' }
    $configuration = (Invoke-RehearsalDocker -Arguments ($composeArguments + @('config', '--format', 'json'))) | ConvertFrom-Json
    if ($configuration.name -cne $projectName -or @($configuration.services.PSObject.Properties).Count -ne 4) { throw 'Unexpected Compose project/services.' }
    foreach ($owner in $owners) {
        $service = $configuration.services."${owner}-db"
        if (@($service.ports).Count -ne 1 -or $service.ports[0].host_ip -cne '127.0.0.1' -or
                $service.ports[0].target -ne 5432 -or $service.ports[0].published) {
            throw 'The rehearsal requires a single ephemeral loopback-only PostgreSQL port per owner.'
        }
        $volume = $configuration.volumes."${owner}-db-data"
        if ($volume.external -or $volume.name -cne "${projectName}_${owner}-db-data") { throw 'Refusing a shared/external/non-project volume.' }
        $network = $configuration.networks."${owner}-data"
        if ($network.external -or $network.name -cne "${projectName}_${owner}-data" -or -not $network.internal) { throw 'Refusing a shared or non-internal data network.' }
    }
    $configuration = $null
    $result.Checks += 'Compose override validates four isolated volumes/networks and ephemeral loopback ports'
    Write-Output 'Compose persistence rehearsal configuration: PASS (no private .env, expanded config or credentials printed)'
    if ($StaticOnly) {
        $result.Passed = $true
        Write-Output 'STATIC ONLY: Docker engine, bootstrap, migration and retained-volume assertions were NOT executed.'
        return
    }
    $null = Invoke-RehearsalDocker -Arguments @('info', '--format', '{{.ServerVersion}}')
    if (-not $JavaHome) { throw 'Set JAVA_HOME to Java 21; the rehearsal never builds or downloads dependencies.' }
    $javaExecutable = Join-Path $JavaHome 'bin/java.exe'
    if (-not (Test-Path -LiteralPath $javaExecutable)) { throw 'Java executable not found.' }
    $javaVersion = (& $javaExecutable -version 2>&1 | Out-String)
    if ($javaVersion -notmatch 'version "21\.') { throw 'The rehearsal requires Java 21.' }
    foreach ($owner in $owners) {
        $jar = Join-Path $repositoryRoot $jars[$owner]
        if (-not (Test-Path -LiteralPath $jar)) { throw "Missing existing built JAR: $jar. Build explicitly before this rehearsal." }
        $jarHashes[$owner] = (Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash
    }
    if (Invoke-RehearsalDocker -Arguments @('ps', '-aq', '--filter', "label=com.docker.compose.project=$projectName")) { throw 'Project collision: refusing to touch existing containers.' }
    if (Invoke-RehearsalDocker -Arguments @('volume', 'ls', '-q', '--filter', "label=com.docker.compose.project=$projectName")) { throw 'Project collision: refusing to touch existing volumes.' }
    Write-Output "Disposable project: $projectName"
    for ($pass = 1; $pass -le 2; $pass++) {
        $resourcesAttempted = $true
        $null = Invoke-RehearsalDocker -Arguments ($composeArguments + @('up', '-d', '--wait', '--wait-timeout', [string]$TimeoutSeconds))
        # The production bootstrap helper/SQL is intentionally reused, not copied.
        # Capture bootstrap output too: a psql failure must not accidentally echo
        # an expanded ALTER ROLE password statement into the host transcript.
        try {
            $bootstrapOutput = & (Join-Path $PSScriptRoot 'bootstrap-databases.ps1') -ComposeFile @((Join-Path $repositoryRoot 'compose.yaml'), (Join-Path $repositoryRoot 'compose.persistence-test.yaml')) -ProjectName $projectName -EnvFile (Join-Path $repositoryRoot '.env.example') 2>&1
        } catch {
            throw 'Role bootstrap failed in the isolated project; raw SQL/error output suppressed to protect credentials.'
        } finally { $bootstrapOutput = $null }
        Write-Output "Role bootstrap pass ${pass}: all four databases provisioned by the existing helper"
        foreach ($owner in $owners) {
            $publishedPort = Get-RehearsalPublishedPort -Output (Invoke-RehearsalDocker -Arguments ($composeArguments + @('port', "${owner}-db", '5432')))
            Start-RehearsalApplication -Owner $owner -DatabasePort $publishedPort -Pass $pass
            if ($pass -eq 1) { $null = Invoke-RehearsalSql -Owner $owner -Mode setup }
            $null = Invoke-RehearsalSql -Owner $owner -Mode runtime
            $null = Invoke-RehearsalSql -Owner $owner -Mode roles
            $snapshot = Invoke-RehearsalSql -Owner $owner -Mode snapshot
            if ($pass -eq 1) { $snapshots[$owner] = $snapshot }
            elseif ($snapshot -cne $snapshots[$owner]) { throw "$owner sentinel or Flyway history changed across retained-volume restart/repeated bootstrap/migration." }
            foreach ($foreignOwner in $owners | Where-Object { $_ -ne $owner }) {
                $foreignPrefix = $foreignOwner.ToUpperInvariant() + '_DB_RUNTIME_'
                $foreignInput = [Environment]::GetEnvironmentVariable($foreignPrefix + 'USER', 'Process') + "`n" + [Environment]::GetEnvironmentVariable($foreignPrefix + 'PASSWORD', 'Process') + "`n"
                # Foreign credentials travel only on stdin, never command arguments.
                $shell = 'IFS= read -r foreign_user; IFS= read -r PGPASSWORD; export PGPASSWORD PGCONNECT_TIMEOUT=5; output=$(psql -X -w -h 127.0.0.1 -U "$foreign_user" -d "$POSTGRES_DB" -c "SELECT 1" 2>&1); code=$?; [ "$code" -ne 0 ] || exit 19; case "$output" in *"password authentication failed"*|*"does not exist"*) exit 0;; *) exit 20;; esac'
                $null = Invoke-RehearsalDocker -Arguments ($composeArguments + @('exec', '-T', "${owner}-db", 'sh', '-c', $shell)) -InputText $foreignInput
                $foreignInput = $null
            }
            $result.Checks += "$owner pass $pass SQL runtime DML/no DDL/history writes; separate restricted roles; three foreign logins denied"
            Write-Output "$owner pass ${pass}: runtime/role/foreign-credential assertions passed"
        }
        $null = Invoke-RehearsalDocker -Arguments ($composeArguments + @('down', '--timeout', '15'))
        if ($pass -eq 1) { Write-Output 'First project stopped WITHOUT deleting volumes; restarting the same retained storage.' }
    }
    $result.Checks += 'Four immutable sentinel/history snapshots identical after retained-volume restart and repeated bootstrap/Flyway'
    $result.Passed = $true
    Write-Output 'PASS: all four postgres JARs, real role boundaries, and retained-volume restart/repeated migration checks.'
} finally {
    try {
        foreach ($process in $ownedProcesses) {
            try {
                if (-not $process.HasExited) { $process.Kill(); $null = $process.WaitForExit(5000) }
            } catch { Write-Warning 'Could not stop an owned Java child; no unrelated process was targeted.' }
            finally { $process.Dispose() }
        }
        if ($resourcesAttempted) {
            try { $null = Invoke-RehearsalDocker -Arguments ($composeArguments + @('down', '--timeout', '15')) }
            catch { Write-Warning "Could not stop the owned project $projectName; no volumes were deleted. Inspect this named project manually." }
        }
    } finally {
        # Even a failed child/container cleanup cannot leave generated credentials
        # installed in the caller's PowerShell process.
        Restore-RehearsalEnvironment -SavedEnvironment $savedEnvironment
        try {
            if (Test-Path -LiteralPath $logDirectory) {
                $result.JarSha256 = $jarHashes
                $result | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $logDirectory 'results.json') -Encoding utf8
            }
            if ($resourcesAttempted) {
                Write-Output "Retained disposable volumes for project: $projectName"
                Write-Output 'Optional cleanup ONLY after you approve deleting this disposable evidence:'
                $volumeNames = $owners | ForEach-Object { "'${projectName}_${_}-db-data'" }
                Write-Output ('docker volume rm -- ' + ($volumeNames -join ' '))
            }
        } finally { Pop-Location }
    }
}

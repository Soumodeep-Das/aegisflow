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
$ownedApplications = [Collections.Generic.List[object]]::new()
$resourcesAttempted = $false
$logDirectory = Join-Path $repositoryRoot ".cache/compose-persistence/$projectName"
$snapshots = @{}
$jarHashes = @{}
$result = [ordered]@{ Project = $projectName; StaticOnly = [bool]$StaticOnly; Passed = $false; Stage = 'configuration'; Checks = @(); VolumesRetained = $true }
$dockerExecutable = (Get-Command docker -ErrorAction Stop).Source

function Invoke-RehearsalDocker {
    param(
        [Parameter(Mandatory)][string[]]$Arguments,
        [string]$InputText,
        [System.Collections.IDictionary]$ChildEnvironment,
        [switch]$AllowNonzero,
        [switch]$IncludeStandardError
    )
    $start = [Diagnostics.ProcessStartInfo]::new($dockerExecutable)
    $start.WorkingDirectory = $repositoryRoot
    $start.UseShellExecute = $false
    $start.CreateNoWindow = $true
    $start.WindowStyle = [Diagnostics.ProcessWindowStyle]::Hidden
    $start.RedirectStandardOutput = $true
    $start.RedirectStandardError = $true
    $start.RedirectStandardInput = $true
    if ($null -ne $ChildEnvironment) {
        $start.Environment.Clear()
        foreach ($key in $ChildEnvironment.Keys) { $start.Environment[[string]$key] = [string]$ChildEnvironment[$key] }
    }
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
        $errorOutput = $errorTask.GetAwaiter().GetResult()
        if ($IncludeStandardError) { $output += $errorOutput }
        # No expanded Compose config, credentials, or raw Docker errors are printed.
        if ($AllowNonzero) { return [pscustomobject]@{ ExitCode = $process.ExitCode; Output = $output.Trim() } }
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

function Stop-RehearsalApplication {
    param([Parameter(Mandatory)]$Application)
    if ($Application.Removed) { return }
    $label = Invoke-RehearsalDocker -Arguments @('inspect', '--format', '{{ index .Config.Labels "com.aegisflow.rehearsal.project" }}', $Application.Id)
    Assert-RehearsalOwnedContainer -ContainerId $Application.Id -ProjectName $projectName -ObservedProjectLabel $label
    # Collect diagnostics before removal, including failed startup; never print raw logs.
    try {
        $log = Invoke-RehearsalDocker -Arguments @('logs', $Application.Id) -IncludeStandardError
        foreach ($password in $passwords) { $log = $log.Replace($password, '[REDACTED]') }
        Set-Content -LiteralPath (Join-Path $logDirectory "$($Application.Owner)-pass-$($Application.Pass).log") -Value $log -Encoding utf8
    } catch {
        Write-Warning 'Could not save redacted application logs; still stopping only the verified owned container.'
    }
    $null = Invoke-RehearsalDocker -Arguments @('stop', '--time', '15', $Application.Id)
    # Recheck ownership immediately before removing this one container; no project or volume pruning.
    $label = Invoke-RehearsalDocker -Arguments @('inspect', '--format', '{{ index .Config.Labels "com.aegisflow.rehearsal.project" }}', $Application.Id)
    Assert-RehearsalOwnedContainer -ContainerId $Application.Id -ProjectName $projectName -ObservedProjectLabel $label
    $null = Invoke-RehearsalDocker -Arguments @('rm', $Application.Id)
    $Application.Removed = $true
}

function Start-RehearsalApplication {
    param([string]$Owner, [int]$Pass)
    $prefix = $Owner.ToUpperInvariant() + '_DB_'
    $connection = @{}
    $connection[$prefix + 'URL'] = "jdbc:postgresql://${Owner}-db:5432/aegisflow_$Owner"
    foreach ($suffix in @('RUNTIME_USER','RUNTIME_PASSWORD','MIGRATION_USER','MIGRATION_PASSWORD')) {
        $connection[$prefix + $suffix] = [Environment]::GetEnvironmentVariable($prefix + $suffix, 'Process')
    }
    $childEnvironment = New-RehearsalChildEnvironment -SourceEnvironment ([Environment]::GetEnvironmentVariables('Process')) -Owner $Owner -ConnectionEnvironment $connection
    $createArguments = @(Get-RehearsalApplicationArguments -RepositoryRoot $repositoryRoot -ProjectName $projectName -Owner $Owner -JarPath (Join-Path $repositoryRoot $jars[$Owner]) -ProbeDirectory $probeDirectory)
    $containerId = Invoke-RehearsalDocker -Arguments $createArguments -ChildEnvironment $childEnvironment
    if ($containerId -cnotmatch '^[0-9a-f]{64}$') { throw 'Docker did not return one exact application container ID; refusing an ambiguous target.' }
    $application = [pscustomobject]@{ Id = $containerId; Owner = $Owner; Pass = $Pass; Removed = $false }
    $ownedApplications.Add($application)
    try {
        $label = Invoke-RehearsalDocker -Arguments @('inspect', '--format', '{{ index .Config.Labels "com.aegisflow.rehearsal.project" }}', $containerId)
        Assert-RehearsalOwnedContainer -ContainerId $containerId -ProjectName $projectName -ObservedProjectLabel $label
        $null = Invoke-RehearsalDocker -Arguments @('start', $containerId)
        $deadline = [DateTimeOffset]::UtcNow.AddSeconds($TimeoutSeconds)
        $lastProbe = 'probe/not-executed';
        while ([DateTimeOffset]::UtcNow -lt $deadline) {
            $running = Invoke-RehearsalDocker -Arguments @('inspect', '--format', '{{.State.Running}}', $containerId)
            if ($running -cne 'true') { throw "$Owner postgres JAR exited before readiness; inspect its redacted rehearsal logs." }
            $probe = Invoke-RehearsalDocker -Arguments @('exec', $containerId, 'java', '-cp', '/opt/aegisflow/probe', 'RehearsalHealthProbe') -AllowNonzero
            # Only accept the Java helper's fixed diagnostic vocabulary. Never
            # propagate raw Docker output, HTTP bodies or environment values.
            $lastProbe = if ($probe.Output -cmatch '\A(?:HTTP [1-5][0-9]{2}/(?:UP|not-ready|unexpected-health-body)|probe/(?:interrupted|connection-failed))\z') {
                $probe.Output
            } else { "probe/docker-exec-exit-$($probe.ExitCode)" }
            if ($probe.ExitCode -eq 0) {
                if ((Get-FileHash -LiteralPath (Join-Path $repositoryRoot $jars[$Owner]) -Algorithm SHA256).Hash -ne $jarHashes[$Owner]) {
                    throw 'JAR changed during the persistence rehearsal.'
                }
                $result.Checks += "$Owner postgres JAR pass $Pass healthy in isolated Java 21 container; Flyway/Hibernate validation completed"
                Write-Output "$Owner postgres JAR pass ${Pass}: HTTP 200/UP; own database credentials/network only"
                return
            }
            Start-Sleep -Milliseconds 500
        }
        throw "$Owner postgres JAR did not become healthy within $TimeoutSeconds seconds; last check: $lastProbe."
    } finally {
        Stop-RehearsalApplication -Application $application
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
    $result.Stage = 'engine-access'
    $null = Invoke-RehearsalDocker -Arguments @('info', '--format', '{{.ServerVersion}}')
    $result.Stage = 'artifact-and-probe-prerequisites'
    if (-not $JavaHome) { throw 'Set JAVA_HOME to Java 21; the rehearsal never builds or downloads dependencies.' }
    $javaExecutable = Join-Path $JavaHome 'bin/java.exe'
    if (-not (Test-Path -LiteralPath $javaExecutable)) { throw 'Java executable not found.' }
    $javaVersion = (& $javaExecutable -version 2>&1 | Out-String)
    if ($javaVersion -notmatch 'version "21\.') { throw 'The rehearsal requires Java 21.' }
    $javacExecutable = Join-Path $JavaHome 'bin/javac.exe'
    if (-not (Test-Path -LiteralPath $javacExecutable)) { throw 'Java 21 JDK compiler not found; required for the isolated readiness probe.' }
    $probeSource = Join-Path $PSScriptRoot 'java/RehearsalHealthProbe.java'
    if (-not (Test-Path -LiteralPath $probeSource)) { throw 'Rehearsal readiness probe source is missing.' }
    $probeDirectory = Join-Path $logDirectory 'probe'
    New-Item -ItemType Directory -Force -Path $probeDirectory | Out-Null
    $compileOutput = (& $javacExecutable --release 21 -d $probeDirectory $probeSource 2>&1 | Out-String)
    if ($LASTEXITCODE -ne 0) { throw 'Could not compile the isolated readiness probe; no containers were started.' }
    $compileOutput = $null
    foreach ($owner in $owners) {
        $jar = Join-Path $repositoryRoot $jars[$owner]
        if (-not (Test-Path -LiteralPath $jar)) { throw "Missing existing built JAR: $jar. Build explicitly before this rehearsal." }
        $jarHashes[$owner] = (Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash
    }
    $result.Stage = 'pinned-java-runtime'
    $result.RuntimeImage = Get-RehearsalJavaImage
    # Downloaded by the engine before isolated containers start; applications gain no external network.
    Write-Output 'Preparing the pinned official Java 21 test image; the first run may download it.'
    $null = Invoke-RehearsalDocker -Arguments @('pull', $result.RuntimeImage)
    if (Invoke-RehearsalDocker -Arguments @('ps', '-aq', '--filter', "label=com.docker.compose.project=$projectName")) { throw 'Project collision: refusing to touch existing containers.' }
    if (Invoke-RehearsalDocker -Arguments @('volume', 'ls', '-q', '--filter', "label=com.docker.compose.project=$projectName")) { throw 'Project collision: refusing to touch existing volumes.' }
    Write-Output "Disposable project: $projectName"
    for ($pass = 1; $pass -le 2; $pass++) {
        $result.Stage = "pass-$pass-database-startup"
        $resourcesAttempted = $true
        $null = Invoke-RehearsalDocker -Arguments ($composeArguments + @('up', '-d', '--wait', '--wait-timeout', [string]$TimeoutSeconds))
        # The production bootstrap helper/SQL is intentionally reused, not copied.
        # Capture bootstrap output too: a psql failure must not accidentally echo
        # an expanded ALTER ROLE password statement into the host transcript.
        $result.Stage = "pass-$pass-role-bootstrap"
        try {
            $bootstrapOutput = & (Join-Path $PSScriptRoot 'bootstrap-databases.ps1') -ComposeFile @((Join-Path $repositoryRoot 'compose.yaml'), (Join-Path $repositoryRoot 'compose.persistence-test.yaml')) -ProjectName $projectName -EnvFile (Join-Path $repositoryRoot '.env.example') 2>&1
        } catch {
            throw 'Role bootstrap failed in the isolated project; raw SQL/error output suppressed to protect credentials.'
        } finally { $bootstrapOutput = $null }
        $result.Checks += "Role bootstrap pass ${pass}: all four databases provisioned by the existing helper"
        Write-Output "Role bootstrap pass ${pass}: all four databases provisioned by the existing helper"
        foreach ($owner in $owners) {
            $result.Stage = "pass-$pass-$owner-application"
            # Internal-only Docker networks need no published database port: use
            # the same packaged JAR on its owner's network, not a less-isolated DB.
            Start-RehearsalApplication -Owner $owner -Pass $pass
            $result.Stage = "pass-$pass-$owner-sql-boundaries"
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
        $result.Stage = "pass-$pass-retain-storage"
        $null = Invoke-RehearsalDocker -Arguments ($composeArguments + @('down', '--timeout', '15'))
        if ($pass -eq 1) { Write-Output 'First project stopped WITHOUT deleting volumes; restarting the same retained storage.' }
    }
    $result.Checks += 'Four immutable sentinel/history snapshots identical after retained-volume restart and repeated bootstrap/Flyway'
    $result.Stage = 'complete'
    $result.Passed = $true
    Write-Output 'PASS: all four postgres JARs, real role boundaries, and retained-volume restart/repeated migration checks.'
} finally {
    try {
        foreach ($application in $ownedApplications) {
            try { Stop-RehearsalApplication -Application $application }
            catch { Write-Warning 'Could not stop/remove an exactly labeled rehearsal application; no unrelated container or volume was targeted.' }
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

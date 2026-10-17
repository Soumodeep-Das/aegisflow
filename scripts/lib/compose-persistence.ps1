# Pure guards shared by the host rehearsal and its infrastructure-free regression checks.
function New-RehearsalProjectName {
    'aegisflow-p03-verify-' + [Guid]::NewGuid().ToString('N')
}

function Restore-RehearsalEnvironment {
    param([Parameter(Mandatory)][System.Collections.IDictionary]$SavedEnvironment)
    foreach ($name in $SavedEnvironment.Keys) {
        if ($name -cnotmatch '^(CORE|ORDER|PAYMENT|WAREHOUSE)_DB_(USER|PASSWORD|MIGRATION_USER|MIGRATION_PASSWORD|RUNTIME_USER|RUNTIME_PASSWORD)$') {
            throw 'Refusing to restore an unrelated environment setting.'
        }
    }
    foreach ($name in $SavedEnvironment.Keys) {
        # Newer .NET preserves empty environment strings. PowerShell's binding
        # can coerce a null string argument to empty: remove an originally absent
        # variable explicitly, rather than leave a blank credential override.
        if ($null -eq $SavedEnvironment[$name]) {
            Remove-Item -LiteralPath "Env:$name" -ErrorAction SilentlyContinue
        } else {
            [Environment]::SetEnvironmentVariable($name, [string]$SavedEnvironment[$name], 'Process')
        }
    }
}

function Get-RehearsalComposeArguments {
    param([Parameter(Mandatory)][string]$RepositoryRoot, [Parameter(Mandatory)][string]$ProjectName)
    if ($ProjectName -cnotmatch '^aegisflow-p03-verify-[0-9a-f]{32}$') {
        throw 'Refusing a non-disposable Compose project name.'
    }
    if (-not [IO.Path]::IsPathFullyQualified($RepositoryRoot)) { throw 'RepositoryRoot must be absolute.' }
    @('compose', '--project-name', $ProjectName, '--env-file', (Join-Path $RepositoryRoot '.env.example'),
        '--file', (Join-Path $RepositoryRoot 'compose.yaml'),
        '--file', (Join-Path $RepositoryRoot 'compose.persistence-test.yaml'), '--profile', 'postgres')
}

function Get-RehearsalPublishedPort {
    param([Parameter(Mandatory)][string]$Output)
    if ($Output.Trim() -cnotmatch '^127\.0\.0\.1:([0-9]{1,5})$') {
        throw 'Expected exactly one ephemeral IPv4 loopback database binding.'
    }
    $port = [int]$Matches[1]
    if ($port -lt 1 -or $port -gt 65535) { throw 'Invalid published database port.' }
    $port
}

function Get-RehearsalJavaImage {
    # Official multi-platform index recorded in the rehearsal evidence; never a floating tag.
    'eclipse-temurin:21-jre-jammy@sha256:8d584faeef02c22f6b58ad8a3cee8e1e53ae52bdf2224f4afb989cc35983b8ae'
}

function Get-RehearsalApplicationArguments {
    param(
        [Parameter(Mandatory)][string]$RepositoryRoot,
        [Parameter(Mandatory)][string]$ProjectName,
        [Parameter(Mandatory)][ValidateSet('core', 'order', 'payment', 'warehouse')][string]$Owner,
        [Parameter(Mandatory)][string]$JarPath,
        [Parameter(Mandatory)][string]$ProbeDirectory
    )
    if ($ProjectName -cnotmatch '^aegisflow-p03-verify-[0-9a-f]{32}$') { throw 'Refusing a non-disposable application project.' }
    if (-not [IO.Path]::IsPathFullyQualified($RepositoryRoot)) { throw 'RepositoryRoot must be absolute.' }
    foreach ($path in @($JarPath, $ProbeDirectory)) {
        if (-not [IO.Path]::IsPathFullyQualified($path) -or $path.Contains(',')) { throw 'Rehearsal mounts require absolute, unambiguous paths.' }
        $relative = [IO.Path]::GetRelativePath($RepositoryRoot, $path)
        if ([IO.Path]::IsPathFullyQualified($relative) -or $relative -eq '..' -or $relative.StartsWith('..' + [IO.Path]::DirectorySeparatorChar)) {
            throw 'Refusing an application mount outside the repository.'
        }
    }
    $arguments = @('create', '--pull', 'never', '--label', "com.aegisflow.rehearsal.project=$ProjectName",
        '--network', "${ProjectName}_${Owner}-data", '--read-only', '--user', '65532:65532',
        '--cap-drop', 'ALL', '--security-opt', 'no-new-privileges',
        '--tmpfs', '/tmp:rw,noexec,nosuid,nodev,size=128m,mode=1777', '--entrypoint', 'java',
        '--mount', "type=bind,source=$JarPath,target=/opt/aegisflow/app.jar,readonly",
        '--mount', "type=bind,source=$ProbeDirectory,target=/opt/aegisflow/probe,readonly")
    $prefix = $Owner.ToUpperInvariant() + '_DB_'
    foreach ($suffix in @('URL', 'RUNTIME_USER', 'RUNTIME_PASSWORD', 'MIGRATION_USER', 'MIGRATION_PASSWORD')) {
        # Docker copies values from its own filtered child environment; no password is an argument.
        $arguments += @('--env', ($prefix + $suffix))
    }
    $arguments + @((Get-RehearsalJavaImage), '-Duser.timezone=UTC', '-jar', '/opt/aegisflow/app.jar',
        '--spring.profiles.active=postgres', '--server.address=127.0.0.1', '--server.port=8080')
}

function Assert-RehearsalOwnedContainer {
    param(
        [Parameter(Mandatory)][string]$ContainerId,
        [Parameter(Mandatory)][string]$ProjectName,
        [Parameter(Mandatory)][AllowEmptyString()][string]$ObservedProjectLabel
    )
    if ($ContainerId -cnotmatch '^[0-9a-f]{64}$' -or
            $ProjectName -cnotmatch '^aegisflow-p03-verify-[0-9a-f]{32}$' -or
            $ObservedProjectLabel -cne $ProjectName) {
        throw 'Refusing to target an application container without its exact ID and matching rehearsal ownership label.'
    }
}

function New-RehearsalChildEnvironment {
    param(
        [Parameter(Mandatory)][System.Collections.IDictionary]$SourceEnvironment,
        [Parameter(Mandatory)][ValidateSet('core', 'order', 'payment', 'warehouse')][string]$Owner,
        [Parameter(Mandatory)][System.Collections.IDictionary]$ConnectionEnvironment
    )
    $result = [Collections.Generic.Dictionary[string,string]]::new([StringComparer]::OrdinalIgnoreCase)
    foreach ($key in $SourceEnvironment.Keys) {
        # Do not inherit another owner's credentials or Spring/JVM config that can
        # override the selected profile, connection, or test-only bind address.
        if ($key -match '(^|_)(DB|DATABASE|JDBC)(_|$)|^PG[A-Z_]*$|^SPRING_|^JAVA_TOOL_OPTIONS$|^JDK_JAVA_OPTIONS$|^_JAVA_OPTIONS$|^CLASSPATH$') { continue }
        $result[[string]$key] = [string]$SourceEnvironment[$key]
    }
    $prefix = $Owner.ToUpperInvariant() + '_DB_'
    $required = @('URL', 'RUNTIME_USER', 'RUNTIME_PASSWORD', 'MIGRATION_USER', 'MIGRATION_PASSWORD')
    if ($ConnectionEnvironment.Count -ne $required.Count) { throw 'Exactly five owner connection variables are required.' }
    foreach ($suffix in $required) {
        $key = $prefix + $suffix
        if (-not $ConnectionEnvironment.Contains($key) -or -not $ConnectionEnvironment[$key]) {
            throw 'Missing or foreign owner connection variable.'
        }
        $result[$key] = [string]$ConnectionEnvironment[$key]
    }
    $result
}

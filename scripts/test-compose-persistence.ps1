#requires -Version 7.0
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'lib/compose-persistence.ps1')
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$passed = 0

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
}

function Assert-Rejected([scriptblock]$Action) {
    $rejected = $false
    try { & $Action | Out-Null } catch { $rejected = $true }
    Assert-True $rejected 'Unsafe input was accepted.'
}

function Invoke-Case([string]$Name, [scriptblock]$Action) {
    & $Action
    $script:passed++
    Write-Output "PASS: $Name"
}

Invoke-Case 'fresh project names are unique and restricted to the rehearsal namespace' {
    $first = New-RehearsalProjectName
    $second = New-RehearsalProjectName
    Assert-True ($first -cmatch '^aegisflow-p03-verify-[0-9a-f]{32}$') 'Unexpected project namespace.'
    Assert-True ($first -cne $second) 'Project identity reused.'
}

Invoke-Case 'the normal development project cannot be targeted' {
    Assert-Rejected { Get-RehearsalComposeArguments -RepositoryRoot $repositoryRoot -ProjectName 'aegisflow-foundation' }
    Assert-Rejected { Get-RehearsalComposeArguments -RepositoryRoot $repositoryRoot -ProjectName '../aegisflow' }
    Assert-Rejected { Get-RehearsalComposeArguments -RepositoryRoot $repositoryRoot -ProjectName 'aegisflow-p03-verify-*' }
}

Invoke-Case 'Compose source paths must be absolute' {
    Assert-Rejected { Get-RehearsalComposeArguments -RepositoryRoot '.' -ProjectName (New-RehearsalProjectName) }
}

Invoke-Case 'Compose invocation names its project, files and nonprivate env file explicitly' {
    $project = New-RehearsalProjectName
    $arguments = @(Get-RehearsalComposeArguments -RepositoryRoot $repositoryRoot -ProjectName $project)
    Assert-True ($arguments -contains $project) 'Project missing.'
    Assert-True ($arguments -contains (Join-Path $repositoryRoot '.env.example')) 'Explicit example env file missing.'
    Assert-True ($arguments -contains (Join-Path $repositoryRoot 'compose.yaml')) 'Original Compose model missing.'
    Assert-True ($arguments -contains (Join-Path $repositoryRoot 'compose.persistence-test.yaml')) 'Ephemeral port override missing.'
    Assert-True (-not ($arguments -contains '--volumes')) 'Volume deletion must not be implicit.'
}

Invoke-Case 'published database ports accept loopback only' {
    Assert-True ((Get-RehearsalPublishedPort -Output '127.0.0.1:54321') -eq 54321) 'Valid port rejected.'
    Assert-True ((Get-RehearsalPublishedPort -Output "127.0.0.1:65535`n") -eq 65535) 'Valid trailing newline rejected.'
}

Invoke-Case 'wildcard, remote, invalid and ambiguous published ports are rejected' {
    foreach ($address in @('0.0.0.0:5432', '192.168.1.1:5432', '[::]:5432', '127.0.0.1:0', '127.0.0.1:65536', "127.0.0.1:5432`n127.0.0.1:5433")) {
        Assert-Rejected { Get-RehearsalPublishedPort -Output $address }
    }
}

$source = @{
    Path = 'unchanged-path'
    CORE_DB_PASSWORD = 'fixture-core-admin'
    ORDER_DB_PASSWORD = 'fixture-order-admin'
    PAYMENT_DB_PASSWORD = 'fixture-payment-admin'
    WAREHOUSE_DB_PASSWORD = 'fixture-warehouse-admin'
    CORE_DB_RUNTIME_PASSWORD = 'fixture-inherited-runtime'
    PGUSER = 'fixture-global-user'
    PGPASSWORD = 'fixture-global-password'
    DATABASE_URL = 'fixture-global-url'
    SPRING_DATASOURCE_PASSWORD = 'fixture-overlay'
    SPRING_FLYWAY_PASSWORD = 'fixture-overlay'
    SPRING_APPLICATION_JSON = 'fixture-overlay'
    SPRING_CONFIG_IMPORT = 'fixture-overlay'
    SPRING_PROFILES_ACTIVE = 'fixture-overlay'
    JAVA_TOOL_OPTIONS = 'fixture-overlay'
}

foreach ($owner in @('core', 'order', 'payment', 'warehouse')) {
    # These strings are only unit fixtures, never usable database credentials.
    $prefix = $owner.ToUpperInvariant()
    $connection = @{}
    $connection["${prefix}_DB_URL"] = 'jdbc:postgresql://127.0.0.1:54321/fixture'
    $connection["${prefix}_DB_RUNTIME_USER"] = 'fixture_runtime'
    $connection["${prefix}_DB_RUNTIME_PASSWORD"] = 'fixture-runtime-only'
    $connection["${prefix}_DB_MIGRATION_USER"] = 'fixture_migrator'
    $connection["${prefix}_DB_MIGRATION_PASSWORD"] = 'fixture-migration-only'
    Invoke-Case "$owner child receives only its own five database settings" {
        $child = New-RehearsalChildEnvironment -SourceEnvironment $source -Owner $owner -ConnectionEnvironment $connection
        Assert-True ($child['Path'] -eq 'unchanged-path') 'Unrelated process environment changed.'
        Assert-True ($source.Count -eq 15) 'Parent environment was modified.'
        Assert-True ($source['CORE_DB_PASSWORD'] -eq 'fixture-core-admin') 'Parent credentials were changed.'
        foreach ($name in $connection.Keys) {
            Assert-True ($child[$name] -eq $connection[$name]) "Own connection field missing: $name"
        }
        foreach ($name in $source.Keys) {
            if ($name -ne 'Path') {
                Assert-True (-not $child.ContainsKey($name) -or $connection.ContainsKey($name)) "Inherited credential/configuration leaked: $name"
            }
        }
        $databaseNames = @($child.Keys | Where-Object { $_ -match '^(CORE|ORDER|PAYMENT|WAREHOUSE)_DB_' })
        Assert-True ($databaseNames.Count -eq 5) 'A sibling database setting leaked.'
    }
}

Invoke-Case 'missing or foreign connection fields cannot be injected into Core' {
    Assert-Rejected { New-RehearsalChildEnvironment -SourceEnvironment $source -Owner core -ConnectionEnvironment @{ CORE_DB_URL = 'fixture' } }
    $foreign = @{
        CORE_DB_URL = 'fixture'
        CORE_DB_RUNTIME_USER = 'fixture'
        CORE_DB_RUNTIME_PASSWORD = 'fixture'
        CORE_DB_MIGRATION_USER = 'fixture'
        PAYMENT_DB_PASSWORD = 'fixture'
    }
    Assert-Rejected { New-RehearsalChildEnvironment -SourceEnvironment $source -Owner core -ConnectionEnvironment $foreign }
}

foreach ($originalState in @('absent', 'empty', 'existing')) {
    Invoke-Case "credential cleanup exactly restores the $originalState environment state" {
        $name = 'WAREHOUSE_DB_RUNTIME_PASSWORD'
        $actualOriginal = @{ $name = [Environment]::GetEnvironmentVariable($name, 'Process') }
        try {
            $expected = switch ($originalState) { absent { $null }; empty { '' }; existing { 'fixture-original' } }
            $saved = @{ $name = $expected }
            [Environment]::SetEnvironmentVariable($name, 'fixture-generated', 'Process')
            Restore-RehearsalEnvironment -SavedEnvironment $saved
            Assert-True ([Environment]::GetEnvironmentVariable($name, 'Process') -ceq $expected) 'Credential environment state was not restored exactly.'
        } finally { Restore-RehearsalEnvironment -SavedEnvironment $actualOriginal }
    }
}

foreach ($owner in @('core', 'order', 'payment', 'warehouse')) {
    Invoke-Case "$owner verification container stays on its own internal network with owner-only credentials" {
        $project = New-RehearsalProjectName
        $containerArguments = @(Get-RehearsalApplicationArguments -RepositoryRoot $repositoryRoot -ProjectName $project -Owner $owner `
                -JarPath (Join-Path $repositoryRoot 'synthetic fixture/application.jar') -ProbeDirectory (Join-Path $repositoryRoot 'synthetic fixture/probe'))
        Assert-True ($containerArguments -contains 'create') 'Expected an explicit container creation command.'
        Assert-True ($containerArguments -contains "${project}_${owner}-data") 'Owner network missing.'
        foreach ($required in @('--read-only', '--cap-drop', 'ALL', '--security-opt', 'no-new-privileges', '--user', '65532:65532')) {
            Assert-True ($containerArguments -contains $required) "Container hardening argument missing: $required"
        }
        Assert-True (-not ($containerArguments -contains '--publish') -and -not ($containerArguments -contains '-p')) 'A verification application port was exposed to the host.'
        Assert-True (-not (($containerArguments -join ' ') -match 'docker\.sock|dockerDesktopLinuxEngine')) 'Docker control endpoint mounted in the application.'
        $environmentNames = @()
        for ($index = 0; $index -lt $containerArguments.Count; $index++) {
            if ($containerArguments[$index] -in @('--env', '-e')) { $environmentNames += $containerArguments[$index + 1] }
        }
        Assert-True ($environmentNames.Count -eq 5) 'Unexpected database environment count.'
        foreach ($name in $environmentNames) {
            Assert-True ($name -cmatch ('^' + $owner.ToUpperInvariant() + '_DB_(URL|RUNTIME_USER|RUNTIME_PASSWORD|MIGRATION_USER|MIGRATION_PASSWORD)$')) 'A sibling credential or credential value was placed in Docker arguments.'
        }
        $mounts = @($containerArguments | Where-Object { $_ -match '^(type=bind,|.*:ro$)' })
        Assert-True ($mounts.Count -ge 2) 'Read-only JAR/probe mounts missing.'
        Assert-True (($containerArguments -join ' ') -match '@sha256:[0-9a-f]{64}') 'Verification image is not digest-pinned.'
    }
}

Invoke-Case 'cleanup rejects foreign projects and malformed container identities' {
    $project = New-RehearsalProjectName
    $containerId = 'a' * 64
    Assert-RehearsalOwnedContainer -ContainerId $containerId -ProjectName $project -ObservedProjectLabel $project
    Assert-Rejected { Assert-RehearsalOwnedContainer -ContainerId $containerId -ProjectName $project -ObservedProjectLabel 'aegisflow-foundation' }
    Assert-Rejected { Assert-RehearsalOwnedContainer -ContainerId 'not-an-id' -ProjectName $project -ObservedProjectLabel $project }
}

Write-Output "Compose rehearsal helper tests: passed=$passed, failures=0; Docker/PostgreSQL not exercised."

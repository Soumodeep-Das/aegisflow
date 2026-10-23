#requires -Version 7.0
function Get-DevelopmentSecretKeys {
    foreach($owner in @('CORE','ORDER','PAYMENT','WAREHOUSE')) {
        foreach($role in @('PASSWORD','MIGRATION_PASSWORD','RUNTIME_PASSWORD')) { "${owner}_DB_$role" }
    }
    foreach($principal in @('BROKER','ADMIN','ORDER','PAYMENT','WAREHOUSE','CORE')) { "KAFKA_${principal}_PASSWORD" }
}

function New-DevelopmentCredentials {
    $values=[ordered]@{}
    foreach($key in Get-DevelopmentSecretKeys) {
        do { $secret=[Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(32)).ToLowerInvariant() }
        while($values.Values -contains $secret)
        $values[$key]=$secret
    }
    $values['KAFKA_CLUSTER_ID']=[Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(16)).TrimEnd('=').Replace('+','-').Replace('/','_')
    $values['KAFKA_HOST_PORT']='19092'
    return $values
}

function Write-NewDevelopmentCredentials([string]$Path,$Values) {
    Assert-DevelopmentCredentials $Values
    if(-not $IsWindows) { throw 'Private-file initialization currently requires Windows ACL support.' }
    # Apply the protected DACL at creation, before any credential bytes are written.
    $security=[Security.AccessControl.FileSecurity]::new()
    $security.SetAccessRuleProtection($true,$false)
    $sid=[Security.Principal.WindowsIdentity]::GetCurrent().User
    $security.SetOwner($sid)
    $security.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new($sid,'FullControl','Allow'))
    $stream=[IO.FileSystemAclExtensions]::Create([IO.FileInfo]::new($Path),[IO.FileMode]::CreateNew,
        [Security.AccessControl.FileSystemRights]::FullControl,[IO.FileShare]::None,4096,[IO.FileOptions]::None,$security)
    $writer=[IO.StreamWriter]::new($stream,[Text.UTF8Encoding]::new($false))
    try { foreach($key in $Values.Keys) { $writer.WriteLine("$key=$($Values[$key])") } } finally { $writer.Dispose() }
}

function Read-DevelopmentCredentials([string]$Path) {
    $values=[ordered]@{}
    $allowed=@(Get-DevelopmentSecretKeys)+@('KAFKA_CLUSTER_ID','KAFKA_HOST_PORT')
    foreach($line in [IO.File]::ReadAllLines($Path)) {
        if([string]::IsNullOrWhiteSpace($line) -or $line.StartsWith('#')) { continue }
        if($line -notmatch '^([A-Z_]+)=([A-Za-z0-9_-]+)$') { throw 'Invalid private configuration syntax; values withheld.' }
        $key=$Matches[1]; $value=$Matches[2]
        if($key -notin $allowed -or $values.Contains($key)) { throw 'Unknown or duplicate private configuration key; values withheld.' }
        $values[$key]=$value
    }
    Assert-DevelopmentCredentials $values
    return $values
}

function Assert-DevelopmentCredentials($Values) {
    $seen=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach($key in Get-DevelopmentSecretKeys) {
        if($Values[$key] -cnotmatch '^[0-9a-f]{64}$' -or -not $seen.Add($Values[$key])) {
            throw 'All 18 passwords must be distinct 256-bit lowercase hex values; values withheld.'
        }
    }
    if($Values['KAFKA_CLUSTER_ID'] -cnotmatch '^[A-Za-z0-9_-]{22}$' -or $Values['KAFKA_HOST_PORT'] -ne '19092') {
        throw 'Invalid cluster identity or unsupported local listener port; values withheld.'
    }
}

function Assert-DevelopmentInitializationSafe([bool]$PrivateExists,[string[]]$Volumes,[bool]$LegacyExists) {
    if(-not $PrivateExists -and ($Volumes.Count -gt 0 -or $LegacyExists)) {
        throw 'Private configuration missing but existing storage or legacy configuration exists. Restore matching credentials; nothing regenerated or overwritten.'
    }
}

function Invoke-WithDevelopmentEnvironment([scriptblock]$EnvironmentBody) {
    # Shell interpolation outranks --env-file. Remove every variable used by the fixed Compose files.
    $keys=@(Get-DevelopmentSecretKeys)+@('KAFKA_CLUSTER_ID','KAFKA_HOST_PORT','COMPOSE_FILE','COMPOSE_PROJECT_NAME','COMPOSE_PROFILES','COMPOSE_ENV_FILES')
    foreach($owner in @('CORE','ORDER','PAYMENT','WAREHOUSE')) {
        $keys+=@("${owner}_DB_USER","${owner}_DB_MIGRATION_USER","${owner}_DB_RUNTIME_USER")
    }
    $saved=@{}
    try {
        foreach($key in $keys) {
            $saved[$key]=@{Present=(Test-Path -LiteralPath "Env:$key");Value=[Environment]::GetEnvironmentVariable($key,'Process')}
            # On this runtime SetEnvironmentVariable(null) leaves an empty override.
            # Compose distinguishes absent from empty; use the provider's removal operation.
            Remove-Item -LiteralPath "Env:$key" -ErrorAction SilentlyContinue
        }
        & $EnvironmentBody
    } finally {
        foreach($key in $keys) {
            if($saved[$key].Present) { [Environment]::SetEnvironmentVariable($key,$saved[$key].Value,'Process') }
            else { Remove-Item -LiteralPath "Env:$key" -ErrorAction SilentlyContinue }
        }
    }
}

#requires -Version 7.0
$ErrorActionPreference='Stop'
. "$PSScriptRoot/lib/development-credentials.ps1"
$count=0
function Check([scriptblock]$Case,[bool]$Reject=$false) {
    $failed=$false
    try { & $Case } catch { $failed=$true; if(-not $Reject) { throw } }
    if($Reject -and -not $failed) { throw 'Expected rejection did not occur.' }
    $script:count++
}
Check { Assert-DevelopmentCredentials (New-DevelopmentCredentials) }
Check { $v=New-DevelopmentCredentials; $v.CORE_DB_PASSWORD=$v.ORDER_DB_PASSWORD; Assert-DevelopmentCredentials $v } $true
Check { $v=New-DevelopmentCredentials; $v.CORE_DB_PASSWORD='weak'; Assert-DevelopmentCredentials $v } $true
Check { Assert-DevelopmentInitializationSafe $false @('retained') $false } $true
Check { Assert-DevelopmentInitializationSafe $false @() $true } $true
Check { Assert-DevelopmentInitializationSafe $false @() $false }
Check { Assert-DevelopmentInitializationSafe $true @('retained') $true }
$old=[Environment]::GetEnvironmentVariable('CORE_DB_PASSWORD','Process')
try {
    [Environment]::SetEnvironmentVariable('CORE_DB_PASSWORD','test-sentinel','Process')
    Check { Invoke-WithDevelopmentEnvironment { if($env:CORE_DB_PASSWORD) { throw 'Environment override leaked.' } } }
    Check { Invoke-WithDevelopmentEnvironment { foreach($key in Get-DevelopmentSecretKeys) { if(Test-Path -LiteralPath "Env:$key") { throw 'Empty environment override remains.' } } } }
    Check { if($env:CORE_DB_PASSWORD -ne 'test-sentinel') { throw 'Environment was not restored.' } }
    Check { Invoke-WithDevelopmentEnvironment { throw 'Expected action failure' } } $true
    Check { if($env:CORE_DB_PASSWORD -ne 'test-sentinel') { throw 'Failure did not restore environment.' } }
} finally { [Environment]::SetEnvironmentVariable('CORE_DB_PASSWORD',$old,'Process') }
$scratch=Join-Path (Split-Path -Parent $PSScriptRoot) ('.cache/credential-policy/'+[guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $scratch -Force | Out-Null
$testFile=Join-Path $scratch 'private.env'
$generated=New-DevelopmentCredentials
Check { Write-NewDevelopmentCredentials $testFile $generated }
Check { $parsed=Read-DevelopmentCredentials $testFile; if($parsed.CORE_DB_PASSWORD -ne $generated.CORE_DB_PASSWORD) { throw 'Roundtrip mismatch.' } }
$hash=(Get-FileHash $testFile).Hash
Check { Write-NewDevelopmentCredentials $testFile (New-DevelopmentCredentials) } $true
Check { if((Get-FileHash $testFile).Hash -ne $hash) { throw 'Existing private file changed.' } }
Check { $acl=Get-Acl $testFile; if(-not $acl.AreAccessRulesProtected -or @($acl.Access).Count -ne 1) { throw 'Private ACL not protected.' } }
Check {
    $repo=Split-Path -Parent $PSScriptRoot
    Invoke-WithDevelopmentEnvironment {
        & docker compose --env-file $testFile --file "$repo/compose.yaml" --file "$repo/compose.kafka.yaml" --profile postgres config --quiet
        if($LASTEXITCODE -ne 0) { throw 'Actual Compose file interpolation failed.' }
    }
}
foreach($file in @('dev-init.ps1','dev-infrastructure-up.ps1','verify-development-credentials.ps1','lib/development-credentials.ps1')) {
    Check { $tokens=$null; $errors=$null; [Management.Automation.Language.Parser]::ParseFile("$PSScriptRoot/$file",[ref]$tokens,[ref]$errors) | Out-Null; if($errors.Count) { throw 'Parse failed.' } }
}
Write-Output "PASS: $count credential policy/file/ACL/environment/parser/Compose checks; disposable ignored credentials only. Docker Compose parsing used, no engine resources created. B is not verified."

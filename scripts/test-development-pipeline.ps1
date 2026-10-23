#requires -Version 7.0
$ErrorActionPreference='Stop'
# Extract only pure/helper function definitions; NEVER execute the live script body.
$tokens=$null; $errors=$null
$ast=[Management.Automation.Language.Parser]::ParseFile("$PSScriptRoot/verify-development-pipeline.ps1",[ref]$tokens,[ref]$errors)
if($errors.Count) { throw 'Pipeline script parsing failed.' }
foreach($name in @('Wait-Condition','Invoke-Compose','Read-Database','Count-Core')) {
    $definition=$ast.FindAll({param($node) $node -is [Management.Automation.Language.FunctionDefinitionAst]},$false) | Where-Object Name -eq $name
    if(@($definition).Count -ne 1) { throw "Missing unique function: $name" }
    . ([scriptblock]::Create($definition.Extent.Text))
}
$tenant='demo-11111111-1111-1111-1111-111111111111'
$run='11111111-1111-1111-1111-111111111111'
$evidence='synthetic-evidence'
$compose=@('compose','--project-name','synthetic-test')
$script:calls=@()
function docker {
    $script:calls+=,@($args)
    $global:LASTEXITCODE=0
    if($args -contains 'exec') { '3' }
}
function Assert($Condition,$Message) { if(-not $Condition) { throw $Message } }
function Must-Fail([scriptblock]$Body) {
    $failed=$false
    try { & $Body | Out-Null } catch { $failed=$true }
    Assert $failed 'Expected rejection did not happen.'
}
Invoke-Compose @('config','--quiet')
Assert (($script:calls[0] -join '|') -eq 'compose|--project-name|synthetic-test|config|--quiet') 'Compose argument array drift.'
Must-Fail { Read-Database core "DELETE FROM aegisflow.incident WHERE tenant_id='$tenant'" }
Must-Fail { Read-Database core 'SELECT count(*) FROM aegisflow.incident' }
Assert ($script:calls.Count -eq 1) 'Rejected query reached Docker.'
Assert ((Count-Core incident missing) -eq 3) 'Count conversion failed.'
$query=$script:calls[-1][-1]
Assert ($query.Contains("tenant_id='$tenant'") -and $query.Contains("business_transaction_id='missing-$run'")) 'Tenant/transaction scoping missing.'
Assert (($script:calls[-1] -join ' ').Contains('default_transaction_read_only=on')) 'Database read-only enforcement missing.'
Wait-Condition { $true } 0 'immediate-success'
Must-Fail { Wait-Condition { $false } 0 'bounded-timeout' }
Write-Output 'PASS: pipeline parser and six helper checks (argument routing, two query guards, scoped count, immediate readiness, bounded timeout). Mock Docker only; no live acceptance.'

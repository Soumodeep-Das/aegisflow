#requires -Version 7.0
$ErrorActionPreference='Stop'
. "$PSScriptRoot/lib/development-boundaries.ps1"
$valid='a'*64
if((Get-DevelopmentContainerId @($valid) 0) -cne $valid) { throw 'Valid ID rejected.' }
if((Get-DevelopmentContainerId @(''," $valid ") 0) -cne $valid) { throw 'Whitespace handling failed.' }
$cases=@(@{Output=@();Code=0},@{Output=$null;Code=0},@{Output=@('');Code=0},@{Output=@($valid,$valid);Code=0},@{Output=@('invalid');Code=0},@{Output=@($valid);Code=1})
foreach($case in $cases) {
    $rejected=$false
    try { Get-DevelopmentContainerId -Output $case.Output -ExitCode $case.Code | Out-Null } catch { $rejected=$true }
    if(-not $rejected) { throw 'Invalid discovery accepted.' }
}
foreach($file in @('verify-development-boundaries.ps1','lib/development-boundaries.ps1','verify-boundary-guards.ps1')) {
    $t=$null; $e=$null
    [Management.Automation.Language.Parser]::ParseFile("$PSScriptRoot/$file",[ref]$t,[ref]$e) | Out-Null
    if($e.Count) { throw 'Parse failed.' }
}
Write-Output 'PASS: 8 container-discovery guards and 3 script parses; no Docker operations.'

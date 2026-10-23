function Get-DevelopmentContainerId {
    param([AllowNull()][AllowEmptyCollection()][object[]]$Output,[int]$ExitCode)
    $lines=@($Output | ForEach-Object { if($null -ne $_ -and -not [string]::IsNullOrWhiteSpace("$_")) { "$_".Trim() } })
    if($ExitCode -ne 0 -or $lines.Count -ne 1 -or $lines[0] -cnotmatch '^[a-f0-9]{64}$') {
        throw 'Expected exactly one existing container ID; discovery returned none, multiple, invalid output or failed. No inspection attempted.'
    }
    return [string]$lines[0]
}

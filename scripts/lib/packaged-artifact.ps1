function Assert-RepackagedJarCurrent {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$JarPath,
        [Parameter(Mandatory)][string[]]$InputPaths
    )
    $originalPath="$JarPath.original"
    foreach($path in @($JarPath,$originalPath)) {
        if(-not (Test-Path -LiteralPath $path -PathType Leaf)) {
            throw "Missing build artifact: $path; run the full verifier."
        }
    }
    # Boot normalizes the executable archive's timestamp for reproducibility.
    # The original Maven JAR retains the build time; verify the pair's content too.
    $latest=($InputPaths | ForEach-Object { Get-Item -LiteralPath $_ } |
        Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1).LastWriteTimeUtc
    if((Get-Item -LiteralPath $originalPath).LastWriteTimeUtc -lt $latest) {
        throw "Original JAR is older than its production inputs: $JarPath; run the full verifier."
    }
    $original=$null
    $packaged=$null
    try {
        $original=[IO.Compression.ZipFile]::OpenRead($originalPath)
        $packaged=[IO.Compression.ZipFile]::OpenRead($JarPath)
        $matched=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
        $classCount=0
        foreach($entry in $original.Entries) {
            if($entry.FullName.EndsWith('/') -or $entry.FullName -ceq 'META-INF/MANIFEST.MF') { continue }
            $target=$packaged.GetEntry("BOOT-INF/classes/$($entry.FullName)")
            if($null -eq $target -and $entry.FullName.StartsWith('META-INF/',[StringComparison]::Ordinal)) {
                $target=$packaged.GetEntry($entry.FullName)
            }
            if($null -eq $target -or $target.Length -ne $entry.Length) {
                throw "Executable/original JAR content differs: $($entry.FullName); run the full verifier."
            }
            $left=$entry.Open()
            $right=$target.Open()
            $sha=[Security.Cryptography.SHA256]::Create()
            try {
                $leftHash=[BitConverter]::ToString($sha.ComputeHash($left))
                $rightHash=[BitConverter]::ToString($sha.ComputeHash($right))
                if($leftHash -cne $rightHash) {
                    throw "Executable/original JAR content differs: $($entry.FullName); run the full verifier."
                }
            } finally { $sha.Dispose(); $left.Dispose(); $right.Dispose() }
            [void]$matched.Add($target.FullName)
            if($entry.FullName.EndsWith('.class',[StringComparison]::Ordinal)) { $classCount++ }
        }
        if($classCount -eq 0) { throw 'Original JAR contains no application classes.' }
        foreach($entry in $packaged.Entries) {
            if($entry.FullName.StartsWith('BOOT-INF/classes/',[StringComparison]::Ordinal) -and
                -not $entry.FullName.EndsWith('/') -and -not $matched.Contains($entry.FullName)) {
                throw "Executable JAR contains unmatched application content: $($entry.FullName); run the full verifier."
            }
        }
    } finally {
        if($null -ne $packaged) { $packaged.Dispose() }
        if($null -ne $original) { $original.Dispose() }
    }
}

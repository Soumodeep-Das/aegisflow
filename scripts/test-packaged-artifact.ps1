#requires -Version 7.0
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'lib/packaged-artifact.ps1')
$fixture=Join-Path (Split-Path -Parent $PSScriptRoot) ('.cache/artifact-guard/'+[Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $fixture -Force | Out-Null
$jar=Join-Path $fixture 'application.jar'
$inputPath=Join-Path $fixture 'pom.xml'
New-Item -ItemType File -Path $inputPath | Out-Null
$built=[DateTime]::UtcNow.AddMinutes(-1)
(Get-Item -LiteralPath $inputPath).LastWriteTimeUtc=$built.AddMinutes(-1)
function Write-FixtureArchive([string]$Path,[hashtable]$Entries) {
    $file=[IO.File]::Open($Path,[IO.FileMode]::Create)
    $zip=[IO.Compression.ZipArchive]::new($file,[IO.Compression.ZipArchiveMode]::Create)
    try {
        foreach($name in $Entries.Keys) {
            $stream=$zip.CreateEntry($name).Open()
            try { $bytes=[Text.Encoding]::UTF8.GetBytes($Entries[$name]); $stream.Write($bytes,0,$bytes.Length) }
            finally { $stream.Dispose() }
        }
    } finally { $zip.Dispose(); $file.Dispose() }
}
function Assert-Rejected([scriptblock]$Action,[string]$Expected) {
    $rejected=$false
    try { & $Action } catch {
        if($_.Exception.Message -notlike "*$Expected*") { throw }
        $rejected=$true
    }
    if(-not $rejected) { throw "Guard accepted invalid fixture: $Expected" }
}
$originalEntries=@{
    'com/example/App.class'='current-bytecode'
    'application.yaml'='current-config'
    'META-INF/maven/example/app/pom.xml'='current-pom'
    'META-INF/MANIFEST.MF'='original-manifest'
}
$packagedEntries=@{
    'BOOT-INF/classes/com/example/App.class'='current-bytecode'
    'BOOT-INF/classes/application.yaml'='current-config'
    'META-INF/maven/example/app/pom.xml'='current-pom'
    'META-INF/MANIFEST.MF'='rewritten-boot-manifest'
}
Write-FixtureArchive "$jar.original" $originalEntries
(Get-Item -LiteralPath "$jar.original").LastWriteTimeUtc=$built
Write-FixtureArchive $jar $packagedEntries
(Get-Item -LiteralPath $jar).LastWriteTimeUtc=[DateTime]::new(1980,2,1,0,0,0,[DateTimeKind]::Utc)
Assert-RepackagedJarCurrent -JarPath $jar -InputPaths $inputPath
Write-Output 'PASS: normalized executable timestamp with fresh matching original archive.'

(Get-Item -LiteralPath $inputPath).LastWriteTimeUtc=$built.AddSeconds(1)
Assert-Rejected { Assert-RepackagedJarCurrent -JarPath $jar -InputPaths $inputPath } 'older than its production inputs'
(Get-Item -LiteralPath $inputPath).LastWriteTimeUtc=$built.AddMinutes(-1)
Write-Output 'PASS: changed production inputs still require a rebuild.'

$packagedEntries['BOOT-INF/classes/com/example/App.class']='changed-bytecode'
Write-FixtureArchive $jar $packagedEntries
Assert-Rejected { Assert-RepackagedJarCurrent -JarPath $jar -InputPaths $inputPath } 'content differs'
$packagedEntries['BOOT-INF/classes/com/example/App.class']='current-bytecode'
Write-Output 'PASS: mismatched executable/original application bytes are rejected.'

$packagedEntries.Remove('BOOT-INF/classes/application.yaml')
Write-FixtureArchive $jar $packagedEntries
Assert-Rejected { Assert-RepackagedJarCurrent -JarPath $jar -InputPaths $inputPath } 'content differs'
$packagedEntries['BOOT-INF/classes/application.yaml']='current-config'
Write-Output 'PASS: missing executable application resources are rejected.'

$packagedEntries['BOOT-INF/classes/removed.yaml']='stale-config'
Write-FixtureArchive $jar $packagedEntries
Assert-Rejected { Assert-RepackagedJarCurrent -JarPath $jar -InputPaths $inputPath } 'unmatched application content'
Write-Output 'PASS: leftover executable application resources are rejected.'

Assert-Rejected { Assert-RepackagedJarCurrent -JarPath (Join-Path $fixture 'missing.jar') -InputPaths $inputPath } 'Missing build artifact'
Write-FixtureArchive (Join-Path $fixture 'unpaired.jar') $packagedEntries
Assert-Rejected { Assert-RepackagedJarCurrent -JarPath (Join-Path $fixture 'unpaired.jar') -InputPaths $inputPath } 'Missing build artifact'
Write-Output 'PASS: missing executable or original archive is rejected.'

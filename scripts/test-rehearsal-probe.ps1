#requires -Version 7.0
[CmdletBinding()]
param([string]$JavaHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
if (-not $JavaHome) { throw 'Set JAVA_HOME to Java 21.' }
$java = Join-Path $JavaHome 'bin/java.exe'
$javac = Join-Path $JavaHome 'bin/javac.exe'
if (-not (Test-Path -LiteralPath $java) -or -not (Test-Path -LiteralPath $javac)) { throw 'Java 21 JDK required.' }
if ((& $java -version 2>&1 | Out-String) -notmatch 'version "21\.') { throw 'Java 21 is required.' }
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$classes = Join-Path $repositoryRoot '.cache/rehearsal-probe-tests'
New-Item -ItemType Directory -Force -Path $classes | Out-Null
& $javac --release 21 -d $classes (Join-Path $PSScriptRoot 'java/RehearsalHealthProbe.java') (Join-Path $PSScriptRoot 'java/RehearsalHealthProbeTests.java')
if ($LASTEXITCODE -ne 0) { throw 'Health-probe compilation failed.' }
& $java -cp $classes RehearsalHealthProbeTests
if ($LASTEXITCODE -ne 0) { throw 'Health-probe contract tests failed.' }

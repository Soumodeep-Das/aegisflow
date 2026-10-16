#requires -Version 7.0
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$required = @(
    'README.md', 'AGENTS.md', 'docs/requirements/PRD.md',
    'docs/architecture/SYSTEM_ARCHITECTURE.md', 'docs/architecture/DATA_MODEL.md',
    'docs/architecture/EVENT_CONTRACTS.md', 'docs/architecture/RECOVERY_STATE_MACHINE.md',
    'docs/architecture/INVARIANTS.md', 'docs/architecture/MODULE_BOUNDARIES.md',
    'docs/security/THREAT_MODEL.md', 'docs/evaluation/FAILURE_MATRIX.md',
    'docs/evaluation/AI_EVALUATION.md', 'docs/evidence/TEST_RESULTS.md',
    'docs/roadmap/IMPLEMENTATION_PLAN.md', 'docs/OPEN_QUESTIONS.md',
    'docs/ROADMAP.md', 'docs/KNOWN_LIMITATIONS.md', 'docs/HANDOFF.md',
    'docs/devlog/DEVELOPMENT_JOURNAL.md', 'docs/learning/CODEBASE_GUIDE.md'
)
$issues = [System.Collections.Generic.List[string]]::new()
foreach ($relativePath in $required) {
    $path = Join-Path $repositoryRoot $relativePath
    if (-not (Test-Path -LiteralPath $path) -or (Get-Item -LiteralPath $path).Length -eq 0) {
        $issues.Add("Missing or empty document: $relativePath")
    }
}

# Inspect project documentation only, not dependency/cache/generated Markdown.
$files = @(
    Get-Item -LiteralPath (Join-Path $repositoryRoot 'README.md'), (Join-Path $repositoryRoot 'AGENTS.md')
    Get-ChildItem -LiteralPath (Join-Path $repositoryRoot 'docs') -Recurse -File -Filter '*.md'
)
$links = 0
$diagrams = 0
$jsonExamples = 0
foreach ($file in $files) {
    $content = Get-Content -Raw -LiteralPath $file.FullName
    if (([regex]::Matches($content, '(?m)^```')).Count % 2 -ne 0) {
        $issues.Add("Unbalanced fences: $($file.Name)")
    }
    $prose = [regex]::Replace($content, '(?ms)^```[^\r\n]*\r?\n.*?^```[^\r\n]*$', '')
    foreach ($match in [regex]::Matches($prose, '\[[^\]]*\]\(([^)]+)\)')) {
        $target = $match.Groups[1].Value.Trim().Trim('<', '>')
        if ($target -match '^(https?|mailto):' -or $target.StartsWith('#')) { continue }
        $target = ($target -split '#', 2)[0]
        if (-not $target) { continue }
        $links++
        if (-not (Test-Path -LiteralPath (Join-Path $file.DirectoryName $target))) {
            $issues.Add("Broken link: $($file.Name) -> $target")
        }
    }
    $diagrams += ([regex]::Matches($content, '(?m)^```mermaid\s*$')).Count
    foreach ($match in [regex]::Matches($content, '(?ms)^```json\r?\n(.*?)^```')) {
        $jsonExamples++
        try { $null = $match.Groups[1].Value | ConvertFrom-Json }
        catch { $issues.Add("Invalid JSON example: $($file.Name)") }
    }
}
$matrix = Get-Content -Raw -LiteralPath (Join-Path $repositoryRoot 'docs/evaluation/FAILURE_MATRIX.md')
$scenarios = ([regex]::Matches($matrix, '(?m)^### FM-\d{3} —')).Count
if ($scenarios -lt 16) { $issues.Add("Required failure scenarios missing; found $scenarios") }
foreach ($label in @('Fault mechanism', 'Expected state/incident', 'Prohibited actions', 'Required evidence', 'Test assertions')) {
    $count = ([regex]::Matches($matrix, '\*\*' + [regex]::Escape($label) + ':\*\*')).Count
    if ($count -ne $scenarios) { $issues.Add("Expected $scenarios '$label' fields; found $count") }
}
$guide = Get-Content -Raw -LiteralPath (Join-Path $repositoryRoot 'docs/learning/CODEBASE_GUIDE.md')
$questions = ([regex]::Matches($guide, '(?m)^### Q\d+\.')).Count
if ($questions -lt 25) { $issues.Add('Expected historical twenty plus five P0.3 checkpoint interview Q&As') }
$adrCount = @(Get-ChildItem -LiteralPath (Join-Path $repositoryRoot 'docs/adr') -File -Filter '*.md').Count
if ($adrCount -lt 7) { $issues.Add('Expected at least seven ADRs') }
Write-Output "Markdown=$($files.Count) Required=$($required.Count) LocalLinks=$links Mermaid=$diagrams JSON=$jsonExamples Scenarios=$scenarios InterviewQA=$questions ADRs=$adrCount Issues=$($issues.Count)"
if ($issues.Count) { throw ($issues -join [Environment]::NewLine) }
# This checks file/fence/JSON/field presence, not Mermaid rendering or application behavior.

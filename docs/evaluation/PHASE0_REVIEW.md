# Phase 0 documentation review

This review checks the specification, not application behavior. There are no runtime test, benchmark, AI, or security results in Phase 0.

## Reproduce the mechanical checks

Prerequisites: Git and PowerShell 7. Run from the AegisFlow repository root. This code reads files only; it does not install packages or change the repository.

```powershell
$ErrorActionPreference = 'Stop'
$required = @(
  'README.md', 'AGENTS.md', 'docs/requirements/PRD.md',
  'docs/architecture/SYSTEM_ARCHITECTURE.md', 'docs/architecture/DATA_MODEL.md',
  'docs/architecture/EVENT_CONTRACTS.md', 'docs/architecture/RECOVERY_STATE_MACHINE.md',
  'docs/architecture/INVARIANTS.md', 'docs/security/THREAT_MODEL.md',
  'docs/evaluation/FAILURE_MATRIX.md', 'docs/evaluation/AI_EVALUATION.md',
  'docs/roadmap/IMPLEMENTATION_PLAN.md', 'docs/OPEN_QUESTIONS.md',
  'docs/ROADMAP.md', 'docs/KNOWN_LIMITATIONS.md', 'docs/HANDOFF.md',
  'docs/devlog/DEVELOPMENT_JOURNAL.md', 'docs/learning/CODEBASE_GUIDE.md'
)
$issues = [System.Collections.Generic.List[string]]::new()
foreach ($path in $required) {
  if (-not (Test-Path -LiteralPath $path) -or (Get-Item -LiteralPath $path).Length -eq 0) {
    $issues.Add("Missing or empty required document: $path")
  }
}
$files = @(Get-ChildItem -Recurse -File -Filter '*.md')
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
$matrix = Get-Content -Raw 'docs/evaluation/FAILURE_MATRIX.md'
$scenarios = ([regex]::Matches($matrix, '(?m)^### FM-\d{3} —')).Count
if ($scenarios -ne 16) { $issues.Add("Expected 16 failure scenarios; found $scenarios") }
foreach ($label in @('Fault mechanism', 'Expected state/incident', 'Prohibited actions', 'Required evidence', 'Test assertions')) {
  $count = ([regex]::Matches($matrix, '\*\*' + [regex]::Escape($label) + ':\*\*')).Count
  if ($count -ne 16) { $issues.Add("Expected 16 '$label' fields; found $count") }
}
$guide = Get-Content -Raw 'docs/learning/CODEBASE_GUIDE.md'
$questions = ([regex]::Matches($guide, '(?m)^### Q\d+\.')).Count
if ($questions -lt 10) { $issues.Add('Expected at least 10 interview Q&As') }
$adrCount = @(Get-ChildItem 'docs/adr' -File -Filter '*.md').Count
if ($adrCount -lt 6) { $issues.Add('Expected at least six ADRs') }
$nonMarkdown = @(Get-ChildItem -Recurse -File | Where-Object { $_.Extension -ne '.md' })
if ($nonMarkdown.Count) { $issues.Add('Phase 0 contains non-Markdown project files') }
Write-Output "Markdown=$($files.Count) Required=$($required.Count) LocalLinks=$links Mermaid=$diagrams JSON=$jsonExamples Scenarios=$scenarios InterviewQA=$questions ADRs=$adrCount Issues=$($issues.Count)"
if ($issues.Count) { throw ($issues -join [Environment]::NewLine) }
git diff --check
if ($LASTEXITCODE -ne 0) { throw 'Working-tree whitespace check failed' }
git diff --cached --check
if ($LASTEXITCODE -ne 0) { throw 'Staged whitespace check failed' }
```

To run the exact block directly from this document:

```text
$review = Get-Content -Raw docs/evaluation/PHASE0_REVIEW.md
$check = [regex]::Match($review, '(?ms)^```powershell\r?\n(.*?)^```').Groups[1].Value
& ([scriptblock]::Create($check))
```

The local-link check confirms target paths exist; it does not validate remote URLs or a renderer's anchor rules. Mermaid fences are counted mechanically and their source is reviewed manually. A Mermaid parser/renderer is not installed in the current environment, so successful parsing and visual layout remain unverified. This limitation does not supply any evidence of runtime behavior.

## Consistency review checklist

- Valid event consumption commits inbox, fact, projection/deadline, and processed status atomically before Kafka acknowledgement.
- `OrderCreated` drives independently persisted Payment/Warehouse consumers; Order consumes their decision events; Warehouse retains cancellation intent across reordering.
- `dueAt` is the base business deadline; `enforceAfter` adds allowed lateness once.
- The plan has exactly one action; distinct attempts reuse its stable idempotency key.
- The material incident revision is separate from the state-transition CAS version.
- Canonical plan/approval bindings agree with data records and include evidence and policy hashes.
- Pre-dispatch invalidation has explicit reverse transitions; potentially dispatched calls enter verification.
- Attempt `UNKNOWN`, action `OUTCOME_UNKNOWN`, and incident `VERIFYING` are separate states.
- AI proposes an action type only; deterministic code derives parameters and controls policy/approval/execution.
- Every named failure includes mechanism, expected state, prohibited actions, evidence, and assertions.
- Every runtime metric remains a target or explicitly unmeasured; no implementation is claimed.
- Invalid input uses technical quarantine without inventing authenticated tenant/source fields; shared-topic ACL limitations are explicit.
- Parent cancellation makes uninvoked work non-claimable; self-resolution and definitive rejection have corresponding incident/action paths.

## Observed review result

Final command outcome, review date, commit, limitations, and remaining questions are recorded in the development journal and handoff. The scenario matrix itself remains entirely **not run**.

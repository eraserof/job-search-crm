<#
.SYNOPSIS
  Plan stage: the big model turns one ask into self-contained method jobs for the small model.

.DESCRIPTION
  Reads a request file ({ ask, context[] }), inlines the context files, and calls the "plan" stage
  from codegen\models.json. The model must return JSON jobs, one per method body. Every job is
  validated against the real files (target exists, signature found exactly once). If validation
  fails, the planner is re-asked once with the errors, as a fresh prompt.

  Output goes to codegen\drafts\plans\<name>-<timestamp>\:
    plan.json, NN-<id>.prompt.txt, NN-<id>.job.json
  Review it, then run:  codegen\run-plan.ps1 <that folder>

.EXAMPLE
  powershell -NoProfile -ExecutionPolicy Bypass -File codegen\plan.ps1 codegen\requests\findByContact.json
#>
param(
  [Parameter(Mandatory = $true, Position = 0)][string]$RequestFile,
  [int]$Retries = 1
)

$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
Set-Location $root
[Environment]::CurrentDirectory = $root
$utf8 = New-Object System.Text.UTF8Encoding($false)

function Resolve-RepoPath([string]$p) { if ([IO.Path]::IsPathRooted($p)) { $p } else { Join-Path $root $p } }
function Read-Text([string]$p) { [IO.File]::ReadAllText((Resolve-RepoPath $p), $utf8) }
function Write-Text([string]$p, [string]$t) { [IO.File]::WriteAllText((Resolve-RepoPath $p), $t, $utf8) }

# ---------- config ----------
$models = Read-Text 'codegen/models.json' | ConvertFrom-Json
$stage = $models.stages.plan
if (-not $stage) { throw "no 'plan' stage in codegen/models.json" }
$platform = $models.platforms.($stage.platform)
if (-not $platform) { throw "plan stage uses unknown platform '$($stage.platform)'" }

$headers = @{}
if ($platform.apiKeyEnv) {
  $tok = [Environment]::GetEnvironmentVariable($platform.apiKeyEnv)
  if ($tok) { $headers['Authorization'] = "Bearer $tok" }
}

# JSON schema for the plan. LM Studio enforces it via response_format; others just get it in the prompt.
$planSchema = @{
  type       = 'object'
  required   = @('jobs')
  properties = @{
    jobs = @{
      type  = 'array'
      items = @{
        type       = 'object'
        required   = @('id', 'target', 'signature', 'classContext', 'types', 'behavior', 'imports')
        properties = @{
          id           = @{ type = 'string' }
          target       = @{ type = 'string' }
          signature    = @{ type = 'string' }
          classContext = @{ type = 'string' }
          types        = @{ type = 'string' }
          behavior     = @{ type = 'array'; items = @{ type = 'string' } }
          imports      = @{ type = 'array'; items = @{ type = 'string' } }
        }
      }
    }
  }
}

function Invoke-Planner([string]$prompt) {
  $body = @{
    model       = $stage.model
    temperature = $stage.temperature
    stream      = $false
    messages    = @(@{ role = 'user'; content = $prompt })
  }
  if ($platform.kind -eq 'lmstudio') {
    $body.response_format = @{
      type        = 'json_schema'
      json_schema = @{ name = 'plan'; strict = $true; schema = $planSchema }
    }
  }
  $bytes = $utf8.GetBytes(($body | ConvertTo-Json -Depth 20))
  $r = Invoke-RestMethod -Uri ($platform.baseUrl.TrimEnd('/') + '/chat/completions') -Method Post `
    -Headers $headers -Body $bytes -ContentType 'application/json; charset=utf-8' `
    -TimeoutSec $stage.timeoutSec
  # LM Studio's reasoning split can misfire and leave content empty: fall back to reasoning_content.
  $message = $r.choices[0].message
  $content = [string]$message.content
  if (-not $content.Trim() -and ([string]$message.reasoning_content).Trim()) {
    Write-Host "note: reply content was empty; using reasoning_content instead"
    return [string]$message.reasoning_content
  }
  return $content
}

# Pull the JSON object out of a reply that may contain <think> blocks or markdown fences.
function Get-JsonText([string]$t) {
  # Strip only a LEADING <think>...</think>: "</think>" can be data inside the plan's strings.
  $t = $t.TrimStart()
  if ($t.StartsWith('<think>')) {
    $close = $t.IndexOf('</think>')
    if ($close -ge 0) { $t = $t.Substring($close + 8) }
  }
  $a = $t.IndexOf('{'); $b = $t.LastIndexOf('}')
  if ($a -lt 0 -or $b -le $a) { return $null }
  return $t.Substring($a, $b - $a + 1)
}

# Same whitespace-tolerant match the orchestrator uses to splice.
function Get-SignatureCount([string]$src, [string]$sig) {
  $pattern = (($sig.Trim() -split '\s+') | ForEach-Object { [regex]::Escape($_) }) -join '\s+'
  return [regex]::Matches($src, $pattern).Count
}

function Test-Plan($plan) {
  $errors = @()
  if (-not $plan.jobs -or @($plan.jobs).Count -eq 0) { return @('plan has no jobs') }
  $ids = @{}
  foreach ($j in @($plan.jobs)) {
    if ($ids.ContainsKey($j.id)) { $errors += "duplicate job id '$($j.id)'" }
    $ids[$j.id] = $true
    if ($j.id -notmatch '^[A-Za-z0-9_-]+$') { $errors += "job id '$($j.id)' must be letters, digits, _ or -" }
    $path = Join-Path $root $j.target
    if (-not (Test-Path -LiteralPath $path)) { $errors += "job '$($j.id)': target not found: $($j.target)"; continue }
    $n = Get-SignatureCount ([IO.File]::ReadAllText($path, $utf8)) $j.signature
    if ($n -ne 1) {
      $errors += "job '$($j.id)': signature '$($j.signature)' found $n times in $($j.target) (must be exactly 1; copy it verbatim, without annotations)"
    }
    if (@($j.behavior).Count -eq 0) { $errors += "job '$($j.id)': behavior is empty" }
  }
  return $errors
}

# ---------- build the planner prompt ----------
$req = Read-Text $RequestFile | ConvertFrom-Json
$ctx = New-Object System.Text.StringBuilder
foreach ($p in @($req.context)) {
  [void]$ctx.Append("`n----- FILE: $p -----`n").Append((Read-Text $p)).Append("`n")
}

$basePrompt = @"
You are the planning stage of a code-generation pipeline for a Java 25 / Spring Boot codebase.

A separate small model (7B) will implement each method BODY you plan. It sees ONLY the job you
write: nothing else from the codebase. So each job must be fully self-contained.

Rules for jobs:
- One job per method body. Only plan methods whose signature ALREADY EXISTS in a target file
  shown below (stubs or methods to rewrite). Never plan new classes or new methods.
- "target": the file path exactly as shown in the FILE header.
- "signature": copied verbatim from the target file, without annotations, e.g.
  "public List<Opportunity> findByContact(ContactId contact)".
- "classContext": the class declaration line plus every field and private helper the body may use,
  with exact signatures.
- "types": exact signatures of every other type and method the body may call (records with their
  accessors like "ContactId: UUID value()", interfaces, static helpers, SQL table columns if
  relevant). Copy names exactly from the files. If it is not listed here, the small model must not use it.
- "behavior": short, ordered, concrete steps. Include guards, error handling, exact SQL, and what to
  return. Point at an existing method in the same class to mirror when there is one.
- "imports": fully qualified imports the body needs that the target file does not already have.
  Empty array if none.
- Order jobs so that a method's dependencies are implemented first.

Output ONLY a JSON object of this shape, no prose:
{ "jobs": [ { "id": "camelCaseId", "target": "...", "signature": "...", "classContext": "...",
  "types": "...", "behavior": ["..."], "imports": ["..."] } ] }

=== The ask ===
$($req.ask)

=== Codebase context ===
$($ctx.ToString())
"@

# ---------- run ----------
$name = [IO.Path]::GetFileNameWithoutExtension($RequestFile)
$outDir = "codegen/drafts/plans/$name-$(Get-Date -Format 'yyyyMMdd-HHmmss')"
New-Item -ItemType Directory -Force -Path (Join-Path $root $outDir) | Out-Null
Write-Host "plan: model=$($stage.model) @ $($platform.baseUrl)  (context files: $(@($req.context).Count))"

$prompt = $basePrompt
$plan = $null
for ($attempt = 0; $attempt -le $Retries; $attempt++) {
  Write-Host "attempt $($attempt + 1)/$($Retries + 1): calling planner..."
  $raw = Invoke-Planner $prompt
  Write-Text "$outDir/attempt-$attempt.prompt.txt" $prompt
  Write-Text "$outDir/attempt-$attempt.out.txt" $raw

  $errors = @()
  $jsonText = Get-JsonText $raw
  if (-not $jsonText) { $errors = @('reply contained no JSON object') }
  else {
    try { $plan = $jsonText | ConvertFrom-Json; $errors = Test-Plan $plan }
    catch { $errors = @("invalid JSON: $($_.Exception.Message)") }
  }
  if ($errors.Count -eq 0) { break }

  Write-Host "plan rejected:"; $errors | ForEach-Object { Write-Host "  - $_" }
  $plan = $null
  $prompt = $basePrompt + "`n`n=== Your previous plan was rejected ===`n" + $jsonText +
    "`n`n=== Validation errors ===`n" + ($errors -join "`n") + "`n`nReturn a corrected JSON plan only."
}
if (-not $plan) { Write-Host "FAILED: no valid plan. see $outDir"; exit 1 }

# ---------- write prompts + jobs for the code stage ----------
# Reuse the house rules from TEMPLATE.txt (everything before the first section header).
$template = Read-Text 'codegen/TEMPLATE.txt'
$rules = $template.Substring(0, $template.IndexOf('=== The class')).TrimEnd()

Write-Text "$outDir/plan.json" ($plan | ConvertTo-Json -Depth 10)
$i = 0
foreach ($j in @($plan.jobs)) {
  $i++
  $stem = '{0:D2}-{1}' -f $i, $j.id
  $steps = (@($j.behavior) | ForEach-Object { "- $_" }) -join "`n"
  $codePrompt = @"
$rules

=== The class this method belongs to (relevant fields) ===
$($j.classContext)

=== Types and methods you may use ===
$($j.types)

=== Contract ===
Implement:  $($j.signature)

Behavior:
$steps
"@
  Write-Text "$outDir/$stem.prompt.txt" $codePrompt
  $jobObj = [ordered]@{
    stage     = 'code'
    target    = $j.target
    signature = $j.signature
    prompt    = "$outDir/$stem.prompt.txt"
    imports   = @($j.imports | Where-Object { $_ })
  }
  Write-Text "$outDir/$stem.job.json" ($jobObj | ConvertTo-Json -Depth 5)
  Write-Host "  job $stem -> $($j.target)"
}

Write-Host "PLAN OK: $i job(s) in $outDir"
Write-Host "review the *.prompt.txt files, then run:"
Write-Host "  powershell -NoProfile -ExecutionPolicy Bypass -File codegen\run-plan.ps1 $outDir"

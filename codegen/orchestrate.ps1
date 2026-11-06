<#
.SYNOPSIS
  Small orchestrator: model writes one method body, this script does all the file work.

.DESCRIPTION
  For one job file it: builds a fresh prompt, calls the model stage's endpoint (OpenAI-compatible,
  so Ollama / llama.cpp / LM Studio all work), splices the body into the target method, runs
  gradlew compileJava, and on failure walks an escalation ladder:
    code stage x codeAttempts (retries get the compiler errors as a hint, never the failed code,
    which small models tend to copy) -> repair stage rewrites the job prompt -> code stage again.
  Every model call is a brand-new single-message request, so each attempt is a clean slate. On
  final failure (or any crash) the target file is restored to its original content. A prompt that
  led to success after repair is saved over the job's prompt file (original kept as *.orig).

  Auto-commit (default, off with -NoAutoCommit, and implied off by -NoCompile): before starting,
  the target file must have no uncommitted changes and the branch must not be main/master. After
  the job compiles, only the target file is committed, so each job is one reviewable/revertable
  commit. A failed job leaves no commit.

  Endpoints live in codegen\models.json: "platforms" are servers (base URL + optional token env
  var), "stages" pick a platform and a model id on it.

.EXAMPLE
  powershell -NoProfile -File codegen\orchestrate.ps1 codegen\jobs\readOpportunity.json
  powershell -NoProfile -File codegen\orchestrate.ps1 codegen\jobs\x.json -CodeAttempts 3 -RepairRounds 0
  powershell -NoProfile -File codegen\orchestrate.ps1 -ListModels
#>
param(
  [Parameter(Position = 0)][string]$JobFile,
  [int]$CodeAttempts = -1,   # -1 = use escalation.codeAttempts from models.json
  [int]$RepairRounds = -1,   # -1 = use escalation.repairRounds; 0 disables escalation
  [switch]$NoCompile,
  [switch]$NoAutoCommit,     # default: commit the target file after a job compiles
  [switch]$ListModels
)

$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
Set-Location $root
[Environment]::CurrentDirectory = $root
$utf8 = New-Object System.Text.UTF8Encoding($false)

function Resolve-RepoPath([string]$p) { if ([IO.Path]::IsPathRooted($p)) { $p } else { Join-Path $root $p } }
function Read-Text([string]$p) { [IO.File]::ReadAllText((Resolve-RepoPath $p), $utf8) }
function Write-Text([string]$p, [string]$t) { [IO.File]::WriteAllText((Resolve-RepoPath $p), $t, $utf8) }

# ---------- platforms ----------
# Token comes from an env var named in the config, never from the JSON itself.
function Get-Headers($platform) {
  $h = @{}
  if ($platform.apiKeyEnv) {
    $tok = [Environment]::GetEnvironmentVariable($platform.apiKeyEnv)
    if ($tok) { $h['Authorization'] = "Bearer $tok" }
  }
  return $h
}

# Both Ollama and LM Studio answer GET <baseUrl>/models (OpenAI-compatible) with { data: [{ id }] }.
function Get-PlatformModels($platform, [int]$timeoutSec = 10) {
  $r = Invoke-RestMethod -Uri ($platform.baseUrl.TrimEnd('/') + '/models') -Method Get `
    -Headers (Get-Headers $platform) -TimeoutSec $timeoutSec
  return @($r.data | ForEach-Object { [string]$_.id })
}

# Merge stage settings with its platform into one call config.
function Resolve-Stage($models, [string]$stageName) {
  $stage = $models.stages.$stageName
  if (-not $stage) { throw "stage '$stageName' not in codegen/models.json" }
  $platform = $models.platforms.($stage.platform)
  if (-not $platform) { throw "stage '$stageName' uses unknown platform '$($stage.platform)'" }
  return [pscustomobject]@{
    platformName = $stage.platform
    platform     = $platform
    baseUrl      = $platform.baseUrl
    model        = $stage.model
    temperature  = $stage.temperature
    timeoutSec   = $stage.timeoutSec
  }
}

# ---------- model call: one fresh message list per call ----------
function Invoke-Model($cfg, [string]$prompt) {
  $payload = @{
    model       = $cfg.model
    temperature = $cfg.temperature
    stream      = $false
    messages    = @(@{ role = 'user'; content = $prompt })
  } | ConvertTo-Json -Depth 6
  $bytes = $utf8.GetBytes($payload)
  $r = Invoke-RestMethod -Uri ($cfg.baseUrl.TrimEnd('/') + '/chat/completions') -Method Post `
    -Headers (Get-Headers $cfg.platform) -Body $bytes `
    -ContentType 'application/json; charset=utf-8' -TimeoutSec $cfg.timeoutSec
  return [string]$r.choices[0].message.content
}

function Remove-Fences([string]$t) {
  $lines = $t -split "`r?`n" | Where-Object { $_.Trim() -notmatch '^```' }
  return ($lines -join "`n").Trim("`n", "`r")
}

# ---------- java-aware brace matching (skips strings, chars, comments, text blocks) ----------
function Find-CloseBrace([string]$s, [int]$open) {
  $depth = 0
  $i = $open
  while ($i -lt $s.Length) {
    $c = $s[$i]
    $n = if ($i + 1 -lt $s.Length) { $s[$i + 1] } else { [char]0 }
    if ($c -eq '/' -and $n -eq '/') { $e = $s.IndexOf("`n", $i); if ($e -lt 0) { return -1 }; $i = $e; continue }
    if ($c -eq '/' -and $n -eq '*') { $e = $s.IndexOf('*/', $i + 2); if ($e -lt 0) { return -1 }; $i = $e + 2; continue }
    if ($c -eq '"' -and $s.Substring($i).StartsWith('"""')) {
      $j = $i + 3
      while ($j -lt $s.Length) {
        if ($s[$j] -eq '\') { $j += 2; continue }
        if ($s.Substring($j).StartsWith('"""')) { break }
        $j++
      }
      $i = $j + 3; continue
    }
    if ($c -eq '"' -or $c -eq "'") {
      $q = $c; $j = $i + 1
      while ($j -lt $s.Length -and $s[$j] -ne $q) { if ($s[$j] -eq '\') { $j++ }; $j++ }
      $i = $j + 1; continue
    }
    if ($c -eq '{') { $depth++ }
    elseif ($c -eq '}') { $depth--; if ($depth -eq 0) { return $i } }
    $i++
  }
  return -1
}

# Returns @{ Start; Open; Close } for the method whose signature matches (whitespace-tolerant).
function Find-Method([string]$src, [string]$sig) {
  $pattern = (($sig.Trim() -split '\s+') | ForEach-Object { [regex]::Escape($_) }) -join '\s+'
  $ms = [regex]::Matches($src, $pattern)
  if ($ms.Count -ne 1) { return $null }   # not found, or ambiguous
  $m = $ms[0]
  $open = $src.IndexOf('{', $m.Index + $m.Length)
  if ($open -lt 0) { return $null }
  $close = Find-CloseBrace $src $open
  if ($close -lt 0) { return $null }
  return @{ Start = $m.Index; Open = $open; Close = $close }
}

# If the model echoed the whole method, keep only the body between the outer braces.
function Get-Body([string]$out, [string]$sig) {
  $hit = Find-Method $out $sig
  if ($hit) { return $out.Substring($hit.Open + 1, $hit.Close - $hit.Open - 1).Trim("`n", "`r") }
  return $out
}

function Add-Imports([string]$src, [string[]]$imports, [string]$eol) {
  foreach ($imp in $imports) {
    if (-not $imp) { continue }
    $line = "import $imp;"
    if ($src.Contains($line)) { continue }
    $pk = [regex]::Match($src, '(?m)^package\s+[^;]+;[^\n]*\n')
    if ($pk.Success) { $at = $pk.Index + $pk.Length; $src = $src.Insert($at, $eol + $line + $eol) }
  }
  return $src
}

function Splice([string]$src, [string]$sig, [string]$body, [string[]]$imports) {
  $hit = Find-Method $src $sig
  if (-not $hit) { throw "signature not found exactly once in target: $sig" }
  $eol = if ($src.Contains("`r`n")) { "`r`n" } else { "`n" }
  $b = ($body -split "`r?`n") -join $eol
  # Closing brace gets the same indent as the signature line (2 or 4 spaces, whatever the file uses).
  $lineStart = $src.LastIndexOf("`n", [Math]::Max($hit.Start - 1, 0)) + 1
  $indent = [regex]::Match($src.Substring($lineStart), '^[ \t]*').Value
  $new = $src.Substring(0, $hit.Open + 1) + $eol + $b + $eol + $indent + $src.Substring($hit.Close)
  return (Add-Imports $new $imports $eol)
}

function Invoke-Compile {
  # Full path + explicit working dir, so it works no matter who launched this script.
  $psi = New-Object System.Diagnostics.ProcessStartInfo
  $psi.FileName = 'cmd.exe'
  # classes + testClasses: test sources must compile too, or a broken test body would be committed.
  # Run from the root, these tasks cover every Gradle project (app and codegen subproject).
  $psi.Arguments = '/c ""' + (Join-Path $root 'gradlew.bat') + '" classes testClasses --console=plain -q 2>&1"'
  $psi.WorkingDirectory = $root
  $psi.UseShellExecute = $false
  $psi.RedirectStandardOutput = $true
  $proc = [System.Diagnostics.Process]::Start($psi)
  $out = $proc.StandardOutput.ReadToEnd()
  $proc.WaitForExit()
  return @{ Ok = ($proc.ExitCode -eq 0); Output = $out }
}

# ---------- run ----------
$models = Read-Text 'codegen/models.json' | ConvertFrom-Json

if ($ListModels) {
  foreach ($p in $models.platforms.PSObject.Properties) {
    Write-Host "[$($p.Name)] $($p.Value.kind) $($p.Value.baseUrl)"
    try { Get-PlatformModels $p.Value | ForEach-Object { Write-Host "    $_" } }
    catch { Write-Host "    UNREACHABLE: $($_.Exception.Message)" }
  }
  exit 0
}
if (-not $JobFile) { throw 'usage: orchestrate.ps1 <job.json> [-Retries N] [-NoCompile] | -ListModels' }

$job = Read-Text $JobFile | ConvertFrom-Json
$cfg = Resolve-Stage $models $job.stage

# Fail fast if the server is down or does not have the model, before touching any file.
try { $available = Get-PlatformModels $cfg.platform }
catch { throw "platform '$($cfg.platformName)' unreachable at $($cfg.baseUrl): $($_.Exception.Message)" }
$known = $available | Where-Object { $_ -eq $cfg.model -or $_ -eq "$($cfg.model):latest" }
if (-not $known) {
  throw "model '$($cfg.model)' not on platform '$($cfg.platformName)'. available: $($available -join ', ')"
}

# ---------- auto-commit pre-flight: fail before any model call or file edit ----------
$autoCommit = -not ($NoAutoCommit -or $NoCompile)
if ($autoCommit) {
  $branch = (& git rev-parse --abbrev-ref HEAD | Out-String).Trim()
  if ($LASTEXITCODE -ne 0) { throw 'auto-commit: not a git repository (use -NoAutoCommit)' }
  if ($branch -in @('main', 'master')) {
    throw "auto-commit: refusing to commit on '$branch'. switch to a task branch, or use -NoAutoCommit"
  }
  $dirty = (& git status --porcelain -- $job.target | Out-String).Trim()
  if ($dirty) {
    throw "auto-commit: $($job.target) has uncommitted changes. commit or stash them first, or use -NoAutoCommit`n$dirty"
  }
}

$name = [IO.Path]::GetFileNameWithoutExtension($JobFile)
$runDir = "codegen/drafts/runs/$name-$(Get-Date -Format 'yyyyMMdd-HHmmss')"
New-Item -ItemType Directory -Force -Path (Join-Path $root $runDir) | Out-Null

$original = Read-Text $job.target
# Backup in case the run is killed before it can restore: copy this file back over the target.
Write-Text "$runDir/original.java.bak" $original
$fileName = [IO.Path]::GetFileName($job.target)

# ---------- escalation settings (CLI overrides config) ----------
$esc = $models.escalation
if ($CodeAttempts -lt 0) { $CodeAttempts = if ($esc -and $esc.codeAttempts) { [int]$esc.codeAttempts } else { 2 } }
if ($RepairRounds -lt 0) { $RepairRounds = if ($esc -and $null -ne $esc.repairRounds) { [int]$esc.repairRounds } else { 0 } }
if ($CodeAttempts -lt 1) { $CodeAttempts = 1 }
$repairMode = if ($esc -and $esc.repairMode) { [string]$esc.repairMode } else { 'prompt' }
if ($repairMode -ne 'prompt') { throw "escalation.repairMode '$repairMode' not supported yet (only 'prompt')" }
if ($RepairRounds -gt 0 -and -not $models.stages.repair) { throw "repairRounds > 0 but no 'repair' stage in models.json" }

# Keep each javac error line for this file plus the detail lines after it
# (source line, caret, "symbol: ...", "location: ..."), deduplicated.
function Get-FileErrors([string]$output) {
  $lines = $output -split "`r?`n"
  $picked = New-Object System.Collections.Generic.List[string]
  $seen = @{}
  for ($k = 0; $k -lt $lines.Count; $k++) {
    if ($lines[$k] -notmatch ([regex]::Escape($fileName) + ':\d+: error:')) { continue }
    $block = @($lines[$k].Trim())
    for ($m = $k + 1; $m -lt $lines.Count -and $m -le $k + 4; $m++) {
      if ($lines[$m] -match ':\d+: (error|warning):' -or $lines[$m] -match '^\s*\d+ errors?$') { break }
      $block += $lines[$m].Trim()
    }
    $text = $block -join "`n"
    if (-not $seen.ContainsKey($text.Trim())) { $seen[$text.Trim()] = $true; $picked.Add($text) }
  }
  $errs = $picked -join "`n"
  if (-not $errs) { $errs = $output }
  return $errs
}

# One code-stage attempt: call, splice onto the ORIGINAL (failed attempts never stack), compile.
function Invoke-CodeAttempt([string]$prompt, [string]$tag) {
  $raw = Invoke-Model $cfg $prompt
  Write-Text "$runDir/$tag.prompt.txt" $prompt
  Write-Text "$runDir/$tag.out.txt" $raw
  $clean = Remove-Fences $raw
  if ($clean -match 'NEEDS_CLARIFICATION') {
    return @{ Ok = $false; Clarify = $true; Body = $clean; Errs = $clean }
  }
  $body = Get-Body $clean $job.signature
  Write-Text $job.target (Splice $original $job.signature $body $job.imports)
  if ($NoCompile) { return @{ Ok = $true; Clarify = $false; Body = $body; Errs = '' } }
  $c = Invoke-Compile
  Write-Text "$runDir/$tag.compile.txt" $c.Output
  if ($c.Ok) { return @{ Ok = $true; Clarify = $false; Body = $body; Errs = '' } }
  return @{ Ok = $false; Clarify = $false; Body = $body; Errs = (Get-FileErrors $c.Output) }
}

# Retry prompt: the errors as a warning, WITHOUT the failed code (the 7B copies what it is shown).
function Get-HintPrompt([string]$jobPrompt, [string]$errs) {
  return $jobPrompt + "`n`n=== Warning: an earlier attempt at this method failed to compile ===`n" +
    $errs + "`n`nWrite the method so these errors cannot happen. Output ONLY the method implementation."
}

# Repair stage: the big model rewrites the JOB PROMPT (not the code) so the small model can succeed.
function Invoke-Repair([string]$jobPrompt, $last, [int]$round) {
  $rcfg = Resolve-Stage $models 'repair'
  $problem = if ($last.Clarify) { "The coder asked for clarification instead of writing code:`n$($last.Errs)" }
             else { "Its last attempt:`n$($last.Body)`n`nCompiler errors:`n$($last.Errs)" }
  $rp = @"
You are the repair stage of a code-generation pipeline. A small coding model (7B) was given the
JOB PROMPT below and could not produce a method that compiles. It sees ONLY the job prompt, nothing
else from the codebase.

Rewrite the job prompt so the small model gets it right on the next try:
- Keep the same structure, rules section, sections, and the exact "Implement:" signature.
- Fix the root cause: correct or add type signatures, qualify nested types (Outer.Inner),
  remove anything misleading, and make the failing step explicit. Short code expressions in the
  behavior steps are fine (e.g. "pass the lambda inline: ps -> ps.setString(1, uuid(x.value()))").
- Do not include the full method implementation.

Output ONLY the revised prompt between these two marker lines, nothing else:
=== REVISED PROMPT ===
...
=== END ===

=== JOB PROMPT ===
$jobPrompt

=== WHAT WENT WRONG ===
$problem
"@
  Write-Host "escalating to repair: model=$($rcfg.model) @ $($rcfg.baseUrl)"
  $raw = Invoke-Model $rcfg $rp
  Write-Text "$runDir/repair-$round.prompt.txt" $rp
  Write-Text "$runDir/repair-$round.out.txt" $raw
  # Reasoning may leak into the reply (sometimes without an opening <think>) and often quotes the
  # markers. "</think>" can also be DATA inside the answer (prompts about think-tag handling), so
  # never cut at it: strip only a leading <think>...</think> and take the LAST block.
  $t = $raw.TrimStart()
  if ($t.StartsWith('<think>')) {
    $close = $t.IndexOf('</think>')
    if ($close -ge 0) { $t = $t.Substring($close + 8) }
  }
  $ms = [regex]::Matches($t, '(?s)=== REVISED PROMPT ===\s*(.*?)\s*=== END ===')
  if ($ms.Count -eq 0) { Write-Host "repair reply had no REVISED PROMPT block"; return $null }
  $revised = $ms[$ms.Count - 1].Groups[1].Value.Trim()
  # Guard: the revision must still be a job prompt for the same method.
  $sigPattern = (($job.signature.Trim() -split '\s+') | ForEach-Object { [regex]::Escape($_) }) -join '\s+'
  if ($revised -notmatch '=== Contract ===' -or $revised -notmatch $sigPattern) {
    Write-Host "repair reply dropped the Contract section or the signature; ignoring it"
    return $null
  }
  Write-Text "$runDir/repair-$round.revised.txt" $revised
  return $revised
}

# ---------- the ladder ----------
$jobPrompt = Read-Text $job.prompt
$total = $CodeAttempts * ($RepairRounds + 1)
Write-Host "job=$name model=$($cfg.model) @ $($cfg.baseUrl)  (code x$CodeAttempts, repair rounds $RepairRounds)"

$succeeded = $false
$n = 0
try {
  for ($round = 0; $round -le $RepairRounds -and -not $succeeded; $round++) {
    $last = $null
    for ($a = 1; $a -le $CodeAttempts; $a++) {
      $n++
      $prompt = if ($last -and -not $last.Clarify) { Get-HintPrompt $jobPrompt $last.Errs } else { $jobPrompt }
      Write-Host "attempt $n/$total (round $round, code $a): calling model..."
      $last = Invoke-CodeAttempt $prompt "r$round-a$a"
      if ($last.Ok) { $succeeded = $true; break }
      if ($last.Clarify) { Write-Host "coder needs clarification: $($last.Errs)"; break }
      Write-Host "compile failed:`n$($last.Errs)"
    }
    if ($succeeded) { break }
    if ($round -ge $RepairRounds) { break }
    $revised = Invoke-Repair $jobPrompt $last $round
    if (-not $revised) { break }
    $jobPrompt = $revised
  }
}
finally {
  # Runs on success, failure, Ctrl+C, or an exception: never leave a broken splice behind.
  if (-not $succeeded) { Write-Text $job.target $original }
}

if ($succeeded) {
  if ($NoCompile) { Write-Host "spliced (compile skipped). run dir: $runDir"; exit 0 }
  # Save the prompt that worked, so re-runs and similar jobs start from the repaired version.
  if ($jobPrompt -ne (Read-Text $job.prompt)) {
    $orig = "$($job.prompt).orig"
    if (-not (Test-Path -LiteralPath (Resolve-RepoPath $orig))) { Write-Text $orig (Read-Text $job.prompt) }
    Write-Text $job.prompt $jobPrompt
    Write-Host "repaired prompt saved over $($job.prompt) (original: $orig)"
  }
  Write-Host "COMPILED OK after $n attempt(s). target updated: $($job.target). run dir: $runDir"

  if ($autoCommit) {
    # Commit ONLY the target (git commit <path> ignores anything else that happens to be staged).
    # Subject names the class and method, e.g. "PlanPrompts.planner": plan job numbers repeat.
    $className = [IO.Path]::GetFileNameWithoutExtension($job.target)
    $methodMatch = [regex]::Match($job.signature, '(\w+)\s*\(')
    $jobId = if ($methodMatch.Success) { "$className.$($methodMatch.Groups[1].Value)" } else { $name -replace '\.job$', '' }
    $planName = Split-Path (Split-Path (Resolve-RepoPath $JobFile) -Parent) -Leaf
    $suffix = if ($n -gt $CodeAttempts) { ', repaired' } else { '' }
    $subject = "codegen: $jobId ($n attempt(s)$suffix)"
    $bodyMsg = "Generated by codegen/orchestrate.ps1. Plan: $planName. Model: $($cfg.model). Run: $runDir"
    & git add -- $job.target
    if ($LASTEXITCODE -eq 0) { & git commit -q -m $subject -m $bodyMsg -- $job.target }
    if ($LASTEXITCODE -ne 0) {
      Write-Host "AUTO-COMMIT FAILED (code compiled and is left in place, uncommitted): $($job.target)"
      exit 5
    }
    $sha = (& git rev-parse --short HEAD | Out-String).Trim()
    Write-Host "committed $sha  $subject"
  }
  exit 0
}
if ($last -and $last.Clarify) {
  Write-Host "STOPPED: coder needs clarification. target restored. see $runDir"
  exit 3
}
Write-Host "FAILED after $n attempt(s). target restored. see $runDir"
exit 1

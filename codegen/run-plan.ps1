<#
.SYNOPSIS
  Code stage driver: runs every NN-*.job.json in a plan folder through orchestrate.ps1, in order.

.DESCRIPTION
  Stops at the first job that fails, so later jobs never build on a broken method. Each job is
  independent (fresh prompt, own compile check, file restored on failure). Re-run with -From NN
  to resume after fixing the failing job's prompt.

.EXAMPLE
  powershell -NoProfile -ExecutionPolicy Bypass -File codegen\run-plan.ps1 codegen\drafts\plans\findByContact-20261106-101500
  powershell -NoProfile -ExecutionPolicy Bypass -File codegen\run-plan.ps1 <folder> -From 3
#>
param(
  [Parameter(Mandatory = $true, Position = 0)][string]$PlanDir,
  [int]$From = 1,
  [switch]$NoAutoCommit   # default: each job that compiles becomes its own commit
)
# Attempts and escalation come from "escalation" in codegen\models.json.

$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
Set-Location $root

$jobs = Get-ChildItem -LiteralPath (Join-Path $root $PlanDir) -Filter '*.job.json' | Sort-Object Name
if (-not $jobs) { throw "no *.job.json files in $PlanDir" }

$done = 0
foreach ($f in $jobs) {
  $num = [int]($f.Name.Substring(0, 2))
  if ($num -lt $From) { continue }
  Write-Host "===== $($f.Name) ====="
  $jobArgs = @($f.FullName)
  if ($NoAutoCommit) { $jobArgs += '-NoAutoCommit' }
  & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'orchestrate.ps1') @jobArgs
  $code = $LASTEXITCODE
  if ($code -ne 0) {
    Write-Host "STOPPED at $($f.Name) (exit $code). $done job(s) succeeded before it."
    Write-Host "fix its prompt in $PlanDir, then resume with: -From $num"
    exit $code
  }
  $done++
}

if ($NoAutoCommit) {
  Write-Host "ALL DONE: $done job(s) compiled. next: gradlew.bat test, review the diff, commit."
} else {
  Write-Host "ALL DONE: $done job(s) compiled, one commit each. next: gradlew.bat test, review with git log -p."
}

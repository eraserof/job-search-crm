@echo off
setlocal
rem Generate one Java method body with the local model and print it fence-free, ready to paste.
rem It does NOT touch your source files: you paste the body into the method and let the IDE add imports.
rem
rem Usage:   codegen\gen.cmd <prompt-file> [model]
rem Example: codegen\gen.cmd codegen\drafts\opportunityService_advanceStage.txt
rem          codegen\gen.cmd codegen\drafts\foo.txt qwen2.5-coder:7b
rem
rem Default model is "coder-full-gpu-7b" (pinned in codegen\Modelfile: temp 0.2, num_ctx 8192).
rem Create it once with:  ollama create coder-full-gpu-7b -f codegen\Modelfile

if "%~1"=="" (
  echo usage: %~nx0 ^<prompt-file^> [model]
  echo see codegen\TEMPLATE.txt for the prompt format
  exit /b 2
)
set "PROMPT=%~1"
set "MODEL=%~2"
if "%MODEL%"=="" set "MODEL=coder-full-gpu-7b"

if not exist "%PROMPT%" (
  echo ERROR: prompt file not found: %PROMPT%
  exit /b 1
)

rem Save raw output next to the prompt, then print a fence-stripped copy to the screen.
set "RAW=%PROMPT%.out.java"
ollama run %MODEL% < "%PROMPT%" > "%RAW%"

echo.
echo ===================== generated method (review every line) =====================
rem Strip lines that are exactly a markdown fence (```), print the rest.
powershell -NoProfile -Command "Get-Content -LiteralPath '%RAW%' | Where-Object { $_.Trim() -ne '```' -and $_.Trim() -ne '```java' } | ForEach-Object { $_ }"
echo ================================================================================
echo raw output saved to %RAW%
echo next: paste into the method, run 'organize imports' in your IDE, compile, review, commit.
endlocal

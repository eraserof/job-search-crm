@echo off
rem cg: run the codegen tool, rebuilding it first so it is never stale.
rem   .\cg models
rem   .\cg plan codegen\requests\x.json
rem   .\cg run codegen\drafts\plans\x-20261107-074929 [--from N] [--no-auto-commit]
rem   .\cg job codegen\jobs\x.json [--code-attempts N] [--repair-rounds N] [--no-compile]
rem
rem The installed launcher (codegen\build\install\codegen\bin\codegen.bat) is a snapshot, and the
rem pipeline edits codegen's own sources, so this runs installDist first. When nothing changed,
rem Gradle finds the build up to date and it costs only a few seconds.
rem Runs from the repo root so relative paths behave the same from any folder.
setlocal
set "ROOT=%~dp0"
set "BUILD_LOG=%TEMP%\cg-installdist.log"
pushd "%ROOT%"
call "%ROOT%gradlew.bat" :codegen:installDist -q --console=plain > "%BUILD_LOG%" 2>&1
if errorlevel 1 (
  echo cg: building codegen failed:
  type "%BUILD_LOG%"
  popd
  exit /b 2
)
call "%ROOT%codegen\build\install\codegen\bin\codegen.bat" %*
set "RC=%ERRORLEVEL%"
popd
exit /b %RC%

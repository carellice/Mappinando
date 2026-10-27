@echo off
setlocal
cd /d "%~dp0"
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\Build-Apk.ps1" %*
set "buildExitCode=%errorlevel%"
echo.
if not defined MAPPINANDO_NO_PAUSE pause
exit /b %buildExitCode%

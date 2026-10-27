@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\Publish-Release.ps1" %*
set "MAPPINANDO_RELEASE_EXIT=%ERRORLEVEL%"
if not "%MAPPINANDO_NO_PAUSE%"=="1" pause
exit /b %MAPPINANDO_RELEASE_EXIT%

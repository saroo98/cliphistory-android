@echo off
setlocal
cd /d "%~dp0"
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\build-windows.ps1"
set "CODE=%errorlevel%"
echo.
if not "%CODE%"=="0" echo BUILD DID NOT COMPLETE. Read the error above and build-windows.log.
pause
exit /b %CODE%

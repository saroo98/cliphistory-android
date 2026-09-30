@echo off
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\gradle.ps1" %*
exit /b %errorlevel%

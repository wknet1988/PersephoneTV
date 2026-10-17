@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0.."
echo.
echo  Persephone TV - build APK
echo.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0build-apk.ps1" %*
echo.
pause

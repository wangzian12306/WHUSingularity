@echo off
setlocal
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0run-k6-order-gateway-fraud-ramp.ps1" %*

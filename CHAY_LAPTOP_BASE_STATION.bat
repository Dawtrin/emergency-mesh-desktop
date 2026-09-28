@echo off
setlocal
cd /d "%~dp0"
title [BASE STATION] Tram Chi Huy Trung Tam - Port 8888
powershell.exe -NoLogo -NoExit -ExecutionPolicy Bypass -File "%~dp0scripts\windows\start-base.ps1" -ListenPort 8888 -RelayPort 8002 %*

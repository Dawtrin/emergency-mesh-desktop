@echo off
setlocal
cd /d "%~dp0"
title [VICTIM] Node A Victim - Port 8001
powershell.exe -NoLogo -NoExit -ExecutionPolicy Bypass -File "%~dp0scripts\windows\start-node.ps1" -Mode VICTIM -NodeId NODE_A_VICTIM -ListenPort 8001 -NextHopPort 8002 %*

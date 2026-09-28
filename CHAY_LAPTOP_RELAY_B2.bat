@echo off
setlocal
cd /d "%~dp0"
title [RELAY B2] Node B2 Relay - Port 8003
powershell.exe -NoLogo -NoExit -ExecutionPolicy Bypass -File "%~dp0scripts\windows\start-node.ps1" -Mode RELAY -NodeId NODE_B2_RELAY -ListenPort 8003 -NextHopPort 8888 -VictimId NODE_A_VICTIM -VictimPort 8001 %*

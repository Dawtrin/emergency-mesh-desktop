@echo off
setlocal
cd /d "%~dp0"
title [RELAY B1] Node B1 Relay - Port 8002
powershell.exe -NoLogo -NoExit -ExecutionPolicy Bypass -File "%~dp0scripts\windows\start-node.ps1" -Mode RELAY -NodeId NODE_B1_RELAY -ListenPort 8002 -NextHopPort 8888 -VictimId NODE_A_VICTIM -VictimPort 8001 %*

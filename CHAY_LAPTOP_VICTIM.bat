@echo off
chcp 65001 >nul
cd /d "%~dp0"
title [VICTIM] Node A Victim - Gui Tin Hieu SOS
echo ================================================================
echo   [VICTIM NODE A] EMERGENCY MESH RESCUE - GUI TIN HIEU SOS
echo ================================================================
echo.

REM Hien thi IP LAN
for /f "tokens=2 delims=:" %%a in ('ipconfig ^| findstr /i "IPv4"') do (
    echo   [IP LAN] May nay: %%a
)
echo.
echo ================================================================
echo.

set RELAY_IP=localhost
set /p RELAY_IP="Nhap IP may chay Relay (Enter = localhost): "

echo.
echo   [--^>] Victim se gui SOS den Relay tai: %RELAY_IP%:8002
echo ================================================================
echo.

java -jar target\MeshNodeClient-jar-with-dependencies.jar --mode VICTIM --port 8001 --host %RELAY_IP% --next-hop 8002 --id NODE_A_VICTIM
pause

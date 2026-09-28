@echo off
chcp 65001 >nul
cd /d "%~dp0"
title [RELAY B2] Node B2 Relay - Port 8003 (Load Balancing)
echo ================================================================
echo   [RELAY NODE B2] EMERGENCY MESH RESCUE - LOAD BALANCING
echo ================================================================
echo.

REM Hien thi IP LAN
for /f "tokens=2 delims=:" %%a in ('ipconfig ^| findstr /i "IPv4"') do (
    echo   [IP LAN] May nay: %%a
)
echo   [PORT]   Lang nghe: 8003
echo.
echo   [INFO] Cho victim biet IP nay neu muon di qua B2.
echo   [INFO] Nho tat Windows Firewall hoac cho phep port 8003!
echo ================================================================
echo.

set BASE_IP=localhost
set /p BASE_IP="Nhap IP may Base Station (Enter = localhost): "

echo.
echo   [--^>] Relay B2 se chuyen tiep len Base Station tai: %BASE_IP%:8888
echo ================================================================
echo.

java -jar target\MeshNodeClient-jar-with-dependencies.jar --mode RELAY --port 8003 --host %BASE_IP% --next-hop 8888 --id NODE_B2_RELAY
pause

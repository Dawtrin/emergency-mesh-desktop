@echo off
chcp 65001 >nul
cd /d "%~dp0"
title [RELAY B1] Node B1 Relay - Port 8002
echo ================================================================
echo   [RELAY NODE B1] EMERGENCY MESH RESCUE - TIEP SONG TRUNG GIAN
echo ================================================================
echo.

REM Hien thi IP LAN
for /f "tokens=2 delims=:" %%a in ('ipconfig ^| findstr /i "IPv4"') do (
    echo   [IP LAN] May nay: %%a
)
echo.
echo   [INFO] Cho victim biet IP nay de ket noi.
echo   [INFO] Nho tat Windows Firewall hoac cho phep port 8002!
echo ================================================================
echo.

set BASE_IP=localhost
set /p BASE_IP="Nhap IP may Base Station (Enter = localhost): "

echo.
echo   [--^>] Relay se chuyen tiep len Base Station tai: %BASE_IP%:8888
echo ================================================================
echo.

java -jar target\MeshNodeClient-jar-with-dependencies.jar --mode RELAY --port 8002 --host %BASE_IP% --next-hop 8888 --id NODE_B1_RELAY
pause

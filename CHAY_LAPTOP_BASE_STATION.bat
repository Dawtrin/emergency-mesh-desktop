@echo off
chcp 65001 >nul
cd /d "%~dp0"
title [BASE STATION] Tram Chi Huy Trung Tam - Port 8888
echo ================================================================
echo   [BASE STATION] EMERGENCY MESH RESCUE - TRAM CHI HUY
echo ================================================================
echo.

REM Hien thi IP LAN
for /f "tokens=2 delims=:" %%a in ('ipconfig ^| findstr /i "IPv4"') do (
    echo   [IP LAN] May nay: %%a
)
echo   [PORT]   Lang nghe: 8888
echo.
echo   [INFO] Cho cac may Relay / Victim biet IP nay de ket noi.
echo   [INFO] Nho tat Windows Firewall hoac cho phep port 8888!
echo ================================================================
echo.

java -jar target\BaseStationServer-jar-with-dependencies.jar --mode BASE_STATION --port 8888
pause

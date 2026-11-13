@echo off
chcp 65001 >nul 2>&1
cd /d "%~dp0\.."
title 📦 ĐÓNG GÓI TACTICAL C2 RESCUE — mvn clean package
color 0E

echo.
echo ===============================================================================
echo   📦 ĐÓNG GÓI FAT JAR — NASA C2 RESCUE (TACTICAL WEBC2)
echo   Dự án: Emergency Mesh Rescue — Dual-Engine Hybrid (TCP 8888 + Web 8080)
echo ===============================================================================
echo.

:: Tìm Maven trong hệ thống hoặc IntelliJ IDEA
set MVN_CMD=mvn
where mvn >nul 2>&1
if %ERRORLEVEL% neq 0 (
    if exist "C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.1\plugins\maven-plugin\lib\maven3\bin\mvn.cmd" (
        set "MVN_CMD=C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.1\plugins\maven-plugin\lib\maven3\bin\mvn.cmd"
    ) else if exist "C:\Program Files\JetBrains\IntelliJ IDEA 2024.2.1\plugins\maven-plugin\lib\maven3\bin\mvn.cmd" (
        set "MVN_CMD=C:\Program Files\JetBrains\IntelliJ IDEA 2024.2.1\plugins\maven-plugin\lib\maven3\bin\mvn.cmd"
    )
)

echo [*] Sử dụng Maven: %MVN_CMD%
echo [*] Bắt đầu đóng gói: mvn clean package -DskipTests...
echo.

"%MVN_CMD%" clean package -DskipTests

if %ERRORLEVEL% equ 0 (
    echo.
    echo ===============================================================================
    echo   ✅ ĐÓNG GÓI THÀNH CÔNG!
    echo   File xuất xưởng: target\TacticalWebC2.jar
    echo   Sẵn sàng chạy với: CHAY_TRAM_CHI_HUY.bat
    echo ===============================================================================
) else (
    echo.
    echo ===============================================================================
    echo   ❌ ĐÓNG GÓI THẤT BẠI! Vui lòng kiểm tra lỗi biên dịch phía trên.
    echo ===============================================================================
)

echo.
pause

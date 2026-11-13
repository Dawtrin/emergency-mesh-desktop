@echo off
chcp 65001 >nul 2>&1
cd /d "%~dp0\.."
title TRẠM CHỈ HUY TÁC CHIẾN - NASA C2 RESCUE (EMERGENCY MESH RESCUE)
color 0B

echo.
echo ===============================================================================
echo   🛰️  TRẠM CHỈ HUY TÁC CHIẾN CHUẨN NASA MISSION CONTROL (TACTICAL C2)
echo   Dự án: Emergency Mesh Rescue - Dual-Engine Hybrid (TCP 8888 + Web 8080)
echo   Kiến trúc: Spring Boot 3 + SQLite WAL + STOMP WebSocket + Leaflet GIS
echo ===============================================================================
echo.

REM -----------------------------------------------------------------------------
REM BƯỚC 1: Dò tìm địa chỉ IP mạng nội bộ (Wi-Fi / LAN / Hotspot)
REM -----------------------------------------------------------------------------
set LOCAL_IP=127.0.0.1
for /f "tokens=4" %%a in ('route print ^| findstr 0.0.0.0 ^| findstr /v "0.0.0.0/0"') do (
    if not "%%a"=="" if not "%%a"=="127.0.0.1" set LOCAL_IP=%%a
)

echo [✓] ĐỊA CHỈ IP NỘI BỘ DÃ CHIẾN : %LOCAL_IP%
echo [✓] CỔNG GIAO TIẾP MESH ANDROID  : 8888 (TCP Socket Inbound)
echo [✓] CỔNG ĐIỀU PHỐI DISPATCH       : 8889 (TCP Socket Outbound)
echo [✓] CỔNG ĐIỀU HÀNH WEB DASHBOARD  : 8080 (HTTP / WebSocket STOMP)
echo.
echo +-------------------------------------------------------------------------+
echo ^| 📱 TRUY CẬP TRẠM CHỈ HUY TỪ IPAD / ĐIỆN THOẠI BAN CHỈ HUY DÃ CHIẾN:     ^|
echo ^|    👉 http://%LOCAL_IP%:8080                                           ^|
echo ^|    👉 http://localhost:8080 (Trực tiếp trên Laptop này)                 ^|
echo +-------------------------------------------------------------------------+
echo.

REM -----------------------------------------------------------------------------
REM BƯỚC 2: Kiểm tra bản đóng gói Fat JAR (TacticalWebC2.jar)
REM -----------------------------------------------------------------------------
if not exist "target\TacticalWebC2.jar" (
    echo [!] Chưa tìm thấy target\TacticalWebC2.jar. Đang tự động đóng gói...
    call "%~dp0DONG_GOI_TACTICAL_C2.bat"
)

if not exist "target\TacticalWebC2.jar" (
    echo [ERROR] Không tìm thấy target\TacticalWebC2.jar sau khi đóng gói.
    pause
    exit /b 1
)

REM -----------------------------------------------------------------------------
REM BƯỚC 3: Mở trình duyệt Web C2 Dashboard
REM -----------------------------------------------------------------------------
echo [INFO] Đang chuẩn bị mở Web Dashboard...
start "" cmd /c "timeout /t 3 /nobreak >nul && (if exist ""C:\Program Files\Google\Chrome\Application\chrome.exe"" (""C:\Program Files\Google\Chrome\Application\chrome.exe"" --start-fullscreen http://localhost:8080) else if exist ""C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"" (""C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"" --start-fullscreen http://localhost:8080) else (start http://localhost:8080))"

REM -----------------------------------------------------------------------------
REM BƯỚC 4: Khởi động Dual-Engine Hybrid Server
REM -----------------------------------------------------------------------------
echo.
echo ===============================================================================
echo [EXEC] KHỞI ĐỘNG FAT JAR: target\TacticalWebC2.jar
echo ===============================================================================
echo.
echo ⚡ KỊCH BẢN DIỄN TẬP BẢO VỆ ĐỒ ÁN (5 PHÚT):
echo    1. Nhìn màn hình Fullscreen: 8 phân hệ C2 theo chuẩn NASA Mission Control
echo    2. Quét mã QR trên màn hình bằng iPad/Điện thoại
echo    3. Bấm [⚡ BƠM DIỄN TẬP] trên thanh Header (hoặc nút bên Trang 7)
echo    4. Còi hú khẩn cấp vang lên + Viền màn hình chớp đỏ Strobe Alarm
echo    5. Bản đồ COP tự động định tuyến đường cấp cứu đến Bệnh viện
echo    6. Bấm [⚡ ĐIỀU PHỐI] -^> Lệnh bắn về điện thoại -^> Nhận ACK
echo    7. Chuyển đổi 8 tab mượt mà 60-120 FPS
echo ===============================================================================
echo.

java -jar target\TacticalWebC2.jar

pause

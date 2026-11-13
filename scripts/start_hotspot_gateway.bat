@echo off
chcp 65001 >nul 2>&1
title 📡 TRẠM CHỈ HUY EMERGENCY MESH RESCUE - HOTSPOT GATEWAY
color 0A

echo.
echo ==============================================================
echo  📡  TRẠM CHỈ HUY EMERGENCY MESH RESCUE - HOTSPOT GATEWAY
echo ==============================================================
echo.
echo  [*] Đang quét địa chỉ IP trên các card mạng...
echo.

:: Tìm IP của card mạng Wi-Fi / Hotspot
set FOUND_IP=0

:: Ưu tiên IP của Mobile Hotspot (192.168.137.x)
for /f "tokens=2 delims=:" %%a in ('ipconfig ^| findstr /i "192.168.137"') do (
    set IP_ADDR=%%a
    set IP_ADDR=!IP_ADDR: =!
    set FOUND_IP=1
)

:: Nếu không có Hotspot, tìm IP Wi-Fi thường
if "%FOUND_IP%"=="0" (
    for /f "tokens=2 delims=:" %%a in ('ipconfig ^| findstr /r "192\.168\.[0-9]*\.[0-9]*"') do (
        set IP_ADDR=%%a
        set FOUND_IP=1
    )
)

:: Nếu không tìm được IP nào, dùng ipconfig để hiển thị toàn bộ
if "%FOUND_IP%"=="0" (
    echo  [!] CẢNH BÁO: Không tìm thấy IP mạng nội bộ!
    echo  [!] Hãy kiểm tra Wi-Fi hoặc bật Mobile Hotspot.
    echo.
    echo  --- DANH SÁCH CARD MẠNG ---
    ipconfig | findstr /i "IPv4"
    echo.
    goto :END
)

:: Dọn khoảng trắng đầu
for /f "tokens=*" %%b in ("%IP_ADDR%") do set IP_CLEAN=%%b

echo  ==============================================================
echo  ✅ PHÁT HIỆN IP TRẠM CHỈ HUY THÀNH CÔNG!
echo  ==============================================================
echo.
echo  ┌──────────────────────────────────────────────────────┐
echo  │  🛰️  IP Trạm Chỉ Huy  :  %IP_CLEAN%
echo  │  📡  Cổng lắng nghe SOS (TCP IN)  :  8888
echo  │  📤  Cổng phát Lệnh (TCP OUT)     :  8889
echo  │  🌐  Web Dashboard (HTTP)          :  8080
echo  │  🔌  WebSocket STOMP               :  8080/ws
echo  └──────────────────────────────────────────────────────┘
echo.
echo  ==============================================================
echo  📱 HƯỚNG DẪN KẾT NỐI TỪ APP ANDROID:
echo  ==============================================================
echo.
echo  1. Bật Wi-Fi Hotspot trên Laptop (hoặc kết nối cùng Wi-Fi)
echo  2. Kết nối điện thoại Android vào Hotspot của Laptop
echo  3. Mở App Emergency Mesh Rescue trên Android
echo  4. Vào mục ⚙️ CÀI ĐẶT (Settings)
echo  5. Nhập IP Trạm Chỉ Huy: %IP_CLEAN%
echo  6. Nhập Port: 8888
echo  7. Bấm LƯU và THỬ KẾT NỐI
echo.
echo  ==============================================================
echo  🌐 TRUY CẬP WEB DASHBOARD:
echo  ==============================================================
echo.
echo  Mở trình duyệt và truy cập:
echo  👉 http://%IP_CLEAN%:8080
echo  👉 http://localhost:8080 (trên máy này)
echo.
echo  ==============================================================
echo  ⚡ LỆNH NHANH (dành cho diễn tập):
echo  ==============================================================
echo.
echo  Bơm dữ liệu demo:
echo  👉 curl -X POST http://localhost:8080/api/simulator/inject -H "Content-Type: application/json" -d "{\"scenario\":\"FULL_DEMO\"}"
echo.

:END
echo  ==============================================================
echo  [*] Nhấn phím bất kỳ để đóng cửa sổ này...
echo  ==============================================================
pause >nul

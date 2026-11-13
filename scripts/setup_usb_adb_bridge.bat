@echo off
chcp 65001 >nul 2>&1
title 🔌 USB ADB BRIDGE — Emergency Mesh Rescue
color 0B

echo.
echo ==============================================================
echo  🔌 CẦU NỐI USB ADB BRIDGE — EMERGENCY MESH RESCUE
echo ==============================================================
echo.
echo  Dùng khi phòng thi kín sóng hoặc Wi-Fi bị nhiễu.
echo  Kết nối qua cáp USB cho độ trễ 0ms.
echo.
echo  [*] Đang kiểm tra ADB...
echo.

:: Kiểm tra ADB có sẵn không
where adb >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo  [!] CẢNH BÁO: ADB chưa được cài đặt hoặc chưa có trong PATH!
    echo.
    echo  Hướng dẫn cài ADB:
    echo  1. Tải Android Platform Tools từ:
    echo     https://developer.android.com/tools/releases/platform-tools
    echo  2. Giải nén vào thư mục, ví dụ: C:\platform-tools
    echo  3. Thêm đường dẫn vào biến môi trường PATH
    echo  4. Chạy lại script này
    echo.
    goto :END
)

:: Kiểm tra thiết bị kết nối
echo  [*] Đang tìm thiết bị Android kết nối USB...
echo.
adb devices 2>nul | findstr /v "List" | findstr /v "^$"
if %ERRORLEVEL% neq 0 (
    echo  [!] Không tìm thấy thiết bị Android nào!
    echo.
    echo  Kiểm tra:
    echo  1. Đã bật USB Debugging trên Android?
    echo     (Cài đặt → Tuỳ chọn nhà phát triển → USB Debugging)
    echo  2. Cáp USB đã cắm chắc chưa?
    echo  3. Đã cho phép kết nối trên popup Android chưa?
    echo.
    goto :END
)

echo.
echo  [OK] Đã phát hiện thiết bị Android!
echo.

:: Thiết lập port forwarding
echo  [*] Đang thiết lập cầu nối ADB...
echo.

echo  [1/3] Forward TCP 8888 (Android → Desktop: SOS Data)
adb forward tcp:8888 tcp:8888
if %ERRORLEVEL% equ 0 (
    echo        ✅ OK - Port 8888 forward thành công
) else (
    echo        ❌ THẤT BẠI - Không forward được port 8888
)

echo.
echo  [2/3] Reverse TCP 8889 (Desktop → Android: Dispatch CMD)
adb reverse tcp:8889 tcp:8889
if %ERRORLEVEL% equ 0 (
    echo        ✅ OK - Port 8889 reverse thành công
) else (
    echo        ❌ THẤT BẠI - Không reverse được port 8889
)

echo.
echo  [3/3] Reverse TCP 8080 (Desktop → Android: Web Dashboard)
adb reverse tcp:8080 tcp:8080
if %ERRORLEVEL% equ 0 (
    echo        ✅ OK - Port 8080 reverse thành công
) else (
    echo        ❌ THẤT BẠI - Không reverse được port 8080
)

echo.
echo  ==============================================================
echo  ✅ CẦU NỐI ADB HOÀN TẤT!
echo  ==============================================================
echo.
echo  ┌──────────────────────────────────────────────────────┐
echo  │  📱 Trên App Android, đặt IP là: 127.0.0.1         │
echo  │  📡 Port SOS: 8888                                  │
echo  │  📤 Port Dispatch: 8889                             │
echo  │  🌐 Web Dashboard: http://127.0.0.1:8080            │
echo  └──────────────────────────────────────────────────────┘
echo.
echo  Lưu ý: Cầu nối ADB sẽ tự ngắt khi rút cáp USB.
echo  Cần chạy lại script này sau mỗi lần kết nối lại cáp.
echo.

:END
echo  ==============================================================
echo  [*] Nhấn phím bất kỳ để đóng cửa sổ này...
echo  ==============================================================
pause >nul

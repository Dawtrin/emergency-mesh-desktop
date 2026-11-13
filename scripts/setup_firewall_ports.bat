@echo off
chcp 65001 >nul 2>&1
title 🛡️ CẤU HÌNH FIREWALL — Emergency Mesh Rescue
color 0C

echo.
echo ==============================================================
echo  🛡️ MỞ PORT FIREWALL CHO EMERGENCY MESH RESCUE
echo ==============================================================
echo.
echo  Script này cần chạy với quyền Administrator!
echo  (Click phải → Run as Administrator)
echo.

:: Kiểm tra quyền Admin
net session >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo  ❌ KHÔNG CÓ QUYỀN ADMINISTRATOR!
    echo.
    echo  Cách chạy:
    echo  1. Click phải vào file này
    echo  2. Chọn "Run as administrator"
    echo.
    goto :END
)

echo  [*] Đang thêm rule Firewall...
echo.

echo  [1/4] Mở TCP 8888 (Mesh SOS Inbound)...
netsh advfirewall firewall add rule name="EmergencyMeshRescue_TCP8888_IN" dir=in action=allow protocol=TCP localport=8888 >nul 2>&1
echo        ✅ Done

echo  [2/4] Mở TCP 8889 (Dispatch Outbound)...
netsh advfirewall firewall add rule name="EmergencyMeshRescue_TCP8889_IN" dir=in action=allow protocol=TCP localport=8889 >nul 2>&1
echo        ✅ Done

echo  [3/4] Mở TCP 8080 (Web Dashboard)...
netsh advfirewall firewall add rule name="EmergencyMeshRescue_TCP8080_IN" dir=in action=allow protocol=TCP localport=8080 >nul 2>&1
echo        ✅ Done

echo  [4/4] Mở UDP cho mDNS/Discovery...
netsh advfirewall firewall add rule name="EmergencyMeshRescue_UDP5353" dir=in action=allow protocol=UDP localport=5353 >nul 2>&1
echo        ✅ Done

echo.
echo  ==============================================================
echo  ✅ ĐÃ MỞ TẤT CẢ PORT CẦN THIẾT!
echo  ==============================================================
echo.
echo  ┌──────────────────────────────────────────────────────┐
echo  │  TCP 8888 IN  — Nhận SOS từ Android Mesh            │
echo  │  TCP 8889 IN  — Phát lệnh Dispatch                  │
echo  │  TCP 8080 IN  — Web Dashboard HTTP + WebSocket       │
echo  │  UDP 5353 IN  — mDNS Discovery                      │
echo  └──────────────────────────────────────────────────────┘
echo.
echo  Để gỡ bỏ rule sau khi demo xong:
echo  netsh advfirewall firewall delete rule name="EmergencyMeshRescue_TCP8888_IN"
echo  netsh advfirewall firewall delete rule name="EmergencyMeshRescue_TCP8889_IN"
echo  netsh advfirewall firewall delete rule name="EmergencyMeshRescue_TCP8080_IN"
echo  netsh advfirewall firewall delete rule name="EmergencyMeshRescue_UDP5353"
echo.

:END
echo  ==============================================================
echo  [*] Nhấn phím bất kỳ để đóng cửa sổ này...
echo  ==============================================================
pause >nul

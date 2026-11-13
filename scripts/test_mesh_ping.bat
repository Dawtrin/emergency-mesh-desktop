@echo off
chcp 65001 >nul 2>&1
title 📡 MESH PING TEST — Emergency Mesh Rescue
color 0B

echo.
echo ==============================================================
echo  📡 KIỂM TRA THÔNG MẠNG MESH (MESH PING TEST)
echo ==============================================================
echo.

set /p TARGET_IP="  Nhập IP máy cần ping (mặc định 127.0.0.1): "
if "%TARGET_IP%"=="" set TARGET_IP=127.0.0.1

echo.
echo  [1/4] Ping ICMP tới %TARGET_IP%...
ping -n 2 -w 1000 %TARGET_IP% | findstr "Reply TTL time"
if %ERRORLEVEL% equ 0 (
    echo        ✅ ICMP Ping OK
) else (
    echo        ⚠️  ICMP Ping thất bại (có thể bị Firewall chặn, vẫn thử TCP)
)

echo.
echo  [2/4] Kiểm tra TCP 8888 (Mesh SOS)...
powershell -NoProfile -Command ^
  "try { $c = New-Object System.Net.Sockets.TcpClient('%TARGET_IP%', 8888); $c.Close(); Write-Host '       ✅ TCP 8888 OK — Server Mesh đang lắng nghe' -ForegroundColor Green } catch { Write-Host '       ❌ TCP 8888 ĐÓNG — Server chưa chạy hoặc bị chặn' -ForegroundColor Red }"

echo.
echo  [3/4] Kiểm tra TCP 8080 (Web Dashboard)...
powershell -NoProfile -Command ^
  "try { $c = New-Object System.Net.Sockets.TcpClient('%TARGET_IP%', 8080); $c.Close(); Write-Host '       ✅ TCP 8080 OK — Web Dashboard sẵn sàng' -ForegroundColor Green } catch { Write-Host '       ❌ TCP 8080 ĐÓNG — Spring Boot chưa khởi động' -ForegroundColor Red }"

echo.
echo  [4/4] Kiểm tra HTTP GET /api/victims/stats...
powershell -NoProfile -Command ^
  "try { $r = Invoke-RestMethod -Uri 'http://%TARGET_IP%:8080/api/victims/stats' -TimeoutSec 3; Write-Host '       ✅ REST API OK — Total:' $r.total '/ Resolved:' $r.resolved -ForegroundColor Green } catch { Write-Host '       ❌ REST API thất bại' -ForegroundColor Red }"

echo.
echo  ==============================================================
echo  [*] Nhấn phím bất kỳ để đóng cửa sổ này...
echo  ==============================================================
pause >nul

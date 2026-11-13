@echo off
chcp 65001 >nul 2>&1
title 🔬 TEST E2E MESH PACKET — Emergency Mesh Rescue
color 0E

echo.
echo ==============================================================
echo  🔬 TEST END-TO-END GÓI TIN MESH — EMERGENCY MESH RESCUE
echo ==============================================================
echo.
echo  Gửi 1 gói SOS_DATA mẫu chuẩn JSON qua TCP 8888
echo  để kiểm tra Server Desktop có nhận, hú còi, và vẽ bản đồ.
echo.
echo  [*] Đang kiểm tra PowerShell...
echo.

:: Tạo gói JSON chuẩn MeshPacket
set TIMESTAMP=%TIME:~0,2%%TIME:~3,2%%TIME:~6,2%

echo  ==============================================================
echo  📡 GỬI GÓI TIN QUA TCP 8888 (Trực tiếp Socket)
echo  ==============================================================
echo.

:: Sử dụng PowerShell để gửi TCP socket (không cần curl)
powershell -NoProfile -Command ^
  "$json = @{" ^
  "  packetId = [guid]::NewGuid().ToString();" ^
  "  packetType = 'SOS_DATA';" ^
  "  sourceNodeId = 'TEST_E2E_NODE';" ^
  "  destinationNodeId = 'BASE_STATION';" ^
  "  senderHopId = 'TEST_E2E_NODE';" ^
  "  ttl = 5;" ^
  "  hopCount = 0;" ^
  "  timestamp = [DateTimeOffset]::Now.ToUnixTimeMilliseconds();" ^
  "  payload = @{" ^
  "    senderName = 'Nguyễn Văn Test E2E';" ^
  "    alertType = 'FLOOD_TRAPPED';" ^
  "    message = 'Test End-to-End từ script bat - Kiểm tra liên tầng Mảng 3';" ^
  "    victimCount = 2;" ^
  "    severity = 'CRITICAL';" ^
  "    phoneNumber = '0905123456';" ^
  "    bloodType = 'AB-';" ^
  "    medicalNotes = 'Test packet from setup script';" ^
  "    location = @{ latitude = 16.0612; longitude = 108.2134 }" ^
  "  };" ^
  "  routeHistory = @('TEST_E2E_NODE');" ^
  "  checksum = 'e2e_test_checksum_placeholder'" ^
  "} | ConvertTo-Json -Depth 5 -Compress;" ^
  "try {" ^
  "  $client = New-Object System.Net.Sockets.TcpClient('127.0.0.1', 8888);" ^
  "  $stream = $client.GetStream();" ^
  "  $writer = New-Object System.IO.StreamWriter($stream, [System.Text.Encoding]::UTF8);" ^
  "  $writer.WriteLine($json);" ^
  "  $writer.Flush();" ^
  "  $writer.Close();" ^
  "  $client.Close();" ^
  "  Write-Host '  ✅ GỬI THÀNH CÔNG qua TCP 8888!' -ForegroundColor Green;" ^
  "  Write-Host '';" ^
  "  Write-Host '  Nội dung gói tin:' -ForegroundColor Cyan;" ^
  "  Write-Host $json;" ^
  "} catch {" ^
  "  Write-Host '  ❌ KHÔNG KẾT NỐI ĐƯỢC TCP 8888!' -ForegroundColor Red;" ^
  "  Write-Host '  Lỗi:' $_.Exception.Message;" ^
  "  Write-Host '';" ^
  "  Write-Host '  Kiểm tra:' -ForegroundColor Yellow;" ^
  "  Write-Host '  1. Đã chạy CHAY_TRAM_CHI_HUY.bat chưa?';" ^
  "  Write-Host '  2. Port 8888 có bị Firewall chặn không?';" ^
  "  Write-Host '  3. Thử: netstat -an | findstr 8888';" ^
  "}"

echo.
echo  ==============================================================
echo  🌐 GỬI GÓI TIN QUA HTTP REST API (Simulator)
echo  ==============================================================
echo.

:: Gửi thêm qua HTTP REST API để test cả luồng Web
powershell -NoProfile -Command ^
  "try {" ^
  "  $body = @{ scenario = 'RARE_BLOOD' } | ConvertTo-Json;" ^
  "  $r = Invoke-RestMethod -Uri 'http://localhost:8080/api/simulator/inject' -Method POST -Body $body -ContentType 'application/json';" ^
  "  Write-Host '  ✅ HTTP REST API OK!' -ForegroundColor Green;" ^
  "  Write-Host '  Response:' ($r | ConvertTo-Json -Compress);" ^
  "} catch {" ^
  "  Write-Host '  ❌ HTTP REST API thất bại!' -ForegroundColor Red;" ^
  "  Write-Host '  Lỗi:' $_.Exception.Message;" ^
  "}"

echo.
echo  ==============================================================
echo  📋 KẾT QUẢ KIỂM TRA:
echo  ==============================================================
echo.
echo  Nếu cả 2 phương thức thành công:
echo  ✅ Mở http://localhost:8080 → kiểm tra:
echo     - KPI card "TỔNG SỰ CỐ" tăng lên
echo     - Bảng Priority Feed hiện tên "Nguyễn Văn Test E2E"
echo     - Bản đồ có marker đỏ tại [16.0612, 108.2134]
echo     - Còi hú vang lên (nếu chưa tắt tiếng)
echo     - Console sniffer hiện gói tin mới
echo.

echo  ==============================================================
echo  [*] Nhấn phím bất kỳ để đóng cửa sổ này...
echo  ==============================================================
pause >nul

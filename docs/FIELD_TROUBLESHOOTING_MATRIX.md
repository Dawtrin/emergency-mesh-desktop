# 🔧 SỔ TAY KHẮC PHỤC SỰ CỐ THỰC ĐỊA (FIELD TROUBLESHOOTING MATRIX)

> **Dự án:** Emergency Mesh Rescue — Mảng 3 Integration  
> **Mục đích:** Xử lý nhanh mọi sự cố khi demo/bảo vệ đồ án trong vòng 30 giây.

---

## 📋 MA TRẬN KHẮC PHỤC NHANH

### 🔴 SỰ CỐ MẠNG & KẾT NỐI

| # | Triệu chứng | Nguyên nhân | Cách khắc phục (< 30s) |
|:---:|:---|:---|:---|
| 1 | **App Android báo "Không kết nối được Server"** | Sai IP hoặc Server chưa chạy | 1. Chạy `start_hotspot_gateway.bat` để xem IP đúng<br>2. Kiểm tra Server: `CHAY_TRAM_CHI_HUY.bat` đang chạy?<br>3. Nhập lại IP vào App Android |
| 2 | **TCP 8888 bị Firewall Windows chặn** | Windows Defender Firewall | Chạy CMD Admin:<br>`netsh advfirewall firewall add rule name="MeshRescue8888" dir=in action=allow protocol=TCP localport=8888`<br>`netsh advfirewall firewall add rule name="MeshRescue8889" dir=in action=allow protocol=TCP localport=8889`<br>`netsh advfirewall firewall add rule name="MeshRescue8080" dir=in action=allow protocol=TCP localport=8080` |
| 3 | **Wi-Fi Hotspot không phát được** | Windows chưa bật tính năng | Cài đặt → Network → Mobile Hotspot → Bật ON<br>Hoặc: `netsh wlan set hostednetwork mode=allow ssid=MeshRescue key=12345678` rồi `netsh wlan start hostednetwork` |
| 4 | **IP thay đổi sau khi bật/tắt Hotspot** | DHCP cấp IP mới | Chạy lại `start_hotspot_gateway.bat` → lấy IP mới → cập nhật trên App Android |
| 5 | **Android không thấy Hotspot laptop** | Wi-Fi band 5GHz vs 2.4GHz | Đổi Hotspot sang 2.4GHz trong Settings → Mobile Hotspot → Band |
| 6 | **Kết nối Wi-Fi nhưng ping không thông** | Client Isolation trên router | Chuyển sang Hotspot trực tiếp từ laptop (không qua router trường) |

### 🟡 SỰ CỐ APPLICATION

| # | Triệu chứng | Nguyên nhân | Cách khắc phục (< 30s) |
|:---:|:---|:---|:---|
| 7 | **Server Java báo "Address already in use: 8888"** | Port đang bị tiến trình khác chiếm | CMD: `netstat -ano | findstr :8888` → `taskkill /F /PID <pid>` |
| 8 | **Web Dashboard trắng / không hiện giao diện** | Browser cache file cũ | `Ctrl + Shift + R` (Hard Refresh) hoặc mở Incognito |
| 9 | **Biểu đồ / Bản đồ không hiện** | Chưa bơm dữ liệu demo | Bấm **⚡ BƠM DIỄN TẬP** trên header hoặc chạy `test_e2e_mesh_packet.bat` |
| 10 | **Còi hú không kêu** | Browser chưa cho phép Audio | Click bất kỳ vào trang trước → browser unlock AudioContext<br>Hoặc kiểm tra nút 🔊 CÒI BÁO ĐỘNG có đang BẬT không |
| 11 | **SQLite lock "database is locked"** | Nhiều write đồng thời | Restart server (tắt rồi bật lại). WAL mode sẽ tự recovery |
| 12 | **WebSocket disconnect liên tục** | Proxy/Antivirus chặn WS | Tắt Antivirus tạm thời hoặc dùng kết nối Hotspot trực tiếp |

### 🟢 SỰ CỐ KHI DEMO TRƯỚC HỘI ĐỒNG

| # | Triệu chứng | Nguyên nhân | Cách khắc phục (< 30s) |
|:---:|:---|:---|:---|
| 13 | **Phòng thi kín sóng, Wi-Fi không dùng được** | Phòng che chắn tín hiệu | Dùng **cáp USB ADB**: chạy `setup_usb_adb_bridge.bat` → IP trên Android đặt `127.0.0.1` |
| 14 | **2 điện thoại cùng phát SOS trùng nhau** | Duplicate packet | Hệ thống tự xử lý! SeenPacketCache + SHA-256 sẽ khử trùng lặp |
| 15 | **Demo xong, muốn reset dữ liệu sạch** | Dữ liệu cũ còn trong SQLite | Xóa file `rescue_c2.db` → Restart server. Database tự tạo lại |
| 16 | **Máy chiếu/TV hiện Dashboard nhỏ** | Responsive chưa fit | Bấm `Ctrl + -` để zoom out, hoặc `F11` full screen |
| 17 | **QR Code trên Dashboard scan không được** | URL sai localhost | Chạy `start_hotspot_gateway.bat` → dùng IP thật (không phải localhost) |

---

## ⚡ CHECKLIST TRƯỚC KHI VÀO PHÒNG BẢO VỆ ĐỒ ÁN

```
□ 1. Laptop đã sạc đầy pin (hoặc cắm nguồn)
□ 2. Hotspot đã bật, ghi nhớ IP (chạy start_hotspot_gateway.bat)
□ 3. Firewall đã mở port 8888, 8889, 8080
□ 4. CHAY_TRAM_CHI_HUY.bat đã chạy, Dashboard hiện OK
□ 5. Cáp USB ADB đã chuẩn bị sẵn (backup)
□ 6. setup_usb_adb_bridge.bat đã test thành công
□ 7. 2 điện thoại Android đã cài App, đã nhập IP đúng
□ 8. test_e2e_mesh_packet.bat đã chạy thành công
□ 9. Audio: bấm 1 lần vào Dashboard để unlock
□ 10. Ctrl+Shift+R trên browser để đảm bảo UI mới nhất
```

---

## 📱 KỊCH BẢN PHÒNG THI — PHÂN VAI 3 NGƯỜI

### Phút 00:00 – 01:00: Thành viên 1 (Trưởng Trạm)
```
1. Mở laptop, chạy CHAY_TRAM_CHI_HUY.bat
2. Mở Chrome → http://localhost:8080
3. Giới thiệu: "Đây là Trạm Chỉ Huy Dã Chiến C2..."
4. Chỉ vào Dashboard: KPI = 0, Bản đồ trống, Server READY
```

### Phút 01:00 – 02:00: Thành viên 2 (Relay Node B)
```
1. Đứng ở cửa phòng thi, cầm điện thoại B
2. Mở App → Bật nút TIẾP SÓNG (RELAY)
3. Nói: "Trạm trung chuyển đã kích hoạt..."
4. Dashboard hiện: Node B xanh ONLINE, Heartbeat tick
```

### Phút 02:00 – 03:00: Thành viên 3 (Nạn nhân Node A)
```
1. Đi ra hành lang, cầm điện thoại A
2. Nhấn giữ SOS 1.5 giây → Chọn Lũ lụt, 3 người, O+
3. App rung nhịp tim, Flash nháy SOS Morse, còi hú
4. Nói: "Tín hiệu SOS đang truyền qua mạng Mesh..."
```

### Phút 03:00 – 04:00: Hiệu ứng kịch tính
```
→ Gói tin: Phone A → Wi-Fi → Phone B (Relay) → TCP 8888 → Laptop
→ Dashboard: CÒI HÚ VANG! Viền đỏ strobe! KPI nhảy!
→ Bản đồ: Marker đỏ xuất hiện vị trí hành lang
→ Bảng Priority: Tên nạn nhân + Máu O+ + Lũ lụt
→ Trạm Trưởng nói: "Đã nhận tín hiệu, bắt đầu điều phối!"
```

### Phút 04:00 – 05:00: Trạm Trưởng điều phối
```
1. Bấm ⚡ ĐIỀU PHỐI trên bảng Priority Feed
2. Nhập: "Ca-nô số 3 và bác sĩ xuất kích"
3. Bấm XÁC NHẬN
4. Phone A ngoài hành lang nhận popup lệnh!
5. Kết luận: "Toàn bộ luồng REQ-ACQ-REL đã chứng minh!"
```

---

## 🔑 LỆNH NHANH KHẨN CẤP (COPY-PASTE)

```powershell
# Mở port Firewall (chạy CMD Admin)
netsh advfirewall firewall add rule name="MeshRescue" dir=in action=allow protocol=TCP localport=8888,8889,8080

# Kiểm tra port đang mở
netstat -an | findstr "8888 8889 8080"

# Tìm IP hiện tại
ipconfig | findstr "IPv4"

# Kill process chiếm port
netstat -ano | findstr :8888
taskkill /F /PID <số_PID>

# ADB port forwarding
adb forward tcp:8888 tcp:8888
adb reverse tcp:8889 tcp:8889

# Reset database
del rescue_c2.db rescue_c2.db-shm rescue_c2.db-wal

# Bơm dữ liệu test qua HTTP
curl -X POST http://localhost:8080/api/simulator/inject -H "Content-Type: application/json" -d "{\"scenario\":\"FULL_DEMO\"}"
```

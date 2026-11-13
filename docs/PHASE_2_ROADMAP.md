# 🚀 KẾ HOẠCH TRIỂN KHAI GIAI ĐOẠN 2 (PHASE 2 ROADMAP)
## EMERGENCY MESH RESCUE: ANDROID APP & ENHANCED C2 SERVER

---

## 📌 TỔNG QUAN MỤC TIÊU GIAI ĐOẠN 2
Chuyển hóa toàn diện từ mô hình mô phỏng phòng thí nghiệm (Phase 1 Desktop Simulator) sang **Hệ thống cứu nạn thực địa hoàn chỉnh (Phase 2 Real-world System)**:
1. **Ứng dụng Di động Android (Kotlin):** Chạy trên smartphone người dân/nạn nhân, tự kết nối ngang hàng (P2P), lấy tọa độ GPS vệ tinh thực tế.
2. **Nâng cấp Trạm Chỉ Huy Server (Command & Control Desktop):** Bổ sung Cơ sở dữ liệu SQLite/H2 lưu trữ vĩnh viễn, nâng cấp bản đồ GIS vẽ đường đi đa bước nhảy và quản lý trạng thái cứu nạn chuẩn mực.
3. **Đáp ứng hoàn hảo tiêu chí Đề số 53 (Định tuyến & Giám sát) và Đề số 54 (Hệ phân tán 3 trạng thái REQ - ACQ - REL).**

---

## 🗺️ KIẾN TRÚC TỔNG THỂ PHASE 2

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                              KIẾN TRÚC HỆ THỐNG GIAI ĐOẠN 2                            │
├────────────────────────────────────────────────────────┬───────────────────────────────┤
│          📱 TẦNG THIẾT BỊ DI ĐỘNG (ANDROID)            │   🏛️ TẦNG CHỈ HUY DÃ CHIẾN    │
│                                                        │       (DESKTOP C2 SERVER)     │
│   ┌────────────────────┐      ┌────────────────────┐   │    ┌────────────────────────┐ │
│   │  [NODE A: VICTIM]  │      │  [NODE B: RELAY]   │   │    │ [BASE STATION SERVER]  │ │
│   │  • UI Phát SOS     │      │  • Auto Relay ngầm │   │    │ • JavaFX C2 Dashboard  │ │
│   │  • GPS Phần cứng   │      │  • P2P Wi-Fi/BLE   │   │    │ • SQLite/H2 Database   │ │
│   │  • Core Routing    │      │  • Core Routing    │   │    │ • Leaflet Route Path   │ │
│   └─────────┬──────────┘      └─────────┬──────────┘   │    │ • Multi-client Manager │ │
│             │                           │              │    └───────────▲────────────┘ │
│             └────── Wi-Fi P2P / BLE ────┴──────────────┼────────────────┘              │
│                       (Không Internet)                 │     Wi-Fi Hotspot / Gateway   │
└────────────────────────────────────────────────────────┴───────────────────────────────┘
```

---

## 📦 4 MẢNG TRIỂN KHAI CHI TIẾT

### 📱 MẢNG 1: XÂY DỰNG ỨNG DỤNG ANDROID (CLIENT & RELAY NODE)

| Hạng mục | Mô tả kỹ thuật chi tiết |
| :--- | :--- |
| **1. Ngôn ngữ & Công nghệ** | • **Kotlin**, Android SDK (minSdk 26, targetSdk 34).<br>• Giao diện: Jetpack Compose hoặc XML Views (Material 3 Modern Dark Mode). |
| **2. Tầng mạng P2P Ngoại tuyến** | • **Wi-Fi Direct (`WifiP2pManager`)**: Tự động dò tìm (Peer Discovery) và bắt tay thiết lập nhóm (Group Formation) không cần mật khẩu.<br>• **TCP Socket Client/Server trên Android**: Mở cổng cục bộ trao đổi gói tin JSON. |
| **3. Định vị phần cứng (Hardware GPS)** | • Sử dụng `FusedLocationProviderClient` và `LocationManager`.<br>• Lấy trực tiếp tọa độ (Latitude, Longitude) từ chip GPS vệ tinh độc lập, không phụ thuộc vào mạng 4G/Wi-Fi. |
| **4. Tái sử dụng Tầng Core Phase 1** | • Tái sử dụng 100% logic: `MeshPacket` (POJO/Data Class), `ChecksumUtil` (SHA-256), `SeenPacketCache` (khử trùng lặp), `RoutingEngine` (Controlled Flooding). |
| **5. Dịch vụ Chạy ngầm (Background Relay)** | • `Foreground Service` với thông báo trên thanh trạng thái (Notification bar).<br>• Giữ CPU tỉnh (WakeLock) để tự động tiếp sóng gói tin ngay cả khi người dùng tắt màn hình hoặc đang chạy ứng dụng khác. |
| **6. Giao diện Người dùng Mobile** | • **Màn hình SOS khẩn cấp:** Nút bấm to, rung phản hồi, chọn loại nạn (Lũ lụt, Y tế, Sạt lở).<br>• **Thẻ Nhận Lệnh Điều Phối:** Pop-up thông báo to rõ khi nhận được lệnh chỉ đạo từ Trạm chỉ huy. |

---

### 🏛️ MẢNG 2: NÂNG CẤP TRẠM CHỈ HUY SERVER DESKTOP (C2 ENHANCEMENTS)

| Hạng mục | Mô tả kỹ thuật chi tiết |
| :--- | :--- |
| **1. Cơ sở Dữ liệu Lưu trữ (Persistence)** | • Tích hợp **SQLite** (hoặc **H2 Database**) nhúng nhẹ, không cần cài đặt server DB ngoài.<br>• Bảng dữ liệu: `victims` (danh sách nạn nhân), `sos_packets` (lịch sử gói tin), `dispatch_logs` (lịch sử lệnh chỉ huy), `nodes` (danh sách trạm tiếp sóng). |
| **2. Bản đồ Tác chiến Nâng cao (Advanced GIS)** | • **Vẽ đường truyền gói tin (Route Polyline):** Kẻ đường thẳng/mũi tên nối các điểm: `Nạn nhân A` $\rightarrow$ `Relay B` $\rightarrow$ `Base Station`.<br>• **Vùng nhiệt cứu hộ (Heatmap):** Nhóm các cụm nạn nhân mật độ cao thành vùng đỏ cảnh báo.<br>• **Bộ lọc tác chiến:** Lọc nhanh danh sách nạn nhân theo Mức độ (CRITICAL/HIGH), Loại nạn, hoặc Tình trạng xử lý. |
| **3. Quản lý Vòng đời Cứu nạn (Đề 54)** | • Ánh xạ 3 trạng thái phân tán:<br>  1. `PENDING` (Mới nhận SOS - tương đương REQ).<br>  2. `DISPATCHED` (Đang điều phối lực lượng - tương đương ACQ).<br>  3. `RESOLVED` (Đã cứu xong / Giải phóng tài nguyên - tương đương REL). |
| **4. Thống kê Tải Mạng & Băng thông (Đề 53)** | • Biểu đồ thống kê số lượng gói tin đi qua từng node relay theo thời gian.<br>• Giám sát tỷ lệ khử trùng lặp (`Duplicate Drop Rate`) để đánh giá hiệu năng thuật toán. |

---

### 🌐 MẢNG 3: CƠ CHẾ KẾT NỐI LIÊN TẦNG (ANDROID $\longleftrightarrow$ BASE STATION DESKTOP)

| Kịch bản kết nối | Cách thức hoạt động thực tế |
| :--- | :--- |
| **Kịch bản 1: Wi-Fi Hotspot Dã chiến** | • Laptop Trạm chỉ huy (hoặc một bộ phát di động chạy pin) phát một mạng Wi-Fi nội bộ (không cắm Internet).<br>• Các điện thoại Android tự động kết nối vào Wi-Fi này và gửi gói tin SOS đến địa chỉ IP của Laptop tại cổng `8888`. |
| **Kịch bản 2: USB / Bluetooth Gateway** | • Một điện thoại Android đóng vai trò "Trạm thu sóng biên" (Edge Gateway), kết nối qua cáp USB (ADB Forward / USB Tethering) hoặc Bluetooth PAN đổ trực tiếp luồng gói tin vào ứng dụng Desktop. |
| **Kịch bản 3: Cloud Gateway (Tùy chọn mở rộng)** | • Khi Trạm chỉ huy di chuyển đến vùng có sóng 4G/Vệ tinh trở lại, hệ thống tự động đồng bộ CSDL lên Web Dashboard phục vụ Ban Chỉ huy cứu nạn cấp Tỉnh/Thành phố. |

---

### 📑 MẢNG 4: BỘ TÀI LIỆU BÁO CÁO & KỊCH BẢN THỰC ĐỊA

1. **Báo cáo Đồ án Toàn diện (Word/PDF):**
   - Cơ sở lý thuyết mạng Mesh ad-hoc (MANET) và hệ phân tán.
   - Thiết kế chi tiết kiến trúc phân tầng (Clean Architecture).
   - Đánh giá hiệu năng giải thuật và kết quả đo đạc thực nghiệm.
2. **Kịch bản Live Demo Thực địa (Live Demonstration):**
   - Người 1: Cầm điện thoại Android đi ra sân/ngoài hành lang bấm nút SOS.
   - Người 2: Đứng ở cửa phòng tiếp sóng (Relay).
   - Người 3: Ngồi tại bàn thuyết trình với Laptop Base Station $\rightarrow$ Còi hú, bản đồ hiển thị đường truyền từ sân trường vào phòng thi, phát lệnh chỉ đạo ngược ra điện thoại.

---

## 📅 LỘ TRÌNH THỰC HIỆN THEO TUẦN (TIMELINE)

```
┌──────────────┬────────────────────────────────────────────────────────────────┐
│ Giai đoạn    │ Nhiệm vụ trọng tâm                                             │
├──────────────┼────────────────────────────────────────────────────────────────┤
│ Tuần 1       │ • Nâng cấp Server Desktop: Thêm SQLite Database + Route Polyline│
│              │ • Khởi tạo Project Android Studio (Kotlin) + Dựng UI SOS Form   │
├──────────────┼────────────────────────────────────────────────────────────────┤
│ Tuần 2       │ • Tích hợp GPS vệ tinh phần cứng trên Android                   │
│              │ • Port toàn bộ Core Model, Checksum, RoutingEngine sang Kotlin │
│              │ • Cài đặt Wi-Fi Direct P2P / Local TCP Socket trên Android     │
├──────────────┼────────────────────────────────────────────────────────────────┤
│ Tuần 3       │ • Cài đặt Background Foreground Service cho Relay Node Android  │
│              │ • Kết nối thử nghiệm Android ↔ Android ↔ Desktop Server        │
│              │ • Hoàn thiện quy trình 3 bước REQ → ACQ → RESOLVED             │
├──────────────┼────────────────────────────────────────────────────────────────┤
│ Tuần 4       │ • Test thực địa cự ly ngoài trời, đo độ trễ và tỷ lệ mất gói   │
│              │ • Hoàn thiện Báo cáo Đồ án, Slide thuyết trình và Video Demo   │
└──────────────┴────────────────────────────────────────────────────────────────┘
```

---
*Tài liệu được thiết lập làm kim chỉ nam triển khai Giai đoạn 2 cho nhóm đồ án.*

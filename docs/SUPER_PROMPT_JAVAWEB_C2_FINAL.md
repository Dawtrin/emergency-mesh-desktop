# 🛰️ SIÊU PROMPT: TRẠM CHỈ HUY TÁC CHIẾN CHUẨN NASA MISSION CONTROL & LIÊN THÔNG LIÊN NGÀNH (AEROSPACE TACTICAL C2 & INTER-AGENCY PLATFORM)
> **Dự án:** Emergency Mesh Rescue — Tactical Web Command & Control (C2) Center
> **Ngôn ngữ Thiết kế:** NASA Mission Control & Deep Space Avionics Design System — chi tiết đầy đủ token màu/typography/component tại file đi kèm **`ART_DIRECTION_NASA_C2.md`** (bắt buộc đọc song song với file này khi code Frontend)
> **Kiến trúc:** Dual-Engine Hybrid — **TCP Socket 8888** (nhận gói tin Mesh Android, inbound) + **TCP Socket 8889** (gửi lệnh Dispatch ngược ra mesh, outbound) + **HTTP/WebSocket 8080** (Web C2 Dashboard)
> **Cổng Điều Phối Liên Ngành:** **115** (Cấp Cứu Y Tế/Bệnh Viện) • **114** (Cứu Nạn Cứu Hộ/Ca-nô PCCC) • **113** (Công An/An Ninh/Phong Tỏa)
> **Động cơ Bản đồ:** Leaflet.js + plugin AntPath/Heat, phong cách Dark Tactical, chạy thẳng trong browser (không cần bridge Java↔JS)
> **Độ Sẵn Sàng Thực Chiến:** Mã QR tác chiến kết nối nhanh, trình khởi động 1-Click `CHAY_TRAM_CHI_HUY.bat`, chống mất dữ liệu khi mất điện (SQLite WAL Mode)
> **Nguyên lý hoạt động:** 100% NGOẠI TUYẾN (OFFLINE FIRST), hỗ trợ ĐA MÀN HÌNH CHỈ HUY (Laptop, iPad, điện thoại) cùng truy cập qua Wi-Fi nội bộ
> **Chuẩn học thuật:** Đề tài số 53 (Đo tải & Giám sát thông lượng IP) & Đề tài số 54 (Hệ phân tán 3 trạng thái REQ - ACQ - REL)

---

## 🎯 1. VAI TRÒ & TIÊU CHUẨN THIẾT KẾ
Bạn là **Lead Aerospace Avionics & Tactical C2 Systems Architect**:
* **Tuyệt đối KHÔNG thiết kế kiểu Web phổ thông / AI generic rẻ tiền** — mọi chi tiết thị giác tuân thủ 100% file `ART_DIRECTION_NASA_C2.md` (token màu, chamfered corner, glass console, typography, component spec). File này chỉ mô tả **kiến trúc & hành vi hệ thống**, không lặp lại phần thị giác.
* **Chuyên môn Hệ thống Phân tán & Mạng:** Socket TCP Non-blocking, Broadcast Storm Mitigation, SHA-256, Source Routing Polyline, hiện thực Đề 53 & Đề 54 như mô tả ở phần mở đầu.
* **Quy chuẩn Code:** Không sơ sài, không Mock/Giả lập, không để lại `TODO`, 100% tương thích JSON với App Android Mảng 1 (xem hợp đồng dữ liệu ở mục 9).

---

## 💡 2. KIẾN TRÚC LAI HAI TẦNG (DUAL-ENGINE C2)

```
┌────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│   📱 [ĐIỆN THOẠI NẠN NHÂN A] ──(Gói SOS_DATA - REQ)──► 📱 [ĐIỆN THOẠI RELAY B] (Lọc trùng SHA-256)     │
│                                                                 │                                      │
│                                                                 │ TCP Socket Port 8888 (INBOUND)        │
│                                                                 ▼                                      │
│   💻 [LAPTOP CHỈ HUY - SPRING BOOT DÃ CHIẾN (OFFLINE 100%)]                                             │
│       ├── MeshTcpServer (8888): Lắng nghe & xử lý qua RoutingEngine                                    │
│       ├── MeshTcpClient (8889): Gửi DISPATCH_CMD ngược ra mesh (OUTBOUND, kết nối riêng tới relay gần │
│       │      nhất theo route_history đảo ngược của packet gốc)                                         │
│       ├── SQLite (WAL Mode): Lưu trữ vĩnh viễn, chống mất dữ liệu khi sập nguồn                        │
│       ├── InterAgencyGateway: Cầu nối Y tế 115 - Cứu nạn 114 - Công an 113                             │
│       ├── TacticalQrBridge: Tự sinh mã QR theo IP mạng nội bộ                                          │
│       └── WebSocket Broker (Port 8080): Bắn sự kiện Real-time trong ~0.05s                             │
│                 │                                                                                      │
│                 ├───────────────────────────────┬───────────────────────────────┐                     │
│                 ▼                               ▼                               ▼                     │
│         [ MÀN HÌNH LAPTOP ]             [ MÁY TÍNH BẢNG IPAD ]         [ ĐIỆN THOẠI TRƯỞNG ]          │
│         http://localhost:8080           http://192.168.x.x:8080        http://192.168.x.x:8080        │
└────────────────────────────────────────────────────────────────────────────────────────────────────────┘
```

> **Vì sao cần 2 cổng TCP riêng (8888/8889):** giữ tách biệt luồng nhận (server thụ động, nhiều kết nối đồng thời từ các Relay khác nhau) khỏi luồng gửi lệnh (client chủ động, kết nối ngắn hạn theo yêu cầu). Tách port giúp debug/log rõ ràng luồng nào là SOS đến, luồng nào là lệnh đi, tránh deadlock khi cùng lúc vừa đọc vừa ghi trên 1 socket duy nhất.

---

## 🎛️ 3. THANH ĐIỀU HƯỚNG & 8 PHÂN HỆ TÁC CHIẾN

```
┌────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│ 🔴 NASA C2 RESCUE │ [🏠 TRANG CHỦ] [🗺️ BẢN ĐỒ COP] [📊 ĐO TẢI NOC] [🏥 LIÊN NGÀNH] [🚑 Y TẾ/TRIAGE]     │
│                   │                                    [📡 TOPOLOGY] [📜 BÁO CÁO] [⚙️ CẤU HÌNH]        │
└────────────────────────────────────────────────────────────────────────────────────────────────────────┘
```

### 🏠 TRANG 0 — TRANG CHỦ TỔNG QUAN (EXECUTIVE SITUATION ROOM) *[mặc định khi mở web]*
Threat Level Banner (cấp báo động + trạng thái socket + toạ độ GPS trạm) → Mã QR kết nối nhanh → 4 thẻ KPI (Tổng SOS / Nguy cấp chờ cứu / Đang tiếp cận / Đã cứu an toàn) → Khung radar map thu nhỏ → Bảng ưu tiên số 1 (3–5 ca nguy hiểm nhất, nút Điều phối ngay) → Thanh phím tắt (Báo động toàn vùng, Mở GIS, Cổng liên ngành, Phân tích Đề 53, Xuất báo cáo IAP).

### 🗺️ TRANG 1 — BẢN ĐỒ TÁC CHIẾN TOÀN CẢNH (COP)
3 lớp bản đồ (Vệ tinh/Địa hình/Dark Tactical) → AntPath route Nạn nhân→Relay→C2 → Layer hạ tầng (Bệnh viện/PCCC/Công an) → Định tuyến tự động tới bệnh viện gần nhất kèm ETA → Heatmap mật độ mắc kẹt → Range Rings 500m/1km/2km → Drawer trái: danh sách `PENDING` + nút Dispatch 1-chạm.

### 🏥 TRANG 2 — CỔNG LIÊN THÔNG LIÊN NGÀNH (115 - 114 - 113)
3 phân hệ nút bắn hồ sơ sang Y tế/Cứu nạn/Công an (chi tiết hành vi giữ nguyên như bản gốc mục "Trang 2") + Danh bạ khẩn cấp dã chiến (hotline, tần số VHF/UHF).

### 📊 TRANG 3 — TRUNG TÂM GIÁM SÁT MẠNG (TACTICAL NOC — ĐỀ 53)
4 biểu đồ: Real-time Throughput, Hop Count Distribution, Duplicate Drop Gauge, Relay Traffic Heatmap + Live Packet Sniffer (console JSON thô + SHA-256 chạy real-time).

### 🚑 TRANG 4 — PHÂN LOẠI Y TẾ & THỐNG KÊ THẢM HỌA (TRIAGE MATRIX — ĐỀ 54)
3 biểu đồ: Loại hình thảm họa (Donut), Thống kê nhóm máu cần (BarChart ngang), Cumulative Rescue Progress + Bảng phân loại Mã Đỏ/Vàng/Xanh.

### 📡 TRANG 5 — SƠ ĐỒ MẠNG LƯỚI MESH (D3.js Force-directed Topology)
Đồ thị nút động C2↔Relay↔Nạn nhân, kéo thả kiểm tra topo, đường liên kết sáng khi có Heartbeat/SOS + Bảng giám sát phần cứng Relay.

### 📜 TRANG 6 — NHẬT KÝ BẰNG CHỨNG & XUẤT BÁO CÁO (AUDIT TRAIL & IAP REPORT)
Bảng nhật ký bất biến theo mili-giây + Xuất PDF (IAP Report) + Xuất CSV/Excel dữ liệu thô.

### ⚙️ TRANG 7 — CẤU HÌNH TRẠM & BỘ BƠM DỮ LIỆU DEMO (SCENARIO SIMULATOR)
Cấu hình GPS/port/âm lượng còi + Packet Injector 1-chạm (bơm nạn nhân demo, ca máu hiếm, packet đa hop) để trình diễn trước hội đồng.

---

## 🔊 4. HỆ THỐNG CẢNH BÁO ÂM THANH & THỊ GIÁC
Còi báo Web Audio API (800Hz–1500Hz khi có SOS mới) + Strobe viền đỏ toàn màn hình + nút `MUTE/DISMISS`. Chi tiết CSS/animation xem `ART_DIRECTION_NASA_C2.md` mục 5.6.

---

## 🗄️ 5. THIẾT KẾ CƠ SỞ DỮ LIỆU SQLITE (WAL MODE)

`PRAGMA journal_mode=WAL;` để chống mất dữ liệu khi sập nguồn.

```sql
-- 1. Hồ sơ nạn nhân & vòng đời cứu nạn (Đề 54)
CREATE TABLE IF NOT EXISTS victims (
    node_id TEXT PRIMARY KEY,
    full_name TEXT NOT NULL,
    phone_number TEXT,
    blood_type TEXT DEFAULT 'O+',
    medical_notes TEXT,
    victim_count INTEGER DEFAULT 1,
    alert_type TEXT NOT NULL,
    severity TEXT NOT NULL,
    latitude REAL NOT NULL,
    longitude REAL NOT NULL,
    status TEXT DEFAULT 'PENDING', -- PENDING (REQ), DISPATCHED (ACQ), RESOLVED (REL)
    dispatched_team TEXT,
    first_seen_timestamp INTEGER NOT NULL,
    last_updated_timestamp INTEGER NOT NULL
);

-- 2. Nhật ký toàn bộ gói tin Mesh
CREATE TABLE IF NOT EXISTS mesh_packets (
    packet_id TEXT PRIMARY KEY,
    packet_type TEXT NOT NULL,
    source_node_id TEXT NOT NULL,
    destination_node_id TEXT NOT NULL,
    sender_hop_id TEXT NOT NULL,
    ttl INTEGER NOT NULL,
    hop_count INTEGER NOT NULL,
    timestamp INTEGER NOT NULL,
    route_history TEXT NOT NULL,
    checksum TEXT NOT NULL,
    payload_json TEXT
);

-- 3. Lịch sử lệnh điều phối
CREATE TABLE IF NOT EXISTS dispatch_logs (
    dispatch_id TEXT PRIMARY KEY,
    target_node_id TEXT NOT NULL,
    command_message TEXT NOT NULL,
    sender_name TEXT NOT NULL,
    dispatched_at INTEGER NOT NULL,
    ack_received INTEGER DEFAULT 0,
    ack_timestamp INTEGER
);

-- 4. Nhật ký chuyển tiếp liên ngành (115, 114, 113)
CREATE TABLE IF NOT EXISTS interagency_dispatches (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    victim_node_id TEXT NOT NULL,
    agency_type TEXT NOT NULL, -- EMS_115, RESCUE_114, POLICE_113
    agency_name TEXT NOT NULL,
    payload_sent TEXT NOT NULL,
    dispatched_at INTEGER NOT NULL,
    status TEXT DEFAULT 'SENT'
);

-- 5. Danh sách cơ sở cấp cứu & an ninh dã chiến
CREATE TABLE IF NOT EXISTS emergency_facilities (
    facility_id TEXT PRIMARY KEY,
    facility_name TEXT NOT NULL,
    facility_type TEXT NOT NULL, -- HOSPITAL, FIRE_STATION, POLICE_POST
    latitude REAL NOT NULL,
    longitude REAL NOT NULL,
    contact_phone TEXT,
    available_capacity INTEGER DEFAULT 10
);

-- 6. Danh sách trạm tiếp sóng (Relay Nodes)
CREATE TABLE IF NOT EXISTS relay_nodes (
    node_id TEXT PRIMARY KEY,
    node_name TEXT,
    ip_address TEXT,
    port INTEGER,
    last_heartbeat_timestamp INTEGER NOT NULL,
    packets_forwarded_count INTEGER DEFAULT 0,
    status TEXT DEFAULT 'ONLINE' -- ONLINE, TIMEOUT, OFFLINE
);

-- 7. Giám sát lưu lượng & băng thông định kỳ (Đề 53)
CREATE TABLE IF NOT EXISTS network_metrics (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    timestamp INTEGER NOT NULL,
    packets_received INTEGER DEFAULT 0,
    packets_forwarded INTEGER DEFAULT 0,
    packets_dropped INTEGER DEFAULT 0,
    duplicate_drops INTEGER DEFAULT 0,
    active_relay_count INTEGER DEFAULT 0
);
```

### 5.1. Ánh xạ Entity Java ↔ Bảng DB (vá lỗ hổng model còn thiếu)

Bản trước chỉ khai 4 model (`MeshPacket`, `VictimEntity`, `DispatchCommand`, `InterAgencyDispatch`) nhưng có 7 bảng và nhiều Repository/Controller cần entity riêng. Bảng dưới đây là danh sách **đầy đủ, bắt buộc** — mọi field phải khớp tên cột DB (camelCase ↔ snake_case qua JPA/JDBC mapping chuẩn):

| Bảng DB | Entity Java | Trạng thái | Ghi chú |
|---|---|---|---|
| `victims` | `VictimEntity.java` | Có sẵn | Dùng thống nhất cho cả DB lẫn payload REST/WebSocket — **không** dùng thêm tên `RescueIncident` nữa (đã sửa ở mục 6.2) |
| `mesh_packets` | `MeshPacket.java` | Có sẵn | |
| `dispatch_logs` | `DispatchCommand.java` | Có sẵn | Dùng thống nhất cho cả DB lẫn payload `/topic/dispatch-ack` — **không** dùng thêm tên `DispatchLog` |
| `interagency_dispatches` | `InterAgencyDispatch.java` | Có sẵn | |
| `emergency_facilities` | **`FacilityEntity.java`** | 🆕 Bổ sung | Field khớp 1:1 cột bảng `emergency_facilities` |
| `relay_nodes` | **`RelayNodeEntity.java`** | 🆕 Bổ sung | Dùng thống nhất cho payload `/topic/relay-status` — **không** dùng tên `RelayNode` |
| `network_metrics` | **`NetworkMetricEntity.java`** | 🆕 Bổ sung | Dùng thống nhất cho payload `/topic/network-metrics` — **không** dùng tên `NetworkMetric` |
| *(không có bảng riêng)* | **`AuditLogEntity.java`** | 🆕 Bổ sung | **DTO tổng hợp** cho Trang 6 (Audit Trail), KHÔNG map trực tiếp 1 bảng — được `ReportExportService` build bằng cách JOIN theo `node_id`/`timestamp` từ `mesh_packets` + `dispatch_logs` + `interagency_dispatches` tại thời điểm truy vấn, không lưu bảng riêng để tránh trùng lặp dữ liệu |

> Toàn bộ 4 entity mới phải được thêm vào `model/` và có Repository tương ứng (`FacilityRepository` đã có sẵn trong cấu trúc; bổ sung thêm `RelayNodeRepository` đã có sẵn; cần thêm mới `NetworkMetricRepository`). `AuditLogEntity` không cần Repository vì là dữ liệu tổng hợp runtime.

### 5.2. Cảnh báo kỹ thuật: SQLite + HikariCP khi ghi đồng thời

SQLite dù bật `WAL` vẫn **chỉ cho phép 1 writer tại một thời điểm** (nhiều reader thì được đồng thời). Khi nhiều Node gửi SOS dồn dập qua `MeshTcpServer`, nếu HikariCP mở nhiều connection ghi song song sẽ gặp lỗi `SQLITE_BUSY: database is locked`. Bắt buộc cấu hình:

```yaml
# application.yml — xem đầy đủ ở mục 7.1
spring:
  datasource:
    hikari:
      maximum-pool-size: 4   # KHÔNG để mặc định 10 — SQLite không hưởng lợi từ nhiều connection ghi
      connection-timeout: 5000
```

- Cân nhắc dồn toàn bộ thao tác **ghi** (insert/update từ `MeshRoutingService`, `IncidentLifecycleService`) qua **1 hàng đợi tuần tự** (ví dụ `ExecutorService` single-thread hoặc `synchronized` ở tầng Repository ghi) thay vì để nhiều thread TCP handler ghi trực tiếp cùng lúc — tránh retry/lock storm khi có bão gói tin (*Broadcast Storm*).
- Thao tác **đọc** (REST GET cho Frontend) không bị giới hạn này, có thể dùng pool riêng lớn hơn nếu cần.

---

## 📡 6. REST API & WEBSOCKET CONTRACT (MỚI BỔ SUNG — bắt buộc để 8 trang hoạt động & tương thích JSON Android)

### 6.1. REST Endpoints theo từng trang

| Trang | Method | Endpoint | Chức năng |
|---|---|---|---|
| 0, 1 | `GET` | `/api/victims?status=` | Danh sách nạn nhân, lọc theo trạng thái |
| 0, 1 | `GET` | `/api/victims/{nodeId}` | Chi tiết 1 nạn nhân |
| 0, 1 | `POST` | `/api/dispatch` | Gửi lệnh điều phối nội bộ (đội ca-nô/y tế của trạm) |
| 0 | `POST` | `/api/victims/{nodeId}/resolve` | Đánh dấu hoàn tất cứu nạn |
| 2 | `POST` | `/api/interagency/{agencyType}/dispatch` | Bắn hồ sơ sang 115/114/113 — `agencyType` ∈ `EMS_115`, `RESCUE_114`, `POLICE_113` |
| 2 | `GET` | `/api/interagency/directory` | Danh bạ khẩn cấp dã chiến |
| 1, 2 | `GET` | `/api/facilities?type=` | Danh sách bệnh viện/trạm PCCC/đồn công an trên bản đồ |
| 3 | `GET` | `/api/metrics/summary` | Snapshot Đề 53 khi mới load trang (trước khi WebSocket kết nối) |
| 3 | `GET` | `/api/packets?nodeId=&limit=` | Nhật ký gói tin, phân trang, dùng cho Packet Sniffer & Audit Trail |
| 5 | `GET` | `/api/relay-nodes` | Danh sách + trạng thái Relay cho sơ đồ Topology |
| 6 | `GET` | `/api/reports/iap?format=pdf|csv` | Xuất báo cáo IAP / CSV |
| 7 | `GET/POST` | `/api/config/station` | Đọc/ghi cấu hình GPS, port, âm lượng còi |
| 7 | `POST` | `/api/simulator/inject` | Bơm packet demo — body chọn kịch bản (`FLOOD_BATCH`, `RARE_BLOOD`, `MULTI_HOP`) |
| Tất cả | `GET` | `/api/health` | Health check |

### 6.2. WebSocket/STOMP Topics (endpoint kết nối: `/ws`, SockJS fallback bật sẵn)

| Topic | Payload | Trang subscribe | Khi nào phát |
|---|---|---|---|
| `/topic/victims` | `RescueIncident` JSON | 0, 1, 4 | SOS mới, đổi trạng thái |
| `/topic/packets` | `MeshPacket` JSON đầy đủ `routeHistory` | 1, 3, 5, 6 | Mỗi packet hợp lệ nhận từ mesh |
| `/topic/relay-status` | `RelayNode` JSON | 3, 5 | Heartbeat mới / relay TIMEOUT/OFFLINE |
| `/topic/network-metrics` | `NetworkMetric` JSON (window theo phút) | 3 | Định kỳ 5–10s |
| `/topic/dispatch-ack` | `DispatchLog` JSON có `ackReceived=true` | 0, 1, 6 | Nhận ACK từ Android node |
| `/topic/interagency` | `InterAgencyDispatch` JSON | 2, 6 | Sau khi bắn hồ sơ sang 115/114/113 thành công |
| `/topic/alerts` | `{severity, victimNodeId, message}` | Toàn cục (kích hoạt còi + strobe) | Mỗi khi có ca `CRITICAL` mới |

### 6.3. JSON mẫu (khớp Android Mảng 1)

```json
{
  "packetId": "uuid",
  "packetType": "SOS_ALERT",
  "sourceNodeId": "NODE_A_VICTIM",
  "destinationNodeId": "BASE_STATION",
  "senderHopId": "NODE_B_RELAY",
  "ttl": 5,
  "hopCount": 2,
  "timestamp": 1735900000000,
  "routeHistory": ["NODE_A_VICTIM", "NODE_B_RELAY", "BASE_STATION"],
  "checksum": "sha256-hex",
  "payload": {
    "fullName": "Nguyễn Văn A",
    "alertType": "FLOOD",
    "severity": "CRITICAL",
    "latitude": 16.0544,
    "longitude": 108.2022,
    "victimCount": 3
  }
}
```

---

## 📁 7. CẤU TRÚC MÃ NGUỒN DỰ ÁN (JAVA WEB C2)

> Đổi tên thư mục gốc từ `desktop/` (sót từ bản JavaFX cũ) → `tactical-web-c2/` cho đúng bản chất dự án.

```
tactical-web-c2/
├── pom.xml                                   <-- Spring Boot 3 Web, WebSocket, SQLite, HikariCP, Gson
├── CHAY_TRAM_CHI_HUY.bat                     <-- Trình khởi động 1-Click, tự dò IP, mở browser fullscreen
└── src/main/
    ├── java/com/rescue/mesh/
    │   ├── TacticalWebC2Application.java     <-- Entry point Spring Boot DUY NHẤT
    │   ├── config/
    │   │   ├── WebSocketConfig.java          <-- STOMP endpoint /ws, message broker /topic
    │   │   └── AsyncSocketConfig.java        <-- Thread pool cho TCP Socket 8888/8889
    │   ├── model/
    │   │   ├── MeshPacket.java               <-- Tương thích 100% JSON với Android (mục 6.3)
    │   │   ├── VictimEntity.java
    │   │   ├── DispatchCommand.java
    │   │   └── InterAgencyDispatch.java
    │   ├── repository/
    │   │   ├── VictimRepository.java
    │   │   ├── MeshPacketRepository.java
    │   │   ├── InterAgencyRepository.java
    │   │   ├── FacilityRepository.java       <-- DAO emergency_facilities (mới bổ sung, sót ở bản gốc)
    │   │   └── RelayNodeRepository.java
    │   ├── socket/
    │   │   ├── MeshTcpServer.java            <-- Lắng nghe port 8888 (inbound)
    │   │   └── MeshTcpClient.java            <-- Gửi DISPATCH port 8889 (outbound)
    │   ├── service/
    │   │   ├── MeshRoutingService.java       <-- Controlled Flooding + SeenPacketCache + Checksum
    │   │   ├── IncidentLifecycleService.java <-- REQ → ACQ → REL (Đề 54)
    │   │   ├── InterAgencyService.java       <-- Chuyển tiếp 115/114/113
    │   │   ├── NetworkMetricsService.java    <-- Thông lượng & Drop Rate (Đề 53)
    │   │   ├── ReportExportService.java      <-- Xuất PDF/CSV (mới bổ sung cho Trang 6)
    │   │   └── ScenarioSimulatorService.java <-- Bơm gói tin demo (Trang 7)
    │   └── controller/
    │       ├── IncidentApiController.java    <-- /api/victims/**
    │       ├── DispatchApiController.java    <-- /api/dispatch, /api/victims/{id}/resolve
    │       ├── InterAgencyApiController.java <-- /api/interagency/**
    │       ├── FacilityApiController.java    <-- /api/facilities (mới bổ sung)
    │       ├── MetricApiController.java      <-- /api/metrics/**, /api/packets
    │       ├── RelayNodeApiController.java   <-- /api/relay-nodes
    │       ├── ReportApiController.java      <-- /api/reports/iap
    │       ├── ConfigApiController.java      <-- /api/config/station
    │       ├── SimulatorApiController.java   <-- /api/simulator/inject
    │       └── LiveWebSocketBroadcaster.java <-- Gói hàm push toàn bộ /topic/* dùng chung
    └── resources/
        ├── application.yml                   <-- Port 8080, SQLite WAL URL, Mesh Port 8888/8889
        └── static/
            ├── index.html                    <-- Trang chính chứa 8 phân hệ (SPA, tab switch không reload)
            ├── css/
            │   ├── tactical-c2.css           <-- TOÀN BỘ token/class từ ART_DIRECTION_NASA_C2.md
            │   └── leaflet.css
            ├── js/
            │   ├── c2-app.js                 <-- Điều khiển Navbar 8 tab, kết nối STOMP dùng chung
            │   ├── c2-home.js                <-- Trang 0
            │   ├── c2-map.js                 <-- Trang 1 (AntPath, Heatmap, hạ tầng)
            │   ├── c2-interagency.js         <-- Trang 2
            │   ├── c2-noc.js                 <-- Trang 3
            │   ├── c2-triage.js              <-- Trang 4
            │   ├── c2-topology.js            <-- Trang 5 (D3.js)
            │   ├── c2-audit.js               <-- Trang 6
            │   ├── c2-config.js              <-- Trang 7
            │   ├── c2-audio.js               <-- Còi hú Web Audio API
            │   └── ws-client.js              <-- Kết nối STOMP dùng chung, export hàm subscribe theo mục 6.2
            └── vendor/                       <-- Toàn bộ thư viện + font nhúng cục bộ (KHÔNG CDN)
                ├── fonts/                    <-- Space Grotesk, JetBrains Mono (offline)
                ├── leaflet.js
                ├── leaflet.antpath.js
                ├── leaflet.heat.js
                ├── chart.umd.js
                ├── d3.min.js                 <-- cho Trang 5 Topology (sót ở bản gốc)
                ├── qrcode.min.js
                └── stomp.umd.js
```

---

## 📋 8. KẾ HOẠCH TRIỂN KHAI 5 BATCH

* **BATCH 1 — Khởi tạo Spring Boot & Dual-Engine:** `pom.xml`, `MeshTcpServer` (8888), `MeshTcpClient` (8889), `WebSocketConfig`, `AsyncSocketConfig`.
* **BATCH 2 — DB WAL + Routing + Liên ngành:** `sqlite-jdbc` + HikariCP + `PRAGMA journal_mode=WAL`, toàn bộ Repository, `MeshRoutingService` (SHA-256, SeenPacketCache), `IncidentLifecycleService` (Đề 54), `InterAgencyService`.
* **BATCH 3 — REST API + WebSocket Layer (theo mục 6):** toàn bộ Controller + `LiveWebSocketBroadcaster`, đảm bảo mọi thay đổi DB đều tự phát đúng topic tương ứng.
* **BATCH 4 — Frontend NASA Style 8 Phân hệ:** `index.html`, `tactical-c2.css` (bám sát `ART_DIRECTION_NASA_C2.md` tuyệt đối), 8 file JS theo từng trang, `c2-audio.js`, tích hợp font/thư viện nhúng cục bộ.
* **BATCH 5 — Đóng gói & Diễn tập:** `mvn clean package` → `TacticalWebC2.jar`, `CHAY_TRAM_CHI_HUY.bat` tự dò IP + mở fullscreen. Kịch bản kiểm thử: bấm file .bat → quét QR trên iPad/điện thoại → nạn nhân demo bấm SOS (qua Trang 7 Simulator hoặc thiết bị Android thật) → còi hú + vẽ route trên COP → trạm trưởng Dispatch → bắn liên ngành 115/114/113 → nhận ACK → 8 trang chuyển đổi mượt 60–120 FPS.

---

## 🔗 9. LIÊN KẾT VỚI FILE ART DIRECTION

File này mô tả **kiến trúc & hành vi**. File `ART_DIRECTION_NASA_C2.md` mô tả **toàn bộ token thị giác** (màu, chamfered corner, typography, component spec, checklist nghiệm thu UI). Batch 4 bắt buộc đọc song song cả hai — không tự chế màu sắc/bố cục ngoài phạm vi đã định nghĩa.

---

## 🔮 10. ĐỊNH HƯỚNG MỞ RỘNG TƯƠNG LAI (PHASE 5+ ROADMAP)

Để đưa hệ thống từ mức độ **Đồ án Xuất sắc / Demo Tác chiến Cấp cao (9.4/10)** lên chuẩn **Triển khai Thực địa Quy mô Quốc gia (Field Operations Production-Ready)**, 3 tính năng nâng cao sau được quy hoạch cho lộ trình nâng cấp Phase 5+:

### 10.1. ☀️ CHẾ ĐỘ TƯƠNG PHẢN CAO NGOÀI TRỜI (TACTICAL HIGH-CONTRAST / SUNLIGHT MODE)
* **Bối cảnh & Vấn đề:** Giao diện Deep Space Black (`#05070B`) tối ưu cho trung tâm chỉ huy trong nhà (Command Bunker / Ops Center). Tuy nhiên, khi sĩ quan chỉ huy mang Laptop/iPad ra ngoài trời nắng gắt (direct sunlight), màn hình tối bị lóa và khó đọc các chỉ số sinh tồn và bản đồ.
* **Quy cách kỹ thuật:**
  * **CSS Design Tokens:** Bổ sung bộ token High-Contrast đạt chuẩn **WCAG AAA** (Contrast ratio > 7:1): Nền sáng phản xạ thấp `#F4F6F9`, panel `#FFFFFF` viền đậm `2px solid #0A0D14`, chữ `#0A0D14` siêu nét.
  * **Triage & KPI Adapters:** Giữ nguyên màu phân loại Triage (Đỏ `#D32F2F`, Vàng `#E65100`, Xanh lá `#1B5E20`, Đen `#212121`) nhưng tăng độ bão hòa, loại bỏ hoàn toàn hiệu ứng mờ kính (glassmorphism), scanlines và glow shadow để hiển thị sắc nét tuyệt đối dưới ánh nắng mặt trời.
  * **Kích hoạt:** Phím tắt toggle nhanh `Ctrl + H` hoặc công tắc chuyển đổi trực tiếp trên thanh HUD Topbar.

### 10.2. 📋 NHẬT KÝ HOẠT ĐỘNG ĐỘI CỨU HỘ CHUẨN FEMA ICS-214 (UNIT ACTIVITY LOG)
* **Bối cảnh & Vấn đề:** Hệ thống hiện đã tích hợp thành công ICS Form 201 (Incident Briefing), ICS 204 (Assignment List) và ICS 205A (Comms Plan). Để hoàn thiện chu trình báo cáo tác chiến chuẩn FEMA ICS/NIMS, cần bổ sung **ICS Form 214** nhằm ghi nhận nhật ký hoạt động của từng Đội cứu hộ (Unit Activity Log) theo từng mốc thời gian, phục vụ công tác giải trình và rút kinh nghiệm sau thảm họa (Post-Incident Review).
* **Quy cách kỹ thuật:**
  * **Database Schema:** Tạo bảng mới `unit_activity_logs`:
    ```sql
    CREATE TABLE IF NOT EXISTS unit_activity_logs (
        log_id TEXT PRIMARY KEY,
        team_id TEXT NOT NULL,
        timestamp INTEGER NOT NULL,
        activity_type TEXT NOT NULL, -- DISPATCH_ACCEPTED, ARRIVED_AT_SCENE, VICTIM_EXTRICATED, MEDICAL_GIVEN, STANDBY
        activity_description TEXT NOT NULL,
        reported_by TEXT NOT NULL,
        location_lat REAL,
        location_lon REAL
    );
    ```
  * **Backend Service:** Bổ sung phương thức xuất báo cáo trong `ReportExportService.java` (`/api/reports/ics214?teamId=...&format=pdf|csv`) xuất đúng mẫu FEMA ICS-214: *Incident Name, Operational Period, Unit Name/Designators, Personnel Assigned, Activity Log Timeline*.
  * **Frontend UI:** Bổ sung Drawer lịch sử hoạt động chi tiết của từng đội cứu hộ trên Trang 1 (Bản đồ COP) và mục tra cứu lịch sử trên Trang 6 (Audit Trail).

### 10.3. 🔄 PHÂN CẤP CHỈ HUY (SPAN OF CONTROL) & NHẮN TIN 2 CHIỀU (TWO-WAY MESH MESSAGING)
* **Bối cảnh & Vấn đề:** Trong các chiến dịch lớn (hàng chục đội cứu hộ, hàng trăm nạn nhân), một Trạm trưởng không thể điều hành trực tiếp toàn bộ nhân sự (vi phạm nguyên tắc *Span of Control 3–7 người* của FEMA). Đồng thời, chỉ phát lệnh DISPATCH một chiều chưa đủ để hỗ trợ trao đổi thông tin chiến thuật linh hoạt giữa Chỉ huy và Hiện trường.
* **Quy cách kỹ thuật:**
  * **Span of Control (Phân cấp điều hành FEMA NIMS):**
    * Thiết lập cây phân cấp tổ chức: *Incident Commander (IC) → Operations Section Chief → Branch Directors → Division/Group Supervisors → Single Resources / Strike Teams*.
    * Phân quyền tài khoản và bộ lọc sự cố theo từng Khu vực địa lý (Sector/Division) trên Bản đồ COP.
  * **Two-way Mesh Messaging (Kênh liên lạc hai chiều dã chiến):**
    * Thiết kế cấu trúc gói tin `MESH_TEXT_MSG` (hỗ trợ mã hóa AES nhẹ) định tuyến qua mạng Mesh LoRa / Wi-Fi dã chiến trên cổng TCP 8888/8889.
    * Cho phép Sở chỉ huy chat trực tiếp (Direct P2P Message / Broadcast Ping) tới điện thoại nạn nhân để hướng dẫn sơ cứu tâm lý/y tế, hoặc gửi lệnh điều động bí mật tới từng Đội trưởng cứu hộ ngoài hiện trường.


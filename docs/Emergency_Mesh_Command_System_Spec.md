# TÀI LIỆU ĐẶC TẢ KỸ THUẬT & KIẾN TRÚC HỆ THỐNG CỨU HỘ KHẨN CẤP
## HỆ THỐNG LAI TỐI ƯU: FIELD MESH (KOTLIN ANDROID) & COMMAND BASE STATION (JAVA + JAVAFX)
### PHIÊN BẢN CHỐT CHUẨN ĐỒ ÁN MÔN LẬP TRÌNH MẠNG

---

## 1. TỔNG QUAN HỆ THỐNG (EXECUTIVE SUMMARY)

* **Tên đề tài:** Nghiên cứu giao thức định tuyến đa bước và xây dựng hệ thống điều hành cứu nạn khẩn cấp ngoại tuyến kết hợp mạng Mesh di động (Kotlin Android) và Trạm chỉ huy dã chiến (Java + JavaFX Desktop).
* **Mục tiêu kỹ thuật cốt lõi:**
  * Xây dựng mạng Ad-hoc P2P đa bước (Multi-hop Routing) giữa các thiết bị di động Android tại hiện trường hoàn toàn **KHÔNG CẦN INTERNET / 4G / TRẠM BTS**.
  * Thiết bị cứu nạn/nạn nhân gửi tin nhắn SOS, tọa độ GPS và mức độ khẩn cấp nhảy cóc qua các máy trung gian (`Node A -> Node B -> Node C -> Trạm chỉ huy`).
  * Tự động đồng bộ các bản tin cứu hộ về **Trạm chỉ huy Laptop (JavaFX Desktop App)** khi có thiết bị kết nối vào vùng phủ sóng theo cơ chế *Store-and-Forward*.
  * Ban chỉ huy trên Laptop có thể quan sát bản đồ ngoại tuyến lớn, theo dõi danh sách nạn nhân theo thời gian thực và phát mệnh lệnh cứu hộ ngược lại hiện trường.
* **Đối tượng thực hiện:** Nhóm 2 sinh viên CNTT Năm 3 (Môn Lập trình mạng).

---

## 2. BẢNG PHÂN BỔ CÔNG NGHỆ CHỐT (FINALIZED TECH STACK ALLOCATION)

| Phân hệ hệ thống | Thành phần cụ thể | Ngôn ngữ tối ưu | Công nghệ & Thư viện chủ đạo |
|---|---|---|---|
| **1. Trạm Chỉ Huy (Laptop Server)** | **Tầng Mạng (Socket Gateway)** | **Java** | `java.net.ServerSocket`, `Socket`, `Executors.newFixedThreadPool()` |
| | **Giao diện Desktop (UI)** | **Java (JavaFX)** | FXML, Scene Builder, AtlantaFX Dark Theme, `javafx.scene.web.WebView` |
| | **Bản đồ Ngoại tuyến (Desktop Map)** | **JavaScript / HTML** | Leaflet.js Offline Map Tiles (chạy cục bộ trong WebView) |
| | **Lưu trữ Cục bộ (Local DB)** | **Java** | SQLite qua **JDBC** (`sqlite-jdbc`) / Jackson JSON Parser |
| **2. Ngoại Địa (Android Node)** | **Tầng Mạng & P2P Engine (Core)** | **Kotlin** | Android `WifiP2pManager`, Coroutines (`Dispatchers.IO`), `Flow` |
| | **Tiến trình Chạy nền (Background Service)** | **Kotlin** | `MeshForegroundService`, `ServerSocket` Kotlin, WakeLock |
| | **Giao diện Di động (Mobile UI)** | **Kotlin** | Jetpack Compose (hoặc Android XML Layouts), Material Design 3 |
| | **Định vị & Bản đồ Cục bộ** | **Kotlin** | `FusedLocationProviderClient` (GPS), OsmDroid SDK (Bản đồ Offline) |
| | **Cơ sở Dữ liệu Cục bộ** | **Kotlin** | Android Room Database (SQLite Engine) cho Store-and-Forward |

---

## 3. KIẾN TRÚC TỔNG THỂ HỆ THỐNG (HYBRID MESH-TO-SINK TOPOLOGY)

```
+-----------------------------------------------------------------------------------+
|                     LAPTOP COMMAND BASE STATION (JAVA + JAVAFX)                   |
|                                                                                   |
|  +------------------------------------+   +------------------------------------+  |
|  |  JAVA SOCKET GATEWAY (Java 17/21)  |   |  JAVAFX COMMAND DASHBOARD (FXML)   |  |
|  |  - ServerSocket (Port 8888)        | ↔ |  - WebView Offline Map (Leaflet.js)|  |
|  |  - ThreadPool & Checksum Validator |   |  - Real-time Victim Triage (Red/Yel)|
|  |  - SQLite JDBC Storage Engine      |   |  - Downstream Dispatch Controller  |  |
|  +------------------------------------+   +------------------------------------+  |
+-----------------------------------------------------------------------------------+
                                        ▲
                                        │ (TCP Socket Ingest / Port 8888)
+ - - - - - - - - - - - - - - - - - - - ┼ - - - - - - - - - - - - - - - - - - - - - +
| FIELD MESH ZONE (NO INTERNET / NO BTS)│                                           |
|                                       ▼                                           |
|                           [ ANDROID RELAY NODE C ]                                |
|                           (Đội cứu hộ cơ động 2 - Kotlin)                         |
|                                       ▲                                           |
|                           (Wi-Fi Direct P2P Hop)                                  |
|                                       ▼                                           |
|                           [ ANDROID RELAY NODE B ]                                |
|                           (Đội cứu hộ cơ động 1 - Kotlin)                         |
|                                       ▲                                           |
|                           (Wi-Fi Direct P2P Hop)                                  |
|                                       ▼                                           |
|                          [ ANDROID SOURCE NODE A ]                                |
|                             (Nạn nhân mắc kẹt - Kotlin)                           |
+ - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - +
```

---

## 4. CẤU TRÚC GÓI TIN ĐỊNH TUYẾN MẠNG CHUẨN (`MeshPacket`)

Tất cả các bản tin truyền qua Socket giữa Kotlin (Android) và Java (Laptop) đều tuân theo chuẩn định dạng JSON sau:

### Định dạng Schema JSON:
```json
{
  "packet_id": "7b8f9e61-6d2c-4f10-9b34-8c8a2b53b811",
  "packet_type": "SOS_BROADCAST | DISPATCH_COMMAND | ROUTE_DISCOVERY | ACK | HEARTBEAT",
  "source_node_id": "ANDROID_VICTIM_01",
  "destination_node_id": "BASE_STATION_SERVER | BROADCAST | ANDROID_NODE_X",
  "sender_hop_id": "ANDROID_RELAY_02",
  "ttl": 6,
  "hop_count": 2,
  "timestamp": 1771239999000,
  "payload": {
    "sender_name": "Người dân Đồi Cọ",
    "alert_type": "MEDICAL_EMERGENCY | FLOOD_TRAPPED | LANDSLIDE",
    "message": "Nước lũ dâng ngập mái nhà, có 2 trẻ nhỏ cần cứu hộ gấp!",
    "victim_count": 4,
    "severity": "CRITICAL",
    "location": {
      "latitude": 16.074512,
      "longitude": 108.150245,
      "altitude": 15.2,
      "accuracy": 3.5
    }
  },
  "route_history": ["ANDROID_VICTIM_01", "ANDROID_RELAY_01", "ANDROID_RELAY_02"],
  "checksum": "d8e3b4a1"
}
```

---

## 5. PHÂN CHIA CÔNG VIỆC CHI TIẾT CHO NHÓM 2 SINH VIÊN

### SINH VIÊN 1: LẬP TRÌNH TRẠM CHỈ HUY LAPTOP (JAVA SOCKET BACKEND + JAVAFX UI)
* **Ngôn ngữ:** Java (JDK 17/21)
* **Các module đảm nhiệm:**
  1. **Java Socket Gateway:**
     * Khởi tạo `ServerSocket(8888)` với `Executors.newFixedThreadPool(20)` đón nhận kết nối đồng thời từ các thiết bị Android.
     * Giải mã chuỗi JSON thành đối tượng Java POJO, kiểm tra mã toàn vẹn `checksum`.
     * Quản lý bộ đệm `ConcurrentHashMap<String, Long> seenPackets` để loại bỏ các bản tin bị trùng lặp.
  2. **JavaFX Dashboard UI (FXML + Controller):**
     * Thiết kế giao diện trung tâm điều hành chuyên nghiệp bằng FXML và Scene Builder (Dark Theme tương phản cao).
     * **Bảng danh sách nạn nhân (TableView):** Hiển thị theo thời gian thực, tự động phân màu Đỏ/Vàng theo mức độ nguy cấp (`severity`).
     * **Âm thanh cảnh báo:** Kích hoạt còi hú dã chiến (`javafx.scene.media.AudioClip`) khi có bản tin SOS mới về trạm.
     * **Bản đồ tác chiến ngoại tuyến:** Nhúng `javafx.scene.web.WebView` chạy Leaflet.js Offline, tự động ghim Marker vị trí GPS và vẽ tuyến đường bước nhảy (Hop Routes).
     * **Bộ phát lệnh điều phối:** Form nhập tin nhắn chỉ đạo gửi ngược lại máy Android của nạn nhân qua Socket.
  3. **SQLite Database Engine:**
     * Viết lớp `DatabaseManager.java` dùng JDBC để lưu vết toàn bộ bản tin SOS, danh sách Node mạng và lịch sử mệnh lệnh vào tệp `.db`.

---

### SINH VIÊN 2: LẬP TRÌNH ỨNG DỤNG ANDROID NGOẠI ĐỊA (KOTLIN P2P ENGINE + MOBILE UI)
* **Ngôn ngữ:** Kotlin (Android Studio)
* **Các module đảm nhiệm:**
  1. **Tầng Kết Nối P2P (Wi-Fi Direct Manager):**
     * Cài đặt `WifiP2pManager` & `BroadcastReceiver`: Quét tìm thiết bị xung quanh (Peer Discovery), tự động kết nối và bắt tay P2P.
     * Cơ chế tự phục hồi kết nối (Auto-reconnect / Retry Mechanism) khi di chuyển vào/ra vùng phủ sóng.
  2. **Tiến Trình Chạy Nền & Sockets (Kotlin Coroutines):**
     * Xây dựng `MeshForegroundService` với Notification ưu tiên cao để duy trì `ServerSocket(8888)` chạy nền liên tục.
     * Sử dụng **Kotlin Coroutines (`Dispatchers.IO`)** để quản lý các socket gửi/nhận byte stream nhẹ, không gây tràn RAM.
  3. **Thuật Toán Định Tuyến & Store-and-Forward:**
     * Cài đặt `SeenPacketLRUCache` để hủy gói tin đã thấy, giảm `TTL--`, tăng `HopCount++` và chuyển tiếp sang các node khác.
     * Xây dựng cơ chế **Store-and-Forward**: Bản tin SOS chưa tới được Base Station sẽ lưu tạm trong Room Database, khi gặp Base Station hoặc Node cứu hộ khác sẽ tự động đẩy đi.
  4. **Giao Diện Hiện Trường & Định Vị GPS:**
     * Nút bấm phát tín hiệu **"GỬI SOS KHẨN CẤP"** to, nổi bật, thao tác 1 chạm.
     * Màn hình danh sách tin nhắn cứu hộ P2P: Hiển thị trạng thái tin nhắn (`Đang tìm đường`, `Đã chuyển tiếp qua Node X`, `Đã tới Base Station`).
     * Tích hợp `FusedLocationProviderClient` tự động lấy tọa độ GPS chính xác gắn vào gói tin.
     * Tích hợp **OsmDroid SDK** hiển thị bản đồ ngoại tuyến trên điện thoại.

---

## 6. KỊCH BẢN THỰC NGHIỆM VÀ DEMO KHI BẢO VỆ ĐỒ ÁN (TESTING & DEMO PROOF)

### Môi trường thực nghiệm:
* **1 Laptop (Base Station):** Đặt tại bàn Hội đồng chấm thi, chạy ứng dụng JavaFX Desktop.
* **2-3 Điện thoại Android (Field Nodes):** Cầm trên tay 2 sinh viên.
* **ĐIỀU KIỆN QUAN TRỌNG:** Tắt toàn bộ 4G/LTE và ngắt kết nối với mọi Router Wi-Fi có Internet. Laptop và điện thoại chỉ liên lạc cục bộ.

### Quy trình diễn tập Demo (5 Bước):
1. **Bước 1 (Cô lập vị trí):** 
   * Máy Android 1 (Nạn nhân) mang ra góc xa phòng học (ngoài tầm bắt sóng trực tiếp tới Laptop).
   * Máy Android 2 (Relay Node cứu hộ) đứng ở giữa làm trạm tiếp sức.
2. **Bước 2 (Phát tín hiệu SOS):** 
   * Trên Máy 1: Bấm nút "GỬI SOS: NƯỚC NGẬP MÁI NHÀ - 4 NGƯỜI MẮC KẸT".
   * Log Android hiển thị: `[Mesh] Sent Packet [001] to Relay Node 2`.
3. **Bước 3 (Chuyển tiếp đa bước):** 
   * Máy 2 nhận gói tin, rung chuông, hiển thị log: `[Relay] Received from Node 1 -> Forwarding to Base Station (TTL: 5, Hop: 1)`.
4. **Bước 4 (Tiếp nhận tại Dashboard JavaFX trên Laptop):** 
   * Ứng dụng JavaFX trên Laptop của Ban giám khảo phát chuông cảnh báo đỏ.
   * Danh sách TableView tự động thêm dòng mới, Bản đồ tác chiến tự động đánh dấu Marker đỏ tại đúng tọa độ GPS của Máy 1.
5. **Bước 5 (Phát lệnh chỉ huy ngược):** 
   * Ban giám khảo nhập lệnh trên JavaFX: *"Đã tiếp nhận tọa độ, cano cứu hộ đang tiếp cận"*.
   * Lệnh được gửi ngược theo chuỗi: Laptop -> Máy 2 -> Máy 1. Máy 1 nhận được tin nhắn và rung chuông an tâm.

---

## 7. LỘ TRÌNH PROMPT CHO CLAUDE / AI ĐỂ XÂY DỰNG MÃ NGUỒN THEO MODULE

Khi đưa tài liệu này vào Claude, hãy yêu cầu sinh mã nguồn theo 4 Phase tuần tự:

* **Phase 1: Shared Data Model (Data Serialization)**
  > *"Dựa trên tài liệu đặc tả, hãy viết class `MeshPacket.java` (cho Laptop) và data class `MeshPacket.kt` (cho Android) sử dụng Gson để serialize/deserialize JSON, tính toán Checksum và quản lý TTL."*

* **Phase 2: Laptop Base Station (Java Socket + JavaFX Desktop UI) (Sinh viên 1)**
  > *"Hãy xây dựng ứng dụng JavaFX Desktop hoàn chỉnh gồm: `BaseStationServer.java` (ServerSocket TCP Port 8888 + ThreadPool), `DatabaseManager.java` (SQLite JDBC), và Controller JavaFX (kèm FXML) hiển thị TableView nạn nhân và bản đồ tác chiến WebView Offline (nhúng Leaflet.js)."*

* **Phase 3: Android Network & Routing Engine (Kotlin Android) (Sinh viên 2)**
  > *"Hãy viết các class Kotlin Android: `WifiDirectManager.kt` (kết nối P2P), `MeshForegroundService.kt` (duy trì ServerSocket nền bằng Coroutines), và `RoutingEngine.kt` (quản lý SeenPacketCache chống bão lặp và cơ chế Store-and-Forward qua Room DB)."*

* **Phase 4: Android UI & GPS Integration (Kotlin Android) (Sinh viên 2)**
  > *"Hãy thiết kế giao diện UI (Jetpack Compose hoặc XML) cho Android: Màn hình nút bấm SOS khẩn cấp, tích hợp `FusedLocationProviderClient` lấy tọa độ GPS, và hiển thị bản đồ ngoại tuyến bằng OsmDroid."*

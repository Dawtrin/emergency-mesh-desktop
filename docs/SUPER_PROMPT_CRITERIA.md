# ⚔️ SIÊU PROMPT NÂNG CẤP — CHUẨN C-R-I-T-E-R-I-A
## Emergency Mesh Rescue System — Phiên bản Chuyên Gia

> **Dùng khi:** Bắt đầu phiên mới, hoặc cần Claude hiểu sâu lý thuyết để viết code đúng chuẩn học thuật.
> **Dán toàn bộ file này** vào đầu cuộc trò chuyện mới.

---

## ██ C — CONTEXT (Bối cảnh đầy đủ)

### Dự án & Con người
- **Tên đề tài:** Nghiên cứu giao thức định tuyến đa bước và xây dựng hệ thống điều hành cứu nạn khẩn cấp ngoại tuyến kết hợp mạng Mesh di động và Trạm chỉ huy dã chiến.
- **Nhóm:** 2 sinh viên năm 3, kỳ học hiện tại.
- **Vai trò của AI:** Kỹ sư Mạng Phân tán + Chuyên gia Java/Kotlin kỳ cựu. Không rút gọn code, không để TODO, không shortcut.
- **Chiến lược 2 giai đoạn:**
  - GIAI ĐOẠN 1 (GIỮA KỲ): JavaFX + Java Sockets giả lập 3 tiến trình trên 1 máy tính.
  - GIAI ĐOẠN 2 (CUỐI KỲ): Android thực địa với Wi-Fi Direct + GPS thật.

### Môi trường kỹ thuật
- OS: Windows 11, IntelliJ IDEA, Java 21 (OpenJDK Microsoft), Maven bundled.
- Cấu trúc project: `emergency-mesh-rescue/phase1-desktop/` (Maven project).
- Package root: `com.rescue.mesh`.

### Trạng thái hiện tại
```
pom.xml                ✅ HOÀN THÀNH
SUPER_PROMPT.md        ✅ HOÀN THÀNH
MODULE_STATUS.md       ✅ HOÀN THÀNH
MODULE 1 (Core)        ⏳ BẮT ĐẦU TIẾP THEO
MODULE 2 (Client)      🔒 CHỜ MODULE 1
MODULE 3 (Server)      🔒 CHỜ MODULE 1 & 2
```

---

## ██ R — ROLE (Vai trò & Năng lực yêu cầu)

### Bạn là AI với 3 vai trò đồng thời:

**VAI TRÒ 1 — Kỹ sư Mạng Phân tán**
- Nắm vững TCP/IP stack, Socket programming, multi-threading.
- Hiểu sâu về Mesh Network topology, flooding vs routing protocols.
- Biết rõ các vấn đề: packet loop, TTL exhaustion, race condition trên shared resources.

**VAI TRÒ 2 — Chuyên gia Java/JavaFX**
- Viết code production-ready: đầy đủ import, exception handling, resource cleanup.
- Tư duy theo Observer Pattern cho network callbacks → UI updates.
- Thread safety: `Platform.runLater()` cho JavaFX, `ConcurrentHashMap` cho shared state.

**VAI TRÒ 3 — Cố vấn Học thuật**
- Giải thích lý do kỹ thuật của từng quyết định thiết kế (để sinh viên báo cáo được).
- Gắn tên thuật toán/pattern chuẩn vào từng đoạn code.
- Gợi ý tài liệu tham khảo, RFC, open source để tăng điểm chất lượng báo cáo.

---

## ██ I — INSTRUCTIONS (Chỉ thị bất biến)

### I1 — Quy tắc sinh code TUYỆT ĐỐI
```
❌ CẤM hoàn toàn:
   // TODO: implement this
   // ... similar code above
   // see getters/setters below
   [code rút gọn để tiết kiệm token]

✅ BẮT BUỘC:
   - Toàn bộ import đầy đủ ở đầu file
   - Mọi getter/setter viết đủ, không dùng Lombok
   - Mọi interface callback viết đủ method
   - Mọi try-catch-finally viết đủ block
```

### I2 — Exception Handling bắt buộc
```java
// Mẫu chuẩn cho mọi Socket operation:
try (Socket socket = new Socket(host, port)) {
    PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
    out.println(gson.toJson(packet));
    logger.info("[SEND] Gói tin {} → {}:{}", packet.getPacketId(), host, port);
} catch (ConnectException e) {
    logger.error("[ERROR] Không kết nối được đến {}:{} — {}", host, port, e.getMessage());
    notifyUIConnectionFailed(host, port);
} catch (IOException e) {
    logger.error("[ERROR] Lỗi I/O khi gửi gói tin {}: {}", packet.getPacketId(), e.getMessage());
} 
// Không cần finally nếu dùng try-with-resources
```

### I3 — Thread Safety bắt buộc
```java
// Collection dùng chung giữa threads:
private final ConcurrentHashMap<String, Long> seenPackets = new ConcurrentHashMap<>();
private final CopyOnWriteArrayList<VictimTableRow> victimList = new CopyOnWriteArrayList<>();

// Cập nhật JavaFX UI từ background thread:
Platform.runLater(() -> {
    tableView.getItems().add(newRow);
    mapWebView.getEngine().executeScript("addMarker(" + lat + "," + lon + ")");
});

// ExecutorService cho multi-client server:
ExecutorService threadPool = Executors.newCachedThreadPool();
```

### I4 — Logging format thống nhất
```
[SEND]   → node gửi gói tin đi
[RECV]   → node nhận được gói tin
[RELAY]  → node chuyển tiếp (không phải đích cuối)
[DROP]   → hủy gói tin (lý do: DUPLICATE / TTL_EXPIRED / CHECKSUM_FAIL)
[ALERT]  → Base Station nhận SOS mới
[ACK]    → xác nhận nhận được
[INFO]   → thông tin hệ thống bình thường
[ERROR]  → exception hoặc lỗi nghiêm trọng
```

### I5 — Phân tầng tuyệt đối (Layer Isolation)
```
┌─────────────────────────────────────────┐
│  UI Layer (JavaFX Controllers / FXML)   │  ← Chỉ gọi xuống, không có logic mạng
├─────────────────────────────────────────┤
│  Network Layer (SocketServer/Client)    │  ← Callback lên UI qua interface
├─────────────────────────────────────────┤
│  Routing Layer (RoutingEngine)          │  ← Chỉ làm việc với MeshPacket
├─────────────────────────────────────────┤
│  Model Layer (MeshPacket, Cache)        │  ← POJO thuần, không biết gì về trên
└─────────────────────────────────────────┘
Vi phạm phân tầng = lỗi thiết kế nghiêm trọng, không chấp nhận.
```

### I6 — Thứ tự sinh code trong 1 phiên
```
Mỗi phiên làm việc: chỉ viết 1 file duy nhất → tóm tắt cách ghép nối → chờ lệnh.
Không tự động nhảy sang file tiếp theo khi chưa được xác nhận.
```

---

## ██ T — THEORY (Cơ sở lý thuyết & Kỹ thuật)

### T1 — Lý thuyết Mạng Mesh (Ad-hoc Mesh Networking)

**Định nghĩa:** Mạng Mesh là mạng phi tập trung (decentralized) nơi mỗi node vừa là thiết bị đầu cuối vừa là relay trung gian. Không cần router trung tâm.

**Phân loại topology áp dụng trong dự án:**
```
FLOODING (giữa kỳ giả lập):
  - Mỗi node nhận gói tin → chuyển tiếp đến TẤT CẢ neighbor đã biết
  - Ưu: đơn giản, tin cậy cao
  - Nhược: bão gói tin (packet storm) nếu không có loop prevention

CONTROLLED FLOODING (chúng ta dùng):
  - Kết hợp TTL + SeenPacketCache để giới hạn flooding
  - Thuật toán: Reverse Path Forwarding (RPF) đơn giản hóa
  - RFC tham khảo: RFC 3973 (PIM-DM), RFC 4944 (IPv6 over LoWPAN)
```

**Lý thuyết TTL (Time-To-Live):**
```
Nguồn gốc: IPv4 Header (RFC 791) — giới hạn vòng đời gói tin trong mạng.
Trong hệ thống này:
  - TTL khởi tạo = 5 (tối đa 5 bước nhảy)
  - Mỗi relay node giảm TTL đi 1 trước khi forward
  - TTL = 0 → DROP (tránh vòng lặp vô tận)
  - Lý do chọn 5: thực tế Mesh cứu nạn hiếm khi cần >3 hop
```

**Loop Prevention — SeenPacketCache:**
```
Vấn đề: Trong topology có chu trình (A→B→C→A), gói tin sẽ loop mãi.
Giải pháp: Duplicate Suppression bằng UUID cache
  - Mỗi gói tin có UUID duy nhất (java.util.UUID.randomUUID())
  - Node lưu UUID đã xử lý vào ConcurrentHashMap<UUID, timestamp>
  - TTL-based expiry: xóa entry sau 60 giây (tránh memory leak)
  - Cơ sở lý thuyết: OSPF Sequence Number, BGP Route Dampening
```

### T2 — Kỹ thuật Định tuyến (Routing Techniques)

**Thuật toán áp dụng: Static Source Routing (đơn giản hóa)**
```
Giữa kỳ: routing table được hard-code (Node A → Node B → Base Station)
Lý do: đủ để demo multi-hop, phù hợp thời gian 2 sinh viên

So sánh với các thuật toán thực tế:
┌──────────────────┬──────────────┬─────────────────────────────────┐
│ Thuật toán       │ Độ phức tạp  │ Ứng dụng thực tế                │
├──────────────────┼──────────────┼─────────────────────────────────┤
│ Static Routing   │ O(1)         │ Hệ thống nhỏ, đã biết topology  │
│ Flooding         │ O(2^n)       │ Emergency, broadcast             │
│ AODV             │ O(n)         │ MANET, Wi-Fi Direct thực tế      │
│ OLSR             │ O(n log n)   │ Mesh cố định, drone swarm        │
│ Dijkstra/OSPF    │ O(n^2)       │ Mạng doanh nghiệp               │
└──────────────────┴──────────────┴─────────────────────────────────┘

Cuối kỳ (Android): nâng lên Reactive Flooding với neighbor discovery
```

**routeHistory — Nguồn gốc lý thuyết:**
```
Tương đương Source Route trong IPv6 Routing Header (RFC 2460).
Dùng để:
  1. Debug: biết gói tin đi qua đâu
  2. Tránh routing loop phụ (dù UUID cache đã xử lý)
  3. Base Station hiển thị đường đi trực quan trên bản đồ Leaflet
```

### T3 — Kỹ thuật Lập trình Mạng Java

**Java Socket Architecture trong dự án:**
```
ServerSocket (port 8888/8002/8001)
    └── accept() → trả về Socket client
         └── BufferedReader (đọc JSON từ stream)
              └── Gson.fromJson() → MeshPacket object
                   └── RoutingEngine.process(packet)
                        └── [DROP / DISPLAY / RELAY]

Multi-threading model:
  - 1 thread chính: ServerSocket.accept() loop (blocking)
  - N worker threads: xử lý từng client connection (ExecutorService)
  - 1 JavaFX Application Thread: cập nhật UI (Platform.runLater)
  
Design Pattern: Reactor Pattern (tương tự Netty, VertX)
```

**Serialization — Tại sao dùng Gson (JSON) thay vì Java Serialization?**
```
Java Serialization (ObjectOutputStream):
  ❌ Binary format → không đọc được bằng mắt để debug
  ❌ Không tương thích với Android nếu version JVM khác nhau
  ❌ Lỗ hổng bảo mật deserialization (CVE-2015-4852)

Gson (JSON over Socket):
  ✅ Human-readable → dễ debug với Wireshark/tcpdump
  ✅ Platform-agnostic → Android dùng được nguyên vẹn
  ✅ Tương thích 100% giai đoạn 2
  ✅ Dễ test bằng cách gõ JSON thủ công qua telnet/netcat
```

### T4 — Checksum & Data Integrity

**SHA-256 Checksum trong MeshPacket:**
```java
// Mục đích: Phát hiện gói tin bị sửa đổi trong quá trình relay
// Cách tính: SHA-256(gson.toJson(packet.getPayload()))
// Cách kiểm tra: mỗi node nhận → tính lại checksum → so sánh

Cơ sở lý thuyết:
  - Cryptographic Hash Function (CHF) — RFC 6234
  - SHA-256 output: 256-bit (64 hex chars) — không thể giả mạo ngược
  - Trong mạng cứu nạn: đảm bảo tọa độ GPS không bị sai do lỗi truyền

Giới hạn: không chống được man-in-the-middle (không có digital signature)
Đủ cho đồ án vì đây là mạng LAN nội bộ.
```

### T5 — JavaFX Architecture (MVC + Observer)

```
Pattern áp dụng: MVC (Model-View-Controller) + Observer

Model:    MeshPacket, VictimTableRow, ObservableList<VictimTableRow>
View:     BaseStation.fxml, NodeClient.fxml (khai báo bằng XML)
Controller: BaseStationController.java (bind Model ↔ View)

Observer Pattern:
  Network thread → fires event → Controller (observer) → updates UI
  
  Cài đặt bằng:
  interface NetworkEventListener {
      void onPacketReceived(MeshPacket packet);
      void onConnectionLost(String nodeId);
  }
  
  SocketServer implement → gọi listener.onPacketReceived()
  BaseStationController implements NetworkEventListener
```

---

## ██ E — EXAMPLES (Ví dụ chuẩn tham chiếu)

### E1 — Luồng dữ liệu demo chính xác
```
[Khởi động hệ thống - thứ tự bắt buộc]
1. java -jar app.jar --mode BASE_STATION --port 8888
2. java -jar app.jar --mode RELAY --port 8002 --next-hop 8888 --id NODE_B
3. java -jar app.jar --mode VICTIM --port 8001 --next-hop 8002 --id NODE_A

[Kịch bản SOS]
Node A UI: Nhập Lat=16.0745, Long=108.1502, Type=FLOOD_TRAPPED, Severity=CRITICAL
Node A UI: Bấm "GỬI TÍN HIỆU SOS"

[Log chuẩn phải thấy]
NODE_A: [SEND]  Packet{id=UUID-001, type=SOS_DATA, ttl=5} → localhost:8002
NODE_B: [RECV]  Packet{id=UUID-001} from NODE_A | TTL=5, hopCount=0
NODE_B: [RELAY] Packet{id=UUID-001} → localhost:8888 | TTL=4, hopCount=1
SERVER: [ALERT] NEW SOS — NODE_A via NODE_B | Lat=16.0745 | FLOOD_TRAPPED | CRITICAL
SERVER: [INFO]  Alarm triggered | Map marker added | TableView updated

[Log kiểm tra chống lặp - nếu gói tin đến lần 2]
NODE_B: [DROP]  Packet{id=UUID-001} — DUPLICATE (already seen 0.3s ago)
```

### E2 — Checksum kiểm chứng
```
Payload JSON: {"senderName":"Nguyen Van A","alertType":"FLOOD_TRAPPED",...}
SHA-256:      a3f8b2c1d4e5...  (64 hex chars)
Khi relay nhận → tính lại SHA-256 → so sánh → MATCH → tiếp tục forward
                                               MISMATCH → DROP + log [ERROR]
```

---

## ██ R2 — REFERENCES (Tài liệu & Open Source tham khảo)

### R2.1 — RFC & Chuẩn học thuật (dùng cho báo cáo)

| RFC | Nội dung | Phần dự án áp dụng |
|-----|----------|---------------------|
| RFC 791 | IPv4 TTL field definition | Cơ sở thiết kế TTL trong MeshPacket |
| RFC 4944 | IPv6 over LoWPAN (IEEE 802.15.4) | Tham chiếu kiến trúc mesh IoT |
| RFC 3561 | AODV Routing Protocol | Thuật toán định tuyến giai đoạn 2 |
| RFC 3973 | PIM-DM (Flooding-based) | Cơ sở lý thuyết Controlled Flooding |
| RFC 6234 | SHA-2 Hash Functions | Checksum implementation |
| IEEE 802.11 | Wi-Fi Direct (P2P) | Giai đoạn 2 Android transport layer |

### R2.2 — Open Source có thể tích hợp hoặc học từ

#### A. ATAK (Android Team Awareness Kit) — Quan trọng nhất
```
Repository:  https://github.com/deptofdefense/AndroidTacticalAssaultKit-CIV
License:     ATAK CIV License (tự do dùng phi thương mại)
Mô tả:       Hệ thống nhận thức tình huống chiến trường của US DoD
             Chính xác là bài toán tương tự dự án này!

Học được gì:
  ✅ CoT (Cursor on Target) — XML packet format cho tactical data
  ✅ Cách thiết kế overlay bản đồ cho nhiều loại marker (SOS, Unit, Hazard)
  ✅ Plugin architecture cho mesh radio (ATAK-Mesh plugin)
  
Cách ứng dụng vào dự án:
  - Học cấu trúc CoT XML → cải tiến MeshPacket schema
  - Xem cách họ handle offline map tiles → áp dụng cho Leaflet offline
  - Không tích hợp trực tiếp (quá phức tạp), nhưng trích dẫn trong báo cáo
    giúp tăng tính học thuật đáng kể
```

#### B. Meshtastic — Gần nhất với đề tài
```
Repository:  https://github.com/meshtastic/Meshtastic-Android
License:     GPL v3
Mô tả:       Mesh radio app cho thiết bị LoRa (thực tế dùng cứu nạn)

Học được gì:
  ✅ MeshPacket Protobuf schema (protobufs/mesh.proto) — rất giống MeshPacket.java
  ✅ Flooding algorithm với duplicate suppression (packet_seen.cpp)
  ✅ Android service architecture cho background mesh relay
  
Cách ứng dụng vào dự án:
  GIAI ĐOẠN 1: Đọc mesh.proto → hiểu field design → cải thiện MeshPacket.java
               File: https://github.com/meshtastic/protobufs/blob/master/mesh.proto
               
  GIAI ĐOẠN 2: Học MeshService.kt → áp dụng pattern cho Android background service
               File: meshtastic-android/app/src/main/java/com/geeksville/mesh/service/
               
  Trong báo cáo: "Thiết kế MeshPacket tham khảo chuẩn Meshtastic v2.x"
```

#### C. OpenStreetMap + Leaflet.js — Bản đồ offline
```
Repository:  https://github.com/Leaflet/Leaflet
             https://github.com/allartk/leaflet.offline
License:     BSD-2-Clause
Mô tả:       Bản đồ offline dùng tile cache cục bộ

Cách tích hợp vào dự án (leaflet_offline.html):
  <!-- Tải tile từ folder local thay vì internet -->
  <script src="leaflet.js"></script>
  <script src="leaflet.offline.js"></script>
  <script>
    // Dùng tile từ thư mục resources/map/tiles/
    var layer = L.tileLayer.offline('tiles/{z}/{x}/{y}.png', {
      minZoom: 10, maxZoom: 17
    });
    
    // Thêm marker SOS từ Java qua WebView bridge:
    function addSOSMarker(lat, lon, nodeId, severity) {
      var color = severity === 'CRITICAL' ? 'red' : 'orange';
      L.circleMarker([lat, lon], {color: color, radius: 10})
       .addTo(map)
       .bindPopup('<b>' + nodeId + '</b><br>' + severity);
    }
    
    // Vẽ đường route history:
    function drawRoute(routeArray) {
      L.polyline(routeArray, {color: 'blue', weight: 2}).addTo(map);
    }
  </script>

Cách tải tile offline:
  Dùng MOBAC (Mobile Atlas Creator) hoặc:
  python -c "import urllib.request; ..."  # script tải tile
  Lưu vào: src/main/resources/map/tiles/{z}/{x}/{y}.png
```

#### D. NetBeans Platform / Apache Commons Net
```
Repository:  https://commons.apache.org/proper/commons-net/
License:     Apache 2.0
Mô tả:       Thư viện network utilities cho Java

Học được gì:
  ✅ Cách implement echo server/client đơn giản (ví dụ học Socket)
  ✅ Utility classes cho byte manipulation
  
Ứng dụng: Đọc example code để học pattern, không integrate trực tiếp.
```

#### E. GoTenna Mesh SDK — Tham khảo commercial
```
URL:         https://developer.gotenna.com/
Mô tả:       SDK mesh radio thương mại dùng trong cứu nạn thực tế (US FEMA)

Học được gì:
  ✅ Message priority queue (CRITICAL > HIGH > MEDIUM) 
  ✅ Acknowledgement mechanism (ACK/NACK per hop)
  ✅ Battery-aware relay (node yếu pin không relay nữa)
  
Ứng dụng: Không dùng SDK (closed source), nhưng đọc docs để thiết kế
           ACK mechanism trong DISPATCH_CMD packet type.
```

---

## ██ I2 — IMPLEMENTATION PLAN (Kế hoạch triển khai chi tiết)

### Giai đoạn 1 — Desktop Simulator

**MODULE 1: Shared Core** (ưu tiên tuyệt đối)
```
Thứ tự viết:
1. ChecksumUtil.java        → SHA-256 helper, không dependency
2. SeenPacketCache.java     → ConcurrentHashMap + TTL expiry thread
3. MeshPacket.java          → POJO + Gson + checksum verify method
4. PacketFactory.java       → static factory methods tạo SOS/CMD/ACK/HB
5. RoutingEngine.java       → core algorithm, nhận callback interface

Kỹ thuật áp dụng:
  - Factory Method Pattern (PacketFactory)
  - Strategy Pattern (RoutingCallback interface)
  - Null Object Pattern (tránh NullPointerException trên payload)
  - Scheduled Executor (cache cleanup mỗi 30 giây)
```

**MODULE 2: Simulated Client Node**
```
Thứ tự viết:
1. NodeClientController.java  → JavaFX Controller với @FXML binding
2. NodeClient.fxml            → Giao diện nhỏ gọn (300x400px)
3. SimulatedNodeApp.java      → Entry point, parse args (--port --role --next-hop)

Kỹ thuật áp dụng:
  - Command Pattern (args parsing → NodeConfig object)
  - Observer Pattern (RoutingEngine callback → UI update)
  - JavaFX Data Binding (ObservableList → ListView log)
```

**MODULE 3: Base Station Dashboard**
```
Thứ tự viết:
1. VictimTableRow.java           → JavaFX Bean với SimpleStringProperty
2. AlarmPlayer.java              → AudioClip wrapper với volume control
3. SocketServer.java             → Multi-threaded TCP accept loop
4. SocketClient.java             → Dispatch command sender
5. BaseStationController.java    → Main dashboard controller
6. BaseStation.fxml              → Dashboard layout (800x600px)
7. leaflet_offline.html          → Leaflet map với JS bridge functions

Kỹ thuật áp dụng:
  - Thread Pool Pattern (ExecutorService.newCachedThreadPool)
  - Callback/Listener Pattern (ServerEventListener interface)
  - JavaFX TableView với PropertyValueFactory
  - WebView JSObject Bridge (Java ↔ JavaScript 2 chiều)
  - Platform.runLater() cho mọi UI update từ network thread
```

### Giai đoạn 2 — Android Field Deployment

```
Tái sử dụng 100% (không sửa):
  MeshPacket.java      → copy sang Android module
  RoutingEngine.java   → copy sang Android module
  ChecksumUtil.java    → copy sang Android module
  PacketFactory.java   → copy sang Android module

Thay thế (viết mới cho Android):
  SocketServer/Client  → WifiP2pManager + WifiP2pSocket
  JavaFX Controllers  → Android Fragments + ViewModel (MVVM)
  AudioClip           → MediaPlayer (Android)
  Leaflet WebView     → Google Maps SDK hoặc OSMDroid

Thư viện Android cần:
  implementation 'org.osmdroid:osmdroid-android:6.1.16'  // offline map
  implementation 'com.google.android.gms:play-services-location:21.0.1'  // GPS
  implementation 'com.google.code.gson:gson:2.10.1'  // JSON (giống Phase 1)

Kiến trúc Android:
  Android Service (background) → WifiP2pManager → relay gói tin
  ViewModel (LiveData)         → observe packets → update UI
  Fragment (UI)                → hiển thị SOS form và log
```

---

## ██ A — ASSESSMENT (Tiêu chí đánh giá & Kiểm tra)

### Tiêu chí đánh giá code (tự kiểm trước khi submit):

**A1 — Correctness (Đúng chức năng)**
```
□ Gói tin SOS đi từ Node A → Node B → Base Station không bị mất
□ Bấm SOS 5 lần → log chỉ thấy [SEND] x5, [RELAY] x5, [ALERT] x5 (không lặp)
□ Gói tin duplicate bị DROP trong vòng 60 giây
□ TTL giảm đúng sau mỗi hop (5→4→3...)
□ Checksum FAIL → packet bị DROP và log [ERROR]
□ Base Station nhận → còi kêu + marker trên bản đồ + dòng mới trong bảng
```

**A2 — Robustness (Chịu lỗi)**
```
□ Node B tắt đột ngột → Node A log [ERROR] ConnectException, không crash
□ Gửi 100 gói tin liên tiếp → không OutOfMemoryError
□ Chạy 10 phút liên tục → SeenPacketCache không tăng kích thước vô hạn
□ Mở/đóng cửa sổ Node nhiều lần → không port binding exception
```

**A3 — Code Quality (Chất lượng code)**
```
□ Không có TODO, không có comment rút gọn
□ Mọi field private, truy cập qua getter/setter
□ Tên biến và method mô tả rõ chức năng (không dùng a, b, x, y)
□ Mỗi class chỉ làm 1 việc (Single Responsibility Principle)
□ Không có static mutable state (dễ gây bug đa luồng)
```

**A4 — Demo Quality (Chất lượng trình diễn)**
```
□ 3 cửa sổ mở trên 1 màn hình, không che nhau
□ Log cuộn tự động xuống dòng mới nhất
□ Bản đồ hiển thị được offline (không cần internet)
□ Âm thanh còi kêu rõ khi nhận SOS
□ Đường route history vẽ được trên bản đồ từ A→B→Server
```

---

## ██ SCHEMA & ALGORITHM (Bất biến — không sửa đổi)

### MeshPacket Schema chuẩn
```json
{
  "packetId": "550e8400-e29b-41d4-a716-446655440000",
  "packetType": "SOS_DATA",
  "sourceNodeId": "NODE_A_VICTIM",
  "destinationNodeId": "BASE_STATION",
  "senderHopId": "NODE_B_RELAY",
  "ttl": 4,
  "hopCount": 1,
  "timestamp": 1703123456789,
  "routeHistory": ["NODE_A_VICTIM", "NODE_B_RELAY"],
  "checksum": "a3f8b2c1d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1",
  "payload": {
    "senderName": "Nguyen Van A",
    "alertType": "FLOOD_TRAPPED",
    "message": "Bị kẹt tại tầng 2, cần thuyền cứu hộ",
    "victimCount": 3,
    "severity": "CRITICAL",
    "location": {
      "latitude": 16.0745,
      "longitude": 108.1502
    }
  }
}
```

### Routing Algorithm (pseudocode bất biến)
```
FUNCTION processPacket(P: MeshPacket):
  // Bước 1: Duplicate check
  IF seenCache.contains(P.packetId):
    LOG [DROP] P.packetId + " — DUPLICATE"
    RETURN
  
  // Bước 2: Đánh dấu đã xử lý
  seenCache.put(P.packetId, System.currentTimeMillis())
  LOG [RECV] P.packetId + " from " + P.senderHopId
  
  // Bước 3: Kiểm tra checksum
  IF NOT ChecksumUtil.verify(P):
    LOG [DROP] P.packetId + " — CHECKSUM_FAIL"
    RETURN
  
  // Bước 4: Kiểm tra đích đến
  IF P.destinationNodeId == MY_NODE_ID OR MY_NODE_ID == "BASE_STATION":
    UI.displayAlert(P)
    AlarmPlayer.play()
    sendACK(P)
    RETURN
  
  // Bước 5: Forward nếu TTL còn
  IF P.ttl > 1:
    P.ttl -= 1
    P.hopCount += 1
    P.senderHopId = MY_NODE_ID
    P.routeHistory.add(MY_NODE_ID)
    SocketClient.send(NEXT_HOP_HOST, NEXT_HOP_PORT, P)
    LOG [RELAY] P.packetId + " → " + NEXT_HOP_PORT + " TTL=" + P.ttl
  ELSE:
    LOG [DROP] P.packetId + " — TTL_EXPIRED"
```

---

## ██ LỆNH NẠP VÀO PHIÊN MỚI

```
Dán toàn bộ file này vào Claude, sau đó nói:

"Tôi đang tiếp tục đồ án Emergency Mesh Rescue.
Trạng thái: [MODULE N đã xong / chưa bắt đầu gì].
Yêu cầu: Viết [tên file cụ thể].
Lưu ý thêm: [nếu có vấn đề gì phát sinh]."

Ví dụ cụ thể:
"Tôi đang tiếp tục đồ án Emergency Mesh Rescue.
Trạng thái: pom.xml xong, chưa viết code gì.
Yêu cầu: Viết ChecksumUtil.java trước tiên."
```

---

## ██ BẢNG QUYẾT ĐỊNH NHANH (Quick Reference)

| Tình huống | Giải pháp kỹ thuật | Pattern áp dụng |
|------------|-------------------|-----------------|
| Gói tin đến 2 lần | UUID cache check | Duplicate Suppression |
| Network thread → cập nhật UI | Platform.runLater() | Thread Confinement |
| Nhiều client kết nối đồng thời | ExecutorService | Thread Pool |
| Node B sập, không gửi được | try-catch ConnectException + retry | Circuit Breaker (đơn giản) |
| Bản đồ không có internet | Leaflet offline tiles | Local Resource Caching |
| Debug gói tin qua mạng | Gson JSON → Wireshark | Transparent Serialization |
| Tái sử dụng sang Android | Interface-based design | Dependency Inversion |

---
*Phiên bản: CRITERIA-2.0 | Tạo bởi Claude | Cập nhật trạng thái module sau mỗi file hoàn thành*

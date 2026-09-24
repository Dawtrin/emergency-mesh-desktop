# 🛰️ TRẠNG THÁI HỆ THỐNG & LỘ TRÌNH PHÁT TRIỂN

## Đề tài: Hệ thống Định tuyến và Phân bố Tải Gói tin SOS trong Mạng Mesh Ngoại tuyến phục vụ Cứu hộ Thiên tai

**Môn học:** Lập trình mạng nâng cao
**Trường:** Đại học Công nghệ Thông tin và Truyền thông Việt – Hàn (VKU)
**Nhóm:** 2–3 sinh viên | **Demo:** Nhiều laptop cùng Wi-Fi lớp

---

## 🌊 BÀI TOÁN THỰC TẾ

Khi thiên tai xảy ra (bão lũ, sạt lở, động đất):
- Mất điện lưới toàn vùng
- Đứt cáp quang Internet
- Trạm BTS 4G/5G sập sau 2–4 giờ

**→ Toàn bộ khu vực mất liên lạc hoàn toàn.**

Câu hỏi đặt ra: *Làm thế nào để gói tin SOS từ nạn nhân vẫn đến được trạm chỉ huy cứu hộ, khi không có Internet và không có sóng di động?*

**Giải pháp:** Xây dựng mạng Mesh ngoại tuyến — các thiết bị tự kết nối ngang hàng qua Socket TCP, định tuyến đa bước nhảy (Multi-hop), và **phân bố tải thông minh** giữa các node relay để không node nào bị quá tải trong thảm họa.

---

## 🕸️ MẠNG MESH LÀ GÌ?

### Mạng thông thường (có Internet)
```
Máy A ──► Router ──► Internet ──► Server
```
Tất cả phụ thuộc vào 1 điểm trung tâm. Router chết → tất cả chết.

### Mạng Mesh (không cần Internet)
```
Máy A ──► Máy B ──► Máy C ──► Đích
           │
           └──► Máy D ──► Đích  (đường dự phòng tự động)
```
Không có trung tâm. Mỗi máy vừa là **client** (nhận gói), vừa là **server** (chuyển tiếp gói). Nếu một node chết, gói tự tìm đường khác.

### 3 đặc điểm cốt lõi của Mesh

| Đặc điểm | Giải thích | Áp dụng trong hệ thống |
|---|---|---|
| **Multi-hop** | Gói đi qua nhiều node trung gian | SOS từ nạn nhân → Relay → Chỉ huy |
| **Decentralized** | Không node nào là bắt buộc | B chết → tự chuyển sang C, D |
| **Self-routing** | Mỗi node tự quyết định forward đi đâu | LoadBalancer chọn relay ít tải nhất |

---

## 📌 1. TRẠNG THÁI HIỆN TẠI — Phase 1 (Proof of Concept)

### 1.1 Kiến trúc đã xây dựng

Hệ thống gồm 3 tiến trình Java độc lập, giao tiếp qua **Socket TCP thuần** (không Internet, không framework):

```
[Node A — Nạn nhân]  →  [Node B — Relay Trung gian]  →  [Base Station — Chỉ huy]
     port 8001                   port 8002                      port 8888
  GPS: 15.9738,                Chuyển tiếp                  GPS: 15.9753,
      108.2515                  gói tin                         108.2532
```

Kịch bản thực tế ánh xạ:
- **Node A** = Nạn nhân bị cô lập trong vùng ngập, dùng laptop gửi SOS
- **Node B** = Tình nguyện viên đứng ở rìa vùng ngập, máy đóng vai trạm tiếp sóng
- **Base Station** = Trạm chỉ huy dã chiến của lực lượng cứu nạn

### 1.2 Cấu trúc gói tin MeshPacket

Mỗi gói tin trong hệ thống có cấu trúc JSON chuẩn:

```json
{
  "uuid": "a7f3-b2c1-...",
  "type": "SOS",
  "sourceId": "Node_A",
  "path": ["Node_A", "Node_B1"],
  "ttl": 5,
  "checksum": "sha256-hash-cua-payload",
  "gpsLat": 15.9738,
  "gpsLng": 108.2515,
  "victimCount": 3,
  "message": "Cần cứu hộ khẩn cấp",
  "timestamp": "2025-09-06T10:30:00Z"
}
```

| Field | Vai trò |
|---|---|
| `uuid` | ID duy nhất — SeenPacketCache dùng để chống lặp |
| `type` | `SOS` / `LOAD_REPORT` / `HEARTBEAT` |
| `path` | Lịch sử đường đi — Monitoring dùng để hiển thị |
| `ttl` | Đếm ngược mỗi hop, gói hủy khi về 0 |
| `checksum` | SHA-256 của payload — chống giả mạo dữ liệu GPS |

### 1.3 Các cơ chế đã cài đặt

| Cơ chế | Trạng thái | Vai trò trong bài toán |
|---|---|---|
| **Socket TCP Multi-hop** | ✅ Hoàn chỉnh | Gói SOS vượt qua địa hình chia cắt nhờ relay trung gian |
| **Flooding Routing** | ✅ Hoàn chỉnh | Định tuyến động — gói tin tìm đường đến đích tự động |
| **SeenPacketCache** | ✅ Hoàn chỉnh | Chống Broadcast Storm — mỗi gói chỉ forward đúng 1 lần |
| **TTL (Time-To-Live)** | ✅ Hoàn chỉnh | Giới hạn bước nhảy tối đa, gói tự hủy khi hết TTL |
| **SHA-256 Checksum** | ✅ Hoàn chỉnh | Đảm bảo tọa độ GPS và số nạn nhân không bị giả mạo |
| **JSON Data Model** | ✅ Hoàn chỉnh | Cấu trúc gói tin chuẩn, dễ mở rộng |

### 1.4 Hạn chế hiện tại — cần giải quyết ở Phase 2

| Vấn đề | Mức độ | Ảnh hưởng thực tế |
|---|---|---|
| **Chưa có Load Balancing** | 🔴 Nghiêm trọng | Flooding gửi sang tất cả node mù quáng — 1 node nghẽn trong khi node khác rảnh |
| **Chưa có bảng định tuyến** | 🔴 Nghiêm trọng | Không biết node nào đang sống, node nào đã sập |
| **Monitoring còn sơ khai** | 🟡 Trung bình | Chỉ huy không thấy gói SOS đang đi đường nào |
| **Chỉ chạy localhost** | 🟡 Trung bình | Chưa kiểm chứng trên nhiều máy thật qua LAN |

---

## 🎯 2. ĐỐI CHIẾU YÊU CẦU ĐỀ BÀI

> *"Viết chương trình cài đặt thuật toán phân bố tải của mạng IP trong quá trình định tuyến các gói tin"*

### Yêu cầu a — Chọn phương pháp định tuyến (tĩnh hoặc động)

**Lựa chọn: Định tuyến động có phân bố tải (Dynamic Load-Aware Routing)**

Lý do không chọn định tuyến tĩnh: Trong thảm họa, topo mạng thay đổi liên tục — node relay có thể mất điện, người dân di chuyển, vùng ngập mở rộng. Bảng định tuyến tĩnh cấu hình cứng sẽ thất bại ngay khi 1 node sập.

Định tuyến động cho phép Base Station **tự động cập nhật bảng định tuyến** dựa trên trạng thái tải thật của từng node relay mỗi 3 giây.

### Yêu cầu b — Mô phỏng chuyển gói giữa các Server

Phase 2 nâng cấp từ localhost lên **3 laptop thật cùng Wi-Fi lớp**, mỗi máy đóng 1 vai:
- Máy 1 (nhóm trưởng): Base Station — nhận SOS, ra quyết định phân bố tải
- Máy 2: Node B1 Relay — trạm tiếp sóng thứ nhất
- Máy 3: Node A — nạn nhân gửi SOS liên tục

Toàn bộ giao tiếp qua **Socket TCP với IP thật** — không Internet, đúng tinh thần ngoại tuyến.

### Yêu cầu c — Monitoring giám sát đường đi gói tin

Xây dựng **Monitoring Dashboard** tại Base Station, hiển thị realtime:
- Đường đi từng gói SOS: `Node_A → Node_B1 → BaseStation`
- Tải hiện tại của từng node relay (số gói đang xử lý)
- Cảnh báo khi node quá tải hoặc mất kết nối
- Thống kê tổng phân bố — bằng chứng Load Balancing hoạt động

---

## 🔧 3. LỘ TRÌNH PHÁT TRIỂN — Phase 2

### 3.1 Load Balancing — Thuật toán Least-Load Routing

**Cơ chế heartbeat:**
Mỗi Node relay gửi báo cáo tải lên Base Station mỗi **3 giây**:

```json
{
  "type": "LOAD_REPORT",
  "nodeId": "Node_B1",
  "currentLoad": 3,
  "processedTotal": 47,
  "timestamp": "2025-09-06T10:30:00Z"
}
```

**Bảng định tuyến tại Base Station (cập nhật realtime):**

```
┌──────────┬──────────────┬───────────────┬──────────┐
│  NodeID  │  CurrentLoad │ LastHeartbeat │  Status  │
├──────────┼──────────────┼───────────────┼──────────┤
│  Node_B1 │    3 pkts    │    2s ago     │  ONLINE  │
│  Node_B2 │    1 pkt     │    1s ago     │  ONLINE ✅│
│  Node_B3 │    —         │   15s ago     │  OFFLINE │
└──────────┴──────────────┴───────────────┴──────────┘
→ Gói SOS tiếp theo → Node_B2 (ít tải nhất, đang online)
```

**Logic chọn relay:**
```java
// LoadBalancer.java
public String selectRelay(Map<String, NodeStatus> routingTable) {
    return routingTable.entrySet().stream()
        .filter(e -> e.getValue().isOnline())
        .filter(e -> e.getValue().isAlive(10))       // Heartbeat trong 10 giây
        .min(Comparator.comparingInt(
            e -> e.getValue().getCurrentLoad()))     // Chọn load thấp nhất
        .map(Map.Entry::getKey)
        .orElseThrow(() -> new RuntimeException("Không còn relay nào!"));
}
```

**Kịch bản demo Load Balancing:**
1. Gửi 10 gói SOS liên tục → thấy phân phối đều B1 và B2
2. Tắt máy B1 đột ngột → hệ thống tự chuyển 100% sang B2 trong 10 giây
3. Bật lại B1 → hệ thống tự nhận B1 trở lại vào bảng định tuyến

### 3.2 Triển khai nhiều laptop — Cấu hình LAN

**Sơ đồ 3 người (tối ưu):**

```
Wi-Fi lớp học (cùng mạng nội bộ)

Máy mày                  Máy bạn 1               Máy bạn 2
┌─────────────────┐      ┌─────────────────┐      ┌─────────────────┐
│  BASE STATION   │      │   NODE B1       │      │   NODE A        │
│  192.168.x.10   │◄─────│   192.168.x.11  │◄─────│   192.168.x.12  │
│  port: 8888     │      │   port: 8002    │      │   port: 8001    │
│  [Dashboard]    │      │  [Relay]        │      │  [Gửi SOS]      │
└─────────────────┘      └─────────────────┘      └─────────────────┘
```

**Sơ đồ 2 người:**
```
Máy mày                           Máy bạn
┌─────────────────────┐           ┌─────────────────────┐
│  BASE STATION       │           │  NODE B1 (Relay)    │
│  + NODE A (chạy     │◄──────────│  192.168.x.11       │
│    2 tiến trình)    │           │  port: 8002         │
│  192.168.x.10       │           └─────────────────────┘
│  port: 8888 + 8001  │
└─────────────────────┘
```

**File config — đổi IP tại chỗ không cần sửa code:**

```properties
# basestation.properties
node.id=BaseStation
node.role=COMMAND
self.port=8888

# nodeB1.properties
node.id=Node_B1
node.role=RELAY
self.port=8002
upstream.ip=192.168.x.10
upstream.port=8888

# nodeA.properties
node.id=Node_A
node.role=VICTIM
self.port=8001
relay.ip=192.168.x.11
relay.port=8002
gps.lat=15.9738
gps.lng=108.2515
```

### 3.3 Monitoring Dashboard

```
╔══════════════════════════════════════════════════════╗
║     🛰️  TRẠM CHỈ HUY CỨU HỘ — MONITORING DASHBOARD  ║
╠══════════════════════════════════════════════════════╣
║  NODE STATUS (cập nhật mỗi 3s)                       ║
║  Node_B1 [ONLINE ] Load: ███░░ 3 pkts               ║
║  Node_B2 [ONLINE ] Load: █░░░░ 1 pkt  ← Đang chọn  ║
╠══════════════════════════════════════════════════════╣
║  PACKET LOG                                          ║
║  [10:30:01] SOS #A7F3 → B2 → Station | Hops:2 ✅    ║
║  [10:30:04] SOS #B2C1 → B1 → Station | Hops:2 ✅    ║
║  [10:30:07] SOS #C9D8 → B2 → Station | Hops:2 ✅    ║
╠══════════════════════════════════════════════════════╣
║  THỐNG KÊ PHÂN BỐ TẢI                               ║
║  Node_B1: 12 gói (48%) | Node_B2: 13 gói (52%)      ║
╚══════════════════════════════════════════════════════╝
```

---

## 🚀 4. HƯỚNG DẪN TRIỂN KHAI TỪNG BƯỚC

### Bước 1 — Thêm NodeConfig.java (làm trước tiên)

Mục đích: Đọc IP/port từ file `.properties` thay vì hardcode, giúp đổi IP tại chỗ khi demo.

```java
// src/config/NodeConfig.java
public class NodeConfig {
    private Properties props = new Properties();

    public NodeConfig(String role) throws IOException {
        // Đọc file: config/basestation.properties hoặc config/nodeB1.properties
        props.load(new FileInputStream("config/" + role + ".properties"));
    }

    public String get(String key) { return props.getProperty(key); }
    public int getInt(String key) { return Integer.parseInt(props.getProperty(key)); }
}

// Cách dùng trong RelayNode:
NodeConfig config = new NodeConfig("nodeB1");
String upstreamIp   = config.get("upstream.ip");    // 192.168.x.10
int    upstreamPort = config.getInt("upstream.port"); // 8888
int    selfPort     = config.getInt("self.port");     // 8002
```

**Test:** Tạo file `config/nodeB1.properties`, chạy NodeB1, kiểm tra kết nối đúng IP chưa.

---

### Bước 2 — Thêm NodeStatus.java

Mục đích: Model lưu trạng thái từng relay node trong bảng định tuyến của BaseStation.

```java
// src/core/NodeStatus.java
public class NodeStatus {
    private String nodeId;
    private int currentLoad;      // Số gói đang xử lý
    private int processedTotal;   // Tổng gói đã xử lý
    private Instant lastHeartbeat;

    // Node được coi là ALIVE nếu heartbeat đến trong vòng N giây
    public boolean isAlive(int timeoutSeconds) {
        return Duration.between(lastHeartbeat, Instant.now())
                       .getSeconds() < timeoutSeconds;
    }

    public boolean isOnline() { return isAlive(10); }

    // Getters + setters...
}
```

**Test:** Tạo 1 NodeStatus, gọi `isAlive(10)` → true. Đợi 11 giây → false.

---

### Bước 3 — Thêm heartbeat vào RelayNode.java

Mục đích: Relay tự động báo tải lên BaseStation mỗi 3 giây.

```java
// Thêm vào RelayNode.java
private void sendHeartbeat() {
    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    scheduler.scheduleAtFixedRate(() -> {
        try {
            MeshPacket heartbeat = new MeshPacket();
            heartbeat.setType("LOAD_REPORT");
            heartbeat.setNodeId(this.nodeId);
            heartbeat.setCurrentLoad(this.activeConnections.get()); // AtomicInteger
            heartbeat.setProcessedTotal(this.totalProcessed.get());

            // Gửi lên BaseStation
            Socket socket = new Socket(upstreamIp, upstreamPort);
            sendJSON(socket, heartbeat);
            socket.close();
        } catch (Exception e) {
            System.err.println("[HEARTBEAT] Mất kết nối với upstream");
        }
    }, 0, 3, TimeUnit.SECONDS);
}
```

**Test:** Chạy RelayNode, nhìn console BaseStation xem có nhận LOAD_REPORT mỗi 3 giây không.

---

### Bước 4 — Thêm LoadBalancer.java

Mục đích: Thuật toán chọn relay ít tải nhất khi BaseStation nhận được gói SOS.

```java
// src/core/LoadBalancer.java
public class LoadBalancer {
    private Map<String, NodeStatus> routingTable = new ConcurrentHashMap<>();

    // RelayNode gọi hàm này khi gửi heartbeat đến
    public void updateStatus(String nodeId, int load, int total) {
        routingTable.put(nodeId, new NodeStatus(nodeId, load, total, Instant.now()));
    }

    // BaseStation gọi hàm này khi có SOS mới cần forward
    public String selectBestRelay() {
        return routingTable.entrySet().stream()
            .filter(e -> e.getValue().isOnline())
            .min(Comparator.comparingInt(e -> e.getValue().getCurrentLoad()))
            .map(Map.Entry::getKey)
            .orElseThrow(() -> new RuntimeException("⚠️ Không còn relay nào hoạt động!"));
    }

    // Dashboard gọi hàm này để hiển thị bảng tải
    public Map<String, NodeStatus> getRoutingTable() {
        return Collections.unmodifiableMap(routingTable);
    }
}
```

**Test:** Thêm 2 node với load khác nhau → `selectBestRelay()` phải trả về node load thấp hơn.

---

### Bước 5 — Cập nhật BaseStation.java

Mục đích: Tích hợp LoadBalancer vào luồng xử lý SOS.

```java
// Thêm vào BaseStation.java
private LoadBalancer loadBalancer = new LoadBalancer();

private void handlePacket(MeshPacket packet) {
    if ("LOAD_REPORT".equals(packet.getType())) {
        // Cập nhật bảng định tuyến
        loadBalancer.updateStatus(
            packet.getNodeId(),
            packet.getCurrentLoad(),
            packet.getProcessedTotal()
        );
        return;
    }

    if ("SOS".equals(packet.getType())) {
        // Hiển thị lên Dashboard
        dashboard.logPacket(packet);

        // Chọn relay tốt nhất để forward tiếp (nếu cần)
        String bestRelay = loadBalancer.selectBestRelay();
        System.out.println("[ROUTING] SOS #" + packet.getUuid()
            + " → forward qua " + bestRelay);
    }
}
```

**Test:** Gửi SOS từ NodeA, xem BaseStation chọn đúng relay không.

---

### Bước 6 — Thêm MonitoringDashboard.java

Mục đích: Hiển thị trạng thái hệ thống realtime trên console.

```java
// src/monitor/MonitoringDashboard.java
public class MonitoringDashboard {
    private LoadBalancer loadBalancer;
    private List<String> packetLog = new ArrayList<>();

    public void logPacket(MeshPacket packet) {
        String path = String.join(" → ", packet.getPath()) + " → BaseStation";
        String entry = String.format("[%s] SOS #%s | %s | Hops:%d",
            LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")),
            packet.getUuid().substring(0, 4).toUpperCase(),
            path,
            packet.getPath().size()
        );
        packetLog.add(entry);
        render();
    }

    public void render() {
        System.out.println("\n╔══════════════════════════════════════════╗");
        System.out.println("║     🛰️  MONITORING DASHBOARD              ║");
        System.out.println("╠══════════════════════════════════════════╣");

        // Node status
        loadBalancer.getRoutingTable().forEach((id, status) -> {
            String bar = "█".repeat(status.getCurrentLoad()) +
                         "░".repeat(Math.max(0, 5 - status.getCurrentLoad()));
            System.out.printf("║  %-8s [%-7s] Load: %s %d pkts%n",
                id,
                status.isOnline() ? "ONLINE" : "OFFLINE",
                bar,
                status.getCurrentLoad()
            );
        });

        System.out.println("╠══════════════════════════════════════════╣");

        // Packet log — 5 dòng gần nhất
        packetLog.stream()
            .skip(Math.max(0, packetLog.size() - 5))
            .forEach(line -> System.out.println("║  " + line));

        System.out.println("╚══════════════════════════════════════════╝");
    }
}
```

**Test:** Gửi vài gói SOS, màn hình BaseStation phải cập nhật dashboard sau mỗi gói.

---

### Bước 7 — Cập nhật RUN_DEMO_ALL.bat

```bat
@echo off
echo === EMERGENCY MESH SYSTEM - DEMO ===
echo.
echo Buoc 1: Kiem tra IP cua may nay...
ipconfig | findstr "IPv4"
echo.
echo Buoc 2: Dien IP vao file config truoc khi chay!
echo   - config/basestation.properties
echo   - config/nodeB1.properties
echo   - config/nodeA.properties
echo.
pause

echo Khoi dong Base Station (port 8888)...
start "BASE STATION" java -jar BaseStation.jar basestation
timeout /t 2

echo Khoi dong Node B1 Relay (port 8002)...
start "NODE B1 RELAY" java -jar RelayNode.jar nodeB1
timeout /t 2

echo Khoi dong Node A Victim (port 8001)...
start "NODE A VICTIM" java -jar VictimNode.jar nodeA

echo.
echo === TẤT CẢ NODE ĐÃ KHỞI ĐỘNG ===
```

---

## 📋 5. CHECKLIST TRƯỚC BUỔI DEMO

### Chuẩn bị kỹ thuật (làm ở nhà):
- [ ] Viết xong và test 6 bước code ở trên
- [ ] Build thành 3 file `.jar`: `BaseStation.jar`, `RelayNode.jar`, `VictimNode.jar`
- [ ] Copy 3 file jar + thư mục `config/` vào từng máy bạn nhóm
- [ ] Test chạy localhost trước — 3 tiến trình trên 1 máy

### Setup tại lớp (5 phút):
- [ ] Tất cả kết nối cùng Wi-Fi lớp
- [ ] Mỗi người chạy `ipconfig` → lấy IPv4 → nhắn cho nhau qua điện thoại
- [ ] Điền IP vào file `.properties` trên từng máy
- [ ] Tắt Windows Firewall: `netsh advfirewall set allprofiles state off`
- [ ] Chạy theo thứ tự: BaseStation → NodeB1 → NodeA
- [ ] Test ping: `ping 192.168.x.10` từ máy bạn → phải thấy reply

### Kịch bản demo (theo thứ tự):
1. **Khởi động** — 3 máy bật lên, Dashboard hiển thị node ONLINE
2. **Demo bình thường** — NodeA gửi 5 gói SOS, thấy đường đi trong Dashboard
3. **Demo Load Balancing** — Spam 20 gói, thấy phân phối đều giữa các relay
4. **Demo node sập** — Tắt máy B1 đột ngột → hệ thống tự chuyển sang B2 trong 10 giây
5. **Demo phục hồi** — Bật lại B1 → hệ thống tự nhận B1 vào bảng định tuyến

### Câu hỏi thầy có thể hỏi:

**"Tại sao chọn định tuyến động thay vì tĩnh?"**
> Trong thảm họa, topo mạng thay đổi liên tục. Node relay có thể mất điện bất cứ lúc nào. Định tuyến tĩnh sẽ gửi gói vào node đã chết và mất SOS của nạn nhân. Định tuyến động với heartbeat 3 giây phát hiện ngay node sập và chuyển hướng tức thì.

**"Load Balancing ở đây hoạt động như thế nào?"**
> Mỗi Node relay báo số gói đang xử lý về Base Station mỗi 3 giây qua gói LOAD_REPORT. Base Station so sánh và chọn node có currentLoad thấp nhất để forward — thuật toán Least-Load Routing.

**"Nếu tất cả node relay đều sập thì sao?"**
> LoadBalancer ném RuntimeException, Base Station log cảnh báo khẩn cấp. Trong thực tế mở rộng, gói sẽ được giữ trong hàng đợi chờ node mới kết nối — nguyên lý Store-and-Forward của mạng DTN.

**"Hệ thống có thể mở rộng lên thực địa không?"**
> Toàn bộ tầng Core (Routing, LoadBalancer, DataModel) tái sử dụng 100% khi port lên Android. Thay Socket TCP bằng Wi-Fi Direct hoặc Bluetooth LE — thuật toán giữ nguyên.

---

## 🏗️ 6. CẤU TRÚC CODE PHASE 2

```
emergency-mesh-desktop/
├── src/
│   ├── core/
│   │   ├── MeshPacket.java           ✅ Giữ nguyên
│   │   ├── SeenPacketCache.java      ✅ Giữ nguyên
│   │   ├── ChecksumUtil.java         ✅ Giữ nguyên
│   │   ├── NodeStatus.java           🆕 Bước 2 — Model trạng thái node
│   │   └── LoadBalancer.java         🆕 Bước 4 — Thuật toán Least-Load
│   ├── node/
│   │   ├── BaseStation.java          🔧 Bước 5 — Tích hợp LoadBalancer
│   │   ├── RelayNode.java            🔧 Bước 3 — Thêm heartbeat
│   │   └── VictimNode.java           ✅ Giữ nguyên
│   ├── monitor/
│   │   └── MonitoringDashboard.java  🆕 Bước 6 — Dashboard console
│   └── config/
│       └── NodeConfig.java           🆕 Bước 1 — Đọc IP từ file
├── config/
│   ├── basestation.properties
│   ├── nodeB1.properties
│   └── nodeA.properties
└── RUN_DEMO_ALL.bat                  🔧 Bước 7 — Cập nhật script
```

**Thứ tự ưu tiên code:**
```
Bước 1 (NodeConfig)  →  Bước 2 (NodeStatus)  →  Bước 3 (Heartbeat)
      →  Bước 4 (LoadBalancer)  →  Bước 5 (BaseStation)  →  Bước 6 (Dashboard)
      →  Bước 7 (BAT file)  →  Test nhiều máy
```

---

*Tài liệu cập nhật: Phase 1 hoàn chỉnh — Phase 2 đang triển khai*
*Môn: Lập trình mạng nâng cao — VKU*

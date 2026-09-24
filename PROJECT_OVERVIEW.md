# 🛰️ TÀI LIỆU TỔNG QUAN DỰ ÁN (PROJECT OVERVIEW)
## EMERGENCY MESH RESCUE SYSTEM

---

## 📌 PHẦN 1: THÔNG TIN DỰ ÁN & BỐI CẢNH THỰC TẾ

### 1.1 Tên đầy đủ của Đề tài
* **Tên tiếng Việt:**  
  > **"Nghiên cứu giao thức định tuyến nguồn đa bước nhảy trên nền TCP/IP kết hợp thuật toán phân bố tải và xây dựng hệ thống giám sát, chỉ huy tác chiến cứu nạn dã chiến"**  
  *(Tên rút gọn: **Hệ thống điều hành cứu nạn khẩn cấp ngoại tuyến qua mạng Mesh dã chiến**)*
* **Tên tiếng Anh:**  
  > **"Emergency Mesh Rescue: An Offline Multi-Hop Mesh Dispatch & Load-Balancing System for Disaster Relief"**
* **Slogan / Thông điệp cốt lõi:**  
  > *"Khi viễn thông gục ngã trước thiên tai, mạng Mesh kết nối sự sống."*
* **Môn học / Chuyên ngành:** Lập trình mạng nâng cao / Mạng máy tính & Hệ thống phân tán
* **Cơ sở đào tạo:** Trường Đại học Công nghệ Thông tin và Truyền thông Việt – Hàn (VKU), Đại học Đà Nẵng.

---

### 1.2 Bối cảnh & Tính cấp thiết (The Problem Space)
Trong các thảm họa thiên nhiên khốc liệt (bão lụt lịch sử tại miền Trung, sạt lở đất tại miền núi):
1. **Lưới điện quốc gia bị cắt:** Toàn bộ khu vực chìm trong bóng tối.
2. **Hạ tầng viễn thông tê liệt:** Các cột ăng-ten, trạm phát sóng di động (BTS 4G/5G) sập nguồn hoặc đứt cáp quang sau 2 – 4 giờ.
3. **Mất liên lạc hoàn toàn (Total Blackout):** Người dân bị cô lập giữa biển nước không thể gọi 114/115, không có 4G/Internet để gửi vị trí; lực lượng cứu nạn ở ngoài không thể định vị chính xác vị trí nạn nhân để điều động cano/trực thăng.

👉 **Giải pháp của dự án:**  
Xây dựng một hệ thống truyền tin cứu nạn **hoàn toàn ngoại tuyến (100% Offline)** dựa trên kiến trúc **Mạng lưới đa bước nhảy (Multi-Hop Mesh Network)**. Các máy tính xách tay/thiết bị dã chiến tự động bắt tay qua sóng vô tuyến (Wi-Fi/LAN), đóng vai trò các trạm tiếp sóng bắc cầu qua vùng ngập lụt, đưa tín hiệu SOS và tọa độ GPS của nạn nhân về đến Trạm chỉ huy trung tâm, đồng thời cho phép Trạm chỉ huy truyền ngược lệnh tác chiến về hiện trường.

---

## 🕸️ PHẦN 2: MÔ HÌNH MẠNG LƯỚI ĐA BƯỚC NHẢY (MULTI-HOP MESH NETWORK)

### 2.1 Bản chất: Đây có phải mô hình Client - Server không?
> **Khẳng định chuyên môn:**  
> **Không phải mô hình Client - Server truyền thống.** Đây là **Mô hình Mạng lưới Phân tán Đa bước nhảy (Decentralized Multi-Hop Mesh Network)**, trong đó mỗi nút mạng là một **"Servent" (Server + Client kết hợp)**.

#### So sánh trực quan:
* **Client - Server truyền thống:**
  ```text
  [Máy A] ──┐
  [Máy B] ──┼──► [Web/DB Server Trung Tâm] (Có Internet)
  [Máy C] ──┘
  ```
  *Điểm yếu chí mạng:* Phụ thuộc 100% vào Server trung tâm. Nếu Server mất kết nối Internet/4G hoặc đứt đường truyền ➔ **Toàn bộ hệ thống tê liệt**.

* **Multi-Hop Mesh Network (Hệ thống đề tài):**
  ```text
  [Node A (Nạn nhân)] ────(Mất sóng tới Base)────► ❌ [Base Station]
          │
          └──► [Node B1 (Relay)] ──► [Base Station (Trạm Chỉ Huy)]
                    ▲
  [Node A2] ────────┘
  ```
  *Cơ chế dã chiến:* Nút mạng ở xa không bắt được sóng tới Trạm chỉ huy sẽ **tự động nhờ các nút lân cận chuyển tiếp gián tiếp (Multi-hop)**. Mỗi nút trung gian hoạt động theo nguyên lý **Store-and-Forward**.

---

### 2.2 Kiến trúc 3 tầng phân định (Architectural Layering)

Khi báo cáo hoặc bảo vệ trước Hội đồng, hệ thống được phân định rõ ràng thành 3 tầng:

#### 📊 SƠ ĐỒ TRỰC QUAN KIẾN TRÚC 3 TẦNG:

```text
┌─────────────────────────────────────────────────────────────────────────────┐
│  TẦNG 3: NGHIỆP VỤ CHỈ HUY TÁC CHIẾN (COMMAND & CONTROL - C2)               │
│                                                                             │
│  ┌───────────────────────────────┐     ┌─────────────────────────────────┐  │
│  │   TRẠM CHỈ HUY (BASE STATION) │     │      NÚT NẠN NHÂN (VICTIM)      │  │
│  │  - Giám sát C2 Dashboard      │     │  - Tạo tín hiệu cấp cứu SOS     │  │
│  │  - Ghim bản đồ số Leaflet     │     │  - Đính kèm GPS thực địa        │  │
│  │  - Điều phối lệnh tác chiến   │     │  - Nhận chỉ thị từ chỉ huy      │  │
│  └───────────────────────────────┘     └─────────────────────────────────┘  │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │
                                       ▼ (Truyền gói tin MeshPacket)
┌─────────────────────────────────────────────────────────────────────────────┐
│  TẦNG 2: MẠNG & ĐỊNH TUYẾN DÃ CHIẾN (AD-HOC MESH ROUTING ENGINE)            │
│                                                                             │
│  ┌───────────────────────┐  ┌──────────────────────┐  ┌──────────────────┐  │
│  │     ROUTING ENGINE    │  │  SEEN PACKET CACHE   │  │  LOAD BALANCER   │  │
│  │ - Controlled Flooding │  │ - Triệt tiêu bão lặp │  │ - Least-Load     │  │
│  │ - Giới hạn TTL = 5    │  │ - ConcurrentHashMap  │  │ - Heartbeat 3s   │  │
│  │ - Strict Source Route │  │ - Atomic checkAndMark│  │ - Auto Failover  │  │
│  └───────────────────────┘  └──────────────────────┘  └──────────────────┘  │
│                                                                             │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │ DATA INTEGRITY: Mã băm SHA-256 Checksum bảo vệ toàn vẹn tọa độ        │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │
                                       ▼ (Truyền qua TCP Socket từng chặng)
┌─────────────────────────────────────────────────────────────────────────────┐
│  TẦNG 1: GIAO VẬN KẾT NỐI (TRANSPORT SOCKET LAYER - SERVENT)                │
│                                                                             │
│  ┌────────────────────────────────┐     ┌────────────────────────────────┐  │
│  │    SOCKET SERVER (SERVER)      │     │     SOCKET CLIENT (CLIENT)     │  │
│  │  - Lắng nghe cổng TCP đa luồng │     │  - Khởi tạo kết nối từng chặng │  │
│  │  - Reactor Worker Thread Pool  │     │  - Tự động thử lại (Retry 3 lần│  │
│  └────────────────────────────────┘     └────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────┘
```

1. **Tầng Giao vận (Transport Layer - Java Socket):**
   * Sử dụng liên kết TCP Socket từng chặng (**Hop-by-Hop TCP Connection**).
   * Mỗi Node đều chạy đồng thời cả `SocketServer` (để nhận gói) và `SocketClient` (để chuyển tiếp), tạo nên cấu trúc **Servent (Server + Client)**.
2. **Tầng Mạng & Định tuyến (Routing Layer):**
   * **Controlled Flooding (Lan truyền có kiểm soát):** Dùng trường `TTL` (Time-To-Live, mặc định = 5) giảm dần qua mỗi bước nhảy để triệt tiêu gói tin lạc trôi vĩnh viễn.
   * **SeenPacketCache (Triệt tiêu bão gói - Anti-Storm):** Bảng băm nguyên tử `ConcurrentHashMap` lưu `packetId (UUID)`. Nếu gói tin đã từng đi qua node này, node lập tức `DROP`, không phát tán tiếp.
   * **Strict Source Routing (Định tuyến nguồn chỉ định):** Gói tin lệnh chỉ đạo mang theo mảng `designatedRoute = ["BASE_STATION", "NODE_B1_RELAY", "NODE_A_VICTIM"]`, ép gói tin đi đúng luồng relay tối ưu.
   * **Load Balancing (Phân bố tải động):** Thuật toán *Least-Load First*. Các Relay gửi bản tin `LOAD_REPORT` (nhịp tim) mỗi 3 giây; Trạm chỉ huy sẽ ưu tiên gửi lệnh qua Relay có tải thấp nhất.
   * **Dynamic Reverse Route Learning (Học đường truyền ngược):** Khi Relay nhận gói SOS từ Victim qua socket, Relay ghi nhớ ánh xạ `(NodeID ➔ IP:Port)` để khi Trạm chỉ huy phản hồi, Relay biết chính xác IP của Nạn nhân để chuyển tiếp ngược lại.
3. **Tầng Ứng dụng & Chỉ huy (Application & C2 Dashboard):**
   * Giao diện JavaFX hiện đại (phong cách Apple iOS Dark Mode qua AtlantaFX).
   * Bản đồ thực địa số hóa Leaflet tích hợp trực tiếp chế độ lưới tọa độ ngoại tuyến (Tactical Grid Fallback).
   * Bộ tổng hợp âm thanh còi hú báo động dã chiến qua thuật toán sóng âm *Phase Accumulation* (không phụ thuộc file `.mp3` ngoài).

---

## 🛠️ PHẦN 3: CÁC KỊCH BẢN DEMO THỰC TẾ & HƯỚNG DẪN TỪNG BƯỚC

Hệ thống được thiết kế linh hoạt, cho phép chạy thử nghiệm từ 1 laptop duy nhất cho đến cụm 4 laptop nối mạng Wi-Fi dã chiến:

#### 📊 SƠ ĐỒ 3 KỊCH BẢN TRIỂN KHAI:

```text
┌─────────────────────────────────────────────────────────────────────────────┐
│ KỊCH BẢN 1: MÔ PHỎNG 4 NODES TRÊN 1 MÁY LAPTOP                              │
│                                                                             │
│ [Node A Victim] ────► [Relay B1 (8002)] ────┐                               │
│    (Port 8001)                               ├──► [Base Station (Port 8888)]│
│                 ────► [Relay B2 (8003)] ────┘      (Dashboard + Map)        │
│                       (Chia tải Least-Load)                                 │
└─────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────┐
│ KỊCH BẢN 2: THỰC TẾ 2 MÁY LAPTOP QUA SÓNG WI-FI HOTSPOT (KHUYÊN DÙNG)       │
│                                                                             │
│         [MÁY 1]                                           [MÁY 2]           │
│  ┌─────────────────────────┐                      ┌──────────────────────┐  │
│  │ - Base Station (8888)   │◄──── Sóng Wi-Fi ────►│ - Relay Node B1      │  │
│  │ - Node A Victim (8001)  │                      │   (Cổng 8002)        │  │
│  └─────────────────────────┘                      └──────────────────────┘  │
│                                                                             │
│  * Luồng tin: Node A (Máy 1) ──► Relay (Máy 2) ──► Base Station (Máy 1)    │
│  * Luồng lệnh: Base Station (Máy 1) ──► Relay (Máy 2) ──► Node A (Máy 1)   │
└─────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────┐
│ KỊCH BẢN 3: MẠNG LƯỚI MỞ RỘNG 3 - 4 MÁY LAPTOP ĐỘC LẬP                      │
│                                                                             │
│  [MÁY 4: Victim] ──Wi-Fi──► [MÁY 2: Relay B1] ──Wi-Fi──► [MÁY 1: Base]      │
│  (Port 8001)                (Port 8002)                  (Port 8888)        │
│          │                                                       ▲          │
│          └─────────Wi-Fi──► [MÁY 3: Relay B2] ──Wi-Fi────────────┘          │
│                             (Port 8003)                                     │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

### 📋 Kịch bản 1: Demo trên 1 máy Laptop (Mô phỏng 4 Nodes - Cân bằng tải & Chịu lỗi)
* **Mục đích:** Chứng minh toàn bộ giải thuật định tuyến, giao diện chỉ huy, phân bố tải và khả năng chịu lỗi (Failover) mà không cần thêm thiết bị phụ.
* **Cách khởi động:**
  1. Mở thư mục dự án, nhấp đúp chạy file:
     ```cmd
     RUN_DEMO_4NODES_LOAD_BALANCING.bat
     ```
  2. Hệ thống sẽ tự động bật 4 cửa sổ theo đúng thứ tự:
     * Cửa sổ 1: **Trạm Chỉ Huy (Base Station)** — Port `8888`.
     * Cửa sổ 2: **Relay Node B1** — Port `8002` (gửi nhịp tim 3s về 8888).
     * Cửa sổ 3: **Relay Node B2** — Port `8003` (chạy song song chia tải).
     * Cửa sổ 4: **Node A Victim** — Port `8001` (trỏ sang Relay).
* **Các bước biểu diễn trước Thầy Cô:**
  * **Bước 1 (Giám sát tải):** Chỉ vào Dashboard Base Station, cho thấy thẻ Relay B1 và B2 đều báo `✅ ONLINE` với thời gian heartbeat nhảy nhịp thực tế.
  * **Bước 2 (Gửi SOS):** Tại Node A, bấm **"🚨 GỬI TÍN HIỆU SOS"** ➔ Còi hú vang lên tại Base Station, số liệu CRITICAL nhảy số, bảng xuất hiện hàng đỏ và bản đồ tự động lướt đến vị trí ký túc xá VKU cắm ghim cảnh báo.
  * **Bước 3 (Chống lặp):** Bấm gửi SOS thêm 2 lần nữa ➔ Chỉ vào log Node B và Base Station: gói tin bị `[DROP] ... DUPLICATE`, chứng minh không bị bão nghẽn mạng.
  * **Bước 4 (Phân bố tải & Chỉ huy ngược):** Tại Base Station, chọn nạn nhân, nhập lệnh *"Ca-nô cứu hộ đang đến"* rồi bấm **Gửi Lệnh** ➔ Hệ thống tự động chọn Relay có tải thấp nhất, Node A nhận ngay thẻ chỉ thị tác chiến màu xanh.
  * **Bước 5 (Kịch bản sự cố Failover):** Tắt cửa sổ Relay B1 (giả lập node bị lũ cuốn trôi) ➔ Base Station ghi nhận B1 `OFFLINE`, khi phát lệnh tiếp theo, hệ thống tự động bẻ luồng sang Relay B2 còn sống!

---

### 📋 Kịch bản 2: Demo trên 2 máy Laptop (Mô hình thực tế qua sóng Wi-Fi — KHUYÊN DÙNG TỐT NHẤT)
* **Ưu điểm:** Chứng minh được gói tin thực sự bay qua không gian vô tuyến giữa 2 máy tính độc lập; tỷ lệ thành công 100%, không bị ảnh hưởng bởi lỗi định tuyến ngược phức tạp.
* **Phân công máy:**
  * **MÁY 1 (Laptop của bạn):** Đóng vai trò **Trạm Chỉ Huy (Base Station)** + **Nạn Nhân (Node A Victim)**.
  * **MÁY 2 (Laptop bạn bè/thầy cô):** Đóng vai trò **Trạm Tiếp Sóng Trung Gian (Relay Node B1)**.
* **Chuẩn bị môi trường (BẮT BUỘC):**
  1. Cả 2 máy cùng bắt vào **1 mạng Wi-Fi phát từ điện thoại (Mobile Hotspot)**.
  2. Mở cổng Firewall trên cả 2 máy (xem mục 3.4).
* **Cách khởi động:**
  * **Trên Máy 2 (Relay):**
    1. Chạy file `CHAY_2MAY_MAY2_RELAY.bat`.
    2. Cửa sổ hiện IP của Máy 2 (ví dụ: `192.168.43.20`).
    3. Nhập IP của Máy 1 (ví dụ: `192.168.43.10`) theo hướng dẫn trên màn hình.
  * **Trên Máy 1 (Base Station + Victim):**
    1. Chạy file `CHAY_2MAY_MAY1_BASE_VA_VICTIM.bat`.
    2. Cửa sổ hiện IP của Máy 1 (ví dụ: `192.168.43.10`).
    3. Nhập IP của Máy 2 (`192.168.43.20`) vào màn hình.
* **Luồng truyền tin thực tế:**
  1. Bạn bấm gửi SOS tại Node A trên Máy 1 ➔ Gói tin bay qua sóng Wi-Fi sang Máy 2.
  2. Máy 2 (Relay) nhận được, hiển thị log tiếp sóng ➔ Đẩy ngược gói tin qua sóng Wi-Fi về Base Station trên Máy 1.
  3. Base Station trên Máy 1 hú còi báo động, cắm cờ bản đồ.
  4. Base Station phát lệnh cứu hộ ➔ Bay qua Máy 2 ➔ Máy 2 trả về Node A trên Máy 1.

---

### 📋 Kịch bản 3: Demo trên 3 hoặc 4 máy Laptop (Mỗi máy 1 Node độc lập)
* **Phân công máy:**
  * **Máy 1:** Base Station (`192.168.43.10:8888`) — Trạm chỉ huy.
  * **Máy 2:** Relay Node B1 (`192.168.43.20:8002`) — Trạm tiếp sóng 1.
  * **Máy 3:** Relay Node B2 (`192.168.43.30:8003`) — Trạm tiếp sóng 2 (chia tải).
  * **Máy 4:** Victim Node A (`192.168.43.40:8001`) — Nạn nhân.
* **Cách khởi động:**
  * Máy 1 chạy: `CHAY_LAPTOP_BASE_STATION.bat`.
  * Máy 2 chạy: `CHAY_LAPTOP_RELAY.bat` (nhập IP Máy 1).
  * Máy 3 chạy: `CHAY_LAPTOP_RELAY_B2.bat` (nhập IP Máy 1).
  * Máy 4 chạy: `CHAY_LAPTOP_VICTIM.bat` (nhập IP Máy 2 hoặc Máy 3).
* **Cơ chế hoạt động:** Gói tin đi từ Máy 4 ➔ Máy 2/3 ➔ Máy 1; Lệnh từ Máy 1 ➔ Máy 2/3 ➔ Máy 4 nhờ cơ chế định tuyến ngược tự động học IP.

---

### ⚠️ 3.4 Bảng kiểm tra kỹ thuật trước giờ Demo (Pre-Flight Checklist)
Để tránh 100% các rủi ro kỹ thuật ngoài hiện trường, thực hiện đúng 3 bước:

1. **Khắc phục tường lửa Windows (Windows Defender Firewall):**
   Mở PowerShell (Run as Administrator) trên **tất cả các máy tham gia** và dán lệnh sau để thông port trong 3 giây:
   ```powershell
   New-NetFirewallRule -DisplayName "Emergency Mesh Ports" -Direction Inbound -LocalPort 8888,8001,8002,8003 -Protocol TCP -Action Allow
   ```
2. **Tránh tuyệt đối Wi-Fi trường học / công cộng:**
   Router tại trường đại học luôn bật chế độ **AP Isolation (Client Isolation)** — ngăn các máy cùng lớp ping thấy nhau.  
   👉 **BẮT BUỘC:** Lấy 1 điện thoại 4G phát Hotspot Wi-Fi cho các laptop kết nối vào.
3. **Kiểm tra ping thông mạng:**
   Mở CMD gõ: `ping <IP_MÁY_CÒN_LẠI>`. Khi nào thấy `Reply from ... time=...ms` mới bắt đầu chạy ứng dụng.

---

## 💾 PHẦN 4: VẤN ĐỀ SAO LƯU DỮ LIỆU (DATA PERSISTENCE & DATABASE)

### 4.1 Hệ thống hiện tại có dùng Database không?
> **Trả lời:** Hiện tại hệ thống hoạt động hoàn toàn bằng **Cấu trúc dữ liệu trong bộ nhớ RAM (In-Memory Architecture)**:
> - Danh sách nạn nhân: `ObservableList<VictimTableRow>` liên kết với giao diện TableView.
> - Danh sách gói tin: `CopyOnWriteArrayList<MeshPacket>` đảm bảo an toàn đa luồng.
> - Bảng kiểm soát trùng lặp: `ConcurrentHashMap<String, Long>` trong `SeenPacketCache`.

---

### 4.2 Tại sao KHÔNG NÊN dùng các Database Server (MySQL, PostgreSQL, SQL Server)?
1. **Bản chất dã chiến (Field Constraints):**
   * Trong tình huống cứu nạn bão lũ, mất điện lưới, các thiết bị tham gia mạng Mesh là các thiết bị biên, laptop chạy pin, mạch IoT LoRa dã chiến.
   * Việc cài đặt và duy trì các hệ quản trị CSDL cồng kềnh tiêu tốn tài nguyên RAM, CPU và yêu cầu dịch vụ nền (Background Service) luôn chạy.
2. **Tính di động khi mang đi Demo (Zero-Dependency Portability):**
   * Nếu dùng MySQL, khi bạn mang file JAR sang máy laptop khác để demo, máy đó **bắt buộc phải cài đúng phiên bản MySQL, đúng cổng 3306, đúng mật khẩu root**. Nếu dịch vụ MySQL chưa bật, ứng dụng sẽ bị crash ngay từ hàm `getConnection()`.
   * Sử dụng In-Memory giúp phần mềm đóng gói trọn vẹn trong 1 file `.jar`, copy sang bất kỳ máy nào có Java là chạy được ngay lập tức.
3. **Hiệu năng thời gian thực (Micro-second Latency):**
   * Xử lý gói tin khẩn cấp trong RAM chỉ mất vài micro-giây, không bị tắc nghẽn I/O ổ đĩa hay chờ hàng đợi khóa bảng (Table Lock).

---

### 4.3 Định hướng kiến trúc dữ liệu cho bản Thương Mại / Đồ Án Tốt Nghiệp
Nếu muốn lưu trữ dữ liệu vĩnh viễn (để tắt máy mở lại không mất dữ liệu), giải pháp chuẩn mực của hệ thống dã chiến là:
* **Phương án 1 (Khuyên dùng - Cực nhẹ): CSDL nhúng SQLite hoặc H2 Database.**
  * Không cần cài đặt server. Toàn bộ cơ sở dữ liệu chỉ là một file duy nhất: `rescue_mission.db`.
  * Thư viện SQLite JDBC tự động đính kèm vào file JAR.
* **Phương án 2 (Hộp đen tác chiến dã chiến - Append-Only JSON/CSV Log):**
  * Mỗi khi Base Station nhận được 1 gói tin SOS, hệ thống tự động ghi nối tiếp (append) vào file `data/nhat_ky_cuu_nan.json` hoặc `.csv`.
  * Vừa nhẹ, vừa có thể mở trực tiếp bằng Excel để báo cáo cho Ban Chỉ huy Phòng chống thiên tai.

---

## ❓ PHẦN 5: BỘ CÂU HỎI PHẢN BIỆN KHI BẢO VỆ & TRẢ LỜI MẪU

Dưới đây là tập hợp **8 câu hỏi "hóc búa"** nhất mà Thầy Cô trong Hội đồng phản biện thường xuyên đặt ra:

1. **[Câu 1](#-câu-1-hệ-thống-của-em-là-client---server-hay-peer-to-peer-p2p):** Hệ thống của em là Client - Server hay Peer-to-Peer (P2P)?
2. **[Câu 2](#-câu-2-thuật-toán-flooding-lan-truyền-rất-dễ-gây-bão-gói-tin-broadcast-storm-làm-sập-mạng-nhóm-giải-quyết-thế-nào):** Chống bão gói tin (Broadcast Storm) trong thuật toán Flooding thế nào?
3. **[Câu 3](#-câu-3-tại-sao-không-dùng-các-giao-thức-định-tuyến-có-sẵn-như-aodv-dsr-hay-ospf-mà-lại-tự-viết-routingengine):** Tại sao không dùng các giao thức AODV, DSR, OSPF mà tự viết RoutingEngine?
4. **[Câu 4](#-câu-4-giao-thức-truyền-tin-có-mã-hóa-không-làm-sao-đảm-bảo-hacker-không-sửa-tọa-độ-nạn-nhân):** Giao thức truyền tin có mã hóa không? Chống giả mạo tọa độ thế nào (SHA-256 Checksum)?
5. **[Câu 5](#-câu-5-tại-sao-hệ-thống-không-dùng-database-mysqlsql-server-tắt-máy-có-mất-dữ-liệu-không):** Tại sao không dùng Database Server (MySQL/SQL Server)? Tắt máy có mất dữ liệu không?
6. **[Câu 6](#-câu-6-bản-đồ-trong-phần-mềm-có-thực-sự-chạy-được-khi-hoàn-toàn-không-có-mạng-internet-không):** Bản đồ trong phần mềm có thực sự chạy được khi hoàn toàn mất Internet không?
7. **[Câu 7](#-câu-7-hệ-thống-đảm-bảo-an-toàn-đa-luồng-thread-safety-như-thế-nào-khi-có-hàng-trăm-kết-nối-cùng-lúc):** Hệ thống đảm bảo an toàn đa luồng (Thread-Safety) như thế nào?
8. **[Câu 8](#-câu-8-5-dòng-lệnh-then-chốt-được-triển-khai-đầu-tiên-khi-khởi-tạo-một-node--base-station-trong-mã-nguồn-là-gì):** 5 dòng lệnh then chốt được triển khai đầu tiên khi khởi tạo dự án trong mã nguồn là gì?

---

### 💬 Câu 1: *"Hệ thống của em là Client - Server hay Peer-to-Peer (P2P)?"*
* **Trả lời chuẩn:**
  > *"Dạ thưa Thầy/Cô, hệ thống của em là **Mô hình hỗn hợp (Hybrid Architecture)** được phân định rõ ràng theo từng tầng:  
  > 1. **Ở tầng Ứng dụng nghiệp vụ (Application Layer):** Đây là mô hình **Chỉ huy dã chiến (Command & Control - C2)**, có Trạm chỉ huy trung tâm (Base Station / Sink Node) và các nút Nạn nhân ở hiện trường.  
  > 2. **Ở tầng Mạng và Định tuyến (Network & Routing Layer):** Đây là **Mạng Mesh phân tán ngang hàng (Ad-hoc Mesh / P2P)**. Các nút không truyền trực tiếp về server mà truyền bắc cầu qua nhiều chặng (Multi-hop) dựa trên thuật toán Controlled Flooding và Strict Source Routing.  
  > 3. **Ở tầng Giao vận (Transport Layer):** Để kết nối điểm-điểm giữa hai nút gần nhau, mỗi máy chạy đồng thời cả `SocketServer` (đón gói) và `SocketClient` (gửi tiếp), tức là mô hình **Servent (Server + Client kết hợp)**."*

---

### 💬 Câu 2: *"Thuật toán Flooding (lan truyền) rất dễ gây bão gói tin (Broadcast Storm) làm sập mạng. Nhóm giải quyết thế nào?"*
* **Trả lời chuẩn:**
  > *"Dạ thưa Thầy/Cô, nhóm em không dùng Flooding vô hạn mà cài đặt thuật toán **Controlled Flooding (Lan truyền có kiểm soát)** với 2 lớp phòng vệ nghiêm ngặt:  
  > 1. **Lớp 1 - Kiểm soát bước nhảy (TTL - Time To Live):** Gói tin khởi tạo có TTL = 5. Mỗi lần qua một Relay, TTL giảm đi 1. Khi TTL <= 1, gói tin bị tiêu hủy ngay lập tức (`DROP: TTL_EXPIRED`), ngăn chặn gói tin chạy vòng lặp vô tận.  
  > 2. **Lớp 2 - Triệt tiêu gói tin trùng lặp (`SeenPacketCache`):** Mỗi gói tin có mã định danh toàn cầu `UUID`. Tại mỗi node, khi gói tin đến, phương thức nguyên tử `checkAndMark(uuid)` sẽ tra cứu trong `ConcurrentHashMap`. Nếu UUID đã từng xuất hiện, gói tin bị `DROP: DUPLICATE` ngay trong vài micro-giây mà không xử lý và không chuyển tiếp tiếp."*

---

### 💬 Câu 3: *"Tại sao không dùng các giao thức định tuyến có sẵn như AODV, DSR hay OSPF mà lại tự viết RoutingEngine?"*
* **Trả lời chuẩn:**
  > *"Dạ, các giao thức như OSPF yêu cầu cấu trúc mạng cố định và trao đổi bảng định tuyến định kỳ, không phù hợp với mạng dã chiến khi các node liên tục di chuyển và mất kết nối đột ngột.  
  > Còn AODV hoặc DSR đòi hỏi pha khám phá tuyến (Route Discovery bằng gói RREQ/RREP) tốn nhiều thời gian chờ trước khi gửi được tin khẩn cấp. Trong cứu nạn, **tính cấp bách là số một** — nạn nhân bấm nút thì gói tin phải được bắn đi ngay lập tức. Vì vậy, giải pháp **Controlled Flooding kết hợp Strict Source Routing** và **Least-Load Balancing** là giải pháp cân bằng hoàn hảo nhất giữa tốc độ tức thì và độ tin cậy."*

---

### 💬 Câu 4: *"Giao thức truyền tin có mã hóa không? Làm sao đảm bảo hacker không sửa tọa độ nạn nhân?"*
* **Trả lời chuẩn:**
  > *"Dạ thưa Thầy/Cô, hệ thống bảo vệ dữ liệu bằng cơ chế **Kiểm tra tính toàn vẹn (Data Integrity) qua mã băm SHA-256 Checksum** theo chuẩn RFC 6234:  
  > - Khi nạn nhân tạo gói tin SOS, hàm `computeAndSetChecksum()` sẽ băm toàn bộ payload (kinh độ, vĩ độ, nội dung, mức độ khẩn cấp).  
  > - Suốt dọc đường truyền qua các Relay, các relay chỉ được phép thay đổi metadata routing (TTL, hopCount, routeHistory) chứ không được chạm vào payload.  
  > - Khi gói tin đến bất kỳ node nào, hàm `verifyChecksum()` sẽ tính lại mã băm. Nếu có bất kỳ sự can thiệp làm sai lệch tọa độ dù chỉ 1 bit, checksum sẽ sai lệch hoàn toàn và gói tin lập tức bị hủy với mã lỗi `CHECKSUM_FAIL`."*

---

### 💬 Câu 5: *"Tại sao hệ thống không dùng Database (MySQL/SQL Server)? Tắt máy có mất dữ liệu không?"*
* **Trả lời chuẩn:**
  > *(Dùng nguyên văn câu trả lời chuẩn ở **Mục 4.2 và 4.3** ở trên để trả lời: Nhấn mạnh tính dã chiến, tính độc lập di động không phụ thuộc server ngoài và định hướng tích hợp SQLite/Append-Only Log trong Phase thương mại).*

---

### 💬 Câu 6: *"Bản đồ trong phần mềm có thực sự chạy được khi hoàn toàn không có mạng Internet không?"*
* **Trả lời chuẩn:**
  > *"Dạ hoàn toàn được. Nhóm em đã giải quyết triệt để vấn đề này qua 2 lớp:  
  > 1. **Lớp 1 (Local Assets):** Toàn bộ mã nguồn thư viện `leaflet.js` và `leaflet.css` đã được tải về lưu nội bộ trong thư mục `resources/map/vendor/`, WebEngine đọc trực tiếp từ ổ đĩa nội bộ mà không cần tải từ CDN unpkg.  
  > 2. **Lớp 2 (Tactical Grid Fallback):** Trong tình huống dã chiến không thể tải ảnh vệ tinh từ OpenStreetMap/CartoDB, bản đồ tự động kích hoạt **Lưới Tọa Độ Quân Sự (Tactical Radar Canvas)**, vẽ lại các trục tọa độ GPS, hiển thị đầy đủ Trạm chỉ huy và các điểm nạn nhân nhấp nháy trên nền radar chuyên nghiệp."*

---

### 💬 Câu 7: *"Hệ thống đảm bảo an toàn đa luồng (Thread-Safety) như thế nào khi có hàng trăm kết nối cùng lúc?"*
* **Trả lời chuẩn:**
  > *"Dạ, hệ thống áp dụng triệt để 3 nguyên lý thiết kế đa luồng:  
  > 1. **Non-blocking Server:** `SocketServer` dùng mô hình Reactor đơn giản với `CachedThreadPool` tách biệt luồng đón kết nối (`acceptThread`) khỏi các luồng đọc ghi dữ liệu (`handleClient`).  
  > 2. **Thread-safe Data Structures:** Dùng `ConcurrentHashMap` cho Cache chống lặp và Bảng phân bố tải; dùng `CopyOnWriteArrayList` cho danh sách gói tin SOS; dùng `AtomicInteger` cho các bộ đếm tải.  
  > 3. **Thread Confinement trên UI:** JavaFX cấm can thiệp giao diện từ worker thread, do đó 100% sự kiện từ tầng mạng đẩy lên màn hình đều được đồng bộ an toàn qua `Platform.runLater()`."*

---

### 💬 Câu 8: *"5 dòng lệnh then chốt được triển khai đầu tiên khi khởi tạo một Node / Base Station trong mã nguồn là gì?"*
* **Trả lời chuẩn:**
  Dạ thưa Thầy/Cô, khi ứng dụng bắt đầu khởi chạy (từ hàm `main()` qua `init()` đến `initializeStation()` hoặc `initializeNode()`), quy trình khởi động hệ thống (Bootstrap Sequence) được thực thi tuần tự qua **5 dòng lệnh nền tảng** sau:

  1. **Dòng 1 — Nạp và phân tích tham số cấu hình Node:**
     ```java
     NodeConfig config = NodeConfig.fromArgs(args);
     ```
     *(Đọc vai trò node `VICTIM` / `RELAY` / `BASE_STATION`, cổng lắng nghe `listenPort`, địa chỉ IP và cổng trạm kế tiếp `nextHopHost:nextHopPort`).*

  2. **Dòng 2 — Khởi tạo Bộ điều phối cân bằng tải:**
     ```java
     loadBalancer = new LoadBalancer();
     ```
     *(Tạo bảng định tuyến thời gian thực bằng `ConcurrentHashMap` để sẵn sàng theo dõi nhịp tim và tải của các relay).*

  3. **Dòng 3 — Khởi tạo Lõi định tuyến mạng Mesh:**
     ```java
     routingEngine = new RoutingEngine(config.getNodeId(), config.getNextHopHost(), config.getNextHopPort(), this);
     ```
     *(Kích hoạt bộ não định tuyến, tự động bật bộ đệm `SeenPacketCache` chống bão lặp gói tin và bộ xác thực SHA-256 Checksum).*

  4. **Dòng 4 — Khởi tạo Máy chủ Socket TCP đa luồng:**
     ```java
     socketServer = new SocketServer(config.getListenPort(), routingEngine, this);
     ```
     *(Gán cổng TCP lắng nghe và liên kết trực tiếp với `RoutingEngine` để trao các gói tin nhận được cho worker pool xử lý).*

  5. **Dòng 5 — Kích hoạt luồng lắng nghe socket nền (Non-blocking):**
     ```java
     socketServer.start();
     ```
     *(Mở `ServerSocket.accept()` chạy trong luồng `acceptThread` riêng biệt, bắt đầu tiếp nhận kết nối từ các máy khác mà không làm đơ giao diện người dùng).*

  > 💡 **Ghi chú mở rộng:** Nếu node chạy ở vai trò **RELAY**, hệ thống sẽ kích hoạt thêm luồng scheduler gửi nhịp tim định kỳ:  
  > `startHeartbeatScheduler();`  
  > để tự động báo cáo tải (`LOAD_REPORT`) về Trạm chỉ huy định kỳ mỗi 3 giây.

---

## 📊 PHẦN 6: BẢNG TỔNG HỢP CÁC DESIGN PATTERNS ÁP DỤNG TRONG DỰ ÁN

| Design Pattern | Lớp áp dụng | Mục đích thiết kế |
| :--- | :--- | :--- |
| **Reactor Pattern (Simplified)** | `SocketServer.java` | Tách biệt luồng lắng nghe `accept()` và cụm luồng worker pool xử lý song song nhiều kết nối TCP cùng lúc. |
| **Strategy Pattern** | `RoutingCallback`, `LoadBalancer.java` | Độc lập hóa thuật toán chọn đường (Least-Load / Round-Robin) và tách biệt nghiệp vụ xử lý gói tin khỏi giao diện UI. |
| **Gateway Pattern** | `SocketClient.java` | Đóng gói toàn bộ cơ chế mở Socket, thiết lập timeout, retry 3 lần vào một interface gọi hàm duy nhất. |
| **Value Object** | `NodeConfig.java`, `MeshPacket.java` | Đảm bảo tính toàn vẹn, bất biến của cấu hình node và định dạng chuẩn hóa của gói tin xuyên suốt mạng. |
| **Adapter Pattern** | `VictimTableRow.java` | Chuyển đổi linh hoạt từ gói tin mạng `MeshPacket` sang đối tượng hiển thị tương thích với `TableView` của JavaFX. |
| **Model - View - Controller (MVC)** | `BaseStationController`, `NodeClientController` | Tách rời hoàn toàn giao diện FXML (View), logic điều khiển (Controller) và dữ liệu nghiệp vụ (Model). |

---

## 🏁 PHẦN 7: TỔNG KẾT & ĐỊNH HƯỚNG PHÁT TRIỂN (ROADMAP)

* **Giai đoạn hiện tại (Phase 1 - Proof of Concept & Desktop C2 Station):**
  * Hoàn thành xuất sắc việc chứng minh giải thuật định tuyến nguồn đa bước nhảy (Multi-Hop Mesh Routing), chống bão gói tin, phân bố tải (Least-Load Balancing) và Trạm chỉ huy dã chiến trên nền tảng Desktop Java 21 / JavaFX.
* **Giai đoạn tiếp theo (Phase 2 - Mobile & IoT Edge Expansion):**
  * Đóng gói mã nguồn `RoutingEngine` và `MeshPacket` thành thư viện dùng chung (Core Library).
  * Chuyển giao sang ứng dụng di động **Android** giao tiếp ngoại tuyến qua **Wi-Fi Direct (P2P)** và **Bluetooth Low Energy (BLE Mesh)**.
  * Tích hợp modem phần cứng **LoRa (Long Range - 433MHz/915MHz)** cho phép truyền tín hiệu cứu nạn xuyên vật cản lên tới 5 – 10 km mà không cần bất kỳ hạ tầng mạng nào.

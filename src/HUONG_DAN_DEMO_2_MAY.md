# 🛰️ HƯỚNG DẪN TRIỂN KHAI & KỊCH BẢN DEMO TRÊN 2 MÁY TÍNH
## Đề tài: Hệ thống Điều hành Cứu nạn Khẩn cấp Ngoại tuyến qua Mạng Mesh (Emergency Mesh Rescue)
*Môn học: Lập trình mạng nâng cao — VKU*

---

## 📌 PHẦN 1: TỔNG QUAN & NGUYÊN LÝ HOẠT ĐỘNG TRÊN 2 MÁY

### 1.1. Tại sao tuyệt đối KHÔNG CẦN cài máy ảo (VMware / VirtualBox)?
* **Nặng máy và rủi ro cao:** Cài máy ảo chiếm nhiều RAM/CPU, dễ gây lag khi chạy JavaFX và bản đồ WebEngine. Đặc biệt, cấu hình mạng máy ảo (NAT vs Bridged) rất hay bị xung đột IP, chặn port, khiến 2 máy không kết nối được khi đem lên lớp.
* **Bản chất tầng mạng TCP/IP:** Trong mô hình TCP/IP, một kết nối mạng được định danh duy nhất bởi **Socket Address = {IP, Port}**. 
  * Hai tiến trình khác nhau trên cùng một máy chỉ cần lắng nghe trên **2 Port khác nhau** là hoàn toàn độc lập về mặt socket (`Port 8888` cho Base Station, `Port 8001` cho Victim).
  * Việc đóng gói Node dưới dạng các tiến trình chạy trên các port riêng biệt chính là cách tiếp cận chuẩn mực của các hệ thống phân tán (Distributed Systems) và mạng mô phỏng (Network Simulation).

### 1.2. Sơ đồ kiến trúc "Vòng lặp Wi-Fi đa chặng" (Physical Multi-hop Loop)
Gói tin không hề truyền "nội bộ trong RAM" mà được bắn **2 lần qua sóng Wi-Fi vật lý** giữa 2 laptop:

```
           MÁY 1 (Ví dụ IP: 192.168.43.10)                        MÁY 2 (Ví dụ IP: 192.168.43.20)
┌──────────────────────────────────────────────┐              ┌──────────────────────────────────────────────┐
│                                              │              │                                              │
│  [Tiến trình 1: NẠN NHÂN - NODE A]           │              │  [Tiến trình 1: TRẠM TIẾP SÓNG - NODE B1]   │
│  - Cổng lắng nghe: 8001                      │              │  - Cổng lắng nghe: 8002                      │
│  - Giao diện nhập thông tin SOS              │              │  - Nhận tin, trừ TTL, tăng Hop count         │
│  - Gửi đến: 192.168.43.20:8002               │─(Wi-Fi Hop 1)►  - Báo tải Heartbeat mỗi 3 giây             │
│                                              │              │  - Chuyển tiếp đến: 192.168.43.10:8888       │
│                                              │              │                                              │
│                                              │              │  [Tùy chọn: RELAY B2 - LOAD BALANCING]       │
│                                              │              │  - Cổng lắng nghe: 8003                      │
│                                              │              │  - Chạy song song chia tải với B1            │
│                                              │              │                                              │
│  [Tiến trình 2: TRẠM CHỈ HUY - BASE STATION] │◄(Wi-Fi Hop 2)┼──────────────────────────────────────────────┘
│  - Cổng lắng nghe: 8888                      │              │
│  - Dashboard theo dõi tải các Relay          │              │
│  - Bản đồ Leaflet số hóa + Còi hú SOS        │              │
│  - Điều phối lệnh cứu hộ (Dispatch)          │              │
│                                              │              │
└──────────────────────────────────────────────┘              └──────────────────────────────────────────────┘
```

#### Chứng minh tính xác thực cho Giảng viên:
1. **Chặng 1 (Hop 1):** Node A (Máy 1) tạo gói tin $\rightarrow$ Gửi qua card mạng Wi-Fi sang Máy 2 (Port 8002).
2. **Chặng 2 (Hop 2):** Node B1 (Máy 2) xử lý định tuyến $\rightarrow$ Gửi ngược qua card mạng Wi-Fi về Máy 1 (Port 8888).
3. **Kết quả:** Đạt đúng 100% yêu cầu định tuyến **Đa chặng (Multi-hop)**, dữ liệu thực sự bay trong không gian qua sóng vô tuyến Wi-Fi LAN.

---

## ⚙️ PHẦN 2: CHUẨN BỊ TRƯỚC KHI DEMO

### 2.1. Chuẩn bị mạng Wi-Fi
> [!IMPORTANT]
> **Không nên dùng Wi-Fi trường:** Wi-Fi ở các trường đại học thường kích hoạt tính năng **AP Isolation (Client Isolation)**, ngăn các laptop trong cùng mạng ping hoặc truyền Socket trực tiếp cho nhau.
> 
> **Giải pháp tối ưu:** Dùng 1 điện thoại di động **bật Điểm phát sóng cá nhân (Wi-Fi Hotspot)**:
> * Không cần bật dữ liệu di động 4G (đúng chuẩn mạng ngoại tuyến - Offline Mesh!).
> * Cho cả Máy 1 và Máy 2 kết nối vào Wi-Fi này. Mạng sẽ tự cấp phát dải IP nội bộ (thường là `192.168.43.x`).

### 2.2. Tắt Windows Firewall (Bắt buộc)
Windows Firewall sẽ chặn các cổng kết nối TCP đến từ máy ngoài.
Trên **cả 2 máy**, mở CMD hoặc PowerShell bằng quyền **Run as Administrator** và chạy:
```cmd
netsh advfirewall set allprofiles state off
```
*(Sau khi bảo vệ đồ án xong, muốn bật lại chỉ cần gõ: `netsh advfirewall set allprofiles state on`).*

### 2.3. Lấy địa chỉ IP của 2 máy
Mở CMD trên từng máy gõ:
```cmd
ipconfig
```
Tìm dòng **IPv4 Address** của card mạng Wi-Fi:
* Giả sử **Máy 1** là: `192.168.43.10`
* Giả sử **Máy 2** là: `192.168.43.20`

---

## 🚀 PHẦN 3: HƯỚNG DẪN CHI TIẾT TỪNG BƯỚC KHỞI ĐỘNG (STEP-BY-STEP)

### BƯỚC 1: Khởi động MÁY 1 (Trạm chỉ huy + Nạn nhân)
Trên Máy 1, mở 2 cửa sổ CMD độc lập tại thư mục dự án:

#### 1.1. Cửa sổ 1: Chạy Trạm Chỉ Huy (Base Station)
* Gõ lệnh:
  ```cmd
  java -jar target\BaseStationServer-jar-with-dependencies.jar --mode BASE_STATION --port 8888
  ```
* **Hiện tượng:** Cửa sổ giao diện trung tâm xuất hiện (phong cách iOS Dark Mode), chấm tròn trạng thái xanh lá cây, cổng 8888 đang lắng nghe, bản đồ Leaflet sẵn sàng.

#### 1.2. Cửa sổ 2: Chạy Node Nạn Nhân (Node A)
* Trỏ next-hop sang IP của **Máy 2** (cổng 8002):
  ```cmd
  java -jar target\MeshNodeClient-jar-with-dependencies.jar --mode VICTIM --port 8001 --host 192.168.43.20 --next-hop 8002 --id NODE_A_VICTIM
  ```
  *(Nhớ đổi `192.168.43.20` thành IP thực tế của Máy 2).*
* **Hiện tượng:** Cửa sổ giao diện Node Client hiện ra với vai trò `VICTIM`, có form nhập thông tin cứu trợ SOS màu đỏ.

---

### BƯỚC 2: Khởi động MÁY 2 (Trạm tiếp sóng Relay)
Trên Máy 2, mở cửa sổ CMD tại thư mục dự án:

#### 2.1. Cửa sổ 1: Chạy Relay chính (Node B1)
* Trỏ upstream sang IP của **Máy 1** (cổng 8888):
  ```cmd
  java -jar target\MeshNodeClient-jar-with-dependencies.jar --mode RELAY --port 8002 --host 192.168.43.10 --next-hop 8888 --id NODE_B1_RELAY
  ```
  *(Nhớ đổi `192.168.43.10` thành IP thực tế của Máy 1).*
* **Hiện tượng:** Giao diện Relay hiện ra, có log:
  ```text
  [INFO] Heartbeat scheduler đã bắt đầu (mỗi 3 giây)
  [HEARTBEAT] LOAD_REPORT → 192.168.43.10:8888 | load=0 total=0
  ```
* Đồng thời, trên **Máy 1 (Base Station)**, panel MONITORING lập tức cập nhật:
  * Node `NODE_B1_RELAY`: trạng thái `ONLINE`, thời gian heartbeat nhảy theo thời gian thực.

#### 2.2. (Tùy chọn nâng cao - Ăn điểm tuyệt đối): Chạy thêm Relay phụ (Node B2)
Nếu muốn biểu diễn tính năng **Phân bố tải (Load Balancing)** và **Tự phục hồi (Failover)**, mở thêm 1 cửa sổ CMD nữa trên Máy 2:
```cmd
java -jar target\MeshNodeClient-jar-with-dependencies.jar --mode RELAY --port 8003 --host 192.168.43.10 --next-hop 8888 --id NODE_B2_RELAY
```
Lúc này Base Station trên Máy 1 sẽ thấy cả 2 Relay B1 và B2 cùng ONLINE và cạnh tranh tải.

---

### BƯỚC 3: Bố trí màn hình để thuyết trình
* **Máy 1:** Đặt giao diện **Base Station** chiếm 70% màn hình bên trái (nơi có bản đồ và còi hú), đặt giao diện **Node A Victim** ở 30% góc phải.
* **Máy 2:** Đặt giao diện **Relay B1** toàn màn hình để thầy cô thấy rõ luồng log dữ liệu chạy qua máy này.

---

## 🎬 PHẦN 4: KỊCH BẢN DEMO CHI TIẾT (4 MÀN TRÌNH DIỄN)

### Màn 1: Chứng minh kết nối Mesh & Báo cáo tải định kỳ (Heartbeat)
1. **Thao tác:** Không cần bấm gì, chỉ vào màn hình của cả 2 máy.
2. **Quan sát:**
   * Máy 2 (Relay): Mỗi 3 giây in log `[HEARTBEAT] LOAD_REPORT → 192.168.43.10:8888`.
   * Máy 1 (Base Station): Log nhận `[LOAD] NODE_B1_RELAY | load=0 total=0`, thẻ Online Nodes nhảy số 1 (hoặc 2).
3. **Ý nghĩa:** Chứng minh giao thức duy trì trạng thái kết nối mạng ngoại tuyến và thu thập tải động đang hoạt động qua sóng Wi-Fi.

---

### Màn 2: Phát tín hiệu SOS khẩn cấp qua 2 chặng Wi-Fi
1. **Thao tác:** 
   * Trên **Máy 1 (Cửa sổ Node A Victim)**:
     * Chọn Loại thiên tai: `FLOOD_TRAPPED` (Nước ngập cô lập).
     * Mức độ khẩn cấp: `CRITICAL` (Cực kỳ nguy cấp).
     * Số nạn nhân: `3`.
     * Lời nhắn: `"Nước dâng lên mái nhà, có người già và trẻ nhỏ!"`.
     * Nhấn nút: **🚨 GỬI TÍN HIỆU SOS**.
2. **Quan sát hiện tượng tức thì:**
   * **Tại Máy 1 (Node A):** Xuất hiện log `[SEND] SOS -> Gửi đến 192.168.43.20:8002`.
   * **Tại Máy 2 (Relay B1):** Ngay lập tức log nhảy `[FORWARD] Nhận gói từ NODE_A_VICTIM, TTL=5 -> Giảm TTL=4, Tăng Hop=1 -> Chuyển tiếp tới 192.168.43.10:8888`.
   * **Tại Máy 1 (Base Station):**
     * **Còi hú cứu nạn tự động vang lên.**
     * Bảng thống kê tăng số ca `CRITICAL`.
     * Bảng danh sách nạn nhân xuất hiện dòng mới màu đỏ nhấp nháy.
     * **Bản đồ Leaflet tự động zoom mượt mà, ghim marker đỏ chính xác tại vị trí nạn nhân kèm popup chi tiết.**
3. **Ý nghĩa:** Chứng minh trọn vẹn luồng dữ liệu cứu hộ truyền đa chặng qua môi trường vật lý.

---

### Màn 3: Trạm chỉ huy điều phối cứu trợ ngược lại mạng Mesh (Dispatch)
1. **Thao tác:**
   * Trên **Máy 1 (Base Station)**, nhấn nút **TẮT CÒI**.
   * Tại khu vực "ĐIỀU PHỐI CỨU HỘ", chọn nạn nhân: `NODE_A_VICTIM`.
   * Nhập lệnh chỉ huy: `"Đội cứu hộ ca nô số 3 đang đến, giữ nguyên vị trí trên mái!"`.
   * Nhấn nút: **GỬI LỆNH ĐIỀU PHỐI**.
2. **Quan sát hiện tượng:**
   * Base Station áp dụng **Strict Source Routing**, đóng gói lộ trình `[BASE_STATION -> NODE_B1_RELAY -> NODE_A_VICTIM]` và bắn sang Máy 2.
   * Máy 2 (Relay) nhận lệnh `DISPATCH_CMD`, bóc tách header thấy đích tiếp theo là Node A $\rightarrow$ tự động forward ngược về Máy 1 (Port 8001).
   * Tại cửa sổ Node A trên Máy 1: Banner màu xanh xuất hiện với thông báo: *"Nhận lệnh từ Chỉ huy: Đội cứu hộ ca nô số 3 đang đến..."*.

---

### Màn 4 (Đỉnh cao - Điểm 10): Demo Chịu lỗi (Failover) khi Relay bị sập
*(Thực hiện nếu đã mở cả B1 và B2 trên Máy 2)*:
1. **Thao tác:** Đang chạy bình thường, bạn bất ngờ **tắt cửa sổ Relay B1** trên Máy 2 (giả lập tình huống trạm tiếp sóng B1 bị ngập nước hoặc hết pin).
2. **Quan sát:**
   * Sau 10 giây (quá thời gian timeout không nhận heartbeat), trên Base Station của Máy 1, trạng thái của `NODE_B1_RELAY` tự động chuyển sang màu đỏ: `OFFLINE`.
   * Gửi tiếp 1 gói tin SOS hoặc gửi lệnh Dispatch $\rightarrow$ Hệ thống tự động chuyển hướng 100% lưu lượng qua `NODE_B2_RELAY` mà không làm gián đoạn liên lạc!

---

## 🎙️ PHẦN 5: LỜI THOẠI MẪU THUYẾT TRÌNH & TRẢ LỜI GIÁNG VIÊN

### 5.1. Lời thoại mở đầu khi giới thiệu mô hình 2 máy
> *"Kính thưa Thầy/Cô trong Hội đồng, để kiểm chứng hệ thống trong điều kiện thực tế của phòng học, nhóm chúng em triển khai mô phỏng trên **2 máy tính vật lý kết nối qua mạng Wi-Fi nội bộ**.*
> 
> *Để tối ưu hóa thiết bị mà vẫn đảm bảo tính chất **Đa bước nhảy (Multi-hop)**, nhóm tận dụng cơ chế phân định theo **Port của giao thức Socket TCP**.*
> * * **Máy 1 (bên trái):** Đóng đồng thời 2 vai trò: Node A là Nạn nhân phát tín hiệu (Port 8001) và Trạm chỉ huy trung tâm Base Station (Port 8888).*
> * * **Máy 2 (bên phải):** Đóng vai trò Trạm tiếp sóng trung gian Relay (Port 8002).*
> 
> *Khi gói tin SOS được phát đi, nó không truyền nội bộ trên Máy 1 mà bắt buộc phải phát sóng Wi-Fi sang Máy 2 để tiếp sóng, sau đó Máy 2 mới chuyển tiếp ngược lại về Trạm chỉ huy ở Máy 1. Toàn bộ quá trình đi qua **2 chặng mạng vật lý hoàn toàn độc lập**."*

---

### 5.2. Lời thoại khi bấm nút gửi SOS
> *"Em xin phép thực hiện gửi tín hiệu SOS từ Máy 1. Thầy cô quan sát:*
> * *Trên Máy 1, log ghi nhận gói tin được đẩy ra card mạng hướng về IP Máy 2 (`192.168.43.20:8002`).*
> * *Ngay tức thì, tại màn hình Máy 2, trạm Relay đã 'bắt' được gói tin, tự động kiểm tra mã băm SHA-256 để đảm bảo tọa độ không bị giả mạo, giảm TTL xuống 4, tăng Hop count lên 1 và lập tức tiếp sóng ngược về Base Station (`192.168.43.10:8888`).*
> * *Tại Máy 1, Trạm chỉ huy nhận gói tin đích: Còi báo động hú lên bằng thuật toán Phase Accumulation tự sinh sóng âm, bản đồ Leaflet tự động di chuyển đến đúng tọa độ GPS của nạn nhân và hiển thị mức độ ưu tiên cứu hộ."*

---

### 5.3. Ngân hàng câu hỏi giảng viên hay hỏi & Cách trả lời "ăn điểm"

#### ❓ Câu hỏi 1: *"Sao không dùng 3 máy mà lại dùng 2 máy? Có làm giảm tính chân thực của mạng Mesh không?"*
* **Trả lời:** 
  > *"Dạ thưa Thầy/Cô, hệ thống của tụi em được thiết kế theo kiến trúc Module hóa hoàn toàn qua file cấu hình `NodeConfig`. Bản chất của Socket TCP là phân định theo cặp `{IP, Port}`.*
  > *Nếu có 3 máy, chúng em chỉ việc đưa file `MeshNodeClient.jar` sang máy thứ 3 và đổi IP trong 1 giây là chạy được ngay (nhóm đã chuẩn bị sẵn file `CHAY_LAPTOP_VICTIM.bat`).*
  > *Việc gom Node A và Base Station trên Máy 1 với 2 cổng khác nhau vừa tiết kiệm tài nguyên demo trong lớp, vừa chứng minh được cơ chế **Vòng lặp mạng vật lý (Physical Loopback)**: Gói tin bắt buộc phải bay qua không gian sang Máy 2 rồi mới được tiếp sóng quay về Máy 1, hoàn toàn đúng bản chất Multi-hop 2 chặng."*

#### ❓ Câu hỏi 2: *"Làm sao thầy biết gói tin thực sự truyền qua Wi-Fi giữa 2 máy chứ không phải gọi hàm nội bộ trong Java?"*
* **Trả lời:**
  > *"Thưa Thầy/Cô, điều này được chứng minh bằng 3 bằng chứng rõ ràng:*
  > 1. *Hai tiến trình chạy trên 2 máy khác nhau, dùng 2 JVM hoàn toàn độc lập, không hề có chung vùng nhớ hay bộ nhớ chia sẻ (Shared Memory).*
  > 2. *Trên màn hình Máy 2, Thầy/Cô thấy rõ dòng log hiển thị địa chỉ IP nguồn gửi đến từ Máy 1 (`192.168.43.10`).*
  > 3. *Nếu em rút dây mạng hoặc ngắt kết nối Wi-Fi trên Máy 2, gói tin tại Máy 1 lập tức báo lỗi `ConnectException: Connection refused` và không thể đến được Base Station."*

#### ❓ Câu hỏi 3: *"Cơ chế Load Balancing (Phân bố tải) hoạt động như thế nào giữa các Relay?"*
* **Trả lời:**
  > *"Dạ, mỗi Relay node sẽ duy trì một thread gửi định kỳ gói tin `LOAD_REPORT` mỗi 3 giây về Base Station. Trong gói tin này chứa số kết nối đang hoạt động (`activeConnections`) và tổng số gói đã xử lý (`totalProcessed`).*
  > *Base Station sử dụng thuật toán **Least-Load Routing** trong class `LoadBalancer.java`: Khi cần điều phối hoặc phát lệnh, hệ thống sẽ lọc các Relay đang `ONLINE` (có heartbeat trong vòng 10 giây) và chọn Relay có `currentLoad` thấp nhất để chuyển tiếp, tránh hiện tượng nghẽn mạng cục bộ trong thảm họa."*

#### ❓ Câu hỏi 4: *"Nếu trạm Relay bị sập giữa chừng thì sao?"*
* **Trả lời:**
  > *"Dạ, hệ thống có cơ chế **Failover tự động**. Bảng định tuyến tại Base Station theo dõi `lastHeartbeat`. Nếu một Relay không báo cáo trong quá 10 giây, trạng thái của nó sẽ chuyển thành `OFFLINE`.*
  > *Khi đó, thuật toán `selectBestRelayStatus()` sẽ tự động loại bỏ node này và chuyển toàn bộ lưu lượng qua Relay dự phòng còn lại, đảm bảo hệ thống không bị điểm lỗi đơn (Single Point of Failure)."*

---

*Tài liệu chuẩn bị bảo vệ đồ án — Hệ thống Emergency Mesh Rescue*
*Chúc bạn có một buổi demo thành công và đạt điểm xuất sắc!*

# 🛡️ CẨM NANG BẢO VỆ ĐỒ ÁN — EMERGENCY MESH RESCUE SYSTEM

---

## 📌 1. TÊN ĐỀ TÀI & THÔNG TIN CHUNG

* **Tên đề tài tiếng Việt:** 
  > **"Hệ thống điều hành cứu nạn khẩn cấp ngoại tuyến kết hợp mạng Mesh di động và Trạm chỉ huy dã chiến"**
* **Tên đề tài tiếng Anh:** 
  > **"Emergency Mesh Rescue: An Offline Emergency Dispatch System Integrating Mobile Mesh Network and Field Command Station"**
* **Slogan / Thông điệp cốt lõi:**
  > *"Khi viễn thông gục ngã trước thiên tai, mạng Mesh kết nối sự sống."*
* **Đối tượng người dùng:**
  1. **Nạn nhân / Người dân:** Gửi tín hiệu cấp cứu (SOS) không cần Internet/4G.
  2. **Các node trung gian (Relay):** Thiết bị di động của người dân/tình nguyện viên tự động chuyển tiếp tín hiệu.
  3. **Lực lượng cứu hộ (Base Station):** Chỉ huy trung tâm tiếp nhận định vị, điều phối lực lượng và gửi lệnh phản hồi.

---

## ⏱️ 2. KỊCH BẢN THUYẾT TRÌNH MẪU (10 – 12 PHÚT)

```
[00:00 - 02:00] Đặt vấn đề & Tính cấp thiết (Pain Point)
[02:00 - 04:00] Giải pháp & Kiến trúc phân tầng (Architecture)
[04:00 - 08:00] LIVE DEMO TRỰC TIẾP (Kịch bản 3 Nodes)  ← ĐIỂM ĂN TIỀN NHẤT
[08:00 - 10:00] Điểm sáng kỹ thuật & Thuật toán cốt lõi (Tech Deep-Dive)
[10:00 - 12:00] Hướng phát triển Phase 2 & Kết luận
```

### Chi tiết lời thoại từng phần:

#### 🎙️ Phần 1: Đặt vấn đề (2 phút)
* *"Kính thưa Hội đồng, trong các đợt bão lũ lịch sử tại miền Trung và vùng núi phía Bắc, kịch bản chung luôn là: Mất điện $\rightarrow$ Trạm phát sóng BTS tê liệt $\rightarrow$ Mất hoàn toàn sóng 4G/Internet $\rightarrow$ Nạn nhân bị cô lập và đội cứu nạn không nắm được vị trí."*
* *"Để giải quyết bài toán sống còn này, nhóm chúng em xây dựng hệ thống **Emergency Mesh Rescue** — cho phép truyền dữ liệu cứu nạn đa bước nhảy (multi-hop) hoàn toàn ngoại tuyến."*

#### 🎙️ Phần 2: Kiến trúc hệ thống (2 phút)
* Trình bày slide kiến trúc 3 tầng:
  * **Model Layer:** Cấu trúc `MeshPacket` chuẩn hóa JSON, xác thực toàn vẹn bằng SHA-256 Checksum.
  * **Routing Layer:** Thuật toán **Controlled Flooding** kiểm soát bước nhảy qua TTL và triệt tiêu gói tin lặp qua `SeenPacketCache`.
  * **Presentation Layer:** Giao diện JavaFX phong cách Apple iOS 27 Dark Mode tích hợp bản đồ thực địa Leaflet số hóa.

#### 🎙️ Phần 3: Live Demo (4 phút) — *Xem chi tiết ở Mục 3 bên dưới*

#### 🎙️ Phần 4: Điểm nhấn kỹ thuật (2 phút)
* Trình bày ngắn gọn 3 thuật toán:
  1. **Duplicate Suppression:** Cơ chế nguyên tử `checkAndMark` với `ConcurrentHashMap`.
  2. **Data Integrity:** Băm SHA-256 phát hiện giả mạo payload.
  3. **Audio Synthesis:** Thuật toán *Phase Accumulation* sinh còi hú động không cần file âm thanh ngoài.

#### 🎙️ Phần 5: Kết luận & Hướng mở rộng Phase 2 (2 phút)
* Khẳng định Phase 1 đã chứng minh thành công tính đúng đắn của thuật toán (POC).
* Giới thiệu Phase 2: Chuyển giao sang thiết bị di động Android thông qua **Wi-Fi Direct / BLE / LoRa Mesh**.

---

## 🎬 3. QUY TRÌNH LIVE DEMO CHUẨN XÁC (3 CỬA SỔ)

Mở sẵn 3 cửa sổ trên màn hình máy tính (chia layout màn hình: Nửa trái BaseStation, Nửa phải chia đôi Node A và Node B):

1. **Khởi động:**
   * Cửa sổ 1: **Base Station** (Port 8888) — Đèn trạng thái xanh, bản đồ Đà Nẵng sẵn sàng.
   * Cửa sổ 2: **Node B (Relay)** (Port 8002 $\rightarrow$ Next 8888) — Giao diện relay tự động.
   * Cửa sổ 3: **Node A (Victim)** (Port 8001 $\rightarrow$ Next 8002) — Form SOS đầy đủ.
2. **Kịch bản 1: Phát tín hiệu SOS khẩn cấp**
   * Tại Node A: Chọn *Loại: FLOOD_TRAPPED*, *Mức: CRITICAL*, *Số nạn nhân: 3*.
   * Bấm nút **🚨 GỬI TÍN HIỆU SOS**.
   * 👉 **Chỉ cho thầy cô xem:**
     * Node A xuất log: `[SEND] SOS #1 -> Port 8002`.
     * Node B xuất log: `[RECV] -> Giảm TTL=4 -> Tăng Hop=1 -> [RELAY] -> Port 8888`.
     * Base Station: **Còi hú vang lên**, thẻ CRITICAL nhảy số, bảng xuất hiện nạn nhân và **bản đồ Leaflet tự động zoom, cắm ghim đỏ nhấp nháy tại tọa độ nạn nhân**.
3. **Kịch bản 2: Chứng minh thuật toán chống lặp (Anti-Storm)**
   * Bấm gửi SOS thêm lần nữa hoặc gửi trùng UUID.
   * 👉 **Chỉ cho thầy cô xem:** Log Base Station và Node B ghi nhận `[DROP] ... - DUPLICATE`. Giải thích: *"Gói tin đã được xử lý rồi nên hệ thống tự hủy, không relay tiếp để chống nghẽn mạng"*.
4. **Kịch bản 3: Chỉ huy gửi lệnh phản hồi (2-way Dispatch)**
   * Tại Base Station: Chọn nạn nhân `NODE_A_VICTIM`, nhập lệnh: *"Đội ca-nô số 2 đang tiếp cận, hãy di chuyển lên mái nhà"*. Bấm **Gửi Lệnh**.
   * 👉 **Chỉ cho thầy cô xem:** Cửa sổ Node A ngay lập tức hiện thẻ xanh/xanh dương nhận lệnh chỉ đạo từ Trạm chỉ huy!

---

## ❓ 4. BỘ CÂU HỎI PHẢN BIỆN THƯỜNG GẶP & CÂU TRẢ LỜI MẪU

### Câu hỏi 1: *"Tại sao nhóm lại dùng thuật toán Flooding? Flooding có gây nghẽn mạng không?"*
* **Trả lời chuẩn:** 
  > *"Thưa thầy/cô, trong mạng cứu nạn khẩn cấp, cấu trúc liên kết mạng thay đổi liên tục do các node di chuyển hoặc mất kết nối đột ngột, các giao thức định tuyến tĩnh như OSPF hay BGP không thể áp dụng được. Nhóm chọn **Controlled Flooding (Lan truyền có kiểm soát)** vì đây là giải pháp tối ưu nhất để đảm bảo gói tin tìm được đường đến đích mà không cần biết trước cấu trúc mạng.
  > Để chống hiện tượng bão broadcast (Broadcast Storm), nhóm đã cài đặt 2 cơ chế kiểm soát:
  > 1. **Time-To-Live (TTL):** Giới hạn tối đa 5 bước nhảy, gói tin tự hủy khi TTL $\le 0$.
  > 2. **SeenPacketCache:** Sử dụng bảng băm `ConcurrentHashMap` lưu UUID. Mỗi node khi nhận gói tin đều kiểm tra nguyên tử (`putIfAbsent`), nếu đã từng thấy thì DROP ngay lập tức."*

---

### Câu hỏi 2: *"Cơ chế đảm bảo an toàn luồng (Thread-safety) trong chương trình được thiết kế như thế nào?"*
* **Trả lời chuẩn:**
  > *"Hệ thống là một ứng dụng đa luồng phân tán cao:
  > - **Tầng Mạng:** `SocketServer` sử dụng mô hình Reactor rút gọn với 1 luồng Accept và 1 `CachedThreadPool` phân phối các kết nối độc lập.
  > - **Tầng Routing:** `SeenPacketCache` sử dụng `ConcurrentHashMap` đảm bảo các worker thread đọc/ghi cache đồng thời mà không bị Race Condition hay Deadlock. Ngoài ra có 1 thread `ScheduledExecutorService` dọn dẹp cache định kỳ theo thời gian sống TTL (60 giây).
  > - **Tầng Giao diện:** JavaFX không an toàn luồng ngoài JavaFX Application Thread, do đó mọi tương tác từ Socket worker thread lên UI bắt buộc phải được bọc qua `Platform.runLater()`."*

---

### Câu hỏi 3: *"SHA-256 Checksum trong gói tin giải quyết vấn đề gì?"*
* **Trả lời chuẩn:**
  > *"Dữ liệu truyền qua mạng không dây rất dễ bị lỗi bit (Bit-flip) hoặc bị kẻ xấu can thiệp sửa đổi tọa độ GPS, số lượng nạn nhân (Tampering attack). 
  > Nhóm áp dụng **SHA-256 Cryptographic Hash (RFC 6234)** tính toán băm trên chuỗi JSON payload. Khi Base Station hoặc Node nhận gói tin, hàm `verifyChecksum()` sẽ tính lại băm payload và đối chiếu với checksum đính kèm. Nếu không khớp $\rightarrow$ DROP gói tin ngay lập tức vì dữ liệu đã bị biến dạng."*

---

### Câu hỏi 4: *"Làm sao hệ thống hoạt động được khi không có Internet thực tế?"*
* **Trả lời chuẩn:**
  > *"Hiện tại ở Phase 1 Desktop, nhóm giả lập qua TCP Socket trên cổng nội bộ (localhost).
  > Nhưng toàn bộ kiến trúc từ Data Model (`MeshPacket`), Routing Logic (`RoutingEngine`), đến cơ chế Serialization JSON (`Gson`) đều được thiết kế **Platform-Agnostic (Độc lập nền tảng)**.
  > Khi bước sang Phase 2 trên Android, nhóm chỉ cần thay thế tầng Network từ Socket TCP bằng chuẩn **Wi-Fi Direct (P2P)** hoặc **Bluetooth Low Energy (BLE)** hoặc cắm module phần cứng **LoRa 433MHz**. Toàn bộ logic lõi định tuyến của đồ án được tái sử dụng 100% mà không cần viết lại."*

---

### Câu hỏi 5: *"Nếu 2 nạn nhân gửi SOS cùng lúc thì Server có xử lý được không?"*
* **Trả lời chuẩn:**
  > *"Hoàn toàn xử lý được ạ. `SocketServer` tạo ra các worker thread song song cho từng socket kết nối. Khi 2 gói tin đến cùng lúc, 2 worker thread xử lý song song, lưu vào `CopyOnWriteArrayList` và đẩy lên giao diện `TableView` qua hàng đợi của `Platform.runLater()` một cách tuần tự và an toàn."*

---

## 🏆 5. PHÂN CÔNG PHẢN BIỆN (DÀNH CHO NHÓM 2 NGƯỜI)

| Thành viên | Vai trò phụ trách | Trọng tâm trình bày & trả lời |
| :--- | :--- | :--- |
| **Bạn 1 (Trưởng nhóm / Core Logic)** | Kiến trúc mạng & Định tuyến | Trình bày phần Đặt vấn đề, Thuật toán Flooding, SeenPacketCache, Multi-threading, Socket Server/Client, Unit Test. |
| **Bạn 2 (Giao diện & Ứng dụng)** | Presentation & Nghiệp vụ | Điều khiển Live Demo (thao tác 3 cửa sổ), Trình bày thiết kế JavaFX iOS 27, Tích hợp bản đồ Leaflet Bridge, Hệ thống còi hú, Hướng phát triển Phase 2. |

---

## 💡 6. NHỮNG ĐIỀU NÊN & KHÔNG NÊN LÀM

### ✅ NÊN LÀM:
1. **Kiểm tra loa máy tính** trước khi vào phòng thi (để tiếng còi hú kêu to rõ khi bấm SOS).
2. **Mở sẵn 3 Terminal / Cửa sổ** trước giờ vào hội đồng, không để vào phòng mới bắt đầu gõ lệnh khởi động.
3. Mở sẵn file test `mvn test` để chứng minh nhóm có viết kiểm thử tự động đạt **BUILD SUCCESS (7/7 tests passed)**.
4. Giữ phong thái tự tin, trả lời gãy gọn, tập trung vào từ khóa kỹ thuật (*Multi-hop, Flooding, TTL, ConcurrentHashMap, Checksum, Platform.runLater*).

### ❌ TRÁNH:
1. Không nói chung chung *"Chương trình của em gửi tin nhắn qua mạng"*, mà hãy nói *"Chương trình thực hiện truyền nhận gói tin JSON qua TCP Socket đa luồng với cơ chế định tuyến Mesh phi tập trung"*.
2. Tránh bấm gửi liên tục loạn xạ làm hội đồng không kịp nhìn sự biến đổi log trên 3 cửa sổ.
3. Không tranh lời của thành viên cùng nhóm khi thầy cô đặt câu hỏi.

---
*Chúc hai bạn tự tin và đạt điểm A+ tuyệt đối trong buổi bảo vệ đồ án!*

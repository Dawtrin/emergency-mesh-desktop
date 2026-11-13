

# 📡 BẢN CHẤT MẠNG MESH NGOẠI TUYẾN & TÍNH CỨU HỘ TRONG THỰC TẾ
*Tài liệu lý giải cơ chế mạng, tính ngoại tuyến (Offline) và giá trị cứu nạn thực địa*

---

## ❓ 1. VẤN ĐỀ CỐT LÕI: "TẠI SAO CÙNG MẠNG THÌ VẪN LÀ CỨU HỘ NGOẠI TUYẾN?"

Rất nhiều người thường nhầm lẫn giữa hai khái niệm:
1. **Môi trường truyền dẫn sóng vật lý (Physical Link):** Sóng Wi-Fi, sóng Radio, cáp mạng LAN, Bluetooth.
2. **Mạng Internet toàn cầu (Global Internet):** Đường cáp quang nối từ nhà mạng (Viettel, VNPT, FPT) để truy cập Google, Facebook, máy chủ đám mây.

---

### 🌐 Phân biệt cụ thể trong bài toán cứu nạn:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ 🔴 KHI THIÊN TAI XẢY RA (Bão lũ, sạt lở đất, động đất):                    │
│   1. Mất điện lưới toàn vùng.                                              │
│   2. Đứt cáp quang Internet.                                               │
│   3. Các trạm BTS phát sóng 4G/5G sập hoặc hết pin dự phòng sau 2 giờ.     │
│   ==> TOÀN BỘ KHU VỰC RƠI VÀO TÌNH TRẠNG "MÙ THÔNG TIN" (BLACKOUT).         │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ 🟢 GIẢI PHÁP MẠNG MESH NGOẠI TUYẾN (CỦA ĐỒ ÁN):                             │
│   1. KHÔNG CẦN INTERNET: Router Wi-Fi dã chiến/Hotspot không cắm dây mạng,  │
│      tắt 4G hoàn toàn. Không tốn 1 byte băng thông Internet.                │
│   2. GIAO TIẾP THIẾT BỊ - ĐẾN - THIẾT BỊ (P2P / Ad-hoc):                   │
│      Các máy tự phát sóng và bắt tay trực tiếp qua giao thức Socket nội bộ. │
│   3. ĐỊNH TUYẾN ĐA BƯỚC NHẢY (MULTI-HOP):                                   │
│      Chuyển tiếp gói tin qua các trạm trung gian vượt rào cản cự ly.        │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 🛰️ 2. TẠI SAO PHẢI CẦN MẠNG MESH (MULTI-HOP) MÀ KHÔNG GỬI THẲNG?

Trong địa hình chia cắt (đồi núi, vùng lũ cô lập), tầm phủ sóng không dây của một thiết bị thông thường chỉ đạt **50m – 100m**.

```
[Node A: Nạn nhân]            [Node B: Người dân/Relay]             [Base Station: Cứu hộ]
 Ở sâu trong vùng ngập                 Ở rìa vùng ngập                   Tại trụ sở dã chiến
       │                                     │                                    │
       ├──── Sóng chỉ tới được 100m ────────►│                                    │
       │     (Không thể tới Base Station)    ├──── Tiếp sóng thêm 100m ──────────►│
       │                                     │     (Chuyển tiếp thành công)       │
```

* **Nếu không có Mesh:** Node A cách Base Station 200m $\rightarrow$ Mất liên lạc hoàn toàn $\rightarrow$ Nạn nhân không được cứu.
* **Khi có Mesh:** Node B ở giữa đóng vai trò **"Trạm tiếp sóng sống" (Relay Node)**:
  * Node B nhận tín hiệu SOS từ Node A.
  * Node B kiểm tra tính toàn vẹn (SHA-256), giảm TTL, tăng Hop.
  * Node B chuyển tiếp tiếp sang Base Station.
* 👉 **Đây chính là bản chất sống còn của Mạng Mesh:** Dùng chính thiết bị của cộng đồng xung quanh để nối dài cánh tay cứu hộ!

---

## 🏗️ 3. CƠ CHẾ TRIỂN KHAI THỰC TIỄN TRÊN NHIỀU MÁY / THIẾT BỊ (REAL-WORLD MULTI-DEVICE ARCHITECTURE)

Khi đưa hệ thống ra thực địa với hàng chục, hàng trăm thiết bị di động độc lập (không còn là mô phỏng), hệ thống sẽ vận hành dựa trên **5 cơ chế kỹ thuật cốt lõi**:

### 🔹 Cơ chế 1: Tầng truyền dẫn vô tuyến P2P (Physical Layer)
Trong thực tế không có router Wi-Fi cố định, các thiết bị sẽ kết nối qua 3 công nghệ:
* **Wi-Fi Direct (Wi-Fi P2P):** Các điện thoại tự bắt tay trực tiếp ngang hàng ở tốc độ cao (tương tự Apple AirDrop / Android Quick Share) trong cự ly **100m – 200m**.
* **Bluetooth Low Energy (BLE Mesh):** Chạy ngầm liên tục với mức tiêu hao pin siêu thấp (< 1% pin/ngày), tự động duy trì liên kết trong bán kính **30m – 50m**.
* **Mô-đun LoRa (Long Range - 433MHz/915MHz):** Thiết bị phần cứng nhỏ gọn bằng bao diêm gắn vào balo cứu hộ, xuồng cứu nạn hoặc drone. Sóng vô tuyến tần số thấp có khả năng **xuyên rừng, đồi núi với tầm xa 2km – 10km**.

### 🔹 Cơ chế 2: Tự động khám phá láng giềng (Dynamic Neighbor Discovery)
* **Beacon Broadcast:** Mỗi thiết bị phát xung nhận diện định kỳ mỗi 5–10 giây: *"Tôi là Node_X, đang ở vị trí Y"*.
* **Bắt tay tự động (Zero-Config Handshake):** Khi hai người cầm điện thoại đi vào vùng phủ sóng của nhau, hai máy tự động kết nối mà người dùng không cần thao tác gì.
* **Cập nhật Topo mạng động:** Nếu một người di chuyển ra xa, đường truyền tự ngắt; khi có người mới đến gần, đường truyền mới tự động được bổ sung vào bảng định tuyến.

### 🔹 Cơ chế 3: Chuyển tiếp "Gặp đâu gửi đó" (Store-Carry-Forward / DTN)
Trong thực địa thảm họa, không phải lúc nào cũng có sẵn một chuỗi người đứng xếp hàng liên tục. Hệ thống ứng dụng nguyên lý **Mạng chịu trễ (Delay-Tolerant Networking)**:

```
[Nạn nhân A] ──(Gửi SOS)──► [Người đi bộ B] ──(Mang gói tin đi 1km)──► [Ca-nô Cứu hộ C] ──► [Trạm Chỉ Huy]
(Ở sâu trong làng)           (Gói tin lưu trong máy B)                (Tiếp cận vùng sóng)
```
1. **Store (Lưu trữ):** Khi Node A phát SOS nhưng chưa có trạm cứu hộ, gói tin được mã hóa lưu tạm vào bộ nhớ máy láng giềng (Node B).
2. **Carry (Mang theo):** Người dân (Node B) di chuyển bằng xuồng hoặc đi bộ ra phía ngoài an toàn.
3. **Forward (Chuyển tiếp):** Ngay khi Node B tiếp cận vùng sóng của Ca-nô cứu hộ hoặc Trạm chỉ huy, gói tin của Node A sẽ **tự động được đẩy lên Trạm chỉ huy ngay lập tức**.

### 🔹 Cơ chế 4: Chống nghẽn sóng và bảo mật toàn vẹn (Anti-Storm & Data Integrity)
* **Khử trùng lặp (Duplicate Suppression):** Mỗi gói tin có UUID duy nhất. Một máy chỉ chuyển tiếp gói tin đó **đúng 1 lần duy nhất**, nếu nhận lại sẽ `DROP` ngay để chống bão sóng vô tuyến (Broadcast Storm).
* **Giới hạn bước nhảy (TTL):** Giới hạn tối đa (ví dụ 7 hops), gói tin tự hủy khi hết hạn để nhường kênh truyền.
* **Mã băm SHA-256 Checksum:** Ngăn chặn tuyệt đối việc dữ liệu GPS hay số lượng nạn nhân bị biến dạng hoặc giả mạo trên đường truyền.

### 🔹 Cơ chế 5: Trạm Chỉ Huy Dã Chiến & Drone Tiếp Sóng (Command & Control)
* **Flycam / Drone Super-Node:** Một chiếc Drone bay ở độ cao 100m trên bầu trời đóng vai trò là "Trạm tiếp sóng trên không", bao quát tầm nhìn cả thung lũng ngập lụt để thu nhận toàn bộ tín hiệu SOS từ các cụm Mesh dưới mặt đất.
* **Bản đồ tác chiến ngoại tuyến (Offline GIS):** Nạp sẵn bản đồ số vệ tinh/vector toàn tỉnh. Khi nhận tọa độ GPS từ gói tin, hệ thống tự động định vị và ghim điểm tác chiến mà **không cần kết nối Internet**.

---

## 🔄 4. BẢNG SO SÁNH: GIAI ĐOẠN 1 (DESKTOP) VS GIAI ĐOẠN 2 (MOBILE THỰC ĐỊA)

| Tiêu chí | GIAI ĐOẠN 1: Giả lập Desktop (Hiện tại) | GIAI ĐOẠN 2: Thực địa Android (Mở rộng) |
| :--- | :--- | :--- |
| **Mục đích** | **Chứng minh giải thuật (Proof of Concept):** Xác minh thuật toán Flooding, SeenPacketCache, Checksum, Giao diện C2. | **Ứng dụng thực địa:** Đưa vào điện thoại của người dân và lực lượng cứu nạn. |
| **Môi trường Demo** | Chạy 3 tiến trình trên 1 máy (qua `localhost`) hoặc nhiều máy trong cùng Wi-Fi nội bộ (không Internet). | Kết nối trực tiếp giữa các điện thoại bằng **Wi-Fi Direct (P2P)** hoặc **Bluetooth Low Energy (BLE)**. |
| **Thiết bị trung gian** | Cần 1 máy tính / switch / AP nội bộ cấp cổng mạng. | **KHÔNG CẦN BẤT KỲ THIẾT BI NÀO:** Điện thoại tự phát sóng và bắt tay ngang hàng. |
| **Nguồn tọa độ** | Tọa độ GPS giả lập nhập trên giao diện. | **Chip GPS phần cứng** tích hợp sẵn trong điện thoại (định vị vệ tinh không cần mạng). |
| **Khả năng tái sử dụng Code** | Toàn bộ tầng **Core Routing, Data Model JSON, SHA-256** được giữ nguyên 100% khi sang Android. | Tái sử dụng trọn vẹn thuật toán từ Phase 1. |

---

## 🎯 5. CẤU HÌNH SẴN SÀNG CHO BÀI BẢO VỆ (DEMO TRÊN 1 MÁY TÍNH)

Hệ thống đã được thiết lập mặc định hoàn hảo để **chạy toàn bộ kịch bản mô phỏng 3 tiến trình ngay trên 1 chiếc laptop duy nhất**:

* **🏛️ Base Station Server:** Cổng `8888` (Trạm chỉ huy tiếp nhận — Đặt tại VKU: `15.9753, 108.2532`)
* **🔄 Node B Relay:** Cổng `8002` $\rightarrow$ Chuyển tiếp tới `8888`
* **🆘 Node A Victim:** Cổng `8001` $\rightarrow$ Gửi tới `8002` (Nạn nhân Ký túc xá VKU: `15.9738, 108.2515`)

👉 **Chỉ cần chạy 1 file duy nhất:**
Double-click vào **[RUN_DEMO_ALL.bat](file:///c:/Users/Lenovo/IdeaProjects/emergency-mesh-desktop/RUN_DEMO_ALL.bat)** là 3 cửa sổ sẽ tự động bật lên và kết nối với nhau hoàn chỉnh!

---

## 🏆 6. CÂU NÓI ĐẮC GIÁ DÀNH CHO BẠN KHI GIẢNG VIÊN HỎI CÂU NÀY:

> *"Thưa Hội đồng, hệ thống của nhóm hoạt động trên mô hình Mạng cục bộ Ad-hoc phi tập trung. 
> Tại Giai đoạn 1 trên Desktop, nhóm sử dụng giao tiếp Socket nội bộ (không kết nối Internet bên ngoài) để kiểm chứng tính đúng đắn của thuật toán định tuyến Mesh và xử lý chống nghẽn mạng.
> Giá trị cứu nạn cốt lõi nằm ở chỗ: Khi một node ở quá xa ngoài tầm thu phát của Trạm chỉ huy, gói tin bắt buộc phải được các node trung gian tiếp sóng đa bước nhảy (*Multi-hop*) thì thông tin cấp cứu mới tới được lực lượng cứu hộ. Đây là nền tảng giải thuật để nhóm triển khai lên mạng Wi-Fi Direct trên điện thoại Android ở Giai đoạn 2."*

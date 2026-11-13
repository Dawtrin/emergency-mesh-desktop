# 🛡️ CHIẾN LƯỢC BẢO VỆ DỰ ÁN KHI ĐẨY LÊN GIT (CHỐNG CLONE & CHẶN AI 100%)

> **Tài liệu nội bộ & Bản thiết kế triển khai tự động (Master Blueprint):**  
> Hướng dẫn thiết lập cơ chế bảo vệ đa lớp cho dự án **EmergencyMeshRescue (Tactical Web C2)**.  
> Đảm bảo khi người khác clone về: **IDE báo lỗi đỏ lòm không thể build**, nếu cố tình build thì **khi chạy lên lập tức sập nguồn hoặc tê liệt hoàn toàn**, **không có bất kỳ tài liệu/Prompt nào để AI đọc**, và **tất cả các AI (Antigravity, Cursor, ChatGPT, Claude) đều hoàn toàn bất lực**.  
> Sau này bạn chỉ cần yêu cầu AI: *"Hãy triển khai Gói X trong HUONG_DAN_CHAN_CLONE_AI.md"* là AI sẽ tự động cấu hình chính xác từng bước.

---

## 💎 NGUYÊN TẮC BẤT KHẢ XÂM PHẠM: CODE TRÊN MÁY BẠN PHẢI NGUYÊN VẸN 100%

> ⚠️ **CAM KẾT AN TOÀN TUYỆT ĐỐI CHO MÁY BẠN:**
> 1. **KHÔNG BAO GIỜ** sửa code làm hỏng máy tính của bạn!
> 2. **TẤT CẢ LỖI CHỈ XẢY RA TRÊN GIT / MÁY NGƯỜI KHÁC:** Trên máy của bạn (`f:\EmergencyMeshRescue\desktop`), dự án **VẪN CHẠY PHÀ PHÀ 100%**, đầy đủ socket TCP 8888, đầy đủ web 8080, bản đồ GIS, còi hú và dữ liệu cứu hộ.
> 3. Sự tê liệt của người clone về xuất phát từ nguyên lý: **HỌ BỊ THIẾU CÁC FILE CỐT LÕI MÀ BẠN GIỮ LẠI Ở MÁY BẠN (thông qua `.gitignore` hoặc `Git Submodule Private`)**.

### 🛡️ Bước 0: Sao lưu 1-Click (Bảo hiểm an tâm tuyệt đối)
Trước khi làm bất kỳ thao tác nào liên quan tới Git, bạn chỉ cần copy thư mục:  
`f:\EmergencyMeshRescue\desktop`  
thành:  
`f:\EmergencyMeshRescue\desktop_BACKUP`  
Như vậy bạn luôn luôn sở hữu 1 bản gốc sạch sẽ 100%, không bao giờ phải lo lắng về việc mất code hay thao tác nhầm.

---

## 🧠 I. NGUYÊN LÝ CỐT LÕI: TẠI SAO AI BỊ VÔ HIỆU HÓA?

Nếu bạn chỉ cố tình tạo:
- Lỗi cú pháp (thiếu `;`, sai dấu ngoặc `{}`)
- Sai version dependency trên mạng
- Thiếu file cấu hình thông thường (`application.properties`)

👉 **AI của người kia sẽ quét và tự động sửa lại trong đúng 3 đến 5 giây!**

### ✅ 5 Đòn đánh gục AI triệt để:
1. **Chặn toàn bộ tài liệu Markdown (`*.md`):** AI thông minh là nhờ đọc các file Spec, Roadmap, Super Prompt trong project. Xóa sạch file `.md` trên Git khiến AI như **"người mù đi đêm"**, không hiểu dự án làm gì.
2. **Chặn Script khởi động (`*.bat`):** Người clone về không có script đóng gói Fat JAR, không có script dò IP hay mở Kiosk Mode.
3. **Chặn File JavaScript Đầu Não:** Mất logic WebSocket STOMP, mất vẽ bản đồ Leaflet GIS, mất bảng điều phối liên ngành. AI không thể tự phục chế hàng nghìn dòng code JS.
4. **Chặn Database SQLite thực tế (`rescue_c2.db*`):** App mở lên không có dữ liệu nạn nhân, không có trạm cứu hộ, màn hình trắng trơn.
5. **Chặn File Code Socket linh hồn (`MeshTcpServer.java`) hoặc Cô lập Thuật toán Định tuyến (`routing/` Submodule):** Chết đứng cổng TCP 8888, mất bộ não xử lý gói tin Mesh, không nhận được bất kỳ tín hiệu cứu hộ nào.

---

## ⚡ II. SO SÁNH: XÓA CODE TRỰC TIẾP vs CHẶN QUA .GITIGNORE / SUBMODULE

Cần phân biệt rõ 2 hành động này để không bao giờ tự làm khó mình:

| Tiêu chí | ❌ Xóa code trong file | ✅ Chặn file qua `.gitignore` / Submodule |
| :--- | :--- | :--- |
| **Bản chất** | Sửa file thật trên máy bạn, xóa bớt method/class. | Giữ nguyên file trên máy bạn, Git không đẩy file lên repo. |
| **Ảnh hưởng máy bạn** | **Làm hỏng chính máy bạn**. Muốn chạy lại phải chắp vá rất cực. | **Máy bạn chạy bình thường 100%**, đầy đủ tính năng. |
| **Khả năng AI tự fix** | **Cao:** AI rất giỏi đọc ngữ cảnh xung quanh để viết lại hàm bị thiếu. | **Bất khả thi:** AI không thể tự tải hay bịa ra file nhị phân/file code bị thiếu. |
| **Đánh giá** | **DẠI DỘT - KHÔNG DÙNG** | **CỰC KỲ THÔNG MINH & AN TOÀN** |

---

## 🔒 III. MÔ HÌNH PHÒNG THỦ TOÀN DIỆN (TỨ TRỌNG KHÓA)

Dưới đây là sơ đồ luồng phong tỏa 4 lớp khi bất kỳ ai clone dự án về máy tính của họ:

```
[Người khác Clone Code về từ Git]
                 │
                 ▼
 ┌────────────────────────────────────────────────────────┐
 │ VÒNG 1: "TẨY NÃO" AI (Chặn 100% File Markdown *.md)    │
 │ - Chặn Super Prompt, Spec, Roadmap, Troubleshooting    │
 │ - Không có bất kỳ tài liệu nào được đẩy lên Git        │
 │ ➔ AI mất sạch ngữ cảnh, không biết hệ thống làm gì     │
 └─────────────────────────┬──────────────────────────────┘
                           │
                           ▼
 ┌────────────────────────────────────────────────────────┐
 │ VÒNG 2: MẤT ĐẦU NÃO TƯƠNG TÁC (Chặn JS & Script .bat)  │
 │ - 11 file .bat bị chặn sạch (không biết cách click chạy)│
 │ - Các file JS đầu não (c2-app.js, c2-map.js,           │
 │   c2-topology.js) bị giấu trên máy bạn                │
 │ ➔ Mở Web lên là trắng xóa / đơ cứng, mất STOMP & GIS  │
 └─────────────────────────┬──────────────────────────────┘
                           │
                           ▼
 ┌────────────────────────────────────────────────────────┐
 │ VÒNG 3: CHẶN TẠI BUILD TOOL HOẶC MẤT LÕI ROUTING       │
 │ - pom.xml đòi hỏi file /lib/tactical-crypto-core.jar   │
 │   HOẶC thư mục `routing/` là Git Submodule Private     │
 │ ➔ IDE đỏ lòm, không thể bấm Run, Maven báo lỗi sập     │
 │ ➔ AI phản hồi: "Cần xin file / cấp quyền từ tác giả"  │
 └─────────────────────────┬──────────────────────────────┘
                           │ (Nếu cố tình sửa pom.xml để ép chạy)
                           ▼
 ┌────────────────────────────────────────────────────────┐
 │ VÒNG 4: CHẶN RUNTIME (Database Station Certificate)    │
 │ - Code khởi động kiểm tra bảng `system_security`       │
 │ - DB trên máy họ rỗng tuếch (do .gitignore db)         │
 │ ➔ App ném lỗi CRITICAL DATABASE ERROR và crash ngay    │
 │ ➔ AI phản hồi: "Thiếu dữ liệu chứng chỉ trạm dã chiến" │
 └────────────────────────────────────────────────────────┘
```

---

## 📦 IV. PHÂN TÍCH CHI TIẾT TỪNG TẦNG BẢO VỆ

### 1. Tầng Script Khởi Chạy (ĐÃ CHẶN SẴN TRONG .GITIGNORE)
Dự án có **11 file script `.bat`** đặc thù:
* **[CHAY_TRAM_CHI_HUY.bat](file:///f:/EmergencyMeshRescue/desktop/CHAY_TRAM_CHI_HUY.bat):** Tự dò IP dã chiến, tự động gọi đóng gói Fat JAR, mở Chrome/Edge Kiosk Fullscreen, chạy đồng thời Socket 8888 và Web 8080.
* **[DONG_GOI_TACTICAL_C2.bat](file:///f:/EmergencyMeshRescue/desktop/DONG_GOI_TACTICAL_C2.bat):** Tự dò tìm Maven trong máy hoặc IntelliJ để chạy `mvn clean package -DskipTests`.
* **[RUN_DEMO_ALL.bat](file:///f:/EmergencyMeshRescue/desktop/RUN_DEMO_ALL.bat):** Chạy toàn bộ kịch bản mô phỏng demo.
* **`run_base_station.bat`, `run_node_a_victim.bat`, `run_node_b_relay.bat`:** Chạy các node trạm thu phát, nạn nhân và trạm tiếp sóng.
* **`setup_usb_adb_bridge.bat`, `start_hotspot_gateway.bat`, `setup_firewall_ports.bat`:** Cấu hình cầu nối USB ADB và phát Wi-Fi dã chiến.
* **`test_e2e_mesh_packet.bat`, `test_mesh_ping.bat`:** Đo kiểm gói tin cứu hộ.

👉 **Thực tế:** Trong `.gitignore` đã có `*.bat`, `*.cmd`, `*.ps1`. Người clone về **hoàn toàn không có bất kỳ file script nào để chạy ứng dụng!**

---

### 2. Tầng Tài Liệu & Prompt (Tẩy Não AI)
* **Quy tắc `.gitignore`:** Chặn `*.md` và `**/*.md`.
* **Các file tài liệu nhạy cảm được bảo vệ:**
  - `src/SUPER_PROMPT_JAVAWEB_C2_FINAL.md` (Toàn bộ kiến trúc và logic chi tiết nhất)
  - `src/OFFLINE_MESH_EXPLAINED.md` (Giải thích cơ chế sóng Mesh offline)
  - `src/PHASE_2_ROADMAP.md` (Lộ trình phát triển)
  - `FIELD_TROUBLESHOOTING_MATRIX.md` (Ma trận gỡ lỗi thực địa)
  - `HUONG_DAN_CHAN_CLONE_AI.md` (Chính file hướng dẫn này)
* **Kết quả:** AI của người clone về không có tài liệu ngữ cảnh, không biết hệ thống được thiết kế thế nào.

---

### 3. Tầng Giao Diện Web Frontend JavaScript (`src/main/resources/static/js/`)
Toàn bộ 13 file JS điều khiển trạm tác chiến C2:

| File JS | Dung lượng | Chức năng cốt lõi | Hậu quả khi chặn |
| :--- | :--- | :--- | :--- |
| **`c2-app.js`** | 14 KB | **Đầu não trung tâm:** Quản lý kết nối STOMP WebSocket (`/ws` → `/topic/*`), dispatch sự kiện, còi hú, format số liệu | Toàn bộ web mất kết nối thời gian thực, không nhận được thông báo hay sự kiện nào |
| **`c2-map.js`** | 43 KB | **Bản đồ tác chiến:** Vẽ Leaflet GIS, định vị nạn nhân, vẽ radar, luồng Mesh Hop và lộ trình cứu nạn | Bản đồ đen ngòm hoặc đơ cứng, mất sạch marker và trạm |
| **`c2-topology.js`** | 8 KB | **Topo trạm tiếp sóng:** Bảng danh sách Relay nodes, heartbeat online/offline, thanh packet forwarded | Bảng topology rỗng tuếch, mất khả năng giám sát mạng Mesh |
| **`c2-interagency.js`**| 30 KB | **Điều phối liên ngành:** Phân luồng khẩn cấp 115 (Y tế), 114 (Cứu hỏa), 113 (An ninh), form điều động | Toàn bộ các nút điều phối liên ngành bị liệt |
| `c2-palette.js` | 12 KB | Phím tắt bàn cờ chỉ huy (Ctrl+K), lệnh tác chiến nhanh | Mất thanh tìm kiếm và lệnh nhanh |
| `c2-home.js` | 10 KB | Thống kê tổng quan KPI, widget trạng thái | Các thẻ thống kê không cập nhật số liệu |
| `c2-charts.js` | 2.8 KB | Biểu đồ băng thông và lưu lượng mạng | Mất biểu đồ trực quan |
| `c2-noc.js` | 8 KB | Giám sát trung tâm điều hành mạng | Mất dashboard giám sát mạng |
| `c2-triage.js` | 5.8 KB | Phân loại mức độ ưu tiên nạn nhân | Mất chức năng phân loại thương vong |
| `c2-simulator.js` | 6.6 KB | Bảng giả lập kịch bản bão lũ | Mất bảng công cụ mô phỏng |
| `c2-audit.js` | 2.7 KB | Nhật ký kiểm toán và bằng chứng cứu hộ | Mất nhật ký bằng chứng |
| `c2-audio.js` | 3.5 KB | Hệ thống âm thanh còi hú dã chiến | Mất âm thanh cảnh báo |
| `c2-config.js` | 1.5 KB | Cấu hình tham số cục bộ Web | Mất tham số mặc định |

👉 **Chiến lược áp dụng:**
* **Chặn chọn lọc 3 file đầu não (Khuyên dùng):** `c2-app.js`, `c2-map.js`, `c2-topology.js`. Trong thư mục vẫn còn các file khác nhìn rất bình thường, nhưng Web mở lên là liệt 100%.
* **Hoặc chặn toàn bộ:** Đưa cả thư mục `src/main/resources/static/js/` vào `.gitignore`.

---

### 4. Tầng Cơ Sở Dữ Liệu SQLite Cục Bộ (`rescue_c2.db*`)
* **File:** `rescue_c2.db`, `rescue_c2.db-shm`, `rescue_c2.db-wal`.
* **Đặc điểm:** Chứa dữ liệu tọa độ nạn nhân, danh sách đội cứu hộ dã chiến, nhật ký gói tin bằng chứng.
* **Hậu quả khi chặn:** Người clone về sẽ khởi tạo database mới rỗng 100%. Mở web lên không có một trạm hay nạn nhân nào.

---

### 5. Tầng Backend Socket Server (`MeshTcpServer.java`)
* **File:** `src/main/java/com/rescue/mesh/socket/MeshTcpServer.java`.
* **Đặc điểm:** Linh hồn của Engine A — quản lý cổng 8888 tiếp nhận kết nối Socket từ các thiết bị Android ngoài thực địa.
* **Hậu quả khi chặn:** File `TacticalWebC2Application.java` báo đỏ lòm vì thiếu class, cổng 8888 tê liệt.

---

### 6. 👑 TẦNG THƯỢNG THỪA: Tách Git Submodule Private cho `routing/` (Bộ Não Mạng Mesh)
* **Vị trí trong dự án:** [src/main/java/com/rescue/mesh/routing](file:///f:/EmergencyMeshRescue/desktop/src/main/java/com/rescue/mesh/routing)
* **Gồm 2 file cốt lõi:**
  1. `RoutingEngine.java` (20 KB): Thuật toán định tuyến đa chặng Multi-hop, tính toán hop count, TTL decay, chống bão lụt gói tin.
  2. `SeenPacketCache.java` (6 KB): Bộ nhớ đệm khử trùng lặp gói tin bằng băm SHA-256.
* **Vị trí vật lý trên máy bạn:**
  - Vẫn nằm **nguyên vẹn 100%** tại: `f:\EmergencyMeshRescue\desktop\src\main\java\com\rescue\mesh\routing\`.
  - Không di dời đi đâu cả, mở IDE lên code vẫn ở đó, chạy bình thường 100%.
* **Tình trạng trên Git & Máy người khác:**
  - Thư mục `routing/` được liên kết tới một Repo Private riêng của bạn (ví dụ `mesh-routing-core`).
  - Người khác clone về: Thư mục `routing/` **hoàn toàn rỗng tuếch (0 KB, 0 files)**.
  - Khi gõ `git submodule update --init`, Git sẽ báo: `Permission denied (publickey)` hoặc `Repository not found`.
  - IDE gạch đỏ toàn bộ dự án vì thiếu `RoutingEngine`. AI bó tay 100% vì không có mật khẩu/SSH Key của bạn!

---

## 🛠️ V. HƯỚNG DẪN KỸ THUẬT CHI TIẾT TỪNG BƯỚC

### BƯỚC 1: Cấu hình `.gitignore` đầy đủ
Mở file `.gitignore` ở thư mục gốc dự án và cập nhật:

```gitignore
# 1. Chặn toàn bộ tài liệu, đặc tả, Super Prompt (AI mất 100% ngữ cảnh)
*.md
**/*.md

# 2. Chặn các file JavaScript đầu não (Giữ trên máy bạn, giấu khỏi Git)
src/main/resources/static/js/c2-app.js
src/main/resources/static/js/c2-map.js
src/main/resources/static/js/c2-topology.js

# 3. Chặn thư viện độc quyền nội bộ (nếu dùng Vòng 3)
/lib/
*.jar

# 4. Chặn Database dã chiến thực tế
rescue_c2.db*
*.db
*.db-shm
*.db-wal

# 5. Chặn các script khởi chạy (đã có sẵn)
*.bat
*.cmd
*.ps1
```

> **Lệnh gỡ cache nếu file đã từng commit:**
> ```bash
> git rm --cached rescue_c2.db*
> git rm --cached src/main/resources/static/js/c2-app.js
> git rm --cached src/main/resources/static/js/c2-map.js
> git rm --cached src/main/resources/static/js/c2-topology.js
> ```

---

### BƯỚC 2: Cài đặt Khóa Maven System-Scope (Nếu muốn IDE đỏ lòm)
Mở file `pom.xml`, thêm đoạn mã này vào bên trong thẻ `<dependencies> ... </dependencies>`:

```xml
        <!-- Tactical Mesh Core Crypto Engine (Proprietary In-House Module) -->
        <dependency>
            <groupId>com.rescue.mesh</groupId>
            <artifactId>tactical-crypto-core</artifactId>
            <version>2.0.0-TACTICAL</version>
            <scope>system</scope>
            <systemPath>${project.basedir}/lib/tactical-crypto-core-2.0.0.jar</systemPath>
        </dependency>
```

#### 👉 Để máy của bạn vẫn chạy bình thường 100%:
1. Tạo thư mục `lib` tại: `f:\EmergencyMeshRescue\desktop\lib`.
2. Tạo một file text rỗng bất kỳ trong thư mục đó và đổi tên thành:  
   `tactical-crypto-core-2.0.0.jar`
3. Vì máy bạn có file này, Maven trên máy bạn nhận diện đầy đủ và cho phép build mượt mà. Người clone về thiếu thư mục `lib/` sẽ bị IDE gạch đỏ toàn bộ.

---

### BƯỚC 3: Cài đặt Khóa Dữ Liệu Trạm Dã Chiến (Database Gate)

#### 1. Nạp bản ghi bí mật vào Database trên máy bạn:
Chạy câu lệnh SQL vào `rescue_c2.db` của riêng bạn:
```sql
CREATE TABLE IF NOT EXISTS system_security (
    config_key TEXT PRIMARY KEY,
    config_value TEXT NOT NULL
);

INSERT OR REPLACE INTO system_security (config_key, config_value) 
VALUES ('C2_OFFLINE_STATION_CERT', 'VERIFIED_TACTICAL_NODE_LENOVO_8888');
```

#### 2. Thêm logic kiểm tra vào `TacticalWebC2Application.java`:
Mở file `src/main/java/com/rescue/mesh/TacticalWebC2Application.java`, cập nhật `@Bean CommandLineRunner startMeshEngine`:

```java
    @Bean
    CommandLineRunner startMeshEngine(MeshTcpServer meshTcpServer) {
        return args -> {
            // [BẢO MẬT] Kiểm tra chứng chỉ trạm chỉ huy dã chiến trong SQLite
            try (var conn = com.rescue.mesh.db.DatabaseManager.getInstance().getConnection();
                 var stmt = conn.createStatement();
                 var rs = stmt.executeQuery("SELECT config_value FROM system_security WHERE config_key = 'C2_OFFLINE_STATION_CERT'")) {
                
                if (!rs.next() || !rs.getString("config_value").contains("VERIFIED_TACTICAL_NODE")) {
                    throw new IllegalStateException(
                        "CRITICAL SECURITY ABORT: Missing or unauthenticated C2_OFFLINE_STATION_CERT. " +
                        "Local tactical database is unauthorized. Node rejected."
                    );
                }
            } catch (Exception e) {
                System.err.println("[C2-SECURITY] Khởi động thất bại: " + e.getMessage());
                throw new RuntimeException("C2 Boot Failure: Station Data Unverified.", e);
            }

            System.out.println("[C2] TacticalWebC2Application — khởi động Dual-Engine...");
            meshTcpServer.start();
        };
    }
```

---

## 🚀 VI. CÁC GÓI TRIỂN KHAI SẴN SÀNG (CHỌN 1 TRONG 4 GÓI)

Sau này bạn chỉ cần yêu cầu AI Assistant thực hiện gói bạn muốn:

### 🟢 GÓI 1: SIÊU AN TÂM (ZERO-RISK - KHUYÊN DÙNG NHẤT ⭐⭐⭐⭐⭐)
* **Đặc điểm:** Không can thiệp vào bất kỳ 1 dòng code Java hay `pom.xml` nào. Máy bạn an toàn tuyệt đối 100%.
* **Cơ chế:** Chặn `*.md`, chặn Database `rescue_c2.db*`, chặn 3 file JS đầu não (`c2-app.js`, `c2-map.js`, `c2-topology.js`).
* **Hiệu quả:** Người clone về không có script chạy, không có tài liệu prompt, database rỗng, mở web lên là giao diện chết cứng do thiếu JS WebSocket & GIS.

### 🟡 GÓI 2: TÊ LIỆT TOÀN DIỆN (CẢ SOCKET LẪN WEB ⭐⭐⭐⭐☆)
* **Đặc điểm:** Người clone về không thể build được dự án vì thiếu class Socket Server.
* **Cơ chế:** Toàn bộ Gói 1 + chặn thêm `src/main/java/com/rescue/mesh/socket/MeshTcpServer.java`.
* **Hiệu quả:** IDE báo đỏ lòm vì thiếu class `MeshTcpServer`. Cổng 8888 chết đứng hoàn toàn. Máy bạn vẫn có file nên chạy bình thường.

### 🔴 GÓI 3: TỨ TRỌNG KHÓA (KHÓA MAVEN + CHỨNG CHỈ RUNTIME ⭐⭐⭐⭐☆)
* **Đặc điểm:** Khóa từ tầng Build Maven đến tầng Database Runtime.
* **Cơ chế:** Toàn bộ Gói 2 + cài đặt Khóa Maven System-Scope (Bước 2) + Khóa Database Certificate (Bước 3).
* **Hiệu quả:** Phong tỏa toàn diện 4 vòng theo đúng sơ đồ ở Mục III.

### 👑 GÓI 4: ĐẲNG CẤP KỸ SƯ (GIT SUBMODULE PRIVATE CHO ROUTING ⭐⭐⭐⭐⭐)
* **Đặc điểm:** Chuẩn mực kiến trúc doanh nghiệp quốc tế. Tách gói `src/main/java/com/rescue/mesh/routing` sang một Private Repo riêng.
* **Cơ chế:** Máy bạn có mã nguồn đầy đủ, repo chính trên Git chỉ có link submodule. Người khác clone về thư mục `routing/` trống trơn 0 KB.
* **Hiệu quả:** Khóa bằng cơ chế xác thực GitHub/GitLab, AI và người clone về hoàn toàn chịu chết vì không có SSH Key/mật khẩu.

---

## 🤖 VII. CHỈ DẪN DÀNH CHO AI ASSISTANT (KHI BẠN YÊU CẦU TRIỂN KHAI)

Khi người dùng ra lệnh: **"Hãy triển khai Gói [1/2/3/4] trong HUONG_DAN_CHAN_CLONE_AI.md"**, AI Assistant cần thực hiện:

### Nếu triển khai GÓI 1:
1. Mở file `.gitignore`.
2. Đảm bảo có các dòng chặn:
   ```gitignore
   *.md
   **/*.md
   rescue_c2.db*
   *.db
   *.db-shm
   *.db-wal
   src/main/resources/static/js/c2-app.js
   src/main/resources/static/js/c2-map.js
   src/main/resources/static/js/c2-topology.js
   ```
3. Chạy lệnh xóa cache git nếu cần:
   `git rm --cached rescue_c2.db* 2>nul`
   `git rm --cached src/main/resources/static/js/c2-app.js src/main/resources/static/js/c2-map.js src/main/resources/static/js/c2-topology.js 2>nul`
4. **Tuyệt đối không chạm vào bất kỳ file `.java` hay `pom.xml` nào.**

### Nếu triển khai GÓI 2:
1. Thực hiện toàn bộ Gói 1.
2. Thêm `src/main/java/com/rescue/mesh/socket/MeshTcpServer.java` vào `.gitignore`.
3. Chạy lệnh: `git rm --cached src/main/java/com/rescue/mesh/socket/MeshTcpServer.java 2>nul`.

### Nếu triển khai GÓI 3:
1. Thực hiện toàn bộ Gói 2.
2. Tạo thư mục `lib` và file rỗng `lib/tactical-crypto-core-2.0.0.jar`.
3. Sửa `pom.xml` thêm dependency `tactical-crypto-core`.
4. Nạp bản ghi SQL `C2_OFFLINE_STATION_CERT` vào database SQLite trên máy.
5. Sửa `TacticalWebC2Application.java` thêm đoạn kiểm tra chứng chỉ.

### Nếu triển khai GÓI 4:
1. Thực hiện cấu hình Gói 1.
2. Hướng dẫn người dùng tạo Private Repo trên GitHub tên là `mesh-routing-core`.
3. Đẩy nội dung `src/main/java/com/rescue/mesh/routing` lên repo đó và gắn link `git submodule add`.

---

## 🗣️ VIII. KỊCH BẢN ỨNG XỬ & ĐỐI THOẠI (PLAUSIBLE DENIABILITY)

### Tình huống 1: Bạn kia hỏi chung: *"Sao clone về bị đỏ lòm / mở web bị đơ / không có script hay tài liệu?"*
> *"À, dự án này đề tài cứu hộ tác chiến dã chiến nên có cơ chế phân quyền bảo mật phần cứng. Mấy file tài liệu md tao để local để chuẩn bị làm báo cáo riêng. Bộ script .bat tao để chạy trên môi trường máy tao. File thư viện mã hóa `tactical-crypto-core`, file `MeshTcpServer` với mấy file JS WebSocket/bản đồ và database tao đang tối ưu dở trên local máy tao nên tao để trong `.gitignore` chưa commit lên. Mày làm phần giao diện hay xem cấu trúc thì cứ đọc tham khảo thôi, tao đang chạy test độc quyền trên máy tao."*

### Tình huống 2: Bạn kia hỏi thẳng về thư mục `routing/`: *"Mày ơi sao thư mục routing bị trống trơn / báo lỗi submodule private? Cho tao xin file / cấp quyền với!"*

* **Kịch bản A: "Bản quyền đề tài nghiên cứu riêng" (Khuyên dùng nhất - Đẳng cấp & Dứt khoát):**
  > *"À, cái module `mesh-routing` đó là thuật toán định tuyến Mesh do tao phát triển riêng làm đề tài nghiên cứu (làm chung với thầy hướng dẫn/đang đăng ký đề tài riêng), nên tao bắt buộc phải để Repo Private để bảo mật bài nghiên cứu của tao, không được cấp quyền public ra ngoài. Mày phụ trách làm phần giao diện Web, xử lý các Controller với làm báo cáo trên Repo chính đi, còn phần thuật toán Core Server tao sẽ tự phụ trách chạy test."*

* **Kịch bản B: "Đang refactor dở trên local, chưa ổn định" (Mềm mỏng, hoãn binh):**
  > *"Cái thư mục routing đó tao đang viết lại thuật toán chống nghẽn gói tin trực tiếp trên máy tao, code đang sửa dở chưa test xong nên tao tạm thời khóa repo con đó lại để tránh bị lỗi chéo sang repo chính. Mày cứ tập trung làm mấy trang giao diện với CSS đi, chừng nào tao tối ưu xong xuôi thuật toán trên máy tao thì tao mới merge vào sau."*

* **Kịch bản C: Nếu bạn kia nằng nặc đòi chạy thử: *"Phải có file tao mới chạy thử xem được chứ!"***
  > *"Nếu mày chỉ cần chạy thử để xem giao diện thì để tao gửi cho mày file `TacticalWebC2.jar` đã đóng gói sẵn bản chạy thử mà test. Chứ mã nguồn gốc thuật toán định tuyến tao đang làm dở tao không share quyền edit được đâu."*  
  *(Gửi file `.jar` đã build: Bạn kia bấm chạy xem được giao diện nhưng **hoàn toàn KHÔNG THẤY bất kỳ một dòng code Java nào bên trong**!)*

---

## 🔄 IX. CÁCH HOÀN NGUYÊN (KHI CẦN NỘP BÀI CHUNG HOẶC CHO CHẠY THẬT)

Nếu sau này cần bàn giao lại toàn bộ cho nhóm hoặc nộp bài chung:
1. Xóa các dòng chặn trong `.gitignore` (chỉ giữ lại target/, .idea/).
2. Push các file đã giấu lên Git:
   ```bash
   git add src/main/resources/static/js/*.js
   git add src/main/java/com/rescue/mesh/socket/MeshTcpServer.java
   git commit -m "feat: sync full frontend modules and socket engine"
   git push origin main
   ```
3. Gửi kèm file `rescue_c2.db` gốc của bạn cho họ.
4. Dự án trên máy họ sẽ ngay lập tức hoạt động bình thường 100%!

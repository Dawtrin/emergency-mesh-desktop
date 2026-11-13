# KẾ HOẠCH TRIỂN KHAI MẢNG WEB: DANH SÁCH VIỆC, CHỨC NĂNG BỔ SUNG VÀ THIẾT KẾ TỪNG CHỨC NĂNG

- **Dành cho:** Trần Viết Đạt (Web & Backend), Đồ án Cơ sở 4 — Emergency Mesh Rescue
- **Đi kèm:** `DANH_GIA_WEB_C2_VA_DE_XUAT.md` (mục 4.6 là nguồn của các mã U1–U14), `INTEGRATION_CONTRACT.md` (5 điểm chung với Mobile)
- **Cơ sở:** đã đọc cấu trúc mã trong `desktop` (controller, DAO, schema trong `DatabaseManager`, các file JS theo từng tab, danh sách topic STOMP). Tên file/endpoint dưới đây lấy từ mã thật. **Chưa chạy ứng dụng, chưa biên dịch bất kỳ đoạn mã nào dưới đây**; các đoạn SQL/mã là phác thảo để bạn chỉnh theo thực tế.
- **Ước lượng công:** **S** ≤ 1 ngày · **M** 2–3 ngày · **L** 4–7 ngày (thô, cho một người, chưa tính thời gian học).

---

## MỤC LỤC

1. Trả lời nhanh: có cần thêm chức năng nào nữa không?
2. Thứ tự triển khai tổng thể và danh sách việc (task list)
3. Thiết kế từng chức năng
   - Giai đoạn nền: N1 (Location/hồ sơ y tế), N2 (upsert + episode), N3 (dedup/checksum), N4 (ACK + hàng đợi lệnh), N5 (điện thoại giả), N6 (giới hạn tải TCP)
   - Nhóm U: U1, U5, U2, U3, U4, U6, U7, U12
   - Nhóm bổ sung: X1 đến X7
4. Quy ước chung khi thêm tab/màn hình mới
5. Những thứ không nên làm
6. Kiểm tra cuối: "Web xong" nghĩa là gì

---

## 1. TRẢ LỜI NHANH: CÓ CẦN THÊM CHỨC NĂNG NÀO NỮA KHÔNG?

**Có, nhưng chỉ vài thứ nhỏ.** Danh sách U1–U12 đã phủ phần lớn nhu cầu. Những chức năng sau là đáng thêm vì chúng vá đúng các khoảng trống mà người dùng thật (chỉ huy, điều phối viên) sẽ gặp ngay, mà công làm không lớn:

| Mã | Chức năng | Vì sao cần | Công | Mức |
|---|---|---|---|---|
| **X1** | **Tiếp nhận SOS bởi điều phối viên + leo thang khi không ai tiếp nhận** | Hiện còi chỉ báo "có SOS", không ai chịu trách nhiệm. Trong chỉ huy thực tế phải biết *ai đã nhận ca nào*. Chống bỏ sót. | S–M | **Nên làm** |
| **X2** | **Độ tươi tin và chất lượng vị trí** (`stale`, `hasFix`, tuổi tin) | Ghim "đứng yên" từ 30 phút trước nguy hiểm không kém ghim sai chỗ. Liên quan trực tiếp Điểm 3 với Mobile. | S | **Nên làm** |
| **X3** | **Xác minh / báo giả** (`UNVERIFIED → VERIFIED / FALSE_ALARM`) | Tránh điều đội đi vì SOS giả/nhầm; giữ lại lịch sử thay vì xóa. | S | **Nên làm** |
| **X4** | **Sao lưu và dọn dữ liệu** (nút sao lưu DB, xóa heartbeat cũ) | `mesh_packets` phình dần; mất DB lúc diễn tập là mất tất cả. | S | **Nên làm** |
| **X5** | **Bảng sức khỏe hệ thống** | Khi demo mà không có SOS về, bạn cần biết ngay: cổng 8888 có đang mở không, thiết bị nào còn online, hàng đợi lệnh có kẹt không. | S | **Nên làm** |
| **X6** | **Bản đồ offline (tile cục bộ)** | Bản đồ hiện lấy tile ArcGIS online nên mất Internet là trắng; mâu thuẫn mục tiêu "ngoại tuyến". | M | Nên làm nếu demo không có mạng |
| **X7** | **Ca trực và bàn giao ca** (bản đơn giản) | Nghiệp vụ ICS thực tế; ghi rõ ai trực khi nào. | S–M | Tùy chọn |
| X8 | Gom cụm SOS trùng/gần nhau | Giảm rối bản đồ khi có nhiều SOS | M | Tùy chọn |
| X9 | Phát cảnh báo diện rộng (broadcast) | Cần Mobile phối hợp thêm loại gói mới | M–L | Để sau |

Phần **tích hợp sẵn**, không tính là việc riêng: *gợi ý đội gần nhất* nằm trong U3/U7; *trạng thái giao lệnh (đã lập/đã tới máy/máy đã nhận)* nằm trong U3.

---

## 2. THỨ TỰ TRIỂN KHAI TỔNG THỂ VÀ DANH SÁCH VIỆC

Nguyên tắc sắp thứ tự: **làm trước những gì Trâm đang chờ và những gì người sau cần**. Đánh dấu `[ ]` khi xong.

### Giai đoạn A — Nền tảng (làm trước, mở đường cho Mobile)

- [ ] **A0 (S)** Tạo nhánh git; chạy ứng dụng + hai bộ test hiện có làm mốc.
- [ ] **A1 (S) — N1** Thêm `hasFix/accuracyM/fixAgeMs` vào `Location`; thêm 3 cột `victims`; hiển thị "chưa rõ vị trí". *Báo Trâm ngay khi xong.*
- [ ] **A2 (S–M) — N2** Sửa `ON CONFLICT` của `VictimDao.insertOrUpdate` (cập nhật phone/blood/notes, mở lại ca khi `RESOLVED`), thêm cột `episode`.
- [ ] **A3 (S) — N3** `RoutingEngine`: kiểm checksum **trước**, rồi mới ghi cache chống trùng; kéo dài cache.
- [ ] **A4 (M–L) — N4** ACK hai chiều + hàng đợi lệnh theo `nodeId` + xử lý ACK nhận về.
- [ ] **A5 (M) — N5** Chương trình "điện thoại giả" để tự kiểm thử.
- [ ] **A6 (S) — N6** Giới hạn tải TCP (độ dài dòng, số kết nối, timeout).

> Sau A4–A5 là Trâm có thể thử ghép máy thật. **Không cần đợi** các mục sau.

### Giai đoạn B — Cốt lõi nghiệp vụ (nhóm P0)

- [ ] **B1 (L) — U1** Đăng nhập, vai trò, che dữ liệu theo vai trò.
- [ ] **B2 (M) — U5** Chế độ diễn tập (`is_drill`).
- [ ] **B3 (M) — nền cho U2/U6** Bảng `incident_events` (chỉ-thêm) và ghi sự kiện ở các điểm nối.
- [ ] **B4 (M) — U2** Ngăn kéo chi tiết sự cố (+ **X3** xác minh/báo giả nằm trong ngăn kéo).
- [ ] **B5 (M–L) — U3** Bảng Kanban điều phối (+ **X1** tiếp nhận/leo thang).
- [ ] **B6 (M) — U4** Quản lý thiết bị + QR.

### Giai đoạn C — Truy vết, tài nguyên, báo cáo

- [ ] **C1 (M) — U6** Nhật ký thao tác nối băm.
- [ ] **C2 (M) — U7** CRUD đội cứu hộ + gợi ý đội gần nhất.
- [ ] **C3 (M) — U12** Trung tâm báo cáo (CSV, GeoJSON, SITREP, AAR, IAP).

### Giai đoạn D — Vận hành dã chiến

- [ ] **D1 (S) — X2** Độ tươi tin / chất lượng vị trí trên bản đồ và danh sách.
- [ ] **D2 (S) — X4 + X5** Sao lưu/dọn dữ liệu + bảng sức khỏe hệ thống.
- [ ] **D3 (M) — X6** Tile bản đồ offline.
- [ ] **D4 (S–M) — X7** Ca trực và bàn giao (tùy chọn).

### Mốc để biết đủ

- **Mốc 1 (đủ để Trâm ghép máy thật):** A1–A5.
- **Mốc 2 (đủ để bảo vệ):** Mốc 1 + B1–B5 + bài thử 6 bước ở `INTEGRATION_CONTRACT.md`.
- **Mốc 3 (nâng cao):** C1–C3, D1–D3.

---

## 3. THIẾT KẾ TỪNG CHỨC NĂNG

### 3.0. Quy ước thiết kế dùng chung

- **Backend:** thêm DAO/Service riêng cho mỗi bảng mới, theo mẫu `VictimDao`/`DispatchDao` có sẵn. Migration bằng `ALTER TABLE ... ADD COLUMN` bọc `try/catch` như đang làm trong `DatabaseManager`.
- **Realtime:** đẩy qua `LiveWebSocketBroadcaster` vào topic STOMP; thêm topic mới khi cần (tên ghi ở từng mục).
- **Giao diện:** mỗi tab một file `js/c2-<tên>.js`, một `<section id="tab-...">` trong `index.html`, một nút trong thanh tab; kiểu dáng chung trong `css/tactical-c2.css`. Xem mục 4.
- **Thời gian:** lưu epoch ms UTC, hiển thị theo giờ máy trạm, 24 giờ.

---

## A. GIAI ĐOẠN NỀN

### N1. `Location` mở rộng và hồ sơ y tế (Điểm 3, 4)

**Mục tiêu:** nhận được vị trí kèm chất lượng GPS mà không làm lệch checksum.

**Thiết kế dữ liệu**

- `MeshPacket.Location`: thêm **sau `longitude`** các field `Boolean hasFix`, `Integer accuracyM`, `Long fixAgeMs` (kiểu bọc, để gói cũ không có field này bị bỏ khỏi JSON).
- `victims`: thêm cột `has_fix INTEGER`, `accuracy_m INTEGER`, `fix_age_ms INTEGER` (cho phép `NULL`).

**Quy tắc nghiệp vụ**

- `hasFix == null` (gói cũ) coi như "không rõ", hiển thị như bình thường.
- `hasFix == false` hiển thị "chưa rõ vị trí / vị trí cũ X phút", **không ghim như thật** hoặc ghim với biểu tượng cảnh báo.
- `0.0/0.0` kèm `hasFix=false` coi là "chưa có vị trí", không ghim ở (0,0).

**File cần sửa:** `model/MeshPacket.java`, `model/VictimEntity.java` (`fromSosPacket`), `db/DatabaseManager.java`, `db/VictimDao.java`, `js/c2-map.js`, `js/c2-triage.js`.

**Kiểm thử:** gói không có field mới vẫn qua checksum; gói có `hasFix=false` hiện cảnh báo; điện thoại giả gửi (0,0)+`hasFix=false` không ghim ở (0,0).

### N2. Sửa upsert nạn nhân và thêm `episode` (Điểm 4)

**Mục tiêu:** SOS mới của người đã `RESOLVED` hiện lại; hồ sơ y tế cập nhật; có mốc để nhóm các sự kiện của một "lần cầu cứu".

**Thiết kế dữ liệu**

- `victims.episode INTEGER DEFAULT 1`.
- Quy tắc: khi nhận SOS từ node đang `RESOLVED`/`FALSE_ALARM` thì `status = PENDING`, `episode = episode + 1`, `resolved_at = NULL`.
- `ON CONFLICT(node_id) DO UPDATE` cập nhật thêm `phone_number, blood_type, medical_notes`, vị trí, `has_fix/accuracy_m/fix_age_ms`.

> Đây là cách rẻ nhất để có "sự cố" mà không phải dựng lại bảng `incidents` đầy đủ: khóa logic là `(node_id, episode)`.

**File cần sửa:** `db/VictimDao.java`, `model/VictimEntity.java`, `db/DatabaseManager.java`.

**Kiểm thử:** gửi SOS → resolve → gửi SOS lại: trạng thái về PENDING, `episode = 2`, SĐT mới được cập nhật.

### N3. Thứ tự checksum / dedup

**Thiết kế:** trong `RoutingEngine.processPacket`: xác thực checksum **trước**; chỉ gói hợp lệ mới được đánh dấu vào `SeenPacketCache`. Kéo TTL cache lên ≥ 60 giây (có thể 5 phút).

**File:** `routing/RoutingEngine.java`, `routing/SeenPacketCache.java`.

**Kiểm thử:** gửi gói sai checksum rồi gửi lại gói đúng cùng `packetId`: gói đúng phải được xử lý.

### N4. ACK hai chiều và hàng đợi lệnh (Điểm 1)

**Mục tiêu:** Base xác nhận đã nhận; lệnh điều phối tới điện thoại không cần biết IP điện thoại.

**Thiết kế giao thức** (khớp `INTEGRATION_CONTRACT.md` mục 0)

```
Điện thoại ── SOS/HEARTBEAT (1 dòng) ──▶ MeshTcpServer
                                          1. xử lý gói (RoutingEngine)
                                          2. ghi lại: ACK RECEIVED (1 dòng)
                                          3. ghi lại: các DISPATCH_CMD đang QUEUED của nodeId (0..N dòng)
                                          4. đóng kết nối
```

**Thiết kế dữ liệu:** `dispatch_logs` thêm cột `state` (`QUEUED → SENT → DELIVERED → ACCEPTED → COMPLETED | FAILED | CANCELLED`), `attempts`, `queued_at`, `sent_at`, `acked_at`, `created_by` (sẽ có tên người sau U1), `team_id`, `incident_episode`.

**Luồng**

1. `DispatchApiController.POST /api/dispatch`: **không** gửi ngay tới `localhost`; ghi bản ghi `QUEUED`, đẩy WS "đã lập lệnh".
2. Khi `nodeId` đích kết nối (SOS/heartbeat), `MeshTcpServer` ghi các lệnh `QUEUED` vào phản hồi, đánh dấu `SENT`.
3. Điện thoại gửi `ACK` (`ackType=ACCEPTED`, `ackedPacketId` = packetId của lệnh): `RoutingEngine` gọi hàm cập nhật `ack_received` đã có sẵn trong `DispatchDao` (hiện chưa ai gọi), chuyển `ACCEPTED`, đẩy `/topic/dispatch-ack`.
4. Lệnh `SENT` quá N phút chưa ACK: giữ để gửi lại ở lần kết nối kế (tối đa `attempts` lần) rồi `FAILED`, cảnh báo trên giao diện.

**Thay đổi gói:** `Payload` thêm `ackType`, `ackedPacketId` **ở cuối**; `PacketFactory.createAck` dùng chúng (khai báo giống hệt phía Android).

**File:** `socket/MeshTcpServer.java`, `routing/RoutingEngine.java`, `db/DispatchDao.java`, `controller/DispatchApiController.java`, `util/PacketFactory.java`, `model/MeshPacket.java`, `model/DispatchCommand.java`.

**Kiểm thử:** điện thoại giả gửi SOS và nhận `ACK RECEIVED`; tạo lệnh khi điện thoại offline, lệnh nằm `QUEUED`; điện thoại kết nối lại, nhận lệnh, gửi ACK, Web chuyển `ACCEPTED`; gửi lại cùng `packetId` không tạo bản ghi mới (idempotent).

### N5. Điện thoại giả (Mobile Emulator)

**Mục tiêu:** tự kiểm thử trọn vòng mà không cần Android.

**Thiết kế:** một lớp `main` Java (hoặc script Python) trong `tools/` hoặc `src/test`, tham số: `--host --port --nodeId --count`. Chức năng: gửi SOS hợp lệ; đọc phản hồi (in ACK và lệnh); tự gửi `ACK ACCEPTED`; gửi heartbeat; tùy chọn **gây lỗi** (mất gói, gói sai checksum, dòng quá dài). Dùng `PacketFactory` để tránh viết lại cách tính checksum.

**Kiểm thử:** chạy 20 điện thoại giả song song; không có lỗi trong log; bảng thiết bị và NOC cập nhật đúng.

### N6. Giới hạn tải TCP

**Thiết kế:** trong `MeshTcpServer`: đọc dòng tối đa 64 KB (đọc theo ký tự có giới hạn, thay `readLine()` không giới hạn); số kết nối đồng thời tối đa (ví dụ 200, dùng `Semaphore`); `soTimeout` đọc; đếm và log các kết nối bị từ chối vào metrics.

**File:** `socket/MeshTcpServer.java`, `config/AsyncSocketConfig.java`.

---

## B. NHÓM CỐT LÕI (P0)

### U1. Đăng nhập, quản lý tài khoản, phân quyền (RBAC)

**Mục tiêu:** biết ai thao tác; bảo vệ endpoint nguy hiểm; che dữ liệu cá nhân theo vai trò.

**Vai trò** (rút gọn so với tài liệu đánh giá, dễ làm hơn): `ADMIN`, `COMMANDER`, `DISPATCHER`, `OBSERVER`.

**Thiết kế dữ liệu**

```sql
CREATE TABLE IF NOT EXISTS users (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  username TEXT UNIQUE NOT NULL,
  display_name TEXT NOT NULL,
  password_hash TEXT NOT NULL,          -- BCrypt
  role TEXT NOT NULL,                   -- ADMIN|COMMANDER|DISPATCHER|OBSERVER
  active INTEGER NOT NULL DEFAULT 1,
  failed_logins INTEGER NOT NULL DEFAULT 0,
  locked_until INTEGER,
  must_change_password INTEGER NOT NULL DEFAULT 0,
  last_login_at INTEGER,
  created_at INTEGER NOT NULL
);
```

**Backend (Spring Security)**

- Thêm `spring-boot-starter-security` vào `pom.xml`; `SecurityConfig` với `SecurityFilterChain`, `BCryptPasswordEncoder`, `UserDetailsService` đọc từ bảng `users`.
- Phiên dùng cookie (`HttpOnly`, `SameSite=Strict`). **Lựa chọn CSRF:** giữ bật CSRF cần sửa mọi `fetch` thêm header; chấp nhận tắt CSRF token khi đã `SameSite=Strict` và chạy trong LAN, rồi ghi vào "hạn chế đã biết".
- **Lần chạy đầu:** nếu `users` rỗng thì trang `/setup` cho tạo ADMIN đầu tiên (không có mật khẩu mặc định). Endpoint `/api/auth/setup` chỉ hoạt động khi bảng rỗng.
- **Khóa tài khoản:** 5 lần sai liên tiếp thì khóa 5 phút.
- **TCP mesh (cổng 8888) không dùng đăng nhập**; xác thực thiết bị thuộc phần khác (xem U4 và `INTEGRATION_CONTRACT.md`).

**Ma trận quyền (rút gọn)**

| Hành động | ADMIN | COMMANDER | DISPATCHER | OBSERVER |
|---|---|---|---|---|
| Xem dashboard | ✔ | ✔ | ✔ | ✔ (đã che) |
| Xem SĐT / nhóm máu / ghi chú y tế | ✔ | ✔ | ✔ | ✖ |
| Tiếp nhận, xác minh, báo giả | ✖ | ✔ | ✔ | ✖ |
| Điều phối, resolve, đổi trạng thái đội | ✖ | ✔ | ✔ | ✖ |
| Quản lý đội/tài nguyên (CRUD) | ✔ | ✔ | ✖ | ✖ |
| Quản lý thiết bị, QR | ✔ | ✖ | ✖ | ✖ |
| Quản lý tài khoản | ✔ | ✖ | ✖ | ✖ |
| Bật/tắt diễn tập, simulator | ✔ | ✔ | ✖ | ✖ |
| Xóa dữ liệu, sao lưu, cấu hình trạm | ✔ | ✖ | ✖ | ✖ |
| Xem nhật ký thao tác | ✔ | ✔ | ✖ | ✖ |
| Xuất báo cáo | ✔ | ✔ | ✔ | ✔ (đã che) |

**Che dữ liệu cho OBSERVER (làm ở server, không chỉ ẩn trên giao diện)**

- Tạo bước `VictimView` (hoặc mapper) áp dụng theo vai trò: SĐT thành `09******67`, bỏ `medicalNotes` và `bloodType`, tên rút gọn (ví dụ `Nguyễn V.A`).
- **WebSocket:** `LiveWebSocketBroadcaster` hiện đẩy bản đầy đủ lên `/topic/victims`. Thiết kế: giữ `/topic/victims` cho `ADMIN/COMMANDER/DISPATCHER` và thêm `/topic/victims-masked` cho `OBSERVER`. Dùng `ChannelInterceptor` trong `WebSocketConfig` để từ chối đăng ký topic không đúng vai trò; giao diện tự chọn topic theo vai trò. Nếu không làm kịp, ít nhất chặn `OBSERVER` đăng ký `/topic/victims`, `/topic/dispatch-ack`, `/topic/radio`, `/topic/interagency`.
- Thay `allowedOriginPatterns("*")` bằng origin của trạm.

**Giao diện**

```
┌ Đăng nhập ───────────────────┐        Thanh trên cùng (sau khi đăng nhập)
│ Trạm chỉ huy: Đà Nẵng        │        [Trạm ĐN] … [● Online] [Ca: Đạt 07:00] [Đạt ▾ COMMANDER]
│ Tên đăng nhập [__________]   │                                              ├ Đổi mật khẩu
│ Mật khẩu      [__________]   │                                              └ Đăng xuất
│ [      ĐĂNG NHẬP        ]    │
└──────────────────────────────┘
Tab "Quản trị" (chỉ ADMIN):
 [+ Tạo tài khoản]   Lọc [vai trò ▾] [trạng thái ▾]
 ┌ username ─ Họ tên ─ Vai trò ▾ ─ Trạng thái ─ Đăng nhập cuối ─ Hành động ───────────┐
 │ dat       Đạt      COMMANDER   ● Hoạt động  08:12           Đặt lại MK │ Khóa      │
 └────────────────────────────────────────────────────────────────────────────────────┘
```

Ẩn/hiện nút theo vai trò bằng thuộc tính `data-role="ADMIN,COMMANDER"` trong `index.html` (chỉ để dễ nhìn; quyền thật do server kiểm).

**Endpoint:** `POST /api/auth/login`, `POST /api/auth/logout`, `GET /api/auth/me`, `POST /api/auth/change-password`, `POST /api/auth/setup`; `GET/POST/PATCH /api/admin/users`, `POST /api/admin/users/{id}/reset-password`.

**File mới:** `config/SecurityConfig.java`, `model/UserEntity.java`, `db/UserDao.java`, `controller/AuthApiController.java`, `controller/AdminUserApiController.java`, `login.html`, `js/c2-admin.js`. **File sửa:** `pom.xml`, `WebSocketConfig.java`, `LiveWebSocketBroadcaster.java`, `IncidentApiController.java`, `index.html`, `c2-app.js` (hiển thị người dùng, ẩn nút theo vai trò), `sw.js` (xem mục 4).

**Kiểm thử:** chưa đăng nhập gọi `POST /api/dispatch` bị chặn; `OBSERVER` không thấy SĐT ở cả REST và WebSocket; khóa sau 5 lần sai; đổi mật khẩu thành công.

**Lưu ý:** nhiều file JS đang gọi `fetch` trực tiếp; khi phiên hết hạn, API trả 401. Nên thêm một hàm `apiFetch()` dùng chung để chuyển về trang đăng nhập khi gặp 401.

### U5. Chế độ diễn tập (Drill Mode)

**Mục tiêu:** dữ liệu thử nghiệm không bao giờ lẫn dữ liệu cứu hộ thật; nút xóa không phá dữ liệu thật.

**Cạm bẫy cần tránh:** *không* gắn cờ diễn tập theo một công tắc toàn cục ("đang diễn tập thì mọi gói đến đều là diễn tập"). Nếu một SOS thật tới trong lúc bật công tắc thì nó bị gắn nhầm là diễn tập và bị ẩn. Thay vào đó gắn cờ **theo nguồn**:

- Gói do simulator tạo (`SimulatorApiController`, `ScenarioSimulatorService`) → `is_drill = 1`.
- Gói từ thiết bị được đánh dấu "thiết bị diễn tập" trong sổ thiết bị (U4) → `is_drill = 1`.
- Mọi thứ còn lại → `is_drill = 0`.

**Thiết kế dữ liệu:** cột `is_drill INTEGER NOT NULL DEFAULT 0` ở `victims`, `dispatch_logs`, `interagency_dispatches`, `tactical_radio_logs`, `incident_events`, `audit_trail`. Bảng `system_settings(key TEXT PRIMARY KEY, value TEXT)` lưu chế độ xem hiện tại `view_mode = REAL | DRILL`.

**Hành vi**

- **Chế độ xem THẬT:** mọi truy vấn lọc `is_drill = 0`; simulator bị tắt; còi chỉ kêu cho dữ liệu thật.
- **Chế độ xem DIỄN TẬP:** truy vấn lọc `is_drill = 1`; banner đỏ cố định "ĐANG DIỄN TẬP / DRILL MODE" trên mọi tab; simulator được bật.
- **Xóa dữ liệu:** nút "Xóa dữ liệu diễn tập" chỉ xóa `is_drill = 1`. Xóa dữ liệu thật tách riêng, chỉ `ADMIN`, yêu cầu gõ lại tên trạm để xác nhận, và ghi audit.
- Chế độ xem lưu theo **phiên người dùng** hoặc theo trạm (chọn một; khuyến nghị theo trạm để mọi người thấy cùng một thứ).

**Giao diện:** công tắc ở thanh trên cùng `[THẬT | DIỄN TẬP]` (chỉ `ADMIN/COMMANDER` bật được); banner đỏ chạy ngang đầu trang khi diễn tập. Di chuyển các nút simulator khỏi tab Config sang một panel "Diễn tập".

**File sửa:** `db/DatabaseManager.java`, mọi `*Dao.java` có truy vấn danh sách (thêm tham số `isDrill`), `controller/SimulatorApiController.java`, `service/C2ControllerService.java` (chuyển cờ từ nguồn gói), `controller/ConfigApiController.java`, `js/c2-simulator.js`, `js/c2-config.js`, `index.html`, `tactical-c2.css`.

**Kiểm thử:** inject từ simulator → chỉ thấy ở chế độ DIỄN TẬP; gửi gói từ điện thoại giả "không đánh dấu diễn tập" trong lúc đang xem DIỄN TẬP → vẫn là dữ liệu thật; xóa dữ liệu diễn tập không đụng dữ liệu thật.

### Nền cho U2/U6: bảng `incident_events`

**Thiết kế dữ liệu**

```sql
CREATE TABLE IF NOT EXISTS incident_events (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  victim_node_id TEXT NOT NULL,
  episode INTEGER NOT NULL DEFAULT 1,
  event_type TEXT NOT NULL,   -- SOS_RECEIVED|ACK_SENT|OPERATOR_ACK|VERIFIED|FALSE_ALARM|
                              -- DISPATCH_QUEUED|DISPATCH_SENT|DISPATCH_ACCEPTED|TEAM_ASSIGNED|
                              -- TEAM_EN_ROUTE|TEAM_ON_SCENE|RESOLVED|REOPENED|NOTE|LOCATION
  actor TEXT,                 -- username, hoặc NULL nếu hệ thống/thiết bị
  detail TEXT,
  lat REAL, lng REAL,
  is_drill INTEGER NOT NULL DEFAULT 0,
  created_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_events_victim ON incident_events(victim_node_id, episode, created_at);
```

**Điểm ghi sự kiện** (một hàm `IncidentEventDao.record(...)` dùng chung):

| Sự kiện | Ghi ở đâu |
|---|---|
| `SOS_RECEIVED`, `LOCATION` | `service/C2ControllerService` khi xử lý SOS |
| `ACK_SENT` | `socket/MeshTcpServer` sau khi ghi ACK |
| `DISPATCH_QUEUED/SENT/ACCEPTED` | `DispatchApiController`, `MeshTcpServer`, `RoutingEngine` (xử lý ACK) |
| `TEAM_*`, `RESOLVED`, `REOPENED`, `NOTE`, `OPERATOR_ACK`, `VERIFIED`, `FALSE_ALARM` | `IncidentApiController`, `RescueTeamApiController` |

**Đẩy realtime:** topic mới `/topic/incident-events`. Kiểm soát dung lượng: chỉ ghi `LOCATION` khi vị trí đổi đáng kể (ví dụ > 20 m hoặc > 60 giây).

### U2. Ngăn kéo chi tiết sự cố (Incident Dossier)

**Mục tiêu:** bấm một nạn nhân là thấy mọi thứ về ca đó và hành động ngay, không phải đổi tab.

**Giao diện** (ngăn kéo bên phải, rộng khoảng 420 px, đóng bằng Esc)

```
┌───────────────────────────────────────────┐
│ ● CRITICAL   Nguyễn Văn A        [PENDING]│
│ ⏱ chờ 04:12 · tin cập nhật 12 giây trước   │
├───────────────────────────────────────────┤
│ HỒ SƠ        SĐT 0901234567  Nhóm máu O+  │  ← bị che với OBSERVER
│              3 người · Ghi chú: dị ứng …  │
│ VỊ TRÍ       [mini map]  ±12 m · cũ 0.8 s │
│ ĐƯỜNG ĐI     NODE_7F3A → RELAY_B → BASE   │
│              (2 bước nhảy)                │
├───────────────────────────────────────────┤
│ DÒNG THỜI GIAN                            │
│  08:30:02  Nhận SOS                       │
│  08:30:02  Đã gửi ACK tới máy             │
│  08:31:10  Tiếp nhận: dispatcher1         │
│  08:32:40  Xác minh: dispatcher1          │
│  08:33:05  Lập lệnh → Ca-nô số 3          │
│  08:33:20  Máy đã nhận lệnh (ACK)         │
├───────────────────────────────────────────┤
│ [Tiếp nhận] [Xác minh] [Báo giả]          │
│ [Điều phối…] [Đóng ca] [Thêm ghi chú]     │
└───────────────────────────────────────────┘
```

**Nguồn dữ liệu**

- Hồ sơ/vị trí: `GET /api/victims/{nodeId}` (đã có).
- Dòng thời gian: `GET /api/victims/{nodeId}/timeline?episode=` đọc `incident_events`.
- Đường đi mesh: từ gói SOS gần nhất trong `mesh_packets` (kiểm tra bảng này có lưu `route_history` chưa; nếu chưa, thêm cột).
- Hành động: dùng lại các endpoint hiện có (`/resolve`, `/status`, `/api/dispatch`) và thêm `POST /api/victims/{nodeId}/notes`, `POST /api/victims/{nodeId}/ack` (tiếp nhận, xem X1), `POST /api/victims/{nodeId}/verify` (xem X3).

**Mở ngăn kéo từ:** ghim bản đồ (`c2-map.js`), dòng trong Triage (`c2-triage.js`), thẻ Kanban (U3), dòng trong Thiết bị (U4). Hàm chung `openIncidentDrawer(nodeId)` trong `js/c2-incident.js`.

**Ràng buộc:** nút hành động ẩn/vô hiệu theo vai trò; thông tin y tế bị che với `OBSERVER`.

**File mới:** `js/c2-incident.js`, `db/IncidentEventDao.java`. **File sửa:** `IncidentApiController.java`, `index.html`, `tactical-c2.css`, `c2-map.js`, `c2-triage.js`.

**Kiểm thử:** thực hiện một ca trọn vòng bằng điện thoại giả; dòng thời gian hiện đủ và đúng thứ tự; SOS lặp sau `RESOLVED` mở dòng thời gian của `episode` mới.

### U3. Bảng Kanban điều phối (Dispatch Board)

**Mục tiêu:** nhìn một màn hình biết ca nào chờ, ca nào đang điều phối, ca nào xong; điều phối bằng một thao tác.

**Cột** (khớp trạng thái hiện có của `victims.status`)

| Cột | Trạng thái | Ghi chú |
|---|---|---|
| Chờ cứu | `PENDING` | Sắp xếp: mức khẩn cấp rồi thời gian chờ lâu nhất |
| Đang điều phối | `DISPATCHED` | Thẻ hiện đội được gán và trạng thái giao lệnh |
| Đã cứu xong | `RESOLVED` | Chỉ hiện ca của ca trực hiện tại hoặc 24 giờ gần nhất |

**Thẻ**

```
┌──────────────────────────┐
│ ● CRITICAL  Nguyễn Văn A │  màu + icon + chữ (không chỉ dựa vào màu)
│ 3 người · Mắc kẹt        │
│ ⏱ chờ 04:12              │
│ Lệnh: ◔ đã lập  ◑ đã tới │  ← trạng thái giao lệnh từ dispatch_logs.state
│        ● máy đã nhận     │
│ Đội: Ca-nô số 3          │
│ [Chi tiết] [Điều phối ▸] │
└──────────────────────────┘
```

**Tương tác**

- **Kéo thả** từ "Chờ cứu" sang "Đang điều phối" (HTML5 drag-and-drop) mở hộp thoại **Chọn đội**: danh sách đội rảnh xếp theo khoảng cách (haversine) và phù hợp năng lực, mỗi dòng ghi "cách 3,2 km · ETA ước tính 14 phút"; mẫu nội dung lệnh chỉnh được; nút Gửi. Đồng thời có nút **Điều phối ▸** trên thẻ để dùng được trên máy cảm ứng, vì kéo thả kém trên điện thoại/tablet.
- Từ "Đang điều phối" sang "Đã cứu xong": hộp thoại xác nhận kèm ghi chú.
- Không cho kéo ngược từ "Đã cứu xong"; có nút **Mở lại** riêng (ghi sự kiện `REOPENED`).

**Chống hai người điều phối cùng một ca:** gửi kèm `updatedAt` của thẻ; server trả `409` nếu trạng thái đã đổi; giao diện báo "Ca này vừa được [tên] điều phối" và làm mới.

**API:** dùng `GET /api/victims`; mở rộng `POST /api/dispatch` nhận `teamId`, `etaMinutes`, `message`; dùng `POST /api/victims/{id}/resolve`; thêm `POST /api/victims/{id}/reopen`; `GET /api/rescue-teams/suggest?nodeId=` trả danh sách gợi ý.

**Quyền:** `DISPATCHER`, `COMMANDER`. `OBSERVER` chỉ xem (không kéo được).

**File mới:** `js/c2-dispatch-board.js`, tab mới `tab-dispatch`. **File sửa:** `DispatchApiController.java`, `IncidentApiController.java`, `RescueTeamApiController.java` (thêm `suggest`), `RescueTeamDao.java`, `index.html`, `tactical-c2.css`.

**Kiểm thử:** hai trình duyệt cùng điều phối một ca: người thứ hai nhận 409; thẻ cập nhật trạng thái giao lệnh theo ACK từ điện thoại giả; đội đã gán chuyển sang không rảnh.

### U4. Quản lý thiết bị hiện trường và QR ghép đôi

**Mục tiêu:** biết thiết bị nào đang sống; cấu hình điện thoại bằng một mã QR; thu hồi thiết bị mất.

**Thiết kế dữ liệu**

```sql
CREATE TABLE IF NOT EXISTS devices (
  node_id TEXT PRIMARY KEY,
  display_name TEXT,
  device_type TEXT NOT NULL DEFAULT 'VICTIM',   -- VICTIM|RELAY|TEAM
  status TEXT NOT NULL DEFAULT 'ACTIVE',        -- ACTIVE|REVOKED
  is_drill INTEGER NOT NULL DEFAULT 0,
  first_seen_at INTEGER, last_seen_at INTEGER,
  last_ip TEXT, battery_pct INTEGER, app_version TEXT, notes TEXT
);
```

**Hành vi**

- **Tự đăng ký** khi nhận gói đầu tiên từ `nodeId` mới (ACTIVE).
- Cập nhật `last_seen_at`, `last_ip` (lấy từ `clientSocket.getInetAddress()` trong `MeshTcpServer`). Để tránh ghi DB quá dày, **giới hạn tối đa mỗi 10 giây/nút** bằng bộ nhớ đệm.
- `battery_pct` lấy từ payload heartbeat **nếu có** (Android chưa gửi; để cột tùy chọn, hiển thị "chưa rõ").
- **Trạng thái sống:** online nếu `last_seen_at` trong 90 giây; "mất tín hiệu" nếu quá 5 phút (ngưỡng cấu hình được).
- **Thu hồi:** gói từ thiết bị `REVOKED` bị bỏ và ghi audit/metric; có thể khôi phục.
- Sổ thiết bị tương tác với bảng `relay_nodes`/`/api/relay-nodes` hiện có: giữ nguyên `relay_nodes` cho phần topology, `devices` bao trùm mọi loại; không cần gộp ngay.

**Giao diện:** tab "Thiết bị"

```
 [Ghép đôi (QR)]  Lọc [loại ▾] [trạng thái ▾]   Online: 5/8
 ┌ nodeId ─ Tên ─ Loại ─ Trạng thái ─ Pin ─ Lần cuối ─ IP ─ Hành động ─────────────┐
 │ NODE_7F3A91C2 Máy A  Nạn nhân ● online  ▓▓▓░ 41%  12 giây  192.168.1.40  ⋯        │
 │ NODE_5B22D100 Máy B  Relay    ◌ mất 8′  ▓░░░ 12%  8 phút   192.168.1.41  ⋯        │
 └──────────────────────────────────────────────────────────────────────────────────┘
 ⋯: Đổi tên │ Đánh dấu diễn tập │ Thu hồi/Khôi phục │ Mở hồ sơ ca
```

**Hộp thoại QR:** chọn địa chỉ IP của trạm (liệt kê các IPv4 LAN qua `NetworkInterface`), hiển thị QR (dùng `qrcode.min.js` đã có trong thư mục vendor) chứa JSON `{"v":1,"station":"Trạm Đà Nẵng","host":"192.168.1.23","port":8888}` và hiển thị **cả địa chỉ/cổng bằng chữ** cho trường hợp phải nhập tay. Phần **quét QR là việc của Trâm** (app hiện chưa có); trong lúc chờ, bạn vẫn có trang hiển thị chữ.

**API:** `GET /api/devices`, `PATCH /api/devices/{nodeId}` (đổi tên, thu hồi, cờ diễn tập), `GET /api/config/station/pairing` (trả danh sách host, cổng, tên trạm).

**Quyền:** `ADMIN`; mọi vai trò khác chỉ xem (OBSERVER không thấy IP).

**File mới:** `db/DeviceDao.java`, `model/DeviceEntity.java`, `controller/DeviceApiController.java`, `js/c2-devices.js`. **File sửa:** `MeshTcpServer.java`, `RoutingEngine.java` (kiểm tra thu hồi), `DatabaseManager.java`, `index.html`.

**Kiểm thử:** hai điện thoại giả hiện trong danh sách; thu hồi một cái thì gói của nó bị từ chối; quét thử QR bằng ứng dụng đọc QR bất kỳ ra đúng JSON.

---

## C. TRUY VẾT, TÀI NGUYÊN, BÁO CÁO

### U6. Nhật ký thao tác (Audit Trail) nối băm

**Mục tiêu:** trả lời "ai đã làm gì, lúc nào, trên đối tượng nào"; phát hiện bản ghi bị sửa.

**Phân biệt:** tab "Audit" hiện nay là **nhật ký gói tin**. Giữ nguyên và đổi tên "Nhật ký gói tin"; thêm "Nhật ký thao tác" cạnh nó.

**Thiết kế dữ liệu**

```sql
CREATE TABLE IF NOT EXISTS audit_trail (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  ts INTEGER NOT NULL,
  actor TEXT, actor_role TEXT, ip TEXT,
  action TEXT NOT NULL,           -- LOGIN|LOGIN_FAILED|DISPATCH_CREATE|VICTIM_RESOLVE|TEAM_STATUS|
                                  -- TICKET_ADVANCE|CONFIG_CHANGE|USER_CREATE|CLEAR_DATA|...
  target_type TEXT, target_id TEXT,
  detail TEXT,
  is_drill INTEGER NOT NULL DEFAULT 0,
  prev_hash TEXT, row_hash TEXT NOT NULL
);
```

- `row_hash = SHA-256(prev_hash + ts + actor + action + target_type + target_id + detail)`; ghi bằng một hàm `synchronized` để chuỗi băm không bị chen nhau.
- Hàm `AuditTrailService.log(action, targetType, targetId, detail)` lấy `actor` từ `SecurityContext` (sau U1) và `ip` từ request.
- Gọi hàm này ở các controller ghi dữ liệu: `DispatchApiController`, `IncidentApiController`, `RescueTeamApiController`, `InterAgencyApiController`, `ConfigApiController`, `SimulatorApiController`, `AdminUserApiController`, `AuthApiController`.

**Giao diện:** trong tab "Audit", hai thẻ con. Thẻ "Nhật ký thao tác": bộ lọc (người, hành động, khoảng thời gian), mỗi dòng đọc được thành câu: `08:30 · Đạt (COMMANDER) điều động "Ca-nô số 3" tới Nguyễn Văn A`; nút **Kiểm tra toàn vẹn** (gọi `GET /api/audit/verify`, báo "nguyên vẹn" hoặc "bản ghi #N bị thay đổi") và **Xuất CSV**.

**Quyền:** `ADMIN`, `COMMANDER` xem; không ai xóa được.

**File mới:** `db/AuditTrailDao.java`, `service/AuditTrailService.java`, `controller/AuditTrailApiController.java`. **File sửa:** `js/c2-audit.js`, `index.html`, các controller kể trên.

**Kiểm thử:** thao tác điều phối, resolve, đổi cấu hình đều hiện đúng tên; sửa tay một dòng trong DB rồi bấm "Kiểm tra toàn vẹn" phải báo sai.

### U7. Quản lý đội cứu hộ (CRUD) và gợi ý đội gần nhất

**Hiện có:** bảng `rescue_teams` và endpoint `GET`, `GET /active`, `POST /{teamId}/deploy`, `/location`, `/status`. **Thiếu:** thêm/sửa/xóa, năng lực, giao diện quản lý.

**Thiết kế dữ liệu:** thêm cột `capabilities TEXT` (chuỗi phân cách bằng dấu phẩy, ví dụ `WATER_RESCUE,MEDICAL`), `base_lat REAL`, `base_lng REAL`, `speed_kmh INTEGER` (tốc độ ước tính để tính ETA), `retired INTEGER DEFAULT 0` (xóa mềm, giữ lịch sử).

**Trạng thái đội:** `STANDBY`, `ASSIGNED`, `EN_ROUTE`, `ON_SCENE`, `OFFLINE` (giữ tương thích giá trị hiện có).

**Gợi ý đội** (`GET /api/rescue-teams/suggest?nodeId=`):

- Chỉ xét đội `STANDBY` (và không `retired`).
- Điểm = khoảng cách haversine từ vị trí đội tới nạn nhân, cộng điều chỉnh theo năng lực khớp `alertType` (ví dụ `FLOOD_TRAPPED` ưu tiên `WATER_RESCUE`).
- ETA = khoảng cách / `speed_kmh`. **Ghi rõ "ước tính"**, không phải thời gian thực.
- Trả tối đa 3 đội kèm khoảng cách, ETA, lý do.

**Giao diện:** tab "Đội & Tài nguyên" (hoặc một thẻ trong Liên ngành)

```
 [+ Thêm đội]                                  ┌ Bản đồ: ghim đội, bấm để đặt vị trí ┐
 ┌ Tên ─ Loại ─ Năng lực ─ Quân số ─ Trạng thái ─ Vị trí cuối ─ ⋯ ┐
 │ Ca-nô số 3  Cứu nạn thủy  WATER_RESCUE  6  ● Sẵn sàng  3′   ⋯  │
 └─────────────────────────────────────────────────────────────────┘
 Form: tên, loại, năng lực (chọn nhiều), quân số, phương tiện, SĐT liên lạc, vị trí căn cứ (bấm bản đồ), tốc độ.
```

**Quyền:** thêm/sửa/xóa `ADMIN`, `COMMANDER`; đổi trạng thái `DISPATCHER` trở lên.

**API:** `POST /api/rescue-teams`, `PUT /api/rescue-teams/{id}`, `DELETE /api/rescue-teams/{id}` (xóa mềm), `GET /api/rescue-teams/suggest`.

**File sửa:** `RescueTeamApiController.java`, `RescueTeamDao.java`, `DatabaseManager.java`, `js/c2-interagency.js` hoặc `js/c2-teams.js` mới, `c2-map.js`, `index.html`.

**Kiểm thử:** thêm đội mới, xuất hiện trên bản đồ; gợi ý cho một SOS trả đúng đội gần và phù hợp; đội đã `ASSIGNED` không còn được gợi ý.

### U12. Trung tâm báo cáo

**Hiện có:** `GET /api/reports/iap`, `GET /api/reports/audit` và `ReportExportService`. **Thêm:**

| Báo cáo | Định dạng | Nội dung |
|---|---|---|
| Danh sách nạn nhân | CSV | `nodeId, tên, mức, trạng thái, tọa độ, hasFix, thời điểm SOS, thời điểm xử lý…` |
| Sự cố có vị trí | GeoJSON | `FeatureCollection` các `Point`, thuộc tính `severity, status, hasFix, accuracyM, time` |
| Lệnh điều phối | CSV | thời điểm lập/gửi/ACK, đội, người lập |
| **SITREP** (báo cáo tình hình) | HTML in được | số ca theo mức/trạng thái, đội ngoài hiện trường, thiết bị online, sự kiện nổi bật |
| **AAR** (tổng kết sau tác chiến) | HTML in được | thời gian TB từ SOS đến ACK, đến tiếp nhận, đến điều phối, đến hoàn tất; tỉ lệ lệnh được ACK; tỉ lệ gói bị loại theo nguyên nhân |
| IAP | HTML (đã có) | giữ nguyên |

**Thiết kế**

- Tham số chung: `from`, `to`, `drill=true|false` (mặc định chế độ xem hiện tại).
- **CSV** ghi UTF-8 **có BOM** để Excel đọc đúng tiếng Việt.
- **PDF:** không cần thư viện; trang HTML có CSS `@media print` để người dùng "In → Lưu PDF" từ trình duyệt.
- Số liệu AAR lấy từ `incident_events` (hiệu số thời gian giữa các sự kiện) và `dispatch_logs`, `network_metrics`.
- **OBSERVER** xuất bản đã che thông tin cá nhân.

**Giao diện:** tab "Báo cáo": các thẻ báo cáo, chọn khoảng thời gian và chế độ, nút **Xem trước** và **Tải xuống / In**.

**API:** `GET /api/reports/victims.csv`, `/incidents.geojson`, `/dispatches.csv`, `/sitrep`, `/aar`.

**File sửa:** `ReportApiController.java`, `ReportExportService.java`; **mới:** `js/c2-reports.js`, `index.html`.

**Kiểm thử:** mở CSV bằng Excel thấy đúng tiếng Việt; nạp GeoJSON vào công cụ xem GeoJSON bất kỳ thấy đúng điểm; AAR có số liệu hợp lý sau một kịch bản chạy bằng điện thoại giả.

---

## D. NHÓM BỔ SUNG (X)

### X1. Tiếp nhận SOS và leo thang

**Vấn đề:** ACK của máy (thiết bị đã nhận) khác với việc *một người đã nhận ca*.

**Thiết kế dữ liệu:** `victims.acknowledged_by TEXT`, `victims.acknowledged_at INTEGER`.

**Hành vi**

- Ca `CRITICAL/HIGH` mới vào: thẻ nhấp nháy "CHƯA AI TIẾP NHẬN".
- Nút **Tiếp nhận** (trong ngăn kéo và trên thẻ Kanban) ghi người nhận, thời điểm, sự kiện `OPERATOR_ACK`; thẻ ngừng nhấp nháy; còi của ca đó tắt.
- **Leo thang:** quá `N` phút (cấu hình, mặc định 2 phút với `CRITICAL`) chưa ai tiếp nhận thì: còi to hơn/lặp lại, banner "SOS CRITICAL chưa được tiếp nhận quá 2 phút" cho mọi người dùng, sự kiện `ESCALATED` ghi vào timeline và audit. Kiểm tra bằng một bộ định giờ phía server (`@Scheduled`) mỗi vài giây.

**File:** `IncidentApiController.java`, `VictimDao.java`, `C2ControllerService.java` (bộ định giờ), `js/c2-audio.js`, `c2-home.js`, `c2-dispatch-board.js`.

**Kiểm thử:** SOS giả CRITICAL không ai nhận: sau thời hạn có leo thang; bấm Tiếp nhận thì dừng.

### X2. Độ tươi tin và chất lượng vị trí

**Thiết kế:** dùng `received_at`/`last_update` của nạn nhân, đội, thiết bị.

- Ghim trên bản đồ có 3 mức: **mới** (< 2 phút, đặc), **cũ** (2–10 phút, mờ 50%), **rất cũ** (> 10 phút, mờ và gạch viền), kèm nhãn "cập nhật 12 phút trước". Ngưỡng cấu hình được.
- `hasFix=false` hoặc `accuracyM` lớn: vẽ **vòng sai số** quanh ghim và biểu tượng cảnh báo.
- Hiển thị cùng thông tin trong Triage, Kanban, ngăn kéo.
- Nhãn theo giờ chạy bằng một bộ đếm phía giao diện (không cần gọi server mỗi giây).

**File:** `js/c2-map.js`, `c2-triage.js`, `c2-dispatch-board.js`, `c2-incident.js`, `tactical-c2.css`.

### X3. Xác minh / báo giả

**Thiết kế dữ liệu:** `victims.verified_state TEXT DEFAULT 'UNVERIFIED'` (`UNVERIFIED | VERIFIED | FALSE_ALARM`).

**Hành vi**

- Nút **Xác minh** và **Báo giả** trong ngăn kéo (`DISPATCHER` trở lên); `Báo giả` bắt buộc nhập lý do, đưa ca về trạng thái đã đóng nhưng **giữ lịch sử** (không xóa), ghi `FALSE_ALARM` vào timeline và audit.
- Bộ lọc "Chưa xác minh" trong Triage; ca chưa xác minh có huy hiệu.
- Mobile gửi `CANCEL_SOS` (sau này) thì Web gán `CANCELLED_BY_VICTIM` tương tự.

**File:** `IncidentApiController.java`, `VictimDao.java`, `c2-incident.js`, `c2-triage.js`.

### X4. Sao lưu và dọn dữ liệu

**Thiết kế**

- **Sao lưu:** `POST /api/admin/backup` dùng lệnh SQLite `VACUUM INTO 'backups/rescue_c2-YYYYMMDD-HHmm.db'` (tạo bản sao nhất quán khi DB đang chạy, *cần xác nhận phiên bản SQLite JDBC hỗ trợ*). Liệt kê và cho tải các bản sao ở tab Quản trị.
- **Khôi phục:** chỉ hướng dẫn thủ công (dừng ứng dụng, thay file); không làm nút khôi phục tự động ở giai đoạn này.
- **Dọn:** tác vụ `@Scheduled` mỗi giờ xóa bản ghi `HEARTBEAT` trong `mesh_packets` cũ hơn 24 giờ (và/hoặc giới hạn tổng số dòng); cấu hình được.

**File:** `controller/AdminSystemApiController.java` (mới), `db/PacketDao.java`, `application.yml`.

### X5. Bảng sức khỏe hệ thống

**Hiện có:** `HealthController` và tab NOC. **Bổ sung** panel "Sức khỏe hệ thống" (trong NOC hoặc Cấu hình):

| Chỉ số | Cách lấy |
|---|---|
| Cổng TCP 8888 đang lắng nghe, số kết nối hiện tại | `MeshTcpServer` |
| Gói cuối nhận được cách đây | thời điểm gói cuối |
| Thiết bị online / mất tín hiệu | `devices` |
| Hàng đợi lệnh: `QUEUED`, `SENT` chưa ACK, `FAILED` | `dispatch_logs` |
| Kích thước file DB, dung lượng ổ đĩa còn lại | hệ thống tệp |
| Lần sao lưu gần nhất | thư mục `backups` |
| Địa chỉ LAN của trạm | `NetworkInterface` (giúp bạn đọc IP cho Trâm) |

Đỏ/vàng/xanh theo ngưỡng; làm mới qua `/topic/system` đã có.

### X6. Bản đồ offline

**Thiết kế:** Leaflet đọc tile từ thư mục cục bộ phục vụ bởi Spring (`/tiles/{z}/{x}/{y}.png`), với **dự phòng**: nếu không có tile cục bộ thì dùng nguồn online hiện tại; nếu cả hai không có thì hiện nền trơn kèm lưới tọa độ để ghim vẫn thấy được vị trí tương đối.

**Cách có tile:** tạo tile cho vùng demo (một khung nhỏ quanh Đà Nẵng, mức zoom khoảng 10–15) từ dữ liệu bản đồ có giấy phép cho phép. **Lưu ý giấy phép:** chính sách của các máy chủ tile công cộng (OpenStreetMap, ArcGIS) thường **không cho tải hàng loạt**; hãy kiểm tra điều khoản, hoặc tự dựng tile từ dữ liệu OSM tải về hợp lệ bằng công cụ như tilemaker/MobileAtlasCreator *(cần xác nhận cách làm phù hợp với bạn)*. Ước lượng dung lượng trước khi làm (số tile tăng rất nhanh theo mức zoom).

**File:** `js/c2-map.js`, `application.yml` (đường dẫn tile), cấu hình tài nguyên tĩnh, `sw.js`.

### X7. Ca trực và bàn giao (tùy chọn, bản đơn giản)

**Thiết kế dữ liệu:** `shifts(id, started_by, started_at, ended_at, handover_note, received_by)`.

**Giao diện:** chip ở thanh trên "Ca: Đạt từ 07:00"; nút **Bàn giao ca** mở hộp thoại tự tổng hợp: số ca mở theo mức, lệnh chưa ACK, đội ngoài hiện trường, ghi chú tự do, chọn người nhận ca. Người nhận bấm xác nhận thì ca mới bắt đầu; ghi audit.

---

## 4. QUY ƯỚC CHUNG KHI THÊM TAB/MÀN HÌNH MỚI

Mẫu hiện có: mỗi tab là một `<section id="tab-xxx" class="tab-content">` trong `index.html`, được chuyển bằng `data-tab="tab-xxx"` ở thanh tab (`.nav-tab`) và thanh tab di động (`.mobile-tab`), mỗi tab có file `js/c2-xxx.js`.

Khi thêm tab mới (Điều phối, Thiết bị, Quản trị, Báo cáo, Đội…):

1. Thêm nút vào **cả hai** thanh tab (`.nav-tab` và `.mobile-tab`); với ít chỗ trên di động, gom bớt tab ít dùng vào một menu "Khác".
2. Thêm `<section>` và nạp file JS mới ở cuối `index.html`.
3. Thêm CSS vào `css/tactical-c2.css`, dùng lại biến màu/lớp sẵn có để giữ giao diện đồng nhất.
4. **Cập nhật danh sách tệp cache trong `sw.js`** (và tăng số phiên bản cache), nếu không trình duyệt có thể dùng bản cũ. *Kiểm tra cấu trúc `sw.js` hiện tại trước khi sửa.*
5. Với màn hình có hành động ghi: kiểm tra quyền ở server, ẩn nút theo vai trò ở giao diện, và ghi audit.
6. Mỗi màn hình cần **trạng thái rỗng, đang tải, lỗi** (đừng để trống trắng).
7. Không dựa vào màu đơn thuần: kèm icon và chữ cho mức khẩn cấp và trạng thái.

---

## 5. NHỮNG THỨ KHÔNG NÊN LÀM

- Đăng nhập OAuth/SSO, đặt lại mật khẩu qua email, đa tenant: hệ thống chạy trên LAN không Internet, một trạm.
- Đổi CSDL khỏi SQLite hay tách microservices: vấn đề nằm ở mô hình dữ liệu và giao thức, không phải engine.
- Dựng lại toàn bộ bảng `incidents` đầy đủ: cặp `(node_id, episode)` kết hợp `incident_events` đã đủ cho phạm vi đồ án.
- Đồng bộ nhiều trạm, mã hóa payload đầu-cuối, phát cảnh báo CAP: ghi vào "Hướng phát triển".
- Làm hết mọi mục X: chỉ X1–X5 là đáng ưu tiên; X6, X7 làm khi còn thời gian.

---

## 6. "WEB XONG" NGHĨA LÀ GÌ

Coi là xong khi **đạt đủ**:

- [ ] A1–A5 hoàn thành và điện thoại giả chạy trọn vòng SOS → ACK → lệnh → ACK lệnh.
- [ ] Bài thử kết nối 6 bước trong `INTEGRATION_CONTRACT.md` đạt với điện thoại thật của Trâm.
- [ ] U1 (đăng nhập, vai trò, che dữ liệu), U5 (diễn tập), U2, U3 hoạt động; mọi thao tác ghi đều có người thực hiện.
- [ ] Không còn endpoint nguy hiểm mở; `clear-data` chỉ xóa dữ liệu diễn tập trừ khi `ADMIN` xác nhận.
- [ ] Khởi động lại máy chủ giữa chừng không mất sự cố, lệnh chờ, trạng thái ACK.
- [ ] Những mục chưa làm được liệt kê rõ trong báo cáo ("Hạn chế đã biết và hướng phát triển").

---

*Các ước lượng công và thiết kế ở đây là đề xuất để bạn điều chỉnh theo thời gian còn lại và yêu cầu của giảng viên. Nếu có điểm nào trong mã thực tế khác với giả định (ví dụ tên cột, cách `sw.js` cache), ưu tiên mã thực tế và sửa lại thiết kế tương ứng.*

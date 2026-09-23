# KẾ HOẠCH PHÁT TRIỂN EMERGENCY MESH COMMAND SYSTEM

## 1. Mục đích của tài liệu

Tài liệu này là lộ trình triển khai phần còn thiếu của dự án theo
`Emergency_Mesh_Command_System_Spec.md`. Kế hoạch được thiết kế để Antigravity chỉ
thực hiện **một phase lớn trong mỗi lần làm việc**. Mỗi phase lớn gồm nhiều phase
nhỏ phải được hoàn thành tuần tự, kiểm thử sau từng bước, rồi dừng lại để Codex
kiểm tra trước khi mở phase lớn kế tiếp.

Không được xem trạng thái `100%` trong `src/MODULE_STATUS.md` là trạng thái hoàn
thành của toàn hệ thống. Repository hiện tại mới là prototype JavaFX với các node
Java mô phỏng trên host/port cố định.

## 2. Quy tắc làm việc bắt buộc cho Antigravity

1. Mỗi lần chỉ làm đúng **một phase lớn** được ghi rõ trong prompt.
2. Trong phase lớn đó, làm các phase nhỏ theo đúng thứ tự; không làm trước nội
   dung của phase lớn sau.
3. Trước khi sửa, phải đọc:
   - Tài liệu đặc tả do người dùng cung cấp.
   - File kế hoạch này.
   - `pom.xml`, `src/MODULE_STATUS.md` và các file liên quan trực tiếp.
4. Phải chạy `git status --short` trước và sau khi làm. Không xóa hoặc ghi đè thay
   đổi có sẵn của người dùng.
5. Sau mỗi phase nhỏ, phải chạy bộ kiểm tra phù hợp. Không để đến cuối phase lớn
   mới kiểm thử tất cả.
6. Không dùng mock/fake để tuyên bố một tính năng thiết bị thật đã hoàn thành.
   Fake chỉ được dùng cho unit test và phải được ghi rõ.
7. Không đổi giao thức tùy tiện. Mọi thay đổi schema phải có fixture JSON và test
   tương thích Java/Kotlin.
8. Không thêm thư viện chỉ vì tiện; phải giải thích mục đích, phiên bản và tác động
   tới chạy ngoại tuyến/đóng gói.
9. Không tự ý commit, push, tạo pull request hoặc sửa lịch sử Git nếu prompt không
   yêu cầu.
10. Khi hoàn tất phase lớn, phải dừng và báo cáo theo mẫu ở mục 4. Không bắt đầu
    phase lớn kế tiếp.

## 3. Definition of Done chung

Một phase lớn chỉ được coi là hoàn thành khi đồng thời đạt các điều kiện sau:

- Mã nguồn compile thành công trên toolchain đã chốt.
- Toàn bộ test cũ và test mới đều vượt qua.
- Không còn stub, TODO hoặc nhánh giả lập được trình bày như tính năng thật trong
  phạm vi phase đó.
- Có tài liệu cách chạy/kiểm tra tính năng vừa thêm.
- Có bằng chứng lệnh test/build và kết quả cuối cùng.
- `git diff` chỉ chứa thay đổi thuộc phạm vi phase hiện tại.
- Các tiêu chí nghiệm thu riêng của phase đều đạt.

Nếu một tiêu chí không thể kiểm tra do thiếu điện thoại, SDK, quyền hệ thống hoặc
phần cứng, Antigravity phải ghi trạng thái là **CHƯA XÁC MINH**, không được ghi
`PASS` hoặc `100%`.

## 4. Mẫu báo cáo bắt buộc sau mỗi phase lớn

```text
PHASE ĐÃ LÀM: Phase N — <tên phase>

1. Phase nhỏ đã hoàn thành
- N.1: ... — PASS/FAIL/CHƯA XÁC MINH
- N.2: ... — PASS/FAIL/CHƯA XÁC MINH

2. File đã thêm/sửa
- đường/dẫn/file: mục đích

3. Lệnh đã chạy
- <lệnh>: PASS/FAIL, số test

4. Tiêu chí nghiệm thu
- <tiêu chí>: PASS/FAIL/CHƯA XÁC MINH, bằng chứng

5. Hạn chế hoặc rủi ro còn lại
- ...

6. Git status cuối cùng
<kết quả git status --short>

ĐÃ DỪNG SAU PHASE N. CHƯA THỰC HIỆN PHASE N+1.
```

## 5. Tổng quan thứ tự các phase lớn

| Phase | Kết quả chính | Phụ thuộc |
|---|---|---|
| 1 | Nền tảng build và giao thức chuẩn, ổn định | Prototype hiện tại |
| 2 | Desktop lưu SQLite và Socket Gateway tin cậy | Phase 1 |
| 3 | Bản đồ desktop ngoại tuyến thật sự | Phase 2 |
| 4 | Demo Desktop đa tiến trình/LAN: SOS → relay → Base → dispatch → ACK | Phase 1–3 |
| 5 | Độ tin cậy, quan sát và kịch bản lỗi cho demo Desktop | Phase 4 |
| 6 | Đóng gói Desktop và tổng duyệt giữa kỳ | Phase 5 |
| 7 | Android Kotlin foundation — **để sau giữa kỳ** | Sau Phase 6 và quyết định mới |

Không gộp hai phase lớn vào cùng một lần giao việc.

## 5.1. Trạng thái triển khai và nghiệm thu

| Phase | Trạng thái triển khai | Trạng thái nghiệm thu | Ngày nghiệm thu | Ghi chú |
|---|---|---|---|---|
| Phase 1 | **HOÀN THÀNH** | **PASS — ĐÃ NGHIỆM THU** | 2026-09-04 | Codex kiểm tra độc lập: JDK 21 `clean test` PASS 91/91, `package` PASS, JDK 17 bị Enforcer chặn đúng, `git diff --check` PASS; các regression về checksum, canonical JSON, malformed input và numeric collision đều PASS. |
| Phase 2 | **HOÀN THÀNH** | **PASS — ĐÃ NGHIỆM THU** | 2026-09-04 | Codex kiểm tra độc lập: JDK 21 `clean test` PASS 134/134, `clean package` PASS và sinh đủ JAR; JDK 17 bị Enforcer chặn đúng, `git diff --check` PASS; migration/repository/restart persistence, bounded Socket Gateway, ACK/outbox retry, E2E ba socket, storage failure và shutdown-during-initialization đều PASS. |
| Phase 3 | **HOÀN THÀNH** | **PASS — ĐÃ NGHIỆM THU** | 2026-09-05 | Codex triển khai phần còn lại và kiểm tra độc lập: JDK 21 `clean package` PASS 162/162; fixture MBTiles được track qua ngoại lệ `.gitignore`; tile cache/pool có giới hạn; marker định danh theo packet ID, queue/replay an toàn, route chỉ vẽ khi resolver có tọa độ relay. Smoke test đã mở MBTiles local, tile server loopback, SQLite/socket và Leaflet offline thành công. |
| Phase 4 | **HOÀN THÀNH** | **PASS — ĐÃ NGHIỆM THU** | 2026-09-05 | Codex hoàn thiện và kiểm tra độc lập: JDK 21 `clean test`/`clean package` PASS 177/177; cấu hình host/port/node ID rõ ràng, relay forward checksum/TTL/route history, dispatch/ACK qua ba SocketServer port động, SQLite ACKED và hướng dẫn hotspot/LAN đều có test/tài liệu. |
| Phase 5 | **CHƯA TRIỂN KHAI** | **CHƯA NGHIỆM THU** | — | Độ tin cậy và quan sát demo Desktop; chi tiết tại `MIDTERM_DESKTOP_DEMO_PLAN.md`. |
| Phase 6 | **CHƯA TRIỂN KHAI** | **CHƯA NGHIỆM THU** | — | Đóng gói và tổng duyệt Desktop; chi tiết tại `MIDTERM_DESKTOP_DEMO_PLAN.md`. |
| Phase 7 | **HOÃN** | **CHƯA NGHIỆM THU** | — | Android được dời sang sau giữa kỳ. |

---

## 5.2. Quyết định phạm vi giữa kỳ — 2026-09-05

Mục tiêu trước giữa kỳ là chứng minh **hệ Desktop Java/JavaFX** chạy được luồng
cứu hộ trên nhiều process (và nếu có điều kiện, nhiều máy tính trong LAN), không
phải demo Android. Vì vậy, các mục Android/Wi-Fi Direct/Room/GPS trong bản kế
hoạch ban đầu bên dưới là backlog sau giữa kỳ và **không được phép triển khai lúc
này**.

Lộ trình có hiệu lực cho phần việc còn lại trước giữa kỳ là
[`MIDTERM_DESKTOP_DEMO_PLAN.md`](MIDTERM_DESKTOP_DEMO_PLAN.md). Nếu có mâu thuẫn,
file này và quyết định phạm vi ở đây được ưu tiên hơn các section Android cũ.

---

# PHASE 1 — ỔN ĐỊNH NỀN TẢNG BUILD VÀ GIAO THỨC CHUẨN

> **Trạng thái: HOÀN THÀNH — PASS — ĐÃ ĐƯỢC CODEX NGHIỆM THU NGÀY 2026-09-04.**
>
> Bằng chứng nghiệm thu cuối: 91/91 automated tests PASS trên JDK 21; Maven
> package PASS và sinh đủ hai fat JAR; JDK 17 bị Maven Enforcer từ chối đúng;
> fixture round-trip và các kiểm thử checksum tampering, delimiter collision,
> malformed/overflow number, số lớn vượt 2^53 và decimal precision đều PASS.
> Không được sửa lại phạm vi Phase 1 trong Phase 2 trừ khi có regression được
> chứng minh bằng test và được báo cáo rõ.

## Mục tiêu

Tạo nền móng duy nhất cho Java desktop và Android Kotlin giao tiếp với nhau về
sau. Sau phase này, build có thể tái lập, schema JSON được chốt, validation và
checksum hoạt động nhất quán, nhưng chưa xây Android, SQLite hay bản đồ mới.

## Phase 1.1 — Chuẩn hóa build Java

- Chốt Java 21.
- Thay cấu hình `source/target` bằng `maven.compiler.release=21`.
- Thêm Maven Wrapper để có thể chạy bằng `mvnw.cmd` mà không cần Maven toàn cục.
- Thêm Maven Enforcer hoặc kiểm tra tương đương để báo lỗi rõ khi dùng Java dưới
  21.
- Kiểm tra `.gitignore` bỏ qua `target/`, IDE metadata, database runtime và file
  bản đồ dung lượng lớn.
- Tạo `README.md` tối thiểu gồm yêu cầu Java 21, lệnh test, package và chạy hai
  ứng dụng mô phỏng hiện tại.

### Nghiệm thu 1.1

- `mvnw.cmd --version` sử dụng Java 21 hoặc mới hơn.
- `mvnw.cmd clean test` chạy được trên checkout sạch.
- Khi dùng Java 17, build dừng với thông báo yêu cầu Java 21 dễ hiểu.

## Phase 1.2 — Chốt schema MeshPacket canonical v1

Tạo tài liệu `docs/protocol/mesh-packet-v1.md` và chốt các tên JSON dạng
`snake_case` theo đặc tả:

- `packet_id`
- `protocol_version`
- `packet_type`
- `source_node_id`
- `destination_node_id`
- `sender_hop_id`
- `ttl`
- `hop_count`
- `timestamp`
- `payload`
- `route_history`
- `checksum`

Các loại gói tin canonical:

- `SOS_BROADCAST`
- `DISPATCH_COMMAND`
- `ROUTE_DISCOVERY`
- `ACK`
- `HEARTBEAT`

Payload phải mô tả rõ field chung và field riêng. Location gồm `latitude`,
`longitude`, `altitude` và `accuracy`. ACK phải có field machine-readable
`ack_for_packet_id`, không chỉ nhét ID vào chuỗi message. Chọn và ghi rõ đơn vị,
kiểu dữ liệu, field bắt buộc/tùy chọn, giới hạn độ dài và giá trị enum.

Cập nhật Java model/factory/routing/UI/test sang schema canonical. Nếu cần giữ
tương thích với tên cũ trong giai đoạn chuyển đổi, chỉ cho phép đọc alias cũ;
mọi JSON mới xuất ra phải dùng schema v1.

### Nghiệm thu 1.2

- JSON do Java serialize khớp fixture canonical.
- Có fixture hợp lệ cho đủ năm loại packet.
- Không còn tạo packet mới với `SOS_DATA` hoặc `DISPATCH_CMD`.
- Các giá trị trong fixture có thể dùng nguyên vẹn cho test Kotlin ở Phase 4.

## Phase 1.3 — Validation và checksum nhất quán

- Tạo validator trả về lỗi có cấu trúc thay vì để null hoặc giá trị sai đi sâu vào
  routing/UI.
- Validate UUID/ID, protocol version, packet type, TTL, hop count, timestamp,
  destination, payload, giới hạn message, victim count và biên GPS.
- Chốt thuật toán canonicalization dùng để tính checksum.
- Checksum phải bảo vệ các trường bất biến và routing metadata cần bảo vệ, không
  chỉ bảo vệ payload.
- Vì relay thay đổi TTL, hop count, sender hop và route history, phải định nghĩa rõ
  relay cập nhật packet rồi tính lại checksum trước khi gửi.
- Ghi rõ SHA-256 checksum chỉ phát hiện lỗi/toàn vẹn, không phải xác thực danh tính
  hay chống giả mạo. Chưa triển khai HMAC/encryption trong phase này.
- So sánh checksum theo cách an toàn, không làm ứng dụng crash với JSON lỗi.

### Nghiệm thu 1.3

- Sửa payload, source, destination, TTL hoặc route history mà không tính lại
  checksum đều bị từ chối.
- Relay hợp lệ cập nhật metadata, tính lại checksum và được next hop chấp nhận.
- Packet sai version/type/GPS/TTL bị validator từ chối với reason rõ ràng.

## Phase 1.4 — Mở rộng test nền tảng

- Unit test đầy đủ cho serialize/deserialize và validation.
- Parameterized test cho enum và boundary values.
- Test checksum tampering từng nhóm field.
- Test backward-read nếu có alias cũ.
- Test fixture round-trip: đọc fixture → object → JSON canonical → object.
- Giữ toàn bộ test cũ hoặc cập nhật hợp lý theo schema mới; không xóa test chỉ để
  build xanh.

### Nghiệm thu Phase 1

- `mvnw.cmd clean test` PASS.
- `mvnw.cmd package` PASS.
- Có README build/run và protocol document.
- Có ít nhất một fixture JSON cho từng packet type.
- Không có thay đổi SQLite, bản đồ, Android hoặc thuật toán mesh ngoài phần cần
  thiết để dùng protocol v1.

### Không được làm trong Phase 1

- Không tạo Android project.
- Không thêm SQLite/Room.
- Không sửa bản đồ online thành offline.
- Không triển khai Wi-Fi Direct hoặc Store-and-Forward.

---

# PHASE 2 — HOÀN THIỆN DESKTOP: SQLITE VÀ SOCKET GATEWAY

> **Trạng thái: HOÀN THÀNH — PASS — ĐÃ ĐƯỢC CODEX NGHIỆM THU NGÀY 2026-09-04.**
>
> Bằng chứng nghiệm thu cuối: 134/134 automated tests PASS trên JDK 21;
> Maven `clean package` PASS và sinh đủ JAR; JDK 17 bị Maven Enforcer từ chối
> đúng; `git diff --check` PASS. Các kiểm thử migration/repository và khôi phục
> dữ liệu sau restart, Socket Gateway hữu hạn và graceful shutdown, ACK/outbox
> retry, E2E qua ba socket, lỗi storage và shutdown trong lúc DB initialization
> đều PASS. Không được sửa lại phạm vi Phase 2 trong Phase 3 trừ khi có regression
> được chứng minh bằng test và được báo cáo rõ.

## Mục tiêu

Biến Base Station từ dashboard lưu trong RAM thành trạm chỉ huy có dữ liệu bền
vững và gateway chịu lỗi có giới hạn tài nguyên.

## Phase 2.1 — Thiết kế SQLite và migration

- Thêm `sqlite-jdbc` với phiên bản cố định.
- Tạo database config cho đường dẫn runtime, không ghi DB vào source tree.
- Tạo migration có version cho các bảng tối thiểu:
  - `packets`: raw canonical JSON, loại, source/destination, thời gian, checksum,
    trạng thái xử lý.
  - `sos_events`: dữ liệu nạn nhân, severity, location, trạng thái cứu hộ.
  - `nodes`: last seen, địa chỉ, port/capability và trạng thái.
  - `dispatch_commands`: nội dung, target, trạng thái gửi/ACK, số lần thử.
  - `routes`: packet/node/route history khi cần truy vết.
- Thêm index cho packet ID, timestamp, source node, severity và dispatch status.
- Bật foreign key và transaction rõ ràng.

## Phase 2.2 — Repository và tích hợp dashboard

- Tách persistence interface khỏi controller JavaFX.
- Lưu packet trước hoặc trong cùng luồng xử lý nghiệp vụ thích hợp.
- Khi mở ứng dụng, nạp lại SOS và số liệu thống kê từ DB.
- Duplicate packet không tạo bản ghi SOS thứ hai.
- Dispatch được lưu với trạng thái `PENDING/SENT/ACKED/FAILED`.
- Lỗi DB phải hiển thị/log nhưng không làm chết accept loop.
- Mọi thao tác DB nặng chạy ngoài JavaFX Application Thread.

## Phase 2.3 — Làm cứng Socket Gateway

- Đổi `newCachedThreadPool()` sang pool giới hạn 20 worker đúng đặc tả.
- Có queue hữu hạn và chính sách từ chối/log rõ ràng khi quá tải.
- Cấu hình connect/read timeout hợp lý.
- Giới hạn kích thước một frame JSON; packet quá lớn bị từ chối.
- Dùng UTF-8 rõ ràng ở cả đọc và ghi.
- Không log toàn bộ dữ liệu không tin cậy hoặc thông tin nhạy cảm.
- Graceful shutdown đóng accept thread, client socket đang mở và worker pool.
- Bắt lỗi parser/validator trên từng packet mà không ngắt server.

## Phase 2.4 — ACK và outbox desktop

- Base Station tạo ACK khi tiếp nhận SOS hợp lệ.
- Dispatch dùng outbox trong SQLite để không mất lệnh khi relay tạm offline.
- Retry có exponential backoff, giới hạn số lần và trạng thái cuối.
- ACK cập nhật đúng dispatch bằng `ack_for_packet_id`.
- Không block UI trong thời gian retry.

## Phase 2.5 — Test desktop

- Test migration từ DB rỗng và mở lại DB đã có schema.
- Repository test bằng DB tạm.
- Socket integration test với port động, JSON hợp lệ/sai, frame quá lớn, nhiều
  client và shutdown.
- Test restart Base Station vẫn thấy SOS/dispatch cũ.
- Test outbox offline → online → gửi lại thành công.

### Nghiệm thu Phase 2

- Toàn bộ test PASS.
- Base Station restart không mất SOS.
- Không tạo trùng SOS khi nhận lại cùng packet ID.
- Socket worker được giới hạn, không còn cached pool vô hạn.
- Dispatch offline được lưu và thử lại có kiểm soát.

### Không được làm trong Phase 2

- Không tạo Android project.
- Không làm Wi-Fi Direct.
- Không làm hệ thống tile map offline hoàn chỉnh.

---

# PHASE 3 — BẢN ĐỒ DESKTOP NGOẠI TUYẾN THẬT SỰ

## Mục tiêu

Dashboard hiển thị bản đồ, marker và route khi máy hoàn toàn không có Internet.
Không còn request tới CDN hoặc tile provider bên ngoài.

## Phase 3.1 — Chốt chiến lược dữ liệu bản đồ

- Viết ADR trong `docs/architecture/` chọn định dạng tile cục bộ, ưu tiên MBTiles
  hoặc giải pháp tương đương có giấy phép rõ ràng.
- File bản đồ lớn phải đặt ngoài Git và cấu hình qua đường dẫn; repository chỉ chứa
  fixture bản đồ nhỏ dùng cho test.
- Ghi tài liệu cách chuẩn bị/copy vùng bản đồ demo và attribution/license.

## Phase 3.2 — Bundle Leaflet hoàn toàn cục bộ

- Đưa Leaflet CSS, JS và image assets vào resources hoặc dependency cục bộ.
- Xóa mọi URL `unpkg.com`, CartoDB và OpenStreetMap tile online.
- Trang HTML phải khởi tạo được khi chặn toàn bộ network.
- Có trạng thái UI rõ nếu file bản đồ cục bộ thiếu/hỏng.

## Phase 3.3 — Cấp tile cục bộ

- Xây tile provider nội bộ đọc nguồn đã chọn.
- Nếu dùng HTTP loopback, chỉ bind `127.0.0.1`, port động, validate path và shutdown
  cùng ứng dụng.
- Có cache giới hạn, content type đúng và chống path traversal.
- Không tự động fallback ra Internet.

## Phase 3.4 — Marker và route bridge đáng tin cậy

- Queue marker đến trước khi WebView load xong và replay khi map ready.
- Escape/serialize dữ liệu Java sang JS an toàn bằng JSON, không ghép chuỗi thủ
  công.
- Hiển thị marker theo severity và route history nếu có tọa độ node phù hợp.
- Cho phép chọn một SOS trong bảng để focus đúng marker.
- Xử lý tọa độ lỗi/ngoài vùng tile mà không crash.

## Phase 3.5 — Kiểm thử offline

- Unit/integration test tile provider.
- Test HTML không chứa URL mạng.
- Chạy smoke test với network bị ngắt/chặn: mở dashboard, tải tile cục bộ, thêm
  marker và focus marker.
- Lưu bằng chứng test/screenshot trong báo cáo, không commit ảnh tạm nếu không cần.

### Nghiệm thu Phase 3

- Tìm kiếm `http://` và `https://` trong map runtime không còn dependency mạng.
- Dashboard hiển thị vùng demo khi offline hoàn toàn.
- SOS tới trước khi map ready vẫn xuất hiện sau khi map load.
- Thiếu MBTiles tạo thông báo dễ hiểu thay vì trang trống.

### Không được làm trong Phase 3

- Không phát triển Android.
- Không thay đổi thuật toán routing ngoài nhu cầu hiển thị route.

---

# ARCHIVED POST-MIDTERM BACKLOG A — KHUNG ỨNG DỤNG ANDROID KOTLIN VÀ PROTOCOL COMPATIBILITY

## Mục tiêu

Tạo Android project build được, có kiến trúc cơ bản và Kotlin model đọc/ghi đúng
MeshPacket v1. Chưa tuyên bố có kết nối Wi-Fi Direct ở phase này.

## Phase 4.1 — Khởi tạo Android project

- Tạo module/thư mục Android rõ ràng, ví dụ `android-app/`.
- Kotlin, Gradle Wrapper, Android Gradle Plugin và version catalog được cố định.
- Chốt minSdk/targetSdk phù hợp thiết bị demo và ghi trong README.
- Tạo application ID, build types debug/release và ProGuard/R8 baseline.
- Thêm Jetpack Compose Material 3, Coroutines và lifecycle libraries cần thiết.

## Phase 4.2 — Kiến trúc ứng dụng tối thiểu

- Chia package `protocol`, `network`, `routing`, `data`, `service`, `location`,
  `ui`.
- Dùng ViewModel + StateFlow cho state; không đưa socket/database trực tiếp vào
  Composable.
- Có abstraction cho clock, ID generator và transport để test được.
- Không thêm framework DI lớn nếu constructor injection đủ dùng.

## Phase 4.3 — Kotlin MeshPacket v1

- Implement Kotlin data model, enum/serializer, validator và checksum canonical
  tương thích Java.
- Dùng fixture từ Phase 1, không tạo schema Kotlin riêng.
- Test Java fixture → Kotlin → canonical JSON và ngược lại.
- Test Unicode tiếng Việt, null/optional, numeric precision và malformed JSON.

## Phase 4.4 — Room nền tảng

- Tạo Room entities/DAO/database cho packet inbox, outbox, node và message status.
- Schema phù hợp protocol v1 và có migration test baseline.
- Repository API dùng coroutine/Flow, không truy cập DB trên main thread.
- Chưa triển khai retry/routing đầy đủ; chỉ chuẩn bị persistence primitives.

## Phase 4.5 — UI shell và kiểm thử build

- Tạo màn hình shell hiển thị node ID và trạng thái `Transport chưa khởi động`.
- Có navigation placeholder hợp lý nhưng không giả vờ tính năng đã hoạt động.
- Unit test protocol/Room và Compose smoke test cơ bản.
- Build APK debug thành công.

### Nghiệm thu Phase 4

- Gradle Wrapper build được từ command line.
- Unit test Android PASS.
- APK debug được tạo và cài/mở được trên emulator hoặc thiết bị.
- Kotlin đọc fixture Java và tạo JSON được Java test chấp nhận.
- UI ghi rõ transport chưa triển khai.

### Không được làm trong Phase 4

- Không tuyên bố Wi-Fi Direct, multi-hop, GPS hoặc offline map đã hoàn thành.
- Không sửa Desktop ngoài fixture/test tương thích cần thiết.

---

# ARCHIVED POST-MIDTERM BACKLOG B — WI-FI DIRECT, FOREGROUND SERVICE VÀ SOCKET ANDROID

## Mục tiêu

Hai thiết bị Android thật có thể khám phá, kết nối và trao đổi MeshPacket v1 qua
Wi-Fi Direct trong một phiên service nền ổn định.

## Phase 5.1 — Technical spike và ADR về giới hạn Wi-Fi Direct

- Xác minh hành vi trên đúng model/Android version của ít nhất hai máy demo.
- Ghi ADR về group owner/client, IP discovery và giới hạn một thiết bị tham gia
  nhiều group đồng thời.
- Phân biệt rõ one-hop đã chứng minh với multi-hop chưa chứng minh.
- Nếu API/thiết bị không hỗ trợ topology dự kiến, dừng phần tuyên bố multi-hop và
  đề xuất topology demo khả thi; không tạo log giả.

## Phase 5.2 — Permission và peer discovery

- Khai báo quyền đúng theo API level, gồm `NEARBY_WIFI_DEVICES` và location cho
  phiên bản Android cần nó.
- Có runtime permission flow và giải thích khi người dùng từ chối.
- Implement `WifiP2pManager`, BroadcastReceiver và lifecycle đăng ký/hủy đăng ký.
- Peer discovery có state `Idle/Discovering/PeersFound/Connecting/Connected/Error`.
- Không giữ Activity/Context gây memory leak.

## Phase 5.3 — Kết nối và xác định endpoint

- Kết nối peer, xử lý group owner/client và lấy IP endpoint thực.
- Có handshake protocol trao đổi node ID, protocol version và listening port.
- Reject peer sai protocol version hoặc node ID không hợp lệ.
- Có reconnect/backoff khi peer rời vùng phủ sóng.

## Phase 5.4 — MeshForegroundService

- Notification channel và persistent notification đúng quy định Android.
- Service sở hữu lifecycle của discovery/socket; UI chỉ bind/observe state.
- Quản lý WakeLock/WifiLock tối thiểu, timeout rõ và luôn release khi stop/error.
- Khởi động lại có kiểm soát; không tạo nhiều server socket/service trùng.

## Phase 5.5 — Socket transport Android

- Server/client dùng framing tương thích desktop, UTF-8, timeout và giới hạn kích
  thước packet.
- Coroutine chạy trên `Dispatchers.IO`, structured concurrency và cancellation.
- Một peer lỗi không làm chết toàn transport.
- Gửi/nhận packet qua repository/interface, chưa nhúng routing phức tạp vào socket.

## Phase 5.6 — Kiểm thử hai thiết bị

- Fake transport unit tests cho state machine/reconnect.
- Instrumentation test phù hợp cho service/database.
- Thực nghiệm hai máy: discover → connect → handshake → gửi heartbeat/SOS test →
  disconnect → reconnect.
- Ghi model máy, Android version và kết quả thật.

### Nghiệm thu Phase 5

- Hai thiết bị thật trao đổi packet thành công khi tắt Internet/mobile data.
- Service tiếp tục duy trì transport khi app về background trong khoảng demo.
- Reconnect sau khi ngắt/kết nối lại đạt.
- Không tuyên bố multi-hop nếu mới chỉ test one-hop.

### Không được làm trong Phase 5

- Không làm UI cứu hộ hoàn chỉnh.
- Không làm bản đồ/GPS.
- Không tuyên bố Store-and-Forward hoặc multi-hop hoàn chỉnh.

---

# ARCHIVED POST-MIDTERM BACKLOG C — ROUTING, ACK, RETRY VÀ STORE-AND-FORWARD

## Mục tiêu

Packet có thể đi từ source qua relay tới Base Station, được lưu khi mất đường và
tự gửi tiếp khi có kết nối; dispatch đi ngược về đúng node và có ACK.

## Phase 6.1 — Routing core có thể kiểm thử

- Tách routing state machine khỏi Android service/UI và JavaFX controller.
- Implement duplicate suppression với cache giới hạn và expiry cấu hình được.
- TTL/hop/route history cập nhật chính xác và tính lại checksum sau relay.
- Không dùng `contains("NODE_A")` hoặc port hard-code để định tuyến.
- Route selection dùng peer registry/capability thực và có chiến lược rõ ràng.

## Phase 6.2 — Route discovery và reverse route learning

- Implement `ROUTE_DISCOVERY` theo protocol v1 hoặc cơ chế route advertisement đã
  được tài liệu hóa.
- Học đường về từ packet nhận được, gắn expiry và invalidation khi peer mất.
- Dispatch chọn next hop dựa trên route table, không dựa vào port 8001/8002.
- Tránh loop bằng packet ID, TTL và route validation.

## Phase 6.3 — Store-and-Forward Android

- Packet không có route/peer được lưu outbox Room trong transaction.
- Trạng thái tối thiểu: `PENDING`, `IN_FLIGHT`, `SENT_WAITING_ACK`, `DELIVERED`,
  `FAILED`, `EXPIRED`.
- Retry theo backoff có jitter, giới hạn lần thử và expiry.
- Khi service/app restart, hàng đợi tiếp tục đúng, không nhân bản packet.
- Ưu tiên CRITICAL nhưng tránh starvation mức khác.

## Phase 6.4 — ACK end-to-end và idempotency

- Đích cuối tạo ACK tham chiếu `ack_for_packet_id`.
- ACK đi ngược route và cập nhật outbox đúng packet.
- ACK trùng lặp là idempotent.
- Nếu ACK mất, resend không tạo SOS duplicate tại Base Station.
- UI/data layer nhận trạng thái delivery machine-readable.

## Phase 6.5 — Tương thích Desktop

- Desktop peer/node registry học địa chỉ/route từ handshake và packet.
- Xóa routing table static và relay port mặc định khỏi đường chạy production.
- Có thể giữ cấu hình simulator riêng cho automated test, đặt tên rõ là simulation.
- Desktop SQLite lưu packet/route/ACK và dispatch status.

## Phase 6.6 — Test đồ thị và mất kết nối

- Test graph in-memory A → B → Base cho SOS và Base → B → A cho dispatch.
- Test duplicate paths, loop, TTL expiry, corrupt checksum, ACK loss.
- Test B offline: A lưu; B online: A gửi; Base nhận một lần; ACK về A.
- Test restart node khi outbox còn packet.
- Chạy thử trên topology thiết bị thật khả thi đã ghi ở Phase 5.

### Nghiệm thu Phase 6

- Kịch bản Store-and-Forward vượt qua test tự động và test thiết bị thật.
- Không còn port/node ID hard-code trên đường chạy thật.
- Dispatch về đúng source và chuyển `ACKED/DELIVERED`.
- Duplicate hoặc resend không tạo sự kiện cứu hộ trùng.

### Không được làm trong Phase 6

- Không đại tu giao diện Android.
- Không làm bản đồ Android.

---

# ARCHIVED POST-MIDTERM BACKLOG D — UI ANDROID, GPS VÀ BẢN ĐỒ NGOẠI TUYẾN

## Mục tiêu

Hoàn thiện trải nghiệm hiện trường: gửi SOS một chạm, tự lấy GPS, theo dõi trạng
thái giao hàng, nhận dispatch và xem bản đồ khi offline.

## Phase 7.1 — Luồng quyền và GPS

- Xin quyền location theo ngữ cảnh, có trạng thái từ chối/từ chối vĩnh viễn.
- Dùng `FusedLocationProviderClient` với timeout và accuracy phù hợp.
- Hiển thị timestamp/accuracy; không dùng tọa độ cũ mà không cảnh báo.
- Có lựa chọn nhập tọa độ thủ công khi GPS không khả dụng.

## Phase 7.2 — Form SOS và nút một chạm

- Form tên, loại cảnh báo, message, số nạn nhân, severity.
- Validate dữ liệu trước khi tạo packet.
- Nút SOS lớn, chống double tap tạo nhiều packet và có xác nhận rõ.
- CRITICAL flow thao tác nhanh nhưng không gửi packet rỗng/sai vị trí âm thầm.

## Phase 7.3 — Trạng thái tin nhắn

- Danh sách packet hiển thị state từ Room/routing engine:
  `Đang chờ kết nối`, `Đang chuyển tiếp`, `Chờ xác nhận`, `Đã tới trạm`, `Lỗi`.
- Hiển thị hop count/route ở màn hình chi tiết, không dựa vào chuỗi log.
- App restart vẫn thấy lịch sử và trạng thái chính xác.

## Phase 7.4 — Nhận dispatch

- Notification/rung khi có lệnh mới.
- Màn hình chi tiết hiển thị nội dung, mức ưu tiên và thời gian.
- Gửi ACK tự động/idempotent theo Phase 6.
- Không hiển thị cùng một lệnh nhiều lần khi relay resend.

## Phase 7.5 — Bản đồ Android ngoại tuyến

- Dùng OsmDroid hoặc giải pháp đã chốt với tile/MBTiles cục bộ.
- Không phụ thuộc tile online trong đường chạy demo.
- Hiển thị vị trí hiện tại, điểm SOS và trạng thái thiếu dữ liệu bản đồ.
- Tài liệu cách chép vùng bản đồ demo, attribution và dung lượng cần thiết.

## Phase 7.6 — Accessibility và test UI

- Touch target, contrast, content description và font scaling hợp lý.
- UI không phụ thuộc màu duy nhất để thể hiện severity/status.
- Compose UI tests cho luồng SOS, permission states, delivery states và dispatch.
- Smoke test trên ít nhất hai kích thước màn hình thiết bị demo.

### Nghiệm thu Phase 7

- Người dùng gửi SOS với GPS bằng luồng một chạm rõ ràng.
- Có trạng thái từ lúc tạo tới lúc ACK.
- Nhận dispatch có notification và không duplicate.
- Bản đồ hoạt động khi tắt Internet.

### Không được làm trong Phase 7

- Không thay đổi protocol/routing trừ sửa bug đã chứng minh và có regression test.

---

# ARCHIVED POST-MIDTERM BACKLOG E — END-TO-END, HARDENING, DEMO VÀ PHÁT HÀNH

## Mục tiêu

Chứng minh toàn hệ thống chạy theo kịch bản đặc tả, xử lý lỗi cơ bản và tạo bộ cài
có tài liệu để nhóm trình diễn lại được.

## Phase 8.1 — Ma trận tương thích và môi trường demo

- Ghi model điện thoại, Android version, Java 21 distribution, Windows version,
  firewall rule và port cần mở.
- Script/checklist chuẩn bị laptop, DB, bản đồ và APK.
- Kiểm tra clock/timezone và node ID duy nhất.

## Phase 8.2 — Kịch bản end-to-end chuẩn

- Tắt mobile data và Internet theo đúng đặc tả.
- Source gửi SOS → relay → Base Station.
- Dashboard lưu DB, phát cảnh báo, thêm dòng và marker offline.
- Base gửi dispatch → relay → source.
- Source hiển thị lệnh; ACK quay về; dashboard cập nhật trạng thái.
- Ghi bằng chứng packet ID, route history, hop count và timestamp xuyên suốt.

## Phase 8.3 — Kịch bản lỗi bắt buộc

- Relay tắt khi gửi rồi bật lại.
- Base Station restart khi còn outbox.
- Packet trùng/corrupt/TTL hết.
- GPS unavailable và map file thiếu.
- Nhiều SOS gần nhau và nhiều client kết nối.
- Xác minh không crash, không mất dữ liệu đã cam kết và không tạo cứu hộ trùng.

## Phase 8.4 — Security và resource hardening phù hợp đồ án

- Viết threat model ngắn: packet giả, replay, malformed input, thiết bị lạ, flood.
- Giới hạn packet/rate/connection/log/database growth.
- Không gọi checksum SHA-256 là authentication.
- Nếu thêm shared-key HMAC, phải version protocol, quản lý key rõ và test cross-
  platform; không tự thêm encryption nửa vời.

## Phase 8.5 — Đóng gói phát hành

- Desktop dùng `jlink/jpackage` hoặc bộ phân phối có bundled runtime để không phụ
  thuộc Java mặc định trên máy.
- Tạo APK release phù hợp demo, ghi rõ signing strategy.
- Không đóng gói file map lớn vào Git nếu vượt giới hạn; có installer/copy guide.
- Kiểm tra trên máy/thiết bị sạch, không chỉ IDE của người phát triển.

## Phase 8.6 — Tài liệu và checklist bảo vệ

- README tổng thể, kiến trúc, protocol, cách build/run, troubleshooting.
- Sơ đồ topology thật và topology simulator phải được phân biệt.
- Kịch bản demo 5 bước, lệnh khởi động, expected output và phương án fallback.
- Cập nhật `MODULE_STATUS.md` bằng trạng thái có bằng chứng; mục chưa test thiết bị
  phải ghi `CHƯA XÁC MINH`.

### Nghiệm thu Phase 8

- Kịch bản end-to-end chạy thành công ít nhất hai lần từ môi trường sạch.
- Toàn bộ automated tests PASS.
- Desktop installer chạy không phụ thuộc Java 17 đang có trên máy.
- APK release cài/chạy được trên thiết bị demo.
- Offline map, Store-and-Forward và dispatch ACK được chứng minh khi không Internet.

---

# 6. Quy trình kiểm tra giữa các phase với Codex

Sau khi Antigravity hoàn tất một phase lớn:

1. Không yêu cầu Antigravity làm tiếp.
2. Gửi cho Codex câu: `Kiểm tra Phase N Antigravity vừa làm theo
   ANTIGRAVITY_DEVELOPMENT_PLAN.md`.
3. Codex sẽ kiểm tra diff, chạy build/test, đối chiếu tiêu chí nghiệm thu và phân
   loại:
   - `PASS`: được mở phase kế tiếp.
   - `PASS CÓ ĐIỀU KIỆN`: cần sửa nhỏ hoặc ghi nhận phần chưa xác minh.
   - `FAIL`: Antigravity phải sửa đúng Phase N, chưa được sang Phase N+1.
4. Chỉ sau khi phase được chấp nhận, Codex mới tạo prompt riêng cho phase lớn tiếp
   theo dựa trên trạng thái thực tế của repository.

# 7. Prompt đầu tiên giao Antigravity — chỉ làm Phase 1

Sao chép nguyên prompt dưới đây cho Antigravity:

```text
Bạn đang làm việc trong repository emergency-mesh-desktop.

NHIỆM VỤ DUY NHẤT CỦA LẦN NÀY:
Thực hiện PHASE 1 — ỔN ĐỊNH NỀN TẢNG BUILD VÀ GIAO THỨC CHUẨN trong file
ANTIGRAVITY_DEVELOPMENT_PLAN.md.

Hãy đọc đầy đủ trước khi sửa:
1. Emergency_Mesh_Command_System_Spec.md do người dùng cung cấp.
2. ANTIGRAVITY_DEVELOPMENT_PLAN.md.
3. pom.xml, src/MODULE_STATUS.md và toàn bộ model/routing/test hiện tại liên quan.

Phạm vi được phép gồm đúng các phase nhỏ 1.1, 1.2, 1.3 và 1.4. Làm tuần tự:
- 1.1 Chuẩn hóa Java 21, Maven Wrapper, .gitignore và README.
- 1.2 Chốt MeshPacket canonical v1, tài liệu protocol và fixture JSON.
- 1.3 Thêm validation và checksum canonical bảo vệ cả metadata cần thiết; relay
  phải tính lại checksum sau khi cập nhật routing metadata.
- 1.4 Mở rộng unit/contract tests.

Ràng buộc:
- Chỉ làm Phase 1. Không tạo Android project, SQLite/Room, offline map, Wi-Fi
  Direct hoặc Store-and-Forward.
- Giữ hành vi simulator hiện có hoạt động sau khi đổi protocol.
- Không xóa test để làm build xanh.
- Không gọi checksum SHA-256 là authentication.
- Không tự commit/push.
- Kiểm tra git status trước và sau.
- Chạy test sau từng phase nhỏ; cuối cùng chạy Maven Wrapper clean test và package.
- Nếu máy không có Java 21 hoặc không thể kiểm tra tiêu chí nào, ghi CHƯA XÁC
  MINH, không giả định là PASS.

Tiêu chí hoàn tất bắt buộc là toàn bộ mục "Nghiệm thu Phase 1" trong kế hoạch.
Khi xong, báo cáo đúng mẫu tại mục 4 của kế hoạch và DỪNG LẠI. Không thực hiện
Phase 2 dưới bất kỳ hình thức nào.
```

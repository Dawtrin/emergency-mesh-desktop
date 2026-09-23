# MIDTERM DESKTOP DEMO PLAN — EMERGENCY MESH RESCUE

## 1. Quyết định phạm vi

Mục tiêu giữa kỳ là một demo đáng tin cậy bằng **Desktop Java/JavaFX**. Hệ thống
phải chạy được trên một laptop với ba process và có thể cấu hình để chạy trên các
máy tính trong cùng LAN. Luồng phải là thật, không phải mô phỏng bằng log:

`Victim Node → Relay Node → Base Station → Dispatch → Relay Node → Victim Node → ACK`.

Android, Kotlin, Wi-Fi Direct, Room, GPS và bản đồ Android được hoãn sang cuối kỳ.
Không agent nào được tạo `android-app/`, thêm Android SDK/Gradle, hay tuyên bố tính
năng điện thoại trong các Phase 4–6 dưới đây.

## 2. Trạng thái đầu vào đã nghiệm thu

- Phase 1: protocol MeshPacket v1, validation và checksum canonical — PASS.
- Phase 2: SQLite desktop, Socket Gateway giới hạn tài nguyên, dispatch outbox/ACK
  — PASS.
- Phase 3: bản đồ MBTiles/Leaflet offline trên Base Station — PASS.
- Cơ sở hiện tại có simulator JavaFX, nhưng chưa được xem là demo đa process/LAN
  hoàn chỉnh cho tới khi Phase 4 được nghiệm thu.

## 3. Cách làm việc

Mỗi lần Antigravity chỉ được làm **một phase lớn**. Trong phase đó làm tuần tự các
subphase, chạy kiểm thử thích hợp sau từng subphase, báo cáo, rồi dừng. Codex sẽ
nghiệm thu trước khi phase kế tiếp được mở.

Không xóa test để làm build xanh, không tự commit/push, không ghi đè thay đổi có
sẵn, không gọi SHA-256 checksum là authentication, và không dùng dữ liệu/log giả
để chứng minh networking thật.

## 4. PHASE 4 — DESKTOP MULTI-PROCESS/LAN END-TO-END

### Mục tiêu

Làm cho luồng demo chạy được bằng ba ứng dụng Java desktop độc lập, trên cùng máy
hoặc nhiều máy LAN được cấu hình rõ ràng. Không còn phụ thuộc vào chuỗi port/node
ID hard-code ở đường chạy production.

### 4.1 — Chốt topology và cấu hình demo

- Viết `docs/demo/desktop-topology.md` gồm topology một laptop và topology LAN,
  node ID, hướng kết nối, port, firewall note và sơ đồ packet flow.
- Tách cấu hình runtime rõ ràng: bind host/port, next-hop host/port, node ID,
  base endpoint, map-file. Giữ default hợp lý chỉ cho local demo, không dùng
  default để ép routing production.
- Validate node ID, port (1–65535), host rỗng/không hợp lệ và xung đột bind port.
- Thông báo lỗi cấu hình bằng UI/log dễ hiểu; không crash mơ hồ.

### 4.2 — Forwarding relay đúng protocol

- Relay nhận packet rồi validate protocol/checksum/TTL trước khi forward.
- Cập nhật hop count, route history và checksum theo protocol v1 đúng một lần cho
  mỗi forward; packet invalid/expired/duplicate không được forward.
- Giữ connection/retry/timeout bounded của Phase 2; lỗi một peer không làm chết
  relay hoặc UI.
- Không viết routing discovery, store-and-forward hay thuật toán mesh Android ở
  phase này. Đây là demo tuyến relay cấu hình tường minh.

### 4.3 — Dispatch ngược và ACK thật

- Base Station dispatch tới đúng victim qua relay dựa trên route/peer information
  đã quan sát trong phiên, hoặc cấu hình demo được tài liệu hóa; không dựa vào
  so sánh chuỗi node ID hay một port bí mật.
- Victim hiển thị dispatch, tạo ACK với `ack_for_packet_id`, relay forward ACK về
  Base; Base chỉ đánh dấu ACKED khi nhận ACK hợp lệ.
- Trạng thái UI/log phải phân biệt: queued, forwarded, delivered-to-relay,
  waiting-for-ACK, ACKED, failed. Không hiển thị `ACKED` chỉ vì đã bấm Send.
- Restart Base khi dispatch còn pending phải dùng persistence/outbox Phase 2,
  không tạo dispatch duplicate.

### 4.4 — Dashboard và trải nghiệm người trình diễn

- Dashboard hiển thị node/connection activity và route history theo packet thực.
- SOS mới tạo row, cảnh báo, marker offline và có thể focus marker; dispatch status
  cập nhật trên cùng packet ID.
- Node UI có action gửi SOS demo với input được validate và vùng log dễ đọc; thêm
  action/hiển thị nhận dispatch nếu chưa có.
- Không sửa giao diện chỉ để “trông như đã gửi”; mọi trạng thái phải nối với event
  networking thật.

### 4.5 — Kiểm thử E2E đa process có thể lặp lại

- Thêm automated integration test dùng port động và process/socket thật hoặc test
  harness tương đương: Victim → Relay → Base, sau đó Base → Relay → Victim → ACK.
- Assert packet ID, hop count, route history, checksum tại relay, SQLite dispatch
  status cuối cùng và không có SOS duplicate.
- Test negative: sai checksum, TTL hết, relay unavailable, port conflict, peer
  disconnect. Có timeout để test không treo.
- Cập nhật README với lệnh mở ba process trên một laptop và biến/argument cho LAN.

### Nghiệm thu Phase 4

- Ba process chạy được với port động/cấu hình tường minh trên một máy.
- Demo SOS qua Relay tới Base và Dispatch/ACK ngược thành công, có packet ID và
  route/hop bằng chứng trong log/UI/DB.
- Không có hard-code node ID/port trên đường chạy thật ngoài default local được
  ghi rõ.
- `mvnw.cmd clean test` và `mvnw.cmd clean package` PASS trên JDK 21.
- Không phát triển Android hoặc thêm dependency Android.

## 5. PHASE 5 — ĐỘ TIN CẬY, QUAN SÁT VÀ KỊCH BẢN LỖI DESKTOP

### Mục tiêu

Demo không hỏng lặng khi mất relay/kết nối, và người trình bày hiểu ngay chuyện gì
đã xảy ra cũng như cách phục hồi.

### 5.1 — Connection state machine và reconnect

- Định nghĩa state machine máy khách/relay: disconnected, connecting, connected,
  retry-wait, failed, stopped; state phải observable/testable.
- Backoff có giới hạn và cancellation khi app đóng; không spin/retry vô hạn.
- Không làm nghẽn JavaFX UI thread; shutdown đóng socket/executor/server sạch.

### 5.2 — Delivery status và persistence đúng nghĩa

- Chuẩn hóa transition dispatch/SOS status, idempotency và timestamp trong SQLite.
- Restart process ở các điểm hợp lý; giải thích chính xác dữ liệu nào được phục hồi
  và dữ liệu nào chỉ thuộc một phiên socket.
- Giới hạn log/database growth đủ cho demo; lỗi write database không được báo giả
  là giao thành công.

### 5.3 — Fault-injection và regression test

- Test relay bị dừng khi có packet, restart relay, Base restart khi outbox pending,
  duplicate ACK, duplicate SOS, malformed/oversized packet và simultaneous clients.
- Assert không crash, không block indefinitely, không tạo SOS/dispatch duplicate và
  status cuối cùng có lý do lỗi rõ ràng.

### 5.4 — Chế độ demo và observability

- Thêm demo profile/config file được versioned (không chứa secrets) và một lệnh
  kiểm tra preflight port/JDK/map file trước buổi demo.
- Log có timestamp, node ID, packet ID, direction và trạng thái; không log payload
  nhạy cảm không cần thiết.
- Có guide troubleshooting ngắn: port occupied, Java version sai, map missing,
  firewall LAN, relay unavailable, database location.

### Nghiệm thu Phase 5

- Sau các lỗi bắt buộc, hệ thống phục hồi hoặc báo FAILED rõ ràng; không trạng thái
  giả/mập mờ.
- Regression/E2E tests PASS và dashboard vẫn đáp ứng.
- Checklist preflight phát hiện được cấu hình demo sai thông dụng.

## 6. PHASE 6 — ĐÓNG GÓI VÀ TỔNG DUYỆT GIỮA KỲ

### Mục tiêu

Người dùng có thể mang project sang máy Windows sạch hơn, chạy demo theo checklist
và lặp lại được trước giảng viên mà không cần IDE.

### 6.1 — Phân phối Desktop

- Chọn và tài liệu hóa `jpackage`/bundled runtime hoặc phương án distribution khác.
- Đóng gói Base Station và Node Client, không phụ thuộc Java 17 mặc định của máy.
- Giữ MBTiles demo ngoài Git nếu lớn; có fixture test nhỏ và hướng dẫn copy map
  demo/attribution.

### 6.2 — Script khởi động và reset an toàn

- Có script/batch/PowerShell rõ ràng để mở Base, Relay, Victim với config demo,
  không hard-code đường dẫn cá nhân.
- Lệnh reset dữ liệu phải là opt-in, nêu đúng database bị ảnh hưởng và có backup
  hoặc hướng dẫn xác nhận; không xóa ngầm.

### 6.3 — Tổng duyệt kịch bản giữa kỳ

- Viết checklist 5–7 phút: preflight → mở Base/Relay/Victim → gửi SOS → xem
  alert/map/history → gửi dispatch → nhận ACK → mô phỏng relay mất kết nối →
  khôi phục/fallback.
- Ghi expected output, screenshot/evidence cần chụp và phương án fallback local
  nếu LAN/firewall không sẵn sàng.
- Chạy ít nhất hai lần từ thư mục/môi trường sạch tương đối; ghi phiên bản JDK,
  Windows, port và kết quả thật.

### 6.4 — Regression cuối và tài liệu

- `clean test`, `clean package`, static checks và smoke-test artifacts.
- README, architecture/demo topology, protocol documentation, module status và
  known limitations khớp với trạng thái thật.
- Ghi rõ đây là **desktop simulation/demo**, chưa phải mesh điện thoại thật.

### Nghiệm thu Phase 6

- Build artifacts chạy được không cần IDE và không dùng Java 17 sai toolchain.
- Full demo chạy thành công hai lần, có bằng chứng packet/status.
- Tất cả automated tests PASS; các giới hạn còn lại được ghi rõ.

## 7. Backlog sau giữa kỳ — Android

Chỉ mở sau khi Phase 6 PASS và người dùng yêu cầu lại. Thứ tự dự kiến:

1. Android Kotlin foundation + protocol compatibility.
2. Wi-Fi Direct, service nền và socket giữa hai thiết bị thật.
3. Routing/store-and-forward/ACK liên thiết bị và tương thích Desktop.
4. UI SOS, GPS, dispatch, offline map Android và E2E final.

Các nội dung Android chi tiết trong `ANTIGRAVITY_DEVELOPMENT_PLAN.md` hiện chỉ là
tài liệu tham khảo bị hoãn; chúng không được phép triển khai trong phạm vi giữa kỳ.

## 8. Mẫu báo cáo sau mỗi phase

```text
PHASE ĐÃ LÀM: Phase N — <tên phase>

1. Subphase: N.1 ... — PASS/FAIL/CHƯA XÁC MINH
2. File đã thêm/sửa: <path> — <purpose>
3. Lệnh đã chạy: <command> — PASS/FAIL, số test
4. Nghiệm thu: <criterion> — PASS/FAIL/CHƯA XÁC MINH, evidence
5. Rủi ro/hạn chế còn lại: ...
6. Git status cuối: <output>

ĐÃ DỪNG SAU PHASE N. CHƯA THỰC HIỆN PHASE N+1.
```

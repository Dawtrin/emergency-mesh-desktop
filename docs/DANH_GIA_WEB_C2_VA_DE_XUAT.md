# ĐÁNH GIÁ TOÀN DIỆN MẢNG WEB/C2 & ĐỀ XUẤT HOÀN THIỆN
## Đồ án Cơ sở 4 — Emergency Mesh Rescue (Web & Backend)

- **Người phụ trách Web/Backend:** Trần Viết Đạt
- **Phạm vi đánh giá:** thư mục `desktop` (Spring Boot 3.2.5, TCP 8888, STOMP/WebSocket 8080, SQLite WAL, Dashboard C2)
- **Phương pháp:** đọc mã nguồn tĩnh (TCP server, `RoutingEngine`, `MeshPacket`, `ChecksumUtil`, `PacketFactory`, DAO/schema, controller, simulator, cấu trúc tab giao diện, test).
- **Chưa làm:** chưa chạy ứng dụng, chưa chạy test, chưa đọc toàn bộ JS giao diện. Bản này đã được đối chiếu thêm với mã Android (xem `DANH_GIA_ANDROID_MOBILE_VA_DE_XUAT.md` và `INTEGRATION_CONTRACT.md`). Mọi chỗ ghi *(cần xác nhận)* là suy luận từ code, bạn nên chạy lại để kiểm chứng.
- **Về phần đối chiếu quốc tế (mục 2):** viết từ kiến thức nền về các hệ thống/tiêu chuẩn, chưa tra cứu lại tài liệu hiện hành. Hãy kiểm tra nguồn gốc trước khi trích dẫn vào báo cáo.

---

## MỤC LỤC

0. Tóm tắt điều hành
0B. **5 điểm chung để kết nối Web ↔ Mobile** (đọc phần này trước khi ghép)
1. Đánh giá hiện trạng mảng Web
2. Đối chiếu với các hệ thống cứu nạn thực tế
3. Những gì còn thiếu và đề xuất cải tiến
4. Giao diện, tài khoản, phân quyền (phân tích chuyên sâu)
5. Lộ trình thực hiện và tiêu chí "Web xong, sẵn sàng ghép Mobile"
- Phụ lục A: Hợp đồng gói tin v1 (bản nháp) và test vector
- Phụ lục B: Máy trạng thái (SOS, lệnh điều phối)
- Phụ lục C: Phác thảo schema CSDL mới
- Phụ lục D: Ma trận phân quyền
- Phụ lục E: Checklist kiểm thử "Mobile Emulator"

---

## 0. TÓM TẮT ĐIỀU HÀNH

**Kết luận chính:** Mảng Web đã vượt mức một đồ án thông thường về độ rộng tính năng (8 tab, realtime, liên ngành, báo cáo IAP, simulator, PWA). Tuy nhiên nó đang mạnh ở **"hiển thị"** và yếu ở **"vòng khép kín nghiệp vụ"**: gói tin đi lên được, nhưng lệnh đi xuống, xác nhận (ACK), danh tính người thao tác và bảo mật chưa khép kín. Đây chính là những thứ sẽ vỡ ngay khi ghép với điện thoại thật.

| Hạng mục | Mức sẵn sàng | Ghi chú ngắn |
|---|---|---|
| Uplink SOS (điện thoại → Base) | Khá | Chạy được nếu hai bên khớp checksum |
| Checksum / toàn vẹn | Trung bình | Đang khớp với Android (cùng Gson, cùng thứ tự field) nhưng mong manh; chưa chống giả mạo |
| Downlink lệnh (Base → điện thoại) | **Chưa chạy thật** | Gửi tới địa chỉ cố định `localhost:8889` |
| ACK hai chiều | **Chưa có** | Base không ACK SOS; ACK nhận về chỉ in console |
| Mô hình dữ liệu nạn nhân/sự cố | **Có lỗi nghiệp vụ** | SOS mới của người đã RESOLVED bị ẩn |
| Xác thực / phân quyền | **Chưa có** | API xóa dữ liệu, điều phối đều mở |
| Dashboard offline | Một phần | Bản đồ lấy tile từ ArcGIS online |
| Giao diện vận hành | Tốt về trực quan, thiếu quản trị | Thiếu tài khoản, ca trực, chi tiết sự cố, quản lý thiết bị |
| Kiểm thử | Có nền | Có test lõi + E2E, thiếu test hợp đồng/chaos |

**Việc nên làm đầu tiên:** chốt hợp đồng gói tin + checksum chuẩn hóa (Phụ lục A), rồi dựng một **"điện thoại giả" (Mobile Emulator)** để hoàn thiện và kiểm thử toàn bộ Web mà không cần Android. Điều này khớp quyết định của bạn: *làm xong desktop rồi mới làm Mobile*.

---

## 0B. 5 ĐIỂM CHUNG ĐỂ KẾT NỐI WEB ↔ MOBILE (CHỈ CẦN KHỚP NHỮNG ĐIỂM NÀY)

**Nguyên tắc:** mỗi người tự hoàn thành phần của mình, không cần quy trình hay họp. Hai bên chỉ phải **khớp 5 điểm dưới đây**. Mọi thứ còn lại (HMAC, mã hóa, mesh nhiều bước nhảy, đổi phiên bản hợp đồng...) là phần độc lập hoặc để sau. Phần này giống hệt trong cả hai file đánh giá và trong `INTEGRATION_CONTRACT.md`.

| # | Điểm chung | Phía Web (Đạt) làm | Phía Android (Trâm) làm |
|---|---|---|---|
| 1 | **Base trả lời trên cùng kết nối TCP** (không để Web tự tìm IP điện thoại) | `MeshTcpServer` ghi lại trên chính socket: 1 dòng `ACK` (`ackType=RECEIVED`, `ackedPacketId`) rồi 0..N dòng `DISPATCH_CMD` đang chờ của `nodeId` đó, rồi đóng kết nối. Cần hàng đợi lệnh theo `nodeId`. | Sau khi gửi, giữ kết nối ~5 giây (`soTimeout`), đọc tối đa 16 dòng phản hồi. Chỉ hiện "đã tới trạm" khi đọc được `ACK RECEIVED` đúng `packetId`. Nhận lệnh thì gửi lại `ACK` (`ackType=ACCEPTED`, `ackedPacketId` = `packetId` của lệnh). |
| 2 | **Danh tính duy nhất** | Không giả định tên `nodeId`; `packetId` coi là chuỗi (UUID). | `nodeId = "NODE_" + 8 ký tự hex ngẫu nhiên`, sinh một lần, lưu DataStore (thay cho `Build.MODEL`). `packetId` = UUID đầy đủ 36 ký tự (thay cho 8 ký tự). |
| 3 | **Vị trí trung thực** (không tọa độ giả) | Thêm vào lớp `Location` các field **đúng tên, đúng thứ tự, đúng kiểu** (xem lưu ý checksum bên dưới). Khi `hasFix=false` hiển thị "chưa rõ vị trí / vị trí cũ" thay vì ghim như thật. | Bỏ tọa độ cố định Đà Nẵng. Gửi `hasFix`, `accuracyM`, `fixAgeMs`. Không có GPS: gửi vị trí cuối biết được kèm `hasFix=false`, hoặc `0.0/0.0` kèm `hasFix=false`. **Không dùng `null`** cho lat/lng. |
| 4 | **Hồ sơ y tế đi cùng SOS** | Đã có sẵn `phoneNumber, bloodType, medicalNotes` ở cuối `Payload`; sửa `ON CONFLICT` để cập nhật cả 3 field. | Thêm 3 field này **vào cuối** `Payload`, đúng thứ tự `phoneNumber, bloodType, medicalNotes`, lấy từ DataStore. |
| 5 | **Giữ nguyên cách tính checksum hiện tại** | Không đổi: `SHA-256(Gson().toJson(payload))`. | Không đổi. Từ chối checksum rỗng như Web. Mọi field mới chỉ thêm **ở cuối** đối tượng tương ứng. |

### Lưu ý quan trọng về checksum khi làm Điểm 3 (cả hai phải làm cùng lúc)

Web đọc JSON bằng Gson, **bỏ qua field lạ**, rồi serialize lại payload để băm. Nếu Android thêm field vào `location` mà Web chưa khai báo cùng tên, cùng thứ tự, cùng kiểu thì chuỗi băm của hai bên khác nhau và **mọi SOS bị `CHECKSUM_FAIL`**. Vì vậy:

- Thứ tự trong `location`: `latitude, longitude, hasFix, accuracyM, fixAgeMs`.
- Kiểu dữ liệu: `hasFix` = `Boolean` (kiểu bọc, để thiếu thì bị bỏ khỏi JSON), `accuracyM` = **số nguyên mét** (`Int`, làm tròn; tránh `Float` vì đổi sang `Double` dễ ra số lẻ dài), `fixAgeMs` = `Long`.
- Web hiện khai báo `latitude/longitude` kiểu `double` nguyên thủy (nên `null` sẽ biến thành `0.0` và làm lệch checksum), đó là lý do không dùng `null` ở Điểm 3.
- Thứ tự làm an toàn: **Web cập nhật `Location` trước**, sau đó Android mới bật gửi 3 field mới. Hoặc cả hai sửa cùng ngày.

### Gói mẫu để hai bên đối chiếu

SOS (điện thoại → Base):

```json
{
  "packetId": "11111111-2222-3333-4444-555555555555",
  "packetType": "SOS_DATA",
  "sourceNodeId": "NODE_7F3A91C2",
  "destinationNodeId": "BASE_STATION",
  "senderHopId": "NODE_7F3A91C2",
  "ttl": 5, "hopCount": 0, "timestamp": 1760000000000,
  "routeHistory": ["NODE_7F3A91C2"],
  "payload": {
    "senderName": "Nguyễn Văn A", "alertType": "FLOOD_TRAPPED",
    "message": "Nước dâng cao, mắc kẹt tầng 2", "victimCount": 3, "severity": "CRITICAL",
    "location": { "latitude": 16.067, "longitude": 108.152, "hasFix": true, "accuracyM": 12, "fixAgeMs": 800 },
    "phoneNumber": "0901234567", "bloodType": "O+", "medicalNotes": "Dị ứng penicillin"
  },
  "checksum": "<SHA-256 hex của Gson().toJson(payload)>"
}
```

Phản hồi của Base trên cùng kết nối (mỗi dòng một gói, đều có checksum như mọi gói khác):

```json
{"packetType":"ACK","sourceNodeId":"BASE_STATION","destinationNodeId":"NODE_7F3A91C2","payload":{"message":"RECEIVED","ackType":"RECEIVED","ackedPacketId":"11111111-2222-3333-4444-555555555555"}, "...": "..."}
{"packetType":"DISPATCH_CMD","sourceNodeId":"BASE_STATION","destinationNodeId":"NODE_7F3A91C2","payload":{"senderName":"Trạm C2","message":"Đội cứu hộ số 3 đang tới, ETA 15 phút","severity":"HIGH"}, "...": "..."}
```

> Với ACK, hai bên cần thống nhất đưa `ackType`/`ackedPacketId` vào `Payload` ở **cuối**, sau `medicalNotes` (cùng nguyên tắc "thêm ở cuối, cùng tên, cùng kiểu, cả hai khai báo"). Trong lúc chưa thêm, có thể tạm dùng `message = "ACK RECEIVED <packetId>"` và Android tìm theo chuỗi này.

### Bài thử kết nối (đạt bài này là ghép xong)

1. Cả hai nối cùng một Wi-Fi/hotspot; Trâm nhập IP Base (hoặc cấu hình sẵn).
2. Trâm bấm SOS trên điện thoại thật.
3. Web nhận, hiện đúng vị trí; hồ sơ y tế (SĐT, nhóm máu, ghi chú) xuất hiện; điện thoại hiện "đã tới trạm" **chỉ sau khi** nhận `ACK RECEIVED`.
4. Tắt Wi-Fi điện thoại, bấm SOS, bật lại: SOS tự gửi lại và vẫn tới Web.
5. Đạt điều phối trên Web; điện thoại nhận `DISPATCH_CMD` ở lần kết nối kế (heartbeat/SOS) và gửi `ACK ACCEPTED`; Web cập nhật trạng thái.
6. Hai máy cùng model (nếu có) không làm mất gói của nhau.

### Phần còn lại hoàn toàn độc lập

- **Web:** upsert/sự cố, đăng nhập tối thiểu, tắt endpoint xóa dữ liệu, tile offline, giao diện, báo cáo, retention.
- **Android:** vòng đời Service, tự gửi lại SOS khi có mạng, chế độ pin, UX hoảng loạn, ViewModel/test.

---

## 1. ĐÁNH GIÁ HIỆN TRẠNG MẢNG WEB

### 1.1. Kiến trúc hiện tại

```
 Android (Wi-Fi Direct mesh)                Base Station (laptop)
 ┌─────────┐   TCP JSON/line    ┌────────────────────────────────────────────┐
 │ Node A  │ ─────────────────▶ │ MeshTcpServer :8888                        │
 │ Relay B │                    │   └─ RoutingEngine (dedup→checksum→TTL)    │
 └─────────┘                    │        └─ C2ControllerService              │
      ▲                         │             ├─ SQLite (WAL)                │
      │ (DISPATCH_CMD)          │             └─ STOMP /topic/* ─────────┐   │
      └──── MeshTcpClient ◀──── │ DispatchApiController (REST)           │   │
        (host cố định :8889)    └────────────────────────────────────────┼───┘
                                        Dashboard C2 (browser) ◀─────────┘
                                        Leaflet · Chart.js · STOMP · PWA
```

### 1.2. Phân tích từng thành phần

**TCP Socket Server (`MeshTcpServer`, cổng 8888)**
- Mô hình: một thread cho mỗi kết nối (`meshExecutor`), đọc theo dòng (`readLine`), mỗi dòng là một `MeshPacket` JSON. Read timeout 5 giây.
- Tốt: tách khá sạch `Socket → MeshPacket → RoutingEngine → callback`, có đếm metrics (nhận/drop/duplicate/checksum fail).
- Hạn chế: `readLine()` không giới hạn độ dài dòng, không giới hạn số kết nối, không xác thực nguồn, không gửi phản hồi nào lại cho bên gửi trên cùng socket.

**RoutingEngine**
- Thứ tự xử lý: kiểm tra `packetId` → **dedup (đánh dấu đã thấy)** → **kiểm checksum** → phân loại theo `packetType`.
- Chuyển tiếp: trừ TTL, tăng `hopCount`, ghi `routeHistory`, gửi tới cổng đích tra từ `ROUTING_TABLE` tĩnh (`NODE_A → 8001`, `NODE_B → 8002`, host `localhost`).
- `ACK`: chỉ `System.out.println`. Không có callback lên tầng nghiệp vụ.
- Nhận định: phù hợp để demo mesh trên **một máy với nhiều cổng**, chưa phù hợp mô hình **nhiều điện thoại, mỗi máy một IP**.

**Web Server & REST**
- Các controller: Incident (`/api/victims`), Dispatch, InterAgency (phiếu, danh bạ, radio-logs, năng lực cơ sở), RescueTeam, Relay, Metrics, Report (IAP/audit), Simulator, Config, Health.
- Không có Spring Security trong `pom.xml`. Mọi endpoint mở, kể cả `POST /api/simulator/clear-data` (xóa toàn bộ dữ liệu vận hành) và `POST /api/dispatch`.

**WebSocket (STOMP over SockJS, `/ws`)**
- Các topic chính: `/topic/victims`, `/packets`, `/drops`, `/relay-status`, `/dispatch-ack`, `/system`, `/interagency`, `/radio`, `/facilities`.
- `setAllowedOriginPatterns("*")`, không có kiểm soát đăng ký topic theo người dùng.

**CSDL SQLite (WAL, Hikari pool 4)**
- Bảng: `victims`, `mesh_packets`, `dispatch_logs`, `interagency_dispatches`, `emergency_facilities`, `relay_nodes`, `network_metrics`, `rescue_teams`, `tactical_radio_logs`.
- Tốt: WAL, index cơ bản, migration `ALTER TABLE` an toàn.
- Hạn chế: `victims.node_id` là khóa chính (một dòng/thiết bị), không có bảng sự cố riêng, không có lịch sử vị trí, `mesh_packets` lưu cả heartbeat (sẽ phình).

**Dashboard C2**
- 8 tab: Home (KPI + sparkline), Map, Liên ngành, NOC, Triage, Topology, Audit, Config. PWA (`sw.js`, `manifest.json`), command palette, còi hú Web Audio.
- Điểm tốt: thư viện vendor đóng gói cục bộ (Leaflet, Chart.js, STOMP, font) nên chạy được khi mất Internet.
- Hạn chế: bản đồ lấy tile từ `services.arcgisonline.com` (xem lỗi D-07); tab "Audit" thực chất là **nhật ký gói tin** (thời gian, loại sự kiện, node, chi tiết, checksum), không phải nhật ký **ai làm gì**; tab Config trộn simulator với thông số trạm.

### 1.3. Danh sách lỗi và rủi ro (đã đối chiếu với code)

Mức độ: **S1** = chặn ghép Mobile · **S2** = gây sai nghiệp vụ/bảo mật · **S3** = nên sửa.

| ID | Mức | Vấn đề | Vị trí | Hệ quả khi ghép Mobile |
|---|---|---|---|---|
| D-01 | S2 | **Checksum phụ thuộc cách Gson serialize lại payload.** Thứ tự field, bỏ field null, escape ký tự HTML (`=`, `'`, `<`), cách in số thực (`16.0`/`16`, độ dài lat/lng) đều ảnh hưởng chuỗi băm. | `MeshPacket.computeAndSetChecksum / verifyChecksum` | **Hiện đang khớp** với code Android (cả hai dùng Gson, cùng thứ tự field `senderName…location`) nên SOS cơ bản vẫn qua; nhưng rất mong manh: thêm `phoneNumber/bloodType/medicalNotes` sai thứ tự, hoặc đổi thư viện JSON, là toàn bộ SOS bị `CHECKSUM_FAIL` |
| D-02 | S1 | **Downlink không tới được điện thoại.** Lệnh gửi tới `mesh.outbound.host:port` cố định (`localhost:8889`); `ROUTING_TABLE` chỉ có cổng 8001/8002 trên localhost. | `DispatchApiController`, `RoutingEngine.ROUTING_TABLE` | Nút "Điều phối" chạy trên giao diện nhưng điện thoại không nhận được |
| D-03 | S1 | **Không có ACK.** Base không gửi ACK cho SOS (`PacketFactory.createAck` không được gọi ở nơi nào khác). ACK nhận về chỉ in ra console; `DispatchDao` có hàm cập nhật `ack_received` nhưng không ai gọi. | `RoutingEngine` (case `TYPE_ACK`), `DispatchDao` | Nạn nhân không biết SOS đã tới; Base không biết lệnh đã tới; lệnh bị đánh dấu `DISPATCHED` ngay khi tạo |
| D-04 | S1 | **Upsert nạn nhân sai nghiệp vụ.** `ON CONFLICT(node_id) DO UPDATE` không cập nhật `status`, `phone_number`, `blood_type`, `medical_notes`. | `VictimDao.insertOrUpdate` | Người đã `RESOLVED` gửi SOS mới vẫn `RESOLVED` (SOS bị ẩn); sửa hồ sơ y tế trên máy không bao giờ lên Web |
| D-05 | S2 | **Dedup chạy trước checksum.** Gói hỏng cùng `packetId` được đánh dấu "đã thấy" và chặn gói đúng gửi lại. Cache chỉ sống 60 giây. | `RoutingEngine.processPacket`, `SeenPacketCache` | Dễ bị "đầu độc cache"; gói store-carry-forward đến muộn bị xử lý như gói mới |
| D-06 | S2 | **Không có xác thực.** Không Spring Security; WebSocket cho mọi origin; REST mở hoàn toàn; checksum chỉ là SHA-256 công khai nên ai trong hotspot cũng tự tạo được SOS "hợp lệ". Sender của lệnh bị gán cứng `"Trạm trưởng C2"`. | `WebSocketConfig`, mọi controller | SOS giả, điều phối giả, xóa dữ liệu bởi bất kỳ ai cùng mạng; audit không có người thật |
| D-07 | S2 | **Bản đồ phụ thuộc Internet.** Tile ArcGIS tải online; service worker chỉ cache file tĩnh. | `c2-map.js` (`L.tileLayer('https://services.arcgisonline.com/...')`) | Trạm không có mạng thì bản đồ trống |
| D-08 | S2 | **Checksum chỉ bao `payload`.** `packetId`, `type`, `source`, `destination`, `timestamp` không được bảo vệ. | `MeshPacket.computeAndSetChecksum` | Có thể sửa đích/nguồn/loại gói mà checksum vẫn đúng |
| D-09 | S2 | **Tọa độ `NOT NULL`, không có cờ chất lượng GPS.** Máy chưa có GPS fix gửi `0,0` sẽ được ghim ở (0,0). | `victims.latitude/longitude`, payload | Ghim sai chỗ, gây quyết định điều phối sai |
| D-10 | S3 | **Simulator nằm trong giao diện chạy thật**, dữ liệu mô phỏng cùng bảng với dữ liệu thật, không có cờ "diễn tập". | `SimulatorApiController`, tab Config | Rủi ro lẫn dữ liệu demo với dữ liệu thật; nút xóa dữ liệu nằm ngay cạnh |
| D-11 | S3 | **`activeIncidents` là danh sách trong RAM** *(cần xác nhận)*: chỉ được thêm khi có SOS mới, tôi không thấy chỗ nạp lại từ DB khi khởi động. | `C2ControllerService` | Sau khi khởi động lại, một số logic dựa vào danh sách này có thể lệch với DB |
| D-12 | S3 | **`readLine()` không giới hạn, không giới hạn kết nối**, `mesh_packets` không có retention, insert trùng `packet_id` bị nuốt lỗi. | `MeshTcpServer`, `PacketDao` | Dễ bị làm nghẽn/tràn bộ nhớ; DB phình |
| D-13 | S3 | **Tab Audit là packet log**, không có "nhật ký thao tác" (actor, hành động, trước/sau). | `index.html` tab-audit | Khó trả lời câu hỏi "ai đã điều phối/đóng ca này" khi bảo vệ |
| D-14 | S3 | **Service Worker/PWA trên thiết bị khác truy cập bằng `http://IP-LAN`** *(cần xác nhận theo hành vi trình duyệt)*: service worker thường chỉ đăng ký được ở secure context (HTTPS hoặc localhost). | `sw.js` | PWA offline có thể chỉ hoạt động ngay trên máy trạm, không hoạt động trên tablet của chỉ huy |

### 1.4. Mức sẵn sàng ghép nối Mobile

- **Có thể ghép ngay (sau khi khớp checksum):** SOS đi lên, heartbeat, hiển thị trên bản đồ/triage/NOC.
- **Chưa thể ghép trọn vẹn:** điều phối đi xuống, xác nhận hai chiều, đồng bộ hồ sơ y tế, báo hủy SOS, bảo mật.
- **Rủi ro lớn nhất không nằm ở code Web đơn lẻ mà ở "khe hở hợp đồng":** hai bên thống nhất tên field nhưng chưa thống nhất **cách băm**, **enum giá trị**, **quy ước GPS thiếu fix**, **định danh node**, **ai gửi ACK khi nào**. Phụ lục A đóng khe hở này.

---

## 2. ĐỐI CHIẾU VỚI CÁC HỆ THỐNG CỨU NẠN THỰC TẾ

### 2.1. ATAK / WinTAK / TAK Server — chỉ huy dã chiến

- **Mô hình dữ liệu:** mọi thứ là một *sự kiện CoT (Cursor on Target)*: `uid` ổn định, `type`, `how`, `time` / `start` / **`stale`**, `point` kèm sai số (`ce`, `le`). `stale` làm cho thông tin cũ tự mờ/biến mất thay vì "đóng băng" trên bản đồ.
- **Điều rút ra cho bạn:** thêm `stale`/độ tuổi tin cho SOS và vị trí đội; thêm sai số vị trí (`accuracyM`); đặt `uid` ổn định cho node và sự cố.
- **Phân quyền & nhóm:** TAK có nhóm/vai trò và kênh chia sẻ. Bạn không cần cấu trúc phức tạp như vậy, nhưng nên có vai trò (mục 4).
- **Cơ hội thể hiện:** *xuất CoT/GeoJSON* là cách rẻ và thuyết phục để chứng minh khả năng tương thích với hệ sinh thái TAK.

### 2.2. FEMA — NIMS / ICS (Incident Command System)

- **Khái niệm áp dụng được:**
  - **Operational Period (ca/giai đoạn tác chiến)** và **bàn giao ca**.
  - **Nhật ký hoạt động** (kiểu ICS-214): mỗi thao tác có thời gian, người thực hiện, nội dung.
  - **Kế hoạch liên lạc** (kiểu ICS-205): kênh nào dùng cho ai.
  - **Incident Action Plan** (bạn đã có xuất IAP).
  - **Resource typing:** phân loại đội/phương tiện theo năng lực (xuồng cứu hộ, xe cứu thương, đội y tế...) để điều phối đúng nơi.
  - **Unified Command:** nhiều cơ quan cùng chỉ huy, bạn đã có phiếu liên ngành.
- **Điều rút ra:** thêm ca trực + bàn giao, audit trail có actor, phân loại tài nguyên, và báo cáo tình hình định kỳ (SITREP).

### 2.3. Hội Chữ Thập Đỏ quốc tế (ICRC/IFRC) và phân loại y tế

- **Restoring Family Links / Safe and Well:** cho người dân báo "tôi an toàn" và tra cứu người thân. Luồng này giảm tải cho đường dây cứu hộ vì phần lớn người dân chỉ cần báo bình an.
- **Triage START / JumpSTART:** Đỏ (cấp cứu ngay) – Vàng (trì hoãn) – Xanh (nhẹ) – Đen (tử vong/không cứu được). Mã màu gắn với hành động.
- **Điều rút ra:** thêm loại gói `SAFE_CHECKIN`; ánh xạ `CRITICAL/HIGH/MEDIUM` sang nhãn START rõ ràng trong giao diện Triage; luôn kèm icon/chữ ngoài màu để người mù màu đọc được.

### 2.4. Serval Project và Disaster.radio — mạng ngoại tuyến

- **Delay-Tolerant Networking:** không giả định có đường truyền liên tục; dữ liệu được **lưu-mang-chuyển tiếp**, có ID nội dung ổn định để chống trùng khi lan tỏa nhiều đường.
- **Disaster.radio:** mạng LoRa tầm xa, có "cổng" nối vào mạng IP.
- **Điều rút ra:** Base cần **hàng đợi gửi lại có ACK** (outbox), cache dedup đủ dài hoặc dedup theo ID ổn định lưu DB, và thiết kế "gateway" như một nguồn gói tin chung để sau này cắm LoRa/SMS mà không đổi lõi.

### 2.5. Ushahidi — bản đồ khủng hoảng

- **Quy trình xác minh:** báo cáo vào ở trạng thái *chưa xác minh* → *đã xác minh* → hoặc *báo giả*; có danh mục, nhiều kênh vào (web/SMS/email).
- **Gom báo cáo trùng lặp:** nhiều báo cáo cùng một sự kiện cần được gộp cụm.
- **Điều rút ra:** thêm trạng thái xác minh (đặc biệt với SOS ẩn danh hoặc từ nguồn lạ), gom cụm SOS gần nhau theo khoảng cách và thời gian.

### 2.6. Các chuẩn/tham chiếu bổ sung đáng nhắc trong báo cáo

- **CAP (Common Alerting Protocol, OASIS):** định dạng cảnh báo diện rộng, phù hợp cho tính năng *phát cảnh báo từ Base tới toàn mạng*.
- **EDXL (ví dụ HAVE, trao đổi tình trạng giường bệnh):** tương ứng với tính năng năng lực cơ sở y tế bạn đã làm.
- **Sahana Eden:** phần mềm mã nguồn mở quản lý thảm họa với các mô-đun đăng ký người mất tích, nơi trú ẩn, tổ chức, kho vật tư. Là hình mẫu tốt để chọn mô-đun nào nên có.

### 2.7. Bảng đối chiếu tổng hợp

| Khái niệm thực tế | Hệ thống tham chiếu | Web của bạn | Khoảng cách |
|---|---|---|---|
| Sự kiện có `uid`, `stale`, sai số vị trí | ATAK/CoT | Có `nodeId`, không có `stale`/sai số | Thiếu |
| Ca trực & bàn giao | FEMA ICS | Không | Thiếu |
| Nhật ký hoạt động có người thực hiện | FEMA ICS-214 | Packet log, không có actor | Thiếu |
| Kế hoạch liên lạc | ICS-205 | Có nhật ký bộ đàm | Một phần |
| Phân loại tài nguyên theo năng lực | ICS resource typing | Có `rescue_teams` | Một phần |
| Triage START | Y tế thảm họa | 3 mức severity | Cần ánh xạ |
| Báo an toàn / tìm người thân | Chữ Thập Đỏ RFL | Không | Thiếu |
| Lưu-mang-chuyển tiếp, ACK | Serval | Chống trùng 60s, không outbox | Thiếu |
| Cổng LoRa/SMS | Disaster.radio | Không | Để sau |
| Xác minh và gom cụm báo cáo | Ushahidi | Không | Thiếu |
| Phát cảnh báo diện rộng | CAP | Không | Thiếu |
| Trao đổi dữ liệu mở | CoT/GeoJSON/CAP | Chỉ IAP | Thiếu |

---

## 3. NHỮNG GÌ CÒN THIẾU VÀ ĐỀ XUẤT CẢI TIẾN

Ký hiệu mức công: **S** ≤ 1 ngày · **M** 2–3 ngày · **L** 4–7 ngày (ước lượng thô cho lập trình viên làm một mình, chưa tính thời gian học).

### 3.1. Nhóm A — Bắt buộc trước khi ghép Mobile (P0)

| # | Đề xuất | Giải quyết | Công |
|---|---|---|---|
| A1 | **Hợp đồng gói tin v1:** JSON Schema, `schemaVersion`, bảng enum, quy ước GPS (`hasFix`, `accuracyM`), `seq` theo nguồn. Xem Phụ lục A. | D-01, D-08, D-09 | M |
| A2 | **Checksum chuẩn hóa (canonical) + test vector chung.** Băm một chuỗi xác định, không băm lại JSON. Có file vector để cả Java và Kotlin cùng chạy unit test. | D-01 | M |
| A3 | **Sửa thứ tự dedup/checksum**, kéo dài và lưu bền vững bộ nhớ chống trùng (dedup theo `packetId` lưu DB, hoặc cache dài hơn). | D-05 | S |
| A4 | **ACK hai chiều:** Base gửi `ACK(RECEIVED)` cho mọi SOS; nhận ACK từ điện thoại cập nhật `dispatch_logs` và máy trạng thái lệnh (Phụ lục B). | D-03 | M |
| A5 | **Sổ đăng ký node + hộp thư lệnh (inbox/outbox):** Base ghi nhớ IP/cổng/lần cuối thấy của từng `nodeId` từ kết nối đến; lệnh xuống được xếp hàng và **giao khi thiết bị gọi lên** (`GET /api/mesh/inbox`) hoặc đẩy qua kết nối đang mở; gửi lại có backoff đến khi có ACK. | D-02 | L |
| A6 | **Sửa mô hình sự cố:** tách `incidents` (mỗi SOS là một sự cố, có timeline và quỹ đạo vị trí) khỏi thiết bị/người. SOS mới của node đã RESOLVED tạo sự cố mới. Cập nhật đủ hồ sơ y tế. Phụ lục C. | D-04, D-11 | L |
| A7 | **Bảo mật tối thiểu:** đăng nhập, vai trò, bảo vệ endpoint nguy hiểm, bỏ `*` ở origin, tắt simulator ở chế độ thật. Chi tiết mục 4. | D-06, D-10 | L |
| A8 | **Mobile Emulator** (điện thoại giả dạng CLI) để kiểm thử mọi thứ trên mà không cần Android. Phụ lục E. | Toàn bộ | M |

### 3.2. Nhóm B — Nên có để hệ thống đáng tin (P1)

| # | Đề xuất | Lý do | Công |
|---|---|---|---|
| B1 | **HMAC-SHA256 với khóa theo thiết bị** thay cho SHA-256 trần; bao các field bất biến (`packetId`, `type`, `source`, `destination`, `timestamp`, `seq`, payload canonical), **không** bao `ttl`, `hopCount`, `routeHistory`, `senderHopId` (relay sửa các field này). | Chống giả mạo thật sự | M |
| B2 | **Chống phát lại:** cửa sổ `seq` theo nguồn; không dựa vào đồng hồ điện thoại (có thể lệch). | Chống replay | S |
| B3 | **Tile bản đồ offline** cho khu vực triển khai (thư mục tile hoặc MBTiles) + fallback hiển thị khi mất tile. | D-07 | M |
| B4 | **Loại gói mới:** `STATUS_UPDATE`, `CANCEL_SOS` (báo giả/đã an toàn), `SAFE_CHECKIN`, `BROADCAST_ALERT` (Base → toàn mạng), `TEAM_POSITION`. | Nghiệp vụ thực tế | M |
| B5 | **Gợi ý đội phù hợp:** đội rảnh gần nhất theo khoảng cách (haversine) + năng lực + ETA ước tính. | Điều phối thông minh, dễ trình bày | M |
| B6 | **Gom cụm SOS** theo khoảng cách/thời gian; hiển thị một sự cố lớn thay vì nhiều ghim chồng nhau. | Giảm quá tải thông tin | M |
| B7 | **Trạng thái xác minh** (chưa xác minh / đã xác minh / báo giả) kiểu Ushahidi. | Giảm điều phối nhầm | S |
| B8 | **Độ tươi tin (`stale`)**: SOS/đội/relay không cập nhật quá ngưỡng thì mờ dần và gắn nhãn "mất tín hiệu từ HH:MM". | An toàn vận hành | S |
| B9 | **Cảnh báo leo thang:** SOS CRITICAL chưa được xác nhận sau X phút thì leo thang (còi to hơn, thông báo chỉ huy). Còi phải có nút "đã tiếp nhận" ghi lại người nhận. | Chống bỏ sót | M |
| B10 | **Giới hạn tải TCP:** độ dài dòng tối đa, số kết nối tối đa, timeout, rate limit theo nguồn. | D-12 | S |
| B11 | **Retention & sao lưu:** xoay vòng/nén `mesh_packets`, không lưu heartbeat dạng bản ghi đầy đủ, nút sao lưu/khôi phục DB. | D-12 | M |

### 3.3. Nhóm C — Tương thích và báo cáo (P2)

| # | Đề xuất | Công |
|---|---|---|
| C1 | Xuất **GeoJSON** (bản đồ), **CoT XML** (tương thích TAK), **CSV** (phân tích), báo cáo **SITREP** định kỳ. | M |
| C2 | Báo cáo **After-Action Report**: thời gian từ SOS đến ACK, đến điều phối, đến hoàn tất; tỉ lệ giao thành công; phân bố số bước nhảy; tỉ lệ drop theo nguyên nhân. Đây là số liệu có giá trị cao trong báo cáo đồ án. | M |
| C3 | **Vùng nguy hiểm** (vẽ đa giác ngập lụt/sạt lở) và tự gắn cờ SOS nằm trong vùng. | M |
| C4 | Xuất/nhập **kế hoạch liên lạc** (kiểu ICS-205) từ danh bạ liên ngành hiện có. | S |
| C5 | Thiết kế sẵn **gateway** (LoRa/SMS) như một nguồn gói tin; chưa cần cài đặt. | S |

### 3.4. Những thứ **không nên** làm (kiểm soát phạm vi)

- Đăng nhập bằng OAuth/SSO, đặt lại mật khẩu qua email, đa tenant: hệ thống chạy trên LAN không Internet, không có email, một trạm một tổ chức.
- Microservices hay chuyển CSDL sang hệ nặng: SQLite WAL đủ cho quy mô đồ án; vấn đề thật nằm ở mô hình dữ liệu, không phải engine.
- Đồng bộ nhiều trạm: để vào phần "hướng phát triển" của báo cáo.

---

## 4. GIAO DIỆN, TÀI KHOẢN, PHÂN QUYỀN (PHÂN TÍCH CHUYÊN SÂU)

### 4.1. Có cần quản lý tài khoản và phân quyền không?

**Có, nhưng ở mức "vừa đủ cho vận hành thực tế", không phải hệ thống định danh doanh nghiệp.** Lý do cụ thể từ code của bạn:

1. **Có endpoint phá hủy dữ liệu không bảo vệ** (`/api/simulator/clear-data`) và endpoint điều phối mở. Chỉ cần một người lạ trong hotspot hoặc một lần bấm nhầm là mất sạch dữ liệu thật.
2. **Dữ liệu cá nhân nhạy cảm:** họ tên, số điện thoại, nhóm máu, ghi chú y tế, vị trí theo thời gian thực. Không phải ai xem dashboard cũng cần thấy đủ. Việt Nam có quy định bảo vệ dữ liệu cá nhân (Nghị định 13/2023/NĐ-CP; hãy kiểm tra văn bản luật mới hơn nếu có trước khi trích dẫn). Nêu được nguyên tắc *tối thiểu hóa dữ liệu theo vai trò* sẽ là điểm cộng khi bảo vệ.
3. **Audit cần "ai":** hiện lệnh điều phối luôn mang tên `"Trạm trưởng C2"`. Trong ICS, mọi quyết định đều phải truy vết được đến người ra quyết định.
4. **Nhiều người dùng cùng lúc** (chỉ huy, điều phối viên, liên lạc liên ngành) trên các thiết bị khác nhau là kịch bản bình thường của một trạm dã chiến.

### 4.2. Mô hình vai trò đề xuất

Bốn vai trò cốt lõi cho đồ án, thêm hai vai trò tùy chọn:

| Vai trò | Ai | Quyền tóm tắt |
|---|---|---|
| **ADMIN** (Quản trị trạm) | Trạm trưởng/IT | Quản lý tài khoản, thiết bị, cấu hình, sao lưu, xóa dữ liệu (có xác nhận 2 bước), chế độ diễn tập |
| **COMMANDER** (Chỉ huy) | Chỉ huy hiện trường | Toàn quyền nghiệp vụ: điều phối, đóng sự cố, phát cảnh báo, duyệt báo cáo; không quản lý tài khoản |
| **DISPATCHER** (Điều phối viên) | Trực ca | Tiếp nhận SOS, xác minh, gán đội, gửi lệnh, ghi nhật ký; không phát cảnh báo diện rộng, không đóng sự cố nghiêm trọng nếu cấu hình yêu cầu |
| **OBSERVER** (Quan sát) | Khách, giám khảo, cơ quan phối hợp | Chỉ đọc; **che** số điện thoại, ghi chú y tế, tên đầy đủ |
| *LIAISON* (tùy chọn) | Đại diện bệnh viện/công an/PCCC | Chỉ thấy và cập nhật phiếu liên ngành của đơn vị mình, cập nhật năng lực cơ sở |
| *TEAM_LEAD* (tùy chọn) | Đội trưởng đội cứu hộ | Cập nhật trạng thái/vị trí đội mình qua giao diện đơn giản trên điện thoại/tablet |

Ma trận chi tiết ở Phụ lục D.

### 4.3. Màn hình và luồng tài khoản cần có

1. **Màn hình đăng nhập:** tên đăng nhập + mật khẩu (hoặc PIN dài cho tablet), nút lớn, chạy tốt ngoài nắng; khóa tạm sau N lần sai; hiển thị tên trạm và đồng hồ giờ trạm.
2. **Thiết lập lần đầu (first-run wizard):** tạo tài khoản ADMIN đầu tiên; **không** dùng mật khẩu mặc định cố định.
3. **Quản lý người dùng (ADMIN):** tạo/khóa/mở khóa, gán vai trò, gán đội/đơn vị, **đặt lại mật khẩu thủ công** (không có email), buộc đăng xuất, xem lần đăng nhập cuối.
4. **Hồ sơ cá nhân:** đổi mật khẩu, ca trực hiện tại, nhận diện thiết bị đang đăng nhập.
5. **Bảo vệ thao tác nguy hiểm:** xóa dữ liệu, đổi cổng, đổi khóa thiết bị phải qua xác nhận gõ lại và ghi audit.

### 4.4. Thiết kế kỹ thuật gợi ý (phù hợp mô hình LAN ngoại tuyến)

- **Spring Security** + mật khẩu băm **BCrypt**; tài khoản lưu SQLite (bảng `users`).
- **Phiên bằng cookie** (`HttpOnly`, `SameSite=Strict`) cho dashboard; yêu cầu token CSRF hoặc header tùy chỉnh cho request ghi. JWT không cần thiết khi chỉ có một máy chủ.
- **STOMP:** xác thực ở bước handshake; dùng `ChannelInterceptor` kiểm tra quyền **đăng ký từng topic** (ví dụ OBSERVER không được nghe `/topic/dispatch-ack`).
- **Che dữ liệu ở phía server**, không chỉ ẩn trên giao diện: serializer theo vai trò trả `phoneNumber = "09******67"`, bỏ `medicalNotes` cho OBSERVER.
- **Giới hạn đăng nhập sai** (rate limit/khóa tạm) và bỏ `allowedOriginPatterns("*")`.
- **HTTPS trên LAN** *(cần xác nhận)*: để PWA/service worker hoạt động trên tablet truy cập bằng IP, nên dùng chứng chỉ tự ký/được tin cậy cục bộ (ví dụ cấp bằng công cụ như mkcert) hoặc truy cập qua tên miền cục bộ; nếu không, offline PWA có thể chỉ chạy trên chính máy trạm.
- **Danh tính thiết bị mesh tách khỏi tài khoản người dùng:** điện thoại xác thực bằng **khóa thiết bị** (HMAC, mục B1), không dùng tài khoản dashboard.

### 4.5. Quản lý thiết bị (Device Registry): màn hình quan trọng bị bỏ sót

Mesh có "danh tính" riêng là các node. Nên có màn hình:

- Danh sách thiết bị: `nodeId`, tên hiển thị, loại (nạn nhân/relay/đội cứu hộ), lần cuối thấy, IP gần nhất, phiên bản app, trạng thái (Hoạt động / Mất tín hiệu / Bị thu hồi).
- **Ghép đôi bằng mã QR:** QR chứa địa chỉ trạm, cổng, khóa thiết bị; điện thoại quét một lần là cấu hình xong. (Dự án đã đóng gói sẵn `qrcode.min.js` trong `vendor/`, có thể tận dụng.)
- **Thu hồi thiết bị** (mất/đánh cắp): gói từ thiết bị bị thu hồi bị từ chối và gắn cờ.
- **Xoay khóa** khi cần.

### 4.6. Các màn hình/chức năng giao diện nên bổ sung

| # | Màn hình | Mô tả và giá trị | Ưu tiên |
|---|---|---|---|
| U1 | **Đăng nhập + Quản lý người dùng** | Mục 4.3 | P0 |
| U2 | **Chi tiết sự cố (hồ sơ vụ việc)** | Ngăn kéo bên phải khi bấm một ghim/dòng: hồ sơ người gửi, bản đồ nhỏ + quỹ đạo vị trí, **timeline** (nhận SOS → ACK → xác minh → gán đội → tiếp cận → hoàn tất), đội được gán, nhật ký ghi chú, lịch sử gói tin liên quan. | P0 |
| U3 | **Bảng điều phối (Dispatch board)** | Dạng kanban theo trạng thái; kéo thả để gán đội; chip hiển thị *trạng thái giao lệnh* (đã gửi / đã tới điện thoại / đội đã nhận). | P0 |
| U4 | **Quản lý thiết bị** | Mục 4.5 | P0 |
| U5 | **Chế độ diễn tập (Drill/Training mode)** | Công tắc toàn cục; dữ liệu có cờ `is_drill`, dải banner đỏ "ĐANG DIỄN TẬP" trên mọi màn hình; tách simulator ra khỏi tab Config; xóa dữ liệu chỉ xóa phần diễn tập. Giải quyết D-10. | P0 |
| U6 | **Nhật ký thao tác (Audit trail) + Nhật ký gói tin** | Đổi tên tab hiện tại thành "Nhật ký gói tin"; thêm "Nhật ký thao tác" có actor, hành động, trước/sau, lọc theo người/thời gian; bản ghi nối băm (mỗi dòng chứa hash của dòng trước) để phát hiện sửa/xóa. | P1 |
| U7 | **Quản lý đội và tài nguyên** | CRUD đội, năng lực/loại (xuồng, y tế, cứu hỏa...), nhân sự, phương tiện, trạng thái sẵn sàng, vị trí cuối. | P1 |
| U8 | **Ca trực và bàn giao** | Bắt đầu/kết thúc ca, người trực, tóm tắt tự động (sự cố mở, lệnh chờ, đội đang ngoài hiện trường, ghi chú quan trọng), người nhận ca xác nhận. | P1 |
| U9 | **Phát cảnh báo (Broadcast composer)** | Soạn thông điệp theo mẫu (CAP rút gọn): mức độ, khu vực, hướng dẫn, thời hạn; gửi xuống toàn mạng hoặc theo vùng; theo dõi tỉ lệ nhận (ACK). | P1 |
| U10 | **Trạng thái hệ thống (System health)** | Cổng TCP/WS, kích thước DB, độ sâu hàng đợi lệnh, số thiết bị online/mất tín hiệu, lệch đồng hồ, dung lượng ổ đĩa, lỗi gần nhất. Dùng để chẩn đoán nhanh khi đang diễn tập. | P1 |
| U11 | **Lớp bản đồ nâng cao** | Bật/tắt lớp (nạn nhân, đội, relay, cơ sở, vùng nguy hiểm), vẽ vùng, đo khoảng cách, heatmap mật độ SOS, tìm theo tên/ID, hiển thị sai số GPS và `stale`. | P1 |
| U12 | **Trung tâm báo cáo** | IAP (đã có), SITREP, After-Action Report, xuất CSV/GeoJSON/CoT. | P2 |
| U13 | **Cài đặt trạm và sao lưu** | Cổng, TTL mặc định, ngưỡng cảnh báo, retention, sao lưu/khôi phục, ngôn ngữ, giao diện sáng/tối. | P2 |
| U14 | **Báo an toàn / tìm người thân** | Danh sách `SAFE_CHECKIN`, tìm theo tên/số điện thoại (có quyền hạn chế). | P2 |

### 4.7. Nguyên tắc UX cho giao diện chỉ huy dã chiến

- **Đọc được ngoài nắng và qua màn hình nhỏ:** độ tương phản cao, cỡ chữ lớn cho số liệu quan trọng, nút đủ lớn để bấm bằng tay đeo găng/tablet.
- **Không chỉ dựa vào màu:** mỗi mức khẩn cấp có màu **và** icon **và** nhãn chữ (hỗ trợ mù màu, in đen trắng).
- **Chống "mỏi cảnh báo":** còi chỉ cho sự kiện cần hành động; có ngưỡng, gộp và nút "đã tiếp nhận".
- **Thao tác phá hủy phải khó bấm nhầm:** xác nhận hai bước, nằm xa nút thông dụng, ghi audit.
- **Phản hồi trạng thái liên tục:** thanh trạng thái luôn hiển thị kết nối WebSocket, số thiết bị online, giờ trạm, chế độ (Thật/Diễn tập).
- **Phím tắt và command palette** (đã có) nên mở rộng cho thao tác thường dùng của điều phối viên.
- **Đa ngôn ngữ Việt/Anh** cho giám khảo/đối tác quốc tế.
- **Tất cả thời gian theo múi giờ trạm, 24 giờ**, hiển thị rõ khi dữ liệu "cũ".

### 4.8. Điều chỉnh các tab hiện có

| Tab hiện tại | Vấn đề | Đề xuất |
|---|---|---|
| Config | Trộn simulator với thông số trạm, không có bảo vệ | Tách thành "Cài đặt trạm" (ADMIN) và "Diễn tập" (có banner, cờ `is_drill`) |
| Audit | Là packet log, không có actor | Đổi tên "Nhật ký gói tin"; thêm "Nhật ký thao tác" |
| Triage | Chỉ ba mức severity | Thêm nhãn START, icon, trạng thái xác minh |
| Map | Phụ thuộc tile online | Tile offline + lớp nâng cao + `stale` |
| Home | KPI tổng quan | Thêm: thời gian trung bình từ SOS đến ACK/điều phối, SOS chưa ACK, thiết bị mất tín hiệu |
| Liên ngành | Đã có phiếu | Gắn với vai trò LIAISON; gắn lệnh với sự cố (U2) |

---

## 5. LỘ TRÌNH THỰC HIỆN VÀ TIÊU CHÍ "WEB XONG, SẴN SÀNG GHÉP MOBILE"

**Nguyên tắc:** hoàn thành Web theo từng giai đoạn kiểm chứng được bằng **Mobile Emulator**, không chờ Android. Khi Web qua cổng "Definition of Done" (mục 5.2) mới bắt đầu ghép.

### 5.1. Các giai đoạn

| GĐ | Mục tiêu | Công việc chính | Sản phẩm kiểm chứng |
|---|---|---|---|
| **W0** | Nền tảng thống nhất | Chốt hợp đồng gói tin v1 + canonical checksum + test vector (A1, A2). Dựng **Mobile Emulator** (A8). Chạy test hiện có làm baseline. | File schema + vector dùng chung; emulator gửi/nhận được gói; baseline test xanh |
| **W1** | Vá nền dữ liệu | Sửa dedup/checksum (A3); mô hình `incidents` + upsert đúng + GPS fix (A6); giới hạn tải TCP (B10); cờ `is_drill` (U5 phần dữ liệu). | Test: SOS lặp sau RESOLVED mở sự cố mới; gói hỏng không đầu độc cache; (0,0) bị gắn cờ |
| **W2** | Khép kín điều phối | ACK hai chiều (A4); sổ đăng ký node + inbox/outbox + backoff (A5); máy trạng thái lệnh (Phụ lục B); giao diện U2, U3. | Emulator nhận lệnh qua inbox, gửi ACK; giao diện hiển thị đúng từng trạng thái; mất kết nối rồi nối lại vẫn giao đủ |
| **W3** | Tài khoản, phân quyền, truy vết | Spring Security, vai trò, che dữ liệu theo vai trò, STOMP interceptor (A7); U1, U4 (quản lý thiết bị + QR), U6 (audit trail nối băm); HMAC theo thiết bị (B1, B2). | Test phân quyền theo ma trận; thiết bị bị thu hồi bị từ chối; audit có actor |
| **W4** | Vận hành dã chiến | Tile offline (B3); `stale` (B8); cảnh báo leo thang (B9); U7, U8, U9, U10, U11; xác minh + gom cụm (B6, B7); loại gói mới (B4); retention/sao lưu (B11); HTTPS LAN. | Diễn tập mất mạng Internet; ca trực bàn giao trơn tru; dashboard còn dùng được |
| **W5** | Tương thích, báo cáo, hoàn thiện | Xuất GeoJSON/CoT/CSV (C1); SITREP + After-Action Report (C2); vùng nguy hiểm (C3); kiểm thử tải/chaos (Phụ lục E); viết tài liệu, kịch bản demo. | Báo cáo kèm số liệu đo thật; bộ test tự động qua |

### 5.2. Definition of Done: "Web sẵn sàng ghép Mobile"

Chỉ chuyển sang Mobile khi **tất cả** các mục sau đạt:

- [ ] Hợp đồng gói tin v1 và test vector đã được cả hai bên xác nhận bằng văn bản; unit test Web dùng đúng vector.
- [ ] Mobile Emulator thực hiện trọn vòng: gửi SOS → nhận ACK → nhận lệnh qua inbox → gửi ACK lệnh → báo hoàn tất; giao diện hiển thị đúng ở từng bước.
- [ ] Kịch bản chaos (mất gói 20–30%, trùng, đảo thứ tự, trễ, JSON hỏng, dòng quá dài, nhiều kết nối) không làm sập hay sai dữ liệu.
- [ ] SOS lặp lại của người đã RESOLVED mở sự cố mới; hồ sơ y tế cập nhật đúng.
- [ ] Không còn endpoint nguy hiểm mở; ma trận phân quyền được kiểm thử; mọi thao tác ghi đều có actor trong audit trail.
- [ ] Dashboard dùng được khi không có Internet (bản đồ tile offline).
- [ ] Chế độ diễn tập tách biệt dữ liệu thật; có banner rõ ràng.
- [ ] Khởi động lại máy chủ giữa chừng không mất sự cố, lệnh đang chờ hay trạng thái ACK.
- [ ] Tài liệu hợp đồng và hướng dẫn ghép nối (cổng, QR pairing, khóa thiết bị) đã viết xong.

### 5.3. Thứ tự ưu tiên rút gọn (nếu thời gian gấp)

1. A1 + A2 (hợp đồng + checksum) → 2. A8 (emulator) → 3. A6 + A3 (sự cố + dedup) → 4. A4 + A5 (ACK + inbox/outbox) → 5. A7 (đăng nhập + vai trò + bảo vệ endpoint) + U5 (drill mode) + U2/U3 (chi tiết sự cố, bảng điều phối) → 6. B3 (tile offline) → 7. B1 (HMAC) → 8. U6, U8, U9 → 9. C1, C2.

Nếu phải cắt, hãy ghi các mục chưa làm vào phần **"Hạn chế đã biết và hướng phát triển"** trong báo cáo. Nêu rõ giới hạn một cách có hệ thống thường được đánh giá cao hơn là giấu nó.

---

## PHỤ LỤC A — HỢP ĐỒNG GÓI TIN v1 (BẢN NHÁP) VÀ TEST VECTOR

> Đây là **đề xuất** để hai bạn thảo luận, không phải chuẩn đã chốt. Khi áp dụng cần giai đoạn chuyển tiếp: Base chấp nhận cả checksum cũ và mới theo `schemaVersion`.

### A.1. Cấu trúc gói

```json
{
  "schemaVersion": 1,
  "packetId": "11111111-2222-3333-4444-555555555555",
  "packetType": "SOS_DATA",
  "sourceNodeId": "NODE_A_VICTIM",
  "destinationNodeId": "BASE_STATION",
  "senderHopId": "NODE_A_VICTIM",
  "seq": 7,
  "ttl": 5,
  "hopCount": 0,
  "timestamp": 1760000000000,
  "routeHistory": [],
  "payload": {
    "senderName": "Nguyễn Văn A",
    "alertType": "FLOOD_TRAPPED",
    "severity": "CRITICAL",
    "victimCount": 3,
    "location": { "latitude": 16.067, "longitude": 108.152, "hasFix": true, "accuracyM": 12.5 },
    "phoneNumber": "0901234567",
    "bloodType": "O+",
    "medicalNotes": null,
    "message": "Nước dâng cao, mắc kẹt tầng 2",
    "batteryPct": 41
  },
  "checksum": "<hmac hoặc sha256 hex, chữ thường>"
}
```

### A.2. Enum đề xuất (hai bên dùng đúng chuỗi này)

- `packetType`: `SOS_DATA`, `DISPATCH_CMD`, `ACK`, `HEARTBEAT`, `STATUS_UPDATE`, `CANCEL_SOS`, `SAFE_CHECKIN`, `BROADCAST_ALERT`, `TEAM_POSITION`
- `severity`: `CRITICAL`, `HIGH`, `MEDIUM` (kèm bảng ánh xạ sang nhãn START trong giao diện)
- `alertType`: `MEDICAL`, `FLOOD_TRAPPED`, `LANDSLIDE`, … (mở rộng bằng cách thêm vào bảng, không đổi tên; giá trị lạ phải được xử lý an toàn, không làm hỏng gói)
- `ACK.payload.ackType`: `RECEIVED`, `ACCEPTED`, `REJECTED`, `COMPLETED` (kèm `ackedPacketId`)

### A.3. Quy tắc chuỗi ký (canonical) — đề xuất

1. **Chuỗi header** = các giá trị nối bằng `|` theo đúng thứ tự: `packetId | packetType | sourceNodeId | destinationNodeId | timestamp | seq`.
2. **Chuỗi payload** (với `SOS_DATA`) = các giá trị nối bằng `|` theo thứ tự cố định: `senderName | alertType | severity | victimCount | latitude | longitude | phoneNumber | bloodType | medicalNotes | message`.
   - Số thực `latitude`/`longitude` luôn in **đúng 6 chữ số thập phân**, dấu chấm, không dùng ký hiệu khoa học.
   - Số nguyên in thường. `null` hoặc thiếu = chuỗi rỗng.
   - Trong mọi chuỗi văn bản: thay `\` bằng `\\` và `|` bằng `\|` trước khi nối.
   - Văn bản UTF-8, **không** chuẩn hóa/biến đổi thêm.
3. **Chuỗi ký** = `header` + `"\n"` + `payload`.
4. **Checksum** = SHA-256 (giai đoạn đầu) hoặc **HMAC-SHA256 với khóa thiết bị** (mục B1) của chuỗi ký, in **hex chữ thường**.
5. **Không** đưa `ttl`, `hopCount`, `routeHistory`, `senderHopId` vào chuỗi ký vì relay phải sửa các field này.
6. Mỗi `packetType` khác có thứ tự field payload riêng, ghi trong tài liệu hợp đồng.

### A.4. Test vector #1 (đã tính bằng Python để hai bên đối chiếu)

Dữ liệu vào: gói ở A.1 với `medicalNotes = null`, `latitude = 16.067`, `longitude = 108.152`.

Chuỗi ký (hiển thị đúng từng ký tự; dòng 1 là header, dòng 2 là payload):

```
11111111-2222-3333-4444-555555555555|SOS_DATA|NODE_A_VICTIM|BASE_STATION|1760000000000|7
Nguyễn Văn A|FLOOD_TRAPPED|CRITICAL|3|16.067000|108.152000|0901234567|O+||Nước dâng cao, mắc kẹt tầng 2
```

Kết quả mong đợi:

```
SHA-256 : 31bb80761294a39a2bda3589350e8d8da58fee5e46d66a203713694af1d7ad82
HMAC-SHA256 (khóa "demo-station-key-change-me") :
          ed2ceeb132c9f121ca56517f7a0454c5a189925261a7c061ec8f37fabe15e11a
```

> Lưu ý: không có ký tự xuống dòng ở cuối chuỗi ký. Khi hai bên cho ra kết quả khác nhau, so sánh **từng byte** của chuỗi ký trước khi nghi ngờ thuật toán băm. Nên bổ sung thêm vector có dấu `|`, dấu `\`, dấu nháy, ký tự Unicode tổ hợp và `medicalNotes` khác null.

---

## PHỤ LỤC B — MÁY TRẠNG THÁI

### B.1. Sự cố (SOS)

```
RECEIVED ─► VERIFIED ─► ASSIGNED ─► EN_ROUTE ─► ON_SCENE ─► RESOLVED
    │            │
    │            └─► FALSE_ALARM
    └─► CANCELLED_BY_VICTIM   (gói CANCEL_SOS)

Mọi trạng thái (trừ RESOLVED/FALSE_ALARM/CANCELLED) có thể → ESCALATED (quá hạn chưa ACK/chưa xử lý)
```

Quy tắc: SOS mới từ node có sự cố đang mở thì **cập nhật** sự cố đó (thêm điểm vào quỹ đạo vị trí); từ node chỉ có sự cố đã đóng thì **mở sự cố mới**.

### B.2. Lệnh điều phối

```
CREATED ─► QUEUED ─► SENT ─► DELIVERED (ACK RECEIVED) ─► ACCEPTED ─► COMPLETED
                │        │
                │        └─► (hết hạn/retry quá giới hạn) ─► FAILED ─► giao lại / đổi kênh
                └─► CANCELLED (do người dùng thu hồi)
```

Quy tắc: trạng thái `DISPATCHED` của sự cố chỉ chuyển khi lệnh ít nhất ở `DELIVERED`; nếu chưa tới nơi, giao diện hiển thị "đã lập lệnh, chưa giao được" thay vì "đã điều phối".

---

## PHỤ LỤC C — PHÁC THẢO SCHEMA CSDL MỚI (SQLite)

```sql
-- Tài khoản người dùng dashboard
CREATE TABLE users (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  username TEXT UNIQUE NOT NULL,
  display_name TEXT NOT NULL,
  password_hash TEXT NOT NULL,           -- BCrypt
  role TEXT NOT NULL,                    -- ADMIN|COMMANDER|DISPATCHER|OBSERVER|LIAISON|TEAM_LEAD
  agency_id TEXT,                        -- cho LIAISON/TEAM_LEAD
  active INTEGER NOT NULL DEFAULT 1,
  failed_logins INTEGER NOT NULL DEFAULT 0,
  locked_until INTEGER,
  last_login_at INTEGER,
  created_at INTEGER NOT NULL
);

-- Thiết bị mesh
CREATE TABLE devices (
  node_id TEXT PRIMARY KEY,
  display_name TEXT,
  device_type TEXT NOT NULL,             -- VICTIM|RELAY|TEAM
  secret_key_enc TEXT NOT NULL,          -- khóa HMAC (lưu mã hóa/che)
  status TEXT NOT NULL DEFAULT 'ACTIVE', -- ACTIVE|REVOKED
  last_seen_at INTEGER,
  last_ip TEXT, last_port INTEGER,
  app_version TEXT,
  last_seq INTEGER NOT NULL DEFAULT 0    -- chống replay
);

-- Sự cố (mỗi SOS một sự cố)
CREATE TABLE incidents (
  id TEXT PRIMARY KEY,                   -- UUID
  node_id TEXT NOT NULL,
  full_name TEXT, phone_number TEXT, blood_type TEXT, medical_notes TEXT,
  victim_count INTEGER, alert_type TEXT, severity TEXT,
  status TEXT NOT NULL,                  -- theo máy trạng thái B.1
  verified_state TEXT NOT NULL DEFAULT 'UNVERIFIED',
  assigned_team_id TEXT,
  is_drill INTEGER NOT NULL DEFAULT 0,
  opened_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, closed_at INTEGER
);
CREATE INDEX idx_incidents_node_status ON incidents(node_id, status);

-- Dòng thời gian & quỹ đạo vị trí của sự cố
CREATE TABLE incident_events (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  incident_id TEXT NOT NULL,
  event_type TEXT NOT NULL,              -- SOS_RECEIVED|ACK_SENT|VERIFIED|ASSIGNED|LOCATION|NOTE|...
  actor_user_id INTEGER,                 -- null nếu hệ thống/thiết bị
  lat REAL, lng REAL, accuracy_m REAL, has_fix INTEGER,
  detail TEXT,
  created_at INTEGER NOT NULL
);

-- Lệnh điều phối (outbox)
CREATE TABLE dispatch_orders (
  id TEXT PRIMARY KEY,
  incident_id TEXT NOT NULL,
  target_node_id TEXT NOT NULL,
  command_message TEXT NOT NULL,
  severity TEXT,
  state TEXT NOT NULL,                   -- theo máy trạng thái B.2
  attempts INTEGER NOT NULL DEFAULT 0,
  next_attempt_at INTEGER,
  created_by INTEGER NOT NULL,
  created_at INTEGER NOT NULL,
  delivered_at INTEGER, accepted_at INTEGER, completed_at INTEGER
);

-- Nhật ký thao tác nối băm (tamper-evident)
CREATE TABLE audit_trail (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  actor_user_id INTEGER,
  action TEXT NOT NULL,
  target_type TEXT, target_id TEXT,
  before_json TEXT, after_json TEXT,
  prev_hash TEXT,
  row_hash TEXT NOT NULL,                -- SHA-256(prev_hash + nội dung dòng)
  created_at INTEGER NOT NULL
);
```

Ghi chú di cư: giữ bảng `victims` cũ ở chế độ chỉ đọc hoặc chuyển dữ liệu sang `incidents`; không đổi tên tùy tiện vì các controller/DAO/JS hiện tại đang dùng.

---

## PHỤ LỤC D — MA TRẬN PHÂN QUYỀN

Ký hiệu: ✔ có · ✖ không · ◐ có điều kiện/che dữ liệu

| Quyền | ADMIN | COMMANDER | DISPATCHER | OBSERVER | LIAISON | TEAM_LEAD |
|---|---|---|---|---|---|---|
| Xem bản đồ, KPI, topology | ✔ | ✔ | ✔ | ✔ | ✔ | ◐ (khu vực đội) |
| Xem dữ liệu cá nhân đầy đủ (SĐT, y tế) | ✔ | ✔ | ✔ | ✖ (che) | ◐ (ca của đơn vị) | ◐ (ca được gán) |
| Xác minh / báo giả SOS | ✖ | ✔ | ✔ | ✖ | ✖ | ✖ |
| Gán đội và gửi lệnh điều phối | ✖ | ✔ | ✔ | ✖ | ✖ | ✖ |
| Đóng sự cố (RESOLVED) | ✖ | ✔ | ◐ (theo cấu hình) | ✖ | ✖ | ◐ (đề xuất đóng) |
| Phát cảnh báo diện rộng | ✖ | ✔ | ✖ | ✖ | ✖ | ✖ |
| Cập nhật phiếu/năng lực cơ sở | ✖ | ✔ | ✔ | ✖ | ◐ (đơn vị mình) | ✖ |
| Cập nhật trạng thái/vị trí đội | ✖ | ✔ | ✔ | ✖ | ✖ | ◐ (đội mình) |
| Quản lý đội/tài nguyên | ✔ | ✔ | ◐ | ✖ | ✖ | ✖ |
| Quản lý thiết bị/khóa/QR | ✔ | ✖ | ✖ | ✖ | ✖ | ✖ |
| Quản lý tài khoản | ✔ | ✖ | ✖ | ✖ | ✖ | ✖ |
| Xem audit trail | ✔ | ✔ | ◐ (của mình) | ✖ | ✖ | ✖ |
| Bật/tắt chế độ diễn tập, bơm dữ liệu giả | ✔ | ✔ | ✖ | ✖ | ✖ | ✖ |
| Xóa dữ liệu / sao lưu / khôi phục | ✔ (xác nhận 2 bước) | ✖ | ✖ | ✖ | ✖ | ✖ |
| Xuất báo cáo (IAP, SITREP, AAR) | ✔ | ✔ | ✔ | ◐ (bản đã che) | ✖ | ✖ |

Nguyên tắc: **mặc định từ chối**, quyền được cấp theo hành động chứ không theo "trang"; kiểm tra ở **server** cho cả REST lẫn đăng ký topic STOMP.

---

## PHỤ LỤC E — MOBILE EMULATOR VÀ CHECKLIST KIỂM THỬ

### E.1. Mobile Emulator (điện thoại giả) nên làm được gì

- Chạy dạng CLI (một lớp Java `main` hoặc script), cấu hình bằng tham số: địa chỉ trạm, `nodeId`, khóa thiết bị, số lượng "điện thoại" ảo.
- Gửi: `SOS_DATA`, `HEARTBEAT`, `STATUS_UPDATE`, `CANCEL_SOS`, `SAFE_CHECKIN`, `TEAM_POSITION` đúng hợp đồng v1 (dùng đúng test vector).
- Nhận: gọi hộp thư lệnh định kỳ, tự gửi `ACK(RECEIVED)` rồi `ACK(ACCEPTED)`/`COMPLETED` sau độ trễ cấu hình.
- Mô phỏng **mạng xấu**: mất gói x%, trùng, đảo thứ tự, trễ ngẫu nhiên, ngắt kết nối giữa chừng, đồng hồ lệch, JSON hỏng, dòng quá dài, nhiều kết nối đồng thời.
- Mô phỏng **mesh nhiều bước nhảy**: chuỗi relay ảo tăng `hopCount`, thêm `routeHistory`, trừ TTL (đúng quy ước, không bao các field này trong chữ ký).
- In báo cáo cuối phiên: số gói gửi/nhận, thời gian trung bình đến ACK, số lần gửi lại.

Lợi ích: kiểm thử tự động được, tái lập lỗi, và cho phép bạn hoàn thành Web một mình trước khi Mobile sẵn sàng.

### E.2. Checklist kiểm thử

**Hợp đồng**
- [ ] Vector #1 và các vector biên (ký tự `|`, `\`, Unicode, `medicalNotes` có giá trị) cho cùng kết quả ở Java và ở Emulator.
- [ ] Gói thiếu field, sai kiểu, enum lạ: bị từ chối/ghi nhận an toàn, không làm sập luồng xử lý.

**Giao thức và mạng**
- [ ] SOS qua 0, 1, 3 bước nhảy đến nơi; `routeHistory` và `hopCount` đúng.
- [ ] Gói hỏng cùng `packetId` không chặn gói đúng gửi lại.
- [ ] Gói trùng trong và sau cửa sổ dedup được xử lý đúng (không tạo sự cố trùng).
- [ ] `seq` lùi/lặp bị từ chối khi bật chống replay.
- [ ] Dòng JSON cực dài, kết nối ồ ạt: bị giới hạn, máy chủ vẫn phục vụ.

**Nghiệp vụ**
- [ ] SOS → ACK trong thời gian mục tiêu; hiển thị "đã tiếp nhận" phía emulator.
- [ ] Lệnh điều phối: giao qua inbox, ACK, chuyển trạng thái đúng; thiết bị offline rồi online vẫn nhận đủ.
- [ ] SOS sau RESOLVED mở sự cố mới; `CANCEL_SOS` đóng đúng sự cố.
- [ ] Tọa độ `hasFix=false` không ghim vào (0,0).
- [ ] Gom cụm và xác minh hoạt động; `stale` hiển thị đúng.

**Bảo mật**
- [ ] Không đăng nhập thì không gọi được API ghi, không đăng ký topic.
- [ ] Mỗi vai trò chỉ làm được đúng ma trận ở Phụ lục D; dữ liệu cá nhân bị che đúng với OBSERVER ở cả REST lẫn WebSocket.
- [ ] Thiết bị bị thu hồi/sai khóa bị từ chối và ghi audit.
- [ ] `clear-data` yêu cầu ADMIN + xác nhận; chỉ xóa phần diễn tập khi ở chế độ diễn tập.

**Vận hành**
- [ ] Khởi động lại giữa chừng: sự cố, lệnh chờ, trạng thái ACK còn nguyên.
- [ ] Mất Internet: bản đồ tile offline và dashboard vẫn dùng được.
- [ ] Sao lưu/khôi phục DB thành công; retention không làm mất dữ liệu cần thiết.
- [ ] Tải thử (ví dụ 50 điện thoại ảo gửi heartbeat + vài SOS) không làm trễ giao diện đáng kể.

---

*Tài liệu này là bản phân tích và đề xuất; thứ tự và phạm vi nên được điều chỉnh theo thời gian còn lại của đồ án và yêu cầu của giảng viên. Sau khi hợp đồng gói tin (Phụ lục A) được chốt cùng bạn đồng nhóm, nên đưa nó vào thư mục `docs/` như một tài liệu chung của cả hai mảng.*

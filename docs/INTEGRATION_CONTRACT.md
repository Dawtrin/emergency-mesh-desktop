# INTEGRATION CONTRACT — Web C2 ↔ Android Mesh (v1, BẢN NHÁP ĐỂ HAI BÊN DUYỆT)

- **Chủ sở hữu chung:** Trần Viết Đạt (Web/Backend) và Nguyễn Thị Bích Trâm (Android)
- **Vai trò của file này:** nguồn sự thật **duy nhất** về cách hai bên giao tiếp. Hai file đánh giá (`DANH_GIA_WEB_C2_VA_DE_XUAT.md`, `DANH_GIA_ANDROID_MOBILE_VA_DE_XUAT.md`) chỉ *tham chiếu* file này, không định nghĩa lại.
- **Trạng thái:** đề xuất. Mục nào chưa được cả hai đồng ý ghi ở "Sổ quyết định" (mục 10). Mã hiện có của cả hai bên được gọi là **v0 (legacy)**.
- **Nguồn dữ kiện:** đọc mã Web (`MeshPacket.java`, `RoutingEngine`, `MeshTcpServer`...) và mã Android (`MeshPacket.kt`, `RoutingEngine.kt`, `MeshSocketClient/Server`...). Chưa chạy thử trên thiết bị.

---

## 0. BẢN RÚT GỌN: 5 ĐIỂM BẮT BUỘC (nếu chỉ muốn làm cho xong, chỉ cần phần này)

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

> Các mục 1–10 bên dưới là bản đầy đủ/mở rộng. Nếu mâu thuẫn với mục 0 này thì **mục 0 được ưu tiên** cho đến khi hai bạn chủ động nâng cấp.

---

## 1. Truyền tải

- TCP, mỗi gói là **một dòng JSON UTF-8 kết thúc bằng `\n`**.
- Độ dài dòng tối đa **64 KB**; vượt quá thì đóng kết nối.
- Base lắng nghe cổng **8888**. Cổng 8889 chỉ là cổng tùy chọn của nút relay/điện thoại để nhận đẩy trực tiếp.
- Trường không biết phải **được bỏ qua** (không lỗi). Giá trị enum lạ xử lý như `OTHER`, không làm hỏng gói.
- Gói không có `schemaVersion` được coi là **v0 (legacy)**.

## 2. Phong bì gói tin (envelope)

```json
{
  "schemaVersion": 1,
  "packetId": "11111111-2222-3333-4444-555555555555",
  "packetType": "SOS_DATA",
  "sourceNodeId": "NODE_7F3A91C2",
  "destinationNodeId": "BASE_STATION",
  "senderHopId": "NODE_7F3A91C2",
  "seq": 7,
  "ttl": 6,
  "hopCount": 0,
  "timestamp": 1760000000000,
  "routeHistory": ["NODE_7F3A91C2"],
  "payload": { },
  "checksum": "<hex chữ thường>"
}
```

| Trường | Quy ước | Khác v0 |
|---|---|---|
| `packetId` | **UUID đầy đủ** (36 ký tự) | Android v0 dùng 8 ký tự → phải đổi |
| `sourceNodeId` | `NODE_<8 hex>` sinh một lần, **lưu bền**, không dùng `Build.MODEL` | Android v0: `NODE_ANDROID_<Model>` |
| `destinationNodeId` | `BASE_STATION`, một `nodeId`, hoặc `BROADCAST` | giữ |
| `seq` | Số nguyên tăng dần theo từng nguồn (chống phát lại/sắp thứ tự) | mới |
| `timestamp` | epoch ms theo đồng hồ thiết bị (**không tin tuyệt đối**); Base ghi thêm `receivedAt` | giữ |
| `ttl`, `hopCount`, `senderHopId`, `routeHistory` | do relay sửa; **không** nằm trong chữ ký | giữ |

## 3. Loại gói và payload

### 3.1. Bảng enum

- `packetType`: `SOS_DATA`, `DISPATCH_CMD`, `ACK`, `HEARTBEAT`, `STATUS_UPDATE`, `CANCEL_SOS`, `SAFE_CHECKIN`, `BROADCAST_ALERT`, `TEAM_POSITION`
- `severity`: `CRITICAL`, `HIGH`, `MEDIUM`
- `alertType`: `MEDICAL`, `FLOOD_TRAPPED`, `LANDSLIDE`, `EARTHQUAKE`, `OTHER` *(Android v0 có `EARTHQUAKE`; Web cần xác nhận xử lý được)*
- `ackType`: `RECEIVED` (Base/relay đã nhận), `ACCEPTED` (thiết bị đã nhận lệnh), `COMPLETED` (đã thực hiện/đã xử lý), `REJECTED`

### 3.2. `SOS_DATA`

Thứ tự field **cố định** (quan trọng cho checksum v0, giữ đúng thứ tự Web hiện có):

`senderName, alertType, message, victimCount, severity, location, phoneNumber, bloodType, medicalNotes`

```json
"payload": {
  "senderName": "Nguyễn Văn A",
  "alertType": "FLOOD_TRAPPED",
  "message": "Nước dâng cao, mắc kẹt tầng 2",
  "victimCount": 3,
  "severity": "CRITICAL",
  "location": {
    "latitude": 16.067, "longitude": 108.152,
    "hasFix": true, "accuracyM": 12.5, "fixAgeMs": 800
  },
  "phoneNumber": "0901234567",
  "bloodType": "O+",
  "medicalNotes": null
}
```

- **Gói SOS đầu tiên tối thiểu:** `senderName, alertType, victimCount, severity, location` là đủ; hồ sơ y tế có thể gửi sau bằng `STATUS_UPDATE`.
- **Vị trí trung thực (bắt buộc):** nếu không có GPS, gửi `hasFix=false` kèm vị trí cuối biết được và `fixAgeMs`, hoặc `latitude/longitude = null`. **Cấm** gửi tọa độ mặc định/bịa. Web hiển thị "chưa rõ vị trí" hoặc "vị trí cũ X phút".
- Trường `hasFix` (Boolean), `accuracyM` (số nguyên mét), `fixAgeMs` (Long) thêm *vào trong* `location`, **sau** `latitude, longitude`, và **cả hai bên phải khai báo cùng lúc** (xem mục 0 về checksum). Không dùng `null` cho `latitude/longitude`.

### 3.3. `ACK`

```json
"payload": { "ackType": "RECEIVED", "ackedPacketId": "<uuid>", "dispatchId": null }
```

Chuỗi tự do `"ACK for packet: <id>"` của v0 bị thay bằng cấu trúc trên. Bên nhận v1 vẫn nên chịu được ACK v0.

### 3.4. `DISPATCH_CMD` (Base → thiết bị)

```json
"payload": {
  "dispatchId": "<uuid>", "incidentId": "<uuid hoặc nodeId>",
  "teamName": "Đội cứu hộ số 3", "etaMinutes": 15,
  "instruction": "Giữ bình tĩnh, ở nơi cao, bật còi khi thấy đội"
}
```

### 3.5. `HEARTBEAT`

```json
"payload": { "nodeMode": "VICTIM", "batteryPct": 41, "lastFixAgeMs": 12000, "outboxDepth": 0 }
```

Heartbeat **cũng là cách thiết bị "hỏi hộp thư lệnh"** (xem mục 5).

### 3.6. Các loại còn lại (định nghĩa khi cần, cùng nguyên tắc)

`STATUS_UPDATE` (hồ sơ y tế, cập nhật vị trí), `CANCEL_SOS` (`reason`: `SAFE`/`FALSE_ALARM`), `SAFE_CHECKIN`, `BROADCAST_ALERT` (CAP rút gọn), `TEAM_POSITION`.

## 4. Chữ ký toàn vẹn

### 4.1. v0 (legacy, đang chạy ở cả hai bên)

`checksum = SHA-256( Gson().toJson(payload) )`, hex chữ thường.

Điều kiện để v0 **tiếp tục khớp** giữa Java và Kotlin:

- Cả hai dùng Gson mặc định (escape HTML mặc định, **bỏ qua field `null`**).
- Thứ tự field payload đúng như mục 3.2 (Java khai báo field theo thứ tự đó; Kotlin phải khai báo cùng thứ tự, thêm field mới **ở cuối**).
- Chuỗi rỗng `""` vẫn được ghi ra; `null` thì bị bỏ.
- **Từ chối** checksum rỗng/thiếu ở **cả hai** bên (Android v0 đang chấp nhận → phải sửa).

**Test vector v0 legacy** *(không có GPS fix fields, `medicalNotes = null`)*:

```
payload JSON :
{"senderName":"Nguyễn Văn A","alertType":"FLOOD_TRAPPED","message":"Nước dâng cao, mắc kẹt tầng 2","victimCount":3,"severity":"CRITICAL","location":{"latitude":16.067,"longitude":108.152},"phoneNumber":"0901234567","bloodType":"O+"}

SHA-256      : effb931b2b66f9922a345e1aaed805403618394d116136c5f9e53dcb8f8e5b48
```

**Test vector v0 full (đầy đủ GPS Fix fields + Hồ sơ y tế)** *(đã kiểm chứng bằng cả Python và Gson Java)*:

```
payload JSON :
{"senderName":"Nguyễn Văn A","alertType":"FLOOD_TRAPPED","message":"Nước dâng cao, mắc kẹt tầng 2","victimCount":3,"severity":"CRITICAL","location":{"latitude":16.067,"longitude":108.152,"hasFix":true,"accuracyM":12,"fixAgeMs":800},"phoneNumber":"0901234567","bloodType":"O+","medicalNotes":"Dị ứng penicillin"}

SHA-256      : 4919833103c3cfb518bbc8922ecfcb49cb40b529b56f188a7a5eba5ef8571f97
```

### 4.2. v1 (đề xuất: chuỗi ký canonical + HMAC)

1. **Chuỗi header** = `packetId|packetType|sourceNodeId|destinationNodeId|timestamp|seq`
2. **Chuỗi payload** (SOS_DATA) = `senderName|alertType|severity|victimCount|latitude|longitude|phoneNumber|bloodType|medicalNotes|message`
   - `latitude`, `longitude` in **đúng 6 chữ số thập phân**, dấu chấm; `null` hoặc thiếu = chuỗi rỗng.
   - Trong chuỗi văn bản: `\` → `\\`, `|` → `\|`. UTF-8, không chuẩn hóa thêm.
3. **Chuỗi ký** = `header` + `"\n"` + `payload` (không có `\n` ở cuối).
4. `checksum` = SHA-256 (giai đoạn đầu) hoặc **HMAC-SHA256 bằng khóa thiết bị**, hex chữ thường.
5. Không ký `ttl`, `hopCount`, `routeHistory`, `senderHopId`.
6. Mỗi `packetType` khác có thứ tự payload canonical riêng (thêm vào file này khi định nghĩa).

**Test vector v1** *(đã tính bằng Python)*:

```
Chuỗi ký:
11111111-2222-3333-4444-555555555555|SOS_DATA|NODE_A_VICTIM|BASE_STATION|1760000000000|7
Nguyễn Văn A|FLOOD_TRAPPED|CRITICAL|3|16.067000|108.152000|0901234567|O+||Nước dâng cao, mắc kẹt tầng 2

SHA-256     : 31bb80761294a39a2bda3589350e8d8da58fee5e46d66a203713694af1d7ad82
HMAC-SHA256 (khóa "demo-station-key-change-me") :
              ed2ceeb132c9f121ca56517f7a0454c5a189925261a7c061ec8f37fabe15e11a
```

**Chuyển tiếp v0 → v1:** gói có `schemaVersion` ≥ 1 kiểm theo v1, không có thì theo v0. Chỉ cho phép v0 trong giai đoạn chuyển tiếp, sau đó tắt.

## 5. Giao thức phiên: yêu cầu–phản hồi trên cùng kết nối

Lý do: điện thoại ở sau hotspot/NAT, IP đổi, có thể ngủ; Base thì có địa chỉ cố định. Vì vậy **điện thoại luôn là bên khởi tạo**; Base **trả lời trên chính kết nối đó**.

```
Điện thoại                                   Base
   │ ── {SOS_DATA, packetId=P} \n ───────────▶ │  ghi DB, tạo/cập nhật sự cố
   │ ◀── {ACK RECEIVED, ackedPacketId=P} \n ── │
   │ ◀── {DISPATCH_CMD d1} \n  (nếu đang chờ) ─ │  lệnh chờ của nodeId này
   │ ◀── (đóng kết nối)                       │
   │ ── {ACK ACCEPTED, dispatchId=d1} \n ────▶ │  (kết nối mới hoặc cùng phiên)
   │ ── {HEARTBEAT} \n ──────────────────────▶ │  = hỏi hộp thư lệnh
   │ ◀── {ACK RECEIVED} + {DISPATCH_CMD…} ─── │
```

Quy tắc:

1. **Điện thoại** sau khi gửi giữ kết nối mở tối đa ~5 giây (`soTimeout`) để đọc phản hồi; tối đa 16 dòng/lần.
2. **Base** gửi `ACK(RECEIVED)` cho mọi `SOS_DATA`, `STATUS_UPDATE`, `CANCEL_SOS`, `SAFE_CHECKIN` hợp lệ; sau đó gửi các `DISPATCH_CMD` đang ở trạng thái `QUEUED`/`SENT` của `nodeId` đó.
3. **Idempotent:** nhận lại cùng `packetId` thì Base trả lại ACK, **không** tạo sự cố mới.
4. **UI Android chỉ hiện "đã tới trạm"** khi đã đọc được `ACK(RECEIVED)` cho đúng `packetId`.
5. **Lệnh:** trạng thái `QUEUED → SENT → DELIVERED (ACK RECEIVED/ACCEPTED từ thiết bị) → ACCEPTED → COMPLETED`; gửi lại có backoff tới khi có ACK (xem Phụ lục B file Web).
6. **SOS lặp lại:** thiết bị phát lại gói SOS (cùng `packetId`) với backoff có jitter tới khi có `ACK(RECEIVED)`; có thể gửi bản cập nhật vị trí bằng `STATUS_UPDATE`.
7. Server lắng nghe phía điện thoại (cổng 8889) chỉ là tối ưu hóa đẩy trực tiếp, **không phải điều kiện** để nhận lệnh.
8. **Nhiều bước nhảy** (khi triển khai mesh thật): relay giữ nguyên `packetId`, cập nhật `ttl/hopCount/routeHistory`; đường về dùng `routeHistory` đảo ngược. Phạm vi này để sau cùng, xem "Sổ quyết định".

## 6. Danh tính và khám phá

- `nodeId` sinh một lần/thiết bị, lưu bền. Base tạo **sổ đăng ký thiết bị** (`nodeId`, tên, loại, lần cuối thấy, trạng thái, khóa).
- Cấu hình thiết bị bằng **mã QR** do Web sinh: `{station, host, port, deviceKey, schemaVersion}`.
- Dự phòng: cổng mạng mặc định → mDNS `_meshrescue._tcp` → nhập tay.

## 7. Thời gian và thứ tự

- Đồng hồ điện thoại có thể lệch. Base ghi `receivedAt`; sắp xếp theo `seq` trong cùng nguồn.
- Cửa sổ dedup: **≥ 60 giây ở cả hai bên**; Base nên lưu `packetId` đã xử lý vào DB (sống sót qua khởi động lại).

## 8. Lỗi và tương thích

- Gói hỏng/không phân tích được: Base **không** ghi vào cache dedup; có thể đếm metric nhưng không chặn gói đúng gửi lại cùng `packetId`.
- Checksum sai hoặc rỗng: bỏ gói, tăng bộ đếm, ghi log. Không trả `ACK(RECEIVED)`.
- Phiên bản không hỗ trợ: Base trả `ACK(REJECTED)` kèm lý do trong `payload.reason`.

## 9. Quy trình đổi hợp đồng và kiểm thử

**Thay đổi hợp đồng**

1. Mở một mục trong "Sổ quyết định" (mục 10) hoặc một thay đổi có số phiên bản (`v1.1`...).
2. Cả hai duyệt.
3. Cập nhật **test vector** và ví dụ trước khi sửa code.
4. Mỗi bên cập nhật code và chạy test hợp đồng.

**Bộ test hợp đồng chung** (đặt `contract/test-vectors/*.json` dùng chung):

| Bên | Công cụ | Nội dung |
|---|---|---|
| Web | Mobile Emulator + unit test | Dùng đúng các vector ở mục 4; chạy kịch bản SOS→ACK→DISPATCH→ACK |
| Android | Fake Base (ServerSocket giả) + unit test | Dùng cùng vector; Fake Base trả ACK/lệnh theo mục 5; kiểm tra UI trạng thái |

**Cổng tích hợp máy thật** (cần tất cả):

- [ ] Vector v0 và v1 khớp ở Java và Kotlin.
- [ ] Emulator↔Web và Fake Base↔Android đều xanh.
- [ ] Web đạt Definition of Done phần Web; Android đạt M1–M3 (xem file Android).
- [ ] Hai bên cùng chạy một kịch bản demo trên một hotspot, ghi lại kết quả.

## 10. Sổ quyết định (cần hai bạn chốt)

| # | Vấn đề | Đề xuất | Trạng thái |
|---|---|---|---|
| 1 | Giao thức phiên piggyback (mục 5) thay cho "Base tự kết nối tới IP điện thoại" | Piggyback | **Chờ chốt** |
| 2 | Ý nghĩa ACK: `RECEIVED` (tự động) và `ACCEPTED`/`COMPLETED` (người dùng) | Hai tầng | **Chờ chốt** |
| 3 | Checksum: giữ v0 một thời gian rồi chuyển v1 + HMAC | Có lộ trình chuyển tiếp | **Chờ chốt** |
| 4 | Định dạng `nodeId` và `packetId` | `NODE_<8hex>`, UUID | **Chờ chốt** |
| 5 | Quy ước vị trí `hasFix/accuracyM/fixAgeMs`; cấm tọa độ bịa | Như mục 3.2 | **Chờ chốt** |
| 6 | Bảng enum `alertType` (có `EARTHQUAKE`, `OTHER`) | Như mục 3.1 | **Chờ chốt** |
| 7 | Phạm vi mesh nhiều bước nhảy cho đồ án | Demo: Base làm hotspot/gateway, phone→Base trực tiếp; nhiều bước nhảy là mở rộng sau | **Chờ chốt** |
| 8 | Mã hóa payload cho relay | Ngoài phạm vi; ghi là hạn chế đã biết, chỉ làm HMAC | **Chờ chốt** |
| 9 | Cổng/giao diện cấp khóa thiết bị (QR từ Web) | Có | **Chờ chốt** |

---

*File này nên nằm trong repo chung (`docs/INTEGRATION_CONTRACT.md`) và được xem là tài liệu có phiên bản. Khi hai bên đã chốt, đổi trạng thái từng dòng ở mục 10 thành "Đã chốt (v1.0)".*

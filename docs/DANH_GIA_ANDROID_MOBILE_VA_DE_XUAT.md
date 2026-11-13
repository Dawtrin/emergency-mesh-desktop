# ĐÁNH GIÁ TOÀN DIỆN MẢNG ANDROID & ĐỀ XUẤT HOÀN THIỆN
## Đồ án Cơ sở 4 — Emergency Mesh Rescue (Mobile Android)

- **Người phụ trách Mobile:** Nguyễn Thị Bích Trâm
- **Phạm vi:** thư mục `android` (Kotlin, Jetpack Compose, Room, DataStore, Wi-Fi Direct, TCP socket, `MeshRelayService`)
- **Phương pháp:** đọc mã nguồn tĩnh toàn bộ lớp lõi (`MeshPacket`, `ChecksumUtil`, `PacketFactory`, `RoutingEngine`, `SeenPacketCache`, `MeshSocketClient/Server`, `MeshRelayService`, `WifiDirectManager`, `MeshRepository`, `IncidentDatabase`, `UserPreferences`, `GpsLocationManager`, `MainActivity`, manifest, gradle) và đối chiếu từng trường gói tin với mã Web (`desktop`).
- **Chưa làm:** chưa build, chưa chạy trên thiết bị, chưa đo pin, chưa đọc kỹ từng màn hình Compose (chỉ đọc phần nối với Repository). Chỗ nào ghi *(cần xác nhận)* là suy luận từ code và hành vi nền tảng Android, cần kiểm chứng trên máy thật.
- **Phần đối chiếu quốc tế (mục 2):** viết từ kiến thức nền, chưa tra cứu lại tài liệu hiện hành; hãy kiểm tra nguồn trước khi trích dẫn.
- **File đi kèm:** `INTEGRATION_CONTRACT.md` (hợp đồng chung hai bên) và `DANH_GIA_WEB_C2_VA_DE_XUAT.md` (đánh giá phía Web của Đạt).

---

## MỤC LỤC

0. Tóm tắt điều hành
0B. **5 điểm chung để kết nối Web ↔ Mobile** (đọc phần này trước khi ghép)
1. Đánh giá hiện trạng mã nguồn Android
2. Đối chiếu với các ứng dụng cứu nạn/dã chiến thực tế
3. Điểm yếu và đề xuất cải tiến (storm, pin, khám phá láng giềng, UX hoảng loạn, điều phối 2 chiều, bảo mật)
4. Lộ trình thực hiện và tiêu chí hoàn thành
5. Làm sao để Web và Mobile ghép lại ổn định, thống nhất
- Phụ lục A: Kiến trúc đích đề xuất
- Phụ lục B: Hồ sơ năng lượng theo chế độ
- Phụ lục C: Đoạn mã phác thảo (chưa biên dịch)
- Phụ lục D: Checklist kiểm thử

---

## 0. TÓM TẮT ĐIỀU HÀNH

**Kết luận chính:** Ứng dụng đã có bộ khung giao diện và phần cứng sinh tồn rất tốt (SOS một chạm, Morse flash, còi, la bàn, hồ sơ ngoại tuyến, Room lưu trước khi gửi). Tuy nhiên có **ba khoảng cách lớn giữa "mô tả" và "thực tế mã nguồn"**:

1. **Chưa có mạng mesh thật.** `forwardPacket()` luôn gửi gói về `baseStationIp:port`. `WifiDirectManager` chỉ `discoverPeers()` rồi hiển thị danh sách, không `connect()`, không tạo nhóm, không dùng láng giềng để định tuyến. Hiện tại kiến trúc thực tế là **hình sao tới Base với IP gõ tay**.
2. **"Đã gửi" không có nghĩa "trạm đã nhận".** Thông báo "ĐÃ PHÁT TÍN HIỆU SOS THÀNH CÔNG!" hiện ra khi kết nối TCP và ghi dữ liệu không ném lỗi; không có ACK nào quay về xác nhận (Web cũng chưa gửi ACK cho SOS).
3. **Có thể gửi vị trí giả như thật.** Khi chưa có GPS, app gửi tọa độ cố định `16.054407, 108.202167` (Đà Nẵng). Đây là lỗi **an toàn tính mạng** nên xếp ưu tiên cao nhất.

| Hạng mục | Mức sẵn sàng | Ghi chú ngắn |
|---|---|---|
| Giao diện, phần cứng sinh tồn | Tốt | Compose, Morse, còi `USAGE_ALARM`, la bàn, onboarding ngoại tuyến |
| Đóng gói/hợp đồng gói tin | Khá, còn lệch | Cùng Gson, cùng thứ tự field với Web; thiếu phone/blood/notes; `packetId` 8 ký tự; `nodeId` theo model máy |
| `RoutingEngine` | Khá (logic thuần, dễ test) | Thứ tự checksum→dedup đúng; chưa có láng giềng/giảm nhiễu; chấp nhận checksum rỗng |
| Socket / ngoại lệ | Yếu | `PrintWriter` nuốt lỗi, không đọc phản hồi, server không timeout/giới hạn, `catch` rỗng |
| Chạy ngầm (`MeshRelayService`) | Yếu | `Activity.onDestroy` dọn tài nguyên của chính Service; chưa có WakeLock/Doze plan |
| Mesh P2P thật | Chưa có | Chỉ discover, không connect/group |
| Tối ưu pin | Chưa có | Heartbeat 30s mở TCP mới, GPS 3s độ chính xác cao; "Ultra Battery Saver" chỉ đổi màu UI |
| Điều phối 2 chiều với Web | Chưa khép kín | Không ACK nhận, không inbox, Web không biết IP máy |
| Bảo mật/riêng tư | Yếu | Dữ liệu y tế dạng rõ qua relay, không xác thực nguồn, có khả năng vào sao lưu đám mây |
| Kiến trúc MVVM/Clean | Một phần | Có thư mục phân lớp nhưng không có ViewModel/DI; Repository ôm quá nhiều việc |
| Kiểm thử | Gần như chưa có | Chỉ có `ExampleUnitTest`/`ExampleInstrumentedTest` |

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

## 1. ĐÁNH GIÁ HIỆN TRẠNG MÃ NGUỒN ANDROID

### 1.1. Luồng thực tế hiện tại (theo code, không theo mô tả)

```
 Người dùng bấm SOS (SosHomeScreen)
        │
        ▼
 MeshRepository.broadcastSos()
   ├─ PacketFactory.createSosPacket()   (location = GPS, hoặc TỌA ĐỘ GIẢ nếu null)
   ├─ RoutingEngine.prepareOutgoingPacket()  (ghi cache, routeHistory, ký SHA-256)
   ├─ Room.insert(sendSuccess=false)         (lưu trước)
   └─ MeshSocketClient.sendPacket(baseStationIp:8888)   ← 3 lần, mỗi lần connect ≤5s
         └─ connect → println(json) → đóng       (KHÔNG đọc phản hồi)
              └─ không ném lỗi ⇒ markAsSent ⇒ Toast "THÀNH CÔNG"

 Gói tới máy này (MeshSocketServer :relayPort, chỉ chạy khi SosHome đang mở
 hoặc MeshRelayService đang chạy)
        └─ RoutingEngine.processIncomingPacket
             ├─ DeliverLocal  → chỉ xử lý DISPATCH_CMD (overlay + Room)
             └─ Forward       → gửi tới baseStationIp:baseStationPort (luôn về Base)
```

### 1.2. Kiến trúc: MVVM / Clean Architecture?

**Nhận định: có "dáng" phân lớp nhưng chưa phải MVVM/Clean.**

- Có thư mục `data / domain / ui / hardware / network / service` (điểm tốt, dễ đọc).
- **Không có `ViewModel` nào.** Màn hình nhận thẳng `repository` làm tham số, tự `collectAsState()` và giữ trạng thái bằng `remember`. Hệ quả: trạng thái UI mất khi tiến trình bị hệ thống thu hồi, khó kiểm thử UI, logic nghiệp vụ lẫn trong Composable (ví dụ `triggerSos()`, khởi động server trong `LaunchedEffect`).
- `MeshRepository` là **"God object"**: vừa quản lý socket, routing, heartbeat, DB, thống kê, trạng thái SOS/Dispatch, P2P. Khó thay thế/kiểm thử từng phần.
- Lớp `domain` chỉ có `RoutingEngine`, `SeenPacketCache`, `PacketFactory`, `ChecksumUtil`; chưa có use case (`SendSos`, `AcknowledgeDispatch`, `RetryOutbox`...).
- Không có DI (Hilt/Koin); singleton thủ công qua `getInstance(context)`.
- `MainActivity.onCreate` gọi `runBlocking` đọc DataStore **trên luồng chính** (rủi ro giật khi khởi động/ANR trên máy yếu).
- Điểm tốt: `RoutingEngine` thuần Kotlin, không phụ thuộc Android, dùng `sealed class RoutingDecision`, rất hợp để unit test.

### 1.3. Độ ổn định của `RoutingEngine`

**Làm tốt**
- Thứ tự **checksum → dedup → self-origin → TTL → biến đổi gói**, hợp lý hơn phía Web (Web đang dedup trước checksum).
- Sao chép gói và danh sách `routeHistory` khi chuyển tiếp thay vì sửa tại chỗ.
- `isBaseStationForMe` phân biệt đích Base.

**Vấn đề**
1. **Checksum rỗng được chấp nhận:** `verifyChecksum()` trả `true` nếu `checksum.isEmpty()`. Gói không ký đi xuyên qua relay. (Web thì từ chối checksum null, tức hai bên không nhất quán.)
2. **`SELF_ORIGINATED` dựa vào `sourceNodeId == currentNodeId`**, mà `nodeId` lại là `"NODE_ANDROID_" + Build.MODEL` (xem M-03): hai máy cùng model sẽ **bỏ gói của nhau**.
3. **Dedup chỉ 30 giây, chỉ trong RAM** (Web là 60 giây). Sau khi tiến trình chết, cache mất; gói đến muộn bị coi là mới.
4. **Chưa có bảng láng giềng, chưa có giảm nhiễu phát lại** (xem 3.1). Hiện không bão vì mọi thứ chạy hình sao về Base, nhưng sẽ bão ngay khi làm flooding thật.
5. **`SeenPacketCache` tạo luồng `ScheduledExecutor` không-daemon** cho mỗi instance; `RoutingEngine.shutdown()` (được `Activity.onDestroy` gọi) tắt luồng dọn cache nên **cache không bao giờ hết hạn nữa** trong khi singleton vẫn sống (xem M-06).
6. **`prepareOutgoingPacket` sửa trực tiếp** đối tượng được truyền vào (`apply {}`) vì `MeshPacket` có `var` và `MutableList`; dễ gây lỗi khi một gói được dùng ở nhiều coroutine.
7. **Gói `HEARTBEAT` có đích `"BROADCAST"` và TTL 2**: khi bật flooding thật, N máy × 2 bước nhảy là nguồn nhiễu chính.

### 1.4. Socket và xử lý ngoại lệ

**`MeshSocketClient`**
- **`PrintWriter.println()` không ném `IOException`** (lỗi chỉ lộ qua `checkError()`). Vì vậy "thành công" có thể là giả nếu ghi thất bại sau khi kết nối được.
- Đóng kết nối ngay sau khi ghi, **không đọc phản hồi** nên không thể nhận ACK; `soTimeout` được đặt nhưng không bao giờ dùng.
- `catch (e: Exception)` trong coroutine **nuốt cả `CancellationException`**, làm hỏng việc hủy coroutine (phải ném lại).
- Retry: 3 lần, backoff tuyến tính 1s/2s, mỗi lần connect tối đa 5s ⇒ tối đa khoảng 20 giây giữ máy; không có jitter; không phân biệt lỗi tạm thời và lỗi vĩnh viễn (IP sai).
- Không gắn socket vào mạng Wi-Fi Direct/hotspot cụ thể. Trên một số máy, khi Wi-Fi "không có Internet" thì hệ thống có thể ưu tiên mạng khác *(cần xác nhận trên thiết bị)*; cách xử lý thường là dùng `ConnectivityManager` và `Network.bindSocket`.

**`MeshSocketServer`**
- Không `soTimeout` cho client socket ⇒ một kết nối treo giữ một luồng mãi.
- `readLine()` không giới hạn độ dài; mỗi kết nối một `Thread` mới, không giới hạn số lượng.
- **Nuốt mọi ngoại lệ** (`catch (_: Exception) {}`), không log. Nếu `ServerSocket(port)` thất bại (cổng bận), luồng chết nhưng `isRunning` vẫn `true`, UI vẫn hiện "đang lắng nghe".

### 1.5. An toàn khi chạy ngầm `MeshRelayService`

1. **Xung đột vòng đời:** `MainActivity.onDestroy()` gọi `repository.cleanup()`, hàm này **dừng server, dừng heartbeat, hủy đăng ký P2P và `routingEngine.shutdown()`**. Nhưng `Repository` là singleton dùng chung với Service. Khi người dùng vuốt tắt app khỏi danh sách gần đây, Activity bị hủy, công việc của Service bị dọn mất trong khi thông báo vẫn hiện "đang tiếp sóng". Đây là lỗi **thầm lặng** nguy hiểm vì người dùng tin rằng relay vẫn chạy.
2. **Thông báo tĩnh:** nội dung luôn là "đang hoạt động & gửi Heartbeat" bất kể server có thực sự lắng nghe được hay không.
3. **Không giữ WakeLock/WifiLock**, không có kế hoạch cho Doze/App Standby; khai báo `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` nhưng **không có mã nào yêu cầu** quyền này.
4. **Android 14+ và kiểu dịch vụ nền `location|connectedDevice`** *(cần xác nhận trên thiết bị)*: khởi động lại dịch vụ loại `location` từ nền (ví dụ `START_STICKY` khi hệ thống kill) có thể bị chặn nếu quyền vị trí không đang "while-in-use". Service cũng **không dùng vị trí** dù khai báo loại `location`.
5. **Khởi động bằng tay:** Service chỉ chạy khi người dùng bật công tắc ở `RelayMonitorScreen`. Nạn nhân thường không bật nên không nhận được lệnh khi app ở nền.
6. `START_STICKY` + `onStartCommand` luôn mở lại server/heartbeat: đã có `stop...` trước nên không nhân đôi, nhưng không có kiểm tra trạng thái thực.

### 1.6. Wi-Fi Direct và "mesh" thật

- `WifiDirectManager` chỉ: đăng ký receiver, `discoverPeers()` (bấm nút thủ công), `requestPeers`, `requestConnectionInfo`. Không có `connect()`, `createGroup()`, `removeGroup()`, `stopPeerDiscovery()`, dịch vụ khám phá (DNS-SD).
- `groupOwnerAddress` được thu thập vào `P2pState` nhưng **không nơi nào dùng** để chọn đích gửi.
- Danh sách peer chỉ để hiển thị và rung haptic.
- `baseStationIp` mặc định `192.168.1.100`, người dùng tự nhập trong hộp thoại cấu hình.
- Hạn chế nền tảng cần cân nhắc *(cần xác nhận theo thiết bị)*: Wi-Fi Direct thường chỉ cho một thiết bị ở trong một nhóm tại một thời điểm, nên "mesh nhiều bước nhảy chỉ bằng Wi-Fi Direct" khó hơn nhiều so với vẽ trên giấy. Xem lựa chọn thay thế ở 3.3.

### 1.7. Lưu trữ, quyền và riêng tư

- **Room:** lưu SOS trước khi gửi (tư duy outbox tốt). Nhưng `fallbackToDestructiveMigration()` sẽ **xóa hết bản ghi (kể cả SOS chưa gửi) khi nâng phiên bản schema**, `exportSchema = false`. Bảng `incidents` không có phone/blood/notes nên **`retrySingleIncident()` dựng lại gói thiếu thông tin** so với gói gốc.
- **Retry chỉ thủ công:** `retryFailedPackets()` chỉ được gọi từ nút bấm (RelayMonitor/History). Không tự gửi lại khi mạng trở lại, không có `WorkManager`, không có `ConnectivityManager.NetworkCallback`.
- **DataStore** chứa họ tên, số điện thoại, nhóm máu, ghi chú y tế. Manifest để `allowBackup="true"` và file `backup_rules.xml`/`data_extraction_rules.xml` vẫn là **mẫu mặc định**, nên dữ liệu có thể được đưa vào sao lưu đám mây *(cần xác nhận theo phiên bản Android)*.
- **Quyền:** onboarding xin hàng loạt (vị trí, camera, thông báo, thiết bị Wi-Fi lân cận) và **bỏ qua kết quả** (`{ _ -> }`). Không giải thích lý do, không xử lý từ chối. `CAMERA` có thể không cần cho đèn pin vì `CameraManager.setTorchMode` thường không đòi quyền camera *(cần xác nhận)*; bỏ đi sẽ giảm ma sát và rủi ro khi duyệt.
- **GPS:** `PRIORITY_HIGH_ACCURACY` mỗi 3 giây, chỉ thu khi `SosHomeScreen` hiển thị. Khi màn hình tắt, vị trí mới không được cập nhật; không có "tuổi" của vị trí.
- **Release build:** `isMinifyEnabled = false` nên Gson (dùng phản xạ) đang an toàn. Nếu sau này bật R8 mà không giữ lớp `MeshPacket`/`Payload`/`Location`, JSON sẽ sai tên field.

### 1.8. Danh sách lỗi và rủi ro

Mức độ: **S1** = chặn ghép hoặc nguy hiểm tính mạng · **S2** = sai nghiệp vụ/bảo mật · **S3** = nên sửa.

| ID | Mức | Vấn đề | Vị trí | Hệ quả |
|---|---|---|---|---|
| M-01 | S1 | **Tọa độ giả khi không có GPS** (`?: 16.054407 / 108.202167`) | `MeshRepository.broadcastSos` | Đội cứu hộ tới sai chỗ; Web hiển thị như thật |
| M-02 | S1 | **"Gửi thành công" = TCP ghi không lỗi**, không phải Base đã nhận; `PrintWriter` nuốt lỗi; không đọc ACK; `markAsSent` ngay | `MeshSocketClient`, `MeshRepository` | An tâm giả, người dùng ngừng cầu cứu |
| M-03 | S1 | **`deviceNodeId = "NODE_ANDROID_" + Build.MODEL`**, không duy nhất | `MeshRepository` | Hai máy cùng model: `SELF_ORIGINATED` bỏ gói của nhau; Web ghi đè nạn nhân (PK `node_id`) |
| M-04 | S1 | **Không có mesh thật**: `forwardPacket` luôn gửi về Base; P2P chỉ discover; IP gõ tay | `MeshRepository`, `WifiDirectManager` | Không có đa bước nhảy thực sự; ghép nối phụ thuộc IP nhập tay |
| M-05 | S1 | **SOS gửi một lần (3 lần thử trong ~20s) rồi dừng**; thất bại chỉ retry thủ công | `MeshRepository`, UI | Mất tín hiệu nếu lúc bấm không có mạng, dù người dùng vẫn cần cứu |
| M-06 | S2 | **`Activity.onDestroy → repository.cleanup()`** dọn tài nguyên của Service; `routingEngine.shutdown()` làm cache không hết hạn | `MainActivity`, `MeshRepository` | Relay ngừng hoạt động mà thông báo vẫn hiện; rò rỉ cache |
| M-07 | S2 | **Payload thiếu `phoneNumber/bloodType/medicalNotes`** dù đã lưu trong DataStore | `MeshPacket.Payload`, `PacketFactory` | Web không bao giờ nhận hồ sơ y tế |
| M-08 | S2 | **Điều phối 2 chiều chưa khép kín**: ACK chỉ khi người dùng bấm; không ACK `RECEIVED` tự động; nhận lệnh chỉ khi server đang lắng nghe; Web không biết IP máy | `SosHomeScreen`, `MeshRepository` | Lệnh Web không tới điện thoại thật |
| M-09 | S2 | **Pin:** heartbeat 30s mở TCP mới + retry 3 lần; GPS 3s độ chính xác cao; "Ultra Battery Saver" chỉ đổi màu giao diện | `MeshRepository`, `GpsLocationManager`, `RelayMonitorScreen` | Không sống được nhiều ngày |
| M-10 | S2 | **`MeshSocketServer`** không timeout/giới hạn, nuốt lỗi, bind thất bại im lặng | `MeshSocketServer` | Treo luồng, giả vờ đang lắng nghe |
| M-11 | S2 | **Checksum rỗng chấp nhận, SHA-256 không khóa, dữ liệu y tế dạng rõ qua relay** | `MeshPacket.verifyChecksum` | Giả mạo và nghe lén dễ dàng |
| M-12 | S2 | **`allowBackup=true`** với quy tắc mẫu | `AndroidManifest.xml`, `res/xml` | Hồ sơ y tế có thể lên đám mây *(cần xác nhận)* |
| M-13 | S3 | Không ViewModel/DI; Repository quá tải; `runBlocking` ở `onCreate` | `MainActivity`, `MeshRepository` | Khó test, khó bảo trì |
| M-14 | S3 | `catch (e: Exception)` nuốt `CancellationException`; `catch (_: Exception) {}` khắp nơi | nhiều file | Lỗi biến mất, khó chẩn đoán |
| M-15 | S3 | Room `fallbackToDestructiveMigration`; bảng thiếu trường | `IncidentDatabase` | Mất SOS chưa gửi khi nâng phiên bản |
| M-16 | S3 | `packetId` 8 ký tự (32 bit) trái hợp đồng UUID; dedup 30s ≠ 60s của Web | `PacketFactory`, `SeenPacketCache` | Nguy cơ trùng ID khi mạng lớn; cửa sổ dedup lệch |
| M-17 | S3 | Quyền xin hàng loạt, bỏ qua kết quả; `CAMERA` có thể thừa; chưa xin tắt tối ưu pin | `EmergencyOnboardingScreen` | Chức năng hỏng thầm lặng khi bị từ chối |
| M-18 | S3 | Gần như không có test | `src/test`, `src/androidTest` | `RoutingEngine` thuần mà chưa được kiểm |
| M-19 | S3 | Thông báo Service tĩnh, không phản ánh trạng thái thật | `MeshRelayService` | Thông tin sai |

### 1.9. Đối chiếu hợp đồng gói tin Web ↔ Android

| Mục | Web (`desktop`) | Android | Trạng thái |
|---|---|---|---|
| Tên field gói tin | `packetId … payload` | Giống | Khớp |
| Thứ tự field payload (ảnh hưởng checksum) | `senderName, alertType, message, victimCount, severity, location, phoneNumber, bloodType, medicalNotes` | `senderName, alertType, message, victimCount, severity, location` | Khớp phần chung; Android thiếu 3 field cuối |
| Cách tính checksum | SHA-256 của `Gson().toJson(payload)` | SHA-256 của `Gson().toJson(payload)` | **Khớp hiện tại** (cùng Gson, cùng thứ tự), nhưng mong manh |
| Checksum rỗng | Từ chối | **Chấp nhận** | Lệch |
| `packetId` | UUID đầy đủ | 8 ký tự hex | Lệch |
| `nodeId` | Dạng `NODE_A_VICTIM`... (kịch bản) | `NODE_ANDROID_<Model>` | Lệch, không duy nhất |
| Dedup | 60s, dedup trước checksum | 30s, checksum trước dedup | Lệch |
| ACK | Web không gửi ACK cho SOS; ACK nhận về chỉ in console | Android gửi ACK dạng chữ tự do `"ACK for packet: <id>"`, không xử lý ACK đến | Chưa có ngữ nghĩa chung |
| Gửi lệnh xuống | Gửi tới một host:port cố định cấu hình | Lắng nghe `relayPort` khi Home mở hoặc Service chạy | Chưa nối được |
| Vị trí | `latitude/longitude NOT NULL`, không có cờ GPS | Có thể là tọa độ giả | **Nguy hiểm** |
| Enum `alertType` | `MEDICAL, FLOOD_TRAPPED, LANDSLIDE…` | Thêm `EARTHQUAKE` | Cần thống nhất bảng giá trị |
| Heartbeat payload | Chữ tự do | `"Heartbeat from <node>"` | Nên có số liệu có cấu trúc |

> **Cập nhật cho phía Web:** sau khi đọc mã Android, nhận định "checksum dễ lệch ngay" ở bản đánh giá Web đã được hạ xuống "đang khớp nhưng mong manh" (đã chỉnh trong file Web).

### 1.10. Những điểm đã làm tốt (nên giữ)

- Lưu SOS vào Room **trước** khi gửi.
- `RoutingEngine` thuần, có `sealed class` quyết định, checksum trước dedup.
- Còi dùng `USAGE_ALARM`, nháy Morse bằng coroutine, la bàn, theme OLED tiết kiệm màn hình.
- Onboarding và hồ sơ hoàn toàn ngoại tuyến (DataStore).
- Tách `PacketFactory` giúp sinh gói đồng nhất.
- Có màn hình lịch sử và giám sát relay, thuận lợi để hiển thị trạng thái thật khi đã có ACK.

---

## 2. ĐỐI CHIẾU VỚI CÁC ỨNG DỤNG THỰC TẾ

| Hệ thống | Điều đáng học | Áp dụng cho ứng dụng của bạn | Hiện trạng |
|---|---|---|---|
| **Garmin inReach SOS** | SOS **có chủ đích** (nhấn giữ/nắp che), tín hiệu **lặp lại cho đến khi có xác nhận**; thiết bị hiển thị trạng thái "trung tâm đã nhận / đang điều phối"; theo dõi vị trí định kỳ; có thể **hủy** | Nhấn giữ 2–3 giây + đếm ngược; SOS phát lặp lại tới khi có ACK; máy trạng thái hiển thị cho người dùng; nút hủy/báo an toàn | Bấm một lần, gửi một lần, không có xác nhận |
| **ATAK-CIV** | Mỗi đối tượng là sự kiện có `uid`, **`stale`**, sai số vị trí; chia sẻ vị trí nhóm theo chu kỳ; cấu hình mạng bằng gói dữ liệu/QR | Gửi `accuracyM`, `fixAgeMs`; cấu hình trạm bằng QR; thông tin tự cũ đi | Không có độ chính xác/tuổi vị trí; IP gõ tay |
| **Briar** | Mạng **ngoại tuyến**, **lưu-và-chuyển-tiếp**, đồng bộ khi gặp nhau; mã hóa đầu-cuối; Bluetooth/Wi-Fi | Hàng đợi outbox bền vững, tự đồng bộ khi có đường; mã hóa nội dung cho Base | Outbox có nhưng retry thủ công; dữ liệu dạng rõ |
| **Bridgefy** | Mesh Bluetooth LE nhiều bước nhảy với TTL; dùng được khi mất sóng | Hướng đi cho phát hiện/chuyển tiếp phone-to-phone; **bài học bảo mật**: đã có nghiên cứu học thuật chỉ ra điểm yếu của mesh không xác thực/mã hóa kém *(kiểm tra nguồn trước khi trích)* | Chưa có phone-to-phone thật; chưa xác thực |
| **Apple Find My (Offline Finding)** | Thiết bị **phát tín hiệu năng lượng thấp** theo chu kỳ; thiết bị lân cận chuyển tiếp **mà không đọc được nội dung** (mã hóa), định danh **xoay vòng** | Beacon thưa, ít tốn pin; relay không đọc được PII; ID xoay để chống theo dõi | Heartbeat dày 30s, relay đọc được mọi thứ |
| **Meshtastic / Nearby Connections / Wi-Fi Aware** *(bổ sung)* | Nhiều lựa chọn khám phá láng giềng không cần nhập IP; Nearby Connections hỗ trợ cấu trúc cụm nhiều-nhiều qua Google Play Services | Thay thế hoặc bổ sung cho Wi-Fi Direct tự dựng | Chỉ `discoverPeers` |

**Bài học tổng hợp:** (1) SOS phải *bền* (lặp đến khi có xác nhận) và *có chủ đích*. (2) Trạng thái nhìn thấy được và đáng tin quan trọng hơn tính năng. (3) Tiết kiệm pin = ít phát nhưng phát đúng. (4) Relay không nên đọc được nội dung. (5) Vị trí phải đi kèm độ chính xác và độ tươi.

---

## 3. ĐIỂM YẾU VÀ ĐỀ XUẤT CẢI TIẾN

### 3.1. Nguy cơ nghẽn mạng (Broadcast Storm) và cách phòng

**Hiện tại:** chưa có bão vì mọi gói chuyển tiếp đều đi về Base (hình sao). **Ngay khi** bạn làm flooding tới láng giềng, nếu không có giảm nhiễu thì một gói có thể bị phát tới khoảng N×(N−1) lần trong cụm N máy (mỗi máy phát lại cho mọi láng giềng một lần). Heartbeat (đích `BROADCAST`, TTL 2) của mọi máy sẽ là nguồn nhiễu lớn nhất.

**Đề xuất (từ nhẹ đến nặng):**

1. **Bảng láng giềng** (nodeId, lần cuối thấy, RSSI nếu có, `hopsToBase`) cập nhật từ beacon/heartbeat. Không phát lại cho nút đã có trong `routeHistory`.
2. **Định tuyến theo gradient:** Base quảng bá `hops=0`; mỗi nút đặt `hopsToBase = min(láng giềng) + 1` và **chỉ chuyển tiếp lên phía gần Base hơn**. Chỉ khi không có gradient mới quay về flooding có TTL.
3. **Giảm nhiễu theo bộ đếm:** trước khi phát lại, chờ ngẫu nhiên 50–300 ms (jitter); nếu nghe thấy cùng `packetId` từ ≥ 2 láng giềng thì **hủy** phát lại.
4. **Ưu tiên hàng đợi:** `SOS_DATA` > `DISPATCH_CMD`/`ACK` > `STATUS_UPDATE` > `HEARTBEAT`; heartbeat không bao giờ được chuyển tiếp nhiều bước.
5. **Giới hạn tốc độ** theo nguồn và theo loại gói; hạn mức phát lại tổng.
6. **Dedup bền vững hơn:** cache dài hơn (≥ 60 giây, nên đồng bộ với Web), và lưu `packetId` gần đây vào Room để sống sót qua khởi động lại.
7. **TTL theo loại:** SOS 6–8 (cần tới Base), ACK/Dispatch theo `routeHistory` ngược, heartbeat 1.

> Các con số ở trên là điểm khởi đầu để đo thử, không phải chuẩn.

### 3.2. Hao pin (Battery Drain)

**Nguyên nhân hiện có trong code**

- Heartbeat mỗi 30 giây, **mỗi lần mở TCP mới**, thất bại thì thử 3 lần (đánh thức radio nhiều lần).
- GPS `HIGH_ACCURACY` 3 giây/lần, `setWaitForAccurateLocation(true)`.
- Không có chế độ năng lượng; "Ultra Battery Saver" chỉ đổi giao diện sang thang xám/đen ở màn hình relay, **không đổi tần suất phát, GPS hay quét**.
- Không có WakeLock/WifiLock có kiểm soát, không xin tắt tối ưu pin, không xử lý Doze.
- Wi-Fi Direct `discoverPeers` tốn năng lượng nếu để lặp (hiện chỉ gọi thủ công, nên **giữ cách này** và thêm chu kỳ thưa có kiểm soát).

**Đề xuất:** ba chế độ năng lượng (chi tiết ở Phụ lục B):

- **SOS ACTIVE:** nhịp phát lặp có backoff đến khi có ACK, GPS vừa phải, màn hình có thể tắt.
- **STANDBY (relay):** heartbeat thích ứng (thưa dần khi ổn định), tái dùng kết nối, dừng khi pin thấp.
- **SURVIVAL (nhiều ngày):** beacon SOS thưa (ví dụ vài phút/lần với backoff), GPS cân bằng/theo yêu cầu, tắt đèn/còi trừ khi bấm, hiển thị "ước tính còn X giờ".

Ngoài ra: gộp gói tin, dùng một kết nối cho nhiều gói, backoff có jitter thay vì thử dày, dùng `FusedLocationProvider` với `PRIORITY_BALANCED_POWER_ACCURACY` khi không phải lúc SOS, và **đo bằng công cụ thật** (Battery Historian, `adb shell dumpsys batterystats`) trước và sau mỗi thay đổi để có số liệu cho báo cáo.

### 3.3. Phát hiện láng giềng tự động, không cần gõ IP

Đi từ rẻ đến tốn công, nên triển khai theo **bậc thang** (thử bậc trên trước, tự rơi xuống bậc dưới):

1. **Mã QR từ Web** chứa `địa chỉ trạm, cổng, khóa thiết bị, tên trạm` (Web đã có sẵn thư viện QR). Quét một lần là xong, kể cả cấp khóa HMAC.
2. **Cổng mặc định (gateway):** nếu điện thoại nối vào Wi-Fi/hotspot do máy Base phát, địa chỉ cổng mạng thường chính là Base. Lấy từ `ConnectivityManager`/`LinkProperties` *(cần xác nhận theo thiết bị)*.
3. **NSD/mDNS (`NsdManager`):** Web quảng bá dịch vụ `_meshrescue._tcp`, điện thoại tự tìm. Phía Web cần một thư viện mDNS (ví dụ JmDNS).
4. **Địa chỉ chủ nhóm Wi-Fi Direct** (`groupOwnerAddress` bạn đã thu thập nhưng chưa dùng) khi đã tạo nhóm thật.
5. **Quét dải mạng cổng 8888** làm phương án cuối (tốn pin và thời gian).
6. **Phone-to-phone:** cân nhắc **Nearby Connections** hoặc **Wi-Fi Aware** thay cho tự dựng bằng Wi-Fi Direct, vì chúng lo sẵn khám phá/kết nối nhiều thiết bị *(kiểm tra yêu cầu Google Play Services và hỗ trợ phần cứng)*.

Luôn **lưu địa chỉ Base cuối cùng thành công** và hiển thị trạng thái "đã nối Base / mất Base" thay vì bắt người dùng đoán.

### 3.4. Trải nghiệm khi người dùng hoảng loạn

- **Gửi tối thiểu trước, bổ sung sau:** gói SOS đầu tiên chỉ cần `nodeId`, vị trí (kèm chất lượng), mức khẩn cấp, số người. Hồ sơ y tế gửi bằng `STATUS_UPDATE` ngay sau đó. Giảm thời gian đến byte đầu tiên.
- **Bấm giữ có chủ đích** (khoảng 2 giây, có rung và vòng đếm), tránh bấm nhầm; có **hủy trong vài giây** và nút **"Tôi đã an toàn"** (`CANCEL_SOS`/`SAFE_CHECKIN`).
- **Một màn hình trạng thái thật, dễ hiểu:**
  `Đang gửi… → Đã tới trạm (ACK) → Trạm đã xác minh → Đội cứu hộ đang tới (kèm ETA)`.
  Mỗi trạng thái phải **chỉ hiện khi có bằng chứng thật** (ACK), tuyệt đối không hiện "thành công" khi chưa chắc.
- **Thông báo SOS đang hoạt động** cố định (không vuốt đi được) kèm nút hủy.
- **Vị trí trung thực:** hiện "Đang tìm GPS…", độ chính xác và tuổi vị trí; nếu không có fix, gửi `hasFix=false` và vị trí cuối cùng kèm tuổi, **không bao giờ bịa tọa độ**.
- **Chữ lớn, tương phản cao, ít bước,** hoạt động một tay; ngôn ngữ đơn giản; biểu tượng kèm chữ; không phụ thuộc màu.
- **Hướng dẫn ngắn khi chờ** (giữ bình tĩnh, tiết kiệm pin, đứng ở nơi cao/thoáng), và **chế độ tiết kiệm** tự đề xuất khi pin thấp.
- **Quyền:** giải thích trước khi xin, xử lý từ chối, hiện cảnh báo rõ nếu thiếu quyền quan trọng (vị trí, thông báo).

### 3.5. Điều phối 2 chiều với Web C2

**Vấn đề gốc:** Web cố gửi lệnh tới một host:port cố định, trong khi điện thoại ở sau hotspot, IP thay đổi, có thể không lắng nghe, và ngủ khi tắt màn hình.

**Đề xuất chủ đạo: giao thức yêu cầu–phản hồi trên cùng kết nối TCP (piggyback)** (chi tiết ở `INTEGRATION_CONTRACT.md`):

1. Điện thoại gửi một dòng JSON (SOS/heartbeat/status) và **giữ kết nối mở vài giây để đọc phản hồi**.
2. Base trả lại: một `ACK(RECEIVED)` cho gói vừa nhận, rồi **0..N `DISPATCH_CMD` đang chờ** cho `nodeId` đó, rồi đóng kết nối.
3. Heartbeat đóng vai trò **"hỏi hộp thư"** (poll). Không cần điện thoại mở cổng lắng nghe, đi xuyên NAT/hotspot, và tiết kiệm pin hơn việc duy trì server nền.
4. Điện thoại xác nhận lệnh bằng `ACK(ACCEPTED)` (tự động khi nhận) và `ACK(COMPLETED)` hoặc `STATUS_UPDATE` (do người dùng/đội xác nhận).
5. Mọi thao tác **idempotent**: gửi lại cùng `packetId` thì Base trả lại ACK, không tạo sự cố mới.
6. Lệnh có `dispatchId` và nội dung có cấu trúc (`teamName`, `etaMinutes`, `instruction`) thay vì chuỗi tự do.

Server socket phía Android vẫn giữ làm **tối ưu hóa tùy chọn** (nhận đẩy trực tiếp khi có thể).

### 3.6. Bảo mật và riêng tư

- **HMAC-SHA256 bằng khóa thiết bị** cấp qua QR, thay cho SHA-256 trần; từ chối checksum rỗng; chống phát lại bằng `seq` tăng dần theo nguồn.
- **Không để relay đọc được dữ liệu y tế:** ở mức đồ án có thể chỉ cần đánh dấu "dữ liệu dạng rõ" là hạn chế đã biết; nâng cao thì mã hóa payload cho khóa công khai của Base (relay chỉ thấy phần đầu gói để định tuyến).
- Lưu khóa bằng **Android Keystore**; `allowBackup=false` hoặc loại trừ DataStore/Room khỏi sao lưu.
- Bỏ quyền không cần; xin quyền đúng lúc và có giải thích.
- Nếu bật R8, thêm quy tắc giữ các lớp mô hình Gson.

### 3.7. Chất lượng mã và kiểm thử

- Thêm `ViewModel` cho từng màn hình, tách `MeshRepository` thành các thành phần nhỏ (xem Phụ lục A).
- Ném lại `CancellationException`; ghi log có cấu trúc (`Log` + vòng đệm hiển thị ở Relay Monitor); không `catch` rỗng.
- Unit test `RoutingEngine`, `PacketFactory`, canonical checksum (dùng test vector chung); instrumented test với **Fake Base (ServerSocket giả)** để kiểm tra đường đi hai chiều mà không cần Web.
- Đổi `fallbackToDestructiveMigration` thành migration thật và bật `exportSchema`.

---

## 4. LỘ TRÌNH THỰC HIỆN VÀ TIÊU CHÍ HOÀN THÀNH

**Nguyên tắc:** hai bạn đã thống nhất làm xong Web trước. Các việc ở M0–M1 **không phụ thuộc Web** hoặc chỉ phụ thuộc hợp đồng chung nên có thể làm song song nếu muốn, còn M2 trở đi cần Web có ACK/inbox. Ước lượng công: **S** ≤ 1 ngày · **M** 2–3 ngày · **L** 4–7 ngày (thô, cho người làm một mình).

| GĐ | Mục tiêu | Công việc chính | Kiểm chứng |
|---|---|---|---|
| **M0** | Thống nhất nền | Cùng Đạt chốt `INTEGRATION_CONTRACT.md` và test vector. Dựng **Fake Base** (ServerSocket giả) để test độc lập. | Test canonical checksum xanh; Fake Base nhận/phản hồi đúng hợp đồng |
| **M1** | Vá lỗi an toàn (S1) | Bỏ tọa độ giả, thêm `hasFix/accuracyM/fixAgeMs` (M-01); `nodeId` duy nhất, lưu bền (M-03); `packetId` UUID đầy đủ (M-16); sửa `cleanup()` không dọn tài nguyên Service (M-06); thêm `phoneNumber/bloodType/medicalNotes` theo đúng thứ tự (M-07); từ chối checksum rỗng (M-11). | Test: không có GPS thì gửi `hasFix=false`; hai nodeId khác nhau không bỏ gói của nhau |
| **M2** | SOS bền và trung thực | Đọc phản hồi, `ACK(RECEIVED)` mới được coi là "đã tới trạm" (M-02); outbox bền vững + **tự gửi lại với backoff có jitter** tới khi có ACK (M-05); `WorkManager`/`NetworkCallback` thay nút retry thủ công; trạng thái người dùng thấy đúng bằng chứng; sửa `PrintWriter`/`CancellationException`/log (M-10, M-14). | Mất mạng rồi có lại vẫn tự gửi; UI không bao giờ báo "thành công" khi chưa có ACK |
| **M3** | Điều phối 2 chiều | Piggyback: nhận `DISPATCH_CMD` trong phản hồi heartbeat/SOS; `ACK(ACCEPTED/COMPLETED)`; hiển thị ETA/đội; idempotent; hộp thư lệnh bền vững. | Emulator/Web gửi lệnh, điện thoại nhận khi tắt màn hình, ACK về Web |
| **M4** | Khám phá và pin | Bậc thang phát hiện Base (QR → gateway → NSD…) (3.3); ba chế độ năng lượng và đo pin (3.2); GPS thích ứng; WakeLock/WifiLock có kiểm soát; xin tắt tối ưu pin đúng cách; hiển thị trạng thái nối Base. | Số liệu pin trước/sau; không còn gõ IP |
| **M5** | Mesh thật và bảo mật | Bảng láng giềng, gradient, giảm nhiễu (3.1); phone-to-phone (Nearby/Wi-Fi Aware hoặc Wi-Fi Direct nhóm thật); HMAC + chống phát lại (3.6); chặn sao lưu dữ liệu y tế; ViewModel/DI/tách Repository; bộ test đầy đủ. | Kịch bản 3 máy, 2 bước nhảy; không bão; test bảo mật |

### 4.1. Tiêu chí hoàn thành ("Mobile sẵn sàng ghép sâu và demo")

- [ ] Không còn đường nào gửi tọa độ do app tự bịa; `hasFix=false` được Web hiển thị đúng.
- [ ] `nodeId` duy nhất, bền qua khởi động lại; `packetId` là UUID.
- [ ] UI chỉ hiện "đã tới trạm" khi nhận `ACK(RECEIVED)` thật.
- [ ] SOS tự gửi lại với backoff cho tới khi có ACK, kể cả sau khi mất rồi có lại mạng và sau khi tiến trình bị kill.
- [ ] Nhận được lệnh điều phối khi màn hình tắt và gửi lại ACK; Web cập nhật trạng thái.
- [ ] Không cần gõ IP trong luồng chính (QR hoặc tự phát hiện).
- [ ] Có ba chế độ năng lượng và số đo pin trong báo cáo.
- [ ] Vòng đời Service đúng: vuốt tắt app không làm Service "chết im lặng" mà vẫn báo trạng thái thật.
- [ ] Test hợp đồng (vector chung) + test `RoutingEngine` + test Fake Base chạy xanh.
- [ ] Hạn chế còn lại được ghi rõ trong báo cáo.

### 4.2. Thứ tự rút gọn nếu thời gian gấp

1. **M-01** (bỏ tọa độ giả) → 2. **M-03** (`nodeId` duy nhất) → 3. **M-02 + M-05** (ACK thật và gửi lại) → 4. **M-06** (vòng đời Service) → 5. **M-07** (hồ sơ y tế) → 6. **M3** (nhận lệnh + ACK) → 7. QR cấu hình + chế độ năng lượng → 8. giảm nhiễu/mesh thật nếu còn thời gian (còn không thì ghi vào "Hướng phát triển").

---

## 5. LÀM SAO ĐỂ WEB VÀ MOBILE GHÉP LẠI ỔN ĐỊNH, THỐNG NHẤT

**Câu trả lời thẳng:** hai file đánh giá riêng lẻ **chưa đủ** để đảm bảo ghép ổn định. Chúng chỉ là bản đồ; nếu mỗi người tự đọc file của mình rồi tự quyết chi tiết, hai bên sẽ lệch ở đúng những chỗ khó (ACK nghĩa là gì, ai lấy lệnh khi nào, checksum canonical). Cần thêm:

1. **Một nguồn sự thật chung duy nhất:** `INTEGRATION_CONTRACT.md` (đặt trong `docs/` của repo chung hoặc cả hai repo). Mọi chi tiết giao tiếp chỉ định nghĩa ở đó; hai file đánh giá chỉ *tham chiếu*.
2. **Test hợp đồng chạy ở cả hai phía** với cùng file test vector: Web có **Mobile Emulator**, Android có **Fake Base**. Hai bên đều chứng minh được "tuân thủ hợp đồng" mà không cần chờ nhau.
3. **Quy trình đổi hợp đồng:** chỉ đổi qua một thay đổi có đánh số phiên bản, cả hai cùng duyệt, cập nhật test vector trước khi sửa code. Không "tiện tay" thêm field.
4. **Cổng tích hợp:** chỉ ghép máy thật khi Web đạt DoD của Web và Android đạt DoD M1–M3, đồng thời Emulator ↔ Web và Fake Base ↔ Android đều xanh.
5. **Họp ngắn hằng tuần** (hoặc cuối mỗi giai đoạn) để rà "sổ quyết định" trong hợp đồng.

---

## PHỤ LỤC A — KIẾN TRÚC ĐÍCH ĐỀ XUẤT

```
ui/
  screens/…                  (Composable thuần, nhận state + callback)
  viewmodel/                 SosViewModel, RelayViewModel, HistoryViewModel, OnboardingViewModel
domain/
  model/                     MeshPacket, SosState (máy trạng thái), DispatchOrder
  routing/                   RoutingEngine, NeighborTable, RebroadcastPolicy
  usecase/                   SendSosUseCase, AcknowledgeDispatchUseCase, RetryOutboxUseCase
data/
  local/                     Room (incidents, outbox, seen_packets), DataStore
  repository/                SosRepository, OutboxRepository, DispatchRepository, ProfileRepository
network/
  transport/                 MeshTransport (interface), TcpTransport (request-response)
  discovery/                 QrProvisioner, NsdDiscovery, GatewayResolver, (Nearby/WifiAware)
  p2p/                       WifiDirectManager (nếu giữ)
service/
  MeshForegroundService      (chỉ điều phối; không dọn tài nguyên khi Activity đóng)
power/
  PowerProfile               (SOS_ACTIVE / STANDBY / SURVIVAL), WakeLock helper
di/                          Hilt hoặc Koin (hoặc DI thủ công có kiểm soát vòng đời)
```

Nguyên tắc: `Activity`/`Composable` không sở hữu tài nguyên mạng; chỉ **Service/Application** sở hữu, và có **một chủ sở hữu duy nhất** cho server, heartbeat, P2P.

---

## PHỤ LỤC B — HỒ SƠ NĂNG LƯỢNG THEO CHẾ ĐỘ (điểm khởi đầu để đo thử)

| Thành phần | SOS ACTIVE | STANDBY (relay) | SURVIVAL (nhiều ngày) |
|---|---|---|---|
| Phát SOS | Lặp có backoff tới khi có ACK (khởi đầu vài chục giây, giãn dần) | Không | Beacon thưa (vài phút/lần), vẫn bền |
| GPS | Cân bằng–cao khi bấm SOS, sau đó giảm | Cân bằng, thưa | Theo yêu cầu/rất thưa, dùng vị trí cuối kèm tuổi |
| Heartbeat | Có (làm hộp thư lệnh) | Thích ứng thưa dần | Tắt hoặc rất thưa |
| Quét láng giềng | Khi cần | Chu kỳ thưa có kiểm soát | Tắt |
| Đèn/còi | Theo người dùng | Tắt | Chỉ khi bấm |
| Màn hình | Có thể tắt | Tắt | Tắt, hiện ước tính pin còn lại |
| Tự chuyển chế độ | — | Khi pin thấp | Khi pin rất thấp |

Đo bằng `adb shell dumpsys batterystats` / Battery Historian ở **từng chế độ** và ghi số liệu vào báo cáo.

---

## PHỤ LỤC C — ĐOẠN MÃ PHÁC THẢO (chưa biên dịch, chỉ minh họa ý tưởng)

**1. `nodeId` duy nhất, bền (thay cho `Build.MODEL`)**

```kotlin
// Lưu trong DataStore lần đầu chạy; không dùng ANDROID_ID để tránh vấn đề riêng tư.
suspend fun getOrCreateNodeId(prefs: UserPreferences): String {
    val existing = prefs.nodeIdFlow.first()
    if (!existing.isNullOrBlank()) return existing
    val id = "NODE_" + UUID.randomUUID().toString().replace("-", "").take(8).uppercase()
    prefs.saveNodeId(id)
    return id
}
```

**2. Gửi và đọc phản hồi (piggyback), không nuốt hủy coroutine**

```kotlin
suspend fun sendAndReceive(host: String, port: Int, packet: MeshPacket): SendResult =
    withContext(Dispatchers.IO) {
        try {
            Socket().use { s ->
                s.connect(InetSocketAddress(host, port), 5_000)
                s.soTimeout = 5_000
                val out = s.getOutputStream().bufferedWriter(Charsets.UTF_8)
                out.write(packet.toJson()); out.write("\n"); out.flush()   // lỗi ghi sẽ ném IOException
                val reader = s.getInputStream().bufferedReader(Charsets.UTF_8)
                val replies = mutableListOf<MeshPacket>()
                try {
                    while (replies.size < 16) {
                        val line = reader.readLine() ?: break
                        MeshPacket.fromJson(line)?.let(replies::add)
                    }
                } catch (_: SocketTimeoutException) { /* hết thời gian chờ phản hồi */ }
                SendResult(success = replies.any { it.isAckFor(packet.packetId) }, replies = replies)
            }
        } catch (e: CancellationException) {
            throw e                       // quan trọng: không nuốt hủy
        } catch (e: IOException) {
            SendResult(success = false, error = e.message ?: "IO")
        }
    }
```

**3. Dừng `cleanup()` kéo theo Service**

```kotlin
// MainActivity.onDestroy: chỉ giải phóng thứ do Activity sở hữu (đèn, còi).
override fun onDestroy() {
    flashlightManager.shutdown()
    sirenPlayer.release()
    // KHÔNG gọi repository.cleanup(): server/heartbeat/P2P thuộc MeshForegroundService.
    super.onDestroy()
}
```

**4. Không bao giờ bịa vị trí**

```kotlin
val loc = location
val locationPayload = if (loc != null) {
    Location(loc.latitude, loc.longitude, hasFix = true, accuracyM = loc.accuracy, fixAgeMs = loc.ageMs)
} else {
    lastKnown?.let { Location(it.latitude, it.longitude, hasFix = false, accuracyM = it.accuracy, fixAgeMs = it.ageMs) }
        ?: Location(null, null, hasFix = false)      // Web hiển thị "chưa rõ vị trí"
}
```

---

## PHỤ LỤC D — CHECKLIST KIỂM THỬ

**Đơn vị (JVM)**
- [ ] `RoutingEngine`: TTL hết, trùng, tự phát, đích Base, đích nút khác, broadcast, checksum sai/rỗng.
- [ ] Canonical checksum khớp **test vector chung** (v0 và v1).
- [ ] `PacketFactory`: `packetId` UUID, `nodeId` đúng định dạng.
- [ ] Máy trạng thái SOS: các chuyển trạng thái hợp lệ/không hợp lệ.

**Tích hợp với Fake Base (instrumented)**
- [ ] SOS → nhận `ACK(RECEIVED)` → UI chuyển "Đã tới trạm".
- [ ] Fake Base im lặng hoặc đóng kết nối → UI **không** báo thành công; tự gửi lại có backoff.
- [ ] Fake Base trả `DISPATCH_CMD` trong phản hồi → hiển thị overlay → `ACK(ACCEPTED)`.
- [ ] Gửi lại cùng `packetId` → Base trả lại ACK (idempotent), không nhân đôi.
- [ ] Mất mạng, bật lại → tự gửi hàng đợi; kill tiến trình rồi mở lại → hàng đợi còn.
- [ ] Không có GPS → gói có `hasFix=false`, không có tọa độ bịa.

**Thiết bị thật**
- [ ] Vuốt tắt app: Service vẫn chạy và báo trạng thái thật *(cần xác nhận Android 14/15)*.
- [ ] Màn hình tắt 30+ phút: vẫn nhận lệnh/heartbeat theo chế độ năng lượng.
- [ ] Hai máy cùng model không bỏ gói của nhau.
- [ ] Đo pin theo từng chế độ; ghi lại kết quả.
- [ ] Từ chối từng quyền: ứng dụng hướng dẫn thay vì hỏng thầm lặng.

**Bảo mật**
- [ ] Gói sai HMAC/checksum rỗng bị từ chối; `seq` lùi bị từ chối.
- [ ] Dữ liệu y tế không nằm trong sao lưu đám mây.

---

*Tài liệu này là bản phân tích và đề xuất dựa trên mã nguồn tại thời điểm đọc; cần kiểm chứng trên thiết bị thật trước khi kết luận về hành vi nền tảng. Mọi thay đổi liên quan giao tiếp với Web phải đi qua `INTEGRATION_CONTRACT.md`.*

# Đặc tả Giao thức MeshPacket Canonical v1.0

## 1. Giới thiệu

Tài liệu này chuẩn hóa cấu trúc gói tin `MeshPacket` phiên bản 1.0 (v1) dùng chung giữa:
- **Command Base Station** (Java / JavaFX Desktop)
- **Field Mesh Nodes** (Kotlin Android & Desktop Simulator)

Mọi bản tin truyền qua mạng Socket P2P / Mesh đều bắt buộc tuân theo định dạng JSON chuẩn `snake_case` được định nghĩa dưới đây.

---

## 2. Cấu trúc Gói tin Toàn cục (`MeshPacket`)

Mỗi gói tin là một đối tượng JSON gồm 12 trường canonical sau:

| Trường JSON | Kiểu dữ liệu | Bắt buộc | Mô tả & Ràng buộc kiểm tra |
|---|---|---|---|
| `packet_id` | String (UUID v4) | Có | Mã định danh duy nhất toàn cầu (RFC 4122). Định dạng regex: `^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$`. |
| `protocol_version` | String | Có | Phiên bản giao thức, cố định là `"1.0"`. |
| `packet_type` | String (Enum) | Có | Một trong 5 loại: `SOS_BROADCAST`, `DISPATCH_COMMAND`, `ROUTE_DISCOVERY`, `ACK`, `HEARTBEAT`. |
| `source_node_id` | String | Có | ID node khởi tạo gói tin ban đầu (1-64 ký tự, không null/rỗng). |
| `destination_node_id` | String | Có | ID node đích đến cuối cùng (`BASE_STATION`, `BROADCAST`, hoặc ID node cụ thể). Độ dài 1-64 ký tự. |
| `sender_hop_id` | String | Có | ID node vừa chuyển tiếp gói tin ở bước nhảy hiện tại (1-64 ký tự). Cập nhật tại mỗi relay. |
| `ttl` | Integer | Có | Time-To-Live: số bước nhảy còn lại (0..64). Giảm 1 tại mỗi relay. Gói tin bị drop khi `ttl <= 1`. |
| `hop_count` | Integer | Có | Đếm số bước nhảy gói tin đã qua (>= 0). Bắt buộc `hop_count >= route_history.size()`. |
| `timestamp` | Long | Có | Thời điểm khởi tạo gói tin (epoch milliseconds > 0). Nằm trong khoảng hợp lệ [now - 365 ngày, now + 24 giờ]. |
| `payload` | Object | Có | Nội dung nghiệp vụ chi tiết của gói tin (xem Mục 3). |
| `route_history` | Array[String] | Có | Mảng tuần tự các node ID đã chuyển tiếp gói tin: `["NODE_A", "NODE_B", ...]`. Không null; từng phần tử 1-64 ký tự. |
| `checksum` | String (SHA-256) | Có | Bắt buộc đúng 64 ký tự hexadecimal chữ thường bảo vệ toàn bộ metadata và payload. |

---

## 3. Cấu trúc `payload`

### 3.1. Các trường trong `payload`

| Trường JSON | Kiểu dữ liệu | Bắt buộc cho | Ràng buộc giá trị |
|---|---|---|---|
| `sender_name` | String | SOS | Độ dài từ 1 đến 100 ký tự. Không được rỗng hoặc chỉ có khoảng trắng. |
| `alert_type` | String (Enum) | SOS | Chỉ chấp nhận đúng 3 giá trị: `MEDICAL`, `FLOOD_TRAPPED`, `LANDSLIDE`. |
| `message` | String | SOS, DISPATCH | Nội dung thông điệp chi tiết. Độ dài từ 0 đến 500 ký tự. |
| `victim_count` | Integer | SOS | Số lượng nạn nhân (>= 0). |
| `severity` | String (Enum) | SOS, DISPATCH | Một trong 3 mức: `CRITICAL`, `HIGH`, `MEDIUM`. |
| `location` | Object | SOS | Tọa độ GPS sự cố (xem mục 3.2). |
| `ack_for_packet_id` | String (UUID) | ACK | **Machine-readable**: `packet_id` của gói tin được xác nhận (UUID hợp lệ). |
| `discovery_info` | Object/String | ROUTE_DISCOVERY | Thông tin phụ trợ khám phá tuyến đường. |

### 3.2. Cấu trúc `location`

Mọi giá trị số trong `location` bắt buộc là **số hữu hạn (finite)**; hệ thống từ chối `NaN` và `Infinity`.

| Trường JSON | Kiểu | Đơn vị | Khoảng hợp lệ | Mô tả |
|---|---|---|---|---|
| `latitude` | Double | Độ | -90.0 đến +90.0 | Vĩ độ GPS chuẩn WGS84. |
| `longitude` | Double | Độ | -180.0 đến +180.0 | Kinh độ GPS chuẩn WGS84. |
| `altitude` | Double | Mét | -500.0 đến +10000.0 | Cao độ so với mực nước biển. |
| `accuracy` | Double | Mét | >= 0.0 | Bán kính sai số định vị GPS. |

---

## 4. Thuật toán Canonicalization Độc lập Java & Kotlin

Để đảm bảo SHA-256 Checksum sinh ra từ Kotlin (Android) và Java (Desktop) khớp 100% từng byte mà không phụ thuộc vào thư viện JSON ngẫu nhiên và loại bỏ triệt để nguy cơ Delimiter Collision:

### 4.1. Cấu trúc Chuỗi Canonical (Canonical JSON)
Toàn bộ gói tin (ngoại trừ trường `checksum`) được mã hóa thành một đối tượng **Canonical JSON** compact duy nhất không chứa khoảng trắng thừa (`\s`).

Thứ tự 11 trường top-level cố định theo bảng chữ cái ASCII/Unicode code-point nghiêm ngặt:
1. `"destination_node_id"`: String (JSON-escaped)
2. `"hop_count"`: Integer (>= 0)
3. `"packet_id"`: String (UUID v4)
4. `"packet_type"`: String (Enum)
5. `"payload"`: Canonical JSON Object (xem mục 4.2)
6. `"protocol_version"`: String (`"1.0"`)
7. `"route_history"`: Canonical JSON Array các node ID: `["NODE_A","NODE_B"]` hoặc `[]`
8. `"sender_hop_id"`: String (JSON-escaped)
9. `"source_node_id"`: String (JSON-escaped)
10. `"timestamp"`: Long (Epoch milliseconds)
11. `"ttl"`: Integer (0..64)

Ví dụ chuỗi Canonical JSON top-level:
```json
{"destination_node_id":"BASE_STATION","hop_count":0,"packet_id":"...","packet_type":"SOS_BROADCAST","payload":{...},"protocol_version":"1.0","route_history":[],"sender_hop_id":"NODE_A","source_node_id":"NODE_A","timestamp":1771240000000,"ttl":5}
```

### 4.2. Quy tắc Canonical hóa từng thành phần
1. **`canonical_route_history`**:
   - Mảng JSON giữ nguyên thứ tự các bước nhảy.
   - Nếu rỗng hoặc null: `"[]"`.
   - Nếu có phần tử: `["NODE_A","NODE_B"]` (không có khoảng trắng sau dấu phẩy).
2. **`canonical_payload`**:
   - Chỉ chứa các trường không null (trừ `victim_count` luôn xuất hiện).
   - Sắp xếp các khóa (keys) theo thứ tự bảng chữ cái ASCII nghiêm ngặt:
     1. `ack_for_packet_id` (nếu có)
     2. `alert_type` (nếu có)
     3. `discovery_info` (nếu có — được chuẩn hóa đệ quy theo mục 4.3)
     4. `location` (nếu có, sắp xếp: `accuracy`, `altitude`, `latitude`, `longitude`)
     5. `message` (nếu có)
     6. `sender_name` (nếu có)
     7. `severity` (nếu có)
     8. `victim_count`
3. **Chuẩn hóa đệ quy `discovery_info` (Recursive Canonical JSON)**:
   - Áp dụng cho cấu trúc tự do (Map, List, Primitive, Nested Objects):
     - **Object / Map**: Toàn bộ keys bắt buộc sắp xếp tăng dần theo mã Unicode/ASCII (`compareTo`). Phân cách bằng dấu phẩy, không khoảng trắng.
     - **Array / List**: Giữ nguyên thứ tự phần tử.
     - **String**: JSON-escaped theo chuẩn RFC 8259 (`\"`, `\\`, `\n`, `\r`, `\t`, `\b`, `\f`, `\uXXXX`).
     - **Number**: Định dạng deterministic (xem mục 4.4).
     - **Boolean / Null**: Biểu diễn chính xác là `true`, `false`, `null`.
     - **Validation**: Từ chối ngay lập tức số `NaN`, `Infinity` hoặc kiểu đối tượng không hỗ trợ bằng `IllegalArgumentException`.
4. **Quy tắc định dạng số**:
   - Số nguyên top-level (`ttl`, `hop_count`, `timestamp`, `victim_count`): Định dạng thập phân tiêu chuẩn (`Long.toString()`, `Integer.toString()`).
   - Số thực trong `location` (`latitude`, `longitude`, `altitude`, `accuracy`): Chuẩn hóa `-0.0` và `0.0` thành `"0.0"`. Các số thực khác định dạng theo IEEE 754 (`Double.toString()`). Tuyệt đối từ chối `NaN` và `Infinity`.
   - Số trong `discovery_info`: Để đảm bảo tính độc lập tuyệt đối với runtime subtype của Number (`Integer`, `Long`, `Double`, `BigDecimal`), không làm mất độ chính xác đối với số nguyên lớn vượt quá $2^{53}$ (như 9007199254740992 và 9007199254740993), và phân biệt rõ ràng các biểu diễn số thực khác nhau (như 0.1 và 0.10000000000000001):
     - **Không sử dụng** `JsonPrimitive.getAsDouble()` hoặc `Number.doubleValue()` làm biểu diễn canonical duy nhất cho arbitrary JSON numbers.
     - **Parse từ chuỗi số raw (raw numeric string)** bằng `BigDecimal` an toàn.
     - **Chuẩn hóa zero**: Giá trị toán học bằng 0 (ví dụ `0`, `-0`, `0.0`, `-0.0`, `0.00`) luôn chuẩn hóa thành `"0"`.
     - **Loại bỏ số 0 ở phần thập phân dư thừa**: Thực hiện `stripTrailingZeros()` và xuất bằng `toPlainString()` để không phụ thuộc vào scale dư thừa (ví dụ `5.0` thành `"5"`, `0.850` thành `"0.85"`), đồng thời giữ nguyên định dạng thập phân rõ ràng mà không sinh ký hiệu khoa học e/E không xác định.
     - **Không xảy ra collision**: Hai giá trị toán học khác nhau không bao giờ tạo cùng chuỗi canonical.
     - **Giới hạn an toàn**: Tuyệt đối từ chối `NaN`, `Infinity`, chuỗi có độ dài vượt quá 100 ký tự, hoặc số có bậc số mũ (scale) và số lượng chữ số có nghĩa (precision) vượt quá $\pm 100$ (chống tràn số như `1e400` và tấn công từ chối dịch vụ DoS).
5. **Mã hóa và Checksum SHA-256**:
   - Chuỗi Canonical JSON được chuyển thành mảng byte UTF-8 trước khi băm SHA-256.
   - Kết quả SHA-256 (32 bytes) được chuyển thành chuỗi 64 ký tự hex **chữ thường**.
   - **Chính sách kiểm tra**: Protocol v1 TUYỆT ĐỐI KHÔNG CHẤP NHẬN payload-only checksum. Bất kỳ sự can thiệp nào vào metadata hay payload đều dẫn đến SHA-256 không khớp và bị từ chối ngay lập tức.

---

## 5. Bảng Vector Kiểm thử Cố định (Known-Answer Test Vectors)

| Fixture | Packet Type | Packet ID | Checksum SHA-256 Cố định |
|---|---|---|---|
| `sos_broadcast.json` | SOS_BROADCAST | `7b8f9e61-6d2c-4f10-9b34-8c8a2b53b811` | `cd6cbc65ac85b049082c25193cdf1e15269bea578fa59c6cd2e4d41f02bcc009` |
| `dispatch_command.json` | DISPATCH_COMMAND | `1c9a8f23-4e5b-4c6d-8f90-123456789abc` | `d9349c2c98d5718de0740a93f419e504282316b3d41756f9c4ad7a2a839c9488` |
| `route_discovery.json` | ROUTE_DISCOVERY | `3d5f7a92-8c1e-4f3b-9a45-67890abcdef1` | `465cc4eb0ff7453710eba6a70dc8d1edb44ac28c81250f18c7fa0a3a1ba282ad` |
| `ack.json` | ACK | `4e6f8b03-9d2f-4a4c-8b56-78901bcdef23` | `8dea238130aed9ccb562f80beeb5d7c23c4b824ff3a0f2078ee1e47a96169790` |
| `heartbeat.json` | HEARTBEAT | `5f7a9c14-0e3a-4b5d-9c67-89012cdef345` | `25dd683bb69a31a9cf627d2ff4891d57509b44bad90380aaf75b28f835bdaa34` |
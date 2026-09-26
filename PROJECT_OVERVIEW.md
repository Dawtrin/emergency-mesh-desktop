# Emergency Mesh Rescue — Desktop Demo Overview

## Mục tiêu hiện tại

Dự án là bản demo Java/JavaFX chạy trên máy tính để mô phỏng luồng cứu hộ
qua TCP/IP nội bộ:

```text
Victim desktop -> Relay desktop -> Base Station desktop
Base Station desktop -> Relay desktop -> Victim desktop -> ACK
```

Buổi thực hành dự kiến ngày **29/09/2026** có thể dùng Windows host làm Base
Station và Ubuntu VirtualBox làm Relay + Victim, hoặc hai laptop cùng Wi-Fi/LAN.
Internet không cần sau khi đã cài JDK và build, nhưng các máy phải liên lạc TCP
được với nhau trong cùng mạng nội bộ.

## Chức năng đã có

- Gói tin canonical v1 có checksum, TTL, route history và chống xử lý lặp SOS.
- Relay chuyển tiếp SOS/Dispatch bằng tuyến TCP cấu hình rõ ràng.
- Base Station hiển thị SOS, lưu SQLite và quản lý Dispatch outbox có retry.
- Victim tự gửi ACK giao thức khi nhận Dispatch; Base chỉ xem là giao hàng khi
  trạng thái chuyển `ACKED`, không chỉ là TCP `SENT`.
- Relay gửi heartbeat đã kiểm tra checksum. Base chọn relay online có tải thấp
  hơn cho đúng Victim, và retry có thể chuyển sang relay dự phòng.
- Bản đồ desktop dùng MBTiles cục bộ; có fixture nhỏ để kiểm tra kỹ thuật.

## Không phải phạm vi demo

- Không phải radio mesh thật, không tự dựng mạng khi các máy không có đường
  mạng nội bộ.
- Không xác minh Wi-Fi Direct, Bluetooth mesh, GPS thực địa hay vùng bản đồ
  thực tế.
- Android không là phần cần chạy trong demo desktop hiện tại.
- Failover áp dụng cho chiều Dispatch từ Base dựa trên heartbeat. SOS của
  Victim vẫn dùng relay upstream đã cấu hình.

## Chạy demo

1. Cài JDK 21 trên từng máy và build source trên chính hệ điều hành đó.
2. Chạy Base Station Windows trên TCP `18888`.
3. Chạy Relay Ubuntu trên TCP `18002`; Victim cùng Ubuntu dùng loopback
   `127.0.0.1:18001`.
4. Kiểm tra SOS `Victim -> Relay -> Base`, sau đó Dispatch và ACK chiều ngược.
5. Tùy chọn chạy Relay-02 trên `18003`, tắt Relay-01, rồi quan sát Dispatch
   retry qua relay còn online.

Lệnh chính xác, firewall, VirtualBox và checklist có trong
[`docs/demo/two-machine-vbox-test.md`](docs/demo/two-machine-vbox-test.md).
File cấu hình mẫu nằm trong [`config/`](config/).

## Xác minh tự động

Chạy bằng JDK 21:

```powershell
.\mvnw.cmd clean package
```

Test socket tích hợp chạy ba TCP server động, kiểm tra SOS, Dispatch, ACK và
SQLite. Test failover mô phỏng relay chính tắt và xác nhận Dispatch/ACK đi qua
relay dự phòng. Các test này không thay thế việc thực hành giữa Windows và
Ubuntu thật.

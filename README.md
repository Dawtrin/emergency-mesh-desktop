# Emergency Mesh Rescue — Desktop Command Base Station & Mesh Simulator

Hệ thống điều hành cứu nạn khẩn cấp ngoại tuyến phục vụ trạm chỉ huy dã chiến (Java + JavaFX Desktop) kết nối mạng Mesh cứu nạn di động.

## 1. Yêu cầu môi trường

- **Java Development Kit**: JDK 21 LTS trở lên (ví dụ: Eclipse Temurin 21, Microsoft OpenJDK 21, Oracle JDK 21).
- Biến môi trường `JAVA_HOME` trỏ tới thư mục cài đặt JDK 21.
- Dự án tích hợp sẵn **Maven Wrapper (`mvnw.cmd` / `mvnw`)**, không yêu cầu cài đặt sẵn Maven toàn cục. Dự án có Maven Enforcer chặn các bản build chạy trên JDK dưới 21.

## 2. Kiểm tra môi trường & Biên dịch

### Kiểm tra phiên bản toolchain:
```powershell
.\mvnw.cmd --version
```
(Yêu cầu: `Java version: 21` hoặc mới hơn).

### Chạy kiểm thử tự động (Unit & Contract Tests):
```powershell
.\mvnw.cmd clean test
```

### Đóng gói ứng dụng (Tạo Fat JARs):
```powershell
.\mvnw.cmd package
```
Lệnh trên sẽ sinh ra 2 file thực thi độc lập trong thư mục `target/`:
1. `target/BaseStationServer-jar-with-dependencies.jar` (Trạm chỉ huy Base Station GUI)
2. `target/MeshNodeClient-jar-with-dependencies.jar` (Node giả lập Victim / Relay)

---

## 3. Hướng dẫn chạy các ứng dụng mô phỏng

### Preflight an toàn trước khi demo

Profile LAN mẫu được version-control tại
[`config/demo-profile.properties`](config/demo-profile.properties). Sao chép/chỉnh
IP hotspot và `map.file` cho buổi demo (không đặt secret vào file này), sau đó chạy
preflight **trước** khi mở ba ứng dụng:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\preflight-demo.ps1
```

Lệnh chỉ kiểm tra JDK, JAR, cổng, host và MBTiles; nó không tạo firewall rule hay
thay đổi Windows. Khi dùng LAN, hãy tự tạo inbound TCP rule cho các port demo chỉ
trên Windows **Private** profile. `SENT` là TCP write thành công, còn `ACKED` chỉ
xuất hiện sau ACK có correlation hợp lệ từ Victim.

Hướng dẫn demo ba process trên một laptop và ba máy tính cùng Wi-Fi/hotspot nằm
tại [`docs/demo/desktop-topology.md`](docs/demo/desktop-topology.md). Internet
không cần thiết; các ứng dụng trao đổi TCP qua IPv4 nội bộ. Đây là desktop demo,
chưa phải mesh điện thoại/Wi-Fi Direct.

### 3.1. Chạy Trạm chỉ huy Trung tâm (Base Station Server - Port 8888)

**Cách 1: Khởi chạy trực tiếp qua JavaFX Plugin:**
```powershell
.\mvnw.cmd javafx:run
```

**Cách 2: Khởi chạy từ Fat JAR:**
```powershell
java -jar target\BaseStationServer-jar-with-dependencies.jar --id BASE-01 --bind-host 127.0.0.1 --bind-port 18888 --relay-host 127.0.0.1 --relay-port 18002 --map-file C:\Maps\demo.mbtiles
```

### 3.2. Chạy Node Trung gian Tiếp sức (Relay Node - Port 8002 → Forward 8888)
```powershell
java -jar target\MeshNodeClient-jar-with-dependencies.jar --mode RELAY --id RELAY-01 --bind-host 127.0.0.1 --bind-port 18002 --next-hop-host 127.0.0.1 --next-hop-port 18888 --victim-id VICTIM-01 --victim-host 127.0.0.1 --victim-port 18001
```

### 3.3. Chạy Node Nạn nhân (Victim Node - Port 8001 → Forward 8002)
```powershell
java -jar target\MeshNodeClient-jar-with-dependencies.jar --mode VICTIM --id VICTIM-01 --bind-host 127.0.0.1 --bind-port 18001 --next-hop-host 127.0.0.1 --next-hop-port 18002
```

---

## 4. Tài liệu giao thức

Đặc tả chi tiết cấu trúc gói tin canonical v1 và các fixture mẫu xem tại:
`docs/protocol/mesh-packet-v1.md`.

## 5. Android field node (post-midterm module)

The Kotlin Android field-node implementation lives in [`android-app/`](android-app/)
and uses the same canonical protocol as the desktop app. It includes the real
Wi-Fi Direct API facade, foreground service, bounded UTF-8 socket transport,
Room-backed Store-and-Forward, GPS fallback, Compose SOS UI and OsmDroid offline
map configuration. See [`docs/android-field-node.md`](docs/android-field-node.md)
for the requirement mapping.

Build it from `android-app/` with the Gradle wrapper after Android Studio/SDK
is installed. Wi-Fi Direct, background execution, GPS accuracy, offline tile
coverage and multi-hop behavior still require verification on the actual demo
phones; repository code alone does not prove those hardware-only criteria.
The complete requirement-by-requirement audit is in
[`docs/SPEC_COVERAGE.md`](docs/SPEC_COVERAGE.md).

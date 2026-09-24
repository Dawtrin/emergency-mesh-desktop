# Kiểm thử hai máy: Windows host và Ubuntu VirtualBox

Tài liệu này hướng dẫn chạy bản desktop hiện tại bằng hai máy vật lý/lô-gic:

```text
Windows host       = BASE-01 (Base Station)
Ubuntu VirtualBox  = RELAY-01 + VICTIM-01
```

Đây là kiểm thử TCP/IP trong mạng nội bộ. Internet không cần cho lúc chạy demo,
nhưng Windows và Ubuntu vẫn phải có đường mạng nhìn thấy nhau. Bản demo này
chưa phải radio mesh và chưa kiểm thử Wi-Fi Direct.

## 1. Thông tin cần thay trước khi chạy

Các giá trị ví dụ bên dưới dùng VirtualBox Host-only Adapter:

| Biến | Ví dụ | Ý nghĩa |
|---|---|---|
| `WINDOWS_IP` | `192.168.56.1` | IP Host-only của Windows |
| `UBUNTU_IP` | `192.168.56.101` | IP Host-only của Ubuntu VM |
| `BASE_PORT` | `18888` | Port Base Station trên Windows |
| `RELAY_PORT` | `18002` | Port Relay trên Ubuntu |
| `VICTIM_PORT` | `18001` | Port Victim trên Ubuntu, chỉ dùng loopback |

Thay `192.168.56.1` và `192.168.56.101` bằng địa chỉ thực tế của bạn.
Không dùng `0.0.0.0` làm địa chỉ peer; địa chỉ này chỉ dùng cho bind.

## 2. Cấu hình VirtualBox

Tắt Ubuntu VM, sau đó vào `Settings` → `Network`:

1. `Adapter 1`: `Host-only Adapter`.
2. Chọn `VirtualBox Host-Only Ethernet Adapter`.
3. Có thể bật `Adapter 2 = NAT` tạm thời để Ubuntu tải JDK/dependencies.
4. Sau khi build xong, có thể tắt Adapter 2 để chạy demo không Internet.

NAT-only không phù hợp cho hướng dẫn này vì Windows có thể không truy cập
được service bên trong Ubuntu nếu chưa cấu hình port forwarding.

## 3. Lấy đúng source branch `sontien`

### 3.1. Windows host đã có project

Mở PowerShell:

```powershell
$Project = 'D:\emergency-mesh-desktop'
Set-Location $Project

git fetch origin
git switch sontien
git pull --ff-only origin sontien
git log -1 --oneline
```

Commit hiện tại cần thấy là:

```text
bbebdf2 feat: sync desktop mesh demo and android module
```

### 3.2. Ubuntu VM chưa có project

Mở Terminal Ubuntu:

```bash
cd ~
git clone -b sontien https://github.com/Dawtrin/emergency-mesh-desktop.git
cd ~/emergency-mesh-desktop
git log -1 --oneline
```

Nếu project đã tồn tại trong Ubuntu:

```bash
cd ~/emergency-mesh-desktop
git fetch origin
git switch sontien
git pull --ff-only origin sontien
```

## 4. Kiểm tra IP hai máy

### 4.1. Windows

```powershell
Get-NetIPAddress -AddressFamily IPv4 |
  Where-Object { $_.InterfaceAlias -match 'VirtualBox|Host-Only' } |
  Select-Object InterfaceAlias,IPAddress,PrefixLength
```

Nếu lệnh trên không hiện adapter, dùng:

```powershell
ipconfig
```

Ghi lại IPv4 của `VirtualBox Host-Only Network`.

### 4.2. Ubuntu

```bash
ip -4 addr
```

Ghi lại IPv4 của interface VirtualBox, thường có tên như `enp0s3` hoặc
`enp0s8`.

Đặt biến IP trong Ubuntu:

```bash
export WINDOWS_IP='192.168.56.1'
export UBUNTU_IP='192.168.56.101'
```

Đặt biến IP trong PowerShell Windows:

```powershell
$WindowsIp = '192.168.56.1'
$UbuntuIp = '192.168.56.101'
```

### 4.3. Kiểm tra đường mạng

Từ Windows đến Ubuntu:

```powershell
ping $UbuntuIp
Test-NetConnection $UbuntuIp -Port 18002
```

Lệnh `Test-NetConnection` có thể báo fail trước khi Relay được khởi động;
chạy lại sau bước khởi động Relay.

Từ Ubuntu đến Windows:

```bash
ping -c 3 "$WINDOWS_IP"
```

Nếu ping bị chặn nhưng hai máy vẫn cùng subnet thì tiếp tục kiểm tra bằng TCP
sau khi khởi động ứng dụng.

## 5. Cài JDK 21 và build

Project yêu cầu JDK 21 trở lên. Không dùng JDK 17.

### 5.1. Windows

Với máy chính hiện tại:

```powershell
$Project = 'D:\emergency-mesh-desktop'
$env:JAVA_HOME = 'C:\Users\tienc\.jdks\ms-21.0.10'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

java -version
Set-Location $Project
.\mvnw.cmd clean package
```

Kết quả cần có `BUILD SUCCESS` và tạo được:

```text
target\BaseStationServer-jar-with-dependencies.jar
target\MeshNodeClient-jar-with-dependencies.jar
```

### 5.2. Ubuntu

Nếu Ubuntu chưa có JDK 21:

```bash
sudo apt update
sudo apt install -y openjdk-21-jdk netcat-openbsd
```

Kiểm tra và build:

```bash
cd ~/emergency-mesh-desktop
chmod +x mvnw
java -version
./mvnw clean package
```

Build lại trên Ubuntu là bắt buộc cho node JavaFX Linux. Không lấy JAR đã
build trên Windows để chạy Ubuntu vì JavaFX đóng gói native library theo hệ
điều hành.

Ubuntu cần là Desktop hoặc có GUI đang hoạt động. Nếu chạy Ubuntu Server
headless, JavaFX có thể báo lỗi `Unable to open DISPLAY`.

## 6. Cấu hình firewall

### 6.1. Windows: cho phép Base Station port `18888`

Mở PowerShell bằng quyền Administrator. Đổi `$UbuntuIp` thành IP thật:

```powershell
$UbuntuIp = '192.168.56.101'

Get-NetConnectionProfile

New-NetFirewallRule `
  -DisplayName 'Emergency Mesh Base Station 18888' `
  -Direction Inbound `
  -Protocol TCP `
  -LocalPort 18888 `
  -RemoteAddress $UbuntuIp `
  -Action Allow `
  -Profile Private
```

Chỉ mở port cho mạng Host-only/Private và địa chỉ Ubuntu, không mở port ra
toàn bộ Internet.

Nếu adapter đang ở profile `Public`, đổi đúng adapter sang `Private` trước:

```powershell
Set-NetConnectionProfile `
  -InterfaceAlias 'VirtualBox Host-Only Network' `
  -NetworkCategory Private
```

### 6.2. Ubuntu: cho phép Relay port `18002`

```bash
sudo ufw status verbose
sudo ufw allow from "$WINDOWS_IP" to any port 18002 proto tcp
sudo ufw status numbered
```

Không cần mở `18001` ra mạng vì Victim bind vào `127.0.0.1` và chỉ Relay trên
cùng Ubuntu truy cập port đó.

## 7. Khởi động Base Station trên Windows

Mở PowerShell mới:

```powershell
$Project = 'D:\emergency-mesh-desktop'
$env:JAVA_HOME = 'C:\Users\tienc\.jdks\ms-21.0.10'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$UbuntuIp = '192.168.56.101'

Set-Location $Project
$BaseJar = Join-Path $Project 'target\BaseStationServer-jar-with-dependencies.jar'
$MapFile = Join-Path $Project 'src\test\resources\fixtures\test-tiles.mbtiles'

java -jar $BaseJar `
  --id BASE-01 `
  --bind-host 0.0.0.0 `
  --bind-port 18888 `
  --relay-host $UbuntuIp `
  --relay-port 18002 `
  --map-file $MapFile
```

Giữ cửa sổ này mở. `test-tiles.mbtiles` là map fixture nhỏ để kiểm tra chức
năng; có thể thay bằng MBTiles thật sau.

## 8. Khởi động Relay trên Ubuntu

Mở Terminal Ubuntu thứ nhất:

```bash
cd ~/emergency-mesh-desktop
export WINDOWS_IP='192.168.56.1'

java -jar ./target/MeshNodeClient-jar-with-dependencies.jar \
  --mode RELAY \
  --id RELAY-01 \
  --bind-host 0.0.0.0 \
  --bind-port 18002 \
  --next-hop-host "$WINDOWS_IP" \
  --next-hop-port 18888 \
  --victim-id VICTIM-01 \
  --victim-host 127.0.0.1 \
  --victim-port 18001
```

Relay phải bind `0.0.0.0` vì Base Station trên Windows kết nối vào Relay qua
Host-only network.

## 9. Khởi động Victim trên Ubuntu

Mở Terminal Ubuntu thứ hai:

```bash
cd ~/emergency-mesh-desktop

java -jar ./target/MeshNodeClient-jar-with-dependencies.jar \
  --mode VICTIM \
  --id VICTIM-01 \
  --bind-host 127.0.0.1 \
  --bind-port 18001 \
  --next-hop-host 127.0.0.1 \
  --next-hop-port 18002
```

Thứ tự khuyến nghị:

```text
1. Base Station trên Windows
2. Relay trên Ubuntu
3. Victim trên Ubuntu
```

## 10. Kiểm tra port sau khi khởi động

### 10.1. Windows kiểm tra Relay

```powershell
$UbuntuIp = '192.168.56.101'
Test-NetConnection $UbuntuIp -Port 18002
```

Kết quả mong đợi:

```text
TcpTestSucceeded : True
```

### 10.2. Ubuntu kiểm tra Base Station

```bash
export WINDOWS_IP='192.168.56.1'
nc -vz "$WINDOWS_IP" 18888
```

Kết quả mong đợi có chữ `succeeded` hoặc `open`.

### 10.3. Ubuntu kiểm tra process đang listen

```bash
ss -ltnp | grep -E ':18001|:18002'
```

Cần thấy:

```text
127.0.0.1:18001
0.0.0.0:18002
```

### 10.4. Windows kiểm tra Base Station đang listen

```powershell
Get-NetTCPConnection -State Listen -LocalPort 18888
```

## 11. Test SOS từ Victim đến Base Station

1. Trong cửa sổ Victim trên Ubuntu, nhập thông tin nạn nhân và tọa độ thủ
   công nếu UI yêu cầu.
2. Bấm nút gửi SOS.
3. Kiểm tra log Relay: phải có nhận packet và forward packet.
4. Kiểm tra Base Station:
   - Có dòng SOS mới.
   - Packet ID giống packet từ Victim.
   - Route history có `RELAY-01`.
   - Có marker/map entry nếu map fixture có dữ liệu phù hợp.
5. Kiểm tra database Base Station tại:

```text
D:\emergency-mesh-desktop\data\mesh_desktop.db
```

Không xóa database trong lúc đang kiểm tra retry/outbox.

## 12. Test Dispatch và ACK từ Base Station về Victim

1. Trong Base Station trên Windows, tạo dispatch tới node `VICTIM-01`.
2. Gửi dispatch.
3. Kiểm tra log Relay có forward về `127.0.0.1:18001`.
4. Kiểm tra Victim nhận được dispatch.
5. Để Victim gửi ACK.
6. Kiểm tra Base Station chuyển trạng thái:

```text
SENT -> ACKED
```

`SENT` chỉ chứng minh TCP write thành công. `ACKED` mới chứng minh packet đã
đi đến Victim và ACK quay ngược về Base Station qua Relay.

## 13. Test lỗi kết nối và retry

Sau khi test SOS/ACK thành công:

1. Đóng Relay bằng `Ctrl+C`.
2. Từ Base Station gửi dispatch mới.
3. Quan sát trạng thái retry/waiting hoặc failed, không được crash UI.
4. Khởi động lại Relay bằng lệnh ở mục 8.
5. Kiểm tra outbox có thể gửi lại tùy trạng thái packet.

## 14. Dừng toàn bộ ứng dụng

Trong từng terminal đang chạy JavaFX:

```text
Ctrl+C
```

Hoặc đóng cửa sổ JavaFX rồi kiểm tra port đã được giải phóng.

Windows:

```powershell
Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue |
  Where-Object { $_.LocalPort -in @(18001,18002,18888) }
```

Ubuntu:

```bash
ss -ltnp | grep -E ':18001|:18002|:18888' || true
```

## 15. Lỗi thường gặp

| Lỗi | Cách kiểm tra |
|---|---|
| `Java version` là 17 | Đặt `JAVA_HOME` về JDK 21 rồi mở terminal mới |
| `Connection refused` | Process đích chưa chạy hoặc sai port |
| Windows không tới được Ubuntu | Kiểm tra Host-only IP và firewall Ubuntu port `18002` |
| Ubuntu không tới được Windows | Kiểm tra Windows firewall port `18888` và `$WINDOWS_IP` |
| Relay chạy nhưng Base không kết nối | `--next-hop-host` của Relay phải là IP Windows, không phải `127.0.0.1` |
| Dispatch không tới Victim | Relay phải dùng `--victim-host 127.0.0.1 --victim-port 18001` |
| JavaFX `Unable to open DISPLAY` | Ubuntu cần Desktop GUI hoặc X session |
| `ACKED` không xuất hiện | Kiểm tra chiều ngược Victim → Relay → Base, không chỉ kiểm tra TCP `SENT` |
| Map không hiện tile | Dùng file MBTiles hợp lệ; TCP/SOS vẫn có thể test riêng |

## 16. Lưu ý về preflight hiện tại

Không dùng trực tiếp `scripts/preflight-demo.ps1` cho mô hình hai máy này.
Script hiện tại giả định profile ba máy và kiểm tra các bind endpoint như thể
chúng nằm trên cùng môi trường. Với Windows host + Ubuntu VM, dùng các lệnh
kiểm tra IP/port thủ công trong tài liệu này.

## 17. Tiêu chí hoàn thành demo

Demo được xem là đạt khi có đủ các kết quả sau:

- [ ] Windows Base Station khởi động trên port `18888`.
- [ ] Ubuntu Relay khởi động trên port `18002`.
- [ ] Ubuntu Victim khởi động trên `127.0.0.1:18001`.
- [ ] Windows kết nối TCP được tới Ubuntu Relay.
- [ ] Ubuntu kết nối TCP được tới Windows Base Station.
- [ ] SOS đi theo đường `Victim → Relay → Base`.
- [ ] Base hiển thị và lưu được SOS.
- [ ] Dispatch đi theo đường `Base → Relay → Victim`.
- [ ] ACK quay về và Base hiển thị `ACKED`.
- [ ] Relay có log forwarding và packet ID có thể đối chiếu.
# Chạy demo desktop: ba máy Windows hoặc Windows + Ubuntu VM

Tài liệu này dành cho demo desktop ngày **27/09/2026**. Dự án trao đổi gói tin
qua TCP/IP trong mạng nội bộ; không tự tạo Wi-Fi/radio mesh và không tự dò máy.
Do đó, trước khi chạy cần biết IPv4 LAN/Host-only của các máy liên quan.

## 1. Chọn mô hình

### Mô hình A — ba laptop Windows

```text
Victim PC  ->  Relay PC  ->  Base Station PC
    SOS          forward           nhận SOS

Victim PC  <-  Relay PC  <-  Base Station PC
 nhận lệnh       forward            gửi Dispatch
```

| Laptop | Vai trò | Port nghe |
|---|---|---:|
| Máy 1 | `BASE-01` (Base Station) | `18888` |
| Máy 2 | `RELAY-01` (Relay) | `18002` |
| Máy 3 | `VICTIM-01` (Victim) | `18001` |

### Mô hình B — Windows host + Ubuntu VirtualBox

```text
Windows host: Base Station
Ubuntu VM:    Relay + Victim
```

Dùng mô hình này khi chỉ có một laptop Windows và một Ubuntu VM. Hướng dẫn đầy
đủ hơn nằm tại [`two-machine-vbox-test.md`](two-machine-vbox-test.md).

## 2. Quy tắc chung

**Windows dùng PowerShell**, nhận biết bằng dấu nhắc `PS ...>`; không dùng
`cd /d` trong PowerShell. Chạy từng khối theo đúng máy được ghi, không nối tất
cả lệnh thành một dòng. Ubuntu dùng Terminal/Bash, không dán lệnh PowerShell
sang Ubuntu. Nếu lệnh báo lỗi, dừng tại đó trước khi chạy bước sau.

1. Cả ba laptop phải vào cùng Wi-Fi riêng/hotspot hoặc cùng LAN. Internet chỉ
   cần khi clone/build lần đầu; lúc demo TCP nội bộ không cần Internet.
2. Mở Command Prompt hoặc PowerShell trên **từng máy**, chạy `ipconfig`, rồi
   ghi lại `IPv4 Address` trong phần `Wireless LAN adapter Wi-Fi`.
3. Không dùng `127.0.0.1` giữa các laptop. `127.0.0.1` chỉ có nghĩa là chính
   máy đang chạy lệnh.
4. Đặt mạng Wi-Fi thành profile **Private**, không mở firewall demo trên mạng
   Public.
5. Dùng đúng cùng commit nhánh `sontien` trên mọi máy:

```powershell
git fetch origin
git switch sontien
git pull --ff-only origin sontien
git log -1 --oneline
```

6. Mỗi máy cần JDK 21+ và phải có hai JAR sau sau khi build:

```text
target\BaseStationServer-jar-with-dependencies.jar
target\MeshNodeClient-jar-with-dependencies.jar
```

## 3. Mô hình A: ba laptop Windows

### 3.1. Ghi IP thật của nhóm

Ví dụ dưới đây chỉ minh họa. Thay tất cả bằng IP Wi-Fi/hotspot thật.

```text
BASE_IP   = 192.168.43.101   (Máy 1)
RELAY_IP  = 192.168.43.102   (Máy 2)
VICTIM_IP = 192.168.43.103   (Máy 3)
```

Mối quan hệ cần cấu hình:

```text
Base    chỉ cần biết RELAY_IP
Relay   cần biết BASE_IP và VICTIM_IP
Victim  chỉ cần biết RELAY_IP
```

### 3.2. Chuẩn bị source và build trên cả ba máy

Nếu máy chưa có source, chạy trên từng laptop Windows:

```powershell
Set-Location $HOME
git clone -b sontien https://github.com/Dawtrin/emergency-mesh-desktop.git
Set-Location "$HOME\emergency-mesh-desktop"
```

Nếu source đã có ở `D:\emergency-mesh-desktop`, dùng:

```powershell
Set-Location D:\emergency-mesh-desktop
git fetch origin
git switch sontien
git pull --ff-only origin sontien
```

Trong các lệnh bên dưới, đoạn này tự chọn project ở ổ `D:` nếu có; nếu không,
nó dùng thư mục vừa clone trong tài khoản Windows:

```powershell
$Project = if (Test-Path 'D:\emergency-mesh-desktop') {
  'D:\emergency-mesh-desktop'
} else {
  Join-Path $HOME 'emergency-mesh-desktop'
}
Set-Location $Project
```

Kiểm tra JDK:

```powershell
java --version
javac --version
```

Hai lệnh phải báo Java 21 hoặc cao hơn. Nếu Windows đang chọn Java 17, đặt
`JAVA_HOME` về thư mục JDK 21 của máy, ví dụ máy hiện tại:

```powershell
$env:JAVA_HOME = 'C:\Users\tienc\.jdks\ms-21.0.10'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
```

Build trên từng laptop:

```powershell
.\mvnw.cmd clean package
```

Giữ terminal build này để chạy ứng dụng. `JAVA_HOME`/`Path` vừa đặt chỉ có hiệu
lực trong terminal đó; nếu mở terminal mới, đặt lại đường dẫn JDK của máy rồi
kiểm tra `java --version`. Không dùng nguyên đường dẫn `C:\Users\tienc\...`
trên máy bạn bè nếu thư mục đó không tồn tại.

### 3.3. Mở firewall Windows

Mở **PowerShell Run as Administrator** trên từng laptop. Thay IP mẫu trước khi
chạy.

Kiểm tra profile mạng:

```powershell
Get-NetConnectionProfile
```

Nếu Wi-Fi demo đáng tin cậy đang là Public, thay tên adapter trong lệnh sau
bằng đúng `InterfaceAlias` của Wi-Fi đó rồi mới chạy:

```powershell
Set-NetConnectionProfile -InterfaceAlias 'Wi-Fi' -NetworkCategory Private
```

Không đổi profile của mạng công cộng hoặc mạng khác không dùng cho demo.

#### Máy 1 — Base Station: TCP 18888 chỉ nhận từ Relay

```powershell
$RelayIp = '192.168.43.102'
$Rule = 'EmergencyMesh-Demo-Base-18888'
Get-NetFirewallRule -Name $Rule -ErrorAction SilentlyContinue | Remove-NetFirewallRule
New-NetFirewallRule -Name $Rule -DisplayName $Rule -Direction Inbound `
  -Protocol TCP -LocalPort 18888 -RemoteAddress $RelayIp -Action Allow -Profile Private
```

#### Máy 2 — Relay: TCP 18002 nhận từ Base và Victim

```powershell
$BaseIp = '192.168.43.101'
$VictimIp = '192.168.43.103'
$Rule = 'EmergencyMesh-Demo-Relay-18002'
Get-NetFirewallRule -Name $Rule -ErrorAction SilentlyContinue | Remove-NetFirewallRule
New-NetFirewallRule -Name $Rule -DisplayName $Rule -Direction Inbound `
  -Protocol TCP -LocalPort 18002 -RemoteAddress @($BaseIp, $VictimIp) -Action Allow -Profile Private
```

#### Máy 3 — Victim: TCP 18001 chỉ nhận từ Relay

```powershell
$RelayIp = '192.168.43.102'
$Rule = 'EmergencyMesh-Demo-Victim-18001'
Get-NetFirewallRule -Name $Rule -ErrorAction SilentlyContinue | Remove-NetFirewallRule
New-NetFirewallRule -Name $Rule -DisplayName $Rule -Direction Inbound `
  -Protocol TCP -LocalPort 18001 -RemoteAddress $RelayIp -Action Allow -Profile Private
```

### 3.4. Mở ứng dụng: một terminal trên mỗi laptop

Khởi động theo thứ tự: **Base → Victim → Relay**. Giữ terminal và cửa sổ JavaFX
mở trong suốt buổi test.

#### Máy 1 — Base Station

```powershell
$Project = if (Test-Path 'D:\emergency-mesh-desktop') { 'D:\emergency-mesh-desktop' } else { Join-Path $HOME 'emergency-mesh-desktop' }
Set-Location $Project
$RelayIp = '192.168.43.102'
$MapFile = Join-Path (Get-Location) 'src\test\resources\fixtures\test-tiles.mbtiles'

java -jar .\target\BaseStationServer-jar-with-dependencies.jar `
  --id BASE-01 `
  --bind-host 0.0.0.0 `
  --bind-port 18888 `
  --relay-host $RelayIp `
  --relay-port 18002 `
  --map-file $MapFile
```

#### Máy 3 — Victim

```powershell
$Project = if (Test-Path 'D:\emergency-mesh-desktop') { 'D:\emergency-mesh-desktop' } else { Join-Path $HOME 'emergency-mesh-desktop' }
Set-Location $Project
$RelayIp = '192.168.43.102'

java -jar .\target\MeshNodeClient-jar-with-dependencies.jar `
  --mode VICTIM `
  --id VICTIM-01 `
  --bind-host 0.0.0.0 `
  --bind-port 18001 `
  --next-hop-host $RelayIp `
  --next-hop-port 18002
```

#### Máy 2 — Relay

```powershell
$Project = if (Test-Path 'D:\emergency-mesh-desktop') { 'D:\emergency-mesh-desktop' } else { Join-Path $HOME 'emergency-mesh-desktop' }
Set-Location $Project
$BaseIp = '192.168.43.101'
$VictimIp = '192.168.43.103'

java -jar .\target\MeshNodeClient-jar-with-dependencies.jar `
  --mode RELAY `
  --id RELAY-01 `
  --bind-host 0.0.0.0 `
  --bind-port 18002 `
  --next-hop-host $BaseIp `
  --next-hop-port 18888 `
  --victim-id VICTIM-01 `
  --victim-host $VictimIp `
  --victim-port 18001
```

### 3.5. Kiểm tra kết nối trước khi gửi SOS

Sau khi đã mở ba ứng dụng, có thể chạy các lệnh sau trong terminal PowerShell
khác; kết quả đúng là `TcpTestSucceeded : True`.

```powershell
# Trên Base
Test-NetConnection 192.168.43.102 -Port 18002

# Trên Relay
Test-NetConnection 192.168.43.101 -Port 18888
Test-NetConnection 192.168.43.103 -Port 18001

# Trên Victim
Test-NetConnection 192.168.43.102 -Port 18002
```

### 3.6. Tiêu chí PASS

1. Base hiển thị `RELAY ONLINE` sau khoảng 3–6 giây.
2. Victim gửi một SOS. Base tăng `TỔNG SOS` và bảng xuất hiện `VICTIM-01`.
3. Relay log có nhận và forward SOS.
4. Trên Base chọn `VICTIM-01`, nhập lệnh và bấm `Gửi Lệnh`.
5. Victim hiển thị lệnh; Relay forward ACK.
6. Base System Log có dòng `[ACKED] Dispatch ... đã được victim xác nhận hợp lệ`.

Nếu ba laptop cùng kết nối hotspot nhưng không ping/TCP được nhau, hotspot có
thể đang cô lập các thiết bị khách. Khi đó chuyển sang router Wi-Fi/LAN khác;
đừng tắt toàn bộ firewall để né lỗi mạng.

## 4. Mô hình B: Windows + Ubuntu VirtualBox

Các IP Host-only đang dùng trong ví dụ đã kiểm thử:

```text
WINDOWS_IP = 192.168.56.1
UBUNTU_IP  = 192.168.56.101
```

Nếu VM khởi động lại, kiểm tra lại IP Ubuntu bằng `ip -4 addr`; IP cấp động có
thể đổi.

### 4.1. Windows: mở firewall và Base Station

Mở PowerShell Administrator:

```powershell
Set-Location D:\emergency-mesh-desktop
powershell.exe -NoLogo -ExecutionPolicy Bypass -File `
  .\scripts\windows\allow-base-firewall.ps1 -UbuntuIp 192.168.56.101
```

Mở PowerShell thường, chạy Base bằng script:

```powershell
Set-Location D:\emergency-mesh-desktop
.\scripts\windows\START_BASE.cmd -UbuntuIp 192.168.56.101 -Rebuild
```

### 4.2. Ubuntu: kiểm tra Base, rồi mở Relay + Victim

Ubuntu cần Desktop GUI, Git và JDK 21 đã cài. Build lại trên Ubuntu, không sao
chép JAR build từ Windows sang vì phần native JavaFX khác hệ điều hành.

Nếu Ubuntu chưa có project, chạy một lần:

```bash
cd ~
git clone -b sontien https://github.com/Dawtrin/emergency-mesh-desktop.git
```

Nếu đã có project, không clone chồng hay xóa thư mục; cập nhật bằng các lệnh
sau. Nếu Git báo có thay đổi cục bộ, giữ lại thay đổi và xử lý trước khi pull.

Trên Ubuntu Desktop Terminal:

```bash
cd ~/emergency-mesh-desktop
git fetch origin
git switch sontien
git pull --ff-only origin sontien

java --version
javac --version
```

Hai lệnh phải báo phiên bản 21+. Đặt JDK và xác nhận Maven dùng đúng phiên bản:

```bash
export JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")"
export PATH="$JAVA_HOME/bin:$PATH"
bash ./mvnw --version
```

Nếu UFW đang bật, cho phép Windows kết nối Relay; không cần mở Victim ra mạng
vì Victim và Relay cùng máy Ubuntu:

```bash
sudo ufw status verbose
sudo ufw allow from 192.168.56.1 to any port 18002 proto tcp
```

Kiểm tra Base Windows đang mở:

```bash
nc -vz 192.168.56.1 18888
```

Nếu `nc` báo `succeeded` hoặc `open`, chạy:

```bash
REBUILD=1 bash scripts/ubuntu/start-demo.sh 192.168.56.1
```

Script mở hai cửa sổ JavaFX `RELAY-01` và `VICTIM-01`. Base phải chuyển sang
`RELAY ONLINE`; tiếp tục theo tiêu chí PASS ở mục 3.6.

Trên một terminal PowerShell khác của Windows, kiểm tra chiều Windows → Relay:

```powershell
Test-NetConnection 192.168.56.101 -Port 18002
```

Kết quả cần là `TcpTestSucceeded : True`.

### 4.3. Ubuntu: chạy từng node nếu không dùng launcher

**Chọn một cách:** dùng launcher ở mục 4.2 HOẶC chạy riêng ở mục này. Không
chạy cả hai cách cùng lúc, vì sẽ bị trùng cổng.

Build một lần bằng JDK 21 trong thư mục project:

```bash
cd ~/emergency-mesh-desktop
bash ./mvnw clean package
```

Terminal Ubuntu thứ nhất — Relay:

```bash
cd ~/emergency-mesh-desktop
java -jar target/MeshNodeClient-jar-with-dependencies.jar --config config/nodeB1.properties --next-hop-host 192.168.56.1
```

Terminal Ubuntu thứ hai — Victim:

```bash
cd ~/emergency-mesh-desktop
java -jar target/MeshNodeClient-jar-with-dependencies.jar --config config/nodeA.properties
```

Kiểm tra `java --version` trong từng terminal nếu hệ thống có nhiều bản Java.
Giữ cả hai cửa sổ mở khi gửi SOS và Dispatch.

### 4.4. Dừng demo

Đóng các cửa sổ JavaFX Base/Relay/Victim, hoặc nhấn `Ctrl+C` ở terminal đang
chạy node tương ứng. Không xóa database `data/mesh_desktop.db` khi app đang
mở. Muốn giữ bằng chứng, chụp ảnh SOS, route history, lệnh trên Victim và dòng
`[ACKED]` ở Base trước khi dừng.

## 5. Giới hạn cần nói khi bảo vệ

- Đây là multi-hop TCP/IP đã cấu hình trước, không phải radio mesh tự tạo mạng.
- Các máy phải có đường mạng nội bộ chung và phải biết IP next-hop ban đầu.
- Heartbeat làm Base biết Relay online và có thể chọn Relay cho Dispatch; nó
  không phải cơ chế tự dò tất cả laptop trong Wi-Fi.
- Không chứng minh Wi-Fi Direct, Bluetooth mesh, GPS thực địa hoặc vùng bản đồ
  thực tế.

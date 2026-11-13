# 🎨 ART DIRECTION: NASA MISSION CONTROL × DATA-DENSE DARK MODE
> **Áp dụng cho:** Emergency Mesh Rescue — Tactical Web C2 Center (8 Phân hệ)
> **Phiên bản:** v2 — Hợp nhất 2 trường phái thiết kế thành 1 hệ thống duy nhất
> **Đi kèm:** `SUPER_PROMPT_JAVAWEB_C2_FINAL.md` (kiến trúc & hành vi hệ thống)

---

## 0. HAI TRƯỜNG PHÁI HỢP NHẤT

Bản v1 mới có phần **NASA Mission Control** (glass console, chamfered corner, LED trạng thái, màu tác chiến). Bản v2 này bổ sung thêm lớp **Data-Dense Dark Mode / Mission Control UI** — phong cách của các dashboard khoa học/tài chính cao cấp (kiểu Bloomberg Terminal, dashboard khí hậu NASA JPL, dashboard telemetry SpaceX) — đặc trưng bởi:

- **Mật độ thông tin cực cao** nhưng vẫn dễ quét mắt: nhiều panel nhỏ xếp lưới chặt, mỗi panel có tiêu đề + bộ lọc thời gian + số liệu hero + biểu đồ mini.
- **Số liệu là nhân vật chính**: số to, đơn vị nhỏ, delta tăng/giảm có màu + mũi tên, sparkline đi kèm ngay dưới số.
- **Biểu đồ luôn có gradient fill, không gridline mặc định, có crosshair khi hover.**
- **Vi tương tác dày đặc**: mỗi panel có nút "More", dropdown khoảng thời gian, tooltip số liệu chính xác khi hover.

Từ nay, **mọi panel trong 8 trang phải đạt đồng thời cả 2 tiêu chí**: khung hình dạng NASA (glass console, chamfered corner, màu tác chiến) VÀ mật độ/hành vi dữ liệu kiểu Data-Dense Dark Mode (hero number + delta + sparkline + "More" + tooltip).

---

## 1. TRIẾT LÝ THIẾT KẾ

1. Không có gì "web thường" — không bo tròn mềm, không panel trắng/xám mặc định.
2. Góc vát kỹ thuật (chamfered), không `border-radius` tròn (trừ LED, chấm live, avatar).
3. Màu có cấp bậc quân sự cố định — không đổi nghĩa giữa các trang (mục 3).
4. Số liệu không nhảy giật — mono font + `tabular-nums`, transition mượt.
5. Mật độ cao nhưng có trật tự — mọi panel tuân theo cùng 1 khuôn: Header → Hero metric → Delta/sparkline → Body chart/table → Footer action.
6. Cảnh báo "thấy ngay trong 3 giây" — glow/pulse/strobe chỉ dùng đúng chỗ, không lạm dụng.
7. Mọi trạng thái tương tác (hover, focus, loading, empty, error) đều phải có style riêng — không để mặc định trình duyệt lộ ra.

---

## 2. HỆ THỐNG LỚP NỀN (ELEVATION SYSTEM)

```css
:root {
  --z-base: 0;
  --z-panel: 10;
  --z-panel-hover: 15;
  --z-dropdown: 30;
  --z-toast: 40;
  --z-modal: 50;
  --z-command-palette: 60;

  --bg-deep-space:     #05070B;  /* nền toàn ứng dụng, layer 0 */
  --bg-carbon-slate:   #0E131F;  /* nền panel, layer 1 */
  --bg-panel-alt:      #131A28;  /* hàng zebra / sub-panel, layer 2 */
  --bg-elevated:       #1A2233;  /* dropdown / tooltip / modal, layer 3 */
  --bg-glass:          rgba(11, 15, 23, 0.85);
  --bg-hover:          rgba(255, 255, 255, 0.03); /* overlay hover trên mọi bg */
}
```
> Quy tắc: panel càng "nổi" (tương tác được, đang mở) thì dùng layer càng cao, kèm `box-shadow` đậm hơn — không dùng `border-radius` để tạo cảm giác nổi như UI thường, mà dùng **cường độ glow + độ sáng nền** tăng dần.

---

## 3. HỆ MÀU (NASA TELEMETRY PALETTE)

```css
:root {
  --border-glass:      rgba(0, 240, 255, 0.22);
  --border-subtle:     rgba(255, 255, 255, 0.06);
  --glow-glass:         0 0 20px rgba(0, 240, 255, 0.08), inset 0 1px 0 rgba(255,255,255,0.1);

  --text-primary:       #F2F6FA;
  --text-secondary:     #7C8BA1;
  --text-muted:         #4B5566;

  --aero-cyan:          #00F0FF;  /* Tọa độ / Radar / mạng OK / hero accent mặc định */
  --defcon-red:          #FF1E40;  /* SOS / PENDING / CRITICAL / delta âm nguy hiểm */
  --life-green:          #00FF66;  /* Mesh Online / RESOLVED / delta dương tốt */
  --orbital-amber:       #FF9900;  /* Cảnh báo cao / DISPATCHED */
  --relay-purple:        #B026FF;  /* Hop / Relay Node */

  --agency-medical:      #00B4D8;  /* 115 */
  --agency-rescue:        #FF5400;  /* 114 */
  --agency-police:        #FFD000;  /* 113 */
}
```
**Quy tắc cứng:** không tạo màu mới ngoài danh sách. Delta/thay đổi số liệu dùng `--life-green` (tăng tốt/giảm tốt tùy ngữ cảnh) hoặc `--defcon-red` (xấu đi) — không dùng xanh dương/xanh lá ngẫu nhiên như dashboard thường.

---

## 4. TYPOGRAPHY & ĐỊNH DẠNG SỐ

```html
<!-- Nhúng cục bộ trong static/vendor/fonts/ — KHÔNG gọi CDN (offline 100%) -->
```

| Vai trò | Font | Size | Weight | Ghi chú |
|---|---|---|---|---|
| Tiêu đề trang / Nav | Space Grotesk | 13–15px | 600 | Uppercase, letter-spacing 0.04em |
| **Hero KPI Number** | JetBrains Mono | 34–44px | 700 | `tabular-nums`, `text-shadow` glow theo màu ngữ cảnh |
| Delta / % thay đổi | JetBrains Mono | 12px | 600 | Có mũi tên `▲`/`▼` inline, màu theo mục 3 |
| Sparkline label | Space Grotesk | 10px | 500 | Uppercase, `--text-muted` |
| Telemetry HUD (tọa độ, checksum) | JetBrains Mono | 12–13px | 500 | Cố định font toàn hệ thống |
| Body / mô tả | Space Grotesk | 13px | 400 | `--text-secondary` |
| Bảng: header | Space Grotesk | 11px | 600 | Uppercase, sort icon inline |
| Bảng: cell số liệu | JetBrains Mono | 13px | 500 | Căn phải, `tabular-nums` |

**Quy tắc định dạng số (áp dụng toàn hệ thống, mọi trang):**
- Số nguyên lớn: dùng dấu chấm ngăn cách hàng nghìn (`1.240`).
- Số thập phân: tối đa 2 chữ số sau dấu phẩy, đơn vị đặt sau, cỡ nhỏ hơn 60% và mờ hơn (`--text-muted`).
- Delta luôn có dấu `+`/`-` tường minh, không chỉ dựa vào màu.
- Timestamp hiển thị dạng `HH:mm:ss.SSS` (đến mili-giây) trong Audit Trail/Packet Sniffer, dạng `HH:mm` ở nơi khác.

```css
.kpi-number { font-family:'JetBrains Mono',monospace; font-variant-numeric: tabular-nums; font-size:40px; font-weight:700; text-shadow:0 0 12px currentColor; }
.kpi-delta { font-family:'JetBrains Mono',monospace; font-size:12px; font-weight:600; display:inline-flex; align-items:center; gap:4px; }
.kpi-delta--up   { color: var(--life-green); }
.kpi-delta--down { color: var(--defcon-red); }
```

---

## 5. SPACING, GRID & GÓC VÁT

- Base unit **4px**. Toàn bộ margin/padding/gap là bội số 4.
- **Không `border-radius` tròn** trừ LED/dot/avatar — panel/card/nút dùng `clip-path` chamfered (16px panel lớn, 8px nút/badge nhỏ) như mục 6.1.
- **Grid tổng thể (Desktop ≥1280px):** 12 cột, gutter 16px, panel co giãn theo bội số cột (không panel nào lệch lưới).
- **Breakpoint đa thiết bị (bắt buộc — hệ thống chạy trên Laptop/iPad/điện thoại cùng lúc):**

| Breakpoint | Range | Layout |
|---|---|---|
| `--bp-desktop` | ≥1280px | Lưới 12 cột đầy đủ, 3 cột chính (trái/giữa/phải) như thiết kế gốc |
| `--bp-tablet` | 768–1279px | Thu về 2 cột, panel phụ (Network/Dispatch) xuống dưới dạng tab trượt |
| `--bp-mobile` | <768px | 1 cột, Navbar 8 trang chuyển thành bottom tab bar 5 icon chính + "More" |

- **Taxonomy kích thước panel** (dùng nhất quán cho mọi trang):
  - `panel--hero` : span 8/12 cột, cao ≥360px (bản đồ, biểu đồ chính)
  - `panel--stat`  : span 3/12 cột, cao 140px (KPI card)
  - `panel--wide`  : span 6/12 cột, cao 240px (chart đôi)
  - `panel--full`  : span 12/12 cột (bảng dài, audit trail)

---

## 6. COMPONENT SPEC

### 6.1. Khung kính Avionics (mọi panel dùng chung)

```css
.avionics-panel {
  background: var(--bg-glass);
  border: 1px solid var(--border-glass);
  box-shadow: var(--glow-glass);
  backdrop-filter: blur(24px);
  clip-path: polygon(16px 0, 100% 0, 100% calc(100% - 16px), calc(100% - 16px) 100%, 0 100%, 0 16px);
  padding: 16px 20px;
  transition: box-shadow 0.2s ease, border-color 0.2s ease;
}
.avionics-panel:hover {
  border-color: rgba(0,240,255,0.4);
  box-shadow: 0 0 28px rgba(0,240,255,0.14), inset 0 1px 0 rgba(255,255,255,0.12);
}
.panel-header {
  display:flex; justify-content:space-between; align-items:center; margin-bottom:12px;
}
.panel-title { font:600 12px/1.3 'Space Grotesk'; letter-spacing:.04em; text-transform:uppercase; color:var(--text-secondary); }
.panel-more-btn {
  font:500 11px 'Space Grotesk'; color:var(--aero-cyan);
  background:rgba(0,240,255,0.08); border:1px solid rgba(0,240,255,0.25);
  padding:4px 10px; clip-path: polygon(6px 0,100% 0,100% calc(100% - 6px),calc(100% - 6px) 100%,0 100%,0 6px);
  cursor:pointer;
}
```

### 6.2. Date/Time Range Selector (dropdown "Since 1850" style — dùng cho mọi chart có trục thời gian)

```html
<button class="range-selector">
  <span>24 GIỜ QUA</span>
  <svg class="chevron-icon" width="10" height="10">...</svg>
</button>
```
```css
.range-selector {
  background: var(--bg-panel-alt); border:1px solid var(--border-subtle);
  color: var(--text-primary); font:500 11px 'Space Grotesk';
  padding:5px 10px; display:flex; align-items:center; gap:6px;
  clip-path: polygon(6px 0,100% 0,100% calc(100% - 6px),calc(100% - 6px) 100%,0 100%,0 6px);
}
.range-selector:hover { border-color: var(--aero-cyan); }
```

### 6.3. KPI Card đầy đủ (Hero Number + Delta + Sparkline) — chuẩn Data-Dense

```html
<div class="avionics-panel panel--stat kpi-card">
  <div class="panel-header">
    <span class="panel-title">Nguy cấp chờ cứu</span>
    <span class="status-led status-led--critical"></span>
  </div>
  <div class="kpi-number" style="color:var(--defcon-red)">14</div>
  <div class="kpi-delta kpi-delta--up">▲ +3 <span class="kpi-delta-note">/ 10 phút qua</span></div>
  <svg class="kpi-sparkline" viewBox="0 0 120 30" preserveAspectRatio="none">
    <path d="M0,20 L15,18 L30,22 L45,10 L60,14 L75,6 L90,9 L105,3 L120,5"
          fill="none" stroke="var(--defcon-red)" stroke-width="1.5"
          style="filter:drop-shadow(0 0 4px var(--defcon-red))"/>
  </svg>
</div>
```
> Mọi KPI card trong 8 trang bắt buộc đủ 4 lớp: label → hero number → delta → sparkline. Card thiếu sparkline/delta bị coi là chưa đạt chuẩn.

### 6.4. Đèn LED trạng thái

```css
.status-led { width:8px; height:8px; border-radius:50%; box-shadow:0 0 6px currentColor,0 0 12px currentColor; }
.status-led--online   { background:var(--life-green); color:var(--life-green); animation: led-blink 2s infinite; }
.status-led--warning  { background:var(--orbital-amber); color:var(--orbital-amber); animation: led-blink 1.2s infinite; }
.status-led--critical { background:var(--defcon-red); color:var(--defcon-red); animation: led-blink .6s infinite; }
@keyframes led-blink { 0%,100%{opacity:1} 50%{opacity:.35} }
```

### 6.5. Live/Recording Badge

```css
.live-badge { display:inline-flex; align-items:center; gap:6px; font:600 11px 'JetBrains Mono'; color: var(--defcon-red); text-transform:uppercase; }
.live-dot { width:7px; height:7px; border-radius:50%; background:var(--defcon-red); position:relative; }
.live-dot::after {
  content:''; position:absolute; inset:-6px; border-radius:50%;
  border:1px solid var(--defcon-red); animation: ping 1.6s cubic-bezier(0,0,.2,1) infinite;
}
@keyframes ping { 0%{transform:scale(0.6);opacity:.8} 100%{transform:scale(2.2);opacity:0} }
```

### 6.6. Gauge tròn kỹ thuật (Duplicate Drop Rate, Checksum Integrity, Turbin/Velocity style)

```html
<svg viewBox="0 0 120 120" class="tactical-gauge">
  <!-- vạch chia độ (tick marks) kiểu đồng hồ đo hàng không -->
  <g stroke="var(--border-subtle)" stroke-width="1">
    <!-- 24 tick, mỗi 15deg, sinh bằng JS/loop khi render -->
  </g>
  <circle cx="60" cy="60" r="50" fill="none" stroke="rgba(255,255,255,.08)" stroke-width="6"/>
  <circle cx="60" cy="60" r="50" fill="none" stroke="var(--aero-cyan)" stroke-width="6"
          stroke-linecap="butt" stroke-dasharray="314" stroke-dashoffset="94"
          transform="rotate(-90 60 60)"
          style="filter:drop-shadow(0 0 6px var(--aero-cyan));transition:stroke-dashoffset .6s cubic-bezier(.4,0,.2,1)"/>
  <text x="60" y="58" text-anchor="middle" class="gauge-value" font-family="JetBrains Mono" font-size="20" fill="var(--text-primary)">70%</text>
  <text x="60" y="74" text-anchor="middle" class="gauge-label" font-family="Space Grotesk" font-size="8" fill="var(--text-muted)">INTEGRITY</text>
</svg>
```

### 6.7. Bảng dữ liệu mật độ cao (Data-Dense Table)

```css
table.tactical-table { width:100%; border-collapse:collapse; font-size:13px; }
table.tactical-table thead th {
  position: sticky; top:0; background: var(--bg-carbon-slate);
  text-align:left; color:var(--text-secondary); font:600 11px 'Space Grotesk';
  text-transform:uppercase; padding:8px 12px; border-bottom:1px solid var(--border-subtle);
  cursor:pointer; user-select:none;
}
table.tactical-table thead th .sort-icon { opacity:.4; margin-left:4px; }
table.tactical-table thead th.sorted .sort-icon { opacity:1; color:var(--aero-cyan); }
table.tactical-table td { padding:9px 12px; border-bottom:1px solid var(--border-subtle); font-family:'JetBrains Mono'; }
table.tactical-table td.text-cell { font-family:'Space Grotesk'; }
table.tactical-table tr:hover { background: var(--bg-hover); cursor:pointer; }
table.tactical-table tr:nth-child(even) { background: rgba(255,255,255,.015); }
.table-mini-bar { display:inline-block; height:4px; background:var(--aero-cyan); border-radius:2px; }
```
> Cell số liệu quan trọng (vd: `packets_forwarded_count`) nên có `.table-mini-bar` inline thể hiện tỉ lệ so với max trong bảng — pattern "bar-in-cell" chuẩn dashboard tài chính.

### 6.8. Tooltip biểu đồ (Crosshair + Tooltip)

```css
.chart-tooltip {
  background: var(--bg-elevated); border:1px solid var(--border-glass);
  box-shadow: 0 4px 24px rgba(0,0,0,.5);
  padding:8px 12px; font:500 12px 'JetBrains Mono'; color:var(--text-primary);
  clip-path: polygon(6px 0,100% 0,100% calc(100% - 6px),calc(100% - 6px) 100%,0 100%,0 6px);
  z-index: var(--z-dropdown);
}
.chart-crosshair { stroke: var(--aero-cyan); stroke-width:1; stroke-dasharray:3,3; opacity:.5; }
```

### 6.9. Toast Notification (SOS mới — bổ sung cho Strobe toàn màn hình)

```css
.toast {
  position: fixed; top:16px; right:16px; z-index: var(--z-toast);
  background: var(--bg-elevated); border-left:3px solid var(--defcon-red);
  box-shadow: 0 8px 32px rgba(0,0,0,.6);
  padding:12px 16px; min-width:280px;
  animation: toast-in .3s cubic-bezier(.16,1,.3,1);
}
@keyframes toast-in { from{transform:translateX(24px);opacity:0} to{transform:translateX(0);opacity:1} }
```

### 6.10. Skeleton Loading (shimmer)

```css
.skeleton {
  background: linear-gradient(90deg, var(--bg-panel-alt) 0%, var(--bg-elevated) 50%, var(--bg-panel-alt) 100%);
  background-size: 200% 100%;
  animation: shimmer 1.4s ease-in-out infinite;
}
@keyframes shimmer { 0%{background-position:200% 0} 100%{background-position:-200% 0} }
```

### 6.11. Empty / Error / Offline State

```html
<div class="state-empty">
  <svg class="state-icon" ...></svg>
  <p class="state-title">KHÔNG CÓ DỮ LIỆU</p>
  <p class="state-desc">Chưa nhận được packet nào từ Mesh trong phiên này.</p>
</div>
```
```css
.state-empty { display:flex; flex-direction:column; align-items:center; gap:8px; padding:40px 0; color:var(--text-muted); }
.state-title { font:600 12px 'Space Grotesk'; letter-spacing:.04em; text-transform:uppercase; color:var(--text-secondary); }
```
> Trạng thái mất kết nối Socket 8888 phải có banner riêng màu `--orbital-amber` ở top mọi trang — không được để dashboard "đứng hình" mà không báo.

### 6.12. Scrollbar tuỳ biến

```css
::-webkit-scrollbar { width:6px; height:6px; }
::-webkit-scrollbar-track { background: var(--bg-deep-space); }
::-webkit-scrollbar-thumb { background: rgba(0,240,255,.25); }
::-webkit-scrollbar-thumb:hover { background: rgba(0,240,255,.45); }
```

### 6.13. Focus State (Accessibility, vẫn giữ phong cách tác chiến)

```css
:focus-visible {
  outline: none;
  box-shadow: 0 0 0 2px var(--bg-deep-space), 0 0 0 4px var(--aero-cyan);
}
```

### 6.14. Threat Banner & Strobe (giữ từ v1)

```css
.threat-banner {
  background: linear-gradient(90deg, rgba(255,30,64,.18), transparent);
  border-left:3px solid var(--defcon-red); padding:10px 20px;
  font:700 13px 'Space Grotesk'; letter-spacing:.05em;
  animation: banner-pulse 2.4s ease-in-out infinite;
}
@keyframes banner-pulse { 0%,100%{border-left-color:var(--defcon-red)} 50%{border-left-color:#FF6B85} }

@keyframes red-perimeter-strobe {
  0%,100% { box-shadow: inset 0 0 0 4px transparent; }
  50%     { box-shadow: inset 0 0 40px 6px rgba(255,30,64,.55); }
}
.strobe-active { animation: red-perimeter-strobe .8s ease-in-out infinite; }
```

### 6.15. Badge liên ngành (115/114/113)

```css
.badge-agency { padding:4px 10px; font:600 11px 'Space Grotesk'; letter-spacing:.03em; clip-path: polygon(4px 0,100% 0,100% calc(100% - 4px),calc(100% - 4px) 100%,0 100%,0 4px); }
.badge-agency--medical { background:rgba(0,180,216,.15); color:var(--agency-medical); border:1px solid var(--agency-medical); }
.badge-agency--rescue  { background:rgba(255,84,0,.15);  color:var(--agency-rescue);  border:1px solid var(--agency-rescue); }
.badge-agency--police  { background:rgba(255,208,0,.15); color:var(--agency-police);  border:1px solid var(--agency-police); }
```

---

## 7. MOTION SYSTEM (CHUYỂN ĐỘNG CÓ CHỦ ĐÍCH)

```css
:root {
  --ease-standard: cubic-bezier(.4,0,.2,1);
  --ease-decelerate: cubic-bezier(0,0,.2,1);
  --ease-accelerate: cubic-bezier(.4,0,1,1);
  --duration-fast: 150ms;
  --duration-base: 250ms;
  --duration-slow: 600ms;
}
```
| Hành vi | Duration | Easing |
|---|---|---|
| Hover panel/nút | `--duration-fast` | `--ease-standard` |
| Chuyển tab (8 trang) | `--duration-base` | `--ease-decelerate` |
| Số liệu KPI cập nhật (WebSocket) | `--duration-slow` | `--ease-standard` (transition trên `stroke-dashoffset`/opacity, KHÔNG animate trực tiếp nội dung số để tránh giật) |
| Toast vào/ra | `--duration-base` | `--ease-decelerate` / `--ease-accelerate` |
| Pulse/LED/Strobe | riêng theo mục 6 | `ease-in-out` lặp vô hạn |

---

## 8. BẢN ĐỒ GIS & BIỂU ĐỒ (8 LOẠI)

| Yếu tố | Spec |
|---|---|
| Tile bản đồ | Dark Tactical / Satellite Hybrid / Terrain — không dùng tile sáng mặc định |
| AntPath route | Relay=`--relay-purple`, SOS=`--defcon-red`, dash 10,20, delay 400ms |
| Heatmap | gradient `--life-green → --orbital-amber → --defcon-red` |
| Range Rings | vòng tròn `--aero-cyan`, opacity giảm dần theo bán kính |
| Real-time Throughput (Area) | gradient `--aero-cyan → transparent`, có crosshair + tooltip (mục 6.8) |
| Hop Count Distribution (Bar) | màu theo số hop: `--relay-purple` (1 hop) → `--orbital-amber` (2) → `--defcon-red` (3+) |
| Duplicate Drop Gauge | SVG tự vẽ (mục 6.6) |
| Relay Traffic Heatmap | Matrix, gradient như trên |
| Donut loại thảm họa | flood=`--aero-cyan`, medical=`--agency-medical`, landslide=`--orbital-amber`, earthquake=`--defcon-red` |
| BarChart nhóm máu | `--defcon-red` (thiếu) → `--life-green` (đủ) |
| Cumulative Progress | 2 line: tiếp nhận=`--orbital-amber`, đã cứu=`--life-green` |
| Force-directed Topology (D3) | node C2=`--aero-cyan`, relay=`--relay-purple`, victim theo severity, link sáng khi có packet |

**Nguyên tắc chung mọi chart:** không gridline mặc định, nền trong suốt, luôn có tooltip crosshair khi hover, luôn có Date/Time Range Selector (mục 6.2) nếu chart có trục thời gian, luôn có nút "More" ở góc panel để mở view chi tiết toàn màn hình.

---

## 9. ICON & EMOJI

Giữ emoji cho nội dung mô tả nhanh (log, danh sách, nhãn trạng thái) — đúng tinh thần "avionics status matrix". Với **nút hành động chính** (`DISPATCH`, `MUTE`, `XUẤT BÁO CÁO`, sort icon, chevron dropdown...) dùng thêm icon SVG cùng 1 bộ (Lucide/Tabler, stroke `var(--aero-cyan)`, stroke-width 1.5) để đảm bảo hiển thị nhất quán trên mọi thiết bị (emoji render khác nhau giữa Windows/macOS/trình duyệt, rủi ro cho hệ đa màn hình Laptop+iPad+điện thoại dùng song song).

---

## 10. CHECKLIST NGHIỆM THU (v2 — mở rộng)

- [ ] Không `border-radius` tròn ngoài LED/dot/avatar.
- [ ] Không màu nào ngoài token mục 3.
- [ ] Mọi KPI card có đủ 4 lớp: label → hero number (mono, glow) → delta (mũi tên + màu) → sparkline.
- [ ] Mọi chart có time-range selector + tooltip crosshair + nút "More".
- [ ] Mọi bảng có sticky header + sort icon + hover row + ít nhất 1 cột dùng mini-bar-in-cell nếu có số liệu so sánh được.
- [ ] Có đủ 5 trạng thái UI: loading (skeleton shimmer), empty, error/offline, hover, focus-visible.
- [ ] Scrollbar đã custom, không dùng thanh cuộn mặc định trình duyệt.
- [ ] Toast + Strobe cùng hoạt động khi có SOS mới (không chỉ 1 trong 2).
- [ ] Font nhúng cục bộ, không gọi CDN.
- [ ] Số liệu dùng đúng quy tắc định dạng ở mục 4 (dấu chấm hàng nghìn, delta có dấu, timestamp đúng độ chính xác theo ngữ cảnh).
- [ ] Layout đã test đủ 3 breakpoint (desktop/tablet/mobile) theo mục 5.
- [ ] Toàn bộ 8 trang dùng chung 1 file `tactical-c2.css` chứa hết token/class ở file này.

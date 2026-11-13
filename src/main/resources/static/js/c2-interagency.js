/**
 * C2 Inter-Agency Coordination Module (115 EMS, 114 Rescue, 113 Police)
 * Full Two-Way Command & Control Lifecycle Engine (Standard Operating Procedure - SOP):
 *   1. Lập Phiếu Điều Phối Điện Tử (Mission Dispatch Ticket)
 *   2. Vòng đời 4 bước: [ĐÃ PHÁT LỆNH] ──► [XUẤT KÍCH] ──► [HIỆN TRƯỜNG] ──► [BÀN GIAO HOÀN TẤT]
 *   3. Trạm Trực Bộ Đàm VHF Dã Chiến CH16 & Danh bạ khẩn cấp
 *   4. Quản trị Tài nguyên Động 15 Cơ sở Đà Nẵng (Tăng/giảm giường cấp cứu & ca-nô)
 */
const c2InterAgency = {
  currentTicketFilter: 'ALL',
  currentFacilityFilter: 'ALL',
  allTickets: [],
  allFacilities: [],
  allRadioLogs: [],

  init() {
    this.loadTickets();
    this.loadFacilities();
    this.loadRadioLogs();

    // Auto-refresh periodically every 6s
    setInterval(() => {
      this.loadTickets(true);
      this.loadRadioLogs(true);
    }, 6000);
  },

  /* ──────────────────────────── 1. TICKETS LIFECYCLE ──────────────────────────── */
  async loadTickets(silent = false) {
    try {
      const res = await fetch('/api/interagency/tickets');
      if (!res.ok) throw new Error('HTTP ' + res.status);
      this.allTickets = await res.json();
      this.updateHUDMetrics();
      this.renderTickets();
    } catch (e) {
      if (!silent) console.error('[c2InterAgency] Error loading tickets:', e);
    }
  },

  updateHUDMetrics() {
    const total = this.allTickets.length;
    const enroute = this.allTickets.filter(t => t.status === 'EN_ROUTE').length;
    const onscene = this.allTickets.filter(t => t.status === 'ON_SCENE').length;
    const handover = this.allTickets.filter(t => t.status === 'HANDOVER').length;

    const setVal = (id, val) => {
      const el = document.getElementById(id);
      if (el) el.innerText = val;
    };
    setVal('ia-kpi-total', total);
    setVal('ia-kpi-enroute', enroute);
    setVal('ia-kpi-onscene', onscene);
    setVal('ia-kpi-handover', handover);

    setVal('count-ticket-all', total);
    setVal('count-ticket-115', this.allTickets.filter(t => t.agencyType === 'EMS_115').length);
    setVal('count-ticket-114', this.allTickets.filter(t => t.agencyType === 'RESCUE_114').length);
    setVal('count-ticket-113', this.allTickets.filter(t => t.agencyType === 'POLICE_113').length);
  },

  filterTickets(type) {
    this.currentTicketFilter = type;
    document.querySelectorAll('#btn-filter-ticket-all, #btn-filter-ticket-115, #btn-filter-ticket-114, #btn-filter-ticket-113')
      .forEach(btn => btn.classList.remove('active'));
    const activeBtn = document.getElementById(`btn-filter-ticket-${type.toLowerCase().replace('_', '')}`);
    if (activeBtn) activeBtn.classList.add('active');
    this.renderTickets();
  },

  renderTickets() {
    const container = document.getElementById('interagency-tickets-container');
    if (!container) return;

    let list = this.allTickets;
    if (this.currentTicketFilter !== 'ALL') {
      list = list.filter(t => t.agencyType === this.currentTicketFilter);
    }

    if (!list.length) {
      container.innerHTML = '<div class="state-empty"><p class="state-title">KHÔNG CÓ PHIẾU ĐIỀU PHỐI PHÙ HỢP</p><p style="color:var(--text-muted);font-size:11px;margin-top:4px">Bấm nút "+ LẬP PHIẾU ĐIỀU PHỐI MỚI" để phát lệnh chi viện 115/114/113</p></div>';
      return;
    }

    const badgeCfg = {
      EMS_115:    { cls: 'badge-agency--medical', label: '115 Y TẾ' },
      RESCUE_114: { cls: 'badge-agency--rescue',  label: '114 CỨU HỎA' },
      POLICE_113: { cls: 'badge-agency--police',  label: '113 CÔNG AN' }
    };

    container.innerHTML = list.map(t => {
      const b = badgeCfg[t.agencyType] || badgeCfg.EMS_115;
      const timeStr = new Date(Number(t.dispatchedAt || Date.now())).toLocaleTimeString('vi-VN');
      const st = t.status || 'DISPATCHED';

      // Stepper logic
      const isS1 = true;
      const isS2 = st === 'EN_ROUTE' || st === 'ON_SCENE' || st === 'HANDOVER';
      const isS3 = st === 'ON_SCENE' || st === 'HANDOVER';
      const isS4 = st === 'HANDOVER';

      // Action toolbar based on current status
      let actionHtml = '';
      if (st === 'DISPATCHED' || st === 'SENT') {
        actionHtml = `
          <button class="btn-tactical" onclick="c2InterAgency.openAdvanceModal(${t.id}, 'EN_ROUTE', '${t.agencyType}')" style="color:var(--orbital-amber);border-color:var(--orbital-amber)">
            ⚡ XÁC NHẬN XE/CA-NÔ XUẤT PHÁT
          </button>
          <button class="btn-tactical" onclick="c2InterAgency.printHandoverDossier(${t.id})" style="margin-left:6px">
            📋 IN PHIẾU
          </button>
        `;
      } else if (st === 'EN_ROUTE') {
        actionHtml = `
          <button class="btn-tactical" onclick="c2InterAgency.advanceTicketDirect(${t.id}, 'ON_SCENE')" style="color:#00F0FF;border-color:#00F0FF">
            📍 BÁO ĐÃ TIẾP CẬN HIỆN TRƯỜNG
          </button>
          <button class="btn-tactical" onclick="c2InterAgency.openAdvanceModal(${t.id}, 'EN_ROUTE', '${t.agencyType}')" style="margin-left:6px">
            ↺ CẬP NHẬT ETA
          </button>
        `;
      } else if (st === 'ON_SCENE') {
        actionHtml = `
          <button class="btn-tactical success" onclick="c2InterAgency.advanceTicketDirect(${t.id}, 'HANDOVER')">
            ✅ BÀN GIAO VIỆN HOÀN TẤT
          </button>
          <button class="btn-tactical" onclick="c2InterAgency.printHandoverDossier(${t.id})" style="margin-left:6px">
            📋 IN BIÊN BẢN
          </button>
        `;
      } else {
        actionHtml = `
          <span style="color:var(--life-green);font:700 11px var(--font-sans);display:inline-flex;align-items:center;gap:4px">
            ✓ NHIỆM VỤ HOÀN TẤT AN TOÀN
          </span>
          <button class="btn-tactical" onclick="c2InterAgency.printHandoverDossier(${t.id})" style="margin-left:8px;padding:3px 8px;font-size:9px">
            📋 BIÊN BẢN BÀN GIAO
          </button>
        `;
      }

      return `
        <div class="ticket-mission-card status-${st}">
          <div style="display:flex;justify-content:space-between;align-items:center">
            <div style="display:flex;align-items:center;gap:8px">
              <strong style="color:var(--aero-cyan);font:700 13px var(--font-mono)">#ORD-${t.id}</strong>
              <span class="badge-agency ${b.cls}">${b.label}</span>
              <strong style="color:#fff;font:600 12px var(--font-sans)">${t.victimNodeId}</strong>
            </div>
            <div style="font:400 10px var(--font-mono);color:var(--text-muted)">
              Khởi tạo: <strong style="color:#fff">${timeStr}</strong>
            </div>
          </div>

          <!-- 4-Step Operational Stepper -->
          <div class="interagency-stepper">
            <div class="stepper-step ${isS1 ? (st === 'DISPATCHED' ? 'active' : 'completed') : ''}">
              <div class="stepper-dot">1</div>
              <span>PHÁT LỆNH</span>
            </div>
            <div class="stepper-divider ${isS2 ? 'completed' : ''}"></div>
            <div class="stepper-step ${isS2 ? (st === 'EN_ROUTE' ? 'active' : 'completed') : ''}">
              <div class="stepper-dot">2</div>
              <span>XUẤT KÍCH</span>
            </div>
            <div class="stepper-divider ${isS3 ? 'completed' : ''}"></div>
            <div class="stepper-step ${isS3 ? (st === 'ON_SCENE' ? 'active' : 'completed') : ''}">
              <div class="stepper-dot">3</div>
              <span>HIỆN TRƯỜNG</span>
            </div>
            <div class="stepper-divider ${isS4 ? 'completed' : ''}"></div>
            <div class="stepper-step ${isS4 ? 'completed active' : ''}">
              <div class="stepper-dot">4</div>
              <span>BÀN GIAO</span>
            </div>
          </div>

          <!-- Mission Telemetry Details -->
          <div style="display:grid;grid-template-columns:repeat(2, 1fr);gap:6px 12px;font:400 11px var(--font-sans);color:var(--text-secondary);background:rgba(0,0,0,0.25);padding:8px 10px;border-radius:2px">
            <div>Cơ sở tiếp nhận: <strong style="color:#fff">${t.agencyName}</strong></div>
            <div>Phương tiện / Đội: <strong style="color:var(--aero-cyan)">${t.responderUnit || 'Đang điều động'}</strong></div>
            <div>Đội trưởng / BS: <strong style="color:#fff">${t.responderLeader || 'Chưa gán'}</strong></div>
            <div>Thời gian tiếp cận: <strong style="color:var(--orbital-amber)">${t.etaMinutes ? '~' + t.etaMinutes + ' phút' : 'Chưa có ETA'}</strong></div>
            <div style="grid-column:span 2;color:var(--text-muted);font-size:10px">
              Yêu cầu đặc biệt: <span style="color:#FFD000">${t.specialNeeds || 'Không có'}</span>
            </div>
          </div>

          <!-- Operator Actions -->
          <div style="margin-top:10px;display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:6px">
            <button class="btn-tactical" onclick="c2InterAgency.viewTicketOnMap('${t.victimNodeId}')" style="color:var(--aero-cyan);border-color:rgba(0,240,255,.3);font-size:10px;padding:5px 8px">
              <svg class="btn-icon" viewBox="0 0 24 24" width="11" height="11" stroke-width="2"><polygon points="1 6 1 22 8 18 16 22 23 18 23 2 16 6 8 2 1 6"/></svg>
              📍 XEM TUYẾN ĐƯỜNG TRÊN BẢN ĐỒ
            </button>
            <div style="display:flex;align-items:center;gap:6px">
              ${actionHtml}
            </div>
          </div>
        </div>
      `;
    }).join('');
  },

  /* ──────────────────────────── 2. TICKET CREATION MODAL ──────────────────────────── */
  openCreateTicketModal() {
    const modal = document.getElementById('interagency-ticket-modal');
    const selVictim = document.getElementById('modal-ticket-victim');
    if (!modal || !selVictim) return;

    let incidents = [];
    if (window.c2Home && c2Home.incidents) {
      incidents = c2Home.incidents.filter(i => i.status !== 'RESOLVED');
    }

    if (!incidents.length) {
      c2App.showToast('Không có ca nạn nhân nào đang chờ cứu để lập phiếu!', 'warning');
      return;
    }

    selVictim.innerHTML = incidents.map(v => `
      <option value="${v.nodeId}" data-blood="${v.bloodType || 'O+'}" data-alert="${v.alertType}" data-name="${v.fullName}">
        ${v.nodeId} — ${v.fullName || 'Nạn nhân'} (${v.alertType} • Máu ${v.bloodType || 'O+'})
      </option>
    `).join('');

    this.onAgencyTypeChange();
    this.onVictimSelectChange();
    modal.classList.add('show');
  },

  closeCreateTicketModal() {
    document.getElementById('interagency-ticket-modal')?.classList.remove('show');
  },

  onAgencyTypeChange() {
    const agencyType = document.getElementById('modal-ticket-agency').value;
    const selFac = document.getElementById('modal-ticket-facility');
    const selVeh = document.getElementById('modal-ticket-vehicle');
    if (!selFac) return;

    let targetType = 'HOSPITAL';
    let vehicles = [];

    if (agencyType === 'EMS_115') {
      targetType = 'HOSPITAL';
      vehicles = [
        'Xe cấp cứu hồi sức chuyên dụng (115)',
        'Xe cấp cứu lưu động tiêu chuẩn',
        'Tổ y tế phản ứng nhanh với máy thở xách tay'
      ];
    } else if (agencyType === 'RESCUE_114') {
      targetType = 'FIRE_STATION';
      vehicles = [
        'Ca-nô cao tốc cứu nạn đường sông Sông Hàn (114)',
        'Xuồng cao su động cơ chân vịt cạn luồn lách (114)',
        'Xe cứu nạn cứu hộ lội nước bánh xích'
      ];
    } else {
      targetType = 'POLICE_POST';
      vehicles = [
        'Xe bán tải đặc chủng phân luồng giao thông',
        'Tổ tuần tra bảo vệ hiện trường & rào chắn cầu',
        'Đội CSGT dẫn đường đoàn xe cấp cứu'
      ];
    }

    const filteredFacs = this.allFacilities.filter(f => f.facilityType === targetType);
    selFac.innerHTML = filteredFacs.map(f => `
      <option value="${f.facilityName}">${f.facilityName} (${f.availableCapacity} ca khả dụng)</option>
    `).join('');

    if (selVeh) {
      selVeh.innerHTML = vehicles.map(v => `<option value="${v}">${v}</option>`).join('');
    }
  },

  onVictimSelectChange() {
    const sel = document.getElementById('modal-ticket-victim');
    const opt = sel.options[sel.selectedIndex];
    if (!opt) return;
    const blood = opt.getAttribute('data-blood');
    const alert = opt.getAttribute('data-alert');
    const needsInput = document.getElementById('modal-ticket-needs');
    if (needsInput) {
      needsInput.value = `Chuẩn bị 2 bịch máu ${blood} và bình oxy dã chiến trước 15 phút. Nạn nhân ${alert}`;
    }
  },

  async submitCreateTicket() {
    const victimNodeId = document.getElementById('modal-ticket-victim')?.value;
    const agencyType = document.getElementById('modal-ticket-agency')?.value;
    const agencyName = document.getElementById('modal-ticket-facility')?.value;
    const requiredVehicle = document.getElementById('modal-ticket-vehicle')?.value;
    const specialNeeds = document.getElementById('modal-ticket-needs')?.value;
    const priority = document.getElementById('modal-ticket-priority')?.value;

    if (!victimNodeId) {
      c2App.showToast('Vui lòng chọn nạn nhân!', 'warning');
      return;
    }

    try {
      const res = await fetch('/api/interagency/ticket/create', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          victimNodeId, agencyType, agencyName, requiredVehicle, specialNeeds, priority
        })
      });
      if (!res.ok) throw new Error('HTTP ' + res.status);
      const data = await res.json();
      c2App.showToast(`ĐÃ PHÁT LỆNH ĐIỀU PHỐI ĐIỆN TỬ #${data.ticketId} CHO ${victimNodeId}!`, 'success');
      this.closeCreateTicketModal();
      this.loadTickets();
      this.loadRadioLogs();
      if (window.c2Home) c2Home.refreshIncidents();
    } catch (e) {
      c2App.showToast('Lỗi lập phiếu điều phối: ' + e.message, 'danger');
    }
  },

  /* ──────────────────────────── 3. TICKET ADVANCE MODAL ──────────────────────────── */
  openAdvanceModal(ticketId, nextStatus, agencyType) {
    const modal = document.getElementById('ticket-advance-modal');
    if (!modal) return;
    document.getElementById('advance-ticket-id').value = ticketId;
    document.getElementById('advance-next-status').value = nextStatus;

    // Default suggestions based on agency
    const unitInput = document.getElementById('advance-unit');
    const leaderInput = document.getElementById('advance-leader');
    const phoneInput = document.getElementById('advance-phone');
    const etaInput = document.getElementById('advance-eta');

    if (agencyType === 'RESCUE_114') {
      unitInput.value = 'Ca-nô 114 Sông Hàn (Số 03)';
      leaderInput.value = 'Đ/c Trần Quốc Tuấn (Đội trưởng CNCH)';
      phoneInput.value = '0913.114.114';
      etaInput.value = 5;
    } else if (agencyType === 'POLICE_113') {
      unitInput.value = 'Tổ tuần tra CA Quận Hải Châu';
      leaderInput.value = 'Trung tá Lê Hoàng Long';
      phoneInput.value = '0905.113.113';
      etaInput.value = 4;
    } else {
      unitInput.value = 'Xe 115-02 (BV Đà Nẵng)';
      leaderInput.value = 'BS. Nguyễn Văn Nam';
      phoneInput.value = '0914.382.115';
      etaInput.value = 7;
    }

    modal.classList.add('show');
  },

  closeAdvanceModal() {
    document.getElementById('ticket-advance-modal')?.classList.remove('show');
  },

  async confirmAdvanceTicket() {
    const id = document.getElementById('advance-ticket-id').value;
    const status = document.getElementById('advance-next-status').value;
    const unit = document.getElementById('advance-unit').value;
    const leader = document.getElementById('advance-leader').value;
    const phone = document.getElementById('advance-phone').value;
    const eta = document.getElementById('advance-eta').value;

    try {
      const res = await fetch(`/api/interagency/ticket/${id}/advance`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ status, unit, leader, phone, eta })
      });
      if (!res.ok) throw new Error('HTTP ' + res.status);
      c2App.showToast(`XÁC NHẬN XUẤT KÍCH: ${unit} (ETA ~${eta} phút)`, 'success');
      this.closeAdvanceModal();
      this.loadTickets();
      this.loadRadioLogs();
      if (window.c2Home) c2Home.refreshIncidents();
    } catch (e) {
      c2App.showToast('Lỗi cập nhật xuất kích: ' + e.message, 'danger');
    }
  },

  async advanceTicketDirect(ticketId, nextStatus) {
    try {
      const res = await fetch(`/api/interagency/ticket/${ticketId}/advance`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ status: nextStatus })
      });
      if (!res.ok) throw new Error('HTTP ' + res.status);
      const msg = nextStatus === 'ON_SCENE' ? 'ĐÃ TIẾP CẬN NẠN NHÂN TẠI HIỆN TRƯỜNG!' : 'BÀN GIAO VIỆN HOÀN TẤT THÀNH CÔNG!';
      c2App.showToast(msg, 'success');
      this.loadTickets();
      this.loadFacilities();
      this.loadRadioLogs();
      if (window.c2Home) c2Home.refreshIncidents();
    } catch (e) {
      c2App.showToast('Lỗi cập nhật tiến trình: ' + e.message, 'danger');
    }
  },

  printHandoverDossier(ticketId) {
    const t = this.allTickets.find(item => item.id == ticketId);
    if (!t) return;
    const printWin = window.open('', '_blank', 'width=650,height=750');
    printWin.document.write(`
      <html>
        <head>
          <title>BIÊN BẢN BÀN GIAO CẤP CỨU DÃ CHIẾN #ORD-${t.id}</title>
          <style>
            body { font-family: 'Courier New', monospace; padding: 24px; color: #000; line-height: 1.5; }
            h2 { text-align: center; border-bottom: 2px solid #000; padding-bottom: 8px; margin-bottom: 16px; }
            table { width: 100%; border-collapse: collapse; margin-top: 12px; }
            th, td { border: 1px solid #000; padding: 8px; text-align: left; }
            .footer { margin-top: 30px; display: flex; justify-content: space-between; text-align: center; }
          </style>
        </head>
        <body>
          <h2>PHIẾU BÀN GIAO ĐIỀU PHỐI CỨU NẠN LIÊN NGÀNH</h2>
          <p><strong>Mã Phiếu:</strong> #ORD-${t.id} | <strong>Thời điểm:</strong> ${new Date(t.dispatchedAt).toLocaleString('vi-VN')}</p>
          <p><strong>Mã Nạn Nhân (Mesh):</strong> ${t.victimNodeId} | <strong>Cơ quan tiếp nhận:</strong> ${t.agencyName}</p>
          <table>
            <tr><th>Đội phản ứng nhanh:</th><td>${t.responderUnit || 'Chưa phân công'}</td></tr>
            <tr><th>Đội trưởng / Bác sĩ:</th><td>${t.responderLeader || 'N/A'}</td></tr>
            <tr><th>SĐT liên hệ:</th><td>${t.contactPhone || 'N/A'}</td></tr>
            <tr><th>Trạng thái hiện tại:</th><td>${t.status}</td></tr>
            <tr><th>Nhu cầu đặc biệt:</th><td>${t.specialNeeds}</td></tr>
          </table>
          <div class="footer">
            <div><strong>TRẠM TRƯỞNG C2 DÃ CHIẾN</strong><br><br><br>(Đã ký điện tử)</div>
            <div><strong>ĐẠI DIỆN ĐƠN VỊ TIẾP NHẬN</strong><br><br><br>(Xác nhận bàn giao)</div>
          </div>
        </body>
      </html>
    `);
    printWin.document.close();
    printWin.focus();
    printWin.print();
  },

  /* ──────────────────────────── 4. TACTICAL RADIO COMMS ──────────────────────────── */
  async loadRadioLogs(silent = false) {
    try {
      const res = await fetch('/api/interagency/radio-logs');
      if (!res.ok) throw new Error('HTTP ' + res.status);
      this.allRadioLogs = await res.json();
      this.renderRadioLogs();
    } catch (e) {
      if (!silent) console.error('[c2InterAgency] Error radio logs:', e);
    }
  },

  renderRadioLogs() {
    const consoleEl = document.getElementById('radio-console-logs');
    if (!consoleEl) return;

    if (!this.allRadioLogs.length) {
      consoleEl.innerHTML = '<div style="color:var(--text-muted);text-align:center;padding:20px">Chưa có khẩu lệnh trên Kênh CH 16</div>';
      return;
    }

    consoleEl.innerHTML = this.allRadioLogs.map(r => {
      const timeStr = new Date(Number(r.timestamp)).toLocaleTimeString('vi-VN');
      const urgencyClass = `urgency-${r.urgency || 'ROUTINE'}`;
      return `
        <div class="radio-msg-item ${urgencyClass}">
          <div class="radio-header-line">
            <span class="radio-sender-tag">[${r.channel}] ${r.sender} ──► ${r.recipient}</span>
            <span>${timeStr}</span>
          </div>
          <div class="radio-msg-text">${r.message}</div>
        </div>
      `;
    }).join('');
  },

  async sendRadioMessage() {
    const input = document.getElementById('radio-input-message');
    const msg = input?.value.trim();
    if (!msg) return;

    try {
      const res = await fetch('/api/interagency/radio-logs', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          channel: 'CH 16 (156.8 MHz)',
          sender: 'C2-COMMAND',
          recipient: 'ALL_STATIONS',
          message: msg,
          urgency: 'ROUTINE'
        })
      });
      if (!res.ok) throw new Error('HTTP ' + res.status);
      input.value = '';
      this.loadRadioLogs();
      c2App.showToast('Đã phát khẩu lệnh qua Tần số Chỉ huy CH 16!', 'info');
    } catch (e) {
      c2App.showToast('Lỗi phát đàm thoại: ' + e.message, 'danger');
    }
  },

  triggerRadioAlert() {
    const sampleAlerts = [
      'BÁO ĐỘNG CẤP 1: Nước sông Hàn dâng thêm 40cm, yêu cầu toàn bộ ca-nô 114 neo đậu vùng an toàn!',
      'YÊU CẦU 115 ĐÀ NẴNG: Dự trù sẵn 4 bịch máu O- và 2 máy sốc tim tại sảnh cấp cứu!',
      'CẢNH BÁO 113: Phong tỏa lập rào chắn ngã tư Nguyễn Tri Phương - Duy Tân, cấm xe dân sự!'
    ];
    const picked = sampleAlerts[Math.floor(Math.random() * sampleAlerts.length)];
    document.getElementById('radio-input-message').value = picked;
    this.sendRadioMessage();
  },

  /* ──────────────────────────── 5. FACILITIES LIVE GRID & CAPACITY ──────────────────────────── */
  async loadFacilities() {
    try {
      const res = await fetch('/api/facilities');
      this.allFacilities = await res.json();
      this.renderFacilitiesGrid();
    } catch (e) {
      console.error('[c2InterAgency] Error loading facilities:', e);
    }
  },

  currentDistrictFilter: 'ALL',
  facilitySearchQuery: '',

  viewTicketOnMap(victimNodeId) {
    if (!victimNodeId) return;
    c2App.switchTab('map');
    setTimeout(() => {
      if (window.c2Map) {
        c2Map.focusAndRoute(victimNodeId);
      }
    }, 250);
  },

  onSearchInput(val) {
    this.facilitySearchQuery = (val || '').trim().toLowerCase();
    this.renderFacilitiesGrid();
  },

  onDistrictChange(val) {
    this.currentDistrictFilter = val || 'ALL';
    this.renderFacilitiesGrid();
  },

  filterFacilities(type, btn) {
    this.currentFacilityFilter = type;
    if (btn) {
      btn.parentElement.querySelectorAll('button').forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
    }
    this.renderFacilitiesGrid();
  },

  renderFacilitiesGrid() {
    const grid = document.getElementById('facilities-mgmt-grid');
    if (!grid) return;

    let list = this.allFacilities || [];

    // Filter by Agency Type
    if (this.currentFacilityFilter !== 'ALL') {
      list = list.filter(f => f.facilityType === this.currentFacilityFilter);
    }

    // Filter by District
    if (this.currentDistrictFilter !== 'ALL') {
      list = list.filter(f => f.district === this.currentDistrictFilter);
    }

    // Filter by Search Query
    if (this.facilitySearchQuery) {
      const q = this.facilitySearchQuery;
      list = list.filter(f => 
        (f.facilityName && f.facilityName.toLowerCase().includes(q)) ||
        (f.address && f.address.toLowerCase().includes(q)) ||
        (f.district && f.district.toLowerCase().includes(q)) ||
        (f.contactPhone && f.contactPhone.includes(q))
      );
    }

    // Update header count badges
    const totalCount = this.allFacilities ? this.allFacilities.length : 25;
    const hospCount = this.allFacilities ? this.allFacilities.filter(f => f.facilityType === 'HOSPITAL').length : 10;
    const fireCount = this.allFacilities ? this.allFacilities.filter(f => f.facilityType === 'FIRE_STATION').length : 7;
    const policeCount = this.allFacilities ? this.allFacilities.filter(f => f.facilityType === 'POLICE_POST').length : 8;

    const btnAll = document.getElementById('btn-fac-filter-all');
    const btnHosp = document.getElementById('btn-fac-filter-hosp');
    const btnFire = document.getElementById('btn-fac-filter-fire');
    const btnPolice = document.getElementById('btn-fac-filter-police');
    if (btnAll) btnAll.textContent = `TẤT CẢ (${totalCount})`;
    if (btnHosp) btnHosp.textContent = `Y TẾ 115 (${hospCount})`;
    if (btnFire) btnFire.textContent = `CỨU HỎA 114 (${fireCount})`;
    if (btnPolice) btnPolice.textContent = `CÔNG AN 113 (${policeCount})`;

    if (!list.length) {
      grid.innerHTML = '<div class="state-empty" style="grid-column:1/-1;padding:30px"><p class="state-title">KHÔNG TÌM THẤY CƠ SỞ PHÙ HỢP</p><p style="color:var(--text-muted);font-size:11px;margin-top:4px">Thử đổi từ khóa tìm kiếm hoặc chọn quận/huyện khác</p></div>';
      return;
    }

    const typeMeta = {
      HOSPITAL: {
        tag: '115 Y TẾ',
        badge: 'badge-agency--medical',
        image: '/images/facilities/hosp_danang.jpg',
        color: 'var(--agency-medical)'
      },
      FIRE_STATION: {
        tag: '114 CỨU HỎA',
        badge: 'badge-agency--rescue',
        image: '/images/facilities/fire_catp_hq.jpg',
        color: 'var(--agency-rescue)'
      },
      POLICE_POST: {
        tag: '113 AN NINH',
        badge: 'badge-agency--police',
        image: '/images/facilities/police_113_hq.jpg',
        color: 'var(--agency-police)'
      }
    };

    grid.innerHTML = list.map(f => {
      const meta = typeMeta[f.facilityType] || typeMeta.HOSPITAL;
      const isLow = f.availableCapacity <= 3;
      const imgPath = f.imageUrl || meta.image;
      const addressDisplay = f.address ? `${f.address}` : 'TP. Đà Nẵng';
      const districtTag = f.district ? `<span style="color:var(--aero-cyan);font-weight:600">• ${f.district}</span>` : '';

      return `
        <div class="facility-mgmt-card">
          <div class="facility-thumb-box">
            <img src="${imgPath}" alt="${f.facilityName}" loading="lazy">
            <div style="position:absolute;inset:0;background:linear-gradient(to top, rgba(8,12,22,1) 0%, transparent 60%)"></div>
            <span class="badge-agency ${meta.badge}" style="position:absolute;top:6px;left:6px;font-size:9px">${meta.tag} ${f.district ? '• ' + f.district : ''}</span>
          </div>
          <div style="padding:10px 12px;display:flex;flex-direction:column;flex:1">
            <strong style="color:#fff;font:600 12px var(--font-sans);line-height:1.3;margin-bottom:4px">${f.facilityName}</strong>
            <div style="font:400 10px var(--font-sans);color:var(--text-muted);margin-bottom:4px">
              📍 ${addressDisplay} ${districtTag}
            </div>
            <div style="font:400 10px var(--font-sans);color:var(--text-secondary)">
              Hotline: <strong style="color:var(--life-green)"><a href="tel:${f.contactPhone}" style="color:var(--life-green);text-decoration:none">📞 ${f.contactPhone}</a></strong>
            </div>
            <div style="font:400 9px var(--font-mono);color:var(--text-muted);margin-top:2px">
              GPS: [${f.latitude.toFixed(4)}°N, ${f.longitude.toFixed(4)}°E]
            </div>

            <!-- Capacity Adjuster -->
            <div style="margin-top:auto;padding-top:8px;border-top:1px solid var(--border-subtle);display:flex;justify-content:space-between;align-items:center">
              <div>
                <span style="font-size:10px;color:var(--text-muted)">Khả dụng: </span>
                <strong style="color:${isLow ? 'var(--defcon-red)' : meta.color};font-size:13px">${f.availableCapacity} ca</strong>
              </div>
              <div class="capacity-controller">
                <button class="btn-cap-step" onclick="c2InterAgency.adjustCapacity('${f.facilityId}', -1)" title="Trừ 1 ca">-</button>
                <button class="btn-cap-step" onclick="c2InterAgency.adjustCapacity('${f.facilityId}', 1)" title="Cộng 1 ca">+</button>
              </div>
            </div>
          </div>
        </div>
      `;
    }).join('');
  },

  async adjustCapacity(facilityId, delta) {
    try {
      const res = await fetch(`/api/interagency/facilities/${facilityId}/capacity`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ delta })
      });
      if (!res.ok) throw new Error('HTTP ' + res.status);
      c2App.showToast(`Đã cập nhật dung lượng cơ sở ${facilityId} (${delta > 0 ? '+' : ''}${delta})`, 'info');
      this.loadFacilities();
    } catch (e) {
      c2App.showToast('Lỗi đổi sức chứa: ' + e.message, 'danger');
    }
  }
};

window.c2InterAgency = c2InterAgency;

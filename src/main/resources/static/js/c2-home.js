/**
 * C2 Home Dashboard — KPI Telemetry with Data-Dense Cards (delta + sparkline), QR & Priority Feed
 */
const c2Home = {
  incidents: [],
  kpiHistory: { total: [], critical: [], dispatched: [], resolved: [] },
  prevStats: null,

  init() {
    this.initQrCode();
    this.refreshIncidents();
    setInterval(() => this.refreshIncidents(), 3000);
  },

  async initQrCode() {
    const box = document.getElementById('qrcode-box');
    if (!box || !window.QRCode) return;
    box.innerHTML = '';
    let url = window.location.origin;
    try {
      const res = await fetch('/api/devices/qr-config');
      if (res.ok) {
        const cfg = await res.json();
        if (cfg.primaryLanIp && (window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1')) {
          const port = window.location.port ? `:${window.location.port}` : '';
          url = `${window.location.protocol}//${cfg.primaryLanIp}${port}`;
        }
      }
    } catch (ignored) {}

    const textEl = document.getElementById('qr-url-text');
    if (textEl) textEl.textContent = url;
    new QRCode(box, { text: url, width: 128, height: 128, colorDark: '#05070B', colorLight: '#FFFFFF', correctLevel: QRCode.CorrectLevel.M });
  },

  async refreshIncidents() {
    try {
      // Fetch stats
      const rs = await fetch('/api/victims/stats');
      const stats = await rs.json();

      this.updateKpiCard('kpi-total', stats.total, 'total', 'cyan');
      this.updateKpiCard('kpi-critical', stats.critical, 'critical', 'defcon');
      this.updateKpiCard('kpi-dispatched', stats.dispatched, 'dispatched', 'amber');
      this.updateKpiCard('kpi-resolved', stats.resolved, 'resolved', 'green');

      // Update resolved rate text
      const resolvedEl = document.getElementById('kpi-resolved');
      if (resolvedEl) resolvedEl.textContent = `${c2App.formatNumber(stats.resolved)}`;
      const resDelta = document.getElementById('kpi-resolved-delta');
      if (resDelta) resDelta.textContent = `${stats.resolveRate || 0}% hoàn thành`;

      this.prevStats = stats;

      // Fetch priority
      const rp = await fetch('/api/victims/priority');
      this.renderPriorityFeed(await rp.json());

      // Fetch all
      const ra = await fetch('/api/victims');
      this.incidents = await ra.json();
      this.renderAllIncidents(this.incidents);

      if (window.c2Map) c2Map.renderVictimMarkers(this.incidents);
      if (window.c2Triage) c2Triage.updateFromIncidents(this.incidents);
      if (window.c2Kanban) c2Kanban.render(this.incidents);
    } catch (e) {
      console.error('[c2Home] refresh error:', e);
    }
  },

  updateKpiCard(elId, value, historyKey, colorClass) {
    const el = document.getElementById(elId);
    if (el) el.textContent = c2App.formatNumber(value);

    // Track history for sparkline
    const h = this.kpiHistory[historyKey];
    h.push(value);
    if (h.length > 12) h.shift();

    // Delta calculation
    const deltaEl = document.getElementById(`${elId}-delta`);
    if (deltaEl && h.length >= 2) {
      const diff = value - h[h.length - 2];
      const sign = diff > 0 ? '+' : '';
      deltaEl.className = `kpi-delta ${diff > 0 ? 'kpi-delta--up' : diff < 0 ? 'kpi-delta--down' : 'kpi-delta--neutral'}`;
      deltaEl.innerHTML = `${diff > 0 ? '▲' : diff < 0 ? '▼' : '—'} ${sign}${diff} <span class="kpi-delta-note">/ cập nhật</span>`;
    }

    // Sparkline SVG
    const spark = document.getElementById(`${elId}-spark`);
    if (spark && h.length > 1) {
      const path = spark.querySelector('path');
      if (path) {
        const max = Math.max(...h, 1);
        const points = h.map((v, i) => {
          const x = (i / (h.length - 1)) * 120;
          const y = 26 - (v / max) * 24;
          return `${x},${y}`;
        });
        path.setAttribute('d', `M${points.join(' L')}`);
      }
    }
  },

  renderPriorityFeed(list) {
    const tbody = document.getElementById('priority-feed-tbody');
    if (!tbody) return;

    if (!list.length) {
      tbody.innerHTML = '<tr><td colspan="5"><div class="state-empty"><p class="state-title">KHÔNG CÓ CA NGUY CẤP</p><p class="state-desc">Tất cả tín hiệu khẩn cấp đã được kiểm soát</p></div></td></tr>';
      return;
    }

    tbody.innerHTML = list.map(item => {
      const sev = item.severity === 'CRITICAL'
        ? '<span class="badge critical"><span class="status-led status-led--critical" style="margin-right:4px"></span>NGUY KỊCH</span>'
        : `<span class="badge high">${item.severity}</span>`;
      return `<tr>
        <td><strong style="color:var(--aero-cyan)">${item.nodeId}</strong></td>
        <td class="text-cell"><strong>${item.fullName}</strong></td>
        <td class="text-cell">${item.alertType}</td>
        <td>${sev}</td>
        <td><button class="btn-tactical danger" style="padding:3px 8px;font-size:10px" onclick="c2Home.openDispatchModal('${item.nodeId}')"><svg class="btn-icon" viewBox="0 0 24 24" width="10" height="10" stroke-width="2"><polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/></svg> ĐIỀU PHỐI</button></td>
      </tr>`;
    }).join('');
  },

  renderAllIncidents(list) {
    const tbody = document.getElementById('all-incidents-tbody');
    if (!tbody) return;

    if (!list.length) {
      tbody.innerHTML = '<tr><td colspan="7"><div class="state-empty"><p class="state-title">CHƯA CÓ TÍN HIỆU SOS</p></div></td></tr>';
      return;
    }

    tbody.innerHTML = list.map(item => {
      const statusMap = {
        PENDING: '<span class="badge pending">CHỜ CỨU</span>',
        DISPATCHED: '<span class="badge dispatched">ĐANG TIẾP CẬN</span>',
        RESOLVED: '<span class="badge resolved">ĐÃ CỨU</span>'
      };

      let action = '';
      if (item.status === 'PENDING') {
        action = `
          <button class="btn-tactical danger" style="padding:3px 7px;font-size:9px" onclick="c2Home.openDispatchModal('${item.nodeId}')">⚡ ĐIỀU PHỐI</button>
          <button class="btn-tactical success" style="padding:3px 7px;font-size:9px;margin-left:4px" onclick="c2Home.updateStatus('${item.nodeId}', 'RESOLVED')">✓ ĐÃ CỨU</button>
        `;
      } else if (item.status === 'DISPATCHED') {
        action = `
          <button class="btn-tactical success" style="padding:3px 7px;font-size:9px" onclick="c2Home.updateStatus('${item.nodeId}', 'RESOLVED')">✓ ĐÃ CỨU</button>
          <button class="btn-tactical" style="padding:3px 7px;font-size:9px;margin-left:4px" onclick="c2Home.updateStatus('${item.nodeId}', 'PENDING')">↺ ĐẶT LẠI</button>
        `;
      } else {
        action = `
          <span style="color:var(--life-green);font-size:10px;font-weight:700">HOÀN THÀNH</span>
          <button class="btn-tactical" style="padding:2px 6px;font-size:8px;margin-left:6px" onclick="c2Home.updateStatus('${item.nodeId}', 'PENDING')">↺ MỞ LẠI</button>
        `;
      }

      const dossierBtn = `<button class="btn-tactical" style="padding:3px 6px;font-size:9px;margin-right:4px" onclick="c2Dossier.openDossier('${item.nodeId}')">📁 HỒ SƠ</button>`;

      return `<tr>
        <td><strong style="color:var(--aero-cyan)">${item.nodeId}</strong></td>
        <td class="text-cell">${item.fullName}</td>
        <td>${item.phone || 'N/A'}</td>
        <td><strong style="color:var(--defcon-red)">${item.bloodType || 'O+'}</strong></td>
        <td class="text-cell">${item.alertType}</td>
        <td>${statusMap[item.status] || item.status}</td>
        <td>${dossierBtn}${action}</td>
      </tr>`;
    }).join('');
  },

  /* ─── Dispatch Modal with Double Confirmation (NASA Launch Sequence) ─── */
  openDispatchModal(nodeId) {
    const modal = document.getElementById('dispatch-modal');
    const nodeInput = document.getElementById('dispatch-node-id');
    const cmdInput = document.getElementById('dispatch-command');
    const confirmBtn = document.getElementById('dispatch-confirm-btn');
    const cancelBtn = document.getElementById('dispatch-cancel-btn');

    if (!modal) return;
    nodeInput.value = nodeId;
    cmdInput.value = 'Ca-nô số 01 và bác sĩ xuất kích khẩn cấp';
    modal.classList.add('show');

    // Reset confirm button state
    confirmBtn.textContent = '⚡ XÁC NHẬN ĐIỀU PHỐI';
    confirmBtn.disabled = false;
    confirmBtn.classList.remove('confirm-armed');
    let armed = false;
    let countdownTimer = null;

    const confirm = async () => {
      if (!armed) {
        // Phase 1: Arm — start 3-second countdown
        armed = true;
        confirmBtn.classList.add('confirm-armed');
        let countdown = 3;
        confirmBtn.textContent = `🔴 XÁC NHẬN LẦN 2 (${countdown}s)`;
        countdownTimer = setInterval(() => {
          countdown--;
          if (countdown <= 0) {
            clearInterval(countdownTimer);
            confirmBtn.textContent = '🔴 NHẤN ĐỂ PHÁT LỆNH';
          } else {
            confirmBtn.textContent = `🔴 XÁC NHẬN LẦN 2 (${countdown}s)`;
          }
        }, 1000);
        return;
      }

      // Phase 2: Execute dispatch
      clearInterval(countdownTimer);
      confirmBtn.disabled = true;
      confirmBtn.textContent = '⏳ ĐANG PHÁT LỆNH...';
      try {
        const res = await fetch('/api/dispatch', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ targetNodeId: nodeId, command: cmdInput.value, severity: 'CRITICAL' })
        });
        if (!res.ok) throw new Error('HTTP ' + res.status);
        c2App.showToast(`✅ Đã phát lệnh điều phối đến ${nodeId}!`, 'success');
        if (window.c2Audio) c2Audio.triggerSosAlarm(1);
        this.refreshIncidents();
      } catch (e) {
        c2App.showToast('Lỗi phát lệnh: ' + e.message, 'danger');
      }
      modal.classList.remove('show');
      cleanup();
    };

    const cancel = () => { clearInterval(countdownTimer); modal.classList.remove('show'); cleanup(); };
    const cleanup = () => {
      confirmBtn.removeEventListener('click', confirm);
      cancelBtn.removeEventListener('click', cancel);
    };

    confirmBtn.addEventListener('click', confirm);
    cancelBtn.addEventListener('click', cancel);
  },

  closeDispatchModal() {
    const cancelBtn = document.getElementById('dispatch-cancel-btn');
    if (cancelBtn) {
      cancelBtn.click();
    } else {
      const modal = document.getElementById('dispatch-modal');
      if (modal) modal.classList.remove('show');
    }
  },

  /** Direct status update API without blocking browser confirm */
  async updateStatus(nodeId, status) {
    try {
      const res = await fetch(`/api/victims/${nodeId}/status`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ status })
      });
      if (!res.ok) throw new Error('HTTP ' + res.status);
      c2App.showToast(`Đã chuyển trạng thái ${nodeId} → ${status}!`, 'success');
      this.refreshIncidents();
    } catch (e) {
      c2App.showToast('Lỗi cập nhật trạng thái: ' + e.message, 'danger');
    }
  },

  async resolveIncident(nodeId) {
    await this.updateStatus(nodeId, 'RESOLVED');
  }
};

window.c2Home = c2Home;

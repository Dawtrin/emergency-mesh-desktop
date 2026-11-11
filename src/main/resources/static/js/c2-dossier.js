/**
 * Incident Dossier Drawer & Timeline Controller (U2)
 * Hiển thị toàn bộ hồ sơ vụ việc, lịch sử gói tin, dòng thời gian sự kiện và nhật ký tác chiến.
 */
const c2Dossier = {
  activeNodeId: null,
  activeVictim: null,

  init() {
    // Lắng nghe phím ESC để đóng drawer nếu đang mở
    document.addEventListener('keydown', (e) => {
      if (e.key === 'Escape' && this.isOpen()) {
        this.closeDossier();
      }
    });
  },

  isOpen() {
    const el = document.getElementById('incident-dossier-drawer');
    return el && el.classList.contains('active');
  },

  /**
   * Mở ngăn kéo hồ sơ vụ việc cho 1 nạn nhân theo nodeId.
   */
  async openDossier(nodeId) {
    if (!nodeId) return;
    this.activeNodeId = nodeId;

    // Tìm thông tin nạn nhân từ c2Home hoặc fetch
    if (window.c2Home && c2Home.incidents) {
      this.activeVictim = c2Home.incidents.find(i => i.nodeId === nodeId) || null;
    }

    const drawer = document.getElementById('incident-dossier-drawer');
    const overlay = document.getElementById('dossier-overlay');
    if (!drawer) return;

    drawer.classList.add('active');
    if (overlay) overlay.classList.add('active');

    // Render thông tin header & loading timeline
    this.renderHeader();
    await this.loadTimeline();
  },

  closeDossier() {
    const drawer = document.getElementById('incident-dossier-drawer');
    const overlay = document.getElementById('dossier-overlay');
    if (drawer) drawer.classList.remove('active');
    if (overlay) overlay.classList.remove('active');
    this.activeNodeId = null;
    this.activeVictim = null;
  },

  renderHeader() {
    const v = this.activeVictim || { nodeId: this.activeNodeId, fullName: 'Nạn nhân Mesh', status: 'PENDING' };
    const titleEl = document.getElementById('dossier-title');
    const badgeEl = document.getElementById('dossier-status-badge');
    const gpsEl = document.getElementById('dossier-gps-info');
    const medicalEl = document.getElementById('dossier-medical-info');

    if (titleEl) {
      titleEl.innerHTML = `<span style="color:var(--aero-cyan)">${v.nodeId}</span> • ${v.fullName || 'Chưa rõ danh tính'}`;
    }

    if (badgeEl) {
      const statusClass = v.status === 'CRITICAL' || v.status === 'PENDING' ? 'pending' : (v.status === 'DISPATCHED' ? 'dispatched' : 'resolved');
      const statusLabel = v.status === 'PENDING' ? 'CHỜ ĐIỀU PHỐI' : (v.status === 'DISPATCHED' ? 'ĐANG TIẾP CẬN' : 'ĐÃ AN TOÀN');
      badgeEl.className = `badge ${statusClass}`;
      badgeEl.textContent = statusLabel;
    }

    if (gpsEl) {
      let gpsHtml = '';
      if (v.latitude && v.longitude) {
        gpsHtml = `<span>Tọa độ: <strong>[${Number(v.latitude).toFixed(4)}°N, ${Number(v.longitude).toFixed(4)}°E]</strong></span>`;
      }
      if (v.hasFix === false) {
        gpsHtml += ` <span class="badge warning" style="margin-left:6px;font-size:9px">⚠ CHƯA FIX GPS THỰC (VỊ TRÍ CŨ/ƯỚC LƯỢNG)</span>`;
      } else if (v.accuracyM) {
        gpsHtml += ` <span class="badge success" style="margin-left:6px;font-size:9px">Độ chính xác: ±${v.accuracyM}m</span>`;
      }
      if (v.fixAgeMs) {
        const sec = Math.round(v.fixAgeMs / 1000);
        gpsHtml += ` <span style="color:var(--text-muted);font-size:10px">(${sec}s trước)</span>`;
      }
      gpsEl.innerHTML = gpsHtml || 'Chưa có dữ liệu định vị';
    }

    if (medicalEl) {
      medicalEl.innerHTML = `
        <div style="font-size:11px;color:var(--text-secondary);display:flex;gap:12px;flex-wrap:wrap">
          <span>SĐT: <strong style="color:#fff">${v.phone || 'N/A'}</strong></span>
          <span>Nhóm máu: <strong style="color:var(--defcon-red)">${v.bloodType || 'O+'}</strong></span>
          <span>Loại nguy cấp: <strong style="color:var(--orbital-amber)">${v.alertType || 'CẤP CỨU MESH'}</strong></span>
          <span>Số người: <strong style="color:#fff">${v.victimCount || 1}</strong></span>
        </div>
        <div style="font-size:11px;color:var(--text-muted);margin-top:4px">
          Ghi chú: ${v.medicalNotes || 'Không có ghi chú y tế đặc biệt.'}
        </div>
      `;
    }
  },

  async loadTimeline() {
    const listEl = document.getElementById('dossier-timeline-list');
    if (!listEl) return;
    listEl.innerHTML = '<div style="color:var(--text-muted);font-size:11px;padding:12px;text-align:center">Đang tải dòng thời gian...</div>';

    try {
      const res = await fetch(`/api/incidents/${this.activeNodeId}/timeline`);
      if (!res.ok) throw new Error('HTTP ' + res.status);
      const events = await res.json();

      if (!events || events.length === 0) {
        listEl.innerHTML = '<div style="color:var(--text-muted);font-size:11px;padding:12px;text-align:center">Chưa có sự kiện nào được ghi nhận.</div>';
        return;
      }

      listEl.innerHTML = events.map(ev => {
        const timeStr = new Date(ev.timestamp).toLocaleTimeString('vi-VN') + ' ' + new Date(ev.timestamp).toLocaleDateString('vi-VN');
        const eventTypeMap = {
          'SOS_RECEIVED': { label: 'TÍN HIỆU SOS ĐÃ NHẬN', color: 'var(--defcon-red)', icon: '🚨' },
          'DISPATCH_SENT': { label: 'PHÁT LỆNH ĐIỀU PHỐI', color: 'var(--orbital-amber)', icon: '⚡' },
          'OPERATOR_ACK': { label: 'CÁN BỘ TIẾP NHẬN', color: 'var(--aero-cyan)', icon: '👤' },
          'MOBILE_ACK': { label: 'ĐIỆN THOẠI XÁC NHẬN (ACK)', color: 'var(--life-green)', icon: '📱' },
          'RESOLVED': { label: 'ĐÃ GIẢI CỨU THÀNH CÔNG', color: 'var(--life-green)', icon: '✓' },
          'NOTE': { label: 'GHI CHÚ CHỈ HUY', color: 'var(--text-primary)', icon: '📝' },
          'FALSE_ALARM': { label: 'BÁO ĐỘNG GIẢ', color: 'var(--text-muted)', icon: '✕' }
        };
        const meta = eventTypeMap[ev.eventType] || { label: ev.eventType, color: 'var(--aero-cyan)', icon: '📌' };

        return `
          <div class="timeline-item" style="display:flex;gap:10px;margin-bottom:12px;border-left:2px solid ${meta.color};padding-left:10px">
            <div style="font-size:14px">${meta.icon}</div>
            <div style="flex:1">
              <div style="display:flex;justify-content:space-between;align-items:center">
                <strong style="color:${meta.color};font-size:11px;letter-spacing:0.5px">${meta.label}</strong>
                <span style="font-size:10px;color:var(--text-muted)">${timeStr}</span>
              </div>
              <div style="color:#fff;font-size:11px;margin-top:2px">${ev.description || ''}</div>
              <div style="font-size:9px;color:var(--text-muted);margin-top:2px">Cán bộ: ${ev.operatorName || 'Hệ thống'}</div>
            </div>
          </div>
        `;
      }).join('');
    } catch (e) {
      listEl.innerHTML = `<div style="color:var(--defcon-red);font-size:11px;padding:12px">Lỗi tải dòng thời gian: ${e.message}</div>`;
    }
  },

  /**
   * Cán bộ trực ban thêm ghi chú tác chiến vào hồ sơ.
   */
  async addEvent() {
    if (!this.activeNodeId) return;
    const input = document.getElementById('dossier-note-input');
    const typeSelect = document.getElementById('dossier-event-type');
    if (!input || !input.value.trim()) {
      c2App.showToast('Vui lòng nhập nội dung sự kiện hoặc chỉ thị tác chiến', 'warning');
      return;
    }

    const payload = {
      eventType: typeSelect ? typeSelect.value : 'NOTE',
      description: input.value.trim(),
      operatorName: 'Cán bộ trực ban C2'
    };

    try {
      const res = await fetch(`/api/incidents/${this.activeNodeId}/events`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });
      if (!res.ok) throw new Error('HTTP ' + res.status);
      c2App.showToast('Đã lưu sự kiện vào hồ sơ vụ việc', 'success');
      input.value = '';
      await this.loadTimeline();
    } catch (e) {
      c2App.showToast('Lỗi ghi sự kiện: ' + e.message, 'danger');
    }
  },

  /**
   * Cập nhật trạng thái nhanh ngay trong hồ sơ.
   */
  async updateStatusFromDossier(newStatus) {
    if (!this.activeNodeId) return;
    if (window.c2Home) {
      await c2Home.updateStatus(this.activeNodeId, newStatus);
      if (this.activeVictim) this.activeVictim.status = newStatus;
      this.renderHeader();
      await this.loadTimeline();
    }
  }
};

window.c2Dossier = c2Dossier;

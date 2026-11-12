/**
 * Victim & Citizen Profile Directory Controller (Hồ sơ người dùng App & Danh bạ Nạn nhân)
 * Giúp trạm chỉ huy tra cứu, xác định danh tính, nhóm máu và tiền sử y tế của người dân dùng app.
 */
const c2Directory = {
  currentList: [],
  selectedNodeId: null,

  init() {
    // Tự động tải danh bạ khi mở tab
  },

  async loadDirectory() {
    const qInput = document.getElementById('dir-search-input');
    const bloodSelect = document.getElementById('dir-filter-blood');
    const statusSelect = document.getElementById('dir-filter-status');

    const q = qInput ? qInput.value.trim() : '';
    const blood = bloodSelect ? bloodSelect.value : 'ALL';
    const status = statusSelect ? statusSelect.value : 'ALL';

    const tbody = document.getElementById('dir-table-tbody');
    if (tbody) {
      tbody.innerHTML = '<tr><td colspan="7" style="text-align:center;padding:16px;color:var(--text-muted)">Đang tra cứu danh bạ nạn nhân...</td></tr>';
    }

    try {
      const url = `/api/victims/search?q=${encodeURIComponent(q)}&bloodType=${encodeURIComponent(blood)}&status=${encodeURIComponent(status)}`;
      const res = await fetch(url);
      if (!res.ok) throw new Error('HTTP ' + res.status);
      this.currentList = await res.json();
      this.renderTable();
      this.updateStats();

      // Nếu chưa chọn ai và danh sách có người, chọn người đầu tiên
      if ((!this.selectedNodeId || !this.currentList.some(v => v.nodeId === this.selectedNodeId)) && this.currentList.length > 0) {
        this.selectVictim(this.currentList[0].nodeId);
      } else if (this.currentList.length === 0) {
        this.renderEmptyMedicalCard();
      }
    } catch (e) {
      if (tbody) {
        tbody.innerHTML = `<tr><td colspan="7" style="text-align:center;padding:16px;color:var(--defcon-red)">Lỗi tra cứu: ${e.message}</td></tr>`;
      }
    }
  },

  renderTable() {
    const tbody = document.getElementById('dir-table-tbody');
    if (!tbody) return;

    if (!this.currentList || this.currentList.length === 0) {
      tbody.innerHTML = '<tr><td colspan="7" style="text-align:center;padding:20px;color:var(--text-muted)">Không tìm thấy hồ sơ nạn nhân phù hợp</td></tr>';
      return;
    }

    tbody.innerHTML = this.currentList.map(v => {
      const isSelected = v.nodeId === this.selectedNodeId;
      const statusClass = v.status === 'PENDING' ? 'pending' : (v.status === 'DISPATCHED' ? 'dispatched' : 'resolved');
      const statusLabel = v.status === 'PENDING' ? 'CHỜ CỨU' : (v.status === 'DISPATCHED' ? 'ĐANG TIẾP CẬN' : 'ĐÃ AN TOÀN');
      const lastSeen = v.lastUpdated ? new Date(v.lastUpdated).toLocaleTimeString('vi-VN') : '—';

      return `
        <tr class="dir-row ${isSelected ? 'selected' : ''}" onclick="c2Directory.selectVictim('${v.nodeId}')" style="cursor:pointer;${isSelected ? 'background:rgba(0,240,255,0.08);border-left:3px solid var(--aero-cyan)' : ''}">
          <td><strong style="color:var(--aero-cyan)">${v.nodeId}</strong></td>
          <td><strong>${v.fullName || 'Nạn nhân'}</strong></td>
          <td>${v.phone || '—'}</td>
          <td><span class="badge" style="color:var(--defcon-red);border-color:var(--defcon-red);font-weight:700">${v.bloodType || 'O+'}</span></td>
          <td><span style="font-size:11px;color:var(--text-secondary)">${v.alertType || 'CẤP CỨU MESH'}</span></td>
          <td><span class="badge ${statusClass}" style="font-size:9px">${statusLabel}</span></td>
          <td><span style="font-size:10px;color:var(--text-muted)">${lastSeen}</span></td>
        </tr>
      `;
    }).join('');
  },

  selectVictim(nodeId) {
    this.selectedNodeId = nodeId;
    const v = this.currentList.find(item => item.nodeId === nodeId);
    if (!v) return;

    this.renderTable(); // cập nhật highlight dòng được chọn
    this.renderMedicalCard(v);
  },

  renderMedicalCard(v) {
    const cardEl = document.getElementById('dir-medical-card-content');
    if (!cardEl) return;

    const isRareBlood = v.bloodType && (v.bloodType.includes('-') || v.bloodType === 'AB+');
    const statusClass = v.status === 'PENDING' ? 'pending' : (v.status === 'DISPATCHED' ? 'dispatched' : 'resolved');
    const statusLabel = v.status === 'PENDING' ? 'CHỜ CỨU' : (v.status === 'DISPATCHED' ? 'ĐANG TIẾP CẬN' : 'ĐÃ AN TOÀN');

    let gpsBadge = '';
    if (v.hasFix === false) {
      gpsBadge = `<div style="color:var(--orbital-amber);font-size:11px;margin-top:2px">⚠ Chưa fix GPS thực (Tọa độ cũ/ước lượng)</div>`;
    } else if (v.accuracyM) {
      gpsBadge = `<div style="color:var(--life-green);font-size:11px;margin-top:2px">✓ GPS chính xác: ±${v.accuracyM}m ${v.fixAgeMs ? `(${Math.round(v.fixAgeMs/1000)}s trước)` : ''}</div>`;
    }

    cardEl.innerHTML = `
      <div style="background:rgba(255,255,255,0.03);border:1px solid var(--border-subtle);border-radius:4px;padding:16px">
        
        <!-- Header thẻ -->
        <div style="display:flex;justify-content:space-between;align-items:flex-start;margin-bottom:12px;border-bottom:1px solid rgba(255,255,255,0.08);padding-bottom:10px">
          <div>
            <div style="font-size:10px;color:var(--text-muted);letter-spacing:1px;font-weight:700">THẺ HỒ SƠ Y TẾ KHẨN CẤP (EMERGENCY MEDICAL ID)</div>
            <div style="font-size:16px;font-weight:700;color:#fff;margin-top:2px">${v.fullName || 'Nạn nhân'}</div>
            <div style="font-size:11px;color:var(--aero-cyan);margin-top:1px">Mã định danh App: <strong>${v.nodeId}</strong></div>
          </div>
          <span class="badge ${statusClass}">${statusLabel}</span>
        </div>

        <!-- Nhóm máu & Cảnh báo -->
        <div style="display:grid;grid-template-columns:100px 1fr;gap:12px;margin-bottom:14px;background:rgba(255,0,51,0.08);border:1px solid rgba(255,0,51,0.3);border-radius:4px;padding:10px;align-items:center">
          <div style="text-align:center">
            <div style="font-size:9px;color:var(--text-muted);font-weight:700">NHÓM MÁU</div>
            <div style="font-size:24px;font-weight:800;color:var(--defcon-red);line-height:1.2">${v.bloodType || 'O+'}</div>
            ${isRareBlood ? '<span class="badge danger" style="font-size:8px;padding:1px 4px">MÁU HIẾM</span>' : ''}
          </div>
          <div>
            <div style="font-size:11px;font-weight:700;color:#fff">BỆNH NỀN / DỊ ỨNG THUỐC:</div>
            <div style="font-size:11px;color:var(--text-secondary);margin-top:3px">${v.medicalNotes || 'Không ghi nhận dị ứng hoặc bệnh mãn tính.'}</div>
          </div>
        </div>

        <!-- Thông tin liên lạc & Vị trí -->
        <div style="display:flex;flex-direction:column;gap:8px;font-size:11px;margin-bottom:16px">
          <div>📱 Số điện thoại liên hệ: <strong style="color:#fff">${v.phone || 'Chưa cung cấp'}</strong></div>
          <div>👥 Số người đi cùng / quy mô nạn nhân: <strong style="color:#fff">${v.victimCount || 1} người</strong></div>
          <div>🚨 Tình trạng khai báo: <strong style="color:var(--orbital-amber)">${v.alertType || 'CẤP CỨU'}</strong> (${v.severity || 'HIGH'})</div>
          <div>📍 Tọa độ GPS: <strong>[${Number(v.latitude).toFixed(4)}°N, ${Number(v.longitude).toFixed(4)}°E]</strong></div>
          ${gpsBadge}
        </div>

        <!-- Các nút hành động tác chiến -->
        <div style="display:flex;flex-direction:column;gap:8px;border-top:1px solid rgba(255,255,255,0.08);padding-top:12px">
          <div style="display:grid;grid-template-columns:1fr 1fr;gap:8px">
            <button class="btn-tactical danger" onclick="c2Home.openDispatchModal('${v.nodeId}')">⚡ ĐIỀU PHỐI CỨU HỘ</button>
            <button class="btn-tactical" onclick="c2Dossier.openDossier('${v.nodeId}')">📁 HỒ SƠ & TIMELINE</button>
          </div>
          <button class="btn-tactical cyan" style="width:100%" onclick="c2Directory.focusOnMap('${v.nodeId}')">🗺️ ĐỊNH VỊ TRÊN BẢN ĐỒ COP</button>
        </div>

      </div>
    `;
  },

  renderEmptyMedicalCard() {
    const cardEl = document.getElementById('dir-medical-card-content');
    if (cardEl) {
      cardEl.innerHTML = `
        <div style="text-align:center;padding:40px 20px;color:var(--text-muted);border:1px dashed var(--border-subtle);border-radius:4px">
          <div style="font-size:24px;margin-bottom:8px">🩺</div>
          <div style="font-size:12px;font-weight:700">CHỌN MỘT NẠN NHÂN ĐỂ XEM HỒ SƠ Y TẾ</div>
          <div style="font-size:10px;margin-top:4px">Nhấp vào một dòng trong danh sách bên trái để mở thẻ thông tin chi tiết</div>
        </div>
      `;
    }
  },

  updateStats() {
    const totalEl = document.getElementById('dir-stat-total');
    const oEl = document.getElementById('dir-stat-o');
    const aEl = document.getElementById('dir-stat-a');
    const bEl = document.getElementById('dir-stat-b');
    const abEl = document.getElementById('dir-stat-ab');

    if (totalEl) totalEl.textContent = this.currentList.length;
    if (oEl) oEl.textContent = this.currentList.filter(v => (v.bloodType || '').includes('O')).length;
    if (aEl) aEl.textContent = this.currentList.filter(v => (v.bloodType || '').includes('A') && !(v.bloodType || '').includes('AB')).length;
    if (bEl) bEl.textContent = this.currentList.filter(v => (v.bloodType || '').includes('B') && !(v.bloodType || '').includes('AB')).length;
    if (abEl) abEl.textContent = this.currentList.filter(v => (v.bloodType || '').includes('AB')).length;
  },

  focusOnMap(nodeId) {
    if (window.c2App) {
      c2App.switchTab('tab-map');
      if (window.c2Map) {
        c2Map.focusAndRoute(nodeId);
      }
    }
  }
};

window.c2Directory = c2Directory;

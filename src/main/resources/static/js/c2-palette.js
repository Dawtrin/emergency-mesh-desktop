/**
 * NASA C2 RESCUE — Tactical Command Palette (Ctrl+K)
 * Ultra-fast fuzzy search and instant execution for Tabs, Rescue Teams, Victims, and C2 Actions
 * Designed for Bloomberg Terminal & Raycast high-speed keyboard-driven operations
 */
const c2Palette = {
  isOpen: false,
  modalEl: null,
  inputEl: null,
  resultsEl: null,
  selectedIndex: 0,
  currentItems: [],

  init() {
    this.modalEl = document.getElementById('command-palette-modal');
    this.inputEl = document.getElementById('palette-search-input');
    this.resultsEl = document.getElementById('palette-results-list');

    if (!this.modalEl || !this.inputEl || !this.resultsEl) {
      return;
    }

    // Input change listener
    this.inputEl.addEventListener('input', () => {
      this.search(this.inputEl.value);
    });

    // Key navigation inside input
    this.inputEl.addEventListener('keydown', (e) => {
      if (e.key === 'ArrowDown') {
        e.preventDefault();
        this.selectNext();
      } else if (e.key === 'ArrowUp') {
        e.preventDefault();
        this.selectPrev();
      } else if (e.key === 'Enter') {
        e.preventDefault();
        this.executeSelected();
      } else if (e.key === 'Escape') {
        e.preventDefault();
        this.close();
      }
    });

    // Close when clicking modal backdrop
    this.modalEl.addEventListener('click', (e) => {
      if (e.target === this.modalEl) {
        this.close();
      }
    });

    // Global shortcut Ctrl+K / Cmd+K
    window.addEventListener('keydown', (e) => {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault();
        this.toggle();
      }
      if (e.key === 'Escape' && this.isOpen) {
        e.preventDefault();
        this.close();
      }
    });
  },

  toggle() {
    if (this.isOpen) {
      this.close();
    } else {
      this.open();
    }
  },

  open() {
    if (!this.modalEl) this.init();
    if (!this.modalEl) return;
    this.isOpen = true;
    this.modalEl.classList.add('active');
    this.inputEl.value = '';
    this.search('');
    setTimeout(() => this.inputEl.focus(), 50);
  },

  close() {
    if (!this.modalEl) return;
    this.isOpen = false;
    this.modalEl.classList.remove('active');
    if (this.inputEl) this.inputEl.blur();
  },

  // Builds search index dynamically from live state
  getIndex() {
    const items = [];

    // 1. Điều hướng Tabs
    const tabs = [
      { id: 'tab-home', name: 'Trang chủ & Tổng quan tác chiến (Overview)', shortcut: 'Ctrl+1', icon: '⚡', category: 'MÀN HÌNH TÁC CHIẾN' },
      { id: 'tab-map', name: 'Bản đồ số COP & Điều phối thực địa (Map)', shortcut: 'Ctrl+2', icon: '🗺️', category: 'MÀN HÌNH TÁC CHIẾN' },
      { id: 'tab-interagency', name: 'Điều phối liên ngành 115 / 114 / 113', shortcut: 'Ctrl+3', icon: '🚨', category: 'MÀN HÌNH TÁC CHIẾN' },
      { id: 'tab-noc', name: 'Giám sát hạ tầng mạng NOC & Thông lượng', shortcut: 'Ctrl+4', icon: '📡', category: 'MÀN HÌNH TÁC CHIẾN' },
      { id: 'tab-triage', name: 'Phân loại nạn nhân y tế START Triage', shortcut: 'Ctrl+5', icon: '🩺', category: 'MÀN HÌNH TÁC CHIẾN' },
      { id: 'tab-topology', name: 'Cấu trúc mạng Mesh Topology', shortcut: 'Ctrl+6', icon: '🕸️', category: 'MÀN HÌNH TÁC CHIẾN' },
      { id: 'tab-audit', name: 'Nhật ký tác chiến Audit Trail & Hộp đen', shortcut: 'Ctrl+7', icon: '📜', category: 'MÀN HÌNH TÁC CHIẾN' },
      { id: 'tab-config', name: 'Mô phỏng thảm họa & Cấu hình trạm', shortcut: 'Ctrl+8', icon: '⚙️', category: 'MÀN HÌNH TÁC CHIẾN' }
    ];

    tabs.forEach(t => {
      items.push({
        title: t.name,
        badge: t.shortcut,
        icon: t.icon,
        category: 'ĐIỀU HƯỚNG MÀN HÌNH',
        keywords: `${t.name} ${t.id} ${t.shortcut}`,
        action: () => window.c2App && c2App.switchTab(t.id)
      });
    });

    // 2. Mệnh lệnh tác chiến nhanh
    items.push({
      title: 'Bật / Tắt còi báo động âm thanh (Toggle Mute)',
      badge: 'Ctrl+M',
      icon: '🔊',
      category: 'LỆNH TÁC CHIẾN TỐC HÀNH',
      keywords: 'mute toggle im lang am thanh coi am bao',
      action: () => window.c2Audio && c2Audio.toggleMute()
    });

    items.push({
      title: 'Mở cửa sổ điều phối khẩn cấp (Quick Dispatch)',
      badge: 'Ctrl+D',
      icon: '⚡',
      category: 'LỆNH TÁC CHIẾN TỐC HÀNH',
      keywords: 'dispatch dieu phoi phat lenh xuat kich',
      action: () => {
        if (window.c2App && window.c2Home && c2Home.incidents) {
          const pending = c2Home.incidents.find(i => i.status === 'PENDING');
          if (pending) {
            c2App.switchTab('tab-home');
            c2Home.openDispatchModal(pending.nodeId);
          } else {
            c2App.showToast('Không có nạn nhân PENDING', 'warning');
          }
        }
      }
    });

    items.push({
      title: 'Xuất Báo cáo Tình huống Sự cố (FEMA ICS Report 201/204/205A)',
      badge: 'PDF / PRINT',
      icon: '📋',
      category: 'LỆNH TÁC CHIẾN TỐC HÀNH',
      keywords: 'iap report bao cao fema ics xuat in',
      action: () => {
        if (window.c2InterAgency && typeof c2InterAgency.exportIAPReport === 'function') {
          c2InterAgency.exportIAPReport();
        } else {
          window.print();
        }
      }
    });

    items.push({
      title: 'Xuất Nhật ký tác chiến (Audit Log CSV)',
      badge: 'CSV',
      icon: '💾',
      category: 'LỆNH TÁC CHIẾN TỐC HÀNH',
      keywords: 'audit csv export nhat ky xuat',
      action: () => window.c2App && c2App.exportCsv()
    });

    items.push({
      title: 'Kích hoạt Kịch bản mô phỏng Siêu bão lũ lụt Đà Nẵng',
      badge: 'SIMULATOR',
      icon: '🌪️',
      category: 'LỆNH TÁC CHIẾN TỐC HÀNH',
      keywords: 'simulator mo phong kịch bản bao lut da nang',
      action: () => {
        if (window.c2App) c2App.switchTab('tab-config');
        if (window.c2Simulator) c2Simulator.injectFullDemo();
      }
    });

    // 3. Đội cứu hộ dã chiến (nếu có markers trong c2Map)
    if (window.c2Map && c2Map.rescueTeamMarkers) {
      Object.keys(c2Map.rescueTeamMarkers).forEach(teamId => {
        const marker = c2Map.rescueTeamMarkers[teamId];
        items.push({
          title: `Đội tác chiến: ${teamId}`,
          badge: 'AVL GPS',
          icon: '🚑',
          category: 'ĐỘI CỨU HỘ DÃ CHIẾN (AVL)',
          keywords: `team ${teamId} doi cuu ho xe cap cuu pccc csgt`,
          action: () => {
            if (window.c2App) c2App.switchTab('tab-map');
            if (marker && c2Map.map) {
              c2Map.map.setView(marker.getLatLng(), 15, { animate: true });
              marker.openPopup();
            }
          }
        });
      });
    }

    // 4. Nạn nhân trên chiến trường (lấy từ c2Home.incidents)
    if (window.c2Home && c2Home.incidents && c2Home.incidents.length > 0) {
      c2Home.incidents.forEach(inc => {
        const isPending = inc.status === 'PENDING';
        const isDispatched = inc.status === 'DISPATCHED';
        const statusText = isPending ? 'CẦN TIẾP CẬN GẤP' : (isDispatched ? 'ĐANG ĐIỀU PHỐI' : 'ĐÃ GIẢI QUYẾT');
        const icon = isPending ? '🔴' : (isDispatched ? '🟡' : '🟢');

        items.push({
          title: `Nạn nhân [${inc.nodeId}]: ${inc.medicalNotes || inc.status} (${inc.severity || 'HIGH'})`,
          badge: statusText,
          icon: icon,
          category: 'NẠN NHÂN TRÊN CHIẾN TRƯỜNG',
          keywords: `${inc.nodeId} ${inc.medicalNotes || ''} ${inc.status} ${inc.severity || ''} nan nhan victim`,
          action: () => {
            if (window.c2App) c2App.switchTab('tab-map');
            if (window.c2Map && c2Map.markers && c2Map.markers[inc.nodeId]) {
              const m = c2Map.markers[inc.nodeId];
              c2Map.map.setView(m.getLatLng(), 15, { animate: true });
              c2Map.onVictimClick(inc);
            }
          }
        });
      });
    }

    return items;
  },

  normalize(str) {
    return (str || '')
      .toLowerCase()
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '');
  },

  search(query) {
    const rawIndex = this.getIndex();
    const q = this.normalize(query.trim());

    if (!q) {
      this.currentItems = rawIndex.slice(0, 12);
    } else {
      const words = q.split(/\s+/);
      this.currentItems = rawIndex.filter(item => {
        const text = this.normalize(`${item.title} ${item.keywords} ${item.category} ${item.badge}`);
        return words.every(word => text.includes(word));
      }).slice(0, 15);
    }

    this.selectedIndex = 0;
    this.render();
  },

  render() {
    if (!this.resultsEl) return;

    if (this.currentItems.length === 0) {
      this.resultsEl.innerHTML = `
        <div class="palette-empty">
          <div style="font-size:24px;margin-bottom:8px">📡</div>
          <div style="color:var(--text-secondary);font-size:12px;font-weight:700">KHÔNG TÌM THẤY LỆNH HOẶC MỤC TIÊU PHÙ HỢP</div>
          <div style="color:var(--text-muted);font-size:10px;margin-top:4px">Thử gõ "map", "115", "nan nhan", "triage" hoặc "bao cao"...</div>
        </div>
      `;
      return;
    }

    let html = '';
    let currentCat = '';

    this.currentItems.forEach((item, idx) => {
      if (item.category !== currentCat) {
        currentCat = item.category;
        html += `<div class="palette-group-title">${currentCat}</div>`;
      }

      const isSelected = idx === this.selectedIndex;
      html += `
        <div class="palette-item ${isSelected ? 'selected' : ''}" data-index="${idx}">
          <div class="palette-item-left">
            <span class="palette-item-icon">${item.icon}</span>
            <span class="palette-item-title">${item.title}</span>
          </div>
          <div class="palette-item-right">
            <span class="palette-item-badge">${item.badge}</span>
          </div>
        </div>
      `;
    });

    this.resultsEl.innerHTML = html;

    this.resultsEl.querySelectorAll('.palette-item').forEach(el => {
      el.addEventListener('click', () => {
        const idx = parseInt(el.dataset.index, 10);
        this.selectedIndex = idx;
        this.executeSelected();
      });
      el.addEventListener('mouseenter', () => {
        const idx = parseInt(el.dataset.index, 10);
        this.selectedIndex = idx;
        this.updateSelectionVisuals();
      });
    });

    this.scrollToSelected();
  },

  updateSelectionVisuals() {
    const items = this.resultsEl.querySelectorAll('.palette-item');
    items.forEach((el, idx) => {
      if (idx === this.selectedIndex) {
        el.classList.add('selected');
      } else {
        el.classList.remove('selected');
      }
    });
  },

  scrollToSelected() {
    const selectedEl = this.resultsEl.querySelector('.palette-item.selected');
    if (selectedEl) {
      selectedEl.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
    }
  },

  selectNext() {
    if (this.currentItems.length === 0) return;
    this.selectedIndex = (this.selectedIndex + 1) % this.currentItems.length;
    this.updateSelectionVisuals();
    this.scrollToSelected();
  },

  selectPrev() {
    if (this.currentItems.length === 0) return;
    this.selectedIndex = (this.selectedIndex - 1 + this.currentItems.length) % this.currentItems.length;
    this.updateSelectionVisuals();
    this.scrollToSelected();
  },

  executeSelected() {
    if (this.selectedIndex >= 0 && this.selectedIndex < this.currentItems.length) {
      const item = this.currentItems[this.selectedIndex];
      this.close();
      if (typeof item.action === 'function') {
        item.action();
      }
    }
  }
};

window.c2Palette = c2Palette;

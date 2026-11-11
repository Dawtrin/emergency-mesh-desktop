/**
 * C2 Scenario Simulator — 1-Click Packet Injector (Trang 7 & Header)
 * Uses toast notifications instead of alert(), binds via event listeners
 */
const c2Simulator = {
  init() {
    document.getElementById('btn-sim-flood')?.addEventListener('click', () => this.injectFlood(5));
    document.getElementById('btn-sim-medical')?.addEventListener('click', () => this.injectMedical());
    document.getElementById('btn-sim-multihop')?.addEventListener('click', () => this.injectMultiHop());
    document.getElementById('btn-sim-full')?.addEventListener('click', () => this.injectFullDemo());
    document.getElementById('btn-clear-data')?.addEventListener('click', () => this.clearAllData());
    document.getElementById('btn-inject-realistic')?.addEventListener('click', () => this.injectRealisticSituations());

    // Map page buttons
    document.getElementById('btn-inject-flood-map')?.addEventListener('click', () => this.injectFlood(3));
    document.getElementById('btn-inject-medical-map')?.addEventListener('click', () => this.injectMedical());
  },

  async injectRealisticSituations() {
    try {
      const res = await fetch('/api/simulator/inject-realistic', { method: 'POST' });
      if (!res.ok) throw new Error('HTTP ' + res.status);
      const data = await res.json();
      c2App.showToast('✓ ĐÃ KÍCH HOẠT 7 TÌNH HUỐNG THỰC TẾ & 7 PHIẾU LIÊN NGÀNH ĐÀ NẴNG!', 'success');
      c2App.logSniffer(`[SIMULATOR] KÍCH HOẠT 7 TÌNH HUỐNG THỰC TẾ: ${data.nodeIds.join(', ')}`);

      if (window.c2Audio) c2Audio.triggerSosAlarm(4);
      c2App.activateStrobe(5000);

      // Refresh all modules
      if (window.c2Home) c2Home.refreshIncidents();
      if (window.c2InterAgency) {
        c2InterAgency.loadTickets();
        c2InterAgency.loadRadioLogs();
        c2InterAgency.loadFacilities();
      }
      if (window.c2Map) {
        c2Map.loadFacilities();
      }
    } catch (e) {
      c2App.showToast('Lỗi kích hoạt tình huống: ' + e.message, 'danger');
    }
  },

  async clearAllData() {
    if (!confirm('XÁC NHẬN TÁC CHIẾN:\nBạn có chắc chắn muốn XÓA SẠCH toàn bộ dữ liệu mẫu (Nạn nhân, Nhật ký gói tin, Lệnh điều phối, Phiếu liên ngành, Nhật ký bộ đàm)?\n\nHệ thống sẽ được đưa về trạng thái sạch 100% để sẵn sàng nhận dữ liệu Mesh thực tế.')) {
      return;
    }
    try {
      const res = await fetch('/api/simulator/clear-data', { method: 'POST' });
      if (!res.ok) throw new Error('HTTP ' + res.status);
      c2App.showToast('✓ ĐÃ XÓA TOÀN BỘ DỮ LIỆU MẪU & LÀM SẠCH HỆ THỐNG!', 'success');
      c2App.logSniffer('[C2-ADMIN] Đã dọn dẹp sạch toàn bộ cơ sở dữ liệu mẫu.');

      // Refresh all C2 views
      if (window.c2Home) c2Home.refreshIncidents();
      if (window.c2Map && c2Map.map) c2Map.renderVictimMarkers([]);
      if (window.c2InterAgency) {
        c2InterAgency.loadTickets();
        c2InterAgency.loadRadioLogs();
        c2InterAgency.loadFacilities();
      }
      if (window.c2Noc) c2Noc.fetchMetrics();
      if (window.c2Topology) c2Topology.loadRelayNodes();
      if (window.c2Audit) c2Audit.loadEvidenceLogs();
    } catch (e) {
      c2App.showToast('Lỗi xóa dữ liệu: ' + e.message, 'danger');
    }
  },

  async injectFlood(count = 5) {
    try {
      let res = await fetch(`/api/simulator/flood?count=${count}`, { method: 'POST' });
      if (!res.ok) {
        res = await fetch('/api/simulator/inject', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ scenario: 'FLOOD_BATCH', count })
        });
      }
      const data = await res.json();
      c2App.logSniffer(`[SIMULATOR] Bơm ${data.count || count} ca lũ lụt (${(data.nodeIds||[]).join(', ')})`);
      c2App.showToast(`Đã bơm ${data.count || count} ca Lũ lụt vào cổng TCP 8888`, 'info');
      if (window.c2Audio) c2Audio.triggerSosAlarm(4);
      if (window.c2Home) c2Home.refreshIncidents();
    } catch (e) {
      c2App.showToast('Lỗi bơm lũ lụt: ' + e.message, 'danger');
    }
  },

  async injectMedical() {
    try {
      let res = await fetch(`/api/simulator/medical`, { method: 'POST' });
      if (!res.ok) {
        res = await fetch('/api/simulator/inject', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ scenario: 'RARE_BLOOD' })
        });
      }
      const data = await res.json();
      c2App.logSniffer(`[SIMULATOR] Ca cấp cứu y tế nguy kịch: ${data.nodeId} (AB-)`);
      c2App.showToast(`Ca Cấp Cứu Máu Hiếm AB- (${data.nodeId})`, 'danger');
      if (window.c2Audio) c2Audio.triggerSosAlarm(6);
      if (window.c2Home) c2Home.refreshIncidents();
    } catch (e) {
      c2App.showToast('Lỗi bơm ca cấp cứu: ' + e.message, 'danger');
    }
  },

  async injectMultiHop() {
    try {
      let res = await fetch('/api/simulator/multihop', { method: 'POST' });
      if (!res.ok) {
        res = await fetch('/api/simulator/inject', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ scenario: 'MULTI_HOP' })
        });
      }
      const data = await res.json();
      c2App.logSniffer(`[SIMULATOR] Gói tin đa bước nhảy (3 Relays): ${data.nodeId}`);
      c2App.showToast(`Gói tin Multi-Hop 3 bước nhảy (${data.nodeId})`, 'info');
      if (window.c2Audio) c2Audio.triggerSosAlarm(4);
      if (window.c2Home) c2Home.refreshIncidents();
    } catch (e) {
      c2App.showToast('Lỗi bơm multi-hop: ' + e.message, 'danger');
    }
  },

  async injectFullDemo() {
    try {
      let res = await fetch('/api/simulator/full-demo', { method: 'POST' });
      if (!res.ok) {
        res = await fetch('/api/simulator/inject', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ scenario: 'FULL_DEMO' })
        });
      }
      const data = await res.json();
      c2App.logSniffer(`[SIMULATOR] DIỄN TẬP TOÀN DIỆN: ${data.total || 7} tình huống`);
      c2App.showToast(`KỊCH BẢN DIỄN TẬP TOÀN DIỆN (${data.total || 7} mục tiêu)`, 'warning');
      if (window.c2Audio) c2Audio.triggerSosAlarm(7);
      c2App.activateStrobe(6000);
      if (window.c2Home) c2Home.refreshIncidents();
    } catch (e) {
      c2App.showToast('Lỗi bơm full demo: ' + e.message, 'danger');
    }
  }
};

window.c2Simulator = c2Simulator;

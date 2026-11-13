/**
 * C2 Master Application Controller — SPA Router, STOMP WebSocket, Toast System & Telemetry HUD
 * NASA Mission Control × Data-Dense Dark Mode
 */
const c2App = {
  stompClient: null,
  activeTab: 'tab-home',
  wsConnected: false,

  init() {
    this.initClock();
    this.initNavigation();
    this.initKeyboardShortcuts();
    this.initWebSocket();
    this.initGlobalButtons();
    this.initServiceWorker();

    // Init submodules (order matters)
    if (typeof c2Home !== 'undefined') { window.c2Home = c2Home; c2Home.init(); }
    if (typeof c2Map !== 'undefined') { window.c2Map = c2Map; c2Map.init(); }
    if (typeof c2InterAgency !== 'undefined') { window.c2InterAgency = c2InterAgency; c2InterAgency.init(); }
    if (typeof c2Charts !== 'undefined') { window.c2Charts = c2Charts; c2Charts.init(); }
    if (typeof c2Noc !== 'undefined') { window.c2Noc = c2Noc; c2Noc.init(); }
    if (typeof c2Triage !== 'undefined') { window.c2Triage = c2Triage; c2Triage.init(); }
    if (typeof c2Topology !== 'undefined') { window.c2Topology = c2Topology; c2Topology.init(); }
    if (typeof c2Audit !== 'undefined') { window.c2Audit = c2Audit; c2Audit.init(); }
    if (typeof c2Config !== 'undefined') { window.c2Config = c2Config; c2Config.init(); }
    if (typeof c2Simulator !== 'undefined') { window.c2Simulator = c2Simulator; c2Simulator.init(); }
    if (typeof c2Palette !== 'undefined') { window.c2Palette = c2Palette; c2Palette.init(); }
    if (typeof c2Dossier !== 'undefined') { window.c2Dossier = c2Dossier; c2Dossier.init(); }
    if (typeof c2Kanban !== 'undefined') { window.c2Kanban = c2Kanban; c2Kanban.init(); }
    if (typeof c2Auth !== 'undefined') { window.c2Auth = c2Auth; c2Auth.init(); }
    if (typeof c2Devices !== 'undefined') { window.c2Devices = c2Devices; c2Devices.init(); }
    if (typeof c2Directory !== 'undefined') { window.c2Directory = c2Directory; c2Directory.init(); }

    this.logSniffer('[C2-SYSTEM] Dual-Engine khởi động hoàn tất. Web :8080 | Mesh TCP :8888/:8889');
  },

  /* ─── Clock HUD ─── */
  initClock() {
    const tick = () => {
      const now = new Date();
      const el = document.getElementById('hud-time-local');
      if (el) el.textContent = now.toLocaleTimeString('vi-VN');
      const utc = document.getElementById('hud-time-utc');
      if (utc) utc.textContent = now.toISOString().slice(11, 19) + ' UTC';
    };
    tick();
    setInterval(tick, 1000);
  },

  /* ─── Navigation (desktop tabs + mobile bottom bar) ─── */
  initNavigation() {
    document.querySelectorAll('.nav-tab, .mobile-tab').forEach(btn => {
      btn.addEventListener('click', () => this.switchTab(btn.dataset.tab));
    });
  },

  switchTab(tabId) {
    document.querySelectorAll('.nav-tab, .mobile-tab').forEach(b => b.classList.remove('active'));
    document.querySelectorAll('.tab-content').forEach(c => c.classList.remove('active'));

    document.querySelectorAll(`[data-tab="${tabId}"]`).forEach(b => b.classList.add('active'));
    const target = document.getElementById(tabId);
    if (target) {
      target.classList.add('active');
      this.activeTab = tabId;

      // Leaflet invalidateSize & redraw - Khắc phục vỡ khung nhìn & tự động khóa chuẩn Đà Nẵng
      if (tabId === 'tab-map' && window.c2Map && c2Map.map) {
        setTimeout(() => {
          c2Map.map.invalidateSize();
          if (!c2Map.activeRoutingVictim) {
            c2Map.map.setView(c2Map.baseCoords, 12);
          }
          if (window.c2Home && c2Home.incidents) c2Map.renderVictimMarkers(c2Home.incidents);
        }, 120);
      }

      // NOC charts resize & update
      if (tabId === 'tab-noc' && window.c2Noc) {
        setTimeout(() => {
          Object.values(c2Noc.charts).forEach(c => { try { c.resize(); c.update(); } catch(e){} });
          c2Noc.fetchMetrics();
        }, 120);
      }

      // Triage charts resize & update
      if (tabId === 'tab-triage' && window.c2Triage) {
        setTimeout(() => {
          Object.values(c2Triage.charts).forEach(c => { try { c.resize(); c.update(); } catch(e){} });
          if (window.c2Home && c2Home.incidents) c2Triage.updateFromIncidents(c2Home.incidents);
        }, 120);
      }

      // Topology render
      if (tabId === 'tab-topology' && window.c2Topology) {
        setTimeout(() => {
          c2Topology.loadRelayNodes();
        }, 120);
      }

      // Admin Users render
      if (tabId === 'tab-admin' && window.c2Auth) {
        setTimeout(() => {
          c2Auth.loadUsersList();
          c2Auth.renderShiftInfo();
        }, 120);
      }

      // Victim Directory render
      if (tabId === 'tab-directory' && window.c2Directory) {
        setTimeout(() => {
          c2Directory.loadDirectory();
        }, 120);
      }
    }
  },

  /* ─── Global Buttons ─── */
  toggleSimMenu() {
    const menu = document.getElementById('sim-menu-dropdown');
    if (menu) menu.classList.toggle('show');
  },

  initGlobalButtons() {
    const muteBtn = document.getElementById('btn-mute-toggle');
    if (muteBtn) muteBtn.addEventListener('click', () => window.c2Audio && c2Audio.toggleMute());

    const demoBtn = document.getElementById('btn-inject-demo');
    if (demoBtn) demoBtn.addEventListener('click', () => {
      const menu = document.getElementById('sim-menu-dropdown');
      if (menu) menu.classList.remove('show');
      window.c2Simulator && c2Simulator.injectFullDemo();
    });

    const realBtn = document.getElementById('btn-inject-realistic');
    if (realBtn) realBtn.addEventListener('click', () => {
      const menu = document.getElementById('sim-menu-dropdown');
      if (menu) menu.classList.remove('show');
    });

    const clearBtn = document.getElementById('btn-clear-data');
    if (clearBtn) clearBtn.addEventListener('click', () => {
      const menu = document.getElementById('sim-menu-dropdown');
      if (menu) menu.classList.remove('show');
    });

    // Close Sim Menu on outside click
    document.addEventListener('click', (e) => {
      const btn = document.getElementById('btn-sim-menu-toggle');
      const menu = document.getElementById('sim-menu-dropdown');
      if (menu && menu.classList.contains('show') && btn && !btn.contains(e.target) && !menu.contains(e.target)) {
        menu.classList.remove('show');
      }
    });

    // Export buttons
    const csvBtn = document.getElementById('btn-export-csv');
    if (csvBtn) csvBtn.addEventListener('click', () => this.exportCsv());
    const csvHome = document.getElementById('btn-export-csv-home');
    if (csvHome) csvHome.addEventListener('click', () => this.exportCsv());

    const iapBtn = document.getElementById('btn-export-iap');
    if (iapBtn) iapBtn.addEventListener('click', () => window.print());
  },

  /* ─── Keyboard Shortcuts (NASA MCC / Bloomberg Terminal style) ─── */
  initKeyboardShortcuts() {
    const tabMap = {
      '1': 'tab-home', '2': 'tab-map', '3': 'tab-interagency', '4': 'tab-noc',
      '5': 'tab-triage', '6': 'tab-topology', '7': 'tab-audit', '8': 'tab-config', '9': 'tab-admin'
    };
    document.addEventListener('keydown', (e) => {
      // Ctrl+K / Cmd+K → Command Palette (luôn kích hoạt mọi lúc)
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault();
        if (window.c2Palette) c2Palette.toggle();
        return;
      }

      // Escape → Đóng Command Palette, Dossier Drawer, hoặc Modal đang mở
      if (e.key === 'Escape') {
        if (window.c2Palette && c2Palette.isOpen) {
          c2Palette.close();
          return;
        }
        if (window.c2Dossier) {
          c2Dossier.closeDossier();
        }
        if (window.c2Home) {
          c2Home.closeDispatchModal();
        }
        document.querySelectorAll('.modal-overlay.show').forEach(m => m.classList.remove('show'));
      }

      // Don't trigger other shortcuts when typing in inputs/textareas
      if (e.target.tagName === 'INPUT' || e.target.tagName === 'TEXTAREA' || e.target.tagName === 'SELECT') return;
      if (e.target.isContentEditable) return;

      // Ctrl+1 through Ctrl+8 → switch tabs
      if (e.ctrlKey && !e.shiftKey && !e.altKey && tabMap[e.key]) {
        e.preventDefault();
        this.switchTab(tabMap[e.key]);
        this.showToast(`⌨ Tab ${e.key}: ${tabMap[e.key].replace('tab-', '').toUpperCase()}`, 'info');
      }
      // Ctrl+D → Quick Dispatch (focus first PENDING victim)
      if (e.ctrlKey && e.key === 'd') {
        e.preventDefault();
        if (window.c2Home && c2Home.incidents) {
          const pending = c2Home.incidents.find(i => i.status === 'PENDING');
          if (pending) {
            this.switchTab('tab-home');
            c2Home.openDispatchModal(pending.nodeId);
          } else {
            this.showToast('Không có ca PENDING nào để điều phối', 'warning');
          }
        }
      }
      // Ctrl+M → Toggle Mute
      if (e.ctrlKey && e.key === 'm') {
        e.preventDefault();
        if (window.c2Audio) c2Audio.toggleMute();
      }
    });

    // Global backdrop click to dismiss modals when clicking outside modal box
    document.addEventListener('click', (e) => {
      if (e.target && e.target.classList.contains('modal-overlay')) {
        if (e.target.id === 'dispatch-modal' && window.c2Home) {
          c2Home.closeDispatchModal();
        } else {
          e.target.classList.remove('show');
        }
      }
    });
  },

  /* ─── Progressive Web App (PWA Service Worker) ─── */
  initServiceWorker() {
    if ('serviceWorker' in navigator) {
      window.addEventListener('load', () => {
        navigator.serviceWorker.register('./sw.js')
          .then((reg) => {
            console.log('[PWA] Service Worker đăng ký thành công (Offline Engine Active), scope:', reg.scope);
          })
          .catch((err) => {
            console.warn('[PWA] Service Worker đăng ký thất bại:', err);
          });
      });
    }
  },

  /* ─── STOMP WebSocket ─── */
  initWebSocket() {
    const statusEl = document.getElementById('hud-ws-status');
    const offlineBanner = document.getElementById('offline-banner');

    if (typeof StompJs === 'undefined') {
      if (statusEl) statusEl.innerHTML = '<span class="status-led status-led--warning" style="margin-right:4px"></span>HTTP POLLING';
      return;
    }

    this.stompClient = new StompJs.Client({
      brokerURL: `${location.protocol === 'https:' ? 'wss:' : 'ws:'}//${location.host}/ws`,
      reconnectDelay: 3000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      webSocketFactory: () => typeof SockJS !== 'undefined' ? new SockJS('/ws') : new WebSocket(this.stompClient.brokerURL)
    });

    this.stompClient.onConnect = () => {
      this.wsConnected = true;
      if (statusEl) statusEl.innerHTML = '<span class="status-led status-led--online" style="margin-right:4px"></span>CONNECTED';
      if (offlineBanner) offlineBanner.classList.remove('show');
      this.logSniffer('[STOMP-WS] Kết nối WebSocket thời gian thực thành công (/ws)');

      // Subscribe all topics (§6.2)
      this.stompClient.subscribe('/topic/victims', msg => {
        const p = JSON.parse(msg.body);
        this.onSosReceived(p);
      });
      this.stompClient.subscribe('/topic/dispatch-ack', msg => {
        const d = JSON.parse(msg.body);
        this.logSniffer(`[DISPATCH-ACK] ${d.type || 'ACK'} → ${d.targetNodeId || d.destinationNodeId}`);
        if (window.c2Home) c2Home.refreshIncidents();
      });
      this.stompClient.subscribe('/topic/interagency', msg => {
        const d = JSON.parse(msg.body);
        this.logSniffer(`[INTER-AGENCY] ${d.victimNodeId || ('Ticket #' + d.ticketId)} → ${d.agencyType || d.type}`);
        if (window.c2InterAgency) {
          c2InterAgency.loadTickets(true);
          c2InterAgency.loadFacilities();
        }
      });
      this.stompClient.subscribe('/topic/radio', msg => {
        const r = JSON.parse(msg.body);
        this.logSniffer(`[VHF-RADIO] ${r.sender}: ${r.message}`);
        if (window.c2InterAgency) c2InterAgency.loadRadioLogs(true);
      });
      this.stompClient.subscribe('/topic/network-metrics', msg => {
        if (window.c2Noc) c2Noc.onMetricsUpdate(JSON.parse(msg.body));
      });
      this.stompClient.subscribe('/topic/relay-status', msg => {
        if (window.c2Topology) c2Topology.onRelayUpdate(JSON.parse(msg.body));
      });
      this.stompClient.subscribe('/topic/packets', msg => {
        const pkt = JSON.parse(msg.body);
        this.logSniffer(`[PKT] ${pkt.packetType} src=${pkt.sourceNodeId} hop=${pkt.hopCount} sha=${(pkt.checksum||'').slice(0,12)}…`);
        if (window.c2Noc) c2Noc.onPacketReceived(pkt);
      });
      this.stompClient.subscribe('/topic/alerts', msg => {
        const a = JSON.parse(msg.body);
        this.logSniffer(`[CRITICAL-ALERT] ${a.message}`);
        if (window.c2Audio) c2Audio.triggerSosAlarm(5);
        this.activateStrobe(5000);
        this.showToast(`🚨 ${a.message}`, 'danger');
      });
      this.stompClient.subscribe('/topic/drops', msg => {
        const d = JSON.parse(msg.body);
        this.logSniffer(`[DROP] ${d.packetId}: ${d.reason}`);
      });
      this.stompClient.subscribe('/topic/rescue-teams', msg => {
        const d = JSON.parse(msg.body);
        this.logSniffer(`[RESCUE-TEAM] ${d.teamId} → ${d.type || d.status}`);
        if (window.c2Map) c2Map.onRescueTeamUpdate(d);
      });
    };

    this.stompClient.onDisconnect = () => {
      this.wsConnected = false;
      if (statusEl) statusEl.innerHTML = '<span class="status-led status-led--critical" style="margin-right:4px"></span>DISCONNECTED';
      if (offlineBanner) offlineBanner.classList.add('show');
    };

    this.stompClient.onStompError = (frame) => console.warn('[STOMP] Error:', frame);
    this.stompClient.activate();
  },

  onSosReceived(packet) {
    const pl = packet.payload || {};
    const name = pl.senderName || pl.fullName || packet.sourceNodeId;
    this.logSniffer(`[ALERT-SOS] ${packet.sourceNodeId} | ${pl.alertType} | ${name}`);
    this.showToast(`🚨 TÍN HIỆU SOS MỚI: ${name} (${pl.alertType})`, 'danger');
    if (window.c2Audio) c2Audio.triggerSosAlarm(5);
    this.activateStrobe(5000);
    if (window.c2Home) c2Home.refreshIncidents();
  },

  /* ─── Strobe ─── */
  activateStrobe(ms) {
    const el = document.getElementById('strobe-alarm');
    if (!el) return;
    el.classList.add('active');
    setTimeout(() => el.classList.remove('active'), ms);
  },

  /* ─── Toast Notification System (§6.9) ─── */
  showToast(message, type = 'info') {
    const container = document.getElementById('toast-container');
    if (!container) return;

    const icons = { danger: '🚨', success: '✅', info: '📡', warning: '⚠️' };
    const toast = document.createElement('div');
    toast.className = `toast toast--${type}`;
    toast.innerHTML = `
      <span class="toast-icon">${icons[type] || '📡'}</span>
      <span class="toast-body">${message}</span>
      <button class="toast-close" onclick="this.parentElement.remove()">✕</button>
    `;
    container.appendChild(toast);

    // Auto-dismiss after 5s
    setTimeout(() => {
      toast.classList.add('toast-exit');
      setTimeout(() => toast.remove(), 300);
    }, 5000);
  },

  /* ─── Packet Sniffer Console ─── */
  logSniffer(text) {
    ['packet-sniffer-console', 'noc-packet-sniffer'].forEach(id => {
      const el = document.getElementById(id);
      if (!el) return;
      const t = new Date().toLocaleTimeString('vi-VN', { hour12: false });
      const ms = String(new Date().getMilliseconds()).padStart(3, '0');
      const entry = document.createElement('div');
      entry.className = 'log-entry';
      entry.innerHTML = `<span class="log-time">[${t}.${ms}]</span> <span>${text}</span>`;
      el.appendChild(entry);
      if (el.children.length > 200) el.removeChild(el.firstChild);
      el.scrollTop = el.scrollHeight;
    });
  },

  /* ─── CSV Export ─── */
  async exportCsv() {
    try {
      const res = await fetch('/api/victims');
      const data = await res.json();
      if (!data.length) { this.showToast('Không có dữ liệu để xuất', 'warning'); return; }

      const headers = ['NodeID','HoVaTen','SDT','NhomMau','TinhHuong','MucDo','TrangThai','ViDo','KinhDo'];
      const rows = data.map(d => [
        d.nodeId, `"${d.fullName||''}"`, d.phone||'', d.bloodType||'O+',
        d.alertType, d.severity, d.status, d.latitude, d.longitude
      ]);
      const csv = '\uFEFF' + [headers.join(','), ...rows.map(r => r.join(','))].join('\n');
      const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
      const a = document.createElement('a');
      a.href = URL.createObjectURL(blob);
      a.download = `BAO_CAO_CUU_NAN_${new Date().toISOString().slice(0,10)}.csv`;
      a.click();
      this.showToast('Đã xuất CSV thành công!', 'success');
    } catch (e) {
      this.showToast('Lỗi xuất CSV: ' + e.message, 'danger');
    }
  },

  /* ─── Number Formatting (§4 rules) ─── */
  formatNumber(n) {
    if (n === null || n === undefined) return '0';
    return n.toString().replace(/\B(?=(\d{3})+(?!\d))/g, '.');
  }
};

window.c2App = c2App;
document.addEventListener('DOMContentLoaded', () => c2App.init());

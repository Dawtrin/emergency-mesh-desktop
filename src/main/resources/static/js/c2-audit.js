/**
 * C2 Audit Trail Module — Immutable Evidence Log with millisecond timestamps
 * IAP Report export (PDF via print) & CSV export
 */
const c2Audit = {
  packets: [],

  init() {
    this.loadAuditTrail();
    this.updateReportTime();
    setInterval(() => this.loadAuditTrail(), 5000);
  },

  async loadAuditTrail() {
    try {
      const res = await fetch('/api/packets?limit=100');
      this.packets = await res.json();
      this.renderTable();
      this.updateReportSummary();
    } catch (e) { /* quiet */ }
  },

  renderTable() {
    const tbody = document.getElementById('audit-trail-tbody');
    if (!tbody) return;

    if (!this.packets.length) {
      tbody.innerHTML = '<tr><td colspan="5"><div class="state-empty"><span class="state-icon">📜</span><p class="state-title">CHƯA CÓ NHẬT KÝ</p><p class="state-desc">Chưa nhận được packet nào từ Mesh</p></div></td></tr>';
      return;
    }

    tbody.innerHTML = this.packets.map(p => {
      const ts = p.timestamp ? new Date(p.timestamp) : new Date();
      const timeStr = ts.toLocaleTimeString('vi-VN', { hour12: false }) + '.' + String(ts.getMilliseconds()).padStart(3, '0');

      const typeColor = {
        SOS_ALERT: 'var(--defcon-red)',
        HEARTBEAT: 'var(--life-green)',
        DISPATCH_CMD: 'var(--orbital-amber)',
        ACK: 'var(--aero-cyan)'
      };

      return `<tr>
        <td style="color:var(--text-muted);font-size:11px">${timeStr}</td>
        <td><span class="badge" style="color:${typeColor[p.packetType] || 'var(--aero-cyan)'};border:1px solid ${typeColor[p.packetType] || 'var(--aero-cyan)'};background:rgba(0,240,255,.1)">${p.packetType || 'UNKNOWN'}</span></td>
        <td><strong style="color:var(--aero-cyan)">${p.sourceNodeId || '—'}</strong></td>
        <td class="text-cell" style="font-size:11px;max-width:200px;overflow:hidden;text-overflow:ellipsis">hop=${p.hopCount} ttl=${p.ttl} route=${p.routeHistory || '—'}</td>
        <td style="font-size:10px;color:var(--text-muted);max-width:140px;overflow:hidden;text-overflow:ellipsis" title="${p.checksum || ''}">${(p.checksum || '—').slice(0, 20)}…</td>
      </tr>`;
    }).join('');
  },

  updateReportTime() {
    const el = document.getElementById('report-time');
    if (el) el.textContent = new Date().toISOString().slice(0, 19).replace('T', ' ');
  },

  async updateReportSummary() {
    try {
      const res = await fetch('/api/victims/stats');
      const stats = await res.json();
      const el = document.getElementById('report-rate');
      if (el) el.textContent = `${stats.resolveRate || 0}% — ${stats.resolved || 0}/${stats.total || 0} ca hoàn thành`;
    } catch (e) { /* quiet */ }
  }
};

window.c2Audit = c2Audit;

/**
 * C2 NOC Module — Tactical Network Operations Center (Đề tài 53)
 * 4 charts: Throughput, Hop Distribution, Dedup Gauge, Relay Load
 * + Live Packet Sniffer console
 * + Time-Range Selector (§6.8 compliance)
 */
const c2Noc = {
  charts: {},
  throughputData: [],
  throughputHistory: [],   // Full history buffer for time-range filtering
  packetCount: 0,
  timeRange: 10,           // Default: show last 10 data points (~30s)
  refreshInterval: 3000,

  init() {
    this.initCharts();
    this.initTimeRangeSelector();
    this.fetchMetrics();
    setInterval(() => this.fetchMetrics(), this.refreshInterval);
  },

  /**
   * Time-Range Selector — Cho phép chọn khoảng thời gian hiển thị
   * Buttons: 5m | 15m | 1h | All
   */
  initTimeRangeSelector() {
    const container = document.getElementById('noc-time-range');
    if (!container) return;
    const ranges = [
      { label: '30s', points: 10 },
      { label: '5m', points: 100 },
      { label: '15m', points: 300 },
      { label: 'ALL', points: -1 }
    ];
    container.innerHTML = ranges.map((r, i) =>
      `<button class="time-range-btn${i === 0 ? ' active' : ''}" data-points="${r.points}">${r.label}</button>`
    ).join('');
    container.querySelectorAll('.time-range-btn').forEach(btn => {
      btn.addEventListener('click', () => {
        container.querySelectorAll('.time-range-btn').forEach(b => b.classList.remove('active'));
        btn.classList.add('active');
        this.timeRange = parseInt(btn.dataset.points);
        this.updateThroughputDisplay();
      });
    });
  },

  updateThroughputDisplay() {
    if (!this.charts.throughput) return;
    const data = this.timeRange === -1
      ? [...this.throughputHistory]
      : this.throughputHistory.slice(-this.timeRange);
    this.charts.throughput.data.labels = data.map((_, i) => `-${(data.length - i) * 3}s`);
    this.charts.throughput.data.datasets[0].data = data;
    this.charts.throughput.update('none');
  },


  initCharts() {
    // 1. Real-time Throughput (Area chart)
    const ctx1 = document.getElementById('chart-throughput');
    if (ctx1) {
      const gradient = ctx1.getContext('2d').createLinearGradient(0, 0, 0, 240);
      gradient.addColorStop(0, 'rgba(0,240,255,.3)');
      gradient.addColorStop(1, 'rgba(0,240,255,0)');

      this.charts.throughput = new Chart(ctx1, {
        type: 'line',
        data: {
          labels: Array(10).fill(''),
          datasets: [{
            label: 'Packets/Interval',
            data: Array(10).fill(0),
            borderColor: '#00F0FF', backgroundColor: gradient,
            borderWidth: 2, fill: true, tension: .4,
            pointBackgroundColor: '#00F0FF', pointRadius: 2, pointHoverRadius: 5
          }]
        },
        options: c2Charts.darkOpts()
      });
    }

    // 2. Hop Count Distribution
    const ctx2 = document.getElementById('chart-hops');
    if (ctx2) {
      this.charts.hops = new Chart(ctx2, {
        type: 'bar',
        data: {
          labels: ['Direct', '1-Hop', '2-Hop', '3+ Hops'],
          datasets: [{
            label: 'Gói tin',
            data: [0, 0, 0, 0],
            backgroundColor: ['#00FF66', '#00F0FF', '#FF9900', '#B026FF'],
            borderWidth: 0, borderRadius: 0
          }]
        },
        options: c2Charts.darkOpts()
      });
    }

    // 3. Relay Traffic Load
    const ctx4 = document.getElementById('chart-relay-load');
    if (ctx4) {
      this.charts.relayLoad = new Chart(ctx4, {
        type: 'bar',
        data: {
          labels: [],
          datasets: [{
            label: 'Gói đã chuyển tiếp',
            data: [],
            backgroundColor: 'rgba(0,240,255,.35)',
            borderColor: '#00F0FF', borderWidth: 1
          }]
        },
        options: c2Charts.darkOpts()
      });
    }

    // Init SVG gauge tick marks
    this.initGaugeTicks();
  },

  initGaugeTicks() {
    const gauge = document.getElementById('gauge-dedup');
    if (!gauge) return;
    const g = gauge.querySelector('.tick-marks');
    if (!g) return;
    for (let i = 0; i < 24; i++) {
      const angle = (i / 24) * 360;
      const rad = angle * Math.PI / 180;
      const x1 = 60 + 44 * Math.cos(rad), y1 = 60 + 44 * Math.sin(rad);
      const x2 = 60 + 48 * Math.cos(rad), y2 = 60 + 48 * Math.sin(rad);
      const line = document.createElementNS('http://www.w3.org/2000/svg', 'line');
      line.setAttribute('x1', x1); line.setAttribute('y1', y1);
      line.setAttribute('x2', x2); line.setAttribute('y2', y2);
      line.setAttribute('stroke', 'rgba(255,255,255,.08)'); line.setAttribute('stroke-width', '1');
      g.appendChild(line);
    }
  },

  async fetchMetrics() {
    try {
      const res = await fetch('/api/metrics/summary');
      const d = await res.json();

      // Update KPI numbers
      const recv = document.getElementById('metric-total-recv');
      if (recv) recv.textContent = c2App.formatNumber(d.totalReceived);

      const drop = document.getElementById('metric-drop-rate');
      if (drop) drop.textContent = `${d.dropRate}%`;

      const integ = document.getElementById('metric-integrity');
      if (integ) integ.textContent = `${d.integrity}%`;

      // Update Dedup Gauge SVG
      const uniqueRate = d.totalReceived > 0 ? ((d.totalReceived - d.totalDuplicates) / d.totalReceived * 100) : 100;
      const arc = document.getElementById('gauge-dedup-arc');
      if (arc) {
        const offset = 314 - (uniqueRate / 100 * 314);
        arc.setAttribute('stroke-dashoffset', offset);
        arc.setAttribute('stroke', uniqueRate > 80 ? 'var(--aero-cyan)' : uniqueRate > 50 ? 'var(--orbital-amber)' : 'var(--defcon-red)');
      }
      const gaugeText = document.getElementById('gauge-dedup-text');
      if (gaugeText) gaugeText.textContent = `${Math.round(uniqueRate)}%`;

      // Update throughput chart — store in history buffer for time-range selector
      const delta = d.totalReceived - (this.packetCount || 0);
      this.throughputData.push(delta);
      this.throughputHistory.push(delta);
      this.packetCount = d.totalReceived;
      if (this.throughputData.length > 10) this.throughputData.shift();
      // Keep history max 1200 points (~1 hour at 3s interval)
      if (this.throughputHistory.length > 1200) this.throughputHistory.shift();

      this.updateThroughputDisplay();

      // Fetch relay load
      const rl = await fetch('/api/metrics/relay-load');
      const relayData = await rl.json();
      if (this.charts.relayLoad && relayData.length) {
        this.charts.relayLoad.data.labels = relayData.map(r => r.nodeId || r.relayNodeId);
        this.charts.relayLoad.data.datasets[0].data = relayData.map(r => r.packetsForwarded || r.count || 0);
        this.charts.relayLoad.update('none');
      }
    } catch (e) { /* quiet */ }
  },

  onMetricsUpdate(data) {
    // Real-time via WebSocket
    this.fetchMetrics();
  },

  onPacketReceived(pkt) {
    // Update hop chart
    if (this.charts.hops && pkt.hopCount !== undefined) {
      const idx = Math.min(pkt.hopCount, 3);
      this.charts.hops.data.datasets[0].data[idx] = (this.charts.hops.data.datasets[0].data[idx] || 0) + 1;
      this.charts.hops.update('none');
    }

    // Log to NOC sniffer
    const sniffer = document.getElementById('noc-packet-sniffer');
    if (sniffer) {
      const t = new Date().toLocaleTimeString('vi-VN', { hour12: false });
      const ms = String(new Date().getMilliseconds()).padStart(3, '0');
      const entry = document.createElement('div');
      entry.className = 'log-entry';
      entry.innerHTML = `<span class="log-time">[${t}.${ms}]</span> <span class="log-hash">${(pkt.checksum||'').slice(0,16)}</span> <span>${pkt.packetType} src=${pkt.sourceNodeId} hop=${pkt.hopCount} ttl=${pkt.ttl}</span>`;
      sniffer.appendChild(entry);
      if (sniffer.children.length > 100) sniffer.removeChild(sniffer.firstChild);
      sniffer.scrollTop = sniffer.scrollHeight;
    }
  }
};

window.c2Noc = c2Noc;

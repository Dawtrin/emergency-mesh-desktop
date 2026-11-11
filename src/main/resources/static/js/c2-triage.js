/**
 * C2 Triage Module — Medical Classification & Disaster Statistics (Đề tài 54)
 * Donut (disaster types), Bar (blood needs), Cumulative progress, Triage matrix
 */
const c2Triage = {
  charts: {},

  init() {
    this.initCharts();
  },

  initCharts() {
    // 1. Disaster Type Donut (§8)
    const ctx1 = document.getElementById('chart-disaster-type');
    if (ctx1) {
      this.charts.disaster = new Chart(ctx1, {
        type: 'doughnut',
        data: {
          labels: ['Lũ lụt & Mắc kẹt', 'Cấp cứu Y tế', 'Sạt lở đất', 'Động đất'],
          datasets: [{
            data: [0, 0, 0, 0],
            backgroundColor: ['#00F0FF', '#00B4D8', '#FF9900', '#FF1E40'],
            borderWidth: 0, hoverOffset: 6
          }]
        },
        options: {
          ...c2Charts.darkOpts(false),
          cutout: '68%',
          plugins: {
            ...c2Charts.darkOpts(false).plugins,
            legend: { position: 'bottom', labels: { color: '#7C8BA1', font: { family: "'Space Grotesk'", size: 10 }, padding: 10, boxWidth: 10 } }
          }
        }
      });
    }

    // 2. Blood Type Needs (Horizontal Bar)
    const ctx2 = document.getElementById('chart-blood-needs');
    if (ctx2) {
      this.charts.blood = new Chart(ctx2, {
        type: 'bar',
        data: {
          labels: ['O+', 'A+', 'B+', 'AB-', 'O-', 'A-', 'B-', 'AB+'],
          datasets: [{
            label: 'Số nạn nhân',
            data: [0, 0, 0, 0, 0, 0, 0, 0],
            backgroundColor: (ctx) => {
              const val = ctx.raw || 0;
              return val >= 3 ? '#FF1E40' : val >= 1 ? '#FF9900' : '#00FF66';
            },
            borderWidth: 0
          }]
        },
        options: {
          ...c2Charts.darkOpts(),
          indexAxis: 'y'
        }
      });
    }

    // 3. Cumulative Rescue Progress
    const ctx3 = document.getElementById('chart-cumulative');
    if (ctx3) {
      const g1 = ctx3.getContext('2d').createLinearGradient(0, 0, 0, 280);
      g1.addColorStop(0, 'rgba(255,153,0,.15)'); g1.addColorStop(1, 'transparent');

      this.charts.cumulative = new Chart(ctx3, {
        type: 'line',
        data: {
          labels: [],
          datasets: [
            {
              label: 'Tổng tiếp nhận',
              data: [], borderColor: '#FF9900', backgroundColor: g1,
              borderWidth: 2, fill: true, tension: .3, pointRadius: 2
            },
            {
              label: 'Đã cứu an toàn',
              data: [], borderColor: '#00FF66', backgroundColor: 'transparent',
              borderWidth: 2, fill: false, tension: .3, pointRadius: 2
            }
          ]
        },
        options: c2Charts.darkOpts()
      });
    }
  },

  /** Called from c2Home.refreshIncidents with fresh data */
  updateFromIncidents(incidents) {
    if (!incidents || !incidents.length) return;

    // Update Disaster Type Donut
    if (this.charts.disaster) {
      const types = { FLOOD: 0, MEDICAL: 0, LANDSLIDE: 0, EARTHQUAKE: 0 };
      incidents.forEach(i => {
        const t = (i.alertType || '').toUpperCase();
        if (t.includes('FLOOD') || t.includes('LŨ')) types.FLOOD++;
        else if (t.includes('MED') || t.includes('CẤP CỨU')) types.MEDICAL++;
        else if (t.includes('LAND') || t.includes('SẠT')) types.LANDSLIDE++;
        else if (t.includes('EARTH') || t.includes('ĐỘNG')) types.EARTHQUAKE++;
        else types.FLOOD++;
      });
      this.charts.disaster.data.datasets[0].data = [types.FLOOD, types.MEDICAL, types.LANDSLIDE, types.EARTHQUAKE];
      this.charts.disaster.update('none');
    }

    // Update Blood Type Bar
    if (this.charts.blood) {
      const bloods = { 'O+': 0, 'A+': 0, 'B+': 0, 'AB-': 0, 'O-': 0, 'A-': 0, 'B-': 0, 'AB+': 0 };
      incidents.forEach(i => {
        const bt = i.bloodType || 'O+';
        if (bt in bloods) bloods[bt]++;
        else bloods['O+']++;
      });
      this.charts.blood.data.datasets[0].data = Object.values(bloods);
      this.charts.blood.update('none');
    }

    // Update Triage Matrix
    this.updateTriageMatrix(incidents);

    // Update Cumulative (simplified — just show current counts)
    if (this.charts.cumulative) {
      const now = new Date().toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' });
      const ds = this.charts.cumulative.data;
      ds.labels.push(now);
      ds.datasets[0].data.push(incidents.length);
      ds.datasets[1].data.push(incidents.filter(i => i.status === 'RESOLVED').length);
      // Keep last 20 points
      if (ds.labels.length > 20) {
        ds.labels.shift();
        ds.datasets[0].data.shift();
        ds.datasets[1].data.shift();
      }
      this.charts.cumulative.update('none');
    }
  },

  updateTriageMatrix(incidents) {
    const red = incidents.filter(i => i.severity === 'CRITICAL' && i.status !== 'RESOLVED');
    const yellow = incidents.filter(i => (i.severity === 'HIGH' || i.severity === 'MEDIUM') && i.status !== 'RESOLVED');
    const green = incidents.filter(i => i.status === 'RESOLVED' || i.severity === 'LOW');

    const render = (list, elId) => {
      const el = document.getElementById(elId);
      if (!el) return;
      if (!list.length) {
        el.innerHTML = '<div class="state-empty"><p class="state-title">CHƯA CÓ</p></div>';
        return;
      }
      el.innerHTML = list.slice(0, 10).map(i => `
        <div class="triage-item">
          <span><strong style="color:var(--aero-cyan)">${i.nodeId}</strong> ${i.fullName || ''}</span>
          <span style="color:var(--text-muted);font-size:10px">${i.alertType}</span>
        </div>
      `).join('');
    };

    render(red, 'triage-red-list');
    render(yellow, 'triage-yellow-list');
    render(green, 'triage-green-list');
  }
};

window.c2Triage = c2Triage;

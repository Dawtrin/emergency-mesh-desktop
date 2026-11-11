/**
 * Device Registry & QR Code Pairing Controller (U4)
 * Quản lý danh sách thiết bị Node/Relay và tạo mã QR ghép đôi tự động cho điện thoại Android.
 */
const c2Devices = {
  qrCodeInstance: null,

  init() {
    // Không cần chạy gì khi init, đợi click mở modal
  },

  async openModal() {
    const modal = document.getElementById('devices-modal');
    if (!modal) return;
    modal.classList.add('show');
    await this.renderQrCode();
    await this.loadDevices();
  },

  closeModal() {
    const modal = document.getElementById('devices-modal');
    if (modal) modal.classList.remove('show');
  },

  async renderQrCode() {
    const container = document.getElementById('devices-qr-container');
    const details = document.getElementById('devices-qr-details');
    if (!container || !window.QRCode) return;

    container.innerHTML = '';
    try {
      const res = await fetch('/api/devices/qr-config');
      const config = await res.json();

      // Tự động nhận diện host: nếu truy cập qua localhost/127.0.0.1 thì ưu tiên dùng IP mạng LAN thực để điện thoại kết nối được
      let resolvedHost = window.location.hostname;
      if (!resolvedHost || resolvedHost === 'localhost' || resolvedHost === '127.0.0.1') {
        resolvedHost = config.primaryLanIp || window.location.hostname || '127.0.0.1';
      }

      // Dữ liệu cấu hình cho Mobile app quét
      const qrData = JSON.stringify({
        c2Host: resolvedHost,
        c2Port: config.meshPort || 8888,
        protocol: config.protocol || 'TCP_JSON_MESH',
        version: config.version || 'v1.0',
        ssid: config.networkSsid || 'MeshRescue_C2'
      });

      this.qrCodeInstance = new QRCode(container, {
        text: qrData,
        width: 160,
        height: 160,
        colorDark: '#05070B',
        colorLight: '#FFFFFF',
        correctLevel: QRCode.CorrectLevel.M
      });

      if (details) {
        details.innerHTML = `
          <div>C2 IP/Host: <strong style="color:var(--aero-cyan)">${resolvedHost}</strong></div>
          <div>Cổng Mesh TCP: <strong style="color:var(--orbital-amber)">${config.meshPort || 8888}</strong></div>
          <div>Giao thức: <strong style="color:var(--life-green)">${config.protocol || 'TCP_JSON_MESH'}</strong></div>
          <div>Mạng dã chiến: <strong>${config.networkSsid || 'MeshRescue_C2'}</strong></div>
        `;
      }
    } catch (e) {
      container.innerHTML = `<div style="color:var(--defcon-red);font-size:11px">Lỗi tạo mã QR: ${e.message}</div>`;
    }
  },

  async loadDevices() {
    const tbody = document.getElementById('devices-table-tbody');
    if (!tbody) return;
    tbody.innerHTML = '<tr><td colspan="5" style="text-align:center;padding:12px;color:var(--text-muted)">Đang quét thiết bị mạng Mesh...</td></tr>';

    try {
      const res = await fetch('/api/devices');
      if (!res.ok) throw new Error('HTTP ' + res.status);
      const list = await res.json();

      if (!list || list.length === 0) {
        tbody.innerHTML = '<tr><td colspan="5" style="text-align:center;padding:12px;color:var(--text-muted)">Chưa phát hiện thiết bị Node/Relay nào.</td></tr>';
        return;
      }

      tbody.innerHTML = list.map(d => {
        const statusClass = d.status === 'ONLINE' ? 'success' : (d.status === 'TIMEOUT' ? 'warning' : 'danger');
        const lastSeen = d.lastHeartbeatTimestamp ? new Date(d.lastHeartbeatTimestamp).toLocaleTimeString('vi-VN') : '—';
        return `
          <tr>
            <td><strong style="color:var(--aero-cyan)">${d.nodeId}</strong></td>
            <td>${d.nodeName || 'Trạm dã chiến'}</td>
            <td>${d.ipAddress || '—'}:${d.port || '—'}</td>
            <td><span class="badge ${statusClass}" style="font-size:9px">${d.status}</span></td>
            <td><span style="font-size:10px;color:var(--text-muted)">${lastSeen}</span></td>
          </tr>
        `;
      }).join('');
    } catch (e) {
      tbody.innerHTML = `<tr><td colspan="5" style="text-align:center;padding:12px;color:var(--defcon-red)">Lỗi: ${e.message}</td></tr>`;
    }
  }
};

window.c2Devices = c2Devices;

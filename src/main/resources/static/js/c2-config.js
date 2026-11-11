/**
 * C2 Config Module — Station Configuration & System Telemetry (Trang 7)
 */
const c2Config = {
  init() {
    this.bindSaveConfig();
    this.bindTestSiren();
  },

  bindSaveConfig() {
    const btn = document.getElementById('btn-save-config');
    if (!btn) return;
    btn.addEventListener('click', async () => {
      const lat = document.getElementById('cfg-latitude')?.value;
      const lon = document.getElementById('cfg-longitude')?.value;
      const vol = document.getElementById('cfg-volume')?.value;

      try {
        const res = await fetch('/api/config/station', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            latitude: parseFloat(lat),
            longitude: parseFloat(lon),
            sirenVolume: parseInt(vol)
          })
        });
        if (!res.ok) throw new Error('HTTP ' + res.status);
        c2App.showToast('Đã lưu cấu hình trạm thành công!', 'success');
      } catch (e) {
        c2App.showToast('Lỗi lưu cấu hình: ' + e.message, 'danger');
      }
    });
  },

  bindTestSiren() {
    const btn = document.getElementById('btn-test-siren');
    if (!btn) return;
    btn.addEventListener('click', () => {
      if (window.c2Audio) {
        c2Audio.triggerSosAlarm(4);
        c2App.showToast('🔊 Đang thử nghiệm còi hú khẩn cấp (4 giây)...', 'warning');
      }
    });
  }
};

window.c2Config = c2Config;

/**
 * Authentication & Duty Officer Administration (U1)
 * Quản lý phiên trực ban, xác thực bảo mật và phân quyền tác chiến (ADMIN / COMMANDER / DISPATCHER / OBSERVER).
 */
const c2Auth = {
  currentUser: null,

  async init() {
    await this.checkSession();
  },

  async checkSession() {
    try {
      const res = await fetch('/api/auth/me');
      if (res.ok) {
        this.currentUser = await res.json();
        this.renderUserBadge();
      } else {
        this.currentUser = null;
        this.renderUserBadge();
      }
    } catch (e) {
      console.warn('[c2Auth] Session check failed:', e);
    }
  },

  renderUserBadge() {
    const badgeContainer = document.getElementById('hud-user-session');
    if (!badgeContainer) return;

    if (this.currentUser && this.currentUser.username) {
      const roleColor = this.currentUser.role === 'ADMIN' ? 'var(--defcon-red)' : (this.currentUser.role === 'COMMANDER' ? 'var(--orbital-amber)' : 'var(--aero-cyan)');
      badgeContainer.innerHTML = `
        <div style="display:flex;align-items:center;gap:8px">
          <span style="font-size:10px;color:var(--text-muted)">TRỰC BAN:</span>
          <span style="font-size:11px;font-weight:700;color:#fff">${this.currentUser.fullName || this.currentUser.username}</span>
          <span class="badge" style="font-size:9px;color:${roleColor};border-color:${roleColor}">${this.currentUser.role}</span>
          <button class="btn-tactical" style="padding:2px 6px;font-size:9px" onclick="c2Auth.openAdminModal()" title="Quản lý nhân sự tác chiến">⚙ QUẢN TRỊ</button>
          <button class="btn-tactical" style="padding:2px 6px;font-size:9px;color:var(--text-muted)" onclick="c2Auth.logout()">ĐĂNG XUẤT</button>
        </div>
      `;
    } else {
      badgeContainer.innerHTML = `
        <button class="btn-tactical cyan" style="padding:4px 10px;font-size:10px" onclick="c2Auth.openLoginModal()">
          🔑 ĐĂNG NHẬP CÁN BỘ
        </button>
      `;
    }
  },

  openLoginModal() {
    const modal = document.getElementById('login-modal');
    if (modal) modal.classList.add('show');
  },

  closeLoginModal() {
    const modal = document.getElementById('login-modal');
    if (modal) modal.classList.remove('show');
  },

  async login() {
    const uInput = document.getElementById('login-username');
    const pInput = document.getElementById('login-password');
    const errEl = document.getElementById('login-error-msg');
    if (!uInput || !pInput) return;

    const username = uInput.value.trim();
    const password = pInput.value;

    if (!username || !password) {
      if (errEl) errEl.textContent = 'Vui lòng nhập đầy đủ tên đăng nhập và mật khẩu';
      return;
    }

    try {
      const res = await fetch('/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, password })
      });

      if (!res.ok) {
        const data = await res.json().catch(() => ({}));
        if (errEl) errEl.textContent = data.error || 'Sai tên đăng nhập hoặc mật khẩu';
        return;
      }

      this.currentUser = await res.json();
      c2App.showToast(`Chào mừng cán bộ: ${this.currentUser.fullName || this.currentUser.username}`, 'success');
      this.renderUserBadge();
      this.closeLoginModal();
    } catch (e) {
      if (errEl) errEl.textContent = 'Lỗi kết nối xác thực: ' + e.message;
    }
  },

  async logout() {
    try {
      await fetch('/api/auth/logout', { method: 'POST' });
    } catch (e) {}
    this.currentUser = null;
    this.renderUserBadge();
    c2App.showToast('Đã đăng xuất khỏi phiên tác chiến', 'info');
  },

  async openAdminModal() {
    const modal = document.getElementById('admin-users-modal');
    if (modal) {
      modal.classList.add('show');
      await this.loadUsersList();
    }
  },

  closeAdminModal() {
    const modal = document.getElementById('admin-users-modal');
    if (modal) modal.classList.remove('show');
  },

  async loadUsersList() {
    const tbody = document.getElementById('admin-users-tbody');
    if (!tbody) return;
    tbody.innerHTML = '<tr><td colspan="5" style="text-align:center;padding:12px;color:var(--text-muted)">Đang tải danh sách cán bộ...</td></tr>';

    try {
      const res = await fetch('/api/admin/users');
      if (!res.ok) throw new Error('Cần quyền ADMIN/COMMANDER để truy cập');
      const users = await res.json();

      tbody.innerHTML = users.map(u => {
        const statusBadge = u.active ? '<span class="badge success" style="font-size:9px">HOẠT ĐỘNG</span>' : '<span class="badge danger" style="font-size:9px">BỊ KHÓA</span>';
        return `
          <tr>
            <td><strong style="color:var(--aero-cyan)">${u.username}</strong></td>
            <td>${u.fullName || '—'}</td>
            <td><span class="badge" style="font-size:9px">${u.role}</span></td>
            <td>${statusBadge}</td>
            <td>
              <button class="btn-tactical" style="padding:2px 6px;font-size:9px" onclick="c2Auth.toggleUserStatus('${u.username}', ${!u.active})">
                ${u.active ? 'Khóa' : 'Mở khóa'}
              </button>
            </td>
          </tr>
        `;
      }).join('');
    } catch (e) {
      tbody.innerHTML = `<tr><td colspan="5" style="text-align:center;padding:12px;color:var(--defcon-red)">${e.message}</td></tr>`;
    }
  },

  async createUser() {
    const uInput = document.getElementById('new-user-username');
    const fInput = document.getElementById('new-user-fullname');
    const pInput = document.getElementById('new-user-password');
    const rSelect = document.getElementById('new-user-role');

    if (!uInput || !pInput || !uInput.value.trim() || !pInput.value) {
      c2App.showToast('Vui lòng nhập tên đăng nhập và mật khẩu cán bộ', 'warning');
      return;
    }

    const payload = {
      username: uInput.value.trim(),
      fullName: fInput ? fInput.value.trim() : '',
      password: pInput.value,
      role: rSelect ? rSelect.value : 'DISPATCHER'
    };

    try {
      const res = await fetch('/api/admin/users', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });
      if (!res.ok) {
        const err = await res.json().catch(() => ({}));
        throw new Error(err.error || 'Lỗi tạo tài khoản');
      }
      c2App.showToast(`Đã tạo tài khoản cán bộ: ${payload.username}`, 'success');
      uInput.value = '';
      if (fInput) fInput.value = '';
      pInput.value = '';
      await this.loadUsersList();
    } catch (e) {
      c2App.showToast(e.message, 'danger');
    }
  },

  async toggleUserStatus(username, active) {
    try {
      const res = await fetch(`/api/admin/users/${username}/status`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ active })
      });
      if (!res.ok) throw new Error('Không thể đổi trạng thái tài khoản');
      c2App.showToast(`Đã ${active ? 'mở khóa' : 'khóa'} tài khoản ${username}`, 'success');
      await this.loadUsersList();
    } catch (e) {
      c2App.showToast(e.message, 'danger');
    }
  }
};

window.c2Auth = c2Auth;

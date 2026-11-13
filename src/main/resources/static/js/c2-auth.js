/**
 * Authentication & Duty Officer Administration (U1)
 * Quản lý phiên trực ban, xác thực bảo mật và phân quyền tác chiến (ADMIN / COMMANDER / DISPATCHER / OBSERVER).
 */
const c2Auth = {
  currentUser: null,
  allUsers: [],
  selectedRoleFilter: 'ALL',

  async init() {
    await this.checkSession();
  },

  async checkSession() {
    try {
      const res = await fetch('/api/auth/me');
      if (res.ok) {
        this.currentUser = await res.json();
        this.renderUserBadge();
        this.renderShiftInfo();
        if (c2App.activeTab === 'tab-admin') {
          this.loadUsersList();
        }
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

    if (this.currentUser && this.currentUser.authenticated) {
      const roleColor = this.currentUser.role === 'ADMIN' ? 'var(--defcon-red)' : (this.currentUser.role === 'COMMANDER' ? 'var(--orbital-amber)' : 'var(--aero-cyan)');
      badgeContainer.innerHTML = `
        <div class="officer-session-card">
          <span style="font-size:10px;color:var(--text-muted);white-space:nowrap">TRỰC BAN:</span>
          <span class="officer-session-name" title="${this.currentUser.displayName || this.currentUser.username}">${this.currentUser.displayName || this.currentUser.username}</span>
          <span class="badge" style="font-size:9px;color:${roleColor};border-color:${roleColor};white-space:nowrap">${this.currentUser.role}</span>
          <button class="btn-tactical" style="padding:2px 7px;font-size:9px;white-space:nowrap" onclick="c2App.switchTab('tab-admin')" title="Mở trang Quản trị Cán bộ">⚙ QUẢN TRỊ</button>
          <button class="btn-tactical" style="padding:2px 7px;font-size:9px;color:var(--text-muted);white-space:nowrap" onclick="c2Auth.logout()" title="Đăng xuất">ĐĂNG XUẤT</button>
        </div>
      `;
    } else {
      badgeContainer.innerHTML = `
        <button class="btn-tactical cyan" style="padding:4px 10px;font-size:10px;white-space:nowrap" onclick="c2Auth.openLoginModal()">
          🔑 ĐĂNG NHẬP CÁN BỘ
        </button>
      `;
    }
  },

  renderShiftInfo() {
    const infoEl = document.getElementById('admin-current-shift-info');
    if (!infoEl) return;
    if (this.currentUser && this.currentUser.authenticated) {
      infoEl.innerHTML = `
        <div>Cán bộ trực ban: <strong style="color:var(--aero-cyan)">${this.currentUser.displayName} (${this.currentUser.username})</strong></div>
        <div>Cấp quyền tác chiến: <strong style="color:var(--orbital-amber)">${this.currentUser.role}</strong></div>
        <div>Phiên làm việc: <span class="badge success" style="font-size:9px">ĐANG TRỰC CHỈ HUY</span></div>
      `;
    } else {
      infoEl.innerHTML = `
        <div style="color:var(--text-muted)">Chưa đăng nhập cán bộ trực ban. Phiên hiện tại là: <strong>KHÁCH QUAN SÁT (OBSERVER)</strong> (Dữ liệu cá nhân bị che mờ để bảo mật).</div>
        <button class="btn-tactical cyan" style="margin-top:8px;padding:4px 12px;font-size:10px" onclick="c2Auth.openLoginModal()">ĐĂNG NHẬP NGAY</button>
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

      await this.checkSession();
      c2App.showToast(`Chào mừng cán bộ: ${this.currentUser.displayName || this.currentUser.username}`, 'success');
      this.closeLoginModal();
      if (window.c2Home) c2Home.refreshIncidents();
    } catch (e) {
      if (errEl) errEl.textContent = 'Lỗi kết nối xác thực: ' + e.message;
    }
  },

  async logout() {
    try {
      await fetch('/api/auth/logout', { method: 'POST' });
    } catch (e) {}
    await this.checkSession();
    c2App.showToast('Đã đăng xuất khỏi phiên tác chiến', 'info');
    if (window.c2Home) c2Home.refreshIncidents();
  },

  setRoleFilter(role) {
    this.selectedRoleFilter = role;
    document.querySelectorAll('.role-filter-btn').forEach(btn => {
      btn.classList.toggle('active', btn.dataset.role === role);
    });
    this.renderUsersTable();
  },

  async loadUsersList() {
    try {
      const res = await fetch('/api/admin/users');
      if (res.status === 401 || res.status === 403) {
        this.renderForbidden();
        return;
      }
      if (!res.ok) throw new Error('HTTP ' + res.status);
      this.allUsers = await res.json();
      this.renderUsersTable();
    } catch (e) {
      console.warn('[c2Auth] loadUsersList error:', e);
      this.renderForbidden();
    }
  },

  renderForbidden() {
    const tbody = document.getElementById('tab-admin-users-tbody');
    if (tbody) {
      tbody.innerHTML = `<tr><td colspan="6" style="text-align:center;padding:20px;color:var(--orbital-amber)">
        🔒 Bạn đang xem ở vai trò Quan sát (OBSERVER) hoặc chưa đăng nhập ADMIN.<br>
        <button class="btn-tactical cyan" style="margin-top:8px" onclick="c2Auth.openLoginModal()">ĐĂNG NHẬP ADMIN ĐỂ QUẢN TRỊ</button>
      </td></tr>`;
    }
  },

  renderUsersTable() {
    const tbody = document.getElementById('tab-admin-users-tbody');
    if (!tbody) return;

    let filtered = this.allUsers;
    if (this.selectedRoleFilter !== 'ALL') {
      filtered = this.allUsers.filter(u => u.role === this.selectedRoleFilter);
    }

    if (!filtered || filtered.length === 0) {
      tbody.innerHTML = '<tr><td colspan="6" style="text-align:center;padding:16px;color:var(--text-muted)">Không có tài khoản nào phù hợp</td></tr>';
      return;
    }

    tbody.innerHTML = filtered.map(u => {
      const statusBadge = u.active ? '<span class="badge success" style="font-size:9px">HOẠT ĐỘNG</span>' : '<span class="badge danger" style="font-size:9px">BỊ KHÓA</span>';
      const roleColor = u.role === 'ADMIN' ? 'var(--defcon-red)' : (u.role === 'COMMANDER' ? 'var(--orbital-amber)' : 'var(--aero-cyan)');
      const lastLogin = u.lastLoginAt ? new Date(u.lastLoginAt).toLocaleTimeString('vi-VN') + ' ' + new Date(u.lastLoginAt).toLocaleDateString('vi-VN') : 'Chưa đăng nhập';

      return `
        <tr>
          <td><strong style="color:var(--aero-cyan)">${u.username}</strong></td>
          <td><strong>${u.displayName || '—'}</strong></td>
          <td><span class="badge" style="font-size:9px;color:${roleColor};border-color:${roleColor}">${u.role}</span></td>
          <td>${statusBadge}</td>
          <td><span style="font-size:10px;color:var(--text-muted)">${lastLogin}</span></td>
          <td>
            <button class="btn-tactical" style="padding:2px 6px;font-size:9px" onclick="c2Auth.promptResetPassword('${u.username}')">Đổi MK</button>
            <button class="btn-tactical ${u.active ? 'danger' : 'success'}" style="padding:2px 6px;font-size:9px;margin-left:4px" onclick="c2Auth.toggleUserStatus('${u.username}', ${!u.active})">
              ${u.active ? 'Khóa' : 'Mở khóa'}
            </button>
          </td>
        </tr>
      `;
    }).join('');
  },

  async createUser() {
    const uInput = document.getElementById('tab-new-user-username');
    const fInput = document.getElementById('tab-new-user-fullname');
    const pInput = document.getElementById('tab-new-user-password');
    const rSelect = document.getElementById('tab-new-user-role');

    if (!uInput || !pInput || !uInput.value.trim() || !pInput.value) {
      c2App.showToast('Vui lòng nhập tên đăng nhập và mật khẩu cán bộ', 'warning');
      return;
    }

    const payload = {
      username: uInput.value.trim(),
      displayName: fInput ? fInput.value.trim() : uInput.value.trim(),
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
      c2App.showToast(`Đã tạo tài khoản cán bộ: ${payload.username} (${payload.role})`, 'success');
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
      const res = await fetch(`/api/admin/users/${username}`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ active })
      });
      if (!res.ok) throw new Error('Không thể đổi trạng thái tài khoản');
      c2App.showToast(`Đã ${active ? 'mở khóa' : 'khóa'} tài khoản ${username}`, 'success');
      await this.loadUsersList();
    } catch (e) {
      c2App.showToast(e.message, 'danger');
    }
  },

  promptResetPassword(username) {
    const newPass = prompt(`Nhập mật khẩu mới cho cán bộ ${username}:`);
    if (!newPass) return;
    this.executeResetPassword(username, newPass);
  },

  async executeResetPassword(username, newPassword) {
    try {
      const res = await fetch(`/api/admin/users/${username}/reset-password`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ newPassword })
      });
      if (!res.ok) throw new Error('Không thể đặt lại mật khẩu');
      c2App.showToast(`Đã đặt lại mật khẩu cho cán bộ ${username}`, 'success');
    } catch (e) {
      c2App.showToast(e.message, 'danger');
    }
  },

  async changeOwnPassword() {
    const oldP = document.getElementById('my-old-password')?.value;
    const newP = document.getElementById('my-new-password')?.value;
    if (!oldP || !newP) {
      c2App.showToast('Vui lòng nhập mật khẩu cũ và mật khẩu mới', 'warning');
      return;
    }
    try {
      const res = await fetch('/api/auth/change-password', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ oldPassword: oldP, newPassword: newP })
      });
      if (!res.ok) {
        const d = await res.json().catch(() => ({}));
        throw new Error(d.error || 'Đổi mật khẩu thất bại');
      }
      c2App.showToast('Đổi mật khẩu cá nhân thành công!', 'success');
      document.getElementById('my-old-password').value = '';
      document.getElementById('my-new-password').value = '';
    } catch (e) {
      c2App.showToast(e.message, 'danger');
    }
  }
};

window.c2Auth = c2Auth;

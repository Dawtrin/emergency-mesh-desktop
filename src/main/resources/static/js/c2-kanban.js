/**
 * Dispatch Kanban Board Controller (U3)
 * Quản lý quy trình cứu nạn 3 cột: PENDING -> DISPATCHED -> RESOLVED
 * Hỗ trợ kéo thả tác chiến (Drag & Drop) và thao tác 1 chạm.
 */
const c2Kanban = {
  currentView: 'table', // 'table' hoặc 'kanban'

  init() {
    // Khởi tạo nút chuyển đổi chế độ xem nếu có
    const toggleBtn = document.getElementById('btn-toggle-kanban');
    if (toggleBtn) {
      toggleBtn.addEventListener('click', () => this.toggleView());
    }
  },

  toggleView() {
    this.currentView = this.currentView === 'table' ? 'kanban' : 'table';
    this.render();
  },

  render(incidents) {
    const list = incidents || (window.c2Home ? c2Home.incidents : []);
    const tableView = document.getElementById('all-incidents-table-wrapper');
    const kanbanView = document.getElementById('incidents-kanban-wrapper');
    const toggleBtn = document.getElementById('btn-toggle-kanban');

    if (!kanbanView) return;

    if (this.currentView === 'kanban') {
      if (tableView) tableView.style.display = 'none';
      kanbanView.style.display = 'grid';
      if (toggleBtn) toggleBtn.innerHTML = '📋 XEM DẠNG BẢNG';
      this.populateColumns(list);
    } else {
      if (tableView) tableView.style.display = 'block';
      kanbanView.style.display = 'none';
      if (toggleBtn) toggleBtn.innerHTML = '📊 BẢNG KANBAN ĐIỀU PHỐI';
    }
  },

  populateColumns(list) {
    const pendingCol = document.getElementById('kanban-col-pending');
    const dispatchedCol = document.getElementById('kanban-col-dispatched');
    const resolvedCol = document.getElementById('kanban-col-resolved');

    if (!pendingCol || !dispatchedCol || !resolvedCol) return;

    const pendingItems = list.filter(i => i.status === 'PENDING' || !i.status);
    const dispatchedItems = list.filter(i => i.status === 'DISPATCHED');
    const resolvedItems = list.filter(i => i.status === 'RESOLVED');

    // Cập nhật số đếm badge
    const pCount = document.getElementById('kanban-count-pending');
    const dCount = document.getElementById('kanban-count-dispatched');
    const rCount = document.getElementById('kanban-count-resolved');
    if (pCount) pCount.textContent = pendingItems.length;
    if (dCount) dCount.textContent = dispatchedItems.length;
    if (rCount) rCount.textContent = resolvedItems.length;

    pendingCol.innerHTML = pendingItems.map(item => this.createCardHtml(item, 'PENDING')).join('') || this.getEmptyColHtml();
    dispatchedCol.innerHTML = dispatchedItems.map(item => this.createCardHtml(item, 'DISPATCHED')).join('') || this.getEmptyColHtml();
    resolvedCol.innerHTML = resolvedItems.map(item => this.createCardHtml(item, 'RESOLVED')).join('') || this.getEmptyColHtml();

    // Gắn sự kiện drag and drop
    this.attachDragEvents();
  },

  getEmptyColHtml() {
    return `<div style="color:var(--text-muted);font-size:11px;text-align:center;padding:16px;border:1px dashed var(--border-subtle);border-radius:4px">Không có mục nào</div>`;
  },

  createCardHtml(item, currentStatus) {
    const sevBadge = item.severity === 'CRITICAL'
      ? '<span class="badge critical" style="font-size:9px">NGUY KỊCH</span>'
      : '<span class="badge high" style="font-size:9px">ƯU TIÊN</span>';

    let gpsBadge = '';
    if (item.hasFix === false) {
      gpsBadge = '<span class="badge warning" style="font-size:9px">⚠ CHƯA FIX GPS</span>';
    } else if (item.latitude && item.longitude) {
      gpsBadge = `<span style="font-size:10px;color:var(--text-muted)">[${Number(item.latitude).toFixed(3)}, ${Number(item.longitude).toFixed(3)}]</span>`;
    }

    let actionButtons = '';
    if (currentStatus === 'PENDING') {
      actionButtons = `
        <button class="btn-tactical danger" style="padding:4px 8px;font-size:9px" onclick="c2Home.openDispatchModal('${item.nodeId}')">⚡ ĐIỀU PHỐI</button>
        <button class="btn-tactical success" style="padding:4px 8px;font-size:9px" onclick="c2Kanban.moveStatus('${item.nodeId}', 'DISPATCHED')">→ TIẾP CẬN</button>
      `;
    } else if (currentStatus === 'DISPATCHED') {
      actionButtons = `
        <button class="btn-tactical success" style="padding:4px 8px;font-size:9px" onclick="c2Kanban.moveStatus('${item.nodeId}', 'RESOLVED')">✓ ĐÃ CỨU</button>
        <button class="btn-tactical" style="padding:4px 8px;font-size:9px" onclick="c2Kanban.moveStatus('${item.nodeId}', 'PENDING')">← CHỜ</button>
      `;
    } else {
      actionButtons = `
        <button class="btn-tactical" style="padding:4px 8px;font-size:9px" onclick="c2Kanban.moveStatus('${item.nodeId}', 'DISPATCHED')">↺ MỞ LẠI</button>
      `;
    }

    return `
      <div class="kanban-card" draggable="true" data-node-id="${item.nodeId}" style="background:var(--bg-card);border:1px solid var(--border-subtle);border-radius:4px;padding:10px;margin-bottom:8px;cursor:grab;transition:transform 0.15s, border-color 0.15s">
        <div style="display:flex;justify-content:space-between;align-items:flex-start;margin-bottom:4px">
          <div>
            <strong style="color:var(--aero-cyan);font-size:11px">${item.nodeId}</strong>
            <span style="font-size:11px;color:#fff;margin-left:4px;font-weight:600">${item.fullName || 'Nạn nhân'}</span>
          </div>
          <div>${sevBadge}</div>
        </div>
        <div style="font-size:10px;color:var(--text-secondary);margin-bottom:4px">
          ${item.alertType || 'CẤP CỨU MESH'} • Nhóm máu: <strong style="color:var(--defcon-red)">${item.bloodType || 'O+'}</strong>
        </div>
        <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:8px">
          <div>${gpsBadge}</div>
          <div style="font-size:10px;color:var(--text-muted)">SĐT: ${item.phone || 'N/A'}</div>
        </div>
        <div style="display:flex;justify-content:space-between;align-items:center;border-top:1px solid rgba(255,255,255,0.06);padding-top:6px">
          <button class="btn-tactical" style="padding:3px 6px;font-size:9px" onclick="c2Dossier.openDossier('${item.nodeId}')">📁 HỒ SƠ</button>
          <div style="display:flex;gap:4px">
            ${actionButtons}
          </div>
        </div>
      </div>
    `;
  },

  attachDragEvents() {
    const cards = document.querySelectorAll('.kanban-card');
    const cols = document.querySelectorAll('.kanban-col-body');

    cards.forEach(card => {
      card.addEventListener('dragstart', (e) => {
        e.dataTransfer.setData('text/plain', card.dataset.nodeId);
        card.style.opacity = '0.4';
      });
      card.addEventListener('dragend', () => {
        card.style.opacity = '1';
      });
    });

    cols.forEach(col => {
      col.addEventListener('dragover', (e) => {
        e.preventDefault();
        col.style.background = 'rgba(0, 240, 255, 0.05)';
      });
      col.addEventListener('dragleave', () => {
        col.style.background = 'transparent';
      });
      col.addEventListener('drop', (e) => {
        e.preventDefault();
        col.style.background = 'transparent';
        const nodeId = e.dataTransfer.getData('text/plain');
        const targetStatus = col.dataset.status;
        if (nodeId && targetStatus) {
          this.moveStatus(nodeId, targetStatus);
        }
      });
    });
  },

  async moveStatus(nodeId, newStatus) {
    if (window.c2Home) {
      await c2Home.updateStatus(nodeId, newStatus);
    }
  }
};

window.c2Kanban = c2Kanban;

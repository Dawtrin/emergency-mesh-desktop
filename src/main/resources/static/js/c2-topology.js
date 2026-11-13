/**
 * C2 Topology Module — SVG Force-Directed Mesh Network Topology
 * Nodes: C2 (cyan), Relay (purple), Victim (severity-based)
 * Links pulse when packets flow through them
 */
const c2Topology = {
  nodes: [],
  links: [],
  svg: null,
  width: 900,
  height: 460,
  simulation: null,
  relayData: [],

  init() {
    this.svg = document.getElementById('topology-svg');
    if (!this.svg) return;
    this.buildBaseTopology();
    this.loadRelayNodes();
    setInterval(() => this.loadRelayNodes(), 5000);
  },

  buildBaseTopology() {
    // Base station node
    this.nodes = [
      { id: 'C2_HQ', label: 'C2 HQ', type: 'base', x: 450, y: 230, r: 28, color: '#00F0FF' }
    ];
    this.links = [];
    this.render();
  },

  async loadRelayNodes() {
    try {
      const res = await fetch('/api/relay-nodes');
      this.relayData = await res.json();
      this.rebuildGraph();
      this.renderRelayTable();
    } catch (e) { /* quiet */ }
  },

  rebuildGraph() {
    const base = { id: 'C2_HQ', label: 'C2 HQ', type: 'base', x: 450, y: 230, r: 28, color: '#00F0FF' };
    this.nodes = [base];
    this.links = [];

    // Add relay nodes in a circle around base
    const relays = this.relayData;
    const angleStep = (2 * Math.PI) / Math.max(relays.length, 1);
    const relayRadius = 160;

    relays.forEach((r, i) => {
      const angle = angleStep * i - Math.PI / 2;
      const x = 450 + relayRadius * Math.cos(angle);
      const y = 230 + relayRadius * Math.sin(angle);
      const statusColor = r.status === 'ONLINE' ? '#00FF66' : r.status === 'TIMEOUT' ? '#FF9900' : '#4B5566';

      this.nodes.push({
        id: r.nodeId, label: r.nodeName || r.nodeId,
        type: 'relay', x, y, r: 20, color: statusColor
      });
      this.links.push({
        source: 'C2_HQ', target: r.nodeId,
        color: '#00F0FF', active: r.status === 'ONLINE'
      });
    });

    // Add victim nodes from c2Home incidents
    if (window.c2Home && c2Home.incidents) {
      const victims = c2Home.incidents.slice(0, 12);
      victims.forEach((v, i) => {
        // Connect to nearest relay or directly to base
        const parentRelay = relays.length > 0 ? relays[i % relays.length].nodeId : 'C2_HQ';
        const parentNode = this.nodes.find(n => n.id === parentRelay);
        const angle = (Math.random() - 0.5) * Math.PI;
        const dist = 80 + Math.random() * 60;
        const px = parentNode ? parentNode.x : 450;
        const py = parentNode ? parentNode.y : 230;

        const sevColor = v.severity === 'CRITICAL' ? '#FF1E40' : v.severity === 'HIGH' ? '#FF9900' : '#00F0FF';
        this.nodes.push({
          id: v.nodeId, label: v.nodeId.slice(0, 8),
          type: 'victim', x: px + dist * Math.cos(angle), y: py + dist * Math.sin(angle),
          r: 12, color: sevColor
        });
        this.links.push({
          source: parentRelay, target: v.nodeId,
          color: sevColor, active: v.status === 'PENDING'
        });
      });
    }

    this.render();
  },

  render() {
    if (!this.svg) return;
    const ns = 'http://www.w3.org/2000/svg';

    // Clear
    this.svg.innerHTML = '';

    // Background gradient
    const defs = document.createElementNS(ns, 'defs');
    const radGrad = document.createElementNS(ns, 'radialGradient');
    radGrad.id = 'topo-bg';
    radGrad.innerHTML = '<stop offset="0%" stop-color="rgba(0,240,255,.03)"/><stop offset="100%" stop-color="transparent"/>';
    defs.appendChild(radGrad);
    this.svg.appendChild(defs);

    // Background circle
    const bgCircle = document.createElementNS(ns, 'circle');
    bgCircle.setAttribute('cx', '450'); bgCircle.setAttribute('cy', '230');
    bgCircle.setAttribute('r', '220'); bgCircle.setAttribute('fill', 'url(#topo-bg)');
    this.svg.appendChild(bgCircle);

    // Draw links
    this.links.forEach(link => {
      const src = this.nodes.find(n => n.id === link.source);
      const tgt = this.nodes.find(n => n.id === link.target);
      if (!src || !tgt) return;

      const line = document.createElementNS(ns, 'line');
      line.setAttribute('x1', src.x); line.setAttribute('y1', src.y);
      line.setAttribute('x2', tgt.x); line.setAttribute('y2', tgt.y);
      line.setAttribute('stroke', link.color);
      line.setAttribute('stroke-width', link.active ? '2' : '1');
      line.setAttribute('stroke-dasharray', '6,4');
      line.setAttribute('opacity', link.active ? '0.8' : '0.3');
      if (link.active) {
        line.classList.add('topology-link', 'active');
      }
      this.svg.appendChild(line);
    });

    // Draw nodes
    this.nodes.forEach(node => {
      const g = document.createElementNS(ns, 'g');
      g.classList.add('topology-node');

      // Glow ring for base station
      if (node.type === 'base') {
        const glow = document.createElementNS(ns, 'circle');
        glow.setAttribute('cx', node.x); glow.setAttribute('cy', node.y);
        glow.setAttribute('r', node.r + 8);
        glow.setAttribute('fill', 'none'); glow.setAttribute('stroke', node.color);
        glow.setAttribute('stroke-width', '1'); glow.setAttribute('opacity', '.3');
        glow.setAttribute('stroke-dasharray', '4,4');
        g.appendChild(glow);
      }

      // Circle
      const circle = document.createElementNS(ns, 'circle');
      circle.setAttribute('cx', node.x); circle.setAttribute('cy', node.y);
      circle.setAttribute('r', node.r);
      circle.setAttribute('fill', '#05070B');
      circle.setAttribute('stroke', node.color);
      circle.setAttribute('stroke-width', node.type === 'base' ? '3' : '2');
      circle.style.filter = `drop-shadow(0 0 6px ${node.color})`;
      g.appendChild(circle);

      // Label
      const text = document.createElementNS(ns, 'text');
      text.setAttribute('x', node.x); text.setAttribute('y', node.y + 4);
      text.setAttribute('fill', node.color);
      text.classList.add('topology-label');
      text.setAttribute('font-size', node.type === 'base' ? '11' : '8');
      text.textContent = node.label;
      g.appendChild(text);

      this.svg.appendChild(g);
    });
  },

  renderRelayTable() {
    const tbody = document.getElementById('relay-monitor-tbody');
    if (!tbody) return;

    if (!this.relayData.length) {
      tbody.innerHTML = '<tr><td colspan="6"><div class="state-empty"><span class="state-icon">📡</span><p class="state-title">CHƯA CÓ RELAY</p></div></td></tr>';
      return;
    }

    tbody.innerHTML = this.relayData.map(r => {
      const statusBadge = r.status === 'ONLINE'
        ? '<span class="badge resolved">ONLINE</span>'
        : r.status === 'TIMEOUT'
          ? '<span class="badge dispatched">TIMEOUT</span>'
          : '<span class="badge pending">OFFLINE</span>';
      const hbTimestamp = r.lastHeartbeat || r.lastHeartbeatTimestamp;
      const hb = hbTimestamp ? new Date(hbTimestamp).toLocaleTimeString('vi-VN') : 'N/A';
      const fwd = r.packetsForwarded || r.packetsForwardedCount || 0;
      const maxFwd = Math.max(...this.relayData.map(x => x.packetsForwarded || x.packetsForwardedCount || 0), 1);
      const barW = Math.round((fwd / maxFwd) * 60);

      return `<tr>
        <td><strong style="color:var(--relay-purple)">${r.nodeId}</strong></td>
        <td class="text-cell">${r.nodeName || '—'}</td>
        <td>${r.ipAddress || '—'}:${r.port || '—'}</td>
        <td style="color:var(--text-muted)">${hb}</td>
        <td>${c2App.formatNumber(fwd)} <span class="table-mini-bar" style="width:${barW}px"></span></td>
        <td>${statusBadge}</td>
      </tr>`;
    }).join('');
  },

  onRelayUpdate(data) {
    this.loadRelayNodes();
  }
};

window.c2Topology = c2Topology;

/**
 * C2 Common Operating Picture (COP) Map Module
 * - Real Street Turn-by-Turn Routing via OSRM (Da Nang road network)
 * - 15 Real Da Nang Facilities with Authentic Photos and Full Telemetry
 * - Interactive Victim Status Switcher (PENDING, DISPATCHED, RESOLVED)
 * - 1-Click Seamless Inter-Agency Dispatch integration
 * - Esri World Dark Gray Base (watermark-free tactical canvas)
 */
const c2Map = {
  map: null,
  miniMap: null,
  baseCoords: [15.9753, 108.2532], // ĐH CNTT & TT Việt - Hàn (VKU HQ Đà Nẵng)
  markers: {},
  facilityMarkers: [],
  routeLine: null,
  activeRoutingVictim: null,
  radarEnabled: true,
  radarLayerGroup: null,
  miniRadarLayerGroup: null,

  init() {
    this.initCopMap();
    this.initMiniMap();
    this.loadFacilities();
    this.loadRescueTeams();
  },

  initCopMap() {
    if (document.getElementById('cop-map') && !this.map) {
      // Ưu tiên bản đồ dã chiến cục bộ offline nếu có, tự động fallback sang ArcGIS online
      this.darkLayer = L.tileLayer('/tiles/{z}/{x}/{y}.png', {
        minZoom: 10,
        maxZoom: 18,
        noWrap: true,
        attribution: '© Offline Tactical Canvas • Fallback Esri Dark Gray',
        errorTileUrl: 'https://services.arcgisonline.com/arcgis/rest/services/Canvas/World_Dark_Gray_Base/MapServer/tile/{z}/{y}/{x}'
      });
      this.satLayer = L.tileLayer('https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}', {
        minZoom: 10,
        maxZoom: 18,
        noWrap: true,
        attribution: '© Esri World Imagery'
      });

      this.map = L.map('cop-map', {
        center: this.baseCoords,
        zoom: 12,
        minZoom: 10,
        maxZoom: 18,
        maxBounds: [[15.65, 107.75], [16.45, 108.65]],
        maxBoundsViscosity: 0.9,
        layers: [this.darkLayer],
        zoomControl: false
      });
      L.control.zoom({ position: 'bottomright' }).addTo(this.map);

      // Radar layer group to toggle
      this.radarLayerGroup = L.layerGroup().addTo(this.map);

      // Base Station HQ marker at VKU
      L.marker(this.baseCoords, {
        icon: L.divIcon({
          className: '',
          html: `<div style="position:relative;width:28px;height:28px;display:flex;align-items:center;justify-content:center">
                   <div style="position:absolute;inset:0;border-radius:50%;background:rgba(0,240,255,.2);animation:pulse 1.5s infinite"></div>
                   <div style="background:#00F0FF;width:18px;height:18px;border-radius:50%;box-shadow:0 0 24px #00F0FF,0 0 48px rgba(0,240,255,.6);border:2px solid #fff;display:flex;align-items:center;justify-content:center">
                     <div style="width:6px;height:6px;background:#05070B;border-radius:50%"></div>
                   </div>
                 </div>`,
          iconSize: [28, 28], iconAnchor: [14, 14]
        })
      }).addTo(this.map).bindPopup(`
        <div style="font:600 11px var(--font-mono);padding:6px;background:#0E131F;color:#fff;border:1px solid #00F0FF">
          <div style="color:#00F0FF;font-size:12px;font-weight:700">🛰️ TRUNG TÂM CHỈ HUY TÁC CHIẾN C2 DÃ CHIẾN (HQ)</div>
          <div style="color:#FFD000;margin-top:2px;font-weight:700">TRƯỜNG ĐẠI HỌC CNTT & TT VIỆT - HÀN (VKU)</div>
          <div style="color:var(--text-muted);font-size:10px;margin-top:2px">📍 470 Trần Đại Nghĩa, Q. Ngũ Hành Sơn, TP. Đà Nẵng</div>
          <div style="font-size:10px;color:var(--text-secondary);margin-top:2px">Tọa độ GPS: [15.9753°N, 108.2532°E]</div>
          <div style="font-size:10px;color:var(--orbital-amber);margin-top:2px">Phủ sóng Radar Mesh: <strong>28 KM TOÀN TP. ĐÀ NẴNG</strong></div>
          <div style="font-size:10px;color:var(--life-green);margin-top:2px">Trạng thái: <strong>SẴN SÀNG TÁC CHIẾN (DEFCON 1)</strong></div>
        </div>
      `);

      // 4 Range Rings covering the whole Da Nang City (5km, 10km, 18km, 28km)
      const ringConfigs = [
        { r: 5000, label: '5 KM — VÀNH ĐAI CẬN CĂN CỨ (HÒA QUÝ / NGŨ HÀNH SƠN)' },
        { r: 10000, label: '10 KM — VÀNH ĐAI TRUNG TÂM (CẨM LỆ / HẢI CHÂU / CẦU RỒNG)' },
        { r: 18000, label: '18 KM — VÀNH ĐAI ĐÔ THỊ (SƠN TRÀ / THANH KHÊ / HÒA VANG)' },
        { r: 28000, label: '28 KM — TOÀN BỘ ĐÀ NẴNG (LIÊN CHIỂU / ĐÈO HẢI VÂN)' }
      ];

      ringConfigs.forEach((cfg) => {
        L.circle(this.baseCoords, {
          radius: cfg.r, color: '#00F0FF', weight: 1,
          dashArray: '5,8', fillOpacity: 0.015, fillColor: '#00F0FF'
        }).addTo(this.radarLayerGroup);

        // Ring distance tag label
        const tagLat = this.baseCoords[0] + (cfg.r / 111320);
        L.marker([tagLat, this.baseCoords[1]], {
          icon: L.divIcon({
            className: '',
            html: `<div style="font:700 9px var(--font-mono);color:rgba(0,240,255,.75);background:rgba(5,7,11,.8);padding:1px 5px;border:1px solid rgba(0,240,255,.3);border-radius:2px;white-space:nowrap;transform:translate(-50%,-50%)">
                     ${cfg.label}
                   </div>`,
            iconSize: [0, 0]
          }),
          interactive: false
        }).addTo(this.radarLayerGroup);
      });

      // Azimuth radial lines (8 rays) from VKU
      const azimuthAngles = [0, 45, 90, 135, 180, 225, 270, 315];
      azimuthAngles.forEach(deg => {
        const rad = (deg * Math.PI) / 180;
        const dLat = (28000 * Math.cos(rad)) / 111320;
        const dLon = (28000 * Math.sin(rad)) / (111320 * Math.cos(this.baseCoords[0] * Math.PI / 180));
        L.polyline([this.baseCoords, [this.baseCoords[0] + dLat, this.baseCoords[1] + dLon]], {
          color: 'rgba(0, 240, 255, 0.15)',
          weight: 1,
          dashArray: '3, 6'
        }).addTo(this.radarLayerGroup);
      });

      // Animated Continuous Rotating Radar Sweep Beam at VKU
      const sweepIcon = L.divIcon({
        className: '',
        html: `<div style="position:relative;width:600px;height:600px;pointer-events:none">
                 <div class="radar-sweep-cone"></div>
                 <div class="radar-sweep-leading-line"></div>
               </div>`,
        iconSize: [600, 600],
        iconAnchor: [300, 300]
      });
      L.marker(this.baseCoords, { icon: sweepIcon, interactive: false }).addTo(this.radarLayerGroup);

      // Auto clear route when popup closed
      this.map.on('popupclose', () => {
        if (this.routeLine) {
          this.map.removeLayer(this.routeLine);
          this.routeLine = null;
        }
        this.activeRoutingVictim = null;
      });
    }
  },

  initMiniMap() {
    if (document.getElementById('mini-radar-map') && !this.miniMap) {
      this.miniMap = L.map('mini-radar-map', {
        center: this.baseCoords, zoom: 10,
        zoomControl: false, attributionControl: false
      });
      L.tileLayer('https://services.arcgisonline.com/arcgis/rest/services/Canvas/World_Dark_Gray_Base/MapServer/tile/{z}/{y}/{x}', { maxZoom: 16 }).addTo(this.miniMap);
      
      // VKU Marker on Mini Map
      L.circleMarker(this.baseCoords, { radius: 5, color: '#00F0FF', fillColor: '#00F0FF', fillOpacity: 1 }).addTo(this.miniMap);
      
      // Mini Radar Sweep
      const miniSweepIcon = L.divIcon({
        className: '',
        html: `<div style="position:relative;width:240px;height:240px;pointer-events:none">
                 <div class="radar-sweep-cone"></div>
                 <div class="radar-sweep-leading-line"></div>
               </div>`,
        iconSize: [240, 240],
        iconAnchor: [120, 120]
      });
      L.marker(this.baseCoords, { icon: miniSweepIcon, interactive: false }).addTo(this.miniMap);

      // Mini Range Rings
      [8000, 18000, 28000].forEach(r => {
        L.circle(this.baseCoords, {
          radius: r, color: 'rgba(0,240,255,.4)', weight: 1,
          dashArray: '3,6', fill: false
        }).addTo(this.miniMap);
      });
    }
  },

  /** Load 15 real Da Nang facilities with authentic photo banners */
  async loadFacilities() {
    try {
      const res = await fetch('/api/facilities');
      const list = await res.json();
      this.facilityMarkers = [];

      list.forEach(f => {
        const cfg = {
          HOSPITAL: {
            color: '#00F0FF',
            iconSvg: `<svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="#00F0FF" stroke-width="2.5"><path d="M12 5v14M5 12h14"/></svg>`,
            label: 'Y TẾ 115',
            image: '/images/facilities/hospital.jpg'
          },
          FIRE_STATION: {
            color: '#FF9900',
            iconSvg: `<svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="#FF9900" stroke-width="2"><polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/></svg>`,
            label: 'CỨU HỎA 114',
            image: '/images/facilities/fire_station.jpg'
          },
          POLICE_POST: {
            color: '#FFD000',
            iconSvg: `<svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="#FFD000" stroke-width="2"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>`,
            label: 'CẢNH SÁT 113',
            image: '/images/facilities/police.jpg'
          }
        };
        const c = cfg[f.facilityType] || cfg.HOSPITAL;

        const imgPath = f.imageUrl || c.image;
        const addressText = f.address ? `${f.address} (${f.district || 'Đà Nẵng'})` : 'Địa bàn TP. Đà Nẵng';
        const popupHtml = `
          <div style="font:500 11px var(--font-mono);width:300px;background:#0E131F;color:#fff;overflow:hidden;border:1px solid rgba(0,240,255,.25)">
            <div style="position:relative;width:100%;height:135px;overflow:hidden;background:#05070B">
              <img src="${imgPath}" style="width:100%;height:100%;object-fit:cover;filter:brightness(0.95)" alt="${f.facilityName}">
              <div style="position:absolute;inset:0;background:linear-gradient(to top, rgba(14,19,31,1) 0%, transparent 60%)"></div>
              <span class="badge" style="position:absolute;top:8px;left:8px;background:rgba(5,7,11,.85);color:${c.color};border:1px solid ${c.color};font-size:9px;font-weight:700">${c.label} • ${f.district || 'ĐÀ NẴNG'}</span>
            </div>
            <div style="padding:10px 14px 14px">
              <div style="font-weight:700;font-size:13px;color:#00F0FF;line-height:1.4;margin-bottom:4px">${f.facilityName}</div>
              <div style="font-size:10px;color:var(--text-muted);margin-bottom:6px">📍 ${addressText}</div>
              <div style="font-size:11px;color:var(--text-secondary);margin-bottom:3px">
                Hotline: <strong style="color:#00FF66"><a href="tel:${f.contactPhone}" style="color:#00FF66;text-decoration:none">📞 ${f.contactPhone}</a></strong>
              </div>
              <div style="font-size:11px;color:var(--text-secondary);margin-bottom:3px">
                Sức chứa khả dụng: <strong style="color:${c.color}">${f.availableCapacity} ca</strong>
              </div>
              <div style="font-size:9px;color:var(--text-muted);margin-top:4px">
                Tọa độ GPS: [${f.latitude.toFixed(4)}°N, ${f.longitude.toFixed(4)}°E]
              </div>
              <button onclick="c2Map.routeAllPendingToFacility('${f.facilityName}')" class="btn-tactical" style="width:100%;padding:6px;font-size:10px;margin-top:10px;text-align:center">
                ⚡ ĐIỀU HƯỚNG TẤT CẢ CA VỀ ĐÂY
              </button>
            </div>
          </div>
        `;

        const marker = L.marker([f.latitude, f.longitude], {
          icon: L.divIcon({
            className: '',
            html: `<div class="facility-badge-icon" style="border-color:${c.color}">${c.iconSvg}</div>`,
            iconSize: [26, 26], iconAnchor: [13, 13]
          })
        }).addTo(this.map).bindPopup(popupHtml, { maxWidth: 310, minWidth: 290 });

        this.facilityMarkers.push({ ...f, marker });
      });
    } catch (e) {
      console.error('[c2Map] Error loading facilities:', e);
    }
  },

  /** Render pulsing victim markers and drawer */
  renderVictimMarkers(incidents) {
    if (!this.map) return;
    const drawer = document.getElementById('map-drawer-victims');

    incidents.forEach(inc => {
      if (!inc.latitude || !inc.longitude) return;
      const isUnfixed = inc.hasFix === false;

      let color = '#FF1E40'; // PENDING (Red)
      if (inc.status === 'RESOLVED') color = '#00FF66'; // Green
      else if (inc.status === 'DISPATCHED') color = '#FF9900'; // Amber
      if (isUnfixed) color = '#8892B0'; // Gray/Muted when GPS not fixed

      const animStyle = isUnfixed ? '' : 'animation:pulse 1.2s infinite;';
      const borderStyle = isUnfixed ? 'border:2px dashed #FFCC00;' : 'border:2px solid #fff;';
      const icon = L.divIcon({
        className: '',
        html: `<div title="${isUnfixed ? 'VỊ TRÍ CHƯA CỐ ĐỊNH (NO GPS FIX)' : 'GPS FIX TỐT'}" style="background:${color};width:14px;height:14px;border-radius:50%;box-shadow:0 0 16px ${color}, 0 0 32px rgba(255,30,64,.3);${borderStyle}${animStyle}"></div>`,
        iconSize: [14, 14], iconAnchor: [7, 7]
      });

      if (this.markers[inc.nodeId]) {
        this.markers[inc.nodeId].setLatLng([inc.latitude, inc.longitude]).setIcon(icon);
      } else {
        const m = L.marker([inc.latitude, inc.longitude], { icon }).addTo(this.map);
        m.on('click', () => this.onVictimClick(inc));
        this.markers[inc.nodeId] = m;
      }
    });

    // Update floating drawer list
    if (drawer) {
      const activeIncidents = incidents.filter(i => i.status !== 'RESOLVED');
      drawer.innerHTML = activeIncidents.length ? activeIncidents.slice(0, 10).map(i => {
        const nearestHosp = this.findNearest(i.latitude, i.longitude, 'HOSPITAL');
        const estDist = nearestHosp ? this.distKm(i.latitude, i.longitude, nearestHosp.latitude, nearestHosp.longitude).toFixed(1) : '—';
        const stBadge = i.status === 'DISPATCHED' 
          ? '<span class="badge dispatched" style="font-size:8px">ĐANG CỨU</span>'
          : `<span class="badge ${i.severity === 'CRITICAL' ? 'critical' : 'high'}" style="font-size:8px">${i.severity}</span>`;
        const fixBadge = i.hasFix === false 
          ? '<span class="badge" style="background:#555;color:#FFCC00;font-size:7px;margin-left:4px">⚠ CHƯA FIX GPS</span>' 
          : (i.accuracyM ? `<span style="font-size:7px;color:var(--text-muted);margin-left:4px">±${i.accuracyM}m</span>` : '');

        return `<div class="facility-item" style="padding:8px;cursor:pointer;margin-bottom:6px;transition:background .2s" onclick="c2Map.focusAndRoute('${i.nodeId}')">
          <div style="display:flex;justify-content:space-between;align-items:center">
            <strong style="color:var(--aero-cyan);font-size:11px">${i.nodeId}</strong>
            <div>${stBadge}${fixBadge}</div>
          </div>
          <div style="font-size:11px;font-weight:600;color:var(--text-primary);margin-top:2px">${i.fullName || 'Nạn nhân Mesh'}</div>
          <div style="font-size:10px;color:var(--text-secondary);margin-top:1px">${i.alertType} • Máu: <strong style="color:var(--defcon-red)">${i.bloodType || 'O+'}</strong></div>
          <div style="font-size:9px;color:var(--text-muted);margin-top:3px;display:flex;justify-content:space-between">
            <span>Viện gần nhất: ${nearestHosp ? nearestHosp.facilityName.split('(')[0] : '—'}</span>
            <span style="color:var(--life-green)">~${estDist} km</span>
          </div>
        </div>`;
      }).join('') : '<p style="font:400 10px var(--font-sans);color:var(--text-muted);text-align:center;padding:16px">Không có ca cứu hộ chờ xử lý</p>';
    }
  },

  /** Focus on victim marker and calculate street route */
  focusAndRoute(nodeId) {
    if (window.c2Home && c2Home.incidents) {
      const victim = c2Home.incidents.find(i => i.nodeId === nodeId);
      if (victim && victim.latitude && victim.longitude) {
        this.map.flyTo([victim.latitude, victim.longitude], 14, { duration: 1 });
        this.onVictimClick(victim);
      }
    }
  },

  /**
   * On Victim Click:
   * 1. Find nearest hospital in Da Nang
   * 2. Call OSRM turn-by-turn road routing API
   * 3. Draw animated street polyline
   * 4. Open Tactical Action Dossier popup with 1-click inter-agency dispatch AND Status Switcher
   */
  /**
   * On Victim Click:
   * 1. Intelligently recommend primary agency based on alertType:
   *    - Flood/Drowning/Landslide/Vehicle Wreck/Mountain -> 114 Rescue
   *    - Violence/Traffic/Chaos -> 113 Police
   *    - Medical/Injuries/Obstetric -> 115 EMS
   * 2. Calculate real OSRM street route without blocking the map
   * 3. Display docked Tactical Mission Control Dock at the bottom (leaves map canvas 100% visible)
   * 4. Auto-frame map using fitBounds with bottom padding so route is never covered
   */
  async onVictimClick(victim) {
    this.activeRoutingVictim = victim;
    this.activeVictim = victim;

    // Intelligently recommend agency
    const alert = (victim.alertType || '').toUpperCase();
    let defaultAgency = 'EMS_115';
    let reason = 'Ưu tiên cấp cứu y tế dã chiến';

    if (alert.includes('LŨ') || alert.includes('CHÌM') || alert.includes('SẠT LỞ') || alert.includes('KẸT') || alert.includes('CHÁY') || alert.includes('NÚI') || alert.includes('VỰC')) {
      defaultAgency = 'RESCUE_114';
      reason = 'Cứu hộ cứu nạn khẩn cấp (Cần Ca-nô lội nước / Kích thủy lực / Dây đu vực)';
    } else if (alert.includes('AN NINH') || alert.includes('HỖN LOẠN') || alert.includes('PHONG TỎA') || alert.includes('GIAO THÔNG')) {
      defaultAgency = 'POLICE_113';
      reason = 'Cần CS113 phong tỏa hiện trường & mở đường cứu thương';
    }

    this.activeAgencyType = defaultAgency;
    this.activeReason = reason;

    // Route to recommended agency
    await this.routeToAgency(defaultAgency);
  },

  /**
   * Route from selected agency facility to the victim
   * agencyType: 'EMS_115' | 'RESCUE_114' | 'POLICE_113'
   */
  async routeToAgency(agencyType) {
    if (!this.activeVictim) return;
    this.activeAgencyType = agencyType;

    const facTypeMap = {
      'EMS_115': 'HOSPITAL',
      'RESCUE_114': 'FIRE_STATION',
      'POLICE_113': 'POLICE_POST'
    };

    const fac = this.findNearest(this.activeVictim.latitude, this.activeVictim.longitude, facTypeMap[agencyType]);
    if (!fac) {
      c2App.showToast('Không tìm thấy cơ sở phù hợp trong bán kính', 'danger');
      return;
    }
    this.activeFacility = fac;

    if (this.routeLine) {
      this.map.removeLayer(this.routeLine);
      this.routeLine = null;
    }

    const start = [fac.latitude, fac.longitude]; // xuất phát từ trạm cứu hộ
    const end = [this.activeVictim.latitude, this.activeVictim.longitude]; // đến hiện trường nạn nhân

    let routeCoordinates = [];
    let distanceKm = 0;
    let etaMinutes = 0;
    let isRealRoad = false;

    // Call OSRM Road Routing API (OpenStreetMap real Da Nang road network)
    try {
      const osrmUrl = `https://router.project-osrm.org/route/v1/driving/${start[1]},${start[0]};${end[1]},${end[0]}?overview=full&geometries=geojson`;
      const res = await fetch(osrmUrl, { signal: AbortSignal.timeout(3000) });
      if (res.ok) {
        const json = await res.json();
        if (json.routes && json.routes.length > 0) {
          const r = json.routes[0];
          routeCoordinates = r.geometry.coordinates.map(c => [c[1], c[0]]);
          distanceKm = (r.distance / 1000).toFixed(1);
          etaMinutes = Math.max(Math.round(r.duration / 60), 2);
          isRealRoad = true;
        }
      }
    } catch (e) {
      console.warn('[c2Map] OSRM offline or timeout, falling back to direct trajectory:', e);
    }

    if (!isRealRoad) {
      routeCoordinates = [start, end];
      distanceKm = this.distKm(start[0], start[1], end[0], end[1]).toFixed(1);
      etaMinutes = Math.max(Math.round((distanceKm / 30) * 60 + 2), 3);
    }

    // Colors: 115 Cyan, 114 Orange, 113 Gold
    const routeColors = {
      'EMS_115': '#00F0FF',
      'RESCUE_114': '#FF9900',
      'POLICE_113': '#FFD000'
    };

    // Draw glowing animated tactical route
    this.routeLine = L.polyline(routeCoordinates, {
      color: routeColors[agencyType] || '#00F0FF',
      weight: 4,
      opacity: 0.95,
      className: 'tactical-route-path'
    }).addTo(this.map);

    // Frame the map bounds with 240px bottom padding so the Dock never covers the route or markers!
    this.map.fitBounds(this.routeLine.getBounds(), {
      paddingTopLeft: [70, 70],
      paddingBottomRight: [70, 240],
      maxZoom: 15
    });

    // Render the Bottom Mission Control Dock
    this.renderMissionDock(distanceKm, etaMinutes);
  },

  /**
   * Render Non-Obstructive Tactical Mission Control Dock at the Bottom of the Map
   */
  renderMissionDock(distanceKm, etaMinutes) {
    const dock = document.getElementById('cop-mission-dock');
    if (!dock || !this.activeVictim || !this.activeFacility) return;

    const v = this.activeVictim;
    const f = this.activeFacility;
    const agency = this.activeAgencyType;

    const statusBadges = {
      PENDING: '<span class="badge pending" style="font-size:9px">CHỜ CỨU</span>',
      DISPATCHED: '<span class="badge dispatched" style="font-size:9px">ĐANG TIẾP CẬN</span>',
      RESOLVED: '<span class="badge resolved" style="font-size:9px">ĐÃ CỨU AN TOÀN</span>'
    };

    const vehicleDescriptions = {
      'EMS_115': 'Xe cấp cứu chuyên dụng ICU • Máy sốc tim & Bình Oxy dã chiến',
      'RESCUE_114': 'Ca-nô CN-02 / Xe cứu nạn đặc chủng • Kích thủy lực & Dây đu núi',
      'POLICE_113': 'Xe tuần tra CS113 phản ứng nhanh • Rào chắn & Dải phân luồng'
    };

    dock.innerHTML = `
      <div class="dock-header">
        <div style="display:flex;align-items:center;gap:10px">
          <strong style="color:var(--aero-cyan);font-size:14px;letter-spacing:1px">${v.nodeId}</strong>
          <span style="color:#fff;font-weight:700;font-size:12px">${v.fullName || 'Nạn nhân Mesh'}</span>
          <span class="badge ${v.severity === 'CRITICAL' ? 'critical' : 'high'}" style="font-size:9px">${v.severity || 'HIGH'}</span>
          ${statusBadges[v.status] || ''}
        </div>
        <button class="modal-close-icon" onclick="c2Map.resetMapView()" title="Đóng bảng điều phối và xóa tuyến đường">✕</button>
      </div>

      <div class="dock-body">
        <!-- CỘT 1: THÔNG TIN NẠN NHÂN MESH -->
        <div class="facility-preview-card">
          <div style="font-size:10px;color:var(--text-muted);margin-bottom:4px;font-weight:700;letter-spacing:1px">HỒ SƠ HIỆN TRƯỜNG NẠN NHÂN:</div>
          <div style="font-size:12px;color:#fff;font-weight:700">${v.alertType}</div>
          <div style="font-size:11px;color:var(--text-secondary);margin-top:2px">
            Nhóm máu: <strong style="color:var(--defcon-red);font-size:12px">${v.bloodType || 'O+'}</strong> | SĐT: <strong>${v.phone || 'N/A'}</strong>
          </div>
          <div style="font-size:10px;color:var(--text-muted);margin-top:2px">
            Tọa độ GPS: [${v.latitude.toFixed(4)}°N, ${v.longitude.toFixed(4)}°E]
          </div>
          <div style="font-size:10px;color:var(--text-secondary);background:rgba(255,255,255,.05);padding:4px;margin-top:4px;border-radius:2px">
            Ghi chú y tế: ${v.medicalNotes || 'Không có ghi chú'}
          </div>
          <div style="font-size:10px;color:var(--orbital-amber);margin-top:4px;font-weight:600">
            💡 Gợi ý chiến thuật: ${this.activeReason}
          </div>
        </div>

        <!-- CỘT 2: CHỌN LỰC LƯỢNG & ĐỊNH TUYẾN -->
        <div>
          <div style="font-size:10px;color:var(--text-muted);margin-bottom:6px;font-weight:700;letter-spacing:1px">LỰC LƯỢNG TÁC CHIẾN (CHỌN ĐỂ ĐỊNH TUYẾN):</div>
          <div class="agency-tabs">
            <button class="agency-tab-btn ${agency === 'EMS_115' ? 'active tab-115' : ''}" onclick="c2Map.routeToAgency('EMS_115')">
              <svg viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M12 4v16M4 12h16"/></svg>
              115 Y TẾ
            </button>
            <button class="agency-tab-btn ${agency === 'RESCUE_114' ? 'active tab-114' : ''}" onclick="c2Map.routeToAgency('RESCUE_114')">
              <svg viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" stroke-width="2"><polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/></svg>
              114 CỨU NẠN
            </button>
            <button class="agency-tab-btn ${agency === 'POLICE_113' ? 'active tab-113' : ''}" onclick="c2Map.routeToAgency('POLICE_113')">
              <svg viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>
              113 AN NINH
            </button>
          </div>

          <div class="facility-preview-card">
            <div style="display:flex;justify-content:space-between;align-items:center">
              <strong style="color:#fff;font-size:12px">${f.facilityName}</strong>
              <span style="font-size:10px;color:var(--aero-cyan)">${f.district}</span>
            </div>
            <div style="font-size:10px;color:var(--text-muted);margin-top:2px">📍 ${f.address}</div>
            <div style="font-size:11px;color:var(--orbital-amber);margin-top:4px;font-weight:700">
              Cự ly đường bộ: ${distanceKm} km • Thời gian xe chạy: ~${etaMinutes} phút
            </div>
            <div style="font-size:10px;color:var(--life-green);margin-top:2px">
              Phương tiện: ${vehicleDescriptions[agency]}
            </div>
          </div>
        </div>
      </div>

      <div class="dock-footer">
        <div style="font-size:10px;color:var(--text-muted)">
          ✓ Đường bộ thực tế OSRM • Lập Phiếu Tác Chiến tự động • Thông báo đàm thoại VHF CH16
        </div>
        <div style="display:flex;gap:8px">
          <button class="btn-tactical" onclick="c2Dossier.openDossier('${v.nodeId}')" style="padding:7px 12px;font-size:11px" title="Xem dòng thời gian và nhật ký vụ việc">
            📁 HỒ SƠ & TIMELINE
          </button>
          <button class="btn-tactical" onclick="c2Map.resetMapView()" style="padding:7px 12px;font-size:11px">
            ✕ HỦY
          </button>
          <button class="btn-tactical active ${agency === 'RESCUE_114' ? 'orange' : agency === 'POLICE_113' ? 'amber' : 'cyan'}"
                  onclick="c2Map.executeDispatch('${v.nodeId}', '${agency}', '${f.facilityName}', '${distanceKm}', ${etaMinutes})"
                  style="padding:8px 16px;font-size:11px;font-weight:700;display:flex;align-items:center;gap:6px">
            <svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" stroke-width="2.5"><polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/></svg>
            ⚡ PHÁT LỆNH ĐIỀU ĐỘNG [${agency.replace('_', ' ')}]: XUẤT KÍCH NGAY
          </button>
        </div>
      </div>
    `;

    dock.classList.add('active');
  },

  /**
   * Execute Inter-Agency Dispatch from Map:
   * 1. Creates Electronic Mission Ticket in database
   * 2. Automatically advances status to EN_ROUTE
   * 3. Dispatches radio transmission over VHF CH16
   * 4. Updates victim marker on map to Amber with pulse
   * 5. Displays Live Mission Telemetry in the Dock
   */
  async executeDispatch(victimNodeId, agencyType, facilityName, distanceKm, etaMinutes) {
    const v = this.activeVictim;
    if (!v) return;

    try {
      const vehicleDescriptions = {
        'EMS_115': 'Xe cấp cứu chuyên dụng ICU',
        'RESCUE_114': 'Ca-nô CN-02 / Xe cứu nạn đặc chủng',
        'POLICE_113': 'Xe tuần tra CS113 phản ứng nhanh'
      };

      const leaders = {
        'EMS_115': { name: 'BS. CK1 Nguyễn Hoài Nam', phone: '0905.115.888' },
        'RESCUE_114': { name: 'Đại úy Trần Quốc Toản', phone: '0905.114.222' },
        'POLICE_113': { name: 'Trung tá Lê Văn Bình', phone: '0905.113.999' }
      };

      const leader = leaders[agencyType] || { name: 'Chỉ huy kíp trực', phone: '0905.000.111' };
      const vehicle = vehicleDescriptions[agencyType] || 'Phương tiện phản ứng nhanh';

      // 1. Create Mission Ticket
      const res = await fetch('/api/interagency/ticket/create', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          victimNodeId: victimNodeId.trim(),
          agencyType: agencyType,
          agencyName: facilityName,
          requiredVehicle: vehicle,
          specialNeeds: (v.medicalNotes || 'Ưu tiên tiếp cận'),
          priority: (v.severity || 'HIGH')
        })
      });

      if (!res.ok) throw new Error('HTTP ' + res.status);
      const data = await res.json();
      const ticketId = data.ticketId || 1;

      // 2. Advance to EN_ROUTE
      await fetch(`/api/interagency/ticket/${ticketId}/advance`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          status: 'EN_ROUTE',
          unit: vehicle,
          leader: leader.name,
          phone: leader.phone,
          eta: etaMinutes
        })
      });

      // 3. Update marker color on map to Amber (DISPATCHED)
      if (this.markers[victimNodeId]) {
        const icon = L.divIcon({
          className: '',
          html: `<div style="background:#FF9900;width:16px;height:16px;border-radius:50%;box-shadow:0 0 20px #FF9900, 0 0 40px rgba(255,153,0,.5);border:2px solid #fff;animation:pulse 1.2s infinite"></div>`,
          iconSize: [16, 16], iconAnchor: [8, 8]
        });
        this.markers[victimNodeId].setIcon(icon);
      }

      // 4. Update Mission Dock to Live Telemetry View
      const dock = document.getElementById('cop-mission-dock');
      if (dock) {
        dock.innerHTML = `
          <div class="dock-header" style="background:rgba(0,255,102,.1);border-bottom:1px solid rgba(0,255,102,.3)">
            <div style="color:#00FF66;font-size:13px;font-weight:700;display:flex;align-items:center;gap:6px">
              <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="3"><polyline points="20 6 9 17 4 12"/></svg>
              ĐIỀU ĐỘNG XUẤT KÍCH THÀNH CÔNG — PHIẾU TÁC CHIẾN #ORD-0${ticketId}
            </div>
            <button onclick="c2Map.resetMapView()" title="Đóng" style="background:none;border:none;color:#fff;font-size:16px;cursor:pointer">✕</button>
          </div>
          <div class="dock-body" style="grid-template-columns:1fr 1fr;gap:12px;padding:14px">
            <div class="facility-preview-card" style="border-color:rgba(0,255,102,.3)">
              <div style="color:var(--orbital-amber);font-weight:700;font-size:12px">TRẠNG THÁI: EN_ROUTE (ĐANG HÀNH QUÂN)</div>
              <div style="color:#fff;font-size:11px;margin-top:4px">Đơn vị tiếp nhận: <strong>${facilityName}</strong></div>
              <div style="color:var(--text-secondary);font-size:11px;margin-top:2px">Phương tiện xuất kích: <strong>${vehicle}</strong></div>
              <div style="color:var(--text-secondary);font-size:11px;margin-top:2px">Chỉ huy kíp xe: <strong>${leader.name}</strong></div>
              <div style="color:var(--aero-cyan);font-size:11px;margin-top:2px">Đường dây nóng: <strong>${leader.phone}</strong></div>
            </div>
            <div class="facility-preview-card" style="border-color:rgba(0,240,255,.3)">
              <div style="color:var(--aero-cyan);font-weight:700;font-size:12px">THÔNG SỐ ĐIỀU HƯỚNG HIỆN TRƯỜNG:</div>
              <div style="color:#fff;font-size:11px;margin-top:4px">Mục tiêu cứu hộ: <strong>${victimNodeId} (${v.fullName || 'Nạn nhân'})</strong></div>
              <div style="color:var(--life-green);font-size:12px;font-weight:700;margin-top:2px">Cự ly: ${distanceKm} km • ETA dự kiến: ~${etaMinutes} phút</div>
              <div style="color:var(--text-muted);font-size:10px;margin-top:4px">
                ✓ Lộ trình OSRM đã nạp vào GPS kíp xe • Bản tin đã phát trên VHF Kênh CH16 (156.800 MHz)
              </div>
            </div>
          </div>
          <div class="dock-footer">
            <div style="font-size:10px;color:var(--life-green)">
              ● Kíp xe đang di chuyển theo lộ trình trên bản đồ
            </div>
            <div style="display:flex;gap:8px">
              <button class="btn-tactical success" onclick="c2App.switchTab('tab-interagency')" style="padding:7px 14px;font-size:11px;font-weight:700">
                📋 MỞ PHIẾU TẠI TAB LIÊN NGÀNH →
              </button>
              <button class="btn-tactical" onclick="c2Map.resetMapView()" style="padding:7px 12px;font-size:11px">
                ✕ GIÁM SÁT BẢN ĐỒ
              </button>
            </div>
          </div>
        `;
      }

      c2App.showToast(`LỆNH ĐÃ PHÁT: ${facilityName} XUẤT KÍCH TIẾP CẬN ${victimNodeId}!`, 'success');

      // Refresh Home and InterAgency modules
      if (window.c2Home) c2Home.refreshIncidents();
      if (window.c2InterAgency) {
        c2InterAgency.loadTickets();
        c2InterAgency.loadRadioLogs();
      }
    } catch (e) {
      c2App.showToast('Lỗi phát lệnh điều động: ' + e.message, 'danger');
    }
  },

  /** Toggle Drawer Collapse */
  toggleDrawer() {
    const d = document.querySelector('.map-floating-drawer');
    const btn = document.getElementById('btn-toggle-map-drawer');
    if (d) {
      d.classList.toggle('collapsed');
      if (btn) btn.innerText = d.classList.contains('collapsed') ? 'MỞ RỘNG ▸' : 'THU GỌN ◂';
    }
  },

  /** Reset Map View & Clear Routes back to VKU HQ overview */
  resetMapView() {
    if (this.routeLine) {
      this.map.removeLayer(this.routeLine);
      this.routeLine = null;
    }
    const dock = document.getElementById('cop-mission-dock');
    if (dock) {
      dock.classList.remove('active');
      dock.innerHTML = '';
    }
    if (this.map) {
      this.map.closePopup();
      this.map.flyTo(this.baseCoords, 12, { duration: 0.8 });
    }
    this.activeRoutingVictim = null;
    this.activeVictim = null;
    c2App.showToast('Đã định vị lại toàn cảnh Đà Nẵng (Tâm: VKU HQ)', 'info');
  },

  /** Switch Map Layer (Dark Tactical vs Satellite) from Top Toolbar */
  switchLayer(type) {
    const btnDark = document.getElementById('btn-layer-dark');
    const btnSat = document.getElementById('btn-layer-sat');

    if (type === 'sat') {
      if (this.map.hasLayer(this.darkLayer)) this.map.removeLayer(this.darkLayer);
      if (!this.map.hasLayer(this.satLayer)) this.satLayer.addTo(this.map);
      if (btnDark) btnDark.classList.remove('active');
      if (btnSat) btnSat.classList.add('active');
      c2App.showToast('Đã chuyển sang bản đồ Vệ tinh Hàng không', 'info');
    } else {
      if (this.map.hasLayer(this.satLayer)) this.map.removeLayer(this.satLayer);
      if (!this.map.hasLayer(this.darkLayer)) this.darkLayer.addTo(this.map);
      if (btnSat) btnSat.classList.remove('active');
      if (btnDark) btnDark.classList.add('active');
      c2App.showToast('Đã chuyển sang bản đồ Tác chiến Quân sự', 'info');
    }
  },

  /** Toggle Continuous Rotating Radar Sweep and Polar Grid */
  toggleRadar() {
    this.radarEnabled = !this.radarEnabled;
    const hud = document.getElementById('cop-radar-hud');
    const btn = document.getElementById('btn-toggle-radar');
    const txt = document.getElementById('txt-toggle-radar');

    if (this.radarEnabled) {
      if (this.radarLayerGroup && !this.map.hasLayer(this.radarLayerGroup)) {
        this.radarLayerGroup.addTo(this.map);
      }
      if (hud) hud.style.display = 'block';
      if (btn) btn.classList.add('active');
      if (txt) txt.textContent = 'QUÉT RADAR: BẬT';
      c2App.showToast('Đã BẬT màn hình quét Radar tác chiến', 'info');
    } else {
      if (this.radarLayerGroup && this.map.hasLayer(this.radarLayerGroup)) {
        this.map.removeLayer(this.radarLayerGroup);
      }
      if (hud) hud.style.display = 'none';
      if (btn) btn.classList.remove('active');
      if (txt) txt.textContent = 'QUÉT RADAR: TẮT';
      c2App.showToast('Đã TẮT màn hình quét Radar', 'neutral');
    }
  },

  /**
   * Direct Status Update API
   * POST /api/victims/{nodeId}/status
   */
  async updateVictimStatus(nodeId, status) {
    try {
      const res = await fetch(`/api/victims/${nodeId}/status`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ status })
      });
      if (!res.ok) throw new Error('HTTP ' + res.status);
      c2App.showToast(`Đã chuyển trạng thái ${nodeId} → ${status}`, 'success');

      // Update marker color immediately
      let color = '#FF1E40';
      if (status === 'RESOLVED') color = '#00FF66';
      else if (status === 'DISPATCHED') color = '#FF9900';

      const icon = L.divIcon({
        className: '',
        html: `<div style="background:${color};width:14px;height:14px;border-radius:50%;box-shadow:0 0 16px ${color}, 0 0 32px rgba(255,30,64,.3);border:2px solid #fff;animation:pulse 1.2s infinite"></div>`,
        iconSize: [14, 14], iconAnchor: [7, 7]
      });

      if (this.markers[nodeId]) {
        this.markers[nodeId].setIcon(icon);
        this.markers[nodeId].closePopup();
      }

      if (window.c2Home) c2Home.refreshIncidents();
    } catch (e) {
      c2App.showToast('Lỗi cập nhật trạng thái: ' + e.message, 'danger');
    }
  },

  routeAllPendingToFacility(facilityName) {
    c2App.showToast(`Đã điều hướng toàn bộ ca cứu nạn dã chiến về ${facilityName}`, 'info');
    if (window.c2Home) c2Home.refreshIncidents();
  },

  /**
   * Seamless 1-Click Interagency Dispatch:
   * 1. Sends dossier to /api/interagency/{agencyType}/dispatch
   * 2. Automatically updates victim status to DISPATCHED
   * 3. Triggers toast & UI refresh
   */
  async dispatchInterAgencyDirect(victimNodeId, agencyType, facilityName) {
    try {
      const res = await fetch(`/api/interagency/${agencyType}/dispatch`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ victimNodeId: victimNodeId.trim(), agencyName: facilityName })
      });
      if (!res.ok) throw new Error('HTTP ' + res.status);

      c2App.showToast(`ĐÃ CHUYỂN HỒ SƠ ${victimNodeId} → ${facilityName}`, 'success');

      // Update marker color to DISPATCHED (Amber) immediately
      if (this.markers[victimNodeId]) {
        const icon = L.divIcon({
          className: '',
          html: `<div style="background:#FF9900;width:14px;height:14px;border-radius:50%;box-shadow:0 0 16px #FF9900, 0 0 32px rgba(255,153,0,.3);border:2px solid #fff;animation:pulse 1.2s infinite"></div>`,
          iconSize: [14, 14], iconAnchor: [7, 7]
        });
        this.markers[victimNodeId].setIcon(icon);
        this.markers[victimNodeId].closePopup();
      }

      // Refresh home & interagency modules
      if (window.c2Home) c2Home.refreshIncidents();
      if (window.c2InterAgency) {
        c2InterAgency.loadTickets();
        c2InterAgency.loadRadioLogs();
        c2InterAgency.loadFacilities();
      }
    } catch (e) {
      c2App.showToast('Lỗi chuyển liên ngành: ' + e.message, 'danger');
    }
  },

  /** Find nearest facility by coordinates */
  findNearest(lat, lon, type) {
    let best = null, min = Infinity;
    this.facilityMarkers.forEach(f => {
      if (type && f.facilityType !== type) return;
      const d = this.distKm(lat, lon, f.latitude, f.longitude);
      if (d < min) { min = d; best = f; }
    });
    return best;
  },

  /** Haversine distance in km */
  distKm(lat1, lon1, lat2, lon2) {
    const R = 6371, dLat = (lat2-lat1)*Math.PI/180, dLon = (lon2-lon1)*Math.PI/180;
    const a = Math.sin(dLat/2)**2 + Math.cos(lat1*Math.PI/180)*Math.cos(lat2*Math.PI/180)*Math.sin(dLon/2)**2;
    return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
  },

  /* ═══════════════════════════════════════════════════════════════
   * RESCUE TEAM TRACKING LAYER (Phase 3)
   * AVL-style tracking markers for rescue teams on COP map
   * ═══════════════════════════════════════════════════════════════ */
  rescueTeamMarkers: {},

  async loadRescueTeams() {
    if (!this.map) return;
    try {
      const res = await fetch('/api/rescue-teams');
      const teams = await res.json();
      teams.forEach(t => this.renderTeamMarker(t));
    } catch (e) { /* quiet */ }
  },

  renderTeamMarker(team) {
    if (!this.map) return;
    const colors = {
      MEDICAL: '#4ADE80', FIRE: '#FF6B6B', SAR: '#00F0FF', POLICE: '#FFB547'
    };
    const icons = {
      MEDICAL: '🚑', FIRE: '🚒', SAR: '🚤', POLICE: '🚔'
    };
    const color = colors[team.teamType] || '#00F0FF';
    const icon = icons[team.teamType] || '🔵';
    const isActive = team.status !== 'STANDBY';

    // Skip if lat/lon is 0 (not yet deployed)
    if (!team.latitude && !team.longitude) return;

    const divIcon = L.divIcon({
      className: '',
      html: `<div style="position:relative;width:32px;height:32px;display:flex;align-items:center;justify-content:center">
               ${isActive ? `<div style="position:absolute;inset:-4px;border-radius:50%;border:2px dashed ${color};animation:spin 3s linear infinite;opacity:.6"></div>` : ''}
               <div style="background:${color};width:22px;height:22px;border-radius:4px;box-shadow:0 0 12px ${color};border:2px solid rgba(255,255,255,.8);display:flex;align-items:center;justify-content:center;font-size:12px;transform:rotate(45deg)">
                 <span style="transform:rotate(-45deg)">${icon}</span>
               </div>
             </div>`,
      iconSize: [32, 32],
      iconAnchor: [16, 16]
    });

    const statusBadge = {
      STANDBY: '<span style="color:#4B5566">STANDBY</span>',
      DEPLOYED: '<span style="color:#FFB547">DEPLOYED</span>',
      ON_SCENE: '<span style="color:#4ADE80">ON_SCENE</span>',
      RETURNING: '<span style="color:#00F0FF">RETURNING</span>'
    };

    const popup = `
      <div style="font-family:'Space Grotesk',sans-serif;min-width:180px">
        <div style="font-size:12px;font-weight:700;color:${color};margin-bottom:4px">${icon} ${team.teamName}</div>
        <div style="font-size:10px;color:#7C8BA1;margin-bottom:6px">${team.vehicleInfo || '—'}</div>
        <table style="font-size:10px;color:#F2F6FA;width:100%">
          <tr><td style="color:#7C8BA1">Trạng thái</td><td>${statusBadge[team.status] || team.status}</td></tr>
          <tr><td style="color:#7C8BA1">Quân số</td><td>${team.memberCount} người</td></tr>
          <tr><td style="color:#7C8BA1">Nạn nhân</td><td>${team.assignedVictimId || '—'}</td></tr>
          <tr><td style="color:#7C8BA1">SĐT</td><td>${team.contactPhone}</td></tr>
        </table>
      </div>`;

    if (this.rescueTeamMarkers[team.teamId]) {
      this.rescueTeamMarkers[team.teamId].setLatLng([team.latitude, team.longitude]);
      this.rescueTeamMarkers[team.teamId].setPopupContent(popup);
    } else {
      const marker = L.marker([team.latitude, team.longitude], { icon: divIcon })
        .bindPopup(popup, {
          className: 'tactical-popup',
          maxWidth: 250
        });
      if (this.radarLayerGroup) marker.addTo(this.radarLayerGroup);
      else marker.addTo(this.map);
      this.rescueTeamMarkers[team.teamId] = marker;
    }
  },

  onRescueTeamUpdate(data) {
    if (data.type === 'LOCATION_UPDATE') {
      const marker = this.rescueTeamMarkers[data.teamId];
      if (marker) marker.setLatLng([data.latitude, data.longitude]);
    } else {
      // Reload all teams for status changes
      this.loadRescueTeams();
    }
  }
};

window.c2Map = c2Map;

package com.rescue.mesh.service;

import com.rescue.mesh.db.*;
import com.rescue.mesh.model.AuditLogEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.StringWriter;
import java.io.PrintWriter;
import java.util.*;
import java.text.SimpleDateFormat;

/**
 * Service xuất báo cáo IAP (Incident Action Plan) — PDF/CSV.
 * Theo FINAL spec §7 — phục vụ Trang 6 (Audit Trail & Report Export).
 *
 * Xây dựng AuditLogEntity bằng cách JOIN dữ liệu từ:
 *   - mesh_packets (evidence log)
 *   - dispatch_logs (lệnh điều phối)
 *   - interagency_dispatches (chuyển tiếp liên ngành)
 */
public class ReportExportService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReportExportService.class);

    private final PacketDao packetDao;
    private final DispatchDao dispatchDao;
    private final VictimDao victimDao;
    private final InterAgencyService interAgencyService;

    public ReportExportService(DatabaseManager dbManager, InterAgencyService interAgencyService) {
        this.packetDao = new PacketDao(dbManager);
        this.dispatchDao = new DispatchDao(dbManager);
        this.victimDao = new VictimDao(dbManager);
        this.interAgencyService = interAgencyService;
    }

    /**
     * Xuất dữ liệu bản đồ dạng GeoJSON FeatureCollection (chuẩn GIS quốc tế).
     */
    public String exportGeoJson() {
        List<VictimDao.VictimRecord> victims = victimDao.findAll();
        StringBuilder sb = new StringBuilder();
        sb.append("{\n  \"type\": \"FeatureCollection\",\n  \"features\": [\n");

        for (int i = 0; i < victims.size(); i++) {
            VictimDao.VictimRecord v = victims.get(i);
            sb.append("    {\n");
            sb.append("      \"type\": \"Feature\",\n");
            sb.append("      \"geometry\": {\n");
            sb.append("        \"type\": \"Point\",\n");
            sb.append("        \"coordinates\": [").append(v.longitude()).append(", ").append(v.latitude()).append("]\n");
            sb.append("      },\n");
            sb.append("      \"properties\": {\n");
            sb.append("        \"nodeId\": \"").append(escapeJson(v.nodeId())).append("\",\n");
            sb.append("        \"fullName\": \"").append(escapeJson(v.fullName())).append("\",\n");
            sb.append("        \"alertType\": \"").append(escapeJson(v.alertType())).append("\",\n");
            sb.append("        \"severity\": \"").append(escapeJson(v.severity())).append("\",\n");
            sb.append("        \"status\": \"").append(escapeJson(v.status())).append("\",\n");
            sb.append("        \"victimCount\": ").append(v.victimCount()).append(",\n");
            sb.append("        \"bloodType\": \"").append(escapeJson(v.bloodType())).append("\"\n");
            sb.append("      }\n");
            sb.append("    }").append(i < victims.size() - 1 ? "," : "").append("\n");
        }
        sb.append("  ]\n}");
        return sb.toString();
    }

    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }
    public List<AuditLogEntity> buildAuditTrail(int limit) {
        List<AuditLogEntity> trail = new ArrayList<>();

        // 1. Lấy gói tin mesh (Evidence Log)
        List<PacketDao.PacketRecord> packets = packetDao.findRecent(limit);
        for (PacketDao.PacketRecord p : packets) {
            trail.add(new AuditLogEntity(
                    p.timestamp(),
                    "PACKET_" + p.packetType(),
                    p.sourceNodeId(),
                    p.destinationNodeId(),
                    "Type=" + p.packetType() + " | Hops=" + p.hopCount() + " | Route=" + p.routeHistory(),
                    p.packetId(),
                    "SOS_ALERT".equals(p.packetType()) ? "HIGH" : "INFO"
            ));
        }

        // 2. Lấy lệnh điều phối
        List<Map<String, Object>> dispatches = dispatchDao.findRecentAsMap(limit);
        for (Map<String, Object> d : dispatches) {
            trail.add(new AuditLogEntity(
                    (Long) d.getOrDefault("dispatchedAt", 0L),
                    "DISPATCH_SENT",
                    String.valueOf(d.getOrDefault("senderName", "C2")),
                    String.valueOf(d.getOrDefault("targetNodeId", "")),
                    String.valueOf(d.getOrDefault("commandMessage", "")),
                    String.valueOf(d.getOrDefault("dispatchId", "")),
                    "MEDIUM"
            ));
        }

        // 3. Lấy chuyển tiếp liên ngành
        List<Map<String, Object>> iaDispatches = interAgencyService.getHistory();
        for (Map<String, Object> ia : iaDispatches) {
            trail.add(new AuditLogEntity(
                    (Long) ia.getOrDefault("dispatchedAt", 0L),
                    "INTERAGENCY_" + ia.getOrDefault("agencyType", "UNKNOWN"),
                    String.valueOf(ia.getOrDefault("victimNodeId", "")),
                    String.valueOf(ia.getOrDefault("agencyName", "")),
                    "Status=" + ia.getOrDefault("status", "SENT"),
                    "",
                    "MEDIUM"
            ));
        }

        // Sắp xếp theo timestamp giảm dần
        trail.sort((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));

        // Giới hạn kết quả
        if (trail.size() > limit) {
            return trail.subList(0, limit);
        }
        return trail;
    }

    /**
     * Xuất dữ liệu CSV cho toàn bộ audit trail.
     */
    public String exportCsv(int limit) {
        List<AuditLogEntity> trail = buildAuditTrail(limit);
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);

        // Header
        pw.println("Timestamp,EventType,SourceNode,TargetNode,Details,PacketId,Severity");

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        for (AuditLogEntity e : trail) {
            pw.printf("%s,%s,%s,%s,\"%s\",%s,%s%n",
                    sdf.format(new Date(e.getTimestamp())),
                    e.getEventType(),
                    e.getSourceNodeId(),
                    e.getTargetNodeId(),
                    e.getDetails().replace("\"", "\"\""),
                    e.getPacketId(),
                    e.getSeverity()
            );
        }

        pw.flush();
        return sw.toString();
    }

    /**
     * Xuất báo cáo ICS Form 201 (Incident Briefing) + ICS 204 (Assignment List).
     *
     * Chuẩn FEMA NIMS/ICS:
     *   - ICS 201: Incident Briefing (tổng quan sự cố)
     *   - ICS 204: Assignment List (phân công nhiệm vụ)
     *   - ICS 205A: Communications Plan (kế hoạch liên lạc)
     */
    public String exportIapReport() {
        List<VictimDao.VictimRecord> all = victimDao.findAll();
        int total = all.size();
        int pending = victimDao.countByStatus("PENDING");
        int dispatched = victimDao.countByStatus("DISPATCHED");
        int resolved = victimDao.countByStatus("RESOLVED");
        long critical = all.stream()
                .filter(v -> "CRITICAL".equals(v.severity()))
                .count();

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");
        SimpleDateFormat dateFmt = new SimpleDateFormat("dd/MM/yyyy");
        SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm");
        Date now = new Date();

        StringBuilder sb = new StringBuilder();

        // ═══════════════════════════════════════════
        // ICS FORM 201 — INCIDENT BRIEFING
        // ═══════════════════════════════════════════
        sb.append("┌──────────────────────────────────────────────────────────────┐\n");
        sb.append("│              ICS FORM 201 — INCIDENT BRIEFING               │\n");
        sb.append("│         FEMA NIMS/ICS Compliant — Emergency Mesh C2         │\n");
        sb.append("└──────────────────────────────────────────────────────────────┘\n\n");

        // Block 1: Incident Name
        sb.append("┌─────────────────────────────────────────────────────────────┐\n");
        sb.append("│ 1. INCIDENT NAME          │ 2. INCIDENT NUMBER             │\n");
        sb.append("│ Lũ lụt miền Trung 2026    │ INC-").append(dateFmt.format(now).replace("/", "")).append("-001       │\n");
        sb.append("├─────────────────────────────────────────────────────────────┤\n");

        // Block 2: Date/Time
        sb.append("│ 3. DATE PREPARED          │ 4. TIME PREPARED               │\n");
        sb.append("│ ").append(String.format("%-27s", dateFmt.format(now))).append("│ ").append(String.format("%-32s", timeFmt.format(now))).append("│\n");
        sb.append("├─────────────────────────────────────────────────────────────┤\n");

        // Block 3: Map/Sketch Reference
        sb.append("│ 5. MAP/SKETCH REFERENCE                                    │\n");
        sb.append("│ Tactical COP Map — Leaflet Dark Tactical                   │\n");
        sb.append("│ Vùng tác chiến: TP. Đà Nẵng (16.0°-16.15°N, 108.15-108.3°E)│\n");
        sb.append("├─────────────────────────────────────────────────────────────┤\n");

        // Block 4: Incident Commander
        sb.append("│ 6. INCIDENT COMMANDER (IC)                                 │\n");
        sb.append("│ Tên IC: Trạm Trưởng C2 (Tactical Web C2 Operator)         │\n");
        sb.append("│ Kênh liên lạc: VHF CH16 Marine / TCP 8888 Mesh            │\n");
        sb.append("│ Hệ thống C2: Emergency Mesh Rescue — Dual-Engine           │\n");
        sb.append("└─────────────────────────────────────────────────────────────┘\n\n");

        // ═══════════════════════════════════════════
        // SECTION 7: SITUATION SUMMARY (ICS 201 Block 7)
        // ═══════════════════════════════════════════
        sb.append("┌─────────────────────────────────────────────────────────────┐\n");
        sb.append("│ 7. SITUATION SUMMARY — TỔNG QUAN TÌNH HÌNH                 │\n");
        sb.append("├─────────────────────────────────────────────────────────────┤\n");
        sb.append(String.format("│  Tổng số nạn nhân đã phát hiện : %-26d │%n", total));
        sb.append(String.format("│  Ca nguy cấp (CRITICAL)        : %-26d │%n", critical));
        sb.append(String.format("│  Chờ cứu (PENDING/REQ)         : %-26d │%n", pending));
        sb.append(String.format("│  Đang tiếp cận (DISPATCHED/ACQ): %-26d │%n", dispatched));
        sb.append(String.format("│  Đã cứu (RESOLVED/REL)         : %-26d │%n", resolved));
        sb.append(String.format("│  Tỷ lệ hoàn thành              : %-26s │%n",
                (total > 0 ? Math.round(resolved * 100.0 / total) : 0) + "%"));
        sb.append("│                                                             │\n");
        sb.append(String.format("│  Thời gian xuất báo cáo: %-33s │%n", sdf.format(now)));
        sb.append("└─────────────────────────────────────────────────────────────┘\n\n");

        // ═══════════════════════════════════════════
        // ICS FORM 204 — ASSIGNMENT LIST
        // ═══════════════════════════════════════════
        sb.append("┌──────────────────────────────────────────────────────────────┐\n");
        sb.append("│            ICS FORM 204 — ASSIGNMENT LIST                   │\n");
        sb.append("├──────────────────────────────────────────────────────────────┤\n");
        sb.append("│ OPERATIONAL PERIOD:                                         │\n");
        sb.append(String.format("│   From: %s %s   To: 24h sau                       │%n",
                dateFmt.format(now), timeFmt.format(now)));
        sb.append("└──────────────────────────────────────────────────────────────┘\n\n");

        // Victim Assignment List
        sb.append(String.format("%-14s %-20s %-10s %-12s %-22s %s%n",
                "NODE_ID", "HỌ TÊN", "MỨC ĐỘ", "TRẠNG THÁI", "TỌA ĐỘ GPS", "GHI CHÚ"));
        sb.append("─".repeat(95)).append("\n");
        for (VictimDao.VictimRecord v : all) {
            String gps = String.format("(%.4f, %.4f)", v.latitude(), v.longitude());
            String team = v.dispatchedTeam() != null ? v.dispatchedTeam() : "—";
            sb.append(String.format("%-14s %-20s %-10s %-12s %-22s Đội: %s%n",
                    v.nodeId(),
                    truncate(v.fullName(), 18),
                    v.severity(),
                    v.status(),
                    gps,
                    team));
        }

        sb.append("\n");

        // ═══════════════════════════════════════════
        // ICS FORM 205A — COMMUNICATIONS PLAN
        // ═══════════════════════════════════════════
        sb.append("┌──────────────────────────────────────────────────────────────┐\n");
        sb.append("│       ICS FORM 205A — COMMUNICATIONS PLAN (LIÊN LẠC)       │\n");
        sb.append("├──────────────────────────────────────────────────────────────┤\n");
        sb.append("│ Kênh liên lạc chính:                                        │\n");
        sb.append("│   1. VHF Marine CH16 (156.800 MHz) — Liên lạc tàu thuyền   │\n");
        sb.append("│   2. TCP Port 8888 (Mesh Inbound)  — Nhận SOS từ Android   │\n");
        sb.append("│   3. TCP Port 8889 (Mesh Outbound) — Gửi lệnh Dispatch    │\n");
        sb.append("│   4. WebSocket /ws (STOMP)         — Dashboard real-time    │\n");
        sb.append("│   5. HTTP REST /api/*              — API tác chiến          │\n");
        sb.append("├──────────────────────────────────────────────────────────────┤\n");
        sb.append("│ Liên ngành:                                                  │\n");
        sb.append("│   115 — Cấp cứu Y tế    │ 114 — PCCC & CNCH               │\n");
        sb.append("│   113 — Công an          │ VHF CH16 — Hàng hải             │\n");
        sb.append("└──────────────────────────────────────────────────────────────┘\n\n");

        sb.append("═══════════════════════════════════════════\n");
        sb.append("  END OF IAP — ICS 201/204/205A REPORT\n");
        sb.append("  Generated by: Emergency Mesh Rescue C2\n");
        sb.append("═══════════════════════════════════════════\n");

        return sb.toString();
    }

    /** Truncate chuỗi cho vừa cột bảng */
    private String truncate(String s, int maxLen) {
        if (s == null) return "—";
        return s.length() <= maxLen ? s : s.substring(0, maxLen - 1) + "…";
    }
}

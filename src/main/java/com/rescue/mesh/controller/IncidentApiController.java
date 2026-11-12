package com.rescue.mesh.controller;

import com.rescue.mesh.db.VictimDao;
import com.rescue.mesh.model.UserEntity;
import com.rescue.mesh.service.C2ControllerService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * REST API cung cấp dữ liệu sự cố cứu nạn cho Web Dashboard.
 * Tích hợp RBAC Data Masking: Tự động che mờ SĐT và thông tin y tế đối với vai trò OBSERVER/Khách.
 */
@RestController
@RequestMapping("/api/victims")
public class IncidentApiController {

    private final C2ControllerService c2Service;
    private final SimpMessagingTemplate messagingTemplate;

    public IncidentApiController(C2ControllerService c2Service,
                                  SimpMessagingTemplate messagingTemplate) {
        this.c2Service = c2Service;
        this.messagingTemplate = messagingTemplate;
    }

    private boolean isOfficer(HttpServletRequest request) {
        if (request == null) return false;
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute("user") != null) {
            UserEntity user = (UserEntity) session.getAttribute("user");
            String role = user.getRole();
            return "ADMIN".equals(role) || "COMMANDER".equals(role) || "DISPATCHER".equals(role);
        }
        return false;
    }

    private VictimDao.VictimRecord maskRecord(VictimDao.VictimRecord r, boolean officer) {
        if (r == null || officer) return r;
        String maskedPhone = r.phone() != null && r.phone().length() > 4
                ? r.phone().substring(0, 3) + "****" + r.phone().substring(r.phone().length() - 2)
                : "09******XX";
        return new VictimDao.VictimRecord(
                r.nodeId(),
                r.fullName(),
                maskedPhone,
                "***",
                "[BẢO MẬT — CẦN QUYỀN CHỈ HUY]",
                r.victimCount(),
                r.alertType(),
                r.severity(),
                r.latitude(),
                r.longitude(),
                r.hasFix(),
                r.accuracyM(),
                r.fixAgeMs(),
                r.status(),
                r.dispatchedTeam(),
                r.firstSeen(),
                r.lastUpdated()
        );
    }

    /** Danh sách tất cả nạn nhân — filter tùy chọn theo status kèm RBAC masking. */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public List<VictimDao.VictimRecord> listVictims(
            @RequestParam(required = false) String status,
            HttpServletRequest request) {
        boolean officer = isOfficer(request);
        List<VictimDao.VictimRecord> list = (status != null && !status.isBlank())
                ? c2Service.getVictimDao().findByStatus(status.toUpperCase())
                : c2Service.getVictimDao().findAll();

        return list.stream().map(r -> maskRecord(r, officer)).toList();
    }

    /** Tìm kiếm & Tra cứu hồ sơ nạn nhân nâng cao (theo từ khóa, nhóm máu, trạng thái). */
    @GetMapping(value = "/search", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<VictimDao.VictimRecord> searchVictims(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String bloodType,
            @RequestParam(required = false) String status,
            HttpServletRequest request) {
        boolean officer = isOfficer(request);
        List<VictimDao.VictimRecord> list = c2Service.getVictimDao().search(q, bloodType, status);
        return list.stream().map(r -> maskRecord(r, officer)).toList();
    }

    /** Thống kê KPI cho Trang Chủ — Avionics Cards. */
    @GetMapping(value = "/stats", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> getStats() {
        VictimDao dao = c2Service.getVictimDao();
        List<VictimDao.VictimRecord> all = dao.findAll();
        int total = all.size();
        int pending = dao.countByStatus("PENDING");
        int dispatched = dao.countByStatus("DISPATCHED");
        int resolved = dao.countByStatus("RESOLVED");
        long critical = all.stream()
                .filter(v -> "CRITICAL".equals(v.severity()) && "PENDING".equals(v.status()))
                .count();

        return Map.of(
                "total", total,
                "critical", critical,
                "pending", pending,
                "dispatched", dispatched,
                "resolved", resolved,
                "resolveRate", total > 0 ? Math.round(resolved * 100.0 / total) : 0
        );
    }

    /** Top 5 ca ưu tiên cao nhất (PENDING + CRITICAL/HIGH trước). */
    @GetMapping(value = "/priority", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<VictimDao.VictimRecord> getPriority(HttpServletRequest request) {
        boolean officer = isOfficer(request);
        List<VictimDao.VictimRecord> pending = new ArrayList<>(c2Service.getVictimDao().findByStatus("PENDING"));
        // Sort: CRITICAL > HIGH > MEDIUM, then by time
        pending.sort((a, b) -> {
            int sa = severityRank(a.severity());
            int sb = severityRank(b.severity());
            if (sa != sb) return sa - sb;
            return Long.compare(a.firstSeen(), b.firstSeen());
        });
        List<VictimDao.VictimRecord> sub = pending.size() > 5 ? pending.subList(0, 5) : pending;
        return sub.stream().map(r -> maskRecord(r, officer)).toList();
    }

    /** Chi tiết 1 ca theo nodeId. */
    @GetMapping(value = "/{nodeId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public VictimDao.VictimRecord getById(@PathVariable String nodeId, HttpServletRequest request) {
        boolean officer = isOfficer(request);
        return maskRecord(c2Service.getVictimDao().findById(nodeId), officer);
    }

    /**
     * Đánh dấu hoàn tất cứu nạn — chuyển trạng thái sang RESOLVED.
     * FINAL spec: POST /api/victims/{nodeId}/resolve
     */
    @PostMapping(value = "/{nodeId}/resolve", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> resolve(@PathVariable String nodeId) {
        c2Service.resolveIncident(nodeId);

        // Broadcast qua STOMP
        messagingTemplate.convertAndSend("/topic/victims", Map.of(
                "type", "RESOLVED",
                "targetNodeId", nodeId,
                "timestamp", System.currentTimeMillis()
        ));

        return ResponseEntity.ok(Map.of(
                "status", "RESOLVED",
                "nodeId", nodeId
        ));
    }

    /**
     * Cập nhật trạng thái cứu nạn bất kỳ: PENDING, DISPATCHED, RESOLVED.
     * POST /api/victims/{nodeId}/status
     */
    @PostMapping(value = "/{nodeId}/status", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> updateStatus(
            @PathVariable String nodeId,
            @RequestBody Map<String, String> body) {
        String newStatus = body.getOrDefault("status", "PENDING").toUpperCase();
        if (!Set.of("PENDING", "DISPATCHED", "RESOLVED").contains(newStatus)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Trạng thái không hợp lệ: " + newStatus));
        }

        if ("RESOLVED".equals(newStatus)) {
            c2Service.resolveIncident(nodeId);
        } else if ("DISPATCHED".equals(newStatus)) {
            c2Service.dispatchRescue(nodeId, "Cập nhật điều phối tác chiến", "Trạm trưởng C2");
        } else {
            c2Service.getVictimDao().updateStatus(nodeId, "PENDING");
        }

        messagingTemplate.convertAndSend("/topic/victims", Map.of(
                "type", "STATUS_CHANGE",
                "targetNodeId", nodeId,
                "status", newStatus,
                "timestamp", System.currentTimeMillis()
        ));

        return ResponseEntity.ok(Map.of(
                "status", newStatus,
                "nodeId", nodeId
        ));
    }

    private int severityRank(String severity) {
        return switch (severity) {
            case "CRITICAL" -> 0;
            case "HIGH" -> 1;
            case "MEDIUM" -> 2;
            default -> 3;
        };
    }
}

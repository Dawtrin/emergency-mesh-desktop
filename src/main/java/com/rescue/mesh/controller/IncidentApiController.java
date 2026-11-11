package com.rescue.mesh.controller;

import com.rescue.mesh.db.VictimDao;
import com.rescue.mesh.service.C2ControllerService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * REST API cung cấp dữ liệu sự cố cứu nạn cho Web Dashboard.
 *
 * Endpoints (theo FINAL spec §6.1):
 *   GET  /api/victims             — Danh sách tất cả nạn nhân (filter by status)
 *   GET  /api/victims/stats       — Thống kê KPI (total, critical, pending, dispatched, resolved)
 *   GET  /api/victims/priority    — Top 5 ca nguy hiểm nhất
 *   GET  /api/victims/{nodeId}    — Chi tiết 1 ca
 *   POST /api/victims/{nodeId}/resolve — Đánh dấu hoàn tất cứu nạn
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

    /** Danh sách tất cả nạn nhân — filter tùy chọn theo status. */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public List<VictimDao.VictimRecord> listVictims(
            @RequestParam(required = false) String status) {
        if (status != null && !status.isBlank()) {
            return c2Service.getVictimDao().findByStatus(status.toUpperCase());
        }
        return c2Service.getVictimDao().findAll();
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
    public List<VictimDao.VictimRecord> getPriority() {
        List<VictimDao.VictimRecord> pending = new ArrayList<>(c2Service.getVictimDao().findByStatus("PENDING"));
        // Sort: CRITICAL > HIGH > MEDIUM, then by time
        pending.sort((a, b) -> {
            int sa = severityRank(a.severity());
            int sb = severityRank(b.severity());
            if (sa != sb) return sa - sb;
            return Long.compare(a.firstSeen(), b.firstSeen());
        });
        return pending.size() > 5 ? pending.subList(0, 5) : pending;
    }

    /** Chi tiết 1 ca theo nodeId. */
    @GetMapping(value = "/{nodeId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public VictimDao.VictimRecord getById(@PathVariable String nodeId) {
        return c2Service.getVictimDao().findById(nodeId);
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

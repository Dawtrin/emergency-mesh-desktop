package com.rescue.mesh.controller;

import com.rescue.mesh.db.DatabaseManager;
import com.rescue.mesh.db.IncidentEventDao;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/incidents")
public class IncidentDossierApiController {

    private final DatabaseManager databaseManager;

    public IncidentDossierApiController(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    /**
     * Lấy toàn bộ dòng thời gian sự kiện (Dossier Timeline) của 1 nạn nhân.
     */
    @GetMapping("/{nodeId}/timeline")
    public List<IncidentEventDao.EventRecord> getTimeline(@PathVariable String nodeId) {
        return databaseManager.getIncidentEventDao().findByVictim(nodeId);
    }

    /**
     * Cán bộ tiếp nhận ca cứu nạn (X1 - Operator Acknowledge).
     */
    @PostMapping("/{nodeId}/operator-ack")
    public ResponseEntity<?> operatorAck(@PathVariable String nodeId, @RequestBody(required = false) Map<String, String> body) {
        String actor = body != null ? body.getOrDefault("actor", "Điều phối viên C2") : "Điều phối viên C2";
        databaseManager.getIncidentEventDao().record(
                nodeId, 1, "OPERATOR_ACK", actor,
                "Điều phối viên đã tiếp nhận hồ sơ và bắt đầu xử lý", null, null, false
        );
        return ResponseEntity.ok(Map.of("status", "ACKNOWLEDGED", "nodeId", nodeId));
    }

    /**
     * Xác minh hoặc báo tin giả (X3 - Verify / False Alarm).
     */
    @PostMapping("/{nodeId}/verify")
    public ResponseEntity<?> verifyIncident(@PathVariable String nodeId, @RequestBody Map<String, Object> body) {
        boolean isVerified = Boolean.TRUE.equals(body.get("verified"));
        String actor = (String) body.getOrDefault("actor", "Chỉ huy C2");
        String note = (String) body.getOrDefault("note", isVerified ? "Đã xác minh tin SOS chính xác" : "Báo động giả / Nhầm lẫn");

        String eventType = isVerified ? "VERIFIED" : "FALSE_ALARM";
        databaseManager.getIncidentEventDao().record(
                nodeId, 1, eventType, actor, note, null, null, false
        );

        return ResponseEntity.ok(Map.of("status", eventType, "nodeId", nodeId));
    }
}

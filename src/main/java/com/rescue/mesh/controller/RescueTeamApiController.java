package com.rescue.mesh.controller;

import com.rescue.mesh.db.RescueTeamDao;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST API quản lý Đội Cứu Hộ (Rescue Team Tracking — Phase 3).
 *
 * Endpoints:
 *   GET  /api/rescue-teams              — Lấy toàn bộ đội
 *   GET  /api/rescue-teams/active       — Lấy đội đang hoạt động
 *   POST /api/rescue-teams/{teamId}/deploy     — Triển khai đội đến nạn nhân
 *   POST /api/rescue-teams/{teamId}/location   — Cập nhật GPS
 *   POST /api/rescue-teams/{teamId}/status     — Cập nhật trạng thái
 */
@RestController
@RequestMapping("/api/rescue-teams")
public class RescueTeamApiController {

    private final RescueTeamDao rescueTeamDao;
    private final SimpMessagingTemplate messagingTemplate;

    public RescueTeamApiController(RescueTeamDao rescueTeamDao,
                                    SimpMessagingTemplate messagingTemplate) {
        this.rescueTeamDao = rescueTeamDao;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Lấy toàn bộ đội cứu hộ.
     */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public List<RescueTeamDao.RescueTeamRecord> getAllTeams() {
        return rescueTeamDao.findAll();
    }

    /**
     * Lấy đội đang hoạt động (không STANDBY).
     */
    @GetMapping(value = "/active", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<RescueTeamDao.RescueTeamRecord> getActiveTeams() {
        return rescueTeamDao.findActive();
    }

    /**
     * Triển khai đội đến nạn nhân cụ thể.
     * POST /api/rescue-teams/{teamId}/deploy
     * Body: { "victimNodeId": "DN_HOAVANG_01" }
     */
    @PostMapping(value = "/{teamId}/deploy",
                 consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> deployTeam(
            @PathVariable String teamId,
            @RequestBody Map<String, String> body) {
        String victimNodeId = body.get("victimNodeId");
        rescueTeamDao.updateStatus(teamId, "DEPLOYED", victimNodeId);

        // Broadcast real-time update
        messagingTemplate.convertAndSend("/topic/rescue-teams", Map.of(
                "type", "DEPLOYED",
                "teamId", teamId,
                "victimNodeId", victimNodeId,
                "timestamp", System.currentTimeMillis()
        ));

        return ResponseEntity.ok(Map.of(
                "status", "DEPLOYED",
                "teamId", teamId,
                "victimNodeId", victimNodeId,
                "message", "Đã triển khai " + teamId + " đến " + victimNodeId
        ));
    }

    /**
     * Cập nhật GPS vị trí đội cứu hộ real-time.
     * POST /api/rescue-teams/{teamId}/location
     * Body: { "latitude": 16.0042, "longitude": 108.1528 }
     */
    @PostMapping(value = "/{teamId}/location",
                 consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> updateLocation(
            @PathVariable String teamId,
            @RequestBody Map<String, Object> body) {
        double lat = ((Number) body.get("latitude")).doubleValue();
        double lon = ((Number) body.get("longitude")).doubleValue();
        rescueTeamDao.updateLocation(teamId, lat, lon);

        // Broadcast GPS update
        messagingTemplate.convertAndSend("/topic/rescue-teams", Map.of(
                "type", "LOCATION_UPDATE",
                "teamId", teamId,
                "latitude", lat,
                "longitude", lon,
                "timestamp", System.currentTimeMillis()
        ));

        return ResponseEntity.ok(Map.of("status", "OK", "teamId", teamId));
    }

    /**
     * Cập nhật trạng thái đội cứu hộ.
     * POST /api/rescue-teams/{teamId}/status
     * Body: { "status": "ON_SCENE" } hoặc { "status": "RETURNING" }
     */
    @PostMapping(value = "/{teamId}/status",
                 consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> updateStatus(
            @PathVariable String teamId,
            @RequestBody Map<String, String> body) {
        String status = body.get("status");
        String victimId = body.getOrDefault("victimNodeId", null);

        // Nếu RETURNING hoặc STANDBY → gỡ gán nạn nhân
        if ("RETURNING".equals(status) || "STANDBY".equals(status)) {
            victimId = null;
        }

        rescueTeamDao.updateStatus(teamId, status, victimId);

        messagingTemplate.convertAndSend("/topic/rescue-teams", Map.of(
                "type", "STATUS_UPDATE",
                "teamId", teamId,
                "status", status,
                "timestamp", System.currentTimeMillis()
        ));

        return ResponseEntity.ok(Map.of(
                "status", status,
                "teamId", teamId,
                "message", "Đội " + teamId + " → " + status
        ));
    }
}

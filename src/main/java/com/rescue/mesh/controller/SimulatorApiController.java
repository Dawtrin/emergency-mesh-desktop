package com.rescue.mesh.controller;

import com.rescue.mesh.service.ScenarioSimulatorService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import com.rescue.mesh.db.DatabaseManager;
import org.springframework.messaging.simp.SimpMessagingTemplate;

/**
 * REST API bơm dữ liệu demo 1-chạm (Scenario Simulator — Trang 7).
 *
 * Endpoints (theo FINAL spec §6.1):
 *   POST /api/simulator/inject     — Bơm packet demo, body chọn kịch bản
 *   POST /api/simulator/clear-data — Xóa toàn bộ dữ liệu mẫu & làm sạch hệ thống
 */
@RestController
@RequestMapping("/api/simulator")
public class SimulatorApiController {

    private final ScenarioSimulatorService simulator;
    private final SimpMessagingTemplate messagingTemplate;
    private final DatabaseManager databaseManager;

    @Value("${mesh.tcp.port:8888}")
    private int meshPort;

    @Value("${mesh.demo-mode:true}")
    private boolean demoMode;

    public SimulatorApiController(ScenarioSimulatorService simulator,
                                  SimpMessagingTemplate messagingTemplate,
                                  DatabaseManager databaseManager) {
        this.simulator = simulator;
        this.messagingTemplate = messagingTemplate;
        this.databaseManager = databaseManager;
    }

    /**
     * Endpoint thống nhất theo FINAL spec: POST /api/simulator/inject
     * Body: { "scenario": "FLOOD_BATCH", "count": 5 }
     */
    @PostMapping(value = "/inject",
                 consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> inject(@RequestBody Map<String, Object> body) {
        String scenario = (String) body.getOrDefault("scenario", "FLOOD_BATCH");
        int count = body.containsKey("count") ? ((Number) body.get("count")).intValue() : 5;

        return switch (scenario.toUpperCase()) {
            case "FLOOD_BATCH" -> {
                List<String> ids = simulator.injectFloodVictims(Math.min(count, 20), meshPort);
                yield ResponseEntity.ok(Map.of(
                        "status", "INJECTED", "scenario", "FLOOD_BATCH",
                        "count", ids.size(), "nodeIds", ids
                ));
            }
            case "RARE_BLOOD" -> {
                String id = simulator.injectMedicalEmergency(meshPort);
                yield ResponseEntity.ok(Map.of(
                        "status", "INJECTED", "scenario", "RARE_BLOOD",
                        "nodeId", id
                ));
            }
            case "MULTI_HOP" -> {
                String id = simulator.injectMultiHopPacket(meshPort);
                yield ResponseEntity.ok(Map.of(
                        "status", "INJECTED", "scenario", "MULTI_HOP",
                        "nodeId", id
                ));
            }
            case "FULL_DEMO" -> {
                List<String> floodIds = simulator.injectFloodVictims(5, meshPort);
                String medId = simulator.injectMedicalEmergency(meshPort);
                String hopId = simulator.injectMultiHopPacket(meshPort);
                yield ResponseEntity.ok(Map.of(
                        "status", "FULL_DEMO_INJECTED", "scenario", "FULL_DEMO",
                        "floodCount", floodIds.size(),
                        "medicalId", medId,
                        "multiHopId", hopId,
                        "total", floodIds.size() + 2
                ));
            }
            default -> ResponseEntity.badRequest().body(Map.of(
                    "error", "Kịch bản không hợp lệ: " + scenario,
                    "validScenarios", List.of("FLOOD_BATCH", "RARE_BLOOD", "MULTI_HOP", "FULL_DEMO")
            ));
        };
    }

    // =========================================================
    // TƯƠNG THÍCH NGƯỢC — các endpoint cũ vẫn hoạt động
    // =========================================================

    @PostMapping(value = "/flood", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> injectFlood(@RequestParam(defaultValue = "5") int count) {
        List<String> ids = simulator.injectFloodVictims(Math.min(count, 20), meshPort);
        return ResponseEntity.ok(Map.of("status", "INJECTED", "type", "FLOOD_TRAPPED", "count", ids.size(), "nodeIds", ids));
    }

    @PostMapping(value = "/medical", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> injectMedical() {
        String id = simulator.injectMedicalEmergency(meshPort);
        return ResponseEntity.ok(Map.of("status", "INJECTED", "type", "MEDICAL", "nodeId", id));
    }

    @PostMapping(value = "/multihop", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> injectMultiHop() {
        String id = simulator.injectMultiHopPacket(meshPort);
        return ResponseEntity.ok(Map.of("status", "INJECTED", "type", "MULTI_HOP", "nodeId", id));
    }

    @PostMapping(value = "/full-demo", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> fullDemo() {
        List<String> floodIds = simulator.injectFloodVictims(5, meshPort);
        String medId = simulator.injectMedicalEmergency(meshPort);
        String hopId = simulator.injectMultiHopPacket(meshPort);
        return ResponseEntity.ok(Map.of(
                "status", "FULL_DEMO_INJECTED", "floodCount", floodIds.size(),
                "medicalId", medId, "multiHopId", hopId, "total", floodIds.size() + 2
        ));
    }

    /**
     * XÓA TOÀN BỘ DỮ LIỆU MẪU & LÀM SẠCH HỆ THỐNG
     * POST /api/simulator/clear-data (Bảo vệ: Chỉ cho phép khi demo-mode: true)
     */
    @PostMapping(value = "/clear-data", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> clearData() {
        if (!demoMode) {
            return ResponseEntity.status(403).body(Map.of(
                    "status", "FORBIDDEN",
                    "message", "Tính năng xóa sạch dữ liệu bị khóa trong môi trường vận hành thực tế (Production)!"
            ));
        }

        databaseManager.clearAllOperationalData();

        // Broadcast realtime cập nhật rỗng tới toàn bộ dashboard client
        messagingTemplate.convertAndSend("/topic/victims", Map.of("type", "CLEAR_ALL", "timestamp", System.currentTimeMillis()));
        messagingTemplate.convertAndSend("/topic/interagency", Map.of("type", "CLEAR_ALL", "timestamp", System.currentTimeMillis()));
        messagingTemplate.convertAndSend("/topic/radio", Map.of("type", "CLEAR_ALL", "timestamp", System.currentTimeMillis()));
        messagingTemplate.convertAndSend("/topic/relay-status", Map.of("type", "CLEAR_ALL", "timestamp", System.currentTimeMillis()));
        messagingTemplate.convertAndSend("/topic/facilities", Map.of("type", "RESET_ALL", "timestamp", System.currentTimeMillis()));

        return ResponseEntity.ok(Map.of(
                "status", "CLEARED",
                "message", "Đã xóa toàn bộ dữ liệu mẫu (Nạn nhân, Nhật ký gói tin, Lệnh điều phối, Phiếu liên ngành, Bộ đàm)"
        ));
    }

    /**
     * BƠM 7 TÌNH HUỐNG CỨU HỘ THỰC TẾ & 7 PHIẾU TÁC CHIẾN LIÊN NGÀNH TẠI ĐÀ NẴNG
     * POST /api/simulator/inject-realistic
     */
    @PostMapping(value = "/inject-realistic", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> injectRealistic() {
        List<String> nodeIds = simulator.injectRealisticSituations(meshPort);

        // Broadcast realtime tới toàn bộ WebSocket clients
        messagingTemplate.convertAndSend("/topic/victims", Map.of("type", "REFRESH", "count", nodeIds.size(), "timestamp", System.currentTimeMillis()));
        messagingTemplate.convertAndSend("/topic/interagency", Map.of("type", "REFRESH", "tickets", 7, "timestamp", System.currentTimeMillis()));
        messagingTemplate.convertAndSend("/topic/radio", Map.of("type", "REFRESH", "radioLogs", 6, "timestamp", System.currentTimeMillis()));
        messagingTemplate.convertAndSend("/topic/facilities", Map.of("type", "REFRESH", "timestamp", System.currentTimeMillis()));

        return ResponseEntity.ok(Map.of(
                "status", "REALISTIC_SCENARIOS_INJECTED",
                "totalVictims", nodeIds.size(),
                "nodeIds", nodeIds,
                "message", "Đã nạp 7 tình huống cứu hộ thực tế tại Đà Nẵng, 7 phiếu liên ngành 2 chiều và 6 đàm thoại vô tuyến VHF CH16!"
        ));
    }
}

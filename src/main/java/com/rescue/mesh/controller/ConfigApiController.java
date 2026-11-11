package com.rescue.mesh.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * REST API đọc/ghi cấu hình trạm chỉ huy (Trang 7).
 *
 * Endpoints (theo FINAL spec §6.1):
 *   GET  /api/config/station — Đọc cấu hình hiện tại
 *   POST /api/config/station — Ghi cấu hình mới
 */
@RestController
@RequestMapping("/api/config/station")
public class ConfigApiController {

    /** Cấu hình trạm — lưu in-memory, reset khi restart. */
    private final ConcurrentHashMap<String, Object> stationConfig = new ConcurrentHashMap<>();

    public ConfigApiController() {
        // Cấu hình mặc định
        stationConfig.put("stationName", "Trạm Chỉ Huy Tác Chiến Đà Nẵng");
        stationConfig.put("latitude", 16.0544);
        stationConfig.put("longitude", 108.2022);
        stationConfig.put("meshPort", 8888);
        stationConfig.put("outboundPort", 8889);
        stationConfig.put("webPort", 8080);
        stationConfig.put("alertVolume", 80);
        stationConfig.put("alertEnabled", true);
        stationConfig.put("heartbeatTimeoutMs", 30000);
        stationConfig.put("metricsIntervalMs", 5000);
    }

    /** Đọc cấu hình trạm hiện tại. */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> getConfig() {
        return Map.copyOf(stationConfig);
    }

    /**
     * Cập nhật cấu hình trạm.
     * Body JSON tùy ý — merge vào config hiện tại.
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> updateConfig(@RequestBody Map<String, Object> updates) {
        stationConfig.putAll(updates);
        return ResponseEntity.ok(Map.of(
                "status", "UPDATED",
                "config", Map.copyOf(stationConfig)
        ));
    }
}

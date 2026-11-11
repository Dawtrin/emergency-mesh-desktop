package com.rescue.mesh.controller;

import com.rescue.mesh.socket.MeshTcpServer;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Endpoint kiểm tra nhanh trạng thái Dual-Engine tại hiện trường.
 * Không chứa dữ liệu nạn nhân; an toàn để cán bộ xác nhận Web 8080 và Mesh 8888 đang sẵn sàng.
 */
@RestController
public class HealthController {

    private final MeshTcpServer meshTcpServer;

    public HealthController(MeshTcpServer meshTcpServer) {
        this.meshTcpServer = meshTcpServer;
    }

    @GetMapping(value = "/api/health", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "webPort", 8080,
                "meshPort", meshTcpServer.getMeshPort(),
                "meshListening", meshTcpServer.isRunning(),
                "database", "SQLite WAL",
                "seenPacketCacheSize", meshTcpServer.getSeenPacketCacheSize()
        );
    }
}

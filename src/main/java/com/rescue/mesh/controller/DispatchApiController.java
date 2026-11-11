package com.rescue.mesh.controller;

import com.rescue.mesh.model.MeshPacket;
import com.rescue.mesh.service.C2ControllerService;
import com.rescue.mesh.socket.MeshTcpClient;
import com.rescue.mesh.socket.MeshTcpServer;
import com.rescue.mesh.util.PacketFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST API nhận lệnh điều phối từ Web Dashboard.
 *
 * Endpoints (theo FINAL spec §6.1):
 *   POST /api/dispatch — Gửi lệnh điều phối qua TCP Mesh + broadcast STOMP
 */
@RestController
@RequestMapping("/api/dispatch")
public class DispatchApiController {

    private final C2ControllerService c2Service;
    private final MeshTcpClient meshTcpClient;
    private final MeshTcpServer meshTcpServer;
    private final SimpMessagingTemplate messagingTemplate;

    @Value("${mesh.outbound.host:localhost}")
    private String relayHost;

    @Value("${mesh.outbound.port:8889}")
    private int relayPort;

    public DispatchApiController(C2ControllerService c2Service,
                                  MeshTcpClient meshTcpClient,
                                  MeshTcpServer meshTcpServer,
                                  SimpMessagingTemplate messagingTemplate) {
        this.c2Service = c2Service;
        this.meshTcpClient = meshTcpClient;
        this.meshTcpServer = meshTcpServer;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Gửi lệnh điều phối cứu nạn.
     * Body JSON: { "targetNodeId": "...", "command": "...", "severity": "CRITICAL" }
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> dispatch(@RequestBody Map<String, String> body) {
        String targetNodeId = body.get("targetNodeId");
        String command = body.getOrDefault("command", "Điều phối cứu hộ");
        String severity = body.getOrDefault("severity", "HIGH");

        if (targetNodeId == null || targetNodeId.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "targetNodeId là bắt buộc"));
        }

        // 1. Lưu lệnh + cập nhật trạng thái
        String dispatchId = c2Service.dispatchRescue(targetNodeId, command, "Trạm trưởng C2");

        // 2. Tạo gói tin DISPATCH_CMD
        MeshPacket dispatchPacket = PacketFactory.createDispatchCommand(
                targetNodeId, command, severity
        );

        // 3. Xếp vào hàng đợi của targetNodeId (khi điện thoại kết nối lại sẽ nhận được trên cùng socket)
        meshTcpServer.enqueueDispatch(targetNodeId, dispatchPacket);

        // 4. Đồng thời thử gửi qua kênh outbound localhost:8889 nếu có relay daemon
        meshTcpClient.sendAsync(relayHost, relayPort, dispatchPacket, null);

        // 5. Broadcast qua WebSocket — topic khớp FINAL spec
        messagingTemplate.convertAndSend("/topic/victims", Map.of(
                "type", "DISPATCH",
                "targetNodeId", targetNodeId,
                "command", command,
                "dispatchId", dispatchId,
                "timestamp", System.currentTimeMillis()
        ));

        return ResponseEntity.ok(Map.of(
                "status", "DISPATCHED",
                "dispatchId", dispatchId,
                "targetNodeId", targetNodeId
        ));
    }
}

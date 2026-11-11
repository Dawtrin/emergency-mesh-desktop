package com.rescue.mesh.controller;

import com.rescue.mesh.db.RelayNodeDao;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/devices")
public class DeviceRegistryApiController {

    private final RelayNodeDao relayNodeDao;

    @Value("${mesh.tcp.port:8888}")
    private int meshPort;

    public DeviceRegistryApiController(RelayNodeDao relayNodeDao) {
        this.relayNodeDao = relayNodeDao;
    }

    /**
     * Lấy danh sách tất cả thiết bị tham gia mạng Mesh (Node Nạn nhân, Relay Node).
     */
    @GetMapping
    public List<RelayNodeDao.RelayNodeRecord> listDevices() {
        return relayNodeDao.findAll();
    }

    /**
     * Tạo chuỗi dữ liệu cấu hình để vẽ mã QR Code ghép đôi thiết bị Android (Zero-Config).
     */
    @GetMapping("/qr-config")
    public ResponseEntity<Map<String, Object>> getQrConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("meshPort", meshPort);
        config.put("protocol", "TCP_JSON_MESH");
        config.put("version", "v1.0");
        config.put("defaultTtl", 5);
        config.put("networkSsid", "MeshRescue_C2");
        return ResponseEntity.ok(config);
    }
}

package com.rescue.mesh.controller;

import com.rescue.mesh.db.RelayNodeDao;
import com.rescue.mesh.service.C2ControllerService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST API danh sách trạm tiếp sóng Relay cho sơ đồ Topology (Trang 5).
 *
 * Endpoints (theo FINAL spec §6.1):
 *   GET /api/relay-nodes — Danh sách + trạng thái Relay
 */
@RestController
@RequestMapping("/api/relay-nodes")
public class RelayNodeApiController {

    private final C2ControllerService c2Service;

    public RelayNodeApiController(C2ControllerService c2Service) {
        this.c2Service = c2Service;
    }

    /** Danh sách relay nodes kèm trạng thái (ONLINE/TIMEOUT/OFFLINE). */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public List<RelayNodeDao.RelayNodeRecord> getRelayNodes() {
        return c2Service.getRelayNodeDao().findAll();
    }

    /** Thống kê relay nodes — tổng, online, timeout, offline. */
    @GetMapping(value = "/stats", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> getRelayStats() {
        List<RelayNodeDao.RelayNodeRecord> all = c2Service.getRelayNodeDao().findAll();
        long online = all.stream().filter(r -> "ONLINE".equals(r.status())).count();
        long timeout = all.stream().filter(r -> "TIMEOUT".equals(r.status())).count();
        long offline = all.stream().filter(r -> "OFFLINE".equals(r.status())).count();

        return Map.of(
                "total", all.size(),
                "online", online,
                "timeout", timeout,
                "offline", offline
        );
    }
}

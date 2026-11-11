package com.rescue.mesh.controller;

import com.rescue.mesh.db.MetricDao;
import com.rescue.mesh.db.PacketDao;
import com.rescue.mesh.service.C2ControllerService;
import com.rescue.mesh.service.NetworkAnalyticsService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST API giám sát lưu lượng mạng — Đề tài 53 (Đo tải & Thông lượng IP).
 *
 * Endpoints (theo FINAL spec §6.1):
 *   GET /api/metrics/summary     — Snapshot Đề 53 khi mới load trang
 *   GET /api/metrics/history     — Lịch sử metrics (cho biểu đồ)
 *   GET /api/metrics/relay-load  — Phân bố tải relay nodes
 *   GET /api/packets?nodeId=&limit= — Nhật ký gói tin (Packet Sniffer & Audit Trail)
 */
@RestController
@RequestMapping("/api")
public class MetricsApiController {

    private final NetworkAnalyticsService analyticsService;
    private final C2ControllerService c2Service;

    public MetricsApiController(NetworkAnalyticsService analyticsService,
                                 C2ControllerService c2Service) {
        this.analyticsService = analyticsService;
        this.c2Service = c2Service;
    }

    /** Snapshot tức thời của thống kê mạng — FINAL spec: GET /api/metrics/summary */
    @GetMapping(value = "/metrics/summary", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> getMetricsSummary() {
        int totalReceived = analyticsService.getTotalReceived();
        int totalDropped = analyticsService.getTotalDropped();
        int totalDuplicates = analyticsService.getTotalDuplicates();
        double dropRate = analyticsService.getDuplicateDropRate();
        double integrity = analyticsService.getChecksumIntegrityRate();

        return Map.of(
                "totalReceived", totalReceived,
                "totalDropped", totalDropped,
                "totalDuplicates", totalDuplicates,
                "dropRate", Math.round(dropRate * 100.0) / 100.0,
                "integrity", Math.round(integrity * 100.0) / 100.0
        );
    }

    /** Lịch sử metrics (20 bản ghi gần nhất cho biểu đồ). */
    @GetMapping(value = "/metrics/history", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<MetricDao.MetricRecord> getMetricsHistory() {
        return analyticsService.getRecentMetrics(20);
    }

    /** Phân bố tải relay nodes (cho BarChart). */
    @GetMapping(value = "/metrics/relay-load", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<MetricDao.RelayLoadStat> getRelayLoad() {
        return analyticsService.getRelayLoadStats();
    }

    /**
     * Nhật ký gói tin — FINAL spec: GET /api/packets?nodeId=&limit=
     * Phục vụ Packet Sniffer (Trang 3) và Audit Trail (Trang 6).
     */
    @GetMapping(value = "/packets", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<PacketDao.PacketRecord> getPackets(
            @RequestParam(required = false) String nodeId,
            @RequestParam(defaultValue = "50") int limit) {
        return c2Service.getPacketDao().findWithFilter(nodeId, Math.min(limit, 200));
    }
}

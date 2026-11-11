package com.rescue.mesh.controller;

import com.rescue.mesh.db.RelayNodeDao;
import com.rescue.mesh.service.C2ControllerService;
import com.rescue.mesh.service.NetworkAnalyticsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Đẩy dữ liệu tổng hợp định kỳ lên trình duyệt qua WebSocket STOMP.
 *
 * Topics (theo FINAL spec §6.2):
 *   /topic/network-metrics — Snapshot thống kê mạng mỗi 5 giây (Đề 53)
 *   /topic/kpi             — Chỉ số KPI cứu nạn mỗi 5 giây (Trang chủ)
 *   /topic/relay-status    — Trạng thái relay nodes mỗi 10 giây
 *   /topic/alerts          — Cảnh báo CRITICAL (event-driven, kích hoạt còi + strobe)
 */
@Component
public class LiveWebSocketBroadcaster {

    private static final Logger LOGGER = LoggerFactory.getLogger(LiveWebSocketBroadcaster.class);

    private final SimpMessagingTemplate messagingTemplate;
    private final NetworkAnalyticsService analyticsService;
    private final C2ControllerService c2Service;

    /** Theo dõi số ca CRITICAL để chỉ alert khi có ca mới */
    private volatile int lastKnownCriticalCount = 0;

    public LiveWebSocketBroadcaster(SimpMessagingTemplate messagingTemplate,
                                     NetworkAnalyticsService analyticsService,
                                     C2ControllerService c2Service) {
        this.messagingTemplate = messagingTemplate;
        this.analyticsService = analyticsService;
        this.c2Service = c2Service;
    }

    /** Push network metrics snapshot mỗi 5 giây cho biểu đồ real-time Đề 53. */
    @Scheduled(fixedRate = 5000)
    public void broadcastNetworkMetrics() {
        messagingTemplate.convertAndSend("/topic/network-metrics", Map.of(
                "totalReceived", analyticsService.getTotalReceived(),
                "totalDropped", analyticsService.getTotalDropped(),
                "totalDuplicates", analyticsService.getTotalDuplicates(),
                "dropRate", round(analyticsService.getDuplicateDropRate()),
                "integrity", round(analyticsService.getChecksumIntegrityRate()),
                "timestamp", System.currentTimeMillis()
        ));
    }

    /** Push KPI summary mỗi 5 giây cho Trang Chủ + kiểm tra alerts. */
    @Scheduled(fixedRate = 5000)
    public void broadcastKpi() {
        int pending = c2Service.getPendingCount();
        int dispatched = c2Service.getDispatchedCount();
        int resolved = c2Service.getResolvedCount();
        int total = pending + dispatched + resolved;

        messagingTemplate.convertAndSend("/topic/kpi", Map.of(
                "total", total,
                "pending", pending,
                "dispatched", dispatched,
                "resolved", resolved,
                "resolveRate", total > 0 ? Math.round(resolved * 100.0 / total) : 0,
                "timestamp", System.currentTimeMillis()
        ));

        // Kiểm tra ca CRITICAL mới — push alert toàn cục
        checkAndBroadcastAlerts();
    }

    /** Push relay node status mỗi 10 giây — FINAL spec /topic/relay-status */
    @Scheduled(fixedRate = 10000)
    public void broadcastRelayStatus() {
        List<Map<String, Object>> relays = c2Service.getRelayNodeDao().findAllAsMap();
        messagingTemplate.convertAndSend("/topic/relay-status", relays);

        // Cũng kiểm tra timeout relay
        c2Service.checkRelayTimeouts();
    }

    /**
     * Kiểm tra và phát cảnh báo CRITICAL mới qua /topic/alerts.
     * Chỉ phát khi có ca CRITICAL mới xuất hiện so với lần kiểm tra trước.
     */
    private void checkAndBroadcastAlerts() {
        var criticalVictims = c2Service.getVictimDao().findByStatus("PENDING").stream()
                .filter(v -> "CRITICAL".equals(v.severity()))
                .toList();
        int currentCritical = criticalVictims.size();

        if (currentCritical > lastKnownCriticalCount && !criticalVictims.isEmpty()) {
            var latestCritical = criticalVictims.get(criticalVictims.size() - 1);
            messagingTemplate.convertAndSend("/topic/alerts", Map.of(
                    "severity", "CRITICAL",
                    "victimNodeId", latestCritical.nodeId(),
                    "message", "🚨 CA NGUY CẤP MỚI: " + latestCritical.fullName()
                            + " (" + latestCritical.alertType() + ") — Cần cứu nạn khẩn cấp!",
                    "timestamp", System.currentTimeMillis()
            ));
            LOGGER.warn("[ALERT] Cảnh báo CRITICAL mới: {} — {}", latestCritical.nodeId(), latestCritical.fullName());
        }
        lastKnownCriticalCount = currentCritical;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}

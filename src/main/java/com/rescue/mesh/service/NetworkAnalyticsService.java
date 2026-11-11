package com.rescue.mesh.service;

import com.rescue.mesh.db.DatabaseManager;
import com.rescue.mesh.db.MetricDao;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Dịch vụ tổng hợp thống kê lưu lượng mạng phân tán (Đề 53).
 *
 * Chức năng:
 *   - Tracking: packets received/forwarded/dropped/duplicate per interval
 *   - Tính toán: Throughput (packets/min), Duplicate Drop Rate (%), Relay Load Distribution
 *   - Ghi metrics vào DB theo chu kỳ (mặc định 10 giây)
 *   - Cung cấp dữ liệu cho LineChart / BarChart real-time trên Dashboard
 *
 * Thread Safety: AtomicInteger cho tất cả counters → safe multi-thread access.
 */
public class NetworkAnalyticsService {

    private final MetricDao metricDao;

    /** Bộ đếm gói tin trong interval hiện tại */
    private final AtomicInteger packetsReceived = new AtomicInteger(0);
    private final AtomicInteger packetsForwarded = new AtomicInteger(0);
    private final AtomicInteger packetsDropped = new AtomicInteger(0);
    private final AtomicInteger duplicateDrops = new AtomicInteger(0);

    /** Tổng cộng cả đời ứng dụng */
    private final AtomicInteger totalReceived = new AtomicInteger(0);
    private final AtomicInteger totalDropped = new AtomicInteger(0);
    private final AtomicInteger totalDuplicates = new AtomicInteger(0);
    private final AtomicInteger totalChecksumFails = new AtomicInteger(0);

    /** Scheduler ghi metrics định kỳ */
    private final ScheduledExecutorService scheduler;

    /** Chu kỳ ghi metrics (giây) */
    private static final long METRIC_INTERVAL_SECONDS = 10L;

    /** Số relay nodes đang online */
    private final AtomicInteger activeRelayCount = new AtomicInteger(0);

    public NetworkAnalyticsService(DatabaseManager dbManager) {
        this.metricDao = new MetricDao(dbManager);

        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "NetworkAnalytics-Scheduler");
            t.setDaemon(true);
            return t;
        });

        // Ghi metrics mỗi METRIC_INTERVAL_SECONDS giây
        scheduler.scheduleAtFixedRate(this::flushMetrics,
                METRIC_INTERVAL_SECONDS, METRIC_INTERVAL_SECONDS, TimeUnit.SECONDS);

        System.out.println("[ANALYTICS] NetworkAnalyticsService khởi tạo — interval: "
                + METRIC_INTERVAL_SECONDS + "s");
    }

    // =========================================================
    // RECORDING (gọi từ RoutingEngine callback)
    // =========================================================

    public void recordPacketReceived() {
        packetsReceived.incrementAndGet();
        totalReceived.incrementAndGet();
    }

    public void recordPacketForwarded() {
        packetsForwarded.incrementAndGet();
    }

    public void recordPacketDropped() {
        packetsDropped.incrementAndGet();
        totalDropped.incrementAndGet();
    }

    public void recordDuplicateDrop() {
        duplicateDrops.incrementAndGet();
        totalDuplicates.incrementAndGet();
    }

    public void recordChecksumFail() {
        totalChecksumFails.incrementAndGet();
    }

    public void setActiveRelayCount(int count) {
        activeRelayCount.set(count);
    }

    // =========================================================
    // FLUSH — Ghi interval metrics vào DB
    // =========================================================

    /**
     * Flush metrics hiện tại vào DB rồi reset counters.
     */
    private void flushMetrics() {
        int recv = packetsReceived.getAndSet(0);
        int fwd = packetsForwarded.getAndSet(0);
        int drop = packetsDropped.getAndSet(0);
        int dup = duplicateDrops.getAndSet(0);
        int relays = activeRelayCount.get();

        // Chỉ ghi nếu có hoạt động (tránh spam DB)
        if (recv > 0 || fwd > 0 || drop > 0 || dup > 0) {
            metricDao.insertMetric(recv, fwd, drop, dup, relays);
        }
    }

    // =========================================================
    // QUERIES — Dữ liệu cho UI Charts
    // =========================================================

    /**
     * Lấy N bản ghi metric gần nhất (cho LineChart).
     */
    public List<MetricDao.MetricRecord> getRecentMetrics(int limit) {
        return metricDao.findRecent(limit);
    }

    /**
     * Lấy phân bố tải relay nodes (cho BarChart).
     */
    public List<MetricDao.RelayLoadStat> getRelayLoadStats() {
        return metricDao.getRelayLoadStats();
    }

    /**
     * Tính tỷ lệ duplicate drop rate (%).
     */
    public double getDuplicateDropRate() {
        int total = totalReceived.get();
        int dups = totalDuplicates.get();
        if (total == 0) return 0.0;
        return (double) dups / total * 100.0;
    }

    /**
     * Tính tỷ lệ checksum integrity (%).
     */
    public double getChecksumIntegrityRate() {
        int total = totalReceived.get();
        int fails = totalChecksumFails.get();
        if (total == 0) return 100.0;
        return (1.0 - (double) fails / total) * 100.0;
    }

    // =========================================================
    // TOTALS
    // =========================================================

    public int getTotalReceived() { return totalReceived.get(); }
    public int getTotalDropped() { return totalDropped.get(); }
    public int getTotalDuplicates() { return totalDuplicates.get(); }

    // =========================================================
    // SHUTDOWN
    // =========================================================

    public void shutdown() {
        // Flush cuối cùng trước khi tắt
        flushMetrics();
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        System.out.println("[ANALYTICS] NetworkAnalyticsService đã shutdown.");
    }
}

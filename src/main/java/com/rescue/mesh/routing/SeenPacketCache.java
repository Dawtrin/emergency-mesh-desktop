package com.rescue.mesh.routing;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Bộ nhớ đệm chống lặp gói tin (Duplicate Suppression Cache).
 *
 * Cơ sở lý thuyết:
 *   Trong mạng Mesh dùng Flooding, một gói tin có thể đến cùng một node
 *   nhiều lần qua các đường khác nhau. Nếu không có cơ chế lọc,
 *   gói tin sẽ bị xử lý nhiều lần và gây bão gói tin (packet storm).
 *
 *   Giải pháp: Mỗi node lưu UUID của gói tin đã xử lý vào ConcurrentHashMap.
 *   Nếu UUID đã tồn tại → DROP ngay lập tức, không xử lý tiếp.
 *
 *   TTL-based expiry: Sau ENTRY_TTL_SECONDS (mặc định 60s), entry bị xóa
 *   để tránh memory leak khi chạy dài hạn.
 *
 * Thread Safety: ConcurrentHashMap đảm bảo an toàn khi nhiều thread
 * cùng đọc/ghi đồng thời (multi-client server scenario).
 */
public class SeenPacketCache {

    /** Thời gian sống của mỗi entry trong cache (giây) */
    private static final long ENTRY_TTL_SECONDS = 60L;

    /** Chu kỳ chạy cleanup thread (giây) */
    private static final long CLEANUP_INTERVAL_SECONDS = 30L;

    /**
     * Map lưu: packetId (UUID string) → thời điểm nhận được (milliseconds).
     * ConcurrentHashMap đảm bảo thread-safe mà không cần synchronized.
     */
    private final ConcurrentHashMap<String, Long> cache = new ConcurrentHashMap<>();

    /** Scheduler tự động dọn dẹp entry hết hạn */
    private final ScheduledExecutorService cleanupScheduler;

    /**
     * Khởi tạo cache và bắt đầu cleanup thread chạy nền.
     */
    public SeenPacketCache() {
        this.cleanupScheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "SeenPacketCache-Cleanup");
            thread.setDaemon(true); // Thread daemon: tự tắt khi JVM tắt
            return thread;
        });

        // Lên lịch cleanup mỗi CLEANUP_INTERVAL_SECONDS giây
        cleanupScheduler.scheduleAtFixedRate(
                this::removeExpiredEntries,
                CLEANUP_INTERVAL_SECONDS,
                CLEANUP_INTERVAL_SECONDS,
                TimeUnit.SECONDS
        );
    }

    /**
     * Kiểm tra xem gói tin với packetId đã được xử lý chưa.
     *
     * @param packetId UUID của gói tin cần kiểm tra
     * @return true nếu đã thấy gói tin này (→ phải DROP), false nếu chưa thấy (→ xử lý tiếp)
     */
    public boolean contains(String packetId) {
        if (packetId == null) {
            return false;
        }
        return cache.containsKey(packetId);
    }

    /**
     * Đánh dấu gói tin đã được xử lý bằng cách thêm UUID vào cache.
     *
     * @param packetId UUID của gói tin vừa xử lý
     */
    public void markAsSeen(String packetId) {
        if (packetId == null) {
            return;
        }
        cache.put(packetId, System.currentTimeMillis());
    }

    /**
     * Kiểm tra VÀ đánh dấu trong một thao tác nguyên tử (atomic).
     * Dùng putIfAbsent để tránh race condition giữa contains() và markAsSeen().
     *
     * @param packetId UUID của gói tin
     * @return true nếu gói tin ĐÃ được thấy trước đó (→ DROP),
     *         false nếu là lần đầu thấy (→ tiếp tục xử lý)
     */
    public boolean checkAndMark(String packetId) {
        if (packetId == null) {
            return false;
        }
        // putIfAbsent trả về null nếu key chưa tồn tại (→ mới thêm vào thành công)
        // trả về giá trị cũ nếu key đã tồn tại (→ đã thấy rồi)
        Long previousValue = cache.putIfAbsent(packetId, System.currentTimeMillis());
        return previousValue != null; // true = đã thấy → cần DROP
    }

    /**
     * Xóa tất cả entry đã quá thời gian sống (ENTRY_TTL_SECONDS).
     * Được gọi tự động bởi cleanupScheduler.
     */
    private void removeExpiredEntries() {
        long now = System.currentTimeMillis();
        long ttlMillis = ENTRY_TTL_SECONDS * 1000L;

        int removedCount = 0;
        for (ConcurrentHashMap.Entry<String, Long> entry : cache.entrySet()) {
            if (now - entry.getValue() > ttlMillis) {
                cache.remove(entry.getKey());
                removedCount++;
            }
        }

        if (removedCount > 0) {
            System.out.println("[INFO] SeenPacketCache: đã xóa " + removedCount
                    + " entry hết hạn. Cache hiện có: " + cache.size() + " entries.");
        }
    }

    /**
     * Trả về số lượng entry hiện có trong cache (dùng để debug/monitor).
     *
     * @return Số lượng UUID đang được lưu
     */
    public int size() {
        return cache.size();
    }

    /**
     * Xóa toàn bộ cache (dùng cho testing hoặc reset hệ thống).
     */
    public void clear() {
        cache.clear();
    }

    /**
     * Dừng cleanup scheduler khi không còn cần dùng cache.
     * Gọi khi shutdown ứng dụng để giải phóng thread.
     */
    public void shutdown() {
        cleanupScheduler.shutdown();
        try {
            if (!cleanupScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
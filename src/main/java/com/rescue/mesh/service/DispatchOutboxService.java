package com.rescue.mesh.service;

import com.rescue.mesh.model.MeshPacket;
import com.rescue.mesh.model.PacketValidator;
import com.rescue.mesh.model.ValidationResult;
import com.rescue.mesh.network.SocketClient;
import com.rescue.mesh.storage.BaseStationStorageService;
import com.rescue.mesh.storage.model.DispatchRecord;
import com.rescue.mesh.util.PacketFactory;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service quản lý Outbox các lệnh điều phối từ Base Station với Exponential Backoff Retry.
 *
 * Tính năng chính:
 *   1. Quản lý trạng thái lệnh gửi: PENDING -> SENT -> ACKED / FAILED trong SQLite.
 *   2. Thử lại với thuật toán Exponential Backoff:
 *      - Lần 1: 2 giây
 *      - Lần 2: 4 giây
 *      - Lần 3: 8 giây
 *      - Lần 4: 16 giây
 *      - Max delay: 60 giây
 *      - Max attempts: 5 lần. Vượt quá 5 lần -> FAILED.
 *   3. Xử lý biên nhận ACK (Machine-Readable ack_for_packet_id):
 *      - Khi nhận ACK, chuyển trạng thái thành ACKED.
 *      - Idempotent: nhận ACK trùng lặp vẫn an toàn.
 *   4. Khôi phục từ SQLite: các lệnh PENDING/SENT chưa hoàn tất được tiếp tục khi restart.
 */
public class DispatchOutboxService {

    private static final Logger LOGGER = Logger.getLogger(DispatchOutboxService.class.getName());

    public static final long INITIAL_BACKOFF_MS = 2000L;
    public static final int BACKOFF_MULTIPLIER = 2;
    public static final long MAX_BACKOFF_MS = 60000L;
    public static final int MAX_ATTEMPTS = 5;
    public static final long DEFAULT_FINAL_ACK_TIMEOUT_MS = 32000L;

    @FunctionalInterface
    public interface DispatchSender {
        boolean send(String host, int port, MeshPacket packet);
    }

    private final BaseStationStorageService storageService;
    private final DispatchSender sender;
    private final String baseNodeId;
    private String targetHost;
    private int targetPort;
    private long finalAckTimeoutMs = DEFAULT_FINAL_ACK_TIMEOUT_MS;
    private final java.util.concurrent.locks.ReentrantLock scanLock = new java.util.concurrent.locks.ReentrantLock();

    private ScheduledExecutorService scheduler;
    private volatile boolean running = false;

    public DispatchOutboxService(BaseStationStorageService storageService) {
        this(storageService, "localhost", 8002, MeshPacket.NODE_BASE_STATION,
                (h, p, pkt) -> SocketClient.send(h, p, pkt));
    }

    public DispatchOutboxService(BaseStationStorageService storageService,
                                 String targetHost,
                                 int targetPort,
                                 DispatchSender sender) {
        this(storageService, targetHost, targetPort,
                MeshPacket.NODE_BASE_STATION, sender);
    }

    public DispatchOutboxService(BaseStationStorageService storageService,
                                 String targetHost,
                                 int targetPort,
                                 String baseNodeId,
                                 DispatchSender sender) {
        if (baseNodeId == null || baseNodeId.isBlank()) {
            throw new IllegalArgumentException("baseNodeId must not be blank");
        }
        this.storageService = storageService;
        this.targetHost = targetHost;
        this.targetPort = targetPort;
        this.baseNodeId = baseNodeId.trim();
        this.sender = sender;
    }

    public void setFinalAckTimeoutMs(long finalAckTimeoutMs) {
        this.finalAckTimeoutMs = finalAckTimeoutMs;
    }

    public long getFinalAckTimeoutMs() {
        return finalAckTimeoutMs;
    }

    /**
     * Kích hoạt quét outbox ngay lập tức một cách an toàn mà không sinh luồng rác.
     * Sử dụng scheduler đơn luồng hiện có hoặc chạy an toàn dưới scanLock.
     */
    public void triggerImmediateScan() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.submit(this::processPendingDispatchesSafely);
        } else {
            processPendingDispatchesSafely();
        }
    }

    /**
     * Khởi động scheduler quét outbox định kỳ.
     */
    public synchronized void start(long pollIntervalMs) {
        if (running) return;
        running = true;
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "DispatchOutbox-Worker");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(this::processPendingDispatchesSafely,
                pollIntervalMs, pollIntervalMs, TimeUnit.MILLISECONDS);
        LOGGER.info("DispatchOutboxService đã khởi động (chu kỳ quét: " + pollIntervalMs + "ms)");
    }

    /**
     * Dừng scheduler an toàn.
     */
    public synchronized void stop() {
        running = false;
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(3, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        LOGGER.info("DispatchOutboxService đã dừng.");
    }

    /**
     * Đưa lệnh điều phối mới vào outbox.
     * Lưu vào SQLite với delivery_status = PENDING, attempt_count = 0, next_attempt_time = now.
     *
     * @param targetNodeId Node đích
     * @param message Tin nhắn lệnh
     * @param severity Mức độ nghiêm trọng
     * @return packetId của lệnh điều phối
     */
    public String enqueueDispatch(String targetNodeId, String message, String severity) {
        MeshPacket dispatchPacket = PacketFactory.createDispatchCommand(targetNodeId, message, severity);
        dispatchPacket.setSourceNodeId(baseNodeId);
        dispatchPacket.setSenderHopId(baseNodeId);
        dispatchPacket.computeAndSetChecksum();
        storageService.saveDispatchCommand(dispatchPacket, message, severity);
        LOGGER.info("Enqueued dispatch " + dispatchPacket.getPacketId() + " to " + targetNodeId);
        return dispatchPacket.getPacketId();
    }

    /**
     * Quét và xử lý các lệnh điều phối cần gửi hoặc thử lại.
     * Race-safe & Single-consumer: chỉ 1 luồng quét tại một thời điểm.
     */
    public void processPendingDispatches() {
        if (!scanLock.tryLock()) {
            LOGGER.fine("Dispatch scan đang được thực thi bởi luồng khác; bỏ qua lần gọi này.");
            return;
        }
        try {
            long now = System.currentTimeMillis();
            List<DispatchRecord> dueList = storageService.findPendingOrSentDue(now);

            for (DispatchRecord record : dueList) {
                processSingleDispatch(record, now);
            }
        } finally {
            scanLock.unlock();
        }
    }

    private void processPendingDispatchesSafely() {
        try {
            processPendingDispatches();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Lỗi khi xử lý dispatch outbox: " + e.getMessage(), e);
        }
    }

    private void processSingleDispatch(DispatchRecord record, long now) {
        // Trường hợp 1: Dispatch đã ở trạng thái SENT và đã gửi lần thứ MAX_ATTEMPTS.
        // Nếu xuất hiện ở đây, nghĩa là đã hết thời gian chờ ACK lần cuối (finalAckTimeoutMs).
        if ("SENT".equals(record.getDeliveryStatus()) && record.getAttemptCount() >= MAX_ATTEMPTS) {
            storageService.updateDispatchRetry(record.getPacketId(), "FAILED", record.getAttemptCount(), null);
            LOGGER.warning("Dispatch " + record.getPacketId() + " hết thời gian chờ ACK lần cuối -> FAILED");
            return;
        }

        int newAttempt = record.getAttemptCount() + 1;

        // Nếu đã thử quá số lần cho phép -> FAILED
        if (newAttempt > MAX_ATTEMPTS) {
            storageService.updateDispatchRetry(record.getPacketId(), "FAILED", record.getAttemptCount(), null);
            LOGGER.warning("Dispatch " + record.getPacketId() + " vượt quá " + MAX_ATTEMPTS + " lần thử -> FAILED");
            return;
        }

        // Tính thời gian chờ: nếu là lần cuối (MAX_ATTEMPTS), dùng finalAckTimeoutMs; các lần trước dùng backoff
        long ackWaitTimeout = (newAttempt < MAX_ATTEMPTS) ? calculateBackoff(newAttempt) : finalAckTimeoutMs;
        Long nextAttemptTime = now + ackWaitTimeout;

        // Tái tạo MeshPacket từ bản ghi lưu trữ với đúng packetId gốc
        MeshPacket packet = PacketFactory.createDispatchCommand(
                record.getTargetNodeId(),
                record.getMessage(),
                record.getSeverity()
        );
        packet.setPacketId(record.getPacketId());
        packet.setSourceNodeId(baseNodeId);
        packet.setSenderHopId(baseNodeId);
        packet.computeAndSetChecksum();

        // Ghi nhận số lần thử và hạn chót ACK vào SQLite TRƯỚC KHI thực hiện network I/O
        storageService.updateDispatchRetry(record.getPacketId(), "SENT", newAttempt, nextAttemptTime);

        LOGGER.info("Gửi dispatch " + record.getPacketId() + " (lần thử " + newAttempt + "/" + MAX_ATTEMPTS + ")");
        boolean success = false;
        try {
            success = sender.send(targetHost, targetPort, packet);
        } catch (Exception e) {
            LOGGER.warning("Ngoại lệ khi gửi dispatch: " + e.getMessage());
        }

        if (success) {
            // Đã gửi socket thành công: KHÔNG ghi đè SENT vô điều kiện ở đây.
            // Bản ghi đã ở trạng thái SENT từ trước khi gửi, hoặc có thể đã chuyển thành ACKED nếu ACK đến trong lúc send().
            LOGGER.info("Dispatch " + record.getPacketId() + " đã gửi thành công (lần " + newAttempt + "/" + MAX_ATTEMPTS + "), chờ ACK đến " + nextAttemptTime);
        } else {
            // Gửi socket thất bại: cập nhật có điều kiện `WHERE delivery_status != 'ACKED'`
            if (newAttempt >= MAX_ATTEMPTS) {
                storageService.updateDispatchRetry(record.getPacketId(), "FAILED", newAttempt, null);
                LOGGER.warning("Dispatch " + record.getPacketId() + " gửi thất bại và hết số lần thử -> FAILED");
            } else {
                storageService.updateDispatchRetry(record.getPacketId(), "PENDING", newAttempt, nextAttemptTime);
                LOGGER.info("Dispatch " + record.getPacketId() + " gửi thất bại, sẽ thử lại sau " + ackWaitTimeout + "ms");
            }
        }
    }

    /**
     * Tính toán khoảng thời gian lùi (backoff) theo số lần thử:
     * base * (multiplier ^ (attempt - 1)), tối đa MAX_BACKOFF_MS.
     */
    public static long calculateBackoff(int attempt) {
        if (attempt <= 1) return INITIAL_BACKOFF_MS;
        long multiplier = 1L << Math.min(attempt - 1, 30);
        long delay = INITIAL_BACKOFF_MS * multiplier;
        if (delay > MAX_BACKOFF_MS || delay < 0) {
            return MAX_BACKOFF_MS;
        }
        return delay;
    }

    /**
     * Accepts only a canonical, checksum-valid ACK addressed to this Base Station
     * and correlated to a SENT dispatch from the expected victim node.
     */
    public boolean handleIncomingAck(MeshPacket ackPacket) {
        if (ackPacket == null || !MeshPacket.TYPE_ACK.equals(ackPacket.getPacketType())) {
            return false;
        }

        ValidationResult validation = PacketValidator.validate(ackPacket);
        if (!validation.isValid()) {
            LOGGER.warning("Rejected invalid ACK: " + validation.getFirstViolation());
            return false;
        }
        if (!ackPacket.verifyChecksum()) {
            LOGGER.warning("Rejected ACK with invalid checksum: " + ackPacket.getPacketId());
            return false;
        }
        if (!baseNodeId.equals(ackPacket.getDestinationNodeId())) {
            LOGGER.warning("Rejected ACK addressed to " + ackPacket.getDestinationNodeId()
                    + " instead of " + baseNodeId);
            return false;
        }

        String ackForId = ackPacket.getPayload() != null
                ? ackPacket.getPayload().getAckForPacketId() : null;
        if (ackForId == null || ackForId.isBlank()) {
            LOGGER.warning("Rejected ACK without machine-readable ack_for_packet_id");
            return false;
        }

        Optional<DispatchRecord> existing = storageService.findDispatch(ackForId);
        if (existing.isEmpty()) {
            LOGGER.fine("Received ACK for unknown dispatch: " + ackForId);
            return false;
        }

        DispatchRecord record = existing.get();
        if (!record.getTargetNodeId().equals(ackPacket.getSourceNodeId())) {
            LOGGER.warning("Rejected ACK source " + ackPacket.getSourceNodeId()
                    + " for dispatch target " + record.getTargetNodeId());
            return false;
        }
        if ("ACKED".equals(record.getDeliveryStatus())) {
            return true;
        }
        if (!"SENT".equals(record.getDeliveryStatus())) {
            LOGGER.warning("Rejected ACK for dispatch " + ackForId + " in state "
                    + record.getDeliveryStatus());
            return false;
        }

        storageService.updateDispatchStatus(ackForId, "ACKED");
        LOGGER.info("Dispatch " + ackForId + " was ACKED by " + record.getTargetNodeId());
        return true;
    }

    public void setTargetHost(String targetHost) {
        this.targetHost = targetHost;
    }

    public void setTargetPort(int targetPort) {
        this.targetPort = targetPort;
    }

    public boolean isRunning() {
        return running;
    }
}

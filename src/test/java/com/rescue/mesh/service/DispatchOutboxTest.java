package com.rescue.mesh.service;

import com.rescue.mesh.model.MeshPacket;
import com.rescue.mesh.storage.BaseStationStorageService;
import com.rescue.mesh.storage.DatabaseConfig;
import com.rescue.mesh.storage.DatabaseManager;
import com.rescue.mesh.storage.model.DispatchRecord;
import com.rescue.mesh.util.ChecksumUtil;
import com.rescue.mesh.util.PacketFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 2.4: Desktop ACK and Persistent Dispatch Outbox Tests")
class DispatchOutboxTest {

    @TempDir
    Path tempDir;

    private Path dbPath;
    private DatabaseManager dbManager;
    private BaseStationStorageService storageService;
    private DispatchOutboxService outboxService;

    // Mock sender
    private AtomicBoolean shouldSendSucceed;
    private AtomicInteger sendAttempts;

    @BeforeEach
    void setUp() throws Exception {
        dbPath = tempDir.resolve("outbox_test.db");
        DatabaseConfig config = DatabaseConfig.forPath(dbPath);
        dbManager = new DatabaseManager(config);
        dbManager.initialize();

        storageService = new BaseStationStorageService(dbManager);

        shouldSendSucceed = new AtomicBoolean(false);
        sendAttempts = new AtomicInteger(0);

        outboxService = new DispatchOutboxService(
                storageService,
                "localhost",
                8002,
                (h, p, pkt) -> {
                    sendAttempts.incrementAndGet();
                    return shouldSendSucceed.get();
                }
        );
    }

    @AfterEach
    void tearDown() {
        if (outboxService != null) {
            outboxService.stop();
        }
        if (dbManager != null) {
            dbManager.close();
        }
    }

    @Test
    @DisplayName("Kiểm tra công thức Exponential Backoff")
    void testExponentialBackoffCalculations() {
        assertEquals(2000L, DispatchOutboxService.calculateBackoff(1));
        assertEquals(4000L, DispatchOutboxService.calculateBackoff(2));
        assertEquals(8000L, DispatchOutboxService.calculateBackoff(3));
        assertEquals(16000L, DispatchOutboxService.calculateBackoff(4));
        assertEquals(32000L, DispatchOutboxService.calculateBackoff(5));
        assertEquals(60000L, DispatchOutboxService.calculateBackoff(6), "Phải bị giới hạn ở MAX_BACKOFF_MS (60s)");
        assertEquals(60000L, DispatchOutboxService.calculateBackoff(10), "Phải bị giới hạn ở MAX_BACKOFF_MS (60s)");
    }

    @Test
    @DisplayName("Thêm lệnh khi offline: lưu PENDING vào SQLite, số lần thử tăng và next_attempt_time lùi theo hàm mũ")
    void testEnqueueOfflineDispatchStoredAsPending() {
        shouldSendSucceed.set(false); // Offline

        String packetId = outboxService.enqueueDispatch("NODE-VICTIM-1", "Evacuate south", MeshPacket.SEVERITY_CRITICAL);
        assertNotNull(packetId);

        // Trước khi xử lý: PENDING, attempt = 0
        Optional<DispatchRecord> initial = storageService.findDispatch(packetId);
        assertTrue(initial.isPresent());
        assertEquals("PENDING", initial.get().getDeliveryStatus());
        assertEquals(0, initial.get().getAttemptCount());

        long beforeProcess = System.currentTimeMillis();
        // Xử lý đợt gửi đầu tiên
        outboxService.processPendingDispatches();

        assertEquals(1, sendAttempts.get());

        // Sau khi gửi thất bại: vẫn PENDING, attempt = 1, next_attempt_time lùi ~2000ms
        Optional<DispatchRecord> afterAttempt1 = storageService.findDispatch(packetId);
        assertTrue(afterAttempt1.isPresent());
        assertEquals("PENDING", afterAttempt1.get().getDeliveryStatus());
        assertEquals(1, afterAttempt1.get().getAttemptCount());
        assertNotNull(afterAttempt1.get().getNextAttemptTime());
        assertTrue(afterAttempt1.get().getNextAttemptTime() >= beforeProcess + 2000L);
    }

    @Test
    @DisplayName("Gửi thành công chuyển trạng thái sang SENT chờ ACK")
    void testSendSuccessTransitionsToSent() {
        shouldSendSucceed.set(true); // Online

        String packetId = outboxService.enqueueDispatch("NODE-VICTIM-2", "Stay in place", MeshPacket.SEVERITY_HIGH);
        outboxService.processPendingDispatches();

        assertEquals(1, sendAttempts.get());
        Optional<DispatchRecord> record = storageService.findDispatch(packetId);
        assertTrue(record.isPresent());
        assertEquals("SENT", record.get().getDeliveryStatus());
        assertEquals(1, record.get().getAttemptCount());
    }

    @Test
    @DisplayName("Nhận ACK hợp lệ chuyển trạng thái thành ACKED")
    void testValidAckTransitionsToAcked() {
        shouldSendSucceed.set(true);
        String packetId = outboxService.enqueueDispatch("NODE-VICTIM-3", "Rescue team en route", MeshPacket.SEVERITY_CRITICAL);
        outboxService.processPendingDispatches();

        assertEquals("SENT", storageService.findDispatch(packetId).get().getDeliveryStatus());

        // Mô phỏng nhận gói tin ACK từ node nạn nhân gửi về Base Station
        MeshPacket ackPacket = PacketFactory.createAck("NODE-VICTIM-3", packetId, MeshPacket.NODE_BASE_STATION);
        outboxService.handleIncomingAck(ackPacket);

        Optional<DispatchRecord> ackedRecord = storageService.findDispatch(packetId);
        assertTrue(ackedRecord.isPresent());
        assertEquals("ACKED", ackedRecord.get().getDeliveryStatus());
    }

    @Test
    @DisplayName("Nhận ACK lặp lại: trạng thái vẫn là ACKED (Idempotent)")
    void testDuplicateAckIsIdempotent() {
        shouldSendSucceed.set(true);
        String packetId = outboxService.enqueueDispatch("NODE-VICTIM-4", "Help arrives", MeshPacket.SEVERITY_CRITICAL);
        outboxService.processPendingDispatches();
        MeshPacket ackPacket = PacketFactory.createAck("NODE-VICTIM-4", packetId, MeshPacket.NODE_BASE_STATION);

        outboxService.handleIncomingAck(ackPacket);
        assertEquals("ACKED", storageService.findDispatch(packetId).get().getDeliveryStatus());

        // Gửi ACK trùng lặp lần 2 và 3
        outboxService.handleIncomingAck(ackPacket);
        outboxService.handleIncomingAck(ackPacket);

        assertEquals("ACKED", storageService.findDispatch(packetId).get().getDeliveryStatus());
    }

    @Test
    @DisplayName("Chỉ ACK hợp lệ, đúng nguồn/đích/correlation và dispatch SENT mới được persist")
    void rejectsUncorrelatedOrInvalidAcks() {
        String pendingId = outboxService.enqueueDispatch(
                "NODE-PENDING", "Wait", MeshPacket.SEVERITY_HIGH);
        MeshPacket premature = PacketFactory.createAck(
                "NODE-PENDING", pendingId, MeshPacket.NODE_BASE_STATION);
        outboxService.handleIncomingAck(premature);
        assertEquals("PENDING", storageService.findDispatch(pendingId).orElseThrow().getDeliveryStatus());

        shouldSendSucceed.set(true);
        String packetId = outboxService.enqueueDispatch(
                "NODE-EXPECTED", "Proceed", MeshPacket.SEVERITY_CRITICAL);
        outboxService.processPendingDispatches();
        assertEquals("SENT", storageService.findDispatch(packetId).orElseThrow().getDeliveryStatus());

        MeshPacket wrongSource = PacketFactory.createAck(
                "NODE-OTHER", packetId, MeshPacket.NODE_BASE_STATION);
        outboxService.handleIncomingAck(wrongSource);

        MeshPacket wrongDestination = PacketFactory.createAck(
                "NODE-EXPECTED", packetId, "OTHER-BASE");
        outboxService.handleIncomingAck(wrongDestination);

        MeshPacket missingCorrelation = PacketFactory.createAck(
                "NODE-EXPECTED", packetId, MeshPacket.NODE_BASE_STATION);
        missingCorrelation.getPayload().setAckForPacketId(null);
        missingCorrelation.computeAndSetChecksum();
        outboxService.handleIncomingAck(missingCorrelation);

        MeshPacket badChecksum = PacketFactory.createAck(
                "NODE-EXPECTED", packetId, MeshPacket.NODE_BASE_STATION);
        badChecksum.setChecksum("bad-checksum");
        outboxService.handleIncomingAck(badChecksum);

        assertEquals("SENT", storageService.findDispatch(packetId).orElseThrow().getDeliveryStatus());
    }

    @Test
    @DisplayName("Thử lại quá 5 lần không thành công chuyển trạng thái thành FAILED")
    void testMaxRetriesExhaustionSetsFailed() {
        shouldSendSucceed.set(false); // Luôn thất bại

        String packetId = outboxService.enqueueDispatch("NODE-VICTIM-5", "Final alert", MeshPacket.SEVERITY_CRITICAL);

        // Chạy qua 5 lần thử thất bại liên tiếp (mỗi lần gạt next_attempt_time về quá khứ để được quét lại)
        for (int i = 1; i <= 5; i++) {
            // Đặt nextAttemptTime về 0 để mô phỏng thời gian đã trôi qua
            storageService.updateDispatchRetry(packetId, "PENDING", i - 1, 0L);
            outboxService.processPendingDispatches();
        }

        Optional<DispatchRecord> finalRecord = storageService.findDispatch(packetId);
        assertTrue(finalRecord.isPresent());
        assertEquals("FAILED", finalRecord.get().getDeliveryStatus(),
                "Sau 5 lần thất bại, trạng thái phải chuyển thành FAILED");
        assertEquals(5, finalRecord.get().getAttemptCount());

        // Lần quét tiếp theo: không retry lệnh đã FAILED
        int countBefore = sendAttempts.get();
        outboxService.processPendingDispatches();
        assertEquals(countBefore, sendAttempts.get(), "Lệnh FAILED không được retry thêm nữa");
    }

    @Test
    @DisplayName("Khởi động lại service: khôi phục các lệnh dở dang từ SQLite DB")
    void testRestartRestoresPendingWorkFromDatabase() throws Exception {
        shouldSendSucceed.set(false);
        String packetId = outboxService.enqueueDispatch("NODE-RESTART", "Prepare bags", MeshPacket.SEVERITY_HIGH);

        // 1. Gửi thất bại lần 1
        outboxService.processPendingDispatches();
        assertEquals(1, sendAttempts.get());

        // 2. Dừng service cũ và đóng DB
        outboxService.stop();
        dbManager.close();

        // 3. Mở lại DB từ cùng file và tạo OutboxService mới
        DatabaseConfig config = DatabaseConfig.forPath(dbPath);
        DatabaseManager reopenedDb = new DatabaseManager(config);
        reopenedDb.initialize();

        BaseStationStorageService reopenedStorage = new BaseStationStorageService(reopenedDb);
        // Đặt nextAttemptTime về 0 để có thể retry ngay
        reopenedStorage.updateDispatchRetry(packetId, "PENDING", 1, 0L);

        AtomicInteger newAttempts = new AtomicInteger(0);
        DispatchOutboxService restartedService = new DispatchOutboxService(
                reopenedStorage,
                "localhost",
                8002,
                (h, p, pkt) -> {
                    newAttempts.incrementAndGet();
                    return true; // Lần này gửi thành công
                }
        );

        // 4. Chạy xử lý outbox
        restartedService.processPendingDispatches();
        assertEquals(1, newAttempts.get(), "Lệnh còn tồn từ DB phải được gửi");

        Optional<DispatchRecord> restored = reopenedStorage.findDispatch(packetId);
        assertTrue(restored.isPresent());
        assertEquals("SENT", restored.get().getDeliveryStatus(), "Sau khi gửi thành công chuyển sang SENT");
        assertEquals(2, restored.get().getAttemptCount());

        restartedService.stop();
        reopenedDb.close();
    }

    @Test
    @DisplayName("Phase 2.4 Correction: Quét đồng thời an toàn (race-safe, single-consumer), bảo toàn packet ID và checksum")
    void testConcurrentScansRaceSafetyAndPacketPreservation() throws Exception {
        int packetCount = 5;
        List<String> createdPacketIds = new ArrayList<>();
        for (int i = 0; i < packetCount; i++) {
            String pid = outboxService.enqueueDispatch("NODE-CONCURRENT-" + i, "Dispatch " + i, MeshPacket.SEVERITY_HIGH);
            createdPacketIds.add(pid);
        }

        List<MeshPacket> capturedPackets = new CopyOnWriteArrayList<>();
        AtomicInteger totalSendCalls = new AtomicInteger(0);

        // Tạo OutboxService mới dùng cùng storageService nhưng có mock sender ghi nhận packet
        DispatchOutboxService concurrentService = new DispatchOutboxService(
                storageService,
                "localhost",
                8002,
                (h, p, pkt) -> {
                    totalSendCalls.incrementAndGet();
                    capturedPackets.add(pkt);
                    try {
                        Thread.sleep(20); // Mô phỏng độ trễ mạng ngắn
                    } catch (InterruptedException ignored) {}
                    return true;
                }
        );

        int threadCount = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            pool.submit(() -> {
                try {
                    startLatch.await();
                    if (index % 2 == 0) {
                        concurrentService.processPendingDispatches();
                    } else {
                        concurrentService.triggerImmediateScan();
                    }
                } catch (Exception e) {
                    fail("Scan không được ném exception: " + e.getMessage());
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(finishLatch.await(5, TimeUnit.SECONDS), "Tất cả các luồng quét phải hoàn thành");
        pool.shutdown();
        pool.awaitTermination(3, TimeUnit.SECONDS);

        // Đảm bảo không xảy ra race condition làm gửi trùng nhiều lần cho cùng đợt gửi đầu
        assertEquals(packetCount, capturedPackets.size(), "Chỉ đúng " + packetCount + " lệnh được gửi, không bị nhân đôi do race");

        // Kiểm tra Packet ID và Checksum bảo toàn nguyên vẹn
        for (MeshPacket pkt : capturedPackets) {
            assertTrue(createdPacketIds.contains(pkt.getPacketId()), "Packet ID phải khớp với ID đã lưu trong DB");
            assertNotNull(pkt.getChecksum(), "Checksum không được null");
            assertTrue(ChecksumUtil.verifyPacketChecksum(pkt), "Checksum phải hợp lệ theo chuẩn Canonical V1");
        }
    }

    @Test
    @DisplayName("Phase 2.4 Correction: Gửi lần 5 thành công -> giữ trạng thái SENT, không ngay lập tức FAILED hay gửi lại")
    void testFifthSendSucceedsRemainsSentAndWaitsForAck() {
        shouldSendSucceed.set(true);
        String packetId = outboxService.enqueueDispatch("NODE-VICTIM-FINAL", "Final Attempt Alert", MeshPacket.SEVERITY_CRITICAL);

        // Đưa dispatch tới trước lần thử thứ 5 (đã thử 4 lần, nextAttemptTime = quá khứ)
        storageService.updateDispatchRetry(packetId, "PENDING", 4, 0L);

        // Lần thử thứ 5: gửi socket thành công
        long beforeSend = System.currentTimeMillis();
        outboxService.processPendingDispatches();

        assertEquals(1, sendAttempts.get(), "Phải thực hiện đúng 1 lần gửi cho attempt 5");

        Optional<DispatchRecord> rec = storageService.findDispatch(packetId);
        assertTrue(rec.isPresent());
        assertEquals("SENT", rec.get().getDeliveryStatus(), "Sau khi gửi lần 5 thành công, trạng thái phải là SENT (chờ ACK)");
        assertEquals(5, rec.get().getAttemptCount(), "Số lần thử phải là 5");
        assertNotNull(rec.get().getNextAttemptTime(), "next_attempt_time không được null (phải là hạn chót chờ ACK)");
        assertTrue(rec.get().getNextAttemptTime() >= beforeSend + outboxService.getFinalAckTimeoutMs() - 1000L,
                "Hạn chót chờ ACK phải nằm trong tương lai (>= now + finalAckTimeoutMs)");

        // Chạy lại processPendingDispatches ngay lập tức: không được gửi lại hoặc đánh rớt FAILED ngay
        outboxService.processPendingDispatches();
        assertEquals(1, sendAttempts.get(), "Không được gửi lại khi đang trong thời hạn chờ ACK");

        Optional<DispatchRecord> recAfter = storageService.findDispatch(packetId);
        assertTrue(recAfter.isPresent());
        assertEquals("SENT", recAfter.get().getDeliveryStatus(), "Vẫn phải giữ trạng thái SENT");
    }

    @Test
    @DisplayName("Phase 2.4 Correction: Gửi lần 5 thành công -> ACK về trước hạn chót -> chuyển thành ACKED")
    void testFifthSendSucceedsAckArrivesBeforeTimeoutTransitionsToAcked() {
        shouldSendSucceed.set(true);
        String packetId = outboxService.enqueueDispatch("NODE-VICTIM-FINAL-ACK", "Save us", MeshPacket.SEVERITY_CRITICAL);

        // Chuẩn bị ở lần thứ 4
        storageService.updateDispatchRetry(packetId, "PENDING", 4, 0L);
        outboxService.processPendingDispatches();

        assertEquals("SENT", storageService.findDispatch(packetId).get().getDeliveryStatus());
        assertEquals(5, storageService.findDispatch(packetId).get().getAttemptCount());

        // ACK từ node nạn nhân gửi về trước khi hết hạn
        MeshPacket ackPacket = PacketFactory.createAck("NODE-VICTIM-FINAL-ACK", packetId, MeshPacket.NODE_BASE_STATION);
        outboxService.handleIncomingAck(ackPacket);

        Optional<DispatchRecord> rec = storageService.findDispatch(packetId);
        assertTrue(rec.isPresent());
        assertEquals("ACKED", rec.get().getDeliveryStatus(), "Khi ACK đến trước timeout, trạng thái phải chuyển thành ACKED");
    }

    @Test
    @DisplayName("Phase 2.4 Correction: Gửi lần 5 thành công -> Hết hạn chờ ACK mà không có ACK -> chuyển thành FAILED")
    void testFifthSendSucceedsTimeoutExpiresTransitionsToFailed() {
        shouldSendSucceed.set(true);
        String packetId = outboxService.enqueueDispatch("NODE-VICTIM-TIMEOUT", "Time out alert", MeshPacket.SEVERITY_CRITICAL);

        // Đã gửi lần 5 thành công và đang SENT
        storageService.updateDispatchRetry(packetId, "SENT", 5, System.currentTimeMillis() - 1000L); // Giả lập hạn chót đã quá khứ

        // Quét outbox khi hạn chót đã hết
        outboxService.processPendingDispatches();

        Optional<DispatchRecord> rec = storageService.findDispatch(packetId);
        assertTrue(rec.isPresent());
        assertEquals("FAILED", rec.get().getDeliveryStatus(), "Khi quá hạn chờ ACK lần cuối, trạng thái phải chuyển sang FAILED");
        assertEquals(5, rec.get().getAttemptCount());
        assertNull(rec.get().getNextAttemptTime(), "next_attempt_time phải là null khi đã FAILED");
    }

    @Test
    @DisplayName("Phase 2.4 Final Correction: ACK về đồng thời trong lúc sender.send() đang chạy không bị SENT ghi đè")
    void testSynchronousAckDuringSendPreservesAckedState() {
        AtomicBoolean ackInjected = new AtomicBoolean(false);

        DispatchOutboxService fastAckService = new DispatchOutboxService(
                storageService,
                "localhost",
                8002,
                (h, p, pkt) -> {
                    // Mô phỏng ACK về ngay lập tức trước khi sender.send() trả về
                    MeshPacket ackPacket = PacketFactory.createAck(pkt.getDestinationNodeId(), pkt.getPacketId(), MeshPacket.NODE_BASE_STATION);
                    outboxService.handleIncomingAck(ackPacket);
                    ackInjected.set(true);
                    return true;
                }
        );

        String packetId = fastAckService.enqueueDispatch("NODE-FAST-ACK", "Immediate ACK test", MeshPacket.SEVERITY_HIGH);
        fastAckService.processPendingDispatches();

        assertTrue(ackInjected.get(), "ACK phải được inject trong lúc send()");
        Optional<DispatchRecord> rec = storageService.findDispatch(packetId);
        assertTrue(rec.isPresent());
        assertEquals("ACKED", rec.get().getDeliveryStatus(), "Trạng thái cuối cùng trong database bắt buộc phải là ACKED, không bị SENT ghi đè");
        assertNull(rec.get().getNextAttemptTime(), "next_attempt_time phải là null khi đã ACKED");
    }
}

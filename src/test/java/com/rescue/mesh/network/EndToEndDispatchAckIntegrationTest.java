package com.rescue.mesh.network;

import com.rescue.mesh.model.MeshPacket;
import com.rescue.mesh.routing.RoutingEngine;
import com.rescue.mesh.service.DispatchOutboxService;
import com.rescue.mesh.storage.BaseStationStorageService;
import com.rescue.mesh.storage.DatabaseConfig;
import com.rescue.mesh.storage.DatabaseManager;
import com.rescue.mesh.storage.model.DispatchRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kiểm thử tích hợp trọn vẹn End-to-End (E2E) trên Socket TCP thật:
 *
 * Kịch bản:
 *   1. Base Station khởi tạo SQLite database và đưa lệnh DISPATCH vào hàng đợi outbox (PENDING).
 *   2. Base Station quét outbox và gửi lệnh DISPATCH qua TCP Socket tới Relay Node.
 *   3. Relay Node nhận lệnh, cập nhật routing metadata, recompute checksum và chuyển tiếp qua TCP Socket tới Victim Node.
 *   4. Victim Node nhận lệnh hợp lệ gửi đến chính nó, hiển thị lệnh và tự động tạo gói tin ACK chứa
 *      ack_for_packet_id = dispatch.packet_id.
 *   5. Victim Node gửi ACK qua TCP Socket ngược lại Relay Node.
 *   6. Relay Node nhận ACK, phát hiện đích là BASE_STATION, chuyển tiếp ACK qua TCP Socket về Base Station.
 *   7. Base Station nhận ACK qua Socket, RoutingEngine phát hiện mình là đích của ACK và chuyển cho DispatchOutboxService.
 *   8. Bản ghi lệnh điều phối trong cơ sở dữ liệu SQLite chuyển trạng thái thành ACKED.
 *
 * Ràng buộc: Toàn bộ quá trình thực thi trên 3 SocketServer động (port 0), KHÔNG gọi trực tiếp handleIncomingAck().
 */
@DisplayName("Phase 4.5: Bidirectional Desktop Multi-Process Socket Flow")
class EndToEndDispatchAckIntegrationTest {

    @TempDir
    Path tempDir;

    // Base Station components
    private DatabaseManager dbManager;
    private BaseStationStorageService storageService;
    private DispatchOutboxService outboxService;
    private RoutingEngine baseEngine;
    private SocketServer baseServer;
    private TestBaseStationCallback baseCallback;

    // Relay Node components
    private RoutingEngine relayEngine;
    private SocketServer relayServer;
    private TestRelayCallback relayCallback;

    // Victim Node components
    private RoutingEngine victimEngine;
    private SocketServer victimServer;
    private TestVictimCallback victimCallback;

    static class TestBaseStationCallback implements RoutingEngine.RoutingCallback {
        final List<MeshPacket> receivedSosPackets = new CopyOnWriteArrayList<>();
        final List<MeshPacket> receivedAcks = new CopyOnWriteArrayList<>();
        final CountDownLatch sosLatch = new CountDownLatch(1);
        final CountDownLatch ackLatch = new CountDownLatch(1);
        private DispatchOutboxService outboxService;

        void setOutboxService(DispatchOutboxService outboxService) {
            this.outboxService = outboxService;
        }

        @Override
        public void onPacketArrived(MeshPacket packet) {
            receivedSosPackets.add(packet);
            sosLatch.countDown();
        }

        @Override
        public void onPacketRelayed(MeshPacket packet, int nextHop) {}

        @Override
        public void onPacketDropped(String packetId, String reason) {}

        @Override
        public void onForwardError(int nextHop, String errorMessage) {}

        @Override
        public void onDispatchReceived(MeshPacket packet) {}

        @Override
        public void onAckReceived(MeshPacket packet) {
            receivedAcks.add(packet);
            if (outboxService != null) {
                outboxService.handleIncomingAck(packet);
            }
            ackLatch.countDown();
        }
    }

    static class TestRelayCallback implements RoutingEngine.RoutingCallback {
        final List<MeshPacket> relayedPackets = new CopyOnWriteArrayList<>();

        @Override
        public void onPacketArrived(MeshPacket packet) {}

        @Override
        public void onPacketRelayed(MeshPacket packet, int nextHop) {
            relayedPackets.add(packet);
        }

        @Override
        public void onPacketDropped(String packetId, String reason) {}

        @Override
        public void onForwardError(int nextHop, String errorMessage) {}

        @Override
        public void onDispatchReceived(MeshPacket packet) {}

        @Override
        public void onAckReceived(MeshPacket packet) {}
    }

    static class TestVictimCallback implements RoutingEngine.RoutingCallback {
        final List<MeshPacket> receivedDispatches = new CopyOnWriteArrayList<>();
        final CountDownLatch dispatchLatch = new CountDownLatch(1);

        @Override
        public void onPacketArrived(MeshPacket packet) {}

        @Override
        public void onPacketRelayed(MeshPacket packet, int nextHop) {}

        @Override
        public void onPacketDropped(String packetId, String reason) {}

        @Override
        public void onForwardError(int nextHop, String errorMessage) {}

        @Override
        public void onDispatchReceived(MeshPacket packet) {
            receivedDispatches.add(packet);
            dispatchLatch.countDown();
        }

        @Override
        public void onAckReceived(MeshPacket packet) {}
    }

    static class SimpleServerListener implements SocketServer.ServerEventListener {
        final CountDownLatch startedLatch = new CountDownLatch(1);

        @Override
        public void onServerStarted(int port) {
            startedLatch.countDown();
        }

        @Override
        public void onClientConnected(String clientAddress) {}

        @Override
        public void onServerError(String errorMessage) {}

        @Override
        public void onServerStopped() {}
    }

    @BeforeEach
    void setUp() throws Exception {
        // 1. Khởi tạo SQLite Database cho Base Station
        Path dbPath = tempDir.resolve("e2e_basestation.db");
        DatabaseConfig dbConfig = DatabaseConfig.forPath(dbPath);
        dbManager = new DatabaseManager(dbConfig);
        dbManager.initialize();
        storageService = new BaseStationStorageService(dbManager);

        // 2. Khởi tạo Base Station Node trên dynamic port 0
        baseCallback = new TestBaseStationCallback();
        baseEngine = new RoutingEngine(
                MeshPacket.NODE_BASE_STATION,
                "127.0.0.1",
                -1,
                baseCallback
        );
        SimpleServerListener baseListener = new SimpleServerListener();
        baseServer = new SocketServer("127.0.0.1", 0, baseEngine, baseListener);
        baseServer.start();
        assertTrue(baseListener.startedLatch.await(5, TimeUnit.SECONDS), "Base Station server không khởi động kịp");
        int basePort = baseServer.getPort();
        assertTrue(basePort > 0);

        // 3. Khởi tạo Relay Node (trung gian) trên dynamic port 0, nextHopPort trỏ về Base Station
        relayCallback = new TestRelayCallback();
        relayEngine = new RoutingEngine(
                "NODE_B_RELAY",
                "127.0.0.1",
                basePort,
                relayCallback
        );
        SimpleServerListener relayListener = new SimpleServerListener();
        relayServer = new SocketServer("127.0.0.1", 0, relayEngine, relayListener);
        relayServer.start();
        assertTrue(relayListener.startedLatch.await(5, TimeUnit.SECONDS), "Relay server không khởi động kịp");
        int relayPort = relayServer.getPort();
        assertTrue(relayPort > 0);

        // 4. Khởi tạo Victim Node (nạn nhân) trên dynamic port 0, nextHopPort trỏ về Relay Node
        victimCallback = new TestVictimCallback();
        victimEngine = new RoutingEngine(
                "NODE-VICTIM-E2E",
                "127.0.0.1",
                relayPort,
                victimCallback
        );
        SimpleServerListener victimListener = new SimpleServerListener();
        victimServer = new SocketServer("127.0.0.1", 0, victimEngine, victimListener);
        victimServer.start();
        assertTrue(victimListener.startedLatch.await(5, TimeUnit.SECONDS), "Victim server không khởi động kịp");
        int victimPort = victimServer.getPort();
        assertTrue(victimPort > 0);

        // 5. Cấu hình endpoint downstream rõ ràng; không suy luận từ node ID/port nguồn.
        relayEngine.recordRoute("NODE-VICTIM-E2E", "127.0.0.1", victimPort);

        // 6. Khởi tạo DispatchOutboxService tại Base Station hướng tới Relay Node
        outboxService = new DispatchOutboxService(
                storageService,
                "127.0.0.1",
                relayPort,
                MeshPacket.NODE_BASE_STATION,
                (h, p, pkt) -> SocketClient.send(h, p, pkt)
        );
        baseCallback.setOutboxService(outboxService);
    }

    @AfterEach
    void tearDown() {
        if (outboxService != null) {
            outboxService.stop();
        }
        if (victimServer != null) {
            victimServer.stop();
        }
        if (relayServer != null) {
            relayServer.stop();
        }
        if (baseServer != null) {
            baseServer.stop();
        }
        if (victimEngine != null) {
            victimEngine.shutdown();
        }
        if (relayEngine != null) {
            relayEngine.shutdown();
        }
        if (baseEngine != null) {
            baseEngine.shutdown();
        }
        if (dbManager != null) {
            dbManager.close();
        }
    }

    @Test
    @DisplayName("Victim -> Relay -> Base -> Dispatch -> Relay -> Victim -> ACK -> Relay -> Base")
    void testBidirectionalLifecycleOverRealSockets() throws Exception {
        String targetNode = "NODE-VICTIM-E2E";

        // Bước 1: Victim phát SOS thật qua Relay đến Base Station.
        MeshPacket sos = com.rescue.mesh.util.PacketFactory.createSosPacket(
                targetNode, "E2E Victim", MeshPacket.ALERT_MEDICAL,
                "Need extraction", 2, MeshPacket.SEVERITY_CRITICAL,
                15.9738, 108.2515);
        String sosId = sos.getPacketId();
        victimEngine.processPacket(sos);

        assertTrue(baseCallback.sosLatch.await(5, TimeUnit.SECONDS),
                "Base Station không nhận được SOS qua relay");
        assertEquals(1, baseCallback.receivedSosPackets.size());
        MeshPacket receivedSos = baseCallback.receivedSosPackets.get(0);
        assertAll(
                () -> assertEquals(sosId, receivedSos.getPacketId()),
                () -> assertEquals(targetNode, receivedSos.getSourceNodeId()),
                () -> assertEquals(MeshPacket.NODE_BASE_STATION, receivedSos.getDestinationNodeId()),
                () -> assertEquals(2, receivedSos.getHopCount()),
                () -> assertEquals(List.of(targetNode, "NODE_B_RELAY"), receivedSos.getRouteHistory()),
                () -> assertEquals("NODE_B_RELAY", receivedSos.getSenderHopId()),
                () -> assertTrue(receivedSos.verifyChecksum()));

        // Bước 2: Base Station đưa lệnh điều phối vào SQLite Outbox.
        String commandText = "Evacuate northern exit, medical drone arriving";
        String severity = MeshPacket.SEVERITY_CRITICAL;

        String packetId = outboxService.enqueueDispatch(targetNode, commandText, severity);
        assertNotNull(packetId);

        // Kiểm tra trạng thái ban đầu trong SQLite là PENDING
        Optional<DispatchRecord> initialRecord = storageService.findDispatch(packetId);
        assertTrue(initialRecord.isPresent());
        assertEquals("PENDING", initialRecord.get().getDeliveryStatus());
        assertEquals(0, initialRecord.get().getAttemptCount());

        // Bước 3: Kích hoạt gửi lệnh từ Outbox qua mạng Socket
        outboxService.processPendingDispatches();

        // Bước 4: Đợi Victim Node nhận được DISPATCH qua Relay
        assertTrue(victimCallback.dispatchLatch.await(5, TimeUnit.SECONDS),
                "Victim node không nhận được DISPATCH command qua mạng socket");
        assertEquals(1, victimCallback.receivedDispatches.size());
        MeshPacket receivedDispatch = victimCallback.receivedDispatches.get(0);
        assertAll(
                () -> assertEquals(packetId, receivedDispatch.getPacketId()),
                () -> assertEquals(targetNode, receivedDispatch.getDestinationNodeId()),
                () -> assertEquals(commandText, receivedDispatch.getPayload().getMessage()),
                () -> assertEquals(1, receivedDispatch.getHopCount()),
                () -> assertEquals(List.of("NODE_B_RELAY"), receivedDispatch.getRouteHistory()),
                () -> assertEquals("NODE_B_RELAY", receivedDispatch.getSenderHopId()),
                () -> assertTrue(receivedDispatch.verifyChecksum()));

        // Bước 5: Đợi Base Station nhận được gói tin ACK qua mạng socket từ Relay
        assertTrue(baseCallback.ackLatch.await(5, TimeUnit.SECONDS),
                "Base Station không nhận được ACK từ mạng socket sau khi victim tiếp nhận lệnh");
        assertEquals(1, baseCallback.receivedAcks.size());
        MeshPacket receivedAck = baseCallback.receivedAcks.get(0);
        assertNotNull(receivedAck.getPayload());
        assertAll(
                () -> assertEquals(MeshPacket.TYPE_ACK, receivedAck.getPacketType()),
                () -> assertEquals(targetNode, receivedAck.getSourceNodeId()),
                () -> assertEquals(MeshPacket.NODE_BASE_STATION, receivedAck.getDestinationNodeId()),
                () -> assertEquals(packetId, receivedAck.getPayload().getAckForPacketId(),
                        "ack_for_packet_id phải khớp chính xác dispatch packet_id"),
                () -> assertEquals(1, receivedAck.getHopCount()),
                () -> assertEquals(List.of("NODE_B_RELAY"), receivedAck.getRouteHistory()),
                () -> assertEquals("NODE_B_RELAY", receivedAck.getSenderHopId()),
                () -> assertTrue(receivedAck.verifyChecksum()));

        // Bước 6: Kiểm tra SQLite đã được cập nhật thành ACKED.
        Optional<DispatchRecord> finalRecord = storageService.findDispatch(packetId);
        assertTrue(finalRecord.isPresent(), "Bản ghi dispatch phải tồn tại trong database");
        assertEquals("ACKED", finalRecord.get().getDeliveryStatus(),
                "Trạng thái delivery_status trong SQLite phải chuyển thành ACKED");
        assertEquals(1, finalRecord.get().getAttemptCount(), "Số lần thử phải là 1");
    }
}

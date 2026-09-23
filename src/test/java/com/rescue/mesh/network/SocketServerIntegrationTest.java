package com.rescue.mesh.network;

import com.google.gson.Gson;
import com.rescue.mesh.model.MeshPacket;
import com.rescue.mesh.routing.RoutingEngine;
import com.rescue.mesh.util.PacketFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 2.3: Hardened SocketServer Integration Tests")
class SocketServerIntegrationTest {

    private SocketServer server;
    private RoutingEngine routingEngine;
    private TestRoutingCallback routingCallback;
    private TestServerListener serverListener;
    private final Gson gson = new Gson();

    static class TestRoutingCallback implements RoutingEngine.RoutingCallback {
        final List<MeshPacket> arrivedPackets = new CopyOnWriteArrayList<>();
        final List<String> droppedPackets = new CopyOnWriteArrayList<>();
        final List<String> dropReasons = new CopyOnWriteArrayList<>();
        volatile CountDownLatch arriveLatch = new CountDownLatch(1);
        volatile CountDownLatch dropLatch = new CountDownLatch(1);

        @Override
        public void onPacketArrived(MeshPacket packet) {
            arrivedPackets.add(packet);
            arriveLatch.countDown();
        }

        @Override
        public void onPacketRelayed(MeshPacket packet, int nextHop) {}

        @Override
        public void onPacketDropped(String packetId, String reason) {
            droppedPackets.add(packetId);
            dropReasons.add(reason);
            dropLatch.countDown();
        }

        @Override
        public void onForwardError(int nextHop, String errorMessage) {}

        @Override
        public void onDispatchReceived(MeshPacket packet) {}
    }

    static class TestServerListener implements SocketServer.ServerEventListener {
        final List<String> clientConnections = new CopyOnWriteArrayList<>();
        final List<String> serverErrors = new CopyOnWriteArrayList<>();
        volatile int boundPort = -1;
        final CountDownLatch startLatch = new CountDownLatch(1);

        @Override
        public void onServerStarted(int port) {
            boundPort = port;
            startLatch.countDown();
        }

        @Override
        public void onClientConnected(String clientAddress) {
            clientConnections.add(clientAddress);
        }

        @Override
        public void onServerError(String errorMessage) {
            serverErrors.add(errorMessage);
        }

        @Override
        public void onServerStopped() {}
    }

    @BeforeEach
    void setUp() throws Exception {
        routingCallback = new TestRoutingCallback();
        serverListener = new TestServerListener();
        routingEngine = new RoutingEngine(
                MeshPacket.NODE_BASE_STATION,
                "localhost",
                -1,
                routingCallback
        );

        // Khởi động server với dynamic port 0
        server = new SocketServer(0, routingEngine, serverListener);
        server.start();

        assertTrue(serverListener.startLatch.await(5, TimeUnit.SECONDS), "Server không khởi động kịp trong 5s");
        assertTrue(server.isRunning(), "Server phải đang chạy");
        assertTrue(server.getPort() > 0, "Server port phải > 0 khi dùng dynamic port");
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
        if (routingEngine != null) {
            routingEngine.shutdown();
        }
    }

    @Test
    @DisplayName("Gửi packet hợp lệ qua socket và tiếp nhận thành công")
    void testValidPacketReception() throws Exception {
            MeshPacket packet = PacketFactory.createSosPacket(
                    "NODE-01", "Le Van C", "MEDICAL", "Injured leg",
                    1, MeshPacket.SEVERITY_HIGH, 21.01, 105.82
            );
    
            boolean sent = SocketClient.send("localhost", server.getPort(), packet);
            assertTrue(sent, "Gửi packet qua SocketClient phải thành công");
    
            assertTrue(routingCallback.arriveLatch.await(3, TimeUnit.SECONDS), "Packet phải đến RoutingCallback");
            assertEquals(1, routingCallback.arrivedPackets.size());
            assertEquals(packet.getPacketId(), routingCallback.arrivedPackets.get(0).getPacketId());
        }
    
        @Test
        @DisplayName("Gửi packet hợp lệ kép: một lần duy nhất đến được, lần thứ 2 bị DUPLICATE")
        void testDuplicatePacketOverRealSocket() throws Exception {
            // Gửi packet valid thứ nhất
            MeshPacket pkt1 = PacketFactory.createSosPacket(
                    "NODE-DUP1", "User Dup1", "MEDICAL", "Tin nhắn duy nhất",
                    1, MeshPacket.SEVERITY_HIGH, 21.01, 105.82
            );
            boolean sent1 = SocketClient.send("localhost", server.getPort(), pkt1);
            assertTrue(sent1, "Gửi packet thứ nhất phải thành công");
    
            assertTrue(routingCallback.arriveLatch.await(3, TimeUnit.SECONDS),
                    "Packet thứ nhất phải đến RoutingCallback");
    
            // Gửi packet identical thứ nhì - server nên drop là DUPLICATE
            boolean sent2 = SocketClient.send("localhost", server.getPort(), pkt1);
            assertTrue(sent2, "Gửi packet thứ nhất một lần nữa vẫn được chấp nhận TCP nhưng routing nên drop");
    
            assertTrue(routingCallback.dropLatch.await(3, TimeUnit.SECONDS),
                    "Packet thứ nhất phải được đánh dấu DUPLICATE qua callback");
            assertEquals(1, routingCallback.arrivedPackets.size(),
                    "Chỉ 1 packet phải đến callback (không trùng)");
            assertEquals(1, routingCallback.droppedPackets.size(),
                    "Phải có 1 packet bị drop do DUPLICATE");
            assertTrue(routingCallback.dropReasons.get(0).contains("DUPLICATE"),
                    "Lý do drop phải là DUPLICATE, nhận: " + routingCallback.dropReasons.get(0));
    
            // Gói đến một node không phải đích phải bị drop khi TTL không còn
            // lượt forward nào. Giữ lại các drop cũ để kiểm tra đúng callback mới.
            int dropsBeforeExpiredPacket = routingCallback.droppedPackets.size();
            routingCallback.dropLatch = new CountDownLatch(1);
    
            // Gửi TTL=1 packet expired qua socket
            MeshPacket pktExpired = PacketFactory.createSosPacket(
                    "NODE-TTL", "User TTL", "MEDICAL", "Tin hết hạn",
                    1, MeshPacket.SEVERITY_HIGH, 21.10, 105.90
            );
            pktExpired.setTtl(1);
            pktExpired.setDestinationNodeId("NODE-NOT-THIS-SERVER");
            pktExpired.computeAndSetChecksum(new com.google.gson.Gson());
    
            boolean sentExpired = SocketClient.send("localhost", server.getPort(), pktExpired);
            assertTrue(sentExpired, "Gửi packet TTL=1 phải gửi qua socket được");
    
            assertTrue(routingCallback.dropLatch.await(3, TimeUnit.SECONDS),
                    "Packet TTL=1 phải được đánh dấu TTL_EXPIRED qua callback");
            assertEquals(dropsBeforeExpiredPacket + 1, routingCallback.droppedPackets.size(),
                    "Phải có đúng một drop mới do TTL_EXPIRED");
            String expiredDropReason = routingCallback.dropReasons.get(dropsBeforeExpiredPacket);
            assertTrue(expiredDropReason.contains("TTL_EXPIRED"),
                    "Lý do drop phải là TTL_EXPIRED, nhận: " + expiredDropReason);
        }

    @Test
    @DisplayName("Gửi chuỗi JSON dị dạng: server không crash và vẫn phục vụ tiếp")
    void testMalformedJsonHandledSafely() throws Exception {
        // 1. Gửi JSON rác
        try (Socket socket = new Socket("localhost", server.getPort());
             PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)) {
            writer.println("{invalid json content missing braces");
            writer.flush();
        }

        // Đợi một chút để server xử lý frame lỗi
        Thread.sleep(300);
        assertTrue(server.isRunning(), "Server vẫn phải đang chạy sau khi nhận JSON lỗi");

        // 2. Gửi packet hợp lệ ngay sau đó — server vẫn xử lý bình thường
        MeshPacket validPacket = PacketFactory.createSosPacket(
                "NODE-02", "Tran D", "FLOOD_TRAPPED", "Water rising",
                2, MeshPacket.SEVERITY_CRITICAL, 21.05, 105.88
        );
        boolean sent = SocketClient.send("localhost", server.getPort(), validPacket);
        assertTrue(sent);

        assertTrue(routingCallback.arriveLatch.await(3, TimeUnit.SECONDS));
        assertEquals(1, routingCallback.arrivedPackets.size());
        assertEquals(validPacket.getPacketId(), routingCallback.arrivedPackets.get(0).getPacketId());
    }

    @Test
    @DisplayName("Gửi packet sai Checksum: bị RoutingEngine từ chối với CHECKSUM_FAIL")
    void testInvalidChecksumDropped() throws Exception {
        MeshPacket packet = PacketFactory.createSosPacket(
                "NODE-03", "Pham E", "LANDSLIDE", "Road blocked",
                1, MeshPacket.SEVERITY_MEDIUM, 21.10, 105.90
        );

        // Sửa checksum thành checksum sai
        packet.setChecksum("0000000000000000000000000000000000000000000000000000000000000000");

        try (Socket socket = new Socket("localhost", server.getPort());
             PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)) {
            writer.println(packet.toJson(gson));
            writer.flush();
        }

        assertTrue(routingCallback.dropLatch.await(3, TimeUnit.SECONDS), "Packet sai checksum phải bị drop");
        assertEquals(1, routingCallback.droppedPackets.size());
        assertTrue(routingCallback.dropReasons.get(0).contains("CHECKSUM_FAIL"),
                "Lý do drop phải là CHECKSUM_FAIL, nhận: " + routingCallback.dropReasons.get(0));
    }

    @Test
    @DisplayName("Enforce Max Frame Size 64KB: Gói tin > 64KB bị ngắt kết nối an toàn")
    void testOversizedFrameRejected() throws Exception {
        routingCallback.dropLatch = new CountDownLatch(1);

        try (Socket socket = new Socket("localhost", server.getPort())) {
            OutputStream out = socket.getOutputStream();
            // Gửi 70.000 ký tự 'X' không có dấu xuống dòng
            byte[] largeChunk = new byte[70000];
            for (int i = 0; i < largeChunk.length; i++) {
                largeChunk[i] = 'X';
            }
            out.write(largeChunk);
            out.write('\n');
            out.flush();

            // Đọc từ socket xem server đã đóng kết nối chưa
            int read = socket.getInputStream().read();
            assertEquals(-1, read, "Server phải đóng kết nối khi frame > 64KB");
        }

        // Server vẫn sống và nhận được kết nối bình thường sau đó
        assertTrue(server.isRunning(), "Server phải tiếp tục chạy sau khi từ chối frame quá lớn");

        MeshPacket validPacket = PacketFactory.createSosPacket(
                "NODE-NORMAL", "Normal User", "MEDICAL", "Normal",
                1, MeshPacket.SEVERITY_MEDIUM, 21.0, 105.0
        );
        assertTrue(SocketClient.send("localhost", server.getPort(), validPacket));
        assertTrue(routingCallback.arriveLatch.await(3, TimeUnit.SECONDS));
        assertEquals(1, routingCallback.arrivedPackets.size());
    }

    @Test
    @DisplayName("Nhiều client gửi đồng thời (Concurrent clients)")
    void testMultipleConcurrentClients() throws Exception {
        int clientCount = 10;
        CountDownLatch finishLatch = new CountDownLatch(clientCount);
        routingCallback.arriveLatch = new CountDownLatch(clientCount);
        ExecutorService clientPool = Executors.newFixedThreadPool(clientCount);

        for (int i = 0; i < clientCount; i++) {
            final int id = i;
            clientPool.submit(() -> {
                try {
                    MeshPacket packet = PacketFactory.createSosPacket(
                            "NODE-" + id, "User " + id, "MEDICAL", "Concurrent test " + id,
                            1, MeshPacket.SEVERITY_CRITICAL, 21.0 + id * 0.01, 105.0 + id * 0.01
                    );
                    boolean ok = SocketClient.send("localhost", server.getPort(), packet);
                    assertTrue(ok);
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        assertTrue(finishLatch.await(5, TimeUnit.SECONDS), "Tất cả client phải gửi xong");
        assertTrue(routingCallback.arriveLatch.await(5, TimeUnit.SECONDS), "Tất cả 10 packets phải đến server");
        assertEquals(10, routingCallback.arrivedPackets.size());

        clientPool.shutdown();
    }

    @Test
    @DisplayName("Phase 2.3 Correction: Gói tin chứa > 64KB ký tự '\\r' bị từ chối an toàn")
    void testOversizedCarriageReturnsRejected() throws Exception {
        try (Socket socket = new Socket("localhost", server.getPort())) {
            OutputStream out = socket.getOutputStream();
            // Gửi 70.000 ký tự '\r'
            byte[] crChunk = new byte[70000];
            for (int i = 0; i < crChunk.length; i++) {
                crChunk[i] = '\r';
            }
            out.write(crChunk);
            out.write('\n');
            out.flush();

            // Đọc từ socket xem server đã ngắt kết nối chưa (EOF = -1)
            int read = socket.getInputStream().read();
            assertEquals(-1, read, "Server phải ngắt kết nối khi số raw bytes (kể cả \\r) vượt quá 64KB");
        }

        assertTrue(server.isRunning(), "Server phải tiếp tục hoạt động bình thường");
    }

    @Test
    @DisplayName("Phase 2.3 Correction: Client treo kết nối (idle / slowloris) bị ngắt bởi read timeout")
    void testSlowClientReadTimeoutEnforced() throws Exception {
        server.setSocketTimeoutMs(500); // Đặt timeout 500ms để test chạy nhanh

        try (Socket socket = new Socket("localhost", server.getPort())) {
            // Client kết nối nhưng không gửi dữ liệu gì cả (treo)
            long startTime = System.currentTimeMillis();
            int read = socket.getInputStream().read(); // Chờ server đóng kết nối khi timeout
            long elapsed = System.currentTimeMillis() - startTime;

            assertEquals(-1, read, "Server phải đóng kết nối khi client vượt quá read timeout");
            assertTrue(elapsed >= 350, "Thời gian chờ phải xấp xỉ giá trị timeout cấu hình (nhận: " + elapsed + "ms)");
        } finally {
            server.setSocketTimeoutMs(SocketServer.DEFAULT_SOCKET_TIMEOUT_MS);
        }

        assertTrue(server.isRunning(), "Server vẫn phải chạy bình thường sau timeout");
    }

    @Test
    @DisplayName("Phase 2.3 Correction: Quá tải worker pool bị từ chối sạch sẽ mà không làm treo accept loop")
    void testBoundedPoolOverloadRejectionDoesNotHangAcceptLoop() throws Exception {
        TestServerListener smallListener = new TestServerListener();
        TestRoutingCallback smallCallback = new TestRoutingCallback();
        RoutingEngine smallEngine = new RoutingEngine("BASE_STATION", "localhost", -1, smallCallback);

        // Server với 1 worker và queue = 1
        SocketServer smallServer = new SocketServer(0, smallEngine, smallListener, 1, 1, 1);
        smallServer.start();
        assertTrue(smallListener.startLatch.await(5, TimeUnit.SECONDS));

        List<Socket> heldSockets = new ArrayList<>();
        try {
            // Client 1 chiếm worker
            Socket s1 = new Socket("localhost", smallServer.getPort());
            heldSockets.add(s1);
            Thread.sleep(100);

            // Client 2 lấp đầy queue
            Socket s2 = new Socket("localhost", smallServer.getPort());
            heldSockets.add(s2);
            Thread.sleep(100);

            // Client 3 kết nối khi pool và queue đã đầy -> bị reject ngay và đóng kết nối
            try (Socket s3 = new Socket("localhost", smallServer.getPort())) {
                int read = s3.getInputStream().read();
                assertEquals(-1, read, "Client 3 phải bị ngắt kết nối sạch sẽ do quá tải");
            }

            assertTrue(smallServer.isRunning(), "Accept loop của server không được bị crash hoặc treo");

        } finally {
            for (Socket s : heldSockets) {
                try { s.close(); } catch (Exception ignored) {}
            }
            smallServer.stop();
            smallEngine.shutdown();
        }
    }

    @Test
    @DisplayName("Phase 4.1: Explicit bind host is honored and a bind conflict fails clearly")
    void testExplicitBindHostAndPortConflict() throws Exception {
        SocketServer explicit = new SocketServer("127.0.0.1", 0, routingEngine, null);
        explicit.start();
        assertEquals("127.0.0.1", explicit.getBindHost());
        assertTrue(explicit.getPort() > 0);

        SocketServer conflicting = new SocketServer("127.0.0.1", explicit.getPort(), routingEngine, null);
        IllegalStateException error = assertThrows(IllegalStateException.class, conflicting::start);
        assertTrue(error.getMessage().contains("127.0.0.1:" + explicit.getPort()));

        conflicting.stop();
        explicit.stop();
    }

    @Test
    @DisplayName("Graceful Shutdown: Đóng server giải phóng port thành công")
    void testGracefulShutdownReleasesPort() throws Exception {
        int boundPort = server.getPort();
        assertTrue(boundPort > 0);

        // Dừng server
        server.stop();
        assertFalse(server.isRunning(), "Server phải dừng");

        // Xác nhận port đã được giải phóng bằng cách mở ServerSocket mới trên đúng port đó
        try (ServerSocket newSocket = new ServerSocket(boundPort)) {
            assertTrue(newSocket.isBound(), "Port phải được giải phóng và có thể bind lại ngay");
        }
    }

    @Test
    @DisplayName("Defect 2 Regression: 100 chu kỳ start/stop liên tục không rò rỉ port và dừng sạch sẽ")
    void test100CycleStartStopLifecycleStress() throws Exception {
        for (int i = 0; i < 100; i++) {
            SocketServer stressServer = new SocketServer(0, routingEngine, null);
            stressServer.start();
            assertTrue(stressServer.isRunning(), "Chu kỳ " + i + ": server phải running sau start()");
            int port = stressServer.getPort();
            assertTrue(port > 0, "Chu kỳ " + i + ": port phải > 0");

            stressServer.stop();
            assertFalse(stressServer.isRunning(), "Chu kỳ " + i + ": server phải dừng sau stop()");

            // Xác minh port có thể re-bind ngay lập tức mà không bị leak
            try (ServerSocket ss = new ServerSocket()) {
                ss.setReuseAddress(true);
                ss.bind(new java.net.InetSocketAddress(port));
                assertTrue(ss.isBound(), "Chu kỳ " + i + ": port " + port + " phải bind lại được ngay");
            }
        }
    }

    @Test
    @DisplayName("Defect 3 Regression: Quá tải server rồi stop() đóng toàn bộ socket đang chạy và đang chờ trong queue")
    void testServerOverloadAndStopClosesAllAcceptedSockets() throws Exception {
        TestRoutingCallback overloadCb = new TestRoutingCallback();
        RoutingEngine overloadEngine = new RoutingEngine("BASE_STATION", "localhost", -1, overloadCb);
        // Server với 1 worker và queue = 2
        SocketServer overloadServer = new SocketServer(0, overloadEngine, null, 1, 1, 2);
        overloadServer.start();
        int port = overloadServer.getPort();

        List<Socket> clientSockets = new ArrayList<>();
        try {
            // Mở 6 client kết nối đồng thời: 1 worker, 2 queued, 3 rejected
            for (int i = 0; i < 6; i++) {
                try {
                    Socket s = new Socket("localhost", port);
                    clientSockets.add(s);
                } catch (IOException ignored) {}
            }

            // Gọi stop() trong khi server đang xử lý và hàng đợi đang đầy
            overloadServer.stop();
            assertFalse(overloadServer.isRunning());
            assertEquals(0, overloadServer.getActiveClientCount(), "Active client count phải bằng 0 sau khi stop");

            // Mọi client socket phải nhận được EOF (-1) hoặc ném SocketException khi đọc
            for (Socket s : clientSockets) {
                try {
                    s.setSoTimeout(1000);
                    int b = s.getInputStream().read();
                    assertEquals(-1, b, "Client socket phải bị server ngắt kết nối (EOF)");
                } catch (IOException e) {
                    // SocketException / Connection reset là hợp lệ khi server đóng socket
                } finally {
                    try { s.close(); } catch (Exception ignored) {}
                }
            }

            // Port phải bind lại được ngay
            try (ServerSocket ss = new ServerSocket()) {
                ss.setReuseAddress(true);
                ss.bind(new java.net.InetSocketAddress(port));
                assertTrue(ss.isBound());
            }

        } finally {
            overloadServer.stop();
            overloadEngine.shutdown();
        }
    }
}

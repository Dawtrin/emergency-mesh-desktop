package com.rescue.mesh;

import com.google.gson.Gson;
import com.rescue.mesh.model.MeshPacket;
import com.rescue.mesh.routing.LoadBalancer;
import com.rescue.mesh.routing.NodeStatus;
import com.rescue.mesh.routing.RoutingEngine;
import com.rescue.mesh.routing.SeenPacketCache;
import com.rescue.mesh.util.ChecksumUtil;
import com.rescue.mesh.util.PacketFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests toàn diện cho hệ thống Emergency Mesh Rescue (Core Routing & Model).
 */
public class CoreMeshSystemTest {

    private final Gson gson = new Gson();

    // =========================================================
    // 1. CHECKSUM & INTEGRITY TESTS
    // =========================================================

    @Test
    @DisplayName("ChecksumUtil: Tính SHA-256 chính xác và nhất quán")
    void testChecksumCalculation() {
        String data = "Emergency SOS Test Payload";
        String hash1 = ChecksumUtil.compute(data);
        String hash2 = ChecksumUtil.compute(data);

        assertNotNull(hash1);
        assertEquals(64, hash1.length(), "SHA-256 hex string phải có độ dài 64 ký tự");
        assertEquals(hash1, hash2, "Cùng dữ liệu phải sinh ra cùng mã băm");

        assertTrue(ChecksumUtil.verify(data, hash1));
        assertFalse(ChecksumUtil.verify("Tampered Data", hash1));
    }

    // =========================================================
    // 2. MESHPACKET & PACKET FACTORY TESTS
    // =========================================================

    @Test
    @DisplayName("PacketFactory: Tạo SOS Packet hợp lệ và đầy đủ metadata")
    void testCreateSosPacket() {
        MeshPacket packet = PacketFactory.createSosPacket(
                "NODE_A_VICTIM",
                "Nguyen Van A",
                MeshPacket.ALERT_FLOOD,
                "Nuoc ngap tang 1",
                2,
                MeshPacket.SEVERITY_CRITICAL,
                16.0745,
                108.1502
        );

        assertNotNull(packet.getPacketId());
        assertEquals("NODE_A_VICTIM", packet.getSourceNodeId());
        assertEquals(MeshPacket.NODE_BASE_STATION, packet.getDestinationNodeId());
        assertEquals(MeshPacket.TYPE_SOS_DATA, packet.getPacketType());
        assertEquals(5, packet.getTtl());
        assertEquals(0, packet.getHopCount());
        assertNotNull(packet.getRouteHistory());

        // Kiểm tra tính toàn vẹn Checksum
        assertTrue(packet.verifyChecksum(gson), "Checksum của gói tin mới tạo phải hợp lệ");

        // Giả lập sửa đổi dữ liệu trái phép (tampering)
        packet.getPayload().setMessage("Hacker modified payload");
        assertFalse(packet.verifyChecksum(gson), "Sau khi sửa payload, checksum phải không khớp");
    }

    @Test
    @DisplayName("MeshPacket: Tuần tự hóa và giải tuần tự JSON (Serialization / Deserialization)")
    void testJsonSerialization() {
        MeshPacket original = PacketFactory.createHeartbeat("NODE_B_RELAY");
        String json = original.toJson(gson);

        assertNotNull(json);
        assertFalse(json.isEmpty());

        MeshPacket parsed = MeshPacket.fromJson(json, gson);
        assertNotNull(parsed);
        assertEquals(original.getPacketId(), parsed.getPacketId());
        assertEquals(original.getSourceNodeId(), parsed.getSourceNodeId());
        assertEquals(original.getPacketType(), parsed.getPacketType());
        assertEquals(original.getChecksum(), parsed.getChecksum());
        assertTrue(parsed.verifyChecksum(gson));
    }

    // =========================================================
    // 3. SEEN PACKET CACHE (DUPLICATE SUPPRESSION) TESTS
    // =========================================================

    @Test
    @DisplayName("SeenPacketCache: Chống lặp gói tin chuẩn xác (Atomic checkAndMark)")
    void testSeenPacketCache() {
        SeenPacketCache cache = new SeenPacketCache();
        String uuid = "test-uuid-1234";

        assertFalse(cache.contains(uuid));
        assertEquals(0, cache.size());

        // Lần đầu thấy -> checkAndMark trả về false (chưa thấy)
        boolean alreadySeenFirst = cache.checkAndMark(uuid);
        assertFalse(alreadySeenFirst, "Lần đầu tiên checkAndMark phải trả về false");
        assertEquals(1, cache.size());
        assertTrue(cache.contains(uuid));

        // Lần thứ 2 thấy cùng UUID -> checkAndMark trả về true (đã thấy -> DROP)
        boolean alreadySeenSecond = cache.checkAndMark(uuid);
        assertTrue(alreadySeenSecond, "Lần thứ hai checkAndMark phải trả về true (duplicate)");
        assertEquals(1, cache.size());

        cache.clear();
        assertEquals(0, cache.size());
        cache.shutdown();
    }

    // =========================================================
    // 4. ROUTING ENGINE LOGIC TESTS
    // =========================================================

    /** Helper: tạo RoutingCallback no-op (có đủ 6 methods) */
    private RoutingEngine.RoutingCallback createNoopCallback() {
        return new RoutingEngine.RoutingCallback() {
            @Override public void onPacketArrived(MeshPacket packet) {}
            @Override public void onPacketRelayed(MeshPacket packet, int nextHop) {}
            @Override public void onPacketDropped(String packetId, String reason) {}
            @Override public void onForwardError(int nextHop, String errorMessage) {}
            @Override public void onDispatchReceived(MeshPacket packet) {}
            @Override public void onLoadReportReceived(MeshPacket packet) {}
        };
    }

    @Test
    @DisplayName("RoutingEngine: Base Station nhận gói tin đích đến (onPacketArrived)")
    void testRoutingEngineArrivedAtDestination() {
        AtomicBoolean arrived = new AtomicBoolean(false);
        AtomicReference<MeshPacket> receivedPacket = new AtomicReference<>();

        RoutingEngine.RoutingCallback callback = new RoutingEngine.RoutingCallback() {
            @Override
            public void onPacketArrived(MeshPacket packet) {
                arrived.set(true);
                receivedPacket.set(packet);
            }
            @Override public void onPacketRelayed(MeshPacket packet, int nextHop) {}
            @Override public void onPacketDropped(String packetId, String reason) {}
            @Override public void onForwardError(int nextHop, String errorMessage) {}
            @Override public void onDispatchReceived(MeshPacket packet) {}
            @Override public void onLoadReportReceived(MeshPacket packet) {}
        };

        RoutingEngine baseEngine = new RoutingEngine(
                MeshPacket.NODE_BASE_STATION,
                "localhost",
                -1,
                callback
        );

        MeshPacket sos = PacketFactory.createSosPacket(
                "NODE_A", "Victim", MeshPacket.ALERT_MEDICAL,
                "Can cap cuu", 1, MeshPacket.SEVERITY_HIGH, 16.0, 108.0
        );

        baseEngine.processPacket(sos);

        assertTrue(arrived.get(), "Base Station phải kích hoạt callback onPacketArrived");
        assertNotNull(receivedPacket.get());
        assertEquals(sos.getPacketId(), receivedPacket.get().getPacketId());
        assertEquals(1, baseEngine.getCacheSize());
        baseEngine.shutdown();
    }

    @Test
    @DisplayName("RoutingEngine: DROP gói tin trùng lặp (Duplicate Suppression)")
    void testRoutingEngineDropDuplicate() {
        AtomicInteger dropCount = new AtomicInteger(0);
        AtomicReference<String> dropReason = new AtomicReference<>();

        RoutingEngine.RoutingCallback callback = new RoutingEngine.RoutingCallback() {
            @Override public void onPacketArrived(MeshPacket packet) {}
            @Override public void onPacketRelayed(MeshPacket packet, int nextHop) {}
            @Override
            public void onPacketDropped(String packetId, String reason) {
                dropCount.incrementAndGet();
                dropReason.set(reason);
            }
            @Override public void onForwardError(int nextHop, String errorMessage) {}
            @Override public void onDispatchReceived(MeshPacket packet) {}
            @Override public void onLoadReportReceived(MeshPacket packet) {}
        };

        RoutingEngine engine = new RoutingEngine(
                MeshPacket.NODE_BASE_STATION,
                "localhost",
                -1,
                callback
        );

        MeshPacket sos = PacketFactory.createSosPacket(
                "NODE_A", "Victim", MeshPacket.ALERT_FLOOD,
                "Cuu voi", 1, MeshPacket.SEVERITY_CRITICAL, 16.0, 108.0
        );

        // Gửi lần 1 -> Nhận thành công
        engine.processPacket(sos);
        assertEquals(0, dropCount.get());

        // Gửi lại cùng gói tin lần 2 -> Phải bị DROP với lý do DUPLICATE
        engine.processPacket(sos);
        assertEquals(1, dropCount.get(), "Gói tin gửi lần 2 phải bị DROP");
        assertEquals("DUPLICATE", dropReason.get());
        engine.shutdown();
    }

    @Test
    @DisplayName("RoutingEngine: DROP gói tin khi TTL = 0 (TTL Expiry)")
    void testRoutingEngineDropTtlExpired() {
        AtomicBoolean dropped = new AtomicBoolean(false);
        AtomicReference<String> reasonRef = new AtomicReference<>();

        RoutingEngine.RoutingCallback callback = new RoutingEngine.RoutingCallback() {
            @Override public void onPacketArrived(MeshPacket packet) {}
            @Override public void onPacketRelayed(MeshPacket packet, int nextHop) {}
            @Override
            public void onPacketDropped(String packetId, String reason) {
                dropped.set(true);
                reasonRef.set(reason);
            }
            @Override public void onForwardError(int nextHop, String errorMessage) {}
            @Override public void onDispatchReceived(MeshPacket packet) {}
            @Override public void onLoadReportReceived(MeshPacket packet) {}
        };

        RoutingEngine engine = new RoutingEngine(
                "NODE_B_RELAY",
                "localhost",
                8888,
                callback
        );

        MeshPacket sos = PacketFactory.createSosPacket(
                "NODE_A", "Victim", MeshPacket.ALERT_FLOOD,
                "Cuu voi", 1, MeshPacket.SEVERITY_CRITICAL, 16.0, 108.0
        );
        // Ép TTL = 0 và recalculate checksum
        sos.setTtl(0);
        sos.computeAndSetChecksum(gson);

        engine.processPacket(sos);

        assertTrue(dropped.get(), "Gói tin có TTL <= 0 phải bị DROP");
        assertEquals("TTL_EXPIRED", reasonRef.get());
        engine.shutdown();
    }

    // =========================================================
    // 5. LOAD BALANCER TESTS (PHASE 2)
    // =========================================================

    @Test
    @DisplayName("LoadBalancer: Chọn relay node ít tải nhất (Least-Load Algorithm)")
    void testLoadBalancerSelectsLeastLoadNode() {
        LoadBalancer lb = new LoadBalancer();

        // Đăng ký 2 relay với tải khác nhau
        lb.updateStatus("NODE_B1_RELAY", 5, 100, "192.168.1.11", 8002);
        lb.updateStatus("NODE_B2_RELAY", 2, 80, "192.168.1.12", 8003);

        // B2 có tải thấp hơn → phải được chọn
        String best = lb.selectBestRelay();
        assertEquals("NODE_B2_RELAY", best,
                "LoadBalancer phải chọn node B2 vì currentLoad=2 < B1.currentLoad=5");

        // Kiểm tra online count
        assertEquals(2, lb.getOnlineCount(), "Cả 2 node vừa update phải là ONLINE");
    }

    @Test
    @DisplayName("LoadBalancer: Phát hiện node OFFLINE khi quá timeout")
    void testLoadBalancerOfflineDetection() {
        LoadBalancer lb = new LoadBalancer();

        // Đăng ký node B1 và cập nhật heartbeat bình thường
        lb.updateStatus("NODE_B1_RELAY", 3, 50, "192.168.1.11", 8002);
        assertTrue(lb.isNodeOnline("NODE_B1_RELAY"), "Node vừa update phải là ONLINE");

        // Giả lập node B2 quá timeout bằng cách set lastHeartbeat = 20 giây trước
        lb.updateStatus("NODE_B2_RELAY", 1, 30, "192.168.1.12", 8003);
        NodeStatus b2 = lb.getNodeStatus("NODE_B2_RELAY");
        b2.setLastHeartbeat(Instant.now().minusSeconds(20)); // Quá 10s timeout

        assertFalse(lb.isNodeOnline("NODE_B2_RELAY"),
                "Node B2 quá 10s timeout phải bị đánh dấu OFFLINE");

        // Chỉ B1 online → selectBestRelay phải chọn B1
        String best = lb.selectBestRelay();
        assertEquals("NODE_B1_RELAY", best,
                "Khi B2 offline, LoadBalancer phải chọn B1 dù tải cao hơn");
        assertEquals(1, lb.getOnlineCount(), "Chỉ 1 node online");
    }

    @Test
    @DisplayName("PacketFactory: Tạo LOAD_REPORT packet hợp lệ")
    void testLoadReportPacketCreation() {
        MeshPacket loadReport = PacketFactory.createLoadReport(
                "NODE_B1_RELAY", 3, 42, 8002
        );

        assertNotNull(loadReport.getPacketId());
        assertEquals(MeshPacket.TYPE_LOAD_REPORT, loadReport.getPacketType());
        assertEquals("NODE_B1_RELAY", loadReport.getSourceNodeId());
        assertEquals(MeshPacket.NODE_BASE_STATION, loadReport.getDestinationNodeId());
        assertNotNull(loadReport.getPayload());
        assertEquals(3, loadReport.getPayload().getCurrentLoad());
        assertEquals(42, loadReport.getPayload().getProcessedTotal());
        assertEquals(8002, loadReport.getPayload().getListenPort());
        assertTrue(loadReport.verifyChecksum(gson));
    }

    @Test
    @DisplayName("NodeStatus: isAlive() trả đúng theo thời gian heartbeat")
    void testNodeStatusAliveCheck() {
        NodeStatus ns = new NodeStatus("NODE_B1_RELAY", 0, 0, "192.168.1.11", 8002);

        // Vừa tạo → phải alive
        assertTrue(ns.isAlive(10), "Node vừa tạo phải isAlive=true");
        assertTrue(ns.isOnline(), "Node vừa tạo phải isOnline=true");

        // Giả lập 20 giây trước → phải offline
        ns.setLastHeartbeat(Instant.now().minusSeconds(20));
        assertFalse(ns.isAlive(10), "Node 20s ago phải isAlive=false với timeout=10s");
        assertFalse(ns.isOnline(), "Node 20s ago phải isOnline=false");

        // Update → phải online trở lại
        ns.update(1, 5);
        assertTrue(ns.isOnline(), "Sau khi update, node phải online trở lại");
    }

    // =========================================================
    // 6. ROUTING ENGINE — LOAD_REPORT HANDLING (PHASE 2)
    // =========================================================

    @Test
    @DisplayName("RoutingEngine: Xử lý LOAD_REPORT qua callback onLoadReportReceived")
    void testRoutingEngineLoadReportCallback() {
        AtomicBoolean loadReportReceived = new AtomicBoolean(false);
        AtomicReference<MeshPacket> receivedReport = new AtomicReference<>();

        RoutingEngine.RoutingCallback callback = new RoutingEngine.RoutingCallback() {
            @Override public void onPacketArrived(MeshPacket packet) {}
            @Override public void onPacketRelayed(MeshPacket packet, int nextHop) {}
            @Override public void onPacketDropped(String packetId, String reason) {}
            @Override public void onForwardError(int nextHop, String errorMessage) {}
            @Override public void onDispatchReceived(MeshPacket packet) {}
            @Override
            public void onLoadReportReceived(MeshPacket packet) {
                loadReportReceived.set(true);
                receivedReport.set(packet);
            }
        };

        RoutingEngine engine = new RoutingEngine(
                MeshPacket.NODE_BASE_STATION,
                "localhost",
                -1,
                callback
        );

        MeshPacket report = PacketFactory.createLoadReport("NODE_B1_RELAY", 5, 42, 8002);
        engine.processPacket(report);

        assertTrue(loadReportReceived.get(),
                "RoutingEngine phải gọi onLoadReportReceived khi nhận LOAD_REPORT");
        assertNotNull(receivedReport.get());
        assertEquals("NODE_B1_RELAY", receivedReport.get().getSourceNodeId());
        assertEquals(5, receivedReport.get().getPayload().getCurrentLoad());
        engine.shutdown();
    }

    // =========================================================
    // 7. STRICT SOURCE ROUTING & LOAD-BALANCED DISPATCH
    // =========================================================

    @Test
    @DisplayName("Source Routing: designatedRoute xác định chính xác next hop qua từng chặng")
    void testStrictSourceRoutingHeaderAndNextHop() {
        List<String> route = Arrays.asList("BASE_STATION", "NODE_B1_RELAY", "NODE_A_VICTIM");

        MeshPacket dispatch = PacketFactory.createDispatchCommand(
                "NODE_A_VICTIM",
                "Đội cứu hộ đang đến!",
                MeshPacket.SEVERITY_CRITICAL,
                route
        );

        assertNotNull(dispatch.getDesignatedRoute(), "Gói tin phải lưu designatedRoute");
        assertEquals(3, dispatch.getDesignatedRoute().size());

        // Kiểm tra trích xuất next hop từ mỗi chặng
        assertEquals("NODE_B1_RELAY", dispatch.getNextHopFromDesignatedRoute("BASE_STATION"));
        assertEquals("NODE_A_VICTIM", dispatch.getNextHopFromDesignatedRoute("NODE_B1_RELAY"));
        assertNull(dispatch.getNextHopFromDesignatedRoute("NODE_A_VICTIM"), "Đích cuối không có next hop");

        // Kiểm tra tra cứu cổng tương ứng
        assertEquals(8002, RoutingEngine.resolveNodePort("NODE_B1_RELAY"));
        assertEquals(8003, RoutingEngine.resolveNodePort("NODE_B2_RELAY"));
        assertEquals(8001, RoutingEngine.resolveNodePort("NODE_A_VICTIM"));
        assertEquals(8888, RoutingEngine.resolveNodePort("BASE_STATION"));
    }

    @Test
    @DisplayName("LoadBalancer & Failover: Chọn relay rảnh nhất cho lệnh chỉ huy, tự chuyển khi đứt kết nối")
    void testLoadBalancerSelectsRelayForDispatch() {
        LoadBalancer lb = new LoadBalancer();

        // 2 Relay cùng online: B1 đang xử lý 5 gói, B2 rảnh rỗi (1 gói)
        lb.updateStatus("NODE_B1_RELAY", 5, 50, "192.168.1.11", 8002);
        lb.updateStatus("NODE_B2_RELAY", 1, 10, "192.168.1.12", 8003);

        NodeStatus best = lb.selectBestRelayStatus();
        assertNotNull(best, "Phải chọn được relay tốt nhất");
        assertEquals("NODE_B2_RELAY", best.getNodeId(), "B2 có tải 1 < 5 nên phải được chọn");

        // Giả lập sự cố: B2 bị ngắt kết nối (mất tín hiệu quá 15 giây)
        NodeStatus b2Status = lb.getRoutingTable().get("NODE_B2_RELAY");
        b2Status.setLastHeartbeat(Instant.now().minusSeconds(15));
        assertFalse(b2Status.isOnline(), "B2 phải bị đánh dấu OFFLINE");

        // Failover: Hệ thống tự động chuyển sang B1
        NodeStatus failoverBest = lb.selectBestRelayStatus();
        assertNotNull(failoverBest, "Sau sự cố phải failover sang relay còn sống");
        assertEquals("NODE_B1_RELAY", failoverBest.getNodeId(), "Phải tự động chuyển sang B1");
    }
}

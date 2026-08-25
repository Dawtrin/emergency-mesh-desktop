package com.rescue.mesh;

import com.google.gson.Gson;
import com.rescue.mesh.model.MeshPacket;
import com.rescue.mesh.routing.RoutingEngine;
import com.rescue.mesh.routing.SeenPacketCache;
import com.rescue.mesh.util.ChecksumUtil;
import com.rescue.mesh.util.PacketFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
}

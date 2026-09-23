package com.rescue.mesh;

import com.google.gson.Gson;
import com.rescue.mesh.model.MeshPacket;
import com.rescue.mesh.model.PacketValidator;
import com.rescue.mesh.model.ValidationResult;
import com.rescue.mesh.util.ChecksumUtil;
import com.rescue.mesh.util.PacketFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.rescue.mesh.routing.RoutingEngine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kiểm thử tính toàn vẹn Checksum SHA-256: phát hiện can thiệp (tampering) từng trường
 * và xác minh vòng đời chuyển tiếp (Relay lifecycle) cập nhật metadata + tính lại checksum.
 */
public class ChecksumTamperTest {

    private final Gson gson = new Gson();

    @Test
    @DisplayName("Tamper: Sửa payload.message làm hỏng checksum")
    void testTamperPayloadMessage() {
        MeshPacket packet = PacketFactory.createSosPacket(
                "NODE_A", "Victim", "FLOOD_TRAPPED", "Original message", 2, "CRITICAL", 16.0, 108.0
        );
        assertTrue(packet.verifyChecksum(gson));

        // Can thiệp message
        packet.getPayload().setMessage("Tampered malicious message");
        assertFalse(packet.verifyChecksum(gson), "Checksum phải không khớp khi sửa message");
    }

    @Test
    @DisplayName("Tamper: Sửa payload.victim_count làm hỏng checksum")
    void testTamperPayloadVictimCount() {
        MeshPacket packet = PacketFactory.createSosPacket(
                "NODE_A", "Victim", "FLOOD_TRAPPED", "Original message", 2, "CRITICAL", 16.0, 108.0
        );
        assertTrue(packet.verifyChecksum(gson));

        // Can thiệp số nạn nhân
        packet.getPayload().setVictimCount(99);
        assertFalse(packet.verifyChecksum(gson), "Checksum phải không khớp khi sửa victim_count");
    }

    @Test
    @DisplayName("Tamper: Sửa source_node_id làm hỏng checksum")
    void testTamperSourceNodeId() {
        MeshPacket packet = PacketFactory.createSosPacket(
                "NODE_A", "Victim", "FLOOD_TRAPPED", "Original message", 2, "CRITICAL", 16.0, 108.0
        );
        assertTrue(packet.verifyChecksum(gson));

        // Giả mạo source node
        packet.setSourceNodeId("IMPOSTER_NODE");
        assertFalse(packet.verifyChecksum(gson), "Checksum phải bảo vệ source_node_id");
    }

    @Test
    @DisplayName("Tamper: Sửa destination_node_id làm hỏng checksum")
    void testTamperDestinationNodeId() {
        MeshPacket packet = PacketFactory.createSosPacket(
                "NODE_A", "Victim", "FLOOD_TRAPPED", "Original message", 2, "CRITICAL", 16.0, 108.0
        );
        assertTrue(packet.verifyChecksum(gson));

        // Giả mạo đích đến
        packet.setDestinationNodeId("ATTACKER_SINK");
        assertFalse(packet.verifyChecksum(gson), "Checksum phải bảo vệ destination_node_id");
    }

    @Test
    @DisplayName("Tamper: Giảm hoặc tăng TTL mà không tính lại checksum bị từ chối")
    void testTamperTtlWithoutRecalculate() {
        MeshPacket packet = PacketFactory.createSosPacket(
                "NODE_A", "Victim", "FLOOD_TRAPPED", "Original message", 2, "CRITICAL", 16.0, 108.0
        );
        assertTrue(packet.verifyChecksum(gson));

        // Can thiệp TTL mà không tính lại checksum
        packet.setTtl(packet.getTtl() - 1);
        assertFalse(packet.verifyChecksum(gson), "Thay đổi TTL mà không tính lại checksum phải bị từ chối");
    }

    @Test
    @DisplayName("Tamper: Sửa đổi route_history mà không tính lại checksum bị từ chối")
    void testTamperRouteHistoryWithoutRecalculate() {
        MeshPacket packet = PacketFactory.createSosPacket(
                "NODE_A", "Victim", "FLOOD_TRAPPED", "Original message", 2, "CRITICAL", 16.0, 108.0
        );
        assertTrue(packet.verifyChecksum(gson));

        // Thêm node vào route history mà không tính lại checksum
        packet.addToRouteHistory("SPOOFED_RELAY");
        assertFalse(packet.verifyChecksum(gson), "Thay đổi route_history mà không tính lại checksum phải bị từ chối");
    }

    @Test
    @DisplayName("Relay Lifecycle: Relay cập nhật metadata, tính lại checksum và được chấp nhận thành công")
    void testRelayUpdatesMetadataAndRecalculatesChecksum() {
        // 1. Node A (Victim) tạo gói tin SOS ban đầu
        MeshPacket packet = PacketFactory.createSosPacket(
                "NODE_A_VICTIM", "Victim 1", "FLOOD_TRAPPED", "Water rising", 3, "CRITICAL", 16.05, 108.20
        );
        assertEquals(5, packet.getTtl());
        assertEquals(0, packet.getHopCount());
        assertEquals("NODE_A_VICTIM", packet.getSenderHopId());
        assertTrue(packet.verifyChecksum(gson));

        // 2. Node B (Relay) nhận gói tin và xác minh hợp lệ
        assertTrue(packet.verifyChecksum(gson), "Node B phải xác minh được checksum từ Node A");

        // 3. Node B cập nhật routing metadata theo đặc tả:
        packet.setTtl(packet.getTtl() - 1);
        packet.setHopCount(packet.getHopCount() + 1);
        packet.addToRouteHistory("NODE_B_RELAY");
        packet.setSenderHopId("NODE_B_RELAY");

        // Khi chưa tính lại checksum -> không hợp lệ
        assertFalse(packet.verifyChecksum(gson), "Trước khi tính lại checksum, gói tin không hợp lệ");

        // Node B tính lại checksum chuẩn canonical
        packet.computeAndSetChecksum(gson);

        // 4. Base Station nhận từ Node B -> Checksum mới phải hoàn toàn hợp lệ!
        assertTrue(packet.verifyChecksum(gson), "Base Station phải chấp nhận packet sau khi Node B tính lại checksum");
        assertEquals(4, packet.getTtl());
        assertEquals(1, packet.getHopCount());
        assertEquals("NODE_B_RELAY", packet.getSenderHopId());
        assertEquals(1, packet.getRouteHistory().size());
        assertEquals("NODE_B_RELAY", packet.getRouteHistory().get(0));
    }

    @Test
    @DisplayName("ChecksumUtil: So sánh an toàn chống timing attack (MessageDigest.isEqual)")
    void testConstantTimeVerify() {
        String data = "Test packet canonical string";
        String validHash = ChecksumUtil.compute(data);

        assertTrue(ChecksumUtil.verify(data, validHash));
        assertTrue(ChecksumUtil.verify(data, validHash.toUpperCase())); // case-insensitive hex
        assertFalse(ChecksumUtil.verify(data, "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"));
        assertFalse(ChecksumUtil.verify(data, null));
        assertFalse(ChecksumUtil.verify(null, validHash));
    }

    @Test
    @DisplayName("Checksum: Packet v1 dùng payload-only checksum phải bị từ chối")
    void testCanonicalPacketWithPayloadOnlyChecksumFails() {
        MeshPacket packet = PacketFactory.createSosPacket(
                "NODE_A", "Victim", "FLOOD_TRAPPED", "Original message", 2, "CRITICAL", 16.0, 108.0
        );
        // Compute SHA-256 of payload only
        String payloadOnlyChecksum = ChecksumUtil.compute(gson.toJson(packet.getPayload()));
        packet.setChecksum(payloadOnlyChecksum);

        assertFalse(packet.verifyChecksum(gson), "Packet v1 có checksum chỉ băm payload phải bị verifyChecksum từ chối");
    }

    @Test
    @DisplayName("Checksum: Sửa metadata khi có payload-only checksum vẫn phải bị từ chối")
    void testCanonicalPacketWithPayloadOnlyChecksumAndMetadataTamperFails() {
        MeshPacket packet = PacketFactory.createSosPacket(
                "NODE_A", "Victim", "FLOOD_TRAPPED", "Original message", 2, "CRITICAL", 16.0, 108.0
        );
        String payloadOnlyChecksum = ChecksumUtil.compute(gson.toJson(packet.getPayload()));
        packet.setChecksum(payloadOnlyChecksum);

        // Tamper metadata
        packet.setSourceNodeId("EVIL_NODE");
        packet.setTtl(99);

        assertFalse(packet.verifyChecksum(gson), "Không chấp nhận payload-only checksum khi sửa metadata");
    }

    @Test
    @DisplayName("Regression: Loại bỏ delimiter collision giữa source_node_id và destination_node_id")
    void testDelimiterCollisionEliminated() {
        // Packet A: source = "A|B", destination = "C"
        MeshPacket packetA = PacketFactory.createSosPacket(
                "A|B", "Victim", "FLOOD_TRAPPED", "Help", 1, "HIGH", 16.0, 108.0
        );
        packetA.setDestinationNodeId("C");
        packetA.computeAndSetChecksum(gson);

        // Packet B: source = "A", destination = "B|C"
        MeshPacket packetB = PacketFactory.createSosPacket(
                "A", "Victim", "FLOOD_TRAPPED", "Help", 1, "HIGH", 16.0, 108.0
        );
        packetB.setDestinationNodeId("B|C");
        packetB.computeAndSetChecksum(gson);

        // 1. Hai packet phải tạo canonical string khác nhau
        String canonicalA = ChecksumUtil.buildCanonicalString(packetA);
        String canonicalB = ChecksumUtil.buildCanonicalString(packetB);
        assertNotEquals(canonicalA, canonicalB, "Canonical string của Packet A và Packet B phải khác nhau hoàn toàn");

        // 2. Checksum phải khác nhau
        assertNotEquals(packetA.getChecksum(), packetB.getChecksum());

        // 3. Packet B dùng checksum của Packet A phải verifyChecksum() = false
        packetB.setChecksum(packetA.getChecksum());
        assertFalse(packetB.verifyChecksum(gson), "Packet B dùng checksum của Packet A phải bị verifyChecksum từ chối");

        // 4. Cả hai packet vẫn phải qua structural validation nếu node ID hợp lệ theo protocol
        ValidationResult valA = PacketValidator.validate(packetA);
        assertTrue(valA.isValid(), "Packet A phải hợp lệ theo validator: " + valA.getViolations());
        ValidationResult valB = PacketValidator.validate(packetB);
        assertTrue(valB.isValid(), "Packet B phải hợp lệ theo validator: " + valB.getViolations());
    }

    @Test
    @DisplayName("Regression: Hai discovery_info map có cùng dữ liệu nhưng khác insertion order phải tạo canonical string và checksum giống nhau")
    void testDiscoveryInfoMapOrderIndependence() {
        Map<String, Object> map1 = new LinkedHashMap<>();
        map1.put("target", "NODE_Z");
        map1.put("metric", "hop_count");
        map1.put("cost", 2.5);
        map1.put("max_hops", 5);

        Map<String, Object> map2 = new LinkedHashMap<>();
        map2.put("max_hops", 5);
        map2.put("cost", 2.5);
        map2.put("metric", "hop_count");
        map2.put("target", "NODE_Z");

        MeshPacket packet1 = PacketFactory.createRouteDiscovery("NODE_A", "BROADCAST");
        packet1.getPayload().setDiscoveryInfo(map1);
        packet1.computeAndSetChecksum(gson);

        MeshPacket packet2 = PacketFactory.createRouteDiscovery("NODE_A", "BROADCAST");
        packet2.setPacketId(packet1.getPacketId());
        packet2.setTimestamp(packet1.getTimestamp());
        packet2.getPayload().setDiscoveryInfo(map2);
        packet2.computeAndSetChecksum(gson);

        String canonical1 = ChecksumUtil.buildCanonicalString(packet1);
        String canonical2 = ChecksumUtil.buildCanonicalString(packet2);
        assertEquals(canonical1, canonical2, "Cùng dữ liệu khác insertion order phải tạo cùng chuỗi canonical");
        assertEquals(packet1.getChecksum(), packet2.getChecksum(), "Checksum phải hoàn toàn bằng nhau");
        assertTrue(packet2.verifyChecksum(gson));
    }

    @Test
    @DisplayName("Regression: Nested object với key order khác nhau phải cho kết quả canonical giống nhau")
    void testDiscoveryInfoNestedObjectKeyOrderIndependence() {
        Map<String, Object> nested1 = new LinkedHashMap<>();
        nested1.put("z", 100);
        nested1.put("a", 200);

        Map<String, Object> root1 = new LinkedHashMap<>();
        root1.put("sub", nested1);
        root1.put("name", "test");

        Map<String, Object> nested2 = new LinkedHashMap<>();
        nested2.put("a", 200);
        nested2.put("z", 100);

        Map<String, Object> root2 = new LinkedHashMap<>();
        root2.put("name", "test");
        root2.put("sub", nested2);

        String canon1 = ChecksumUtil.canonicalizeValue(root1);
        String canon2 = ChecksumUtil.canonicalizeValue(root2);
        assertEquals(canon1, canon2, "Nested object khác thứ tự keys phải cho cùng kết quả canonical");
        assertEquals("{\"name\":\"test\",\"sub\":{\"a\":200,\"z\":100}}", canon1);
    }

    @Test
    @DisplayName("Regression: Array khác thứ tự phải cho checksum khác nhau")
    void testDiscoveryInfoArrayOrderMatters() {
        Map<String, Object> map1 = new LinkedHashMap<>();
        map1.put("path", List.of("NODE_A", "NODE_B"));

        Map<String, Object> map2 = new LinkedHashMap<>();
        map2.put("path", List.of("NODE_B", "NODE_A"));

        MeshPacket packet1 = PacketFactory.createRouteDiscovery("NODE_A", "BROADCAST");
        packet1.getPayload().setDiscoveryInfo(map1);
        packet1.computeAndSetChecksum(gson);

        MeshPacket packet2 = PacketFactory.createRouteDiscovery("NODE_A", "BROADCAST");
        packet2.setPacketId(packet1.getPacketId());
        packet2.setTimestamp(packet1.getTimestamp());
        packet2.getPayload().setDiscoveryInfo(map2);
        packet2.computeAndSetChecksum(gson);

        assertNotEquals(packet1.getChecksum(), packet2.getChecksum(), "Array khác thứ tự phần tử phải sinh checksum khác nhau");
        assertNotEquals(ChecksumUtil.buildCanonicalString(packet1), ChecksumUtil.buildCanonicalString(packet2));
    }

    @Test
    @DisplayName("Regression: Unsupported value hoặc số NaN/Infinity trong discovery_info phải bị từ chối rõ ràng")
    void testDiscoveryInfoUnsupportedAndNonFiniteValuesRejected() {
        Map<String, Object> nanMap = new LinkedHashMap<>();
        nanMap.put("ratio", Double.NaN);
        assertThrows(IllegalArgumentException.class, () -> ChecksumUtil.canonicalizeValue(nanMap),
                "NaN phải bị từ chối với IllegalArgumentException");

        Map<String, Object> infMap = new LinkedHashMap<>();
        infMap.put("ratio", Double.POSITIVE_INFINITY);
        assertThrows(IllegalArgumentException.class, () -> ChecksumUtil.canonicalizeValue(infMap),
                "Infinity phải bị từ chối với IllegalArgumentException");

        Map<String, Object> unsupportedMap = new LinkedHashMap<>();
        unsupportedMap.put("obj", new Object());
        assertThrows(IllegalArgumentException.class, () -> ChecksumUtil.canonicalizeValue(unsupportedMap),
                "Kiểu Object không hỗ trợ phải bị từ chối với IllegalArgumentException");
    }

    @Test
    @DisplayName("Regression: Integer discovery_info Java object -> JSON -> parse -> verify PASS")
    void testDiscoveryInfoIntegerRoundTrip() {
        MeshPacket packet = PacketFactory.createRouteDiscovery("NODE_A", "BROADCAST");
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("hop_limit", 7);
        info.put("channel", 11);
        info.put("retry_count", 0);
        packet.getPayload().setDiscoveryInfo(info);
        packet.computeAndSetChecksum(gson);

        String json = packet.toJson(gson);
        MeshPacket parsed = MeshPacket.fromJson(json, gson);

        assertNotNull(parsed);
        assertTrue(parsed.verifyChecksum(gson), "Integer discovery_info sau khi serialize và parse lại phải verify PASS");
        assertEquals(packet.getChecksum(), parsed.getChecksum());
    }

    @Test
    @DisplayName("Regression: Nested discovery_info gồm integer, decimal, array, nested object: round-trip giữ nguyên canonical string và checksum")
    void testDiscoveryInfoFullRoundTripPreservesCanonicalStringAndChecksum() {
        MeshPacket packet = PacketFactory.createRouteDiscovery("NODE_A", "BROADCAST");

        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("channel", 11);
        nested.put("name", "RESCUE_MESH");

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("max_hops", 5);
        info.put("link_quality", 0.85);
        info.put("network", nested);
        info.put("relays", List.of("NODE_R1", "NODE_R2"));

        packet.getPayload().setDiscoveryInfo(info);
        String canonBefore = ChecksumUtil.buildCanonicalString(packet);
        packet.computeAndSetChecksum(gson);
        String shaBefore = packet.getChecksum();

        // Round-trip qua JSON
        String json = packet.toJson(gson);
        MeshPacket deserialized = MeshPacket.fromJson(json, gson);

        String canonAfter = ChecksumUtil.buildCanonicalString(deserialized);
        assertEquals(canonBefore, canonAfter, "Canonical string trước và sau round-trip phải hoàn toàn giống hệt");
        assertEquals(shaBefore, deserialized.getChecksum(), "Checksum phải không đổi sau round-trip");
        assertTrue(deserialized.verifyChecksum(gson), "verifyChecksum() sau deserialize phải trả về true");
    }

    @Test
    @DisplayName("Regression: JSON chứa số vượt giới hạn (1e400) không làm sập ứng dụng, validator từ chối hợp lệ")
    void testDiscoveryInfoOverflowNumberHandledGracefully() {
        String overflowJson = "{\n" +
                "  \"packet_id\": \"3d5f7a92-8c1e-4f3b-9a45-67890abcdef1\",\n" +
                "  \"protocol_version\": \"1.0\",\n" +
                "  \"packet_type\": \"ROUTE_DISCOVERY\",\n" +
                "  \"source_node_id\": \"NODE_A\",\n" +
                "  \"destination_node_id\": \"BROADCAST\",\n" +
                "  \"sender_hop_id\": \"NODE_A\",\n" +
                "  \"ttl\": 3,\n" +
                "  \"hop_count\": 0,\n" +
                "  \"timestamp\": 1771240000000,\n" +
                "  \"payload\": {\n" +
                "    \"sender_name\": \"NODE_A\",\n" +
                "    \"alert_type\": \"ROUTE_DISCOVERY\",\n" +
                "    \"message\": \"test\",\n" +
                "    \"victim_count\": 0,\n" +
                "    \"severity\": \"MEDIUM\",\n" +
                "    \"discovery_info\": {\n" +
                "      \"overflow_val\": 1e400\n" +
                "    }\n" +
                "  },\n" +
                "  \"route_history\": [],\n" +
                "  \"checksum\": \"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef\"\n" +
                "}";

        assertDoesNotThrow(() -> {
            MeshPacket packet = MeshPacket.fromJson(overflowJson, gson);
            assertNotNull(packet);

            ValidationResult validation = PacketValidator.validate(packet, packet.getTimestamp());
            assertFalse(validation.isValid(), "Packet chứa số 1e400 phải bị validator từ chối");
            assertTrue(validation.getViolations().stream().anyMatch(v -> v.contains("non-finite or overflow number")));

            // verifyChecksum phòng thủ không ném unchecked exception
            assertFalse(packet.verifyChecksum(gson), "verifyChecksum phải trả về false an toàn khi có số overflow");
        });
    }

    @Test
    @DisplayName("Regression: Unsupported type trong discovery_info khi đi qua RoutingEngine được drop an toàn, không văng runtime exception")
    void testRoutingEngineUnsupportedDiscoveryInfoDoesNotCrash() {
        AtomicBoolean dropped = new AtomicBoolean(false);
        AtomicReference<String> dropReason = new AtomicReference<>("");

        RoutingEngine.RoutingCallback callback = new RoutingEngine.RoutingCallback() {
            @Override public void onPacketArrived(MeshPacket packet) {}
            @Override public void onPacketRelayed(MeshPacket packet, int nextHop) {}
            @Override
            public void onPacketDropped(String packetId, String reason) {
                dropped.set(true);
                dropReason.set(reason);
            }
            @Override public void onForwardError(int nextHop, String errorMessage) {}
            @Override public void onDispatchReceived(MeshPacket packet) {}
        };

        RoutingEngine engine = new RoutingEngine(MeshPacket.NODE_BASE_STATION, "localhost", -1, callback);

        MeshPacket packet = PacketFactory.createRouteDiscovery("NODE_A", "BROADCAST");
        JsonObject badObj = new JsonObject();
        badObj.add("overflow", new com.google.gson.JsonPrimitive(Double.POSITIVE_INFINITY));
        packet.getPayload().setDiscoveryInfo(badObj);
        packet.setChecksum("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");

        assertDoesNotThrow(() -> engine.processPacket(packet),
                "RoutingEngine không được văng runtime exception khi xử lý packet có discovery_info không hợp lệ");
        assertTrue(dropped.get(), "Packet phải bị drop");
        assertTrue(dropReason.get().contains("VALIDATION_FAILED") || dropReason.get().contains("MALFORMED_PACKET"),
                "Lý do drop phải rõ ràng: " + dropReason.get());
    }

    @Test
    @DisplayName("Regression: Large integers beyond 2^53 (9007199254740992 vs 9007199254740993) must not collide")
    void testLargeIntegersBeyondDoublePrecisionDoNotCollide() {
        String json1 = "{\n" +
                "  \"packet_id\": \"3d5f7a92-8c1e-4f3b-9a45-67890abcdef1\",\n" +
                "  \"protocol_version\": \"1.0\",\n" +
                "  \"packet_type\": \"ROUTE_DISCOVERY\",\n" +
                "  \"source_node_id\": \"NODE_A\",\n" +
                "  \"destination_node_id\": \"BROADCAST\",\n" +
                "  \"sender_hop_id\": \"NODE_A\",\n" +
                "  \"ttl\": 3,\n" +
                "  \"hop_count\": 0,\n" +
                "  \"timestamp\": 1771240000000,\n" +
                "  \"payload\": {\n" +
                "    \"sender_name\": \"NODE_A\",\n" +
                "    \"alert_type\": \"ROUTE_DISCOVERY\",\n" +
                "    \"message\": \"test\",\n" +
                "    \"victim_count\": 0,\n" +
                "    \"severity\": \"MEDIUM\",\n" +
                "    \"discovery_info\": {\n" +
                "      \"counter\": 9007199254740992\n" +
                "    }\n" +
                "  },\n" +
                "  \"route_history\": [],\n" +
                "  \"checksum\": \"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef\"\n" +
                "}";

        String json2 = "{\n" +
                "  \"packet_id\": \"3d5f7a92-8c1e-4f3b-9a45-67890abcdef1\",\n" +
                "  \"protocol_version\": \"1.0\",\n" +
                "  \"packet_type\": \"ROUTE_DISCOVERY\",\n" +
                "  \"source_node_id\": \"NODE_A\",\n" +
                "  \"destination_node_id\": \"BROADCAST\",\n" +
                "  \"sender_hop_id\": \"NODE_A\",\n" +
                "  \"ttl\": 3,\n" +
                "  \"hop_count\": 0,\n" +
                "  \"timestamp\": 1771240000000,\n" +
                "  \"payload\": {\n" +
                "    \"sender_name\": \"NODE_A\",\n" +
                "    \"alert_type\": \"ROUTE_DISCOVERY\",\n" +
                "    \"message\": \"test\",\n" +
                "    \"victim_count\": 0,\n" +
                "    \"severity\": \"MEDIUM\",\n" +
                "    \"discovery_info\": {\n" +
                "      \"counter\": 9007199254740993\n" +
                "    }\n" +
                "  },\n" +
                "  \"route_history\": [],\n" +
                "  \"checksum\": \"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef\"\n" +
                "}";

        MeshPacket p1 = MeshPacket.fromJson(json1, gson);
        MeshPacket p2 = MeshPacket.fromJson(json2, gson);

        String canon1 = ChecksumUtil.buildCanonicalString(p1);
        String canon2 = ChecksumUtil.buildCanonicalString(p2);

        assertTrue(canon1.contains("\"counter\":9007199254740992"), "Canonical 1 phải chứa 9007199254740992");
        assertTrue(canon2.contains("\"counter\":9007199254740993"), "Canonical 2 phải chứa 9007199254740993");
        assertNotEquals(canon1, canon2, "Canonical string của 9007199254740992 và 9007199254740993 phải khác nhau");

        String checksum1 = ChecksumUtil.computePacketChecksum(p1);
        String checksum2 = ChecksumUtil.computePacketChecksum(p2);
        assertNotEquals(checksum1, checksum2, "Checksum phải khác nhau");

        // Packet thứ hai dùng checksum packet thứ nhất phải verifyChecksum() = false
        p1.setChecksum(checksum1);
        p2.setChecksum(checksum1);
        assertTrue(p1.verifyChecksum(gson));
        assertFalse(p2.verifyChecksum(gson), "Packet thứ 2 dùng checksum của packet 1 phải verify FAIL");
    }

    @Test
    @DisplayName("Regression: 0.1 và 0.10000000000000001 không được collision")
    void testDecimalsDoNotCollide() {
        String json1 = "{\n" +
                "  \"packet_id\": \"3d5f7a92-8c1e-4f3b-9a45-67890abcdef1\",\n" +
                "  \"protocol_version\": \"1.0\",\n" +
                "  \"packet_type\": \"ROUTE_DISCOVERY\",\n" +
                "  \"source_node_id\": \"NODE_A\",\n" +
                "  \"destination_node_id\": \"BROADCAST\",\n" +
                "  \"sender_hop_id\": \"NODE_A\",\n" +
                "  \"ttl\": 3,\n" +
                "  \"hop_count\": 0,\n" +
                "  \"timestamp\": 1771240000000,\n" +
                "  \"payload\": {\n" +
                "    \"sender_name\": \"NODE_A\",\n" +
                "    \"alert_type\": \"ROUTE_DISCOVERY\",\n" +
                "    \"message\": \"test\",\n" +
                "    \"victim_count\": 0,\n" +
                "    \"severity\": \"MEDIUM\",\n" +
                "    \"discovery_info\": {\n" +
                "      \"val\": 0.1\n" +
                "    }\n" +
                "  },\n" +
                "  \"route_history\": [],\n" +
                "  \"checksum\": \"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef\"\n" +
                "}";

        String json2 = "{\n" +
                "  \"packet_id\": \"3d5f7a92-8c1e-4f3b-9a45-67890abcdef1\",\n" +
                "  \"protocol_version\": \"1.0\",\n" +
                "  \"packet_type\": \"ROUTE_DISCOVERY\",\n" +
                "  \"source_node_id\": \"NODE_A\",\n" +
                "  \"destination_node_id\": \"BROADCAST\",\n" +
                "  \"sender_hop_id\": \"NODE_A\",\n" +
                "  \"ttl\": 3,\n" +
                "  \"hop_count\": 0,\n" +
                "  \"timestamp\": 1771240000000,\n" +
                "  \"payload\": {\n" +
                "    \"sender_name\": \"NODE_A\",\n" +
                "    \"alert_type\": \"ROUTE_DISCOVERY\",\n" +
                "    \"message\": \"test\",\n" +
                "    \"victim_count\": 0,\n" +
                "    \"severity\": \"MEDIUM\",\n" +
                "    \"discovery_info\": {\n" +
                "      \"val\": 0.10000000000000001\n" +
                "    }\n" +
                "  },\n" +
                "  \"route_history\": [],\n" +
                "  \"checksum\": \"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef\"\n" +
                "}";

        MeshPacket p1 = MeshPacket.fromJson(json1, gson);
        MeshPacket p2 = MeshPacket.fromJson(json2, gson);

        String canon1 = ChecksumUtil.buildCanonicalString(p1);
        String canon2 = ChecksumUtil.buildCanonicalString(p2);

        assertTrue(canon1.contains("\"val\":0.1"));
        assertTrue(canon2.contains("\"val\":0.10000000000000001"));
        assertNotEquals(canon1, canon2, "0.1 và 0.10000000000000001 phải cho chuỗi canonical khác nhau");
        assertNotEquals(ChecksumUtil.computePacketChecksum(p1), ChecksumUtil.computePacketChecksum(p2));
    }
}
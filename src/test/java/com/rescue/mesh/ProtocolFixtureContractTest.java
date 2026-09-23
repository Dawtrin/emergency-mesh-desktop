package com.rescue.mesh;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rescue.mesh.model.MeshPacket;
import com.rescue.mesh.model.PacketValidator;
import com.rescue.mesh.model.ValidationResult;
import com.rescue.mesh.util.ChecksumUtil;
import com.rescue.mesh.util.PacketFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract tests cho giao thức MeshPacket canonical v1.0 và fixtures JSON bất biến.
 *
 * Tuyệt đối không sinh động hoặc ghi đè file fixture trong quá trình chạy test.
 * Toàn bộ fixture và known-answer vectors đều là hằng số cố định.
 */
public class ProtocolFixtureContractTest {

    private static final Gson GSON = new Gson();
    private static final Path FIXTURE_DIR = Paths.get("src", "test", "resources", "fixtures", "protocol-v1");

    @ParameterizedTest
    @CsvSource({
            "sos_broadcast.json, cd6cbc65ac85b049082c25193cdf1e15269bea578fa59c6cd2e4d41f02bcc009",
            "dispatch_command.json, d9349c2c98d5718de0740a93f419e504282316b3d41756f9c4ad7a2a839c9488",
            "route_discovery.json, 465cc4eb0ff7453710eba6a70dc8d1edb44ac28c81250f18c7fa0a3a1ba282ad",
            "ack.json, 8dea238130aed9ccb562f80beeb5d7c23c4b824ff3a0f2078ee1e47a96169790",
            "heartbeat.json, 25dd683bb69a31a9cf627d2ff4891d57509b44bad90380aaf75b28f835bdaa34"
    })
    @DisplayName("Fixture Contract: Đọc fixture bất biến, đối chiếu mã băm cố định và xác minh tính toàn vẹn")
    void testFixtureContractAgainstFixedVectors(String fixtureName, String expectedChecksum) throws IOException {
        Path fixturePath = FIXTURE_DIR.resolve(fixtureName);
        assertTrue(Files.exists(fixturePath), "Fixture file bắt buộc phải tồn tại sẵn: " + fixtureName);

        String json = Files.readString(fixturePath, StandardCharsets.UTF_8);
        assertNotNull(json);
        assertFalse(json.trim().isEmpty());

        // 1. Parse JSON -> MeshPacket
        MeshPacket packet = MeshPacket.fromJson(json, GSON);
        assertNotNull(packet, "Phải parse được JSON thành MeshPacket: " + fixtureName);

        // 2. Validate cấu trúc (sử dụng timestamp trong fixture làm mốc tham chiếu hợp lệ)
        ValidationResult validation = PacketValidator.validate(packet, packet.getTimestamp());
        assertTrue(validation.isValid(), "Fixture " + fixtureName + " phải hợp lệ theo validator: " + validation.getViolations());

        // 3. Khớp chính xác với checksum vector cố định
        assertEquals(expectedChecksum, packet.getChecksum(), "Checksum của fixture " + fixtureName + " phải bằng vector cố định");

        // 4. Xác minh tính toàn vẹn canonical SHA-256
        assertTrue(packet.verifyChecksum(), "Checksum của fixture " + fixtureName + " phải verify thành công");

        // 5. Kiểm tra schema serialization: xuất ra chuẩn snake_case
        String reserializedJson = packet.toJson(GSON);
        JsonObject jsonObj = JsonParser.parseString(reserializedJson).getAsJsonObject();
        assertTrue(jsonObj.has("packet_id"));
        assertTrue(jsonObj.has("protocol_version"));
        assertTrue(jsonObj.has("packet_type"));
        assertTrue(jsonObj.has("source_node_id"));
        assertTrue(jsonObj.has("destination_node_id"));
        assertTrue(jsonObj.has("sender_hop_id"));
        assertTrue(jsonObj.has("ttl"));
        assertTrue(jsonObj.has("hop_count"));
        assertTrue(jsonObj.has("timestamp"));
        assertTrue(jsonObj.has("payload"));
        assertTrue(jsonObj.has("route_history"));
        assertTrue(jsonObj.has("checksum"));
    }

    @Test
    @DisplayName("Known-Answer Test (KAT): Input packet cố định phải sinh ra canonical string và SHA-256 hard-coded")
    void testKnownAnswerTestVector() {
        MeshPacket packet = new MeshPacket(MeshPacket.TYPE_SOS_BROADCAST, "ANDROID_VICTIM_01", MeshPacket.NODE_BASE_STATION);
        packet.setPacketId("7b8f9e61-6d2c-4f10-9b34-8c8a2b53b811");
        packet.setSenderHopId("ANDROID_RELAY_02");
        packet.setTtl(4);
        packet.setHopCount(2);
        packet.setTimestamp(1771239999000L);
        packet.setRouteHistory(List.of("ANDROID_VICTIM_01", "ANDROID_RELAY_01"));

        MeshPacket.Payload payload = new MeshPacket.Payload();
        payload.setSenderName("Người dân Đồi Cọ");
        payload.setAlertType(MeshPacket.ALERT_FLOOD);
        payload.setMessage("Nước lũ dâng ngập mái nhà, có 2 trẻ nhỏ cần cứu hộ gấp!");
        payload.setVictimCount(4);
        payload.setSeverity(MeshPacket.SEVERITY_CRITICAL);
        payload.setLocation(new MeshPacket.Location(16.074512, 108.150245, 15.2, 3.5));
        packet.setPayload(payload);

        // Canonical string mong đợi được định nghĩa cứng theo tài liệu docs/protocol/mesh-packet-v1.md
        String expectedCanonicalString =
                "{\"destination_node_id\":\"BASE_STATION\","
                + "\"hop_count\":2,"
                + "\"packet_id\":\"7b8f9e61-6d2c-4f10-9b34-8c8a2b53b811\","
                + "\"packet_type\":\"SOS_BROADCAST\","
                + "\"payload\":{\"alert_type\":\"FLOOD_TRAPPED\",\"location\":{\"accuracy\":3.5,\"altitude\":15.2,\"latitude\":16.074512,\"longitude\":108.150245},\"message\":\"Nước lũ dâng ngập mái nhà, có 2 trẻ nhỏ cần cứu hộ gấp!\",\"sender_name\":\"Người dân Đồi Cọ\",\"severity\":\"CRITICAL\",\"victim_count\":4},"
                + "\"protocol_version\":\"1.0\","
                + "\"route_history\":[\"ANDROID_VICTIM_01\",\"ANDROID_RELAY_01\"],"
                + "\"sender_hop_id\":\"ANDROID_RELAY_02\","
                + "\"source_node_id\":\"ANDROID_VICTIM_01\","
                + "\"timestamp\":1771239999000,"
                + "\"ttl\":4}";

        String actualCanonicalString = ChecksumUtil.buildCanonicalString(packet);
        assertEquals(expectedCanonicalString, actualCanonicalString, "Chuỗi canonical phải khớp chính xác từng ký tự theo quy chuẩn");

        // SHA-256 băm từ chuỗi UTF-8 canonical trên
        String expectedSha256 = "cd6cbc65ac85b049082c25193cdf1e15269bea578fa59c6cd2e4d41f02bcc009";
        String actualSha256 = ChecksumUtil.computePacketChecksum(packet);
        assertEquals(expectedSha256, actualSha256, "Mã băm SHA-256 phải khớp chính xác với vector KAT hard-coded");
    }

    @Test
    @DisplayName("Backward Compatibility & Checksum Policy: Đọc alias cũ nhưng TUYỆT ĐỐI KHÔNG chấp nhận payload-only checksum")
    void testLegacyFormatAndStrictChecksumPolicy() {
        // Gói tin cũ có checksum chỉ tính trên payload JSON
        String legacyPayloadJson = "{\"alertType\":\"FLOOD_TRAPPED\",\"message\":\"Need help\",\"severity\":\"HIGH\",\"victimCount\":1}";
        String legacyPayloadOnlyChecksum = ChecksumUtil.compute(legacyPayloadJson);

        String legacySosJson = "{\n" +
                "  \"packetId\": \"99999999-0000-4000-8000-000000000001\",\n" +
                "  \"packetType\": \"SOS_DATA\",\n" +
                "  \"sourceNodeId\": \"OLD_NODE\",\n" +
                "  \"destinationNodeId\": \"BASE_STATION\",\n" +
                "  \"senderHopId\": \"OLD_NODE\",\n" +
                "  \"ttl\": 5,\n" +
                "  \"hopCount\": 0,\n" +
                "  \"timestamp\": 1700000000000,\n" +
                "  \"payload\": {\n" +
                "    \"senderName\": \"Old Sender\",\n" +
                "    \"alertType\": \"FLOOD_TRAPPED\",\n" +
                "    \"message\": \"Need help\",\n" +
                "    \"victimCount\": 1,\n" +
                "    \"severity\": \"HIGH\",\n" +
                "    \"location\": {\n" +
                "      \"latitude\": 16.05,\n" +
                "      \"longitude\": 108.20\n" +
                "    }\n" +
                "  },\n" +
                "  \"routeHistory\": [],\n" +
                "  \"checksum\": \"" + legacyPayloadOnlyChecksum + "\"\n" +
                "}";

        MeshPacket packet = MeshPacket.fromJson(legacySosJson, GSON);
        assertNotNull(packet);

        // 1. Đọc và chuẩn hóa field/type cũ thành công
        assertEquals(MeshPacket.TYPE_SOS_BROADCAST, packet.getPacketType());
        assertEquals("OLD_NODE", packet.getSourceNodeId());

        // 2. CHÍNH SÁCH BẮT BUỘC: Protocol v1 KHÔNG chấp nhận payload-only checksum -> verifyChecksum phải FAIL
        assertFalse(packet.verifyChecksum(), "Protocol v1 tuyệt đối không chấp nhận payload-only checksum");

        // 3. Sau khi tính lại checksum canonical v1 -> verifyChecksum phải PASS
        packet.computeAndSetChecksum();
        assertTrue(packet.verifyChecksum());

        // 4. Xuất ra luôn luôn là JSON snake_case canonical
        String canonicalJson = packet.toJson(GSON);
        JsonObject obj = JsonParser.parseString(canonicalJson).getAsJsonObject();
        assertTrue(obj.has("packet_id"));
        assertTrue(obj.has("packet_type"));
        assertEquals("SOS_BROADCAST", obj.get("packet_type").getAsString());
        assertFalse(obj.has("packetId"));
    }

    @Test
    @DisplayName("Fixture Contract: Round-trip toàn bộ 5 fixture: parse -> serialize -> parse lại -> validate và verify checksum vẫn PASS")
    void testFullFixtureRoundTripTwice() throws IOException {
        String[] fixtures = {
                "sos_broadcast.json",
                "dispatch_command.json",
                "route_discovery.json",
                "ack.json",
                "heartbeat.json"
        };

        for (String fixtureName : fixtures) {
            Path fixturePath = FIXTURE_DIR.resolve(fixtureName);
            String originalJson = Files.readString(fixturePath, StandardCharsets.UTF_8);

            // Parse lần 1
            MeshPacket packet1 = MeshPacket.fromJson(originalJson, GSON);
            assertNotNull(packet1, "Parse lần 1 thất bại: " + fixtureName);
            assertTrue(packet1.verifyChecksum(), "Verify checksum lần 1 thất bại: " + fixtureName);

            // Serialize ra JSON chuỗi
            String serializedJson = packet1.toJson(GSON);

            // Parse lần 2
            MeshPacket packet2 = MeshPacket.fromJson(serializedJson, GSON);
            assertNotNull(packet2, "Parse lần 2 thất bại: " + fixtureName);

            // Validate lần 2
            ValidationResult validation = PacketValidator.validate(packet2, packet2.getTimestamp());
            assertTrue(validation.isValid(), "Validate lần 2 thất bại cho " + fixtureName + ": " + validation.getViolations());

            // Verify checksum lần 2
            assertTrue(packet2.verifyChecksum(), "Verify checksum lần 2 thất bại cho " + fixtureName);
            assertEquals(packet1.getChecksum(), packet2.getChecksum(), "Checksum phải giữ nguyên qua 2 lần parse");
            assertEquals(ChecksumUtil.buildCanonicalString(packet1), ChecksumUtil.buildCanonicalString(packet2));
        }
    }

    @Test
    @DisplayName("Fixture Contract: route_discovery.json chứa đủ số nguyên, số thực, nested object và array")
    void testRouteDiscoveryFixtureContainsAllRequiredTypes() throws IOException {
        Path fixturePath = FIXTURE_DIR.resolve("route_discovery.json");
        String json = Files.readString(fixturePath, StandardCharsets.UTF_8);

        MeshPacket packet = MeshPacket.fromJson(json, GSON);
        assertNotNull(packet);
        assertNotNull(packet.getPayload().getDiscoveryInfo());

        JsonObject info = packet.getPayload().getDiscoveryInfo().getAsJsonObject();

        // 1. Số thực (real/decimal)
        assertTrue(info.has("link_quality"));
        assertTrue(info.get("link_quality").isJsonPrimitive());
        assertEquals(0.85, info.get("link_quality").getAsDouble(), 0.0001);

        // 2. Số nguyên (integer)
        assertTrue(info.has("max_hops"));
        assertTrue(info.get("max_hops").isJsonPrimitive());
        assertEquals(5, info.get("max_hops").getAsInt());

        // 3. Nested object
        assertTrue(info.has("network"));
        assertTrue(info.get("network").isJsonObject());
        JsonObject net = info.get("network").getAsJsonObject();
        assertEquals(11, net.get("channel").getAsInt());
        assertEquals("RESCUE_MESH", net.get("name").getAsString());

        // 4. Array
        assertTrue(info.has("preferred_relays"));
        assertTrue(info.get("preferred_relays").isJsonArray());
        assertEquals(2, info.get("preferred_relays").getAsJsonArray().size());
        assertEquals("ANDROID_RELAY_01", info.get("preferred_relays").getAsJsonArray().get(0).getAsString());
        assertEquals("ANDROID_RELAY_02", info.get("preferred_relays").getAsJsonArray().get(1).getAsString());
    }
}
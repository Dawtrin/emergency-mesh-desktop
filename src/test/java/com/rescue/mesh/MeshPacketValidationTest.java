package com.rescue.mesh;

import com.google.gson.Gson;
import com.rescue.mesh.model.MeshPacket;
import com.rescue.mesh.model.PacketValidator;
import com.rescue.mesh.model.ValidationResult;
import com.rescue.mesh.util.PacketFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests chuyên sâu cho PacketValidator: kiểm tra các giá trị biên, enum, ràng buộc và xử lý lỗi.
 */
public class MeshPacketValidationTest {

    private final Gson gson = new Gson();

    @Test
    @DisplayName("Validation: Gói tin null bị từ chối")
    void testNullPacket() {
        ValidationResult result = PacketValidator.validate(null);
        assertFalse(result.isValid());
        assertTrue(result.getFirstViolation().contains("null"));
    }

    @Test
    @DisplayName("Validation Review Case: Tái hiện packet lỗi tổng hợp (sender_name rỗng, alert_type lạ, altitude=20000, timestamp=Long.MAX_VALUE, checksum=null)")
    void testReplicatedReviewerInvalidPacket() {
        MeshPacket packet = new MeshPacket(MeshPacket.TYPE_SOS_BROADCAST, "NODE_A", "BASE_STATION");
        packet.setPacketId("7b8f9e61-6d2c-4f10-9b34-8c8a2b53b811");
        packet.setChecksum(null); // checksum null
        packet.setTimestamp(Long.MAX_VALUE); // timestamp quá lớn

        MeshPacket.Payload payload = new MeshPacket.Payload();
        payload.setSenderName(""); // sender_name rỗng
        payload.setAlertType("UNKNOWN_ALERT"); // alert_type không hợp lệ
        payload.setMessage("Help needed");
        payload.setVictimCount(1);
        payload.setSeverity("CRITICAL");
        payload.setLocation(new MeshPacket.Location(16.0, 108.0, 20000.0, 5.0)); // altitude = 20000 (vượt quá 10000m)
        packet.setPayload(payload);

        ValidationResult result = PacketValidator.validate(packet);
        assertFalse(result.isValid(), "Packet tổng hợp lỗi bắt buộc phải INVALID");

        List<String> violations = result.getViolations();
        assertTrue(violations.stream().anyMatch(v -> v.contains("checksum")), "Phải báo lỗi checksum");
        assertTrue(violations.stream().anyMatch(v -> v.contains("sender_name")), "Phải báo lỗi sender_name");
        assertTrue(violations.stream().anyMatch(v -> v.contains("alert_type")), "Phải báo lỗi alert_type");
        assertTrue(violations.stream().anyMatch(v -> v.contains("altitude")), "Phải báo lỗi altitude");
        assertTrue(violations.stream().anyMatch(v -> v.contains("timestamp")), "Phải báo lỗi timestamp");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "not-a-uuid",
            "12345",
            "7b8f9e61-6d2c-4f10-9b34-8c8a2b53b81z",
            ""
    })
    @DisplayName("Validation: packet_id sai định dạng UUID bị từ chối")
    void testInvalidPacketId(String invalidId) {
        MeshPacket packet = PacketFactory.createHeartbeat("NODE_A");
        packet.setPacketId(invalidId);
        ValidationResult result = PacketValidator.validate(packet);
        assertFalse(result.isValid());
        assertTrue(result.getFirstViolation().contains("packet_id"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2.0", "0.9", "v1", "beta"})
    @DisplayName("Validation: protocol_version khác 1.0 bị từ chối")
    void testInvalidProtocolVersion(String badVersion) {
        MeshPacket packet = PacketFactory.createHeartbeat("NODE_A");
        packet.setProtocolVersion(badVersion);
        ValidationResult result = PacketValidator.validate(packet);
        assertFalse(result.isValid());
        assertTrue(result.getFirstViolation().contains("protocol_version"));
    }

    @Test
    @DisplayName("Validation: packet_type không xác định bị từ chối")
    void testUnknownPacketType() {
        MeshPacket packet = PacketFactory.createHeartbeat("NODE_A");
        packet.setPacketType("UNKNOWN_CUSTOM_TYPE");
        ValidationResult result = PacketValidator.validate(packet);
        assertFalse(result.isValid());
        assertTrue(result.getFirstViolation().contains("packet_type"));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 65, 100})
    @DisplayName("Validation: TTL ngoài khoảng [0, 64] bị từ chối")
    void testInvalidTtl(int invalidTtl) {
        MeshPacket packet = PacketFactory.createHeartbeat("NODE_A");
        packet.setTtl(invalidTtl);
        ValidationResult result = PacketValidator.validate(packet);
        assertFalse(result.isValid());
        assertTrue(result.getFirstViolation().contains("ttl"));
    }

    @Test
    @DisplayName("Validation: hop_count âm bị từ chối")
    void testNegativeHopCount() {
        MeshPacket packet = PacketFactory.createHeartbeat("NODE_A");
        packet.setHopCount(-1);
        ValidationResult result = PacketValidator.validate(packet);
        assertFalse(result.isValid());
        assertTrue(result.getFirstViolation().contains("hop_count"));
    }

    @Test
    @DisplayName("Validation: hop_count nhỏ hơn số lượng route_history bị từ chối")
    void testHopCountLessThanRouteHistorySize() {
        MeshPacket packet = PacketFactory.createHeartbeat("NODE_A");
        packet.addToRouteHistory("NODE_A");
        packet.addToRouteHistory("NODE_B");
        packet.setHopCount(1); // 1 < 2
        packet.computeAndSetChecksum();

        ValidationResult result = PacketValidator.validate(packet);
        assertFalse(result.isValid());
        assertTrue(result.getFirstViolation().contains("hop_count"));
    }

    @Test
    @DisplayName("Validation: route_history chứa phần tử rỗng hoặc null bị từ chối")
    void testInvalidRouteHistoryElement() {
        MeshPacket packet = PacketFactory.createHeartbeat("NODE_A");
        packet.addToRouteHistory("   ");
        packet.setHopCount(1);
        packet.computeAndSetChecksum();

        ValidationResult result = PacketValidator.validate(packet);
        assertFalse(result.isValid());
        assertTrue(result.getFirstViolation().contains("route_history"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "invalid_short_hash",
            "4df89d19e64f4e5161565c62cc57c97bf3baecb4009878c4e5ae095ec58f943z", // chứa ký tự 'z'
            "4df89d19e64f4e5161565c62cc57c97bf3baecb4009878c4e5ae095ec58f943"   // 63 ký tự
    })
    @DisplayName("Validation: Checksum không đúng 64 ký tự hex bị từ chối")
    void testInvalidChecksumFormat(String badChecksum) {
        MeshPacket packet = PacketFactory.createHeartbeat("NODE_A");
        packet.setChecksum(badChecksum);
        ValidationResult result = PacketValidator.validate(packet);
        assertFalse(result.isValid());
        assertTrue(result.getFirstViolation().contains("checksum"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    @DisplayName("Validation: sender_name trong SOS rỗng bị từ chối")
    void testEmptySenderNameInSos(String emptyName) {
        MeshPacket packet = PacketFactory.createSosPacket("NODE_A", "Victim", "FLOOD_TRAPPED", "Help", 1, "HIGH", 16.0, 108.0);
        packet.getPayload().setSenderName(emptyName);
        packet.computeAndSetChecksum();

        ValidationResult result = PacketValidator.validate(packet);
        assertFalse(result.isValid());
        assertTrue(result.getFirstViolation().contains("sender_name"));
    }

    @Test
    @DisplayName("Validation: sender_name dài hơn 100 ký tự bị từ chối")
    void testTooLongSenderName() {
        MeshPacket packet = PacketFactory.createSosPacket("NODE_A", "A".repeat(101), "FLOOD_TRAPPED", "Help", 1, "HIGH", 16.0, 108.0);
        ValidationResult result = PacketValidator.validate(packet);
        assertFalse(result.isValid());
        assertTrue(result.getFirstViolation().contains("sender_name"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"FIRE", "EARTHQUAKE", "UNKNOWN_ALERT", ""})
    @DisplayName("Validation: alert_type SOS ngoài 3 giá trị chuẩn bị từ chối")
    void testInvalidSosAlertType(String badAlert) {
        MeshPacket packet = PacketFactory.createSosPacket("NODE_A", "Victim", "FLOOD_TRAPPED", "Help", 1, "HIGH", 16.0, 108.0);
        packet.getPayload().setAlertType(badAlert);
        packet.computeAndSetChecksum();

        ValidationResult result = PacketValidator.validate(packet);
        assertFalse(result.isValid());
        assertTrue(result.getFirstViolation().contains("alert_type"));
    }

    @ParameterizedTest
    @ValueSource(doubles = {-501.0, 10001.0, 20000.0, -1000.0})
    @DisplayName("Validation: Altitude ngoài khoảng [-500.0, 10000.0] bị từ chối")
    void testInvalidAltitudeRange(double badAlt) {
        MeshPacket packet = PacketFactory.createSosPacket("NODE_A", "Victim", "FLOOD_TRAPPED", "Help", 1, "HIGH", 16.0, 108.0, badAlt, 2.0);
        ValidationResult result = PacketValidator.validate(packet);
        assertFalse(result.isValid());
        assertTrue(result.getFirstViolation().contains("altitude"));
    }

    @Test
    @DisplayName("Validation: Tọa độ NaN hoặc Infinity bị từ chối")
    void testNonFiniteCoordinates() {
        MeshPacket pLatNaN = PacketFactory.createSosPacket("NODE_A", "Victim", "FLOOD_TRAPPED", "Help", 1, "HIGH", 16.0, 108.0);
        pLatNaN.getPayload().getLocation().setLatitude(Double.NaN);
        assertFalse(PacketValidator.validate(pLatNaN).isValid());

        MeshPacket pLonInf = PacketFactory.createSosPacket("NODE_A", "Victim", "FLOOD_TRAPPED", "Help", 1, "HIGH", 16.0, 108.0);
        pLonInf.getPayload().getLocation().setLongitude(Double.POSITIVE_INFINITY);
        assertFalse(PacketValidator.validate(pLonInf).isValid());

        MeshPacket pAltNaN = PacketFactory.createSosPacket("NODE_A", "Victim", "FLOOD_TRAPPED", "Help", 1, "HIGH", 16.0, 108.0, 10.0, 2.0);
        pAltNaN.getPayload().getLocation().setAltitude(Double.NaN);
        assertFalse(PacketValidator.validate(pAltNaN).isValid());

        MeshPacket pAccInf = PacketFactory.createSosPacket("NODE_A", "Victim", "FLOOD_TRAPPED", "Help", 1, "HIGH", 16.0, 108.0, 10.0, 2.0);
        pAccInf.getPayload().getLocation().setAccuracy(Double.POSITIVE_INFINITY);
        assertFalse(PacketValidator.validate(pAccInf).isValid());
    }

    @ParameterizedTest
    @ValueSource(doubles = {-91.0, 91.0, -100.0, 180.0})
    @DisplayName("Validation: Vĩ độ latitude ngoài khoảng [-90.0, 90.0] bị từ chối")
    void testInvalidLatitude(double badLat) {
        MeshPacket packet = PacketFactory.createSosPacket("NODE_A", "Victim", "FLOOD_TRAPPED", "Need help", 1, "HIGH", badLat, 108.0);
        ValidationResult result = PacketValidator.validate(packet);
        assertFalse(result.isValid());
        assertTrue(result.getFirstViolation().contains("latitude"));
    }

    @ParameterizedTest
    @ValueSource(doubles = {-181.0, 181.0, -200.0, 360.0})
    @DisplayName("Validation: Kinh độ longitude ngoài khoảng [-180.0, 180.0] bị từ chối")
    void testInvalidLongitude(double badLon) {
        MeshPacket packet = PacketFactory.createSosPacket("NODE_A", "Victim", "FLOOD_TRAPPED", "Need help", 1, "HIGH", 16.0, badLon);
        ValidationResult result = PacketValidator.validate(packet);
        assertFalse(result.isValid());
        assertTrue(result.getFirstViolation().contains("longitude"));
    }

    @Test
    @DisplayName("Validation: Accuracy âm bị từ chối")
    void testNegativeAccuracy() {
        MeshPacket packet = PacketFactory.createSosPacket("NODE_A", "Victim", "FLOOD_TRAPPED", "Need help", 1, "HIGH", 16.0, 108.0, 10.0, -1.5);
        ValidationResult result = PacketValidator.validate(packet);
        assertFalse(result.isValid());
        assertTrue(result.getFirstViolation().contains("accuracy"));
    }

    @Test
    @DisplayName("Validation: Độ dài message vượt quá 500 ký tự bị từ chối")
    void testMessageLengthBoundary() {
        String exactly500 = "A".repeat(500);
        MeshPacket packet500 = PacketFactory.createSosPacket("NODE_A", "Victim", "FLOOD_TRAPPED", exactly500, 1, "CRITICAL", 16.0, 108.0);
        assertTrue(PacketValidator.validate(packet500).isValid(), "500 ký tự phải hợp lệ");

        String over500 = "A".repeat(501);
        MeshPacket packet501 = PacketFactory.createSosPacket("NODE_A", "Victim", "FLOOD_TRAPPED", over500, 1, "CRITICAL", 16.0, 108.0);
        ValidationResult result = PacketValidator.validate(packet501);
        assertFalse(result.isValid(), "501 ký tự phải bị từ chối");
        assertTrue(result.getFirstViolation().contains("message"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"CRITICAL", "HIGH", "MEDIUM"})
    @DisplayName("Validation: Severity hợp lệ được chấp nhận")
    void testValidSeverity(String severity) {
        MeshPacket packet = PacketFactory.createSosPacket("NODE_A", "Victim", "FLOOD_TRAPPED", "Help", 1, severity, 16.0, 108.0);
        assertTrue(PacketValidator.validate(packet).isValid());
    }

    @ParameterizedTest
    @ValueSource(strings = {"LOW", "URGENT", "NORMAL", ""})
    @DisplayName("Validation: Severity không hợp lệ bị từ chối")
    void testInvalidSeverity(String badSeverity) {
        MeshPacket packet = PacketFactory.createSosPacket("NODE_A", "Victim", "FLOOD_TRAPPED", "Help", 1, "HIGH", 16.0, 108.0);
        packet.getPayload().setSeverity(badSeverity);
        ValidationResult result = PacketValidator.validate(packet);
        assertFalse(result.isValid());
        assertTrue(result.getFirstViolation().contains("severity"));
    }

    @Test
    @DisplayName("Validation: ACK thiếu ack_for_packet_id bị từ chối")
    void testAckMissingAckForPacketId() {
        MeshPacket ack = PacketFactory.createAck("NODE_BASE", "7b8f9e61-6d2c-4f10-9b34-8c8a2b53b811", "NODE_A");
        ack.getPayload().setAckForPacketId(null);
        ack.computeAndSetChecksum();
        ValidationResult result = PacketValidator.validate(ack);
        assertFalse(result.isValid());
        assertTrue(result.getFirstViolation().contains("ack_for_packet_id"));
    }

    @Test
    @DisplayName("Validation: victim_count âm bị từ chối")
    void testNegativeVictimCount() {
        MeshPacket packet = PacketFactory.createSosPacket("NODE_A", "Victim", "FLOOD_TRAPPED", "Help", -2, "CRITICAL", 16.0, 108.0);
        ValidationResult result = PacketValidator.validate(packet);
        assertFalse(result.isValid());
        assertTrue(result.getFirstViolation().contains("victim_count"));
    }

    @Test
    @DisplayName("Validation: Timestamp quá xa trong quá khứ hoặc tương lai bị từ chối")
    void testTimestampWindow() {
        long now = 1725444000000L; // mốc cố định
        MeshPacket packet = PacketFactory.createHeartbeat("NODE_A");

        // Quá 365 ngày trong quá khứ
        packet.setTimestamp(now - (366L * 24 * 60 * 60 * 1000L));
        packet.computeAndSetChecksum();
        ValidationResult resPast = PacketValidator.validate(packet, now);
        assertFalse(resPast.isValid());
        assertTrue(resPast.getFirstViolation().contains("past"));

        // Quá 24 giờ trong tương lai
        packet.setTimestamp(now + (25L * 60 * 60 * 1000L));
        packet.computeAndSetChecksum();
        ValidationResult resFuture = PacketValidator.validate(packet, now);
        assertFalse(resFuture.isValid());
        assertTrue(resFuture.getFirstViolation().contains("future"));

        // Trong giới hạn hợp lệ
        packet.setTimestamp(now - (10L * 60 * 1000L)); // 10 phút trước
        packet.computeAndSetChecksum();
        assertTrue(PacketValidator.validate(packet, now).isValid());
    }
}
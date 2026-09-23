package com.rescue.mesh.util;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.rescue.mesh.model.MeshPacket;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Tiện ích tính toán và kiểm tra SHA-256 Checksum cho MeshPacket.
 *
 * Cài đặt thuật toán canonicalization độc lập với thư viện JSON bên ngoài,
 * đảm bảo kết quả chuỗi canonical và mã băm SHA-256 hoàn toàn đồng nhất giữa Java và Kotlin.
 *
 * QUAN TRỌNG VỀ AN NINH:
 * SHA-256 Checksum ở đây CHỈ đóng vai trò kiểm tra tính toàn vẹn dữ liệu (Data Integrity Check),
 * KHÔNG PHẢI LÀ CƠ CHẾ XÁC THỰC DANH TÍNH (AUTHENTICATION) hay chống giả mạo có chủ đích.
 * Canonical protocol v1 tuyệt đối KHÔNG cho phép fallback sang payload-only checksum.
 */
public class ChecksumUtil {

    private static final String ALGORITHM = "SHA-256";

    private ChecksumUtil() {}

    /**
     * Tính SHA-256 checksum của một chuỗi đầu vào theo mã hóa UTF-8.
     *
     * @param data Chuỗi dữ liệu cần tính checksum (không được null)
     * @return Chuỗi hex 64 ký tự chữ thường đại diện cho SHA-256 hash
     * @throws IllegalArgumentException nếu data là null
     */
    public static String compute(String data) {
        if (data == null) {
            throw new IllegalArgumentException("[ChecksumUtil] Dữ liệu đầu vào không được null");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
            byte[] hashBytes = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("[ChecksumUtil] Môi trường JVM không hỗ trợ " + ALGORITHM, e);
        }
    }

    /**
     * Kiểm tra xem chuỗi data có khớp với checksum mong đợi không (constant-time comparison).
     */
    public static boolean verify(String data, String expectedChecksum) {
        if (data == null || expectedChecksum == null) {
            return false;
        }
        String cleanExpected = expectedChecksum.trim().toLowerCase();
        if (cleanExpected.length() != 64) {
            return false;
        }
        String actualChecksum = compute(data);
        return MessageDigest.isEqual(
                actualChecksum.getBytes(StandardCharsets.UTF_8),
                cleanExpected.getBytes(StandardCharsets.UTF_8)
        );
    }

    /**
     * Xây dựng chuỗi canonical chuẩn hóa, độc lập nền tảng Java/Kotlin từ MeshPacket.
     * Sử dụng định dạng Canonical JSON cho TOÀN BỘ gói tin (ngoại trừ trường checksum).
     * Các trường top-level được sắp xếp theo thứ tự bảng chữ cái ASCII nghiêm ngặt:
     * 1. destination_node_id
     * 2. hop_count
     * 3. packet_id
     * 4. packet_type
     * 5. payload
     * 6. protocol_version
     * 7. route_history
     * 8. sender_hop_id
     * 9. source_node_id
     * 10. timestamp
     * 11. ttl
     *
     * @param packet Gói tin MeshPacket
     * @return Chuỗi canonical JSON đại diện cho gói tin
     */
    public static String buildCanonicalString(MeshPacket packet) {
        if (packet == null) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder("{");

        // 1. destination_node_id
        sb.append("\"destination_node_id\":")
          .append(packet.getDestinationNodeId() != null ? "\"" + escapeJson(packet.getDestinationNodeId()) + "\"" : "null")
          .append(",");

        // 2. hop_count
        sb.append("\"hop_count\":").append(packet.getHopCount()).append(",");

        // 3. packet_id
        sb.append("\"packet_id\":")
          .append(packet.getPacketId() != null ? "\"" + escapeJson(packet.getPacketId()) + "\"" : "null")
          .append(",");

        // 4. packet_type
        sb.append("\"packet_type\":")
          .append(packet.getPacketType() != null ? "\"" + escapeJson(packet.getPacketType()) + "\"" : "null")
          .append(",");

        // 5. payload
        sb.append("\"payload\":").append(buildCanonicalPayload(packet.getPayload())).append(",");

        // 6. protocol_version
        String version = packet.getProtocolVersion() != null ? packet.getProtocolVersion() : MeshPacket.PROTOCOL_VERSION_1;
        sb.append("\"protocol_version\":\"").append(escapeJson(version)).append("\",");

        // 7. route_history
        sb.append("\"route_history\":").append(buildCanonicalRouteHistory(packet.getRouteHistory())).append(",");

        // 8. sender_hop_id
        sb.append("\"sender_hop_id\":")
          .append(packet.getSenderHopId() != null ? "\"" + escapeJson(packet.getSenderHopId()) + "\"" : "null")
          .append(",");

        // 9. source_node_id
        sb.append("\"source_node_id\":")
          .append(packet.getSourceNodeId() != null ? "\"" + escapeJson(packet.getSourceNodeId()) + "\"" : "null")
          .append(",");

        // 10. timestamp
        sb.append("\"timestamp\":").append(packet.getTimestamp()).append(",");

        // 11. ttl
        sb.append("\"ttl\":").append(packet.getTtl());

        sb.append("}");
        return sb.toString();
    }

    /**
     * Tương thích với interface cũ nhận Gson: ủy quyền sang buildCanonicalString(packet).
     */
    public static String buildCanonicalString(MeshPacket packet, Gson gson) {
        return buildCanonicalString(packet);
    }

    /**
     * Chuẩn hóa danh sách route_history thành chuỗi JSON array không chứa khoảng trắng dư thừa:
     * Ví dụ: ["NODE_A","NODE_B"] hoặc []
     */
    public static String buildCanonicalRouteHistory(List<String> routeHistory) {
        if (routeHistory == null || routeHistory.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < routeHistory.size(); i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append("\"").append(escapeJson(routeHistory.get(i))).append("\"");
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Chuẩn hóa payload thành chuỗi JSON với các trường không null theo thứ tự bảng chữ cái chính xác:
     * 1. ack_for_packet_id
     * 2. alert_type
     * 3. discovery_info
     * 4. location: {"accuracy":<val>,"altitude":<val>,"latitude":<val>,"longitude":<val>}
     * 5. message
     * 6. sender_name
     * 7. severity
     * 8. victim_count
     */
    public static String buildCanonicalPayload(MeshPacket.Payload payload) {
        if (payload == null) {
            return "{}";
        }

        StringBuilder sb = new StringBuilder("{");
        boolean first = true;

        if (payload.getAckForPacketId() != null) {
            sb.append("\"ack_for_packet_id\":\"").append(escapeJson(payload.getAckForPacketId())).append("\"");
            first = false;
        }

        if (payload.getAlertType() != null) {
            if (!first) sb.append(",");
            sb.append("\"alert_type\":\"").append(escapeJson(payload.getAlertType())).append("\"");
            first = false;
        }

        if (payload.getDiscoveryInfo() != null && !payload.getDiscoveryInfo().isJsonNull()) {
            if (!first) sb.append(",");
            sb.append("\"discovery_info\":").append(canonicalizeValue(payload.getDiscoveryInfo()));
            first = false;
        }

        if (payload.getLocation() != null) {
            if (!first) sb.append(",");
            sb.append("\"location\":{")
              .append("\"accuracy\":").append(formatCanonicalDouble(payload.getLocation().getAccuracy())).append(",")
              .append("\"altitude\":").append(formatCanonicalDouble(payload.getLocation().getAltitude())).append(",")
              .append("\"latitude\":").append(formatCanonicalDouble(payload.getLocation().getLatitude())).append(",")
              .append("\"longitude\":").append(formatCanonicalDouble(payload.getLocation().getLongitude()))
              .append("}");
            first = false;
        }

        if (payload.getMessage() != null) {
            if (!first) sb.append(",");
            sb.append("\"message\":\"").append(escapeJson(payload.getMessage())).append("\"");
            first = false;
        }

        if (payload.getSenderName() != null) {
            if (!first) sb.append(",");
            sb.append("\"sender_name\":\"").append(escapeJson(payload.getSenderName())).append("\"");
            first = false;
        }

        if (payload.getSeverity() != null) {
            if (!first) sb.append(",");
            sb.append("\"severity\":\"").append(escapeJson(payload.getSeverity())).append("\"");
            first = false;
        }

        // victim_count luôn được serialize (integer)
        if (!first) sb.append(",");
        sb.append("\"victim_count\":").append(payload.getVictimCount());

        sb.append("}");
        return sb.toString();
    }

    /**
     * Định dạng số thực double theo chuẩn IEEE 754 độc lập nền tảng.
     * Chuẩn hóa 0.0 và -0.0 thành "0.0".
     */
    public static String formatCanonicalDouble(double val) {
        if (Double.isNaN(val) || Double.isInfinite(val)) {
            throw new IllegalArgumentException("Không thể canonicalize số không hữu hạn (NaN/Infinity): " + val);
        }
        if (val == 0.0 || val == -0.0) {
            return "0.0";
        }
        return Double.toString(val);
    }

    /**
     * Canonicalize một giá trị bất kỳ (object, map, list, primitive) thành chuỗi JSON canonical:
     * - Object keys sort theo ASCII code-point (Unicode code-point).
     * - Array giữ nguyên thứ tự.
     * - String được JSON-escape.
     * - Integer/double được định dạng deterministic.
     * - null và boolean được biểu diễn rõ ràng (null, true, false).
     * - Từ chối NaN, Infinity và các kiểu dữ liệu không hỗ trợ.
     */
    public static String canonicalizeValue(Object val) {
        if (val == null) {
            return "null";
        }
        if (val instanceof Boolean) {
            return val.toString();
        }
        if (val instanceof String || val instanceof Character) {
            return "\"" + escapeJson(val.toString()) + "\"";
        }
        if (val instanceof Number) {
            return formatCanonicalNumber((Number) val);
        }
        if (val instanceof Map<?, ?>) {
            Map<?, ?> map = (Map<?, ?>) val;
            List<String> keys = new ArrayList<>();
            for (Object k : map.keySet()) {
                if (k == null) {
                    throw new IllegalArgumentException("Key trong Map không được null trong canonical JSON");
                }
                keys.add(k.toString());
            }
            Collections.sort(keys); // ASCII/Unicode code-point sort
            StringBuilder sb = new StringBuilder("{");
            for (int i = 0; i < keys.size(); i++) {
                if (i > 0) sb.append(",");
                String k = keys.get(i);
                sb.append("\"").append(escapeJson(k)).append("\":");
                sb.append(canonicalizeValue(map.get(k)));
            }
            sb.append("}");
            return sb.toString();
        }
        if (val instanceof Collection<?>) {
            Collection<?> col = (Collection<?>) val;
            StringBuilder sb = new StringBuilder("[");
            int i = 0;
            for (Object item : col) {
                if (i > 0) sb.append(",");
                sb.append(canonicalizeValue(item));
                i++;
            }
            sb.append("]");
            return sb.toString();
        }
        if (val.getClass().isArray()) {
            int len = java.lang.reflect.Array.getLength(val);
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < len; i++) {
                if (i > 0) sb.append(",");
                sb.append(canonicalizeValue(java.lang.reflect.Array.get(val, i)));
            }
            sb.append("]");
            return sb.toString();
        }
        if (val instanceof JsonElement) {
            return canonicalizeJsonElement((JsonElement) val);
        }
        throw new IllegalArgumentException("Kiểu dữ liệu không được hỗ trợ trong canonical JSON: " + val.getClass().getName());
    }

    /**
     * Xác thực chuỗi số theo chính sách số nghiêm ngặt của protocol v1:
     * - Không chấp nhận chuỗi null, rỗng hoặc khoảng trắng.
     * - Từ chối dứt khoát số không hữu hạn (NaN, Infinity, +Infinity, -Infinity).
     * - Giới hạn độ dài chuỗi ký tự tối đa (100 ký tự) để ngăn chặn DoS.
     * - Parse bằng BigDecimal: từ chối nếu không đúng định dạng số.
     * - Từ chối nếu scale (bậc số mũ) hoặc precision (số chữ số có nghĩa) vượt quá giới hạn an toàn 100.
     */
    public static void validateNumericString(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new IllegalArgumentException("Chuỗi số không được rỗng hoặc null");
        }
        String trimmed = raw.trim();
        String lower = trimmed.toLowerCase();
        if (lower.equals("nan") || lower.contains("infinity")) {
            throw new IllegalArgumentException("payload.discovery_info contains non-finite or overflow number: " + raw);
        }
        if (trimmed.length() > 100) {
            throw new IllegalArgumentException("payload.discovery_info contains non-finite or overflow number: " + raw);
        }

        BigDecimal bd;
        try {
            bd = new BigDecimal(trimmed);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("payload.discovery_info contains invalid number format: " + raw, e);
        }

        if (Math.abs(bd.scale()) > 100 || bd.precision() > 100) {
            throw new IllegalArgumentException("payload.discovery_info contains non-finite or overflow number: " + raw);
        }
    }

    /**
     * Canonicalize chuỗi số chính xác từ chuỗi số JSON bằng BigDecimal:
     * - Xác thực tính an toàn qua validateNumericString.
     * - Chuẩn hóa zero và negative zero (0, -0, 0.0, -0.0, 0.00) thành "0".
     * - Loại bỏ số 0 ở phần thập phân dư thừa qua stripTrailingZeros().
     * - Dùng toPlainString() để không sinh định dạng khoa học e/E không xác định.
     * - Đảm bảo hai giá trị toán học khác nhau không tạo cùng canonical output (chống collision số lớn và số thực).
     */
    public static String canonicalizeNumericString(String raw) {
        validateNumericString(raw);
        BigDecimal bd = new BigDecimal(raw.trim());
        if (bd.compareTo(BigDecimal.ZERO) == 0) {
            return "0";
        }
        BigDecimal stripped = bd.stripTrailingZeros();
        return stripped.toPlainString();
    }

    /**
     * Định dạng số thực double thành chuỗi canonical.
     */
    public static String formatCanonicalNumberValue(double d) {
        if (Double.isNaN(d) || Double.isInfinite(d)) {
            throw new IllegalArgumentException("Không thể canonicalize số không hữu hạn hoặc tràn số (NaN/Infinity/Overflow): " + d);
        }
        return canonicalizeNumericString(Double.toString(d));
    }

    /**
     * Định dạng Number bất kỳ (Integer, Long, Double, Float, BigDecimal) theo giá trị chuỗi thống nhất.
     */
    public static String formatCanonicalNumber(Number num) {
        if (num == null) {
            return "null";
        }
        if (num instanceof Double || num instanceof Float) {
            double d = num.doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                throw new IllegalArgumentException("Không thể canonicalize số không hữu hạn (NaN/Infinity): " + num);
            }
            return canonicalizeNumericString(Double.toString(d));
        }
        if (num instanceof BigDecimal) {
            return canonicalizeNumericString(((BigDecimal) num).toPlainString());
        }
        return canonicalizeNumericString(num.toString());
    }

    private static String canonicalizeJsonElement(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return "null";
        }
        if (element.isJsonPrimitive()) {
            JsonPrimitive prim = element.getAsJsonPrimitive();
            if (prim.isBoolean()) {
                return Boolean.toString(prim.getAsBoolean());
            }
            if (prim.isNumber()) {
                return canonicalizeNumericString(prim.getAsString());
            }
            return "\"" + escapeJson(prim.getAsString()) + "\"";
        }
        if (element.isJsonArray()) {
            JsonArray arr = element.getAsJsonArray();
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < arr.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(canonicalizeJsonElement(arr.get(i)));
            }
            sb.append("]");
            return sb.toString();
        }
        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            List<String> keys = new ArrayList<>(obj.keySet());
            Collections.sort(keys);
            StringBuilder sb = new StringBuilder("{");
            for (int i = 0; i < keys.size(); i++) {
                if (i > 0) sb.append(",");
                String k = keys.get(i);
                sb.append("\"").append(escapeJson(k)).append("\":");
                sb.append(canonicalizeJsonElement(obj.get(k)));
            }
            sb.append("}");
            return sb.toString();
        }
        throw new IllegalArgumentException("Unsupported JsonElement type: " + element.getClass().getName());
    }

    public static JsonElement toJsonElement(Object obj) {
        if (obj == null) {
            return com.google.gson.JsonNull.INSTANCE;
        }
        if (obj instanceof JsonElement) {
            return (JsonElement) obj;
        }
        return new Gson().toJsonTree(obj);
    }

    public static String formatDiscoveryInfo(Object discoveryInfo) {
        return canonicalizeValue(discoveryInfo);
    }

    /**
     * Escape chuỗi cho chuẩn JSON string literal.
     */
    public static String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                    break;
            }
        }
        return sb.toString();
    }

    /**
     * Tính toán checksum SHA-256 canonical cho MeshPacket.
     */
    public static String computePacketChecksum(MeshPacket packet) {
        String canonicalString = buildCanonicalString(packet);
        return compute(canonicalString);
    }

    /**
     * Tương thích với interface cũ nhận Gson.
     */
    public static String computePacketChecksum(MeshPacket packet, Gson gson) {
        return computePacketChecksum(packet);
    }

    /**
     * Xác minh tính toàn vẹn của MeshPacket bằng cách tính lại checksum canonical và so sánh an toàn.
     * TUYỆT ĐỐI KHÔNG FALLBACK SANG PAYLOAD-ONLY CHECKSUM.
     *
     * @param packet Gói tin cần kiểm tra
     * @return true nếu checksum khớp chuẩn canonical v1, false nếu bất kỳ metadata hay payload nào bị sửa
     */
    public static boolean verifyPacketChecksum(MeshPacket packet) {
        if (packet == null || packet.getChecksum() == null) {
            return false;
        }
        String expectedChecksum = packet.getChecksum().trim().toLowerCase();
        if (expectedChecksum.length() != 64) {
            return false;
        }
        try {
            String actualCanonicalChecksum = computePacketChecksum(packet);
            return MessageDigest.isEqual(
                    actualCanonicalChecksum.getBytes(StandardCharsets.UTF_8),
                    expectedChecksum.getBytes(StandardCharsets.UTF_8)
            );
        } catch (RuntimeException e) {
            // Dữ liệu gói tin không thể canonicalize (ví dụ số tràn, NaN/Infinity, unsupported type) -> từ chối an toàn
            return false;
        }
    }

    /**
     * Tương thích với interface cũ nhận Gson.
     */
    public static boolean verifyPacketChecksum(MeshPacket packet, Gson gson) {
        return verifyPacketChecksum(packet);
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder hexBuilder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            hexBuilder.append(String.format("%02x", b));
        }
        return hexBuilder.toString();
    }
}
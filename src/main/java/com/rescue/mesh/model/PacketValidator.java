package com.rescue.mesh.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.rescue.mesh.util.ChecksumUtil;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Bộ kiểm tra tính hợp lệ toàn diện của gói tin MeshPacket trước khi định tuyến hoặc xử lý.
 */
public class PacketValidator {

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
    );

    private static final Pattern HEX_64_PATTERN = Pattern.compile("^[0-9a-fA-F]{64}$");

    private static final Set<String> VALID_TYPES = Set.of(
            MeshPacket.TYPE_SOS_BROADCAST,
            MeshPacket.TYPE_DISPATCH_COMMAND,
            MeshPacket.TYPE_ROUTE_DISCOVERY,
            MeshPacket.TYPE_ACK,
            MeshPacket.TYPE_HEARTBEAT,
            MeshPacket.TYPE_SOS_DATA,
            MeshPacket.TYPE_DISPATCH_CMD
    );

    private static final Set<String> VALID_SEVERITIES = Set.of(
            MeshPacket.SEVERITY_CRITICAL,
            MeshPacket.SEVERITY_HIGH,
            MeshPacket.SEVERITY_MEDIUM
    );

    private static final Set<String> VALID_SOS_ALERT_TYPES = Set.of(
            MeshPacket.ALERT_MEDICAL,
            MeshPacket.ALERT_FLOOD,
            MeshPacket.ALERT_LANDSLIDE
    );

    public static final int MAX_MESSAGE_LENGTH = 500;
    public static final int MAX_NODE_ID_LENGTH = 64;
    public static final int MAX_TTL = 64;

    /** Giới hạn quá khứ tối đa: 365 ngày (milliseconds) */
    public static final long MAX_PAST_TOLERANCE_MS = 365L * 24 * 60 * 60 * 1000L;

    /** Giới hạn tương lai tối đa (bù độ lệch đồng hồ clock skew): 24 giờ (milliseconds) */
    public static final long MAX_FUTURE_TOLERANCE_MS = 24L * 60 * 60 * 1000L;

    private PacketValidator() {}

    /**
     * Xác thực MeshPacket sử dụng thời gian hệ thống hiện tại.
     */
    public static ValidationResult validate(MeshPacket packet) {
        return validate(packet, System.currentTimeMillis());
    }

    /**
     * Xác thực MeshPacket sử dụng Clock truyền vào.
     */
    public static ValidationResult validate(MeshPacket packet, Clock clock) {
        return validate(packet, clock != null ? clock.millis() : System.currentTimeMillis());
    }

    /**
     * Xác thực toàn diện MeshPacket theo đặc tả v1.0 với mốc thời gian tham chiếu xác định (deterministic).
     *
     * @param packet          Gói tin cần kiểm tra
     * @param referenceTimeMs Thời điểm tham chiếu (epoch milliseconds), nếu <= 0 sẽ bỏ qua kiểm tra window thời gian
     * @return ValidationResult chứa kết quả và danh sách vi phạm
     */
    public static ValidationResult validate(MeshPacket packet, long referenceTimeMs) {
        if (packet == null) {
            return ValidationResult.fail("MeshPacket cannot be null");
        }

        List<String> violations = new ArrayList<>();

        // 1. Packet ID
        String packetId = packet.getPacketId();
        if (packetId == null || packetId.trim().isEmpty()) {
            violations.add("packet_id is required");
        } else if (!UUID_PATTERN.matcher(packetId.trim()).matches()) {
            violations.add("packet_id must be a valid UUID format: " + packetId);
        }

        // 2. Protocol Version
        String version = packet.getProtocolVersion();
        if (version == null || version.trim().isEmpty()) {
            violations.add("protocol_version is required");
        } else if (!MeshPacket.PROTOCOL_VERSION_1.equals(version.trim())) {
            violations.add("Unsupported protocol_version: " + version + " (expected " + MeshPacket.PROTOCOL_VERSION_1 + ")");
        }

        // 3. Packet Type
        String type = packet.getPacketType();
        if (type == null || type.trim().isEmpty()) {
            violations.add("packet_type is required");
        } else if (!VALID_TYPES.contains(type.trim())) {
            violations.add("Unknown packet_type: " + type);
        }

        // 4. Node IDs
        validateNodeId("source_node_id", packet.getSourceNodeId(), violations);
        validateNodeId("destination_node_id", packet.getDestinationNodeId(), violations);
        validateNodeId("sender_hop_id", packet.getSenderHopId(), violations);

        // 5. TTL & Hop Count
        if (packet.getTtl() < 0 || packet.getTtl() > MAX_TTL) {
            violations.add("ttl must be between 0 and " + MAX_TTL + ", found: " + packet.getTtl());
        }
        if (packet.getHopCount() < 0) {
            violations.add("hop_count cannot be negative, found: " + packet.getHopCount());
        }

        // 6. Route History
        List<String> routeHistory = packet.getRouteHistory();
        if (routeHistory == null) {
            violations.add("route_history cannot be null");
        } else {
            for (int i = 0; i < routeHistory.size(); i++) {
                String hop = routeHistory.get(i);
                if (hop == null || hop.trim().isEmpty()) {
                    violations.add("route_history[" + i + "] cannot be null or blank");
                } else if (hop.length() > MAX_NODE_ID_LENGTH) {
                    violations.add("route_history[" + i + "] exceeds maximum length of " + MAX_NODE_ID_LENGTH + " characters");
                }
            }
            if (packet.getHopCount() >= 0 && packet.getHopCount() < routeHistory.size()) {
                violations.add("hop_count (" + packet.getHopCount() + ") cannot be less than route_history size (" + routeHistory.size() + ")");
            }
        }

        // 7. Timestamp window check
        long ts = packet.getTimestamp();
        if (ts <= 0) {
            violations.add("timestamp must be a positive epoch millisecond value: " + ts);
        } else if (referenceTimeMs > 0) {
            if (referenceTimeMs - ts > MAX_PAST_TOLERANCE_MS) {
                violations.add("timestamp is too far in the past: " + ts + " (ref: " + referenceTimeMs + ")");
            } else if (ts - referenceTimeMs > MAX_FUTURE_TOLERANCE_MS) {
                violations.add("timestamp is too far in the future: " + ts + " (ref: " + referenceTimeMs + ")");
            }
        }

        // 8. Checksum format (bắt buộc, đúng 64 ký tự hex)
        String checksum = packet.getChecksum();
        if (checksum == null || checksum.trim().isEmpty()) {
            violations.add("checksum is required and must be exactly 64 hexadecimal characters");
        } else if (!HEX_64_PATTERN.matcher(checksum.trim()).matches()) {
            violations.add("checksum must be exactly 64 hexadecimal characters: " + checksum);
        }

        // 9. Payload validation
        MeshPacket.Payload payload = packet.getPayload();
        if (payload == null) {
            violations.add("payload cannot be null");
        } else if (type != null) {
            validatePayloadForType(type.trim(), payload, violations);
        }

        if (violations.isEmpty()) {
            return ValidationResult.ok();
        } else {
            return ValidationResult.fail(violations);
        }
    }

    private static void validateNodeId(String fieldName, String nodeId, List<String> violations) {
        if (nodeId == null || nodeId.trim().isEmpty()) {
            violations.add(fieldName + " is required");
        } else if (nodeId.length() > MAX_NODE_ID_LENGTH) {
            violations.add(fieldName + " length exceeds maximum of " + MAX_NODE_ID_LENGTH + " characters");
        }
    }

    private static void validatePayloadForType(String type, MeshPacket.Payload payload, List<String> violations) {
        switch (type) {
            case MeshPacket.TYPE_SOS_BROADCAST:
            case MeshPacket.TYPE_SOS_DATA:
                // sender_name bắt buộc từ 1 đến 100 ký tự
                String senderName = payload.getSenderName();
                if (senderName == null || senderName.trim().isEmpty() || senderName.length() > 100) {
                    violations.add("payload.sender_name must be between 1 and 100 characters");
                }

                // alert_type chỉ chấp nhận MEDICAL, FLOOD_TRAPPED, LANDSLIDE
                String alertType = payload.getAlertType();
                if (alertType == null || !VALID_SOS_ALERT_TYPES.contains(alertType.trim())) {
                    violations.add("payload.alert_type must be one of MEDICAL, FLOOD_TRAPPED, LANDSLIDE; found: " + alertType);
                }

                if (payload.getSeverity() == null || !VALID_SEVERITIES.contains(payload.getSeverity())) {
                    violations.add("payload.severity must be one of CRITICAL, HIGH, MEDIUM; found: " + payload.getSeverity());
                }

                if (payload.getVictimCount() < 0) {
                    violations.add("payload.victim_count cannot be negative: " + payload.getVictimCount());
                }

                if (payload.getMessage() == null) {
                    violations.add("payload.message is required for SOS");
                } else if (payload.getMessage().length() > MAX_MESSAGE_LENGTH) {
                    violations.add("payload.message exceeds maximum limit of " + MAX_MESSAGE_LENGTH + " characters");
                }

                MeshPacket.Location loc = payload.getLocation();
                if (loc == null) {
                    violations.add("payload.location is required for SOS");
                } else {
                    validateLocation(loc, violations);
                }
                break;

            case MeshPacket.TYPE_DISPATCH_COMMAND:
            case MeshPacket.TYPE_DISPATCH_CMD:
                if (payload.getMessage() == null || payload.getMessage().trim().isEmpty()) {
                    violations.add("payload.message is required for DISPATCH_COMMAND");
                } else if (payload.getMessage().length() > MAX_MESSAGE_LENGTH) {
                    violations.add("payload.message exceeds maximum limit of " + MAX_MESSAGE_LENGTH + " characters");
                }
                if (payload.getSeverity() != null && !VALID_SEVERITIES.contains(payload.getSeverity())) {
                    violations.add("payload.severity must be one of CRITICAL, HIGH, MEDIUM; found: " + payload.getSeverity());
                }
                if (payload.getLocation() != null) {
                    validateFiniteCoordinates(payload.getLocation(), violations);
                }
                break;

            case MeshPacket.TYPE_ACK:
                String ackForId = payload.getAckForPacketId();
                if (ackForId == null || ackForId.trim().isEmpty()) {
                    violations.add("payload.ack_for_packet_id is required for ACK");
                } else if (!UUID_PATTERN.matcher(ackForId.trim()).matches()) {
                    violations.add("payload.ack_for_packet_id must be a valid UUID format: " + ackForId);
                }
                if (payload.getLocation() != null) {
                    validateFiniteCoordinates(payload.getLocation(), violations);
                }
                break;

            case MeshPacket.TYPE_HEARTBEAT:
                if (payload.getLocation() != null) {
                    validateFiniteCoordinates(payload.getLocation(), violations);
                }
                break;

            case MeshPacket.TYPE_ROUTE_DISCOVERY:
                if (payload.getDiscoveryInfo() == null || payload.getDiscoveryInfo().isJsonNull()) {
                    violations.add("payload.discovery_info is required for ROUTE_DISCOVERY");
                } else {
                    validateDiscoveryInfoRecursive(payload.getDiscoveryInfo(), violations);
                }
                if (payload.getLocation() != null) {
                    validateFiniteCoordinates(payload.getLocation(), violations);
                }
                break;

            default:
                break;
        }

        // Kiểm tra hợp lệ discovery_info nếu xuất hiện ở các loại packet khác
        if (!MeshPacket.TYPE_ROUTE_DISCOVERY.equals(type) && payload.getDiscoveryInfo() != null && !payload.getDiscoveryInfo().isJsonNull()) {
            validateDiscoveryInfoRecursive(payload.getDiscoveryInfo(), violations);
        }
    }

    public static void validateDiscoveryInfoRecursive(Object info, List<String> violations) {
        if (info == null) {
            return;
        }
        try {
            if (info instanceof JsonElement) {
                validateJsonElementRecursive((JsonElement) info, violations);
            } else if (info instanceof Boolean || info instanceof String || info instanceof Character) {
                // Hợp lệ
            } else if (info instanceof Number) {
                if (info instanceof Double || info instanceof Float) {
                    double d = ((Number) info).doubleValue();
                    if (!Double.isFinite(d)) {
                        violations.add("payload.discovery_info contains non-finite or overflow number: " + info);
                        return;
                    }
                }
                try {
                    ChecksumUtil.validateNumericString(info.toString());
                } catch (IllegalArgumentException e) {
                    violations.add(e.getMessage());
                }
            } else if (info instanceof Map<?, ?>) {
                Map<?, ?> map = (Map<?, ?>) info;
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (entry.getKey() == null || !(entry.getKey() instanceof String)) {
                        violations.add("payload.discovery_info map key must be a non-null String, found: " + entry.getKey());
                    }
                    validateDiscoveryInfoRecursive(entry.getValue(), violations);
                }
            } else if (info instanceof Iterable<?>) {
                for (Object item : (Iterable<?>) info) {
                    validateDiscoveryInfoRecursive(item, violations);
                }
            } else if (info.getClass().isArray()) {
                int len = java.lang.reflect.Array.getLength(info);
                for (int i = 0; i < len; i++) {
                    validateDiscoveryInfoRecursive(java.lang.reflect.Array.get(info, i), violations);
                }
            } else {
                violations.add("payload.discovery_info contains unsupported data type: " + info.getClass().getName());
            }
        } catch (Exception e) {
            violations.add("payload.discovery_info validation error: " + e.getMessage());
        }
    }

    private static void validateJsonElementRecursive(JsonElement element, List<String> violations) {
        if (element == null || element.isJsonNull()) {
            return;
        }
        if (element.isJsonPrimitive()) {
            JsonPrimitive prim = element.getAsJsonPrimitive();
            if (prim.isNumber()) {
                try {
                    ChecksumUtil.validateNumericString(prim.getAsString());
                } catch (IllegalArgumentException e) {
                    violations.add(e.getMessage());
                }
                return;
            }
            return;
        }
        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                if (entry.getKey() == null) {
                    violations.add("payload.discovery_info object key cannot be null");
                }
                validateJsonElementRecursive(entry.getValue(), violations);
            }
            return;
        }
        if (element.isJsonArray()) {
            JsonArray arr = element.getAsJsonArray();
            for (JsonElement item : arr) {
                validateJsonElementRecursive(item, violations);
            }
            return;
        }
        violations.add("payload.discovery_info contains unsupported JsonElement type: " + element.getClass().getName());
    }

    private static void validateLocation(MeshPacket.Location loc, List<String> violations) {
        validateFiniteCoordinates(loc, violations);

        if (Double.isFinite(loc.getLatitude())) {
            if (loc.getLatitude() < -90.0 || loc.getLatitude() > 90.0) {
                violations.add("payload.location.latitude must be in range [-90.0, 90.0], found: " + loc.getLatitude());
            }
        }
        if (Double.isFinite(loc.getLongitude())) {
            if (loc.getLongitude() < -180.0 || loc.getLongitude() > 180.0) {
                violations.add("payload.location.longitude must be in range [-180.0, 180.0], found: " + loc.getLongitude());
            }
        }
        if (Double.isFinite(loc.getAltitude())) {
            if (loc.getAltitude() < -500.0 || loc.getAltitude() > 10000.0) {
                violations.add("payload.location.altitude must be between -500.0 and 10000.0 meters, found: " + loc.getAltitude());
            }
        }
        if (Double.isFinite(loc.getAccuracy())) {
            if (loc.getAccuracy() < 0.0) {
                violations.add("payload.location.accuracy cannot be negative: " + loc.getAccuracy());
            }
        }
    }

    private static void validateFiniteCoordinates(MeshPacket.Location loc, List<String> violations) {
        if (!Double.isFinite(loc.getLatitude())) {
            violations.add("payload.location.latitude must be a finite number");
        }
        if (!Double.isFinite(loc.getLongitude())) {
            violations.add("payload.location.longitude must be a finite number");
        }
        if (!Double.isFinite(loc.getAltitude())) {
            violations.add("payload.location.altitude must be a finite number");
        }
        if (!Double.isFinite(loc.getAccuracy())) {
            violations.add("payload.location.accuracy must be a finite number");
        }
    }
}
package com.rescue.mesh.model;

import com.google.gson.Gson;
import com.rescue.mesh.util.ChecksumUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Cấu trúc gói tin chuẩn của hệ thống Emergency Mesh Rescue.
 *
 * Cơ sở lý thuyết:
 *   - TTL (Time-To-Live): Lấy ý tưởng từ IPv4 Header (RFC 791).
 *     Giới hạn số bước nhảy tối đa, tránh gói tin lưu thông vô hạn.
 *   - routeHistory: Tương đương Source Route trong IPv6 (RFC 2460).
 *     Ghi lại đường đi để debug và hiển thị trực quan trên bản đồ.
 *   - packetId (UUID): Định danh duy nhất để SeenPacketCache phát hiện duplicate.
 *   - checksum (SHA-256): Đảm bảo Data Integrity theo RFC 6234.
 *
 * Serialization: Dùng Gson (JSON) thay vì Java Serialization vì:
 *   - Human-readable → dễ debug
 *   - Platform-agnostic → Android giai đoạn 2 dùng được nguyên vẹn
 *   - Không có lỗ hổng deserialization như Java native serialization
 *
 * QUAN TRỌNG: Class này là POJO thuần — không biết gì về UI, Socket, hay threading.
 */
public class MeshPacket {

    // ===== HẰNG SỐ LOẠI GÓI TIN =====
    public static final String TYPE_SOS_DATA     = "SOS_DATA";
    public static final String TYPE_DISPATCH_CMD = "DISPATCH_CMD";
    public static final String TYPE_ACK          = "ACK";
    public static final String TYPE_HEARTBEAT    = "HEARTBEAT";

    // ===== HẰNG SỐ MỨC ĐỘ NGUY HIỂM =====
    public static final String SEVERITY_CRITICAL = "CRITICAL";
    public static final String SEVERITY_HIGH     = "HIGH";
    public static final String SEVERITY_MEDIUM   = "MEDIUM";

    // ===== HẰNG SỐ LOẠI TÌNH HUỐNG =====
    public static final String ALERT_MEDICAL      = "MEDICAL";
    public static final String ALERT_FLOOD        = "FLOOD_TRAPPED";
    public static final String ALERT_LANDSLIDE    = "LANDSLIDE";

    // ===== HẰNG SỐ NODE ID =====
    public static final String NODE_BASE_STATION = "BASE_STATION";
    public static final String NODE_A_VICTIM     = "NODE_A_VICTIM";
    public static final String NODE_B_RELAY      = "NODE_B_RELAY";

    // ===== TTL MẶC ĐỊNH =====
    public static final int DEFAULT_TTL = 5;

    // ===== FIELDS =====

    /** UUID duy nhất — dùng bởi SeenPacketCache để chống lặp */
    private String packetId;

    /** Loại gói tin: SOS_DATA / DISPATCH_CMD / ACK / HEARTBEAT */
    private String packetType;

    /** ID của node tạo ra gói tin (nguồn gốc ban đầu) */
    private String sourceNodeId;

    /** ID của node đích cuối cùng */
    private String destinationNodeId;

    /** ID của node vừa gửi gói tin này (thay đổi mỗi hop) */
    private String senderHopId;

    /** Số bước nhảy tối đa còn lại — giảm 1 mỗi relay. Drop khi TTL ≤ 1 */
    private int ttl;

    /** Đếm số bước nhảy đã thực hiện — tăng 1 mỗi relay */
    private int hopCount;

    /** Thời điểm tạo gói tin (Unix timestamp milliseconds) */
    private long timestamp;

    /** Nội dung chính của gói tin */
    private Payload payload;

    /** Lịch sử các node đã relay: ["NODE_A", "NODE_B", ...] */
    private List<String> routeHistory;

    /** SHA-256 của payload JSON — kiểm tra tính toàn vẹn dữ liệu */
    private String checksum;

    // ===== INNER CLASS: PAYLOAD =====

    /**
     * Nội dung chính của gói tin SOS/DISPATCH.
     */
    public static class Payload {

        private String senderName;

        /** MEDICAL / FLOOD_TRAPPED / LANDSLIDE */
        private String alertType;

        private String message;

        private int victimCount;

        /** CRITICAL / HIGH / MEDIUM */
        private String severity;

        private Location location;

        // --- Getters & Setters ---

        public String getSenderName() { return senderName; }
        public void setSenderName(String senderName) { this.senderName = senderName; }

        public String getAlertType() { return alertType; }
        public void setAlertType(String alertType) { this.alertType = alertType; }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }

        public int getVictimCount() { return victimCount; }
        public void setVictimCount(int victimCount) { this.victimCount = victimCount; }

        public String getSeverity() { return severity; }
        public void setSeverity(String severity) { this.severity = severity; }

        public Location getLocation() { return location; }
        public void setLocation(Location location) { this.location = location; }

        @Override
        public String toString() {
            return "Payload{"
                    + "senderName='" + senderName + '\''
                    + ", alertType='" + alertType + '\''
                    + ", severity='" + severity + '\''
                    + ", victimCount=" + victimCount
                    + ", location=" + location
                    + '}';
        }
    }

    // ===== INNER CLASS: LOCATION =====

    /**
     * Tọa độ địa lý GPS của nạn nhân.
     */
    public static class Location {

        private double latitude;
        private double longitude;

        public Location() {}

        public Location(double latitude, double longitude) {
            this.latitude  = latitude;
            this.longitude = longitude;
        }

        public double getLatitude()  { return latitude; }
        public void setLatitude(double latitude) { this.latitude = latitude; }

        public double getLongitude() { return longitude; }
        public void setLongitude(double longitude) { this.longitude = longitude; }

        @Override
        public String toString() {
            return "Location{lat=" + latitude + ", lon=" + longitude + '}';
        }
    }

    // ===== CONSTRUCTORS =====

    /** Constructor không tham số — cần cho Gson deserialization */
    public MeshPacket() {
        this.routeHistory = new ArrayList<>();
    }

    /**
     * Constructor đầy đủ để tạo gói tin mới.
     *
     * @param packetType       Loại gói tin (dùng hằng TYPE_*)
     * @param sourceNodeId     ID node nguồn
     * @param destinationNodeId ID node đích
     */
    public MeshPacket(String packetType, String sourceNodeId, String destinationNodeId) {
        this.packetId          = UUID.randomUUID().toString();
        this.packetType        = packetType;
        this.sourceNodeId      = sourceNodeId;
        this.destinationNodeId = destinationNodeId;
        this.senderHopId       = sourceNodeId;
        this.ttl               = DEFAULT_TTL;
        this.hopCount          = 0;
        this.timestamp         = System.currentTimeMillis();
        this.routeHistory      = new ArrayList<>();
    }

    // ===== CHECKSUM METHODS =====

    /**
     * Tính và gán checksum SHA-256 dựa trên nội dung payload hiện tại.
     * Gọi method này SAU KHI đã set đầy đủ payload, TRƯỚC KHI gửi đi.
     *
     * @param gson Instance Gson để serialize payload thành JSON
     */
    public void computeAndSetChecksum(Gson gson) {
        if (this.payload == null) {
            this.checksum = ChecksumUtil.compute("empty_payload");
        } else {
            String payloadJson = gson.toJson(this.payload);
            this.checksum = ChecksumUtil.compute(payloadJson);
        }
    }

    /**
     * Xác minh tính toàn vẹn của gói tin bằng cách tính lại checksum.
     *
     * @param gson Instance Gson để serialize payload thành JSON
     * @return true nếu checksum hợp lệ, false nếu dữ liệu bị thay đổi
     */
    public boolean verifyChecksum(Gson gson) {
        if (this.checksum == null || this.payload == null) {
            return false;
        }
        String payloadJson = gson.toJson(this.payload);
        return ChecksumUtil.verify(payloadJson, this.checksum);
    }

    // ===== SERIALIZATION METHODS =====

    /**
     * Chuyển toàn bộ MeshPacket thành chuỗi JSON để gửi qua Socket.
     *
     * @param gson Instance Gson
     * @return Chuỗi JSON đại diện cho gói tin
     */
    public String toJson(Gson gson) {
        return gson.toJson(this);
    }

    /**
     * Tạo MeshPacket từ chuỗi JSON nhận được qua Socket.
     *
     * @param json Chuỗi JSON nhận được
     * @param gson Instance Gson
     * @return MeshPacket object, hoặc null nếu JSON không hợp lệ
     */
    public static MeshPacket fromJson(String json, Gson gson) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        try {
            return gson.fromJson(json, MeshPacket.class);
        } catch (Exception e) {
            System.err.println("[ERROR] MeshPacket.fromJson: không parse được JSON — " + e.getMessage());
            return null;
        }
    }

    // ===== GETTERS & SETTERS =====

    public String getPacketId() { return packetId; }
    public void setPacketId(String packetId) { this.packetId = packetId; }

    public String getPacketType() { return packetType; }
    public void setPacketType(String packetType) { this.packetType = packetType; }

    public String getSourceNodeId() { return sourceNodeId; }
    public void setSourceNodeId(String sourceNodeId) { this.sourceNodeId = sourceNodeId; }

    public String getDestinationNodeId() { return destinationNodeId; }
    public void setDestinationNodeId(String destinationNodeId) { this.destinationNodeId = destinationNodeId; }

    public String getSenderHopId() { return senderHopId; }
    public void setSenderHopId(String senderHopId) { this.senderHopId = senderHopId; }

    public int getTtl() { return ttl; }
    public void setTtl(int ttl) { this.ttl = ttl; }

    public int getHopCount() { return hopCount; }
    public void setHopCount(int hopCount) { this.hopCount = hopCount; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public Payload getPayload() { return payload; }
    public void setPayload(Payload payload) { this.payload = payload; }

    public List<String> getRouteHistory() { return routeHistory; }
    public void setRouteHistory(List<String> routeHistory) { this.routeHistory = routeHistory; }

    public String getChecksum() { return checksum; }
    public void setChecksum(String checksum) { this.checksum = checksum; }

    // ===== UTILITY METHODS =====

    /**
     * Thêm node ID vào lịch sử route khi relay.
     * @param nodeId ID của node vừa xử lý gói tin
     */
    public void addToRouteHistory(String nodeId) {
        if (this.routeHistory == null) {
            this.routeHistory = new ArrayList<>();
        }
        this.routeHistory.add(nodeId);
    }

    /**
     * Tóm tắt ngắn gọn cho log — không in toàn bộ payload.
     */
    @Override
    public String toString() {
        return "MeshPacket{"
                + "id='" + (packetId != null ? packetId.substring(0, 8) : "null") + "...'"
                + ", type='" + packetType + '\''
                + ", from='" + sourceNodeId + '\''
                + ", to='" + destinationNodeId + '\''
                + ", ttl=" + ttl
                + ", hops=" + hopCount
                + ", route=" + routeHistory
                + '}';
    }
}
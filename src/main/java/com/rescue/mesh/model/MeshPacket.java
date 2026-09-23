package com.rescue.mesh.model;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;
import com.rescue.mesh.util.ChecksumUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Cấu trúc gói tin chuẩn canonical v1.0 của hệ thống Emergency Mesh Rescue.
 *
 * Chuẩn hóa schema JSON snake_case theo tài liệu docs/protocol/mesh-packet-v1.md.
 * Hỗ trợ đọc cả alias cũ (SOS_DATA, DISPATCH_CMD, camelCase) để đảm bảo tương thích ngược.
 *
 * QUAN TRỌNG: Checksum SHA-256 là mã băm kiểm tra tính toàn vẹn (Data Integrity),
 * không phải cơ chế xác thực danh tính (Authentication).
 */
public class MeshPacket {

    // ===== PHIÊN BẢN GIAO THỨC =====
    public static final String PROTOCOL_VERSION_1 = "1.0";

    // ===== HẰNG SỐ LOẠI GÓI TIN CANONICAL V1 =====
    public static final String TYPE_SOS_BROADCAST    = "SOS_BROADCAST";
    public static final String TYPE_DISPATCH_COMMAND  = "DISPATCH_COMMAND";
    public static final String TYPE_ROUTE_DISCOVERY  = "ROUTE_DISCOVERY";
    public static final String TYPE_ACK              = "ACK";
    public static final String TYPE_HEARTBEAT        = "HEARTBEAT";

    // ===== ALIAS CŨ CHO TƯƠNG THÍCH NGƯỢC (DEPRECATED) =====
    @Deprecated
    public static final String TYPE_SOS_DATA         = "SOS_DATA";
    @Deprecated
    public static final String TYPE_DISPATCH_CMD     = "DISPATCH_CMD";

    // ===== HẰNG SỐ MỨC ĐỘ NGUY HIỂM =====
    public static final String SEVERITY_CRITICAL     = "CRITICAL";
    public static final String SEVERITY_HIGH         = "HIGH";
    public static final String SEVERITY_MEDIUM       = "MEDIUM";

    // ===== HẰNG SỐ LOẠI TÌNH HUỐNG =====
    public static final String ALERT_MEDICAL         = "MEDICAL";
    public static final String ALERT_FLOOD           = "FLOOD_TRAPPED";
    public static final String ALERT_LANDSLIDE       = "LANDSLIDE";

    // ===== HẰNG SỐ NODE ID =====
    public static final String NODE_BASE_STATION     = "BASE_STATION";
    public static final String NODE_A_VICTIM         = "NODE_A_VICTIM";
    public static final String NODE_B_RELAY          = "NODE_B_RELAY";
    public static final String NODE_BROADCAST        = "BROADCAST";

    // ===== TTL MẶC ĐỊNH =====
    public static final int DEFAULT_TTL              = 5;

    // ===== CANONICAL FIELDS =====

    @SerializedName(value = "packet_id", alternate = {"packetId"})
    private String packetId;

    @SerializedName(value = "protocol_version", alternate = {"protocolVersion"})
    private String protocolVersion = PROTOCOL_VERSION_1;

    @SerializedName(value = "packet_type", alternate = {"packetType"})
    private String packetType;

    @SerializedName(value = "source_node_id", alternate = {"sourceNodeId"})
    private String sourceNodeId;

    @SerializedName(value = "destination_node_id", alternate = {"destinationNodeId"})
    private String destinationNodeId;

    @SerializedName(value = "sender_hop_id", alternate = {"senderHopId"})
    private String senderHopId;

    @SerializedName("ttl")
    private int ttl;

    @SerializedName(value = "hop_count", alternate = {"hopCount"})
    private int hopCount;

    @SerializedName("timestamp")
    private long timestamp;

    @SerializedName("payload")
    private Payload payload;

    @SerializedName(value = "route_history", alternate = {"routeHistory"})
    private List<String> routeHistory;

    @SerializedName("checksum")
    private String checksum;

    // ===== INNER CLASS: PAYLOAD =====

    /**
     * Nội dung nghiệp vụ của gói tin.
     */
    public static class Payload {

        @SerializedName(value = "sender_name", alternate = {"senderName"})
        private String senderName;

        @SerializedName(value = "alert_type", alternate = {"alertType"})
        private String alertType;

        @SerializedName("message")
        private String message;

        @SerializedName(value = "victim_count", alternate = {"victimCount"})
        private int victimCount;

        @SerializedName("severity")
        private String severity;

        @SerializedName("location")
        private Location location;

        @SerializedName(value = "ack_for_packet_id", alternate = {"ackForPacketId"})
        private String ackForPacketId;

        @SerializedName(value = "discovery_info", alternate = {"discoveryInfo"})
        private JsonElement discoveryInfo;

        public Payload() {}

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

        public String getAckForPacketId() { return ackForPacketId; }
        public void setAckForPacketId(String ackForPacketId) { this.ackForPacketId = ackForPacketId; }

        public JsonElement getDiscoveryInfo() { return discoveryInfo; }
        public void setDiscoveryInfo(JsonElement discoveryInfo) { this.discoveryInfo = discoveryInfo; }
        public void setDiscoveryInfo(Object discoveryInfo) {
            if (discoveryInfo == null) {
                this.discoveryInfo = null;
            } else if (discoveryInfo instanceof JsonElement) {
                this.discoveryInfo = (JsonElement) discoveryInfo;
            } else {
                this.discoveryInfo = ChecksumUtil.toJsonElement(discoveryInfo);
            }
        }

        @Override
        public String toString() {
            return "Payload{"
                    + "senderName='" + senderName + '\''
                    + ", alertType='" + alertType + '\''
                    + ", severity='" + severity + '\''
                    + ", victimCount=" + victimCount
                    + ", ackForPacketId='" + ackForPacketId + '\''
                    + ", location=" + location
                    + '}';
        }
    }

    // ===== INNER CLASS: LOCATION =====

    /**
     * Tọa độ địa lý GPS của sự cố/nạn nhân.
     */
    public static class Location {

        @SerializedName(value = "latitude", alternate = {"lat"})
        private double latitude;

        @SerializedName(value = "longitude", alternate = {"lon", "lng"})
        private double longitude;

        @SerializedName(value = "altitude", alternate = {"alt"})
        private double altitude = 0.0;

        @SerializedName(value = "accuracy", alternate = {"acc"})
        private double accuracy = 0.0;

        public Location() {}

        public Location(double latitude, double longitude) {
            this(latitude, longitude, 0.0, 0.0);
        }

        public Location(double latitude, double longitude, double altitude, double accuracy) {
            this.latitude  = latitude;
            this.longitude = longitude;
            this.altitude  = altitude;
            this.accuracy  = accuracy;
        }

        public double getLatitude()  { return latitude; }
        public void setLatitude(double latitude) { this.latitude = latitude; }

        public double getLongitude() { return longitude; }
        public void setLongitude(double longitude) { this.longitude = longitude; }

        public double getAltitude()  { return altitude; }
        public void setAltitude(double altitude) { this.altitude = altitude; }

        public double getAccuracy()  { return accuracy; }
        public void setAccuracy(double accuracy) { this.accuracy = accuracy; }

        @Override
        public String toString() {
            return "Location{lat=" + latitude + ", lon=" + longitude + ", alt=" + altitude + ", acc=" + accuracy + '}';
        }
    }

    // ===== CONSTRUCTORS =====

    public MeshPacket() {
        this.protocolVersion = PROTOCOL_VERSION_1;
        this.routeHistory = new ArrayList<>();
    }

    public MeshPacket(String packetType, String sourceNodeId, String destinationNodeId) {
        this.packetId          = UUID.randomUUID().toString();
        this.protocolVersion   = PROTOCOL_VERSION_1;
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
     * Tính toán và gán checksum SHA-256 canonical cho toàn bộ gói tin.
     */
    public void computeAndSetChecksum() {
        this.checksum = ChecksumUtil.computePacketChecksum(this);
    }

    public void computeAndSetChecksum(Gson gson) {
        computeAndSetChecksum();
    }

    /**
     * Xác minh tính toàn vẹn của gói tin bằng cách tính lại checksum canonical.
     */
    public boolean verifyChecksum() {
        return ChecksumUtil.verifyPacketChecksum(this);
    }

    public boolean verifyChecksum(Gson gson) {
        return verifyChecksum();
    }

    // ===== SERIALIZATION METHODS =====

    public String toJson() {
        return toJson(new Gson());
    }

    public String toJson(Gson gson) {
        return gson.toJson(this);
    }

    public static MeshPacket fromJson(String json, Gson gson) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        try {
            MeshPacket packet = gson.fromJson(json, MeshPacket.class);
            if (packet != null) {
                packet.normalize();
            }
            return packet;
        } catch (Exception e) {
            System.err.println("[ERROR] MeshPacket.fromJson: không parse được JSON — " + e.getMessage());
            return null;
        }
    }

    /**
     * Chuẩn hóa gói tin sang canonical v1 (chuyển đổi alias cũ nếu có).
     */
    public void normalize() {
        if (TYPE_SOS_DATA.equals(this.packetType)) {
            this.packetType = TYPE_SOS_BROADCAST;
        } else if (TYPE_DISPATCH_CMD.equals(this.packetType)) {
            this.packetType = TYPE_DISPATCH_COMMAND;
        }
        if (this.protocolVersion == null || this.protocolVersion.trim().isEmpty()) {
            this.protocolVersion = PROTOCOL_VERSION_1;
        }
        if (this.routeHistory == null) {
            this.routeHistory = new ArrayList<>();
        }
    }

    // ===== GETTERS & SETTERS =====

    public String getPacketId() { return packetId; }
    public void setPacketId(String packetId) { this.packetId = packetId; }

    public String getProtocolVersion() { return protocolVersion; }
    public void setProtocolVersion(String protocolVersion) { this.protocolVersion = protocolVersion; }

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

    public void addToRouteHistory(String nodeId) {
        if (this.routeHistory == null) {
            this.routeHistory = new ArrayList<>();
        }
        this.routeHistory.add(nodeId);
    }

    @Override
    public String toString() {
        return "MeshPacket{"
                + "id='" + (packetId != null && packetId.length() > 8 ? packetId.substring(0, 8) + "..." : packetId) + '\''
                + ", v='" + protocolVersion + '\''
                + ", type='" + packetType + '\''
                + ", from='" + sourceNodeId + '\''
                + ", to='" + destinationNodeId + '\''
                + ", ttl=" + ttl
                + ", hops=" + hopCount
                + ", route=" + routeHistory
                + '}';
    }
}
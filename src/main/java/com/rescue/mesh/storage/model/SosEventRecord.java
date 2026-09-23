package com.rescue.mesh.storage.model;

/**
 * Bản ghi sự kiện SOS lưu trữ trong SQLite (bảng sos_events kết hợp packets).
 */
public class SosEventRecord {

    private final String packetId;
    private final String sourceNodeId;
    private final String senderName;
    private final String alertType;
    private final String message;
    private final int victimCount;
    private final String severity;
    private final Double latitude;
    private final Double longitude;
    private final Double altitude;
    private final Double accuracy;
    private final String rescueStatus; // PENDING / DISPATCHED / RESOLVED
    private final long timestamp;
    private final String routeHistory;

    public SosEventRecord(String packetId, String sourceNodeId, String senderName,
                          String alertType, String message, int victimCount,
                          String severity, Double latitude, Double longitude,
                          Double altitude, Double accuracy, String rescueStatus,
                          long timestamp, String routeHistory) {
        this.packetId = packetId;
        this.sourceNodeId = sourceNodeId;
        this.senderName = senderName;
        this.alertType = alertType;
        this.message = message;
        this.victimCount = victimCount;
        this.severity = severity;
        this.latitude = latitude;
        this.longitude = longitude;
        this.altitude = altitude;
        this.accuracy = accuracy;
        this.rescueStatus = rescueStatus;
        this.timestamp = timestamp;
        this.routeHistory = routeHistory;
    }

    public String getPacketId() { return packetId; }
    public String getSourceNodeId() { return sourceNodeId; }
    public String getSenderName() { return senderName; }
    public String getAlertType() { return alertType; }
    public String getMessage() { return message; }
    public int getVictimCount() { return victimCount; }
    public String getSeverity() { return severity; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public Double getAltitude() { return altitude; }
    public Double getAccuracy() { return accuracy; }
    public String getRescueStatus() { return rescueStatus; }
    public long getTimestamp() { return timestamp; }
    public String getRouteHistory() { return routeHistory; }
}
package com.rescue.mesh.model;

/**
 * DTO tổng hợp cho Trang 6 (Audit Trail) — FINAL spec §5.1.
 *
 * KHÔNG map trực tiếp 1 bảng — được ReportExportService build bằng cách
 * JOIN theo node_id/timestamp từ mesh_packets + dispatch_logs + interagency_dispatches
 * tại thời điểm truy vấn, không lưu bảng riêng để tránh trùng lặp dữ liệu.
 */
public class AuditLogEntity {

    private long timestamp;
    private String eventType;      // SOS_RECEIVED, DISPATCH_SENT, INTERAGENCY_FORWARDED, HEARTBEAT, etc.
    private String sourceNodeId;
    private String targetNodeId;
    private String details;        // Mô tả sự kiện
    private String packetId;       // Tham chiếu packet nếu có
    private String severity;       // CRITICAL, HIGH, MEDIUM, LOW, INFO

    public AuditLogEntity() {}

    public AuditLogEntity(long timestamp, String eventType, String sourceNodeId,
                           String targetNodeId, String details, String packetId, String severity) {
        this.timestamp = timestamp;
        this.eventType = eventType;
        this.sourceNodeId = sourceNodeId;
        this.targetNodeId = targetNodeId;
        this.details = details;
        this.packetId = packetId;
        this.severity = severity;
    }

    // =========================================================
    // GETTERS & SETTERS
    // =========================================================

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getSourceNodeId() { return sourceNodeId; }
    public void setSourceNodeId(String sourceNodeId) { this.sourceNodeId = sourceNodeId; }

    public String getTargetNodeId() { return targetNodeId; }
    public void setTargetNodeId(String targetNodeId) { this.targetNodeId = targetNodeId; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }

    public String getPacketId() { return packetId; }
    public void setPacketId(String packetId) { this.packetId = packetId; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    @Override
    public String toString() {
        return "AuditLogEntity{time=" + timestamp + ", event='" + eventType
                + "', source='" + sourceNodeId + "', severity='" + severity + "'}";
    }
}

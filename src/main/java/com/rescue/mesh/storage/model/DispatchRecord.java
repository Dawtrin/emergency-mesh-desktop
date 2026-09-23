package com.rescue.mesh.storage.model;

/**
 * Bản ghi lệnh điều phối gửi từ Base Station đến cứu hộ viên / node khác.
 */
public class DispatchRecord {
    private final String packetId;
    private final String targetNodeId;
    private final String message;
    private final String severity;
    private final String deliveryStatus; // PENDING, SENT, ACKED, FAILED
    private final int attemptCount;
    private final Long nextAttemptTime;
    private final long createdAt;
    private final long updatedAt;

    public DispatchRecord(String packetId, String targetNodeId, String message,
                          String severity, String deliveryStatus, int attemptCount,
                          Long nextAttemptTime, long createdAt, long updatedAt) {
        this.packetId = packetId;
        this.targetNodeId = targetNodeId;
        this.message = message;
        this.severity = severity;
        this.deliveryStatus = deliveryStatus;
        this.attemptCount = attemptCount;
        this.nextAttemptTime = nextAttemptTime;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getPacketId() { return packetId; }
    public String getTargetNodeId() { return targetNodeId; }
    public String getMessage() { return message; }
    public String getSeverity() { return severity; }
    public String getDeliveryStatus() { return deliveryStatus; }
    public int getAttemptCount() { return attemptCount; }
    public Long getNextAttemptTime() { return nextAttemptTime; }
    public long getCreatedAt() { return createdAt; }
    public long getUpdatedAt() { return updatedAt; }
}

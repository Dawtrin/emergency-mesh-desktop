package com.rescue.mesh.model;

/**
 * Entity ánh xạ bảng `network_metrics` — giám sát lưu lượng & băng thông (Đề 53).
 * Theo FINAL spec §5.1 — dùng thống nhất cho payload /topic/network-metrics.
 */
public class NetworkMetricEntity {

    private long id;
    private long timestamp;
    private int packetsReceived;
    private int packetsForwarded;
    private int packetsDropped;
    private int duplicateDrops;
    private int activeRelayCount;

    public NetworkMetricEntity() {
        this.timestamp = System.currentTimeMillis();
    }

    public NetworkMetricEntity(int packetsReceived, int packetsForwarded,
                                int packetsDropped, int duplicateDrops, int activeRelayCount) {
        this();
        this.packetsReceived = packetsReceived;
        this.packetsForwarded = packetsForwarded;
        this.packetsDropped = packetsDropped;
        this.duplicateDrops = duplicateDrops;
        this.activeRelayCount = activeRelayCount;
    }

    // =========================================================
    // GETTERS & SETTERS
    // =========================================================

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public int getPacketsReceived() { return packetsReceived; }
    public void setPacketsReceived(int count) { this.packetsReceived = count; }

    public int getPacketsForwarded() { return packetsForwarded; }
    public void setPacketsForwarded(int count) { this.packetsForwarded = count; }

    public int getPacketsDropped() { return packetsDropped; }
    public void setPacketsDropped(int count) { this.packetsDropped = count; }

    public int getDuplicateDrops() { return duplicateDrops; }
    public void setDuplicateDrops(int count) { this.duplicateDrops = count; }

    public int getActiveRelayCount() { return activeRelayCount; }
    public void setActiveRelayCount(int count) { this.activeRelayCount = count; }

    @Override
    public String toString() {
        return "NetworkMetricEntity{received=" + packetsReceived
                + ", forwarded=" + packetsForwarded
                + ", dropped=" + packetsDropped
                + ", duplicates=" + duplicateDrops + "}";
    }
}

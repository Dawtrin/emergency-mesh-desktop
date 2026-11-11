package com.rescue.mesh.model;

/**
 * Entity ánh xạ bảng `relay_nodes` — trạm tiếp sóng trong mạng Mesh.
 * Theo FINAL spec §5.1 — dùng thống nhất cho payload /topic/relay-status.
 */
public class RelayNodeEntity {

    private String nodeId;
    private String nodeName;
    private String ipAddress;
    private int port;
    private long lastHeartbeatTimestamp;
    private int packetsForwardedCount;
    private String status;   // ONLINE, TIMEOUT, OFFLINE

    public RelayNodeEntity() {
        this.status = "ONLINE";
        this.lastHeartbeatTimestamp = System.currentTimeMillis();
    }

    public RelayNodeEntity(String nodeId, String nodeName, String ipAddress, int port) {
        this();
        this.nodeId = nodeId;
        this.nodeName = nodeName;
        this.ipAddress = ipAddress;
        this.port = port;
    }

    // =========================================================
    // GETTERS & SETTERS
    // =========================================================

    public String getNodeId() { return nodeId; }
    public void setNodeId(String nodeId) { this.nodeId = nodeId; }

    public String getNodeName() { return nodeName; }
    public void setNodeName(String nodeName) { this.nodeName = nodeName; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }

    public long getLastHeartbeatTimestamp() { return lastHeartbeatTimestamp; }
    public void setLastHeartbeatTimestamp(long ts) { this.lastHeartbeatTimestamp = ts; }

    public int getPacketsForwardedCount() { return packetsForwardedCount; }
    public void setPacketsForwardedCount(int count) { this.packetsForwardedCount = count; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    @Override
    public String toString() {
        return "RelayNodeEntity{id='" + nodeId + "', name='" + nodeName
                + "', status='" + status + "', forwarded=" + packetsForwardedCount + "}";
    }
}

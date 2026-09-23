package com.rescue.mesh.storage.model;

/**
 * Bản ghi node mạng được phát hiện trong hệ thống.
 */
public class NodeRecord {
    private final String nodeId;
    private final long lastSeen;
    private final String hostAddress;
    private final Integer port;
    private final String capabilityStatus;

    public NodeRecord(String nodeId, long lastSeen, String hostAddress, Integer port, String capabilityStatus) {
        this.nodeId = nodeId;
        this.lastSeen = lastSeen;
        this.hostAddress = hostAddress;
        this.port = port;
        this.capabilityStatus = capabilityStatus;
    }

    public String getNodeId() { return nodeId; }
    public long getLastSeen() { return lastSeen; }
    public String getHostAddress() { return hostAddress; }
    public Integer getPort() { return port; }
    public String getCapabilityStatus() { return capabilityStatus; }
}

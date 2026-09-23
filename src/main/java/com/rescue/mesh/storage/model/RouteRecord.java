package com.rescue.mesh.storage.model;

/**
 * Bản ghi lịch sử định tuyến thu thập từ các gói tin.
 */
public class RouteRecord {
    private final Long id;
    private final String packetId;
    private final String nodeId;
    private final String routeHistory;
    private final long learnedTime;
    private final Long expiryTime;

    public RouteRecord(Long id, String packetId, String nodeId, String routeHistory, long learnedTime, Long expiryTime) {
        this.id = id;
        this.packetId = packetId;
        this.nodeId = nodeId;
        this.routeHistory = routeHistory;
        this.learnedTime = learnedTime;
        this.expiryTime = expiryTime;
    }

    public Long getId() { return id; }
    public String getPacketId() { return packetId; }
    public String getNodeId() { return nodeId; }
    public String getRouteHistory() { return routeHistory; }
    public long getLearnedTime() { return learnedTime; }
    public Long getExpiryTime() { return expiryTime; }
}

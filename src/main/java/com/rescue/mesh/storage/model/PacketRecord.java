package com.rescue.mesh.storage.model;

/**
 * Bản ghi raw packet trong bảng packets.
 */
public class PacketRecord {
    private final String packetId;
    private final String protocolVersion;
    private final String packetType;
    private final String sourceNodeId;
    private final String destinationNodeId;
    private final String senderHopId;
    private final long timestamp;
    private final int ttl;
    private final int hopCount;
    private final String checksum;
    private final String rawJson;
    private final String processingStatus;
    private final long receivedAt;

    public PacketRecord(String packetId, String protocolVersion, String packetType,
                        String sourceNodeId, String destinationNodeId, String senderHopId,
                        long timestamp, int ttl, int hopCount, String checksum,
                        String rawJson, String processingStatus, long receivedAt) {
        this.packetId = packetId;
        this.protocolVersion = protocolVersion;
        this.packetType = packetType;
        this.sourceNodeId = sourceNodeId;
        this.destinationNodeId = destinationNodeId;
        this.senderHopId = senderHopId;
        this.timestamp = timestamp;
        this.ttl = ttl;
        this.hopCount = hopCount;
        this.checksum = checksum;
        this.rawJson = rawJson;
        this.processingStatus = processingStatus;
        this.receivedAt = receivedAt;
    }

    public String getPacketId() { return packetId; }
    public String getProtocolVersion() { return protocolVersion; }
    public String getPacketType() { return packetType; }
    public String getSourceNodeId() { return sourceNodeId; }
    public String getDestinationNodeId() { return destinationNodeId; }
    public String getSenderHopId() { return senderHopId; }
    public long getTimestamp() { return timestamp; }
    public int getTtl() { return ttl; }
    public int getHopCount() { return hopCount; }
    public String getChecksum() { return checksum; }
    public String getRawJson() { return rawJson; }
    public String getProcessingStatus() { return processingStatus; }
    public long getReceivedAt() { return receivedAt; }
}

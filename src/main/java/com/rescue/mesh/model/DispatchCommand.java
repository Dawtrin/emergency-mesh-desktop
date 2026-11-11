package com.rescue.mesh.model;

import java.util.UUID;

/**
 * Entity lệnh điều phối dã chiến — thông tin đầy đủ của một lệnh chỉ đạo.
 */
public class DispatchCommand {

    private String dispatchId;
    private String targetNodeId;
    private String commandMessage;
    private String senderName;
    private long dispatchedAt;
    private boolean ackReceived;
    private long ackTimestamp;

    public DispatchCommand() {
        this.dispatchId = UUID.randomUUID().toString();
        this.dispatchedAt = System.currentTimeMillis();
        this.ackReceived = false;
    }

    public DispatchCommand(String targetNodeId, String commandMessage, String senderName) {
        this();
        this.targetNodeId = targetNodeId;
        this.commandMessage = commandMessage;
        this.senderName = senderName;
    }

    // Getters & Setters
    public String getDispatchId() { return dispatchId; }
    public void setDispatchId(String dispatchId) { this.dispatchId = dispatchId; }

    public String getTargetNodeId() { return targetNodeId; }
    public void setTargetNodeId(String targetNodeId) { this.targetNodeId = targetNodeId; }

    public String getCommandMessage() { return commandMessage; }
    public void setCommandMessage(String commandMessage) { this.commandMessage = commandMessage; }

    public String getSenderName() { return senderName; }
    public void setSenderName(String senderName) { this.senderName = senderName; }

    public long getDispatchedAt() { return dispatchedAt; }
    public void setDispatchedAt(long dispatchedAt) { this.dispatchedAt = dispatchedAt; }

    public boolean isAckReceived() { return ackReceived; }
    public void setAckReceived(boolean ackReceived) { this.ackReceived = ackReceived; }

    public long getAckTimestamp() { return ackTimestamp; }
    public void setAckTimestamp(long ackTimestamp) { this.ackTimestamp = ackTimestamp; }

    @Override
    public String toString() {
        return "DispatchCommand{target='" + targetNodeId + "', msg='" + commandMessage + "', ack=" + ackReceived + "}";
    }
}

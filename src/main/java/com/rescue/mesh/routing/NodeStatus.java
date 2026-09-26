package com.rescue.mesh.routing;

import java.time.Duration;
import java.time.Instant;

public record NodeStatus(String nodeId, int currentLoad, int processedTotal,
                         String ipAddress, int port, String victimId, Instant lastHeartbeat) {
    public boolean isOnline() { return isAlive(10); }
    public boolean isAlive(int seconds) { return Duration.between(lastHeartbeat, Instant.now()).compareTo(Duration.ofSeconds(seconds)) < 0; }
    public long getSecondsSinceLastHeartbeat() { return Duration.between(lastHeartbeat, Instant.now()).getSeconds(); }
    public String getNodeId() { return nodeId; }
    public int getCurrentLoad() { return currentLoad; }
    public int getProcessedTotal() { return processedTotal; }
    public String getIpAddress() { return ipAddress; }
    public int getPort() { return port; }
}

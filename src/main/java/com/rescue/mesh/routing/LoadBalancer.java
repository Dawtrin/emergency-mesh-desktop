package com.rescue.mesh.routing;

import com.google.gson.JsonObject;
import com.rescue.mesh.model.MeshPacket;
import com.rescue.mesh.model.PacketValidator;
import com.rescue.mesh.util.PacketFactory;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class LoadBalancer {
    private final Map<String, NodeStatus> relays = new ConcurrentHashMap<>();

    public boolean acceptHeartbeat(MeshPacket packet, String remoteHost) {
        if (packet == null || remoteHost == null || remoteHost.isBlank()
                || !PacketValidator.validate(packet).isValid()
                || !packet.verifyChecksum(PacketFactory.getGson())
                || !MeshPacket.TYPE_HEARTBEAT.equals(packet.getPacketType())
                || !packet.getSourceNodeId().equals(packet.getSenderHopId())
                || packet.getPayload().getDiscoveryInfo() == null
                || !packet.getPayload().getDiscoveryInfo().isJsonObject()) return false;
        try {
            JsonObject report = packet.getPayload().getDiscoveryInfo().getAsJsonObject();
            if (!"relay_status".equals(report.get("kind").getAsString())) return false;
            int port = report.get("listen_port").getAsBigDecimal().intValueExact();
            int load = report.get("current_load").getAsBigDecimal().intValueExact();
            int total = report.get("processed_total").getAsBigDecimal().intValueExact();
            String victim = report.get("victim_id").getAsString();
            if (port < 1 || port > 65535 || load < 0 || total < 0
                    || !victim.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")) return false;
            if (relays.size() >= 128 && !relays.containsKey(packet.getSourceNodeId())) return false;
            relays.put(packet.getSourceNodeId(), new NodeStatus(packet.getSourceNodeId(),
                    load, total, remoteHost, port, victim, Instant.now()));
            return true;
        } catch (RuntimeException invalidReport) {
            return false;
        }
    }

    public List<NodeStatus> candidates(String victimId) {
        return relays.values().stream().filter(NodeStatus::isOnline)
                .filter(relay -> relay.victimId().equals(victimId))
                .sorted(Comparator.comparingInt(NodeStatus::getCurrentLoad)
                        .thenComparingInt(NodeStatus::getProcessedTotal).thenComparing(NodeStatus::getNodeId))
                .toList();
    }

    public NodeStatus getNodeStatus(String id) { return relays.get(id); }
    public Map<String, NodeStatus> getRoutingTable() { return Map.copyOf(relays); }
    public int getOnlineCount() { return (int) relays.values().stream().filter(NodeStatus::isOnline).count(); }
    public int getTotalCount() { return relays.size(); }
    public Map<String, Double> getDistributionStats() {
        double total = relays.values().stream().mapToDouble(NodeStatus::getProcessedTotal).sum();
        return relays.values().stream().collect(Collectors.toMap(NodeStatus::getNodeId,
                relay -> total == 0 ? 0.0 : relay.getProcessedTotal() * 100.0 / total));
    }
}

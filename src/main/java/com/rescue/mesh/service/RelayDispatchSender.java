package com.rescue.mesh.service;

import com.rescue.mesh.model.MeshPacket;
import com.rescue.mesh.routing.LoadBalancer;
import com.rescue.mesh.routing.NodeStatus;
import com.rescue.mesh.routing.RoutingEngine;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public final class RelayDispatchSender implements DispatchOutboxService.DispatchSender {
    private final LoadBalancer balancer;
    private final RoutingEngine.PacketSender transport;
    private final Map<String, String> previousRelay = new ConcurrentHashMap<>();

    public RelayDispatchSender(LoadBalancer balancer, RoutingEngine.PacketSender transport) {
        this.balancer = balancer;
        this.transport = transport;
    }

    @Override
    public boolean send(String fallbackHost, int fallbackPort, MeshPacket packet) {
        List<NodeStatus> candidates = balancer.candidates(packet.getDestinationNodeId());
        if (candidates.isEmpty()) return transport.send(fallbackHost, fallbackPort, packet);
        String previous = previousRelay.get(packet.getPacketId());
        int start = 0;
        for (int index = 0; index < candidates.size(); index++) {
            if (candidates.get(index).nodeId().equals(previous)) start = (index + 1) % candidates.size();
        }
        for (int offset = 0; offset < candidates.size(); offset++) {
            NodeStatus relay = candidates.get((start + offset) % candidates.size());
            boolean sent;
            try {
                sent = transport.send(relay.ipAddress(), relay.port(), packet);
            } catch (RuntimeException transportError) {
                sent = false;
            }
            if (sent) {
                if (previousRelay.size() >= 1024) previousRelay.clear();
                previousRelay.put(packet.getPacketId(), relay.nodeId());
                System.out.println("[DISPATCH RELAY] packet=" + packet.getPacketId() + " via="
                        + relay.nodeId() + " endpoint=" + relay.ipAddress() + ':' + relay.port()
                        + " state=WAITING_FOR_ACK");
                return true;
            }
        }
        return false;
    }

    public void acknowledged(String packetId) {
        if (packetId != null) previousRelay.remove(packetId);
    }
}

package com.rescue.mesh.service;

import com.rescue.mesh.model.MeshPacket;
import com.rescue.mesh.routing.LoadBalancer;
import com.rescue.mesh.util.PacketFactory;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RelayDispatchSenderTest {
    @Test
    void retriesThroughAnotherRelayAfterTransportFailureAndRotatesOnNextAttempt() {
        LoadBalancer balancer = new LoadBalancer();
        addRelay(balancer, "RELAY-01", 18002, 0);
        addRelay(balancer, "RELAY-02", 18003, 1);
        List<Integer> attempts = new ArrayList<>();
        RelayDispatchSender sender = new RelayDispatchSender(balancer, (host, port, packet) -> {
            attempts.add(port);
            if (port == 18002) throw new IllegalStateException("relay down");
            return true;
        });
        MeshPacket dispatch = PacketFactory.createDispatchCommand("VICTIM-01", "Move", MeshPacket.SEVERITY_HIGH);

        assertTrue(sender.send("fallback", 19000, dispatch));
        assertEquals(List.of(18002, 18003), attempts);

        attempts.clear();
        assertTrue(sender.send("fallback", 19000, dispatch));
        assertEquals(List.of(18002, 18003), attempts);
    }

    private static void addRelay(LoadBalancer balancer, String relayId, int port, int load) {
        MeshPacket heartbeat = PacketFactory.createHeartbeat(relayId, port, load, 0);
        heartbeat.getPayload().getDiscoveryInfo().getAsJsonObject().addProperty("victim_id", "VICTIM-01");
        heartbeat.computeAndSetChecksum(PacketFactory.getGson());
        assertTrue(balancer.acceptHeartbeat(heartbeat, "127.0.0.1"));
    }
}

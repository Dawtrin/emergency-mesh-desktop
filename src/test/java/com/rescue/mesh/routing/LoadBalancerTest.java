package com.rescue.mesh.routing;

import com.rescue.mesh.model.MeshPacket;
import com.rescue.mesh.util.PacketFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoadBalancerTest {
    @Test
    void acceptsVerifiedRelayHeartbeatsAndOrdersCandidatesByLoad() {
        LoadBalancer balancer = new LoadBalancer();

        assertTrue(balancer.acceptHeartbeat(status("RELAY-01", "VICTIM-01", 18002, 4, 12), "192.168.56.101"));
        assertTrue(balancer.acceptHeartbeat(status("RELAY-02", "VICTIM-01", 18003, 1, 8), "192.168.56.102"));

        NodeStatus selected = balancer.candidates("VICTIM-01").getFirst();
        assertEquals("RELAY-02", selected.nodeId());
        assertEquals("192.168.56.102", selected.ipAddress());
        assertEquals(18003, selected.port());
    }

    @Test
    void rejectsHeartbeatWhenChecksumOrVictimIdIsInvalid() {
        LoadBalancer balancer = new LoadBalancer();
        MeshPacket packet = status("RELAY-01", "INVALID VICTIM", 18002, 0, 0);
        assertFalse(balancer.acceptHeartbeat(packet, "192.168.56.101"));

        MeshPacket tampered = status("RELAY-01", "VICTIM-01", 18002, 0, 0);
        tampered.getPayload().setMessage("tampered");
        assertFalse(balancer.acceptHeartbeat(tampered, "192.168.56.101"));
    }

    private static MeshPacket status(String relayId, String victimId, int port, int load, int total) {
        MeshPacket packet = PacketFactory.createHeartbeat(relayId, port, load, total);
        packet.getPayload().getDiscoveryInfo().getAsJsonObject().addProperty("victim_id", victimId);
        packet.computeAndSetChecksum(PacketFactory.getGson());
        return packet;
    }
}
